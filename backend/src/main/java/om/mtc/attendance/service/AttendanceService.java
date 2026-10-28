package om.mtc.attendance.service;

import om.mtc.attendance.config.AppProperties;
import om.mtc.attendance.domain.*;
import om.mtc.attendance.repository.*;
import om.mtc.attendance.security.CurrentUser;
import om.mtc.attendance.web.dto.MiscDtos.PageResponse;
import om.mtc.attendance.web.dto.SessionDtos.*;
import om.mtc.attendance.web.error.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** Attendance sessions: open → (camera / manual) → close (auto-absent) → leader approval. */
@Service
public class AttendanceService {
    private final AttendanceSessionRepository sessions;
    private final AttendanceRecordRepository records;
    private final SoldierRepository soldiers;
    private final PlatoonRepository platoons;
    private final FaceEmbeddingRepository faces;
    private final AppUserRepository users;
    private final AccessPolicy access;
    private final FaceClient faceClient;
    private final BiometricCrypto crypto;
    private final SettingsService settings;
    private final AuditService audit;
    private final AppProperties props;

    public AttendanceService(AttendanceSessionRepository sessions, AttendanceRecordRepository records,
                             SoldierRepository soldiers, PlatoonRepository platoons, FaceEmbeddingRepository faces,
                             AppUserRepository users, AccessPolicy access, FaceClient faceClient,
                             BiometricCrypto crypto, SettingsService settings, AuditService audit,
                             AppProperties props) {
        this.sessions = sessions;
        this.records = records;
        this.soldiers = soldiers;
        this.platoons = platoons;
        this.faces = faces;
        this.users = users;
        this.access = access;
        this.faceClient = faceClient;
        this.crypto = crypto;
        this.settings = settings;
        this.audit = audit;
        this.props = props;
    }

    // ------------------------------------------------------------------ open / list / detail

    @Transactional
    public SessionSummary open(CurrentUser cu, OpenSessionRequest r) {
        access.requireTakeAttendance(cu, r.platoonId());
        Platoon p = platoons.findById(r.platoonId()).orElseThrow(() -> ApiException.notFound("الفصيل"));
        if (!p.isActive()) {
            throw ApiException.unprocessable("PLATOON_INACTIVE", "الفصيل غير نشط");
        }
        sessions.findFirstByPlatoon_IdAndStatus(p.getId(), SessionStatus.OPEN).ifPresent(s -> {
            throw ApiException.conflict("SESSION_ALREADY_OPEN",
                    "توجد جلسة تحضير مفتوحة لهذا الفصيل، أغلقها أولاً (رقم " + s.getId() + ")");
        });
        AttendanceSession s = new AttendanceSession();
        s.setPlatoon(p);
        s.setSessionType(r.sessionType());
        s.setTitle(r.title() == null || r.title().isBlank() ? null : r.title().trim());
        s.setScheduledAt(r.scheduledAt());
        s.setSessionDate(LocalDate.now(props.zoneId()));
        s.setOpenedBy(users.getReferenceById(cu.id()));
        s.setOpenedAt(Instant.now());
        try {
            sessions.saveAndFlush(s);
        } catch (DataIntegrityViolationException e) {
            // Race: another device opened a session for the same platoon at the same moment.
            throw ApiException.conflict("SESSION_ALREADY_OPEN", "توجد جلسة تحضير مفتوحة لهذا الفصيل");
        }
        audit.log("SESSION_OPENED", "SESSION", s.getId(), "platoon=" + p.getId() + " type=" + r.sessionType());
        return summary(s, cu.fullName());
    }

    @Transactional(readOnly = true)
    public PageResponse<SessionSummary> list(CurrentUser cu, Long platoonId, SessionStatus status,
                                             LocalDate from, LocalDate to, int page, int size) {
        List<Long> scope;
        if (platoonId != null) {
            access.requireRead(cu, platoonId);
            scope = List.of(platoonId);
        } else {
            scope = access.readablePlatoonIds(cu);
        }
        LocalDate today = LocalDate.now(props.zoneId());
        LocalDate f = from == null ? today.minusDays(30) : from;
        LocalDate t = to == null ? today : to;
        if (scope.isEmpty() || t.isBefore(f)) {
            return new PageResponse<>(List.of(), 0, size, 0, 0);
        }
        var p = sessions.search(scope, status, f, t,
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
        Map<Long, Counts> counts = countsFor(p.getContent());
        return PageResponse.of(p.map(s -> summary(s, counts.get(s.getId()))));
    }

    @Transactional(readOnly = true)
    public SessionDetail detail(CurrentUser cu, Long id) {
        AttendanceSession s = load(id);
        access.requireRead(cu, s.getPlatoonId());
        return buildDetail(s);
    }

    /** The open session of a platoon, if any (used by the senior's home screen). */
    @Transactional(readOnly = true)
    public Optional<SessionSummary> openSession(CurrentUser cu, Long platoonId) {
        access.requireRead(cu, platoonId);
        return sessions.findFirstByPlatoon_IdAndStatus(platoonId, SessionStatus.OPEN)
                .map(s -> summary(s, countsFor(List.of(s)).get(s.getId())));
    }

    // ------------------------------------------------------------------ camera attendance

    @Transactional
    public RecognitionResult recognize(CurrentUser cu, Long id, MultipartFile file) {
        AttendanceSession s = load(id);
        access.requireTakeAttendance(cu, s.getPlatoonId());
        requireOpen(s);
        byte[] image = ImageGuard.readValidated(file);

        FaceClient.EmbedResponse resp = faceClient.embed(image, file.getOriginalFilename());
        // The image is not kept anywhere beyond this point.

        // Candidate pool: enrolled templates of this platoon's active soldiers only, and only
        // templates produced by the same model (embeddings of different models are not comparable).
        Map<Long, List<float[]>> candidates = new HashMap<>();
        Map<Long, Soldier> soldierById = new HashMap<>();
        for (FaceEmbedding fe : faces.findActiveByPlatoon(s.getPlatoonId())) {
            if (!Objects.equals(fe.getModelName(), resp.model())) {
                continue;
            }
            Soldier sol = fe.getSoldier();
            soldierById.put(sol.getId(), sol);
            candidates.computeIfAbsent(sol.getId(), k -> new ArrayList<>())
                    .add(crypto.decrypt(fe.getEmbeddingEnc(), sol.getId()));
        }

        List<FaceMatcher.Face> detected = resp.faces().stream()
                .map(f -> new FaceMatcher.Face(f.embedding(), f.detScore())).toList();
        List<FaceMatcher.Assignment> assignments = FaceMatcher.match(detected, candidates,
                settings.recognitionThreshold(), settings.recognitionMinDetScore());

        Set<Long> alreadyRecorded = new HashSet<>(records.findSoldierIdsBySession(s.getId()));
        AttendanceStatus statusNow = isLate(s) ? AttendanceStatus.LATE : AttendanceStatus.PRESENT;
        AppUser by = users.getReferenceById(cu.id());

        int recognized = 0, already = 0, unknown = 0, lowQuality = 0;
        List<FaceResult> results = new ArrayList<>();
        for (FaceMatcher.Assignment a : assignments) {
            FaceClient.DetectedFace df = resp.faces().get(a.faceIndex());
            switch (a.outcome()) {
                case LOW_QUALITY -> {
                    lowQuality++;
                    results.add(new FaceResult(df.bbox(), df.detScore(), "LOW_QUALITY", null, null, null, null));
                }
                case UNKNOWN -> {
                    unknown++;
                    results.add(new FaceResult(df.bbox(), df.detScore(), "UNKNOWN", null, null, null, null));
                }
                case MATCHED -> {
                    Soldier sol = soldierById.get(a.soldierId());
                    String outcome;
                    if (alreadyRecorded.contains(sol.getId())) {
                        already++;
                        outcome = "ALREADY_RECORDED";
                    } else {
                        AttendanceRecord rec = new AttendanceRecord();
                        rec.setSession(s);
                        rec.setSoldier(sol);
                        rec.setStatus(statusNow);
                        rec.setMethod(AttendanceMethod.FACE);
                        rec.setSimilarity(round(a.similarity()));
                        rec.setRecordedBy(by);
                        rec.setUpdatedBy(by);
                        records.save(rec);
                        alreadyRecorded.add(sol.getId());
                        recognized++;
                        outcome = statusNow == AttendanceStatus.LATE ? "MATCHED_LATE" : "MATCHED";
                    }
                    results.add(new FaceResult(df.bbox(), df.detScore(), outcome, sol.getId(), sol.getFullName(),
                            sol.getMilitaryNumber(), round(a.similarity())));
                }
            }
        }
        records.flush();

        Counts counts = countsFor(List.of(s)).get(s.getId());
        audit.log("FACE_RECOGNITION", "SESSION", s.getId(), "detected=" + resp.faces().size()
                + " recognized=" + recognized + " already=" + already + " unknown=" + unknown
                + " lowQuality=" + lowQuality + " threshold=" + settings.recognitionThreshold());
        return new RecognitionResult(resp.faces().size(), lowQuality, recognized, already, unknown,
                counts.unrecorded(), resp.imageWidth(), resp.imageHeight(), resp.elapsedMs(), results, counts);
    }

    // ------------------------------------------------------------------ manual attendance

    @Transactional
    public RecordView manual(CurrentUser cu, Long id, ManualMarkRequest r) {
        AttendanceSession s = load(id);
        access.requireTakeAttendance(cu, s.getPlatoonId());
        requireOpen(s);
        Soldier sol = soldiers.findById(r.soldierId()).orElseThrow(() -> ApiException.notFound("الجندي"));
        if (!Objects.equals(sol.getPlatoonId(), s.getPlatoonId())) {
            throw ApiException.unprocessable("SOLDIER_NOT_IN_PLATOON", "الجندي لا ينتمي إلى فصيل هذه الجلسة");
        }
        if (!sol.isActive()) {
            throw ApiException.unprocessable("SOLDIER_INACTIVE", "الجندي غير نشط");
        }
        AppUser by = users.getReferenceById(cu.id());
        AttendanceRecord rec = records.findBySessionIdAndSoldierId(s.getId(), sol.getId()).orElse(null);
        String before = rec == null ? "none" : rec.getStatus() + "/" + rec.getMethod();
        if (rec == null) {
            rec = new AttendanceRecord();
            rec.setSession(s);
            rec.setSoldier(sol);
            rec.setRecordedBy(by);
            rec.setRecordedAt(Instant.now());
        }
        rec.setStatus(r.status());
        rec.setMethod(AttendanceMethod.MANUAL);
        rec.setSimilarity(null);
        rec.setNote(r.note() == null || r.note().isBlank() ? null : r.note().trim());
        rec.setUpdatedBy(by);
        rec.setUpdatedAt(Instant.now());
        records.save(rec);
        audit.log("MANUAL_MARK", "SESSION", s.getId(), "soldier=" + sol.getId() + " " + before + " -> "
                + r.status());
        return view(rec);
    }

    // ------------------------------------------------------------------ close

    @Transactional
    public SessionSummary close(CurrentUser cu, Long id) {
        AttendanceSession s = load(id);
        access.requireTakeAttendance(cu, s.getPlatoonId());
        requireOpen(s);
        Set<Long> recorded = records.findSoldierIdsBySession(s.getId());
        AppUser by = users.getReferenceById(cu.id());
        int autoAbsent = 0;
        for (Soldier sol : soldiers.findByPlatoon_IdAndActiveTrueOrderByFullNameAsc(s.getPlatoonId())) {
            if (!recorded.contains(sol.getId())) {
                AttendanceRecord rec = new AttendanceRecord();
                rec.setSession(s);
                rec.setSoldier(sol);
                rec.setStatus(AttendanceStatus.ABSENT);
                rec.setMethod(AttendanceMethod.AUTO);
                rec.setRecordedBy(by);
                rec.setUpdatedBy(by);
                records.save(rec);
                autoAbsent++;
            }
        }
        s.setStatus(SessionStatus.CLOSED);
        s.setClosedBy(by);
        s.setClosedAt(Instant.now());
        sessions.saveAndFlush(s);
        Counts c = countsFor(List.of(s)).get(s.getId());
        audit.log("SESSION_CLOSED", "SESSION", s.getId(), "autoAbsent=" + autoAbsent + " present=" + c.present()
                + " late=" + c.late() + " excused=" + c.excused() + " absent=" + c.absent());
        return summary(s, c);
    }

    // ------------------------------------------------------------------ approval

    @Transactional
    public SessionSummary approve(CurrentUser cu, Long id, ApproveRequest r) {
        AttendanceSession s = load(id);
        access.requireApprove(cu, s.getPlatoonId());
        if (s.getStatus() != SessionStatus.CLOSED) {
            throw ApiException.conflict("SESSION_NOT_CLOSED", s.getStatus() == SessionStatus.OPEN
                    ? "يجب إغلاق الجلسة قبل اعتمادها" : "تم اعتماد هذه الجلسة مسبقاً");
        }
        AppUser by = users.getReferenceById(cu.id());
        Map<Long, AttendanceRecord> bySoldier = records.findBySessionWithSoldier(s.getId()).stream()
                .collect(Collectors.toMap(rec -> rec.getSoldier().getId(), rec -> rec));
        int changed = 0;
        if (r != null && r.changes() != null) {
            for (ReviewChange ch : r.changes()) {
                AttendanceRecord rec = bySoldier.get(ch.soldierId());
                if (rec == null) {
                    throw ApiException.unprocessable("RECORD_NOT_FOUND", "لا يوجد سجل لهذا الجندي في الجلسة");
                }
                String note = ch.note() == null || ch.note().isBlank() ? rec.getNote() : ch.note().trim();
                if (rec.getStatus() != ch.status() || !Objects.equals(rec.getNote(), note)) {
                    audit.log("RECORD_REVIEWED", "SESSION", s.getId(), "soldier=" + ch.soldierId() + " "
                            + rec.getStatus() + " -> " + ch.status());
                    rec.setStatus(ch.status());
                    rec.setNote(note);
                    rec.setUpdatedBy(by);
                    rec.setUpdatedAt(Instant.now());
                    records.save(rec);
                    changed++;
                }
            }
        }
        s.setStatus(SessionStatus.APPROVED);
        s.setApprovedBy(by);
        s.setApprovedAt(Instant.now());
        sessions.saveAndFlush(s);
        audit.log("SESSION_APPROVED", "SESSION", s.getId(), "changes=" + changed);
        return summary(s, countsFor(List.of(s)).get(s.getId()));
    }

    // ------------------------------------------------------------------ helpers

    private boolean isLate(AttendanceSession s) {
        if (s.getScheduledAt() == null) {
            return false;
        }
        Instant cutoff = s.getScheduledAt().plus(Duration.ofMinutes(settings.lateGraceMinutes()));
        return Instant.now().isAfter(cutoff);
    }

    private void requireOpen(AttendanceSession s) {
        if (s.getStatus() != SessionStatus.OPEN) {
            throw ApiException.conflict("SESSION_NOT_OPEN", "الجلسة مغلقة، لا يمكن تعديل التحضير");
        }
    }

    private AttendanceSession load(Long id) {
        return sessions.findById(id).orElseThrow(() -> ApiException.notFound("جلسة التحضير"));
    }

    private SessionDetail buildDetail(AttendanceSession s) {
        List<AttendanceRecord> recs = records.findBySessionWithSoldier(s.getId());
        Map<Long, AttendanceRecord> bySoldier = new HashMap<>();
        recs.forEach(r -> bySoldier.put(r.getSoldier().getId(), r));

        // Roster = active soldiers of the platoon + anyone who already has a record (e.g. deactivated later).
        LinkedHashMap<Long, Soldier> roster = new LinkedHashMap<>();
        if (s.getStatus() == SessionStatus.OPEN) {
            soldiers.findByPlatoon_IdAndActiveTrueOrderByFullNameAsc(s.getPlatoonId())
                    .forEach(sol -> roster.put(sol.getId(), sol));
        }
        recs.forEach(r -> roster.putIfAbsent(r.getSoldier().getId(), r.getSoldier()));

        Set<Long> withFaces = new HashSet<>();
        List<Long> ids = new ArrayList<>(roster.keySet());
        if (!ids.isEmpty()) {
            for (Object[] row : faces.countBySoldierIds(ids)) {
                withFaces.add((Long) row[0]);
            }
        }
        List<RosterEntry> entries = roster.values().stream()
                .sorted(Comparator.comparing(Soldier::getFullName))
                .map(sol -> new RosterEntry(sol.getId(), sol.getMilitaryNumber(), sol.getFullName(),
                        sol.getMilitaryRank(), withFaces.contains(sol.getId()),
                        sol.getPhotoUpdatedAt() == null ? null : sol.getPhotoUpdatedAt().toEpochMilli(),
                        bySoldier.containsKey(sol.getId()) ? view(bySoldier.get(sol.getId())) : null))
                .toList();
        return new SessionDetail(summary(s, countsFor(List.of(s)).get(s.getId())), entries);
    }

    /** Status counts; for an open session the total is the platoon's active strength. */
    Map<Long, Counts> countsFor(List<AttendanceSession> list) {
        Map<Long, Counts> out = new HashMap<>();
        if (list.isEmpty()) {
            return out;
        }
        Map<Long, EnumMap<AttendanceStatus, Integer>> raw = new HashMap<>();
        List<Long> ids = list.stream().map(AttendanceSession::getId).toList();
        for (Object[] row : records.countByStatusForSessions(ids)) {
            raw.computeIfAbsent((Long) row[0], k -> new EnumMap<>(AttendanceStatus.class))
                    .put((AttendanceStatus) row[1], ((Long) row[2]).intValue());
        }
        Map<Long, Long> strength = new HashMap<>();
        for (AttendanceSession s : list) {
            EnumMap<AttendanceStatus, Integer> m = raw.getOrDefault(s.getId(), new EnumMap<>(AttendanceStatus.class));
            int present = m.getOrDefault(AttendanceStatus.PRESENT, 0);
            int late = m.getOrDefault(AttendanceStatus.LATE, 0);
            int excused = m.getOrDefault(AttendanceStatus.EXCUSED, 0);
            int absent = m.getOrDefault(AttendanceStatus.ABSENT, 0);
            int recorded = present + late + excused + absent;
            int total = recorded;
            if (s.getStatus() == SessionStatus.OPEN) {
                long active = strength.computeIfAbsent(s.getPlatoonId(), soldiers::countByPlatoon_IdAndActiveTrue);
                total = (int) Math.max(active, recorded);
            }
            out.put(s.getId(), Counts.of(total, present, late, excused, absent));
        }
        return out;
    }

    private SessionSummary summary(AttendanceSession s, String openedByName) {
        return summary(s, countsFor(List.of(s)).get(s.getId()), openedByName);
    }

    SessionSummary summary(AttendanceSession s, Counts counts) {
        return summary(s, counts, s.getOpenedBy().getFullName());
    }

    private SessionSummary summary(AttendanceSession s, Counts counts, String openedByName) {
        return new SessionSummary(s.getId(), s.getPlatoonId(), s.getPlatoon().getName(), s.getSessionType(),
                s.getTitle(), s.getSessionDate(), s.getScheduledAt(), s.getStatus(), openedByName, s.getOpenedAt(),
                s.getClosedAt(), s.getApprovedBy() == null ? null : s.getApprovedBy().getFullName(),
                s.getApprovedAt(), counts);
    }

    static RecordView view(AttendanceRecord r) {
        Soldier sol = r.getSoldier();
        return new RecordView(r.getId(), sol.getId(), sol.getMilitaryNumber(), sol.getFullName(),
                sol.getMilitaryRank(), r.getStatus(), r.getMethod(), r.getSimilarity(), r.getNote(),
                r.getRecordedAt());
    }

    private static Float round(float v) {
        return Math.round(v * 1000f) / 1000f;
    }
}
