package om.mtc.attendance.service;

import om.mtc.attendance.domain.AppUser;
import om.mtc.attendance.domain.FaceEmbedding;
import om.mtc.attendance.domain.Platoon;
import om.mtc.attendance.domain.Role;
import om.mtc.attendance.domain.Soldier;
import om.mtc.attendance.repository.AppUserRepository;
import om.mtc.attendance.repository.FaceEmbeddingRepository;
import om.mtc.attendance.repository.PlatoonRepository;
import om.mtc.attendance.repository.SoldierRepository;
import om.mtc.attendance.security.CurrentUser;
import om.mtc.attendance.web.dto.MiscDtos.PageResponse;
import om.mtc.attendance.web.dto.SoldierDtos.EnrollResult;
import om.mtc.attendance.web.dto.SoldierDtos.PhotoResult;
import om.mtc.attendance.web.dto.SoldierDtos.FaceTemplateView;
import om.mtc.attendance.web.dto.SoldierDtos.SoldierRequest;
import om.mtc.attendance.web.dto.SoldierDtos.SoldierView;
import om.mtc.attendance.web.error.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class SoldierService {
    /** Faces smaller than this (in pixels) are rejected at enrollment: too little detail for a good template. */
    static final int MIN_ENROLL_FACE_PX = 80;
    private static final Set<String> SORTABLE = Set.of("fullName", "militaryNumber", "militaryRank", "createdAt");

    private final SoldierRepository soldiers;
    private final PlatoonRepository platoons;
    private final FaceEmbeddingRepository faces;
    private final AppUserRepository users;
    private final AccessPolicy access;
    private final FaceClient faceClient;
    private final BiometricCrypto crypto;
    private final SettingsService settings;
    private final AuditService audit;
    private final PhotoService photos;

    public SoldierService(SoldierRepository soldiers, PlatoonRepository platoons, FaceEmbeddingRepository faces,
                          AppUserRepository users, AccessPolicy access, FaceClient faceClient,
                          BiometricCrypto crypto, SettingsService settings, AuditService audit,
                          PhotoService photos) {
        this.soldiers = soldiers;
        this.platoons = platoons;
        this.faces = faces;
        this.users = users;
        this.access = access;
        this.faceClient = faceClient;
        this.crypto = crypto;
        this.settings = settings;
        this.audit = audit;
        this.photos = photos;
    }

    @Transactional(readOnly = true)
    public PageResponse<SoldierView> search(CurrentUser cu, Long platoonId, Boolean active, String q,
                                            int page, int size, String sort, String dir) {
        List<Long> scope;
        if (platoonId != null) {
            access.requireRead(cu, platoonId);
            scope = List.of(platoonId);
        } else {
            scope = access.readablePlatoonIds(cu);
        }
        if (scope.isEmpty()) {
            return new PageResponse<>(List.of(), 0, size, 0, 0);
        }
        String sortField = SORTABLE.contains(sort) ? sort : "fullName";
        Sort.Direction direction = "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        String like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase() + "%";
        Page<Soldier> p = soldiers.search(scope, active, like,
                PageRequest.of(Math.max(0, page), Math.min(200, Math.max(1, size)), Sort.by(direction, sortField)));
        Map<Long, Long> faceCounts = faceCounts(p.getContent().stream().map(Soldier::getId).toList());
        return PageResponse.of(p.map(s -> SoldierView.of(s, faceCounts.getOrDefault(s.getId(), 0L))));
    }

    @Transactional(readOnly = true)
    public SoldierView get(CurrentUser cu, Long id) {
        Soldier s = load(id);
        access.requireRead(cu, s.getPlatoonId());
        return SoldierView.of(s, faces.countBySoldierId(id));
    }

    @Transactional
    public SoldierView create(CurrentUser cu, SoldierRequest r) {
        access.requireManageSoldiers(cu, r.platoonId());
        String number = r.militaryNumber().trim();
        if (soldiers.existsByMilitaryNumber(number)) {
            throw ApiException.conflict("MILITARY_NUMBER_TAKEN", "الرقم العسكري مسجّل مسبقاً");
        }
        Platoon p = platoons.findById(r.platoonId()).orElseThrow(() -> ApiException.notFound("الفصيل"));
        Soldier s = new Soldier();
        s.setMilitaryNumber(number);
        s.setFullName(r.fullName().trim());
        s.setMilitaryRank(r.militaryRank().trim());
        s.setPlatoon(p);
        applyConsent(cu, s, r.biometricConsent());
        soldiers.save(s);
        audit.log("SOLDIER_CREATED", "SOLDIER", s.getId(), "number=" + number + " platoon=" + p.getId());
        return SoldierView.of(s, 0);
    }

    @Transactional
    public SoldierView update(CurrentUser cu, Long id, SoldierRequest r) {
        Soldier s = load(id);
        access.requireManageSoldiers(cu, s.getPlatoonId());
        if (!Objects.equals(s.getPlatoonId(), r.platoonId())) {
            // Moving a soldier between platoons is a company-level decision.
            if (!cu.is(Role.COMPANY_AGENT)) {
                throw ApiException.forbidden();
            }
            s.setPlatoon(platoons.findById(r.platoonId()).orElseThrow(() -> ApiException.notFound("الفصيل")));
        }
        String number = r.militaryNumber().trim();
        if (!number.equals(s.getMilitaryNumber()) && soldiers.existsByMilitaryNumber(number)) {
            throw ApiException.conflict("MILITARY_NUMBER_TAKEN", "الرقم العسكري مسجّل مسبقاً");
        }
        s.setMilitaryNumber(number);
        s.setFullName(r.fullName().trim());
        s.setMilitaryRank(r.militaryRank().trim());
        boolean revoked = s.isBiometricConsent() && !r.biometricConsent();
        applyConsent(cu, s, r.biometricConsent());
        if (revoked) {
            int removed = faces.deleteBySoldierId(s.getId());
            audit.log("BIOMETRIC_ERASED", "SOLDIER", s.getId(), "consent withdrawn, templates=" + removed);
        }
        soldiers.save(s);
        audit.log("SOLDIER_UPDATED", "SOLDIER", s.getId(), "number=" + number + " platoon=" + s.getPlatoonId());
        return SoldierView.of(s, faces.countBySoldierId(s.getId()));
    }

    /**
     * End of service in the unit: the soldier is deactivated and his biometric
     * templates are deleted immediately (data-retention policy). Attendance history is kept.
     */
    @Transactional
    public SoldierView deactivate(CurrentUser cu, Long id) {
        Soldier s = load(id);
        access.requireManageSoldiers(cu, s.getPlatoonId());
        if (!s.isActive()) {
            return SoldierView.of(s, 0);
        }
        int removed = faces.deleteBySoldierId(s.getId());
        boolean photoErased = photos.delete(s);
        s.setActive(false);
        s.setBiometricConsent(false);
        s.setDeactivatedAt(Instant.now());
        soldiers.save(s);
        audit.log("SOLDIER_DEACTIVATED", "SOLDIER", s.getId(),
                "templates erased=" + removed + " photo erased=" + photoErased);
        return SoldierView.of(s, 0);
    }

    @Transactional
    public SoldierView reactivate(CurrentUser cu, Long id) {
        Soldier s = load(id);
        access.requireManageSoldiers(cu, s.getPlatoonId());
        s.setActive(true);
        s.setDeactivatedAt(null);
        soldiers.save(s);
        audit.log("SOLDIER_REACTIVATED", "SOLDIER", s.getId(), null);
        return SoldierView.of(s, 0);
    }

    @Transactional(readOnly = true)
    public List<FaceTemplateView> listFaces(CurrentUser cu, Long id) {
        Soldier s = load(id);
        access.requireManageSoldiers(cu, s.getPlatoonId());
        return faces.findBySoldierIdOrderByCreatedAtAsc(id).stream()
                .map(f -> new FaceTemplateView(f.getId(), f.getModelName(), f.getDetScore(), f.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void eraseFaces(CurrentUser cu, Long id) {
        Soldier s = load(id);
        access.requireManageSoldiers(cu, s.getPlatoonId());
        int removed = faces.deleteBySoldierId(id);
        audit.log("BIOMETRIC_ERASED", "SOLDIER", id, "templates=" + removed);
    }

    /** Enroll one face photo: exactly one clear face, consistent with earlier photos and with no other soldier. */
    @Transactional
    public EnrollResult enrollFace(CurrentUser cu, Long id, MultipartFile file) {
        Soldier s = load(id);
        access.requireManageSoldiers(cu, s.getPlatoonId());
        byte[] data = ImageGuard.readValidated(file);
        return enrollInternal(cu, s, data, file.getOriginalFilename()).result();
    }

    /**
     * Save the soldier's profile photo. With {@code enroll=true} the same image also enrolls the face
     * (all enrollment checks apply, and nothing is stored if they fail). The photo is cropped around the face.
     */
    @Transactional
    public PhotoResult uploadPhoto(CurrentUser cu, Long id, MultipartFile file, boolean enroll) {
        Soldier s = load(id);
        access.requireManageSoldiers(cu, s.getPlatoonId());
        if (!s.isActive()) {
            throw ApiException.unprocessable("SOLDIER_INACTIVE", "الجندي غير نشط");
        }
        byte[] data = ImageGuard.readValidated(file);
        Enrolled enrolled = null;
        if (enroll) {
            enrolled = enrollInternal(cu, s, data, file.getOriginalFilename());
        }
        byte[] avatar = photos.toAvatar(data, enrolled == null ? null : enrolled.box());
        photos.save(s, avatar, cu.id());
        soldiers.save(s);
        audit.log("PHOTO_SET", "SOLDIER", id, "enrolled=" + enroll);
        return new PhotoResult(SoldierView.of(s, faces.countBySoldierId(id)), enrolled == null ? null : enrolled.result());
    }

    @Transactional(readOnly = true)
    public byte[] photo(CurrentUser cu, Long id) {
        Soldier s = load(id);
        access.requireRead(cu, s.getPlatoonId());
        if (s.getPhotoUpdatedAt() == null) {
            throw ApiException.notFound("الصورة");
        }
        return photos.load(s);
    }

    @Transactional
    public SoldierView deletePhoto(CurrentUser cu, Long id) {
        Soldier s = load(id);
        access.requireManageSoldiers(cu, s.getPlatoonId());
        photos.delete(s);
        soldiers.save(s);
        audit.log("PHOTO_DELETED", "SOLDIER", id, null);
        return SoldierView.of(s, faces.countBySoldierId(id));
    }

    private record Enrolled(EnrollResult result, int[] box) {
    }

    private Enrolled enrollInternal(CurrentUser cu, Soldier s, byte[] data, String filename) {
        Long id = s.getId();
        if (!s.isActive()) {
            throw ApiException.unprocessable("SOLDIER_INACTIVE", "الجندي غير نشط");
        }
        if (!s.isBiometricConsent()) {
            throw ApiException.unprocessable("CONSENT_REQUIRED",
                    "يجب تسجيل موافقة الجندي على معالجة بياناته البيومترية قبل تسجيل الوجه");
        }
        int max = settings.enrollmentMaxFaces();
        List<FaceEmbedding> own = faces.findBySoldierIdOrderByCreatedAtAsc(id);
        if (own.size() >= max) {
            throw ApiException.unprocessable("MAX_FACES_REACHED", "تم تسجيل الحد الأقصى من الصور (" + max + ")");
        }

        FaceClient.EmbedResponse resp = faceClient.embed(data, filename);
        if (resp.faces().isEmpty()) {
            throw ApiException.unprocessable("NO_FACE", "لم يتم اكتشاف وجه في الصورة، أعد التصوير");
        }
        if (resp.faces().size() > 1) {
            throw ApiException.unprocessable("MULTIPLE_FACES",
                    "الصورة تحتوي أكثر من وجه، يجب أن تحتوي صورة التسجيل وجهاً واحداً فقط");
        }
        FaceClient.DetectedFace f = resp.faces().get(0);
        if (f.detScore() < settings.enrollmentMinDetScore()) {
            throw ApiException.unprocessable("LOW_QUALITY", "الوجه غير واضح بما يكفي، حسّن الإضاءة وأعد التصوير");
        }
        if (Math.min(f.width(), f.height()) < MIN_ENROLL_FACE_PX) {
            throw ApiException.unprocessable("FACE_TOO_SMALL", "الوجه صغير جداً في الصورة، اقترب أكثر");
        }

        // Consistency with this soldier's earlier templates (guards against enrolling the wrong person).
        if (!own.isEmpty()) {
            List<float[]> ownTemplates = new ArrayList<>();
            for (FaceEmbedding e : own) {
                if (Objects.equals(e.getModelName(), resp.model())) {
                    ownTemplates.add(crypto.decrypt(e.getEmbeddingEnc(), id));
                }
            }
            float self = FaceMatcher.best(f.embedding(), ownTemplates);
            if (!ownTemplates.isEmpty() && self < settings.recognitionThreshold() - 0.10) {
                throw ApiException.unprocessable("SELF_MISMATCH",
                        "الصورة لا تطابق الصور المسجّلة سابقاً لهذا الجندي");
            }
        }

        // Duplicate identity: the face must not already belong to another active soldier.
        double dupThreshold = settings.enrollmentDuplicateThreshold();
        Long dupId = null;
        float dupSim = -1f;
        for (FaceEmbedding e : faces.findAllActive()) {
            Long otherId = e.getSoldier().getId();
            if (otherId.equals(id) || !Objects.equals(e.getModelName(), resp.model())) {
                continue;
            }
            float sim = FaceMatcher.cosine(f.embedding(), crypto.decrypt(e.getEmbeddingEnc(), otherId));
            if (sim >= dupThreshold && sim > dupSim) {
                dupSim = sim;
                dupId = otherId;
            }
        }
        if (dupId != null) {
            Soldier other = soldiers.findById(dupId).orElseThrow();
            audit.log("FACE_ENROLL_REJECTED", "SOLDIER", id, "duplicate of soldier " + dupId);
            throw ApiException.conflict("DUPLICATE_IDENTITY",
                    "هذا الوجه مسجّل مسبقاً للجندي " + other.getFullName() + " (" + other.getMilitaryNumber() + ")");
        }

        FaceEmbedding fe = new FaceEmbedding();
        fe.setSoldier(s);
        fe.setEmbeddingEnc(crypto.encrypt(f.embedding(), id));
        fe.setModelName(resp.model());
        fe.setDetScore(f.detScore());
        fe.setCreatedBy(users.getReferenceById(cu.id()));
        faces.save(fe);
        audit.log("FACE_ENROLLED", "SOLDIER", id, "template=" + (own.size() + 1) + "/" + max
                + " det=" + String.format("%.2f", f.detScore()));
        return new Enrolled(new EnrollResult(own.size() + 1, max, f.detScore(),
                new FaceTemplateView(fe.getId(), fe.getModelName(), fe.getDetScore(), fe.getCreatedAt())), f.bbox());
    }

    private void applyConsent(CurrentUser cu, Soldier s, boolean consent) {
        if (consent && !s.isBiometricConsent()) {
            AppUser by = users.getReferenceById(cu.id());
            s.setConsentAt(Instant.now());
            s.setConsentBy(by);
        }
        if (!consent) {
            s.setConsentAt(null);
            s.setConsentBy(null);
        }
        s.setBiometricConsent(consent);
    }

    private Map<Long, Long> faceCounts(List<Long> ids) {
        Map<Long, Long> m = new HashMap<>();
        if (ids.isEmpty()) {
            return m;
        }
        for (Object[] row : faces.countBySoldierIds(ids)) {
            m.put((Long) row[0], (Long) row[1]);
        }
        return m;
    }

    private Soldier load(Long id) {
        return soldiers.findById(id).orElseThrow(() -> ApiException.notFound("الجندي"));
    }
}
