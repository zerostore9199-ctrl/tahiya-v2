package om.mtc.attendance.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import om.mtc.attendance.domain.AppUser;
import om.mtc.attendance.domain.Platoon;
import om.mtc.attendance.domain.Role;
import om.mtc.attendance.domain.Soldier;
import om.mtc.attendance.repository.AppUserRepository;
import om.mtc.attendance.repository.PlatoonRepository;
import om.mtc.attendance.repository.SoldierRepository;
import om.mtc.attendance.service.FaceClient;
import om.mtc.attendance.service.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end API tests against a real PostgreSQL database (Flyway schema),
 * with only the face service mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AttendanceFlowIntegrationTest {
    static final String PW = "Parade-Ground-2026";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatoonRepository platoons;
    @Autowired AppUserRepository users;
    @Autowired SoldierRepository soldiers;
    @Autowired PasswordEncoder encoder;
    @Autowired SettingsService settings;
    @MockBean FaceClient faceClient;

    Platoon p1, p2;
    Soldier s1, s2, s3;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE attendance_record, attendance_session, face_embedding, soldier, refresh_token, "
                + "app_user, platoon, audit_log RESTART IDENTITY CASCADE");
        settings.evictCache();
        p1 = platoon("P1", "الفصيل الأول");
        p2 = platoon("P2", "الفصيل الثاني");
        user("agent", Role.COMPANY_AGENT, null, false);
        user("general", Role.GENERAL_SENIOR, null, false);
        user("leader1", Role.PLATOON_LEADER, p1, false);
        user("senior1", Role.SENIOR, p1, false);
        user("senior2", Role.SENIOR, p2, false);
        user("newbie", Role.SENIOR, p1, true);
        s1 = soldier("1001", "سالم الحارثي", p1);
        s2 = soldier("1002", "خالد الكندي", p1);
        s3 = soldier("1003", "محمد البلوشي", p1);
    }

    // ------------------------------------------------------------------ authentication

    @Test
    void loginReturnsTokenAndRefreshCookie() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"senior1\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("SENIOR"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("Set-Cookie"))
                        .contains("mtc_rt=").contains("HttpOnly").contains("SameSite=Strict"));
    }

    @Test
    void wrongPasswordIsRejectedAndAccountLocksAfterRepeatedFailures() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"senior1\",\"password\":\"wrong-password-1\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"senior1\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        Integer failures = jdbc.queryForObject(
                "select count(*) from audit_log where action = 'LOGIN_FAILED' and username = 'senior1'", Integer.class);
        assertThat(failures).isEqualTo(3);
    }

    @Test
    void unknownUserGetsTheSameGenericError() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"whatever-123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void mustChangePasswordBlocksTheApiUntilChanged() throws Exception {
        String t = login("newbie");
        mvc.perform(get("/api/platoons").header("Authorization", "Bearer " + t))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        MvcResult r = mvc.perform(post("/api/auth/change-password").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PW + "\",\"newPassword\":\"Brand-New-Pass-77\"}"))
                .andExpect(status().isOk()).andReturn();
        String fresh = om.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
        mvc.perform(get("/api/platoons").header("Authorization", "Bearer " + fresh)).andExpect(status().isOk());
    }

    @Test
    void requestsWithoutOrWithForgedTokenAreRejected() throws Exception {
        mvc.perform(get("/api/sessions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/sessions").header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ permissions (جدول 3)

    @Test
    void permissionMatrixIsEnforcedOnTheServer() throws Exception {
        String senior1 = login("senior1");
        String general = login("general");
        String leader1 = login("leader1");

        // Seniors cannot manage users, see audit, or reports.
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + senior1)).andExpect(status().isForbidden());
        mvc.perform(get("/api/audit").header("Authorization", "Bearer " + senior1)).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/monthly").header("Authorization", "Bearer " + senior1))
                .andExpect(status().isForbidden());
        // A senior cannot open a session for another platoon.
        mvc.perform(post("/api/sessions").header("Authorization", "Bearer " + senior1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platoonId\":" + p2.getId() + ",\"sessionType\":\"MORNING_PARADE\"}"))
                .andExpect(status().isForbidden());
        // The general senior monitors only — he cannot take attendance.
        mvc.perform(post("/api/sessions").header("Authorization", "Bearer " + general)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platoonId\":" + p1.getId() + ",\"sessionType\":\"MORNING_PARADE\"}"))
                .andExpect(status().isForbidden());
        // A platoon leader's reports are limited to his own platoon.
        mvc.perform(get("/api/reports/monthly?platoonId=" + p2.getId()).header("Authorization", "Bearer " + leader1))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/monthly?platoonId=" + p1.getId()).header("Authorization", "Bearer " + leader1))
                .andExpect(status().isOk());
        // Data scoping: senior1 only sees his own platoon.
        mvc.perform(get("/api/platoons").header("Authorization", "Bearer " + senior1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("P1"));
        mvc.perform(get("/api/soldiers?platoonId=" + p1.getId()).header("Authorization", "Bearer " + login("senior2")))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ enrollment

    @Test
    void enrollmentRequiresConsentOneClearFaceAndAUniqueIdentity() throws Exception {
        String leader = login("leader1");
        // No consent yet.
        faceReturns(face(0, 0.95f, 150));
        mvc.perform(multipart("/api/soldiers/" + s1.getId() + "/faces").file(jpeg())
                        .header("Authorization", "Bearer " + leader))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));

        giveConsent(leader, s1);
        giveConsent(leader, s2);

        faceReturns(face(0, 0.95f, 150), face(1, 0.95f, 150));
        mvc.perform(multipart("/api/soldiers/" + s1.getId() + "/faces").file(jpeg())
                        .header("Authorization", "Bearer " + leader))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MULTIPLE_FACES"));

        faceReturns(face(0, 0.95f, 40));
        mvc.perform(multipart("/api/soldiers/" + s1.getId() + "/faces").file(jpeg())
                        .header("Authorization", "Bearer " + leader))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FACE_TOO_SMALL"));

        faceReturns(face(0, 0.95f, 150));
        mvc.perform(multipart("/api/soldiers/" + s1.getId() + "/faces").file(jpeg())
                        .header("Authorization", "Bearer " + leader))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enrolledFaces").value(1));

        // The same face cannot be enrolled for another soldier.
        mvc.perform(multipart("/api/soldiers/" + s2.getId() + "/faces").file(jpeg())
                        .header("Authorization", "Bearer " + leader))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_IDENTITY"));

        // Embeddings are stored encrypted, never as raw floats or images.
        byte[] blob = jdbc.queryForObject("select embedding_enc from face_embedding limit 1", byte[].class);
        assertThat(blob).hasSize(12 + 512 * 4 + 16);

        // Deactivation erases biometrics immediately.
        mvc.perform(post("/api/soldiers/" + s1.getId() + "/deactivate").header("Authorization", "Bearer " + leader))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from face_embedding", Integer.class)).isZero();
    }

    // ------------------------------------------------------------------ full attendance cycle

    @Test
    void fullCycleCameraManualCloseApproveReportExport() throws Exception {
        String leader = login("leader1");
        for (Soldier s : List.of(s1, s2, s3)) {
            giveConsent(leader, s);
        }
        enroll(leader, s1, 0);
        enroll(leader, s2, 1);
        enroll(leader, s3, 2);

        String senior = login("senior1");
        long sessionId = om.readTree(mvc.perform(post("/api/sessions").header("Authorization", "Bearer " + senior)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platoonId\":" + p1.getId() + ",\"sessionType\":\"MORNING_PARADE\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        // A second open session for the same platoon is refused.
        mvc.perform(post("/api/sessions").header("Authorization", "Bearer " + senior)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platoonId\":" + p1.getId() + ",\"sessionType\":\"TRAINING\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_ALREADY_OPEN"));

        // Group photo: soldiers 1 & 2, a stranger, and a blurry face.
        faceReturns(face(0, 0.95f, 120), face(1, 0.9f, 110), face(100, 0.9f, 100), face(2, 0.3f, 60));
        mvc.perform(multipart("/api/sessions/" + sessionId + "/recognize").file(jpeg())
                        .header("Authorization", "Bearer " + senior))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detected").value(4))
                .andExpect(jsonPath("$.recognized").value(2))
                .andExpect(jsonPath("$.unknown").value(1))
                .andExpect(jsonPath("$.ignoredLowQuality").value(1))
                .andExpect(jsonPath("$.remaining").value(1));

        // Same photo again: nobody is counted twice.
        mvc.perform(multipart("/api/sessions/" + sessionId + "/recognize").file(jpeg())
                        .header("Authorization", "Bearer " + senior))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recognized").value(0))
                .andExpect(jsonPath("$.alreadyRecorded").value(2));

        // Manual: soldier 2 was actually late; a soldier of another platoon is refused.
        mvc.perform(post("/api/sessions/" + sessionId + "/manual").header("Authorization", "Bearer " + senior)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"soldierId\":" + s2.getId() + ",\"status\":\"LATE\",\"note\":\"تأخر 5 دقائق\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("MANUAL"));
        Soldier outsider = soldier("2001", "جندي فصيل آخر", p2);
        mvc.perform(post("/api/sessions/" + sessionId + "/manual").header("Authorization", "Bearer " + senior)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"soldierId\":" + outsider.getId() + ",\"status\":\"PRESENT\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SOLDIER_NOT_IN_PLATOON"));

        // Close: soldier 3 becomes absent automatically.
        mvc.perform(post("/api/sessions/" + sessionId + "/close").header("Authorization", "Bearer " + senior))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.counts.present").value(1))
                .andExpect(jsonPath("$.counts.late").value(1))
                .andExpect(jsonPath("$.counts.absent").value(1));

        // Closed sessions can no longer be edited by the senior.
        mvc.perform(post("/api/sessions/" + sessionId + "/manual").header("Authorization", "Bearer " + senior)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"soldierId\":" + s3.getId() + ",\"status\":\"PRESENT\"}"))
                .andExpect(status().isConflict());
        // Seniors cannot approve.
        mvc.perform(post("/api/sessions/" + sessionId + "/approve").header("Authorization", "Bearer " + senior)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        // Leader approves and excuses soldier 3.
        mvc.perform(post("/api/sessions/" + sessionId + "/approve").header("Authorization", "Bearer " + leader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"changes\":[{\"soldierId\":" + s3.getId()
                                + ",\"status\":\"EXCUSED\",\"note\":\"إجازة مرضية\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.counts.excused").value(1))
                .andExpect(jsonPath("$.counts.absent").value(0));

        // Live dashboard for the general senior.
        mvc.perform(get("/api/dashboard/live").header("Authorization", "Bearer " + login("general")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platoons.length()").value(2))
                .andExpect(jsonPath("$.kpis.present").value(1));

        // Monthly report: rates exclude excused records.
        mvc.perform(get("/api/reports/monthly?platoonId=" + p1.getId()).header("Authorization", "Bearer " + leader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.records").value(3))
                .andExpect(jsonPath("$.totals.attendanceRate").value(100.0))
                .andExpect(jsonPath("$.totals.disciplineRate").value(50.0))
                .andExpect(jsonPath("$.soldiers.length()").value(3));

        // Exports.
        byte[] pdf = mvc.perform(get("/api/reports/monthly/export?format=pdf&platoonId=" + p1.getId())
                        .header("Authorization", "Bearer " + leader))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        byte[] xlsx = mvc.perform(get("/api/reports/daily/export?format=xlsx").header("Authorization", "Bearer " + leader))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(xlsx[0]).isEqualTo((byte) 'P');
        assertThat(xlsx[1]).isEqualTo((byte) 'K');

        // Every step is in the audit log.
        List<String> actions = jdbc.queryForList("select distinct action from audit_log", String.class);
        assertThat(actions).contains("SESSION_OPENED", "FACE_RECOGNITION", "MANUAL_MARK", "SESSION_CLOSED",
                "SESSION_APPROVED", "RECORD_REVIEWED", "FACE_ENROLLED", "REPORT_EXPORTED", "LOGIN_SUCCESS");
    }

    @Test
    void auditLogIsAppendOnlyAtTheDatabaseLevel() {
        jdbc.update("insert into audit_log(action) values ('TEST')");
        assertThatThrownBy(() -> jdbc.update("update audit_log set action = 'X'"))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from audit_log"))
                .hasMessageContaining("cannot be deleted");
    }

    @Test
    void faceServiceOutageReturns503SoTheUiCanFallBackToManual() throws Exception {
        String senior = login("senior1");
        long sessionId = om.readTree(mvc.perform(post("/api/sessions").header("Authorization", "Bearer " + senior)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"platoonId\":" + p1.getId() + ",\"sessionType\":\"SHIFT\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        when(faceClient.embed(any(), anyString())).thenThrow(new om.mtc.attendance.web.error.ApiException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "FACE_SERVICE_UNAVAILABLE", "down"));
        mvc.perform(multipart("/api/sessions/" + sessionId + "/recognize").file(jpeg())
                        .header("Authorization", "Bearer " + senior))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("FACE_SERVICE_UNAVAILABLE"));
        // Manual attendance still works.
        mvc.perform(post("/api/sessions/" + sessionId + "/manual").header("Authorization", "Bearer " + senior)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"soldierId\":" + s1.getId() + ",\"status\":\"PRESENT\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void uploadsThatAreNotImagesAreRejectedBeforeReachingTheFaceService() throws Exception {
        String senior = login("senior1");
        long sessionId = om.readTree(mvc.perform(post("/api/sessions").header("Authorization", "Bearer " + senior)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"platoonId\":" + p1.getId() + ",\"sessionType\":\"OTHER\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        MockMultipartFile evil = new MockMultipartFile("image", "x.jpg", "image/jpeg",
                "<script>alert(1)</script>".getBytes());
        mvc.perform(multipart("/api/sessions/" + sessionId + "/recognize").file(evil)
                        .header("Authorization", "Bearer " + senior))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FORMAT"));
    }

    @Test
    void validationErrorsAreReportedPerField() throws Exception {
        mvc.perform(post("/api/soldiers").header("Authorization", "Bearer " + login("agent"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"militaryNumber\":\"' OR 1=1 --\",\"fullName\":\"\",\"militaryRank\":\"جندي\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.militaryNumber").exists())
                .andExpect(jsonPath("$.fieldErrors.fullName").exists())
                .andExpect(jsonPath("$.fieldErrors.platoonId").exists());
    }

    // ------------------------------------------------------------------ profile photos

    @Test
    void profilePhotosAreEncryptedScopedAndAttendanceCapturesAreNeverStored() throws Exception {
        String leader1 = login("leader1");
        String senior1 = login("senior1");
        String senior2 = login("senior2");
        String agent = login("agent");
        giveConsent(leader1, s1);

        // Photo + face enrollment in one step: stored as a small square JPEG, encrypted at rest.
        faceReturns(face(5, 0.95f, 150));
        mvc.perform(multipart(HttpMethod.PUT, "/api/soldiers/" + s1.getId() + "/photo").file(realJpeg())
                        .param("enroll", "true").header("Authorization", "Bearer " + leader1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soldier.photoVersion").isNumber())
                .andExpect(jsonPath("$.enrollment.enrolledFaces").value(1));
        byte[] stored = jdbc.queryForObject("select content_enc from profile_photo where soldier_id = ?",
                byte[].class, s1.getId());
        assertThat(stored).isNotEmpty();
        assertThat(stored[0] == (byte) 0xFF && stored[1] == (byte) 0xD8).as("not plain JPEG at rest").isFalse();

        MvcResult img = mvc.perform(get("/api/soldiers/" + s1.getId() + "/photo")
                        .header("Authorization", "Bearer " + senior1))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType()).isEqualTo("image/jpeg"))
                .andReturn();
        java.awt.image.BufferedImage decoded = javax.imageio.ImageIO.read(
                new java.io.ByteArrayInputStream(img.getResponse().getContentAsByteArray()));
        assertThat(decoded.getWidth()).isEqualTo(384);
        assertThat(decoded.getHeight()).isEqualTo(384);

        // A senior of another platoon can neither read it nor change it; a senior cannot change photos at all.
        mvc.perform(get("/api/soldiers/" + s1.getId() + "/photo").header("Authorization", "Bearer " + senior2))
                .andExpect(status().isForbidden());
        mvc.perform(multipart(HttpMethod.PUT, "/api/soldiers/" + s1.getId() + "/photo").file(realJpeg())
                        .header("Authorization", "Bearer " + senior1))
                .andExpect(status().isForbidden());

        // A file that is not a decodable image is refused and nothing is stored for a second soldier.
        mvc.perform(multipart(HttpMethod.PUT, "/api/soldiers/" + s2.getId() + "/photo").file(jpeg())
                        .header("Authorization", "Bearer " + leader1))
                .andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("select count(*) from profile_photo", Integer.class)).isEqualTo(1);

        // Taking attendance by camera never stores the captured image.
        Integer before = jdbc.queryForObject("select count(*) from profile_photo", Integer.class);
        MvcResult opened = mvc.perform(post("/api/sessions").header("Authorization", "Bearer " + senior1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platoonId\":" + p1.getId() + ",\"sessionType\":\"MORNING_PARADE\"}"))
                .andExpect(status().isCreated()).andReturn();
        long sessionId = om.readTree(opened.getResponse().getContentAsString()).get("id").asLong();
        faceReturns(face(5, 0.95f, 150));
        mvc.perform(multipart("/api/sessions/" + sessionId + "/recognize").file(realJpeg())
                        .header("Authorization", "Bearer " + senior1))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from profile_photo", Integer.class)).isEqualTo(before);

        // User photos: the user himself or the company agent; nobody else.
        Long senior1Id = users.findByUsernameIgnoreCase("senior1").orElseThrow().getId();
        mvc.perform(multipart(HttpMethod.PUT, "/api/users/" + senior1Id + "/photo").file(realJpeg())
                        .header("Authorization", "Bearer " + senior1))
                .andExpect(status().isOk()).andExpect(jsonPath("$.photoVersion").isNumber());
        mvc.perform(get("/api/users/" + senior1Id + "/photo").header("Authorization", "Bearer " + agent))
                .andExpect(status().isOk());
        mvc.perform(get("/api/users/" + senior1Id + "/photo").header("Authorization", "Bearer " + senior2))
                .andExpect(status().isForbidden());

        // Deactivating a soldier erases both templates and photo.
        mvc.perform(post("/api/soldiers/" + s1.getId() + "/deactivate").header("Authorization", "Bearer " + leader1))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from profile_photo where soldier_id = ?", Integer.class,
                s1.getId())).isZero();
    }

    // ------------------------------------------------------------------ helpers

    private String login(String username) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn();
        JsonNode n = om.readTree(r.getResponse().getContentAsString());
        return n.get("accessToken").asText();
    }

    private void giveConsent(String token, Soldier s) throws Exception {
        Map<String, Object> body = Map.of("militaryNumber", s.getMilitaryNumber(), "fullName", s.getFullName(),
                "militaryRank", s.getMilitaryRank(), "platoonId", s.getPlatoon().getId(), "biometricConsent", true);
        mvc.perform(put("/api/soldiers/" + s.getId()).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    private void enroll(String token, Soldier s, int axis) throws Exception {
        faceReturns(face(axis, 0.95f, 150));
        mvc.perform(multipart("/api/soldiers/" + s.getId() + "/faces").file(jpeg())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
    }

    private void faceReturns(FaceClient.DetectedFace... faces) {
        when(faceClient.embed(any(), anyString()))
                .thenReturn(new FaceClient.EmbedResponse("test-model", 1920, 1080, 42, List.of(faces)));
    }

    /** A synthetic face whose embedding is a unit vector on the given axis (distinct axes = distinct people). */
    private static FaceClient.DetectedFace face(int axis, float det, int size) {
        float[] e = new float[512];
        e[axis] = 1f;
        return new FaceClient.DetectedFace(new int[]{10, 10, 10 + size, 10 + size}, det, size, size, e);
    }

    /** A genuine, decodable JPEG (the fake one below is only good for mocked face-service calls). */
    private static MockMultipartFile realJpeg() {
        try {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(600, 400, java.awt.image.BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < 600; x++) {
                for (int y = 0; y < 400; y++) {
                    img.setRGB(x, y, new java.awt.Color(x * 255 / 600, y * 255 / 400, 120).getRGB());
                }
            }
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "jpg", bos);
            return new MockMultipartFile("image", "face.jpg", "image/jpeg", bos.toByteArray());
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static MockMultipartFile jpeg() {
        byte[] b = new byte[64];
        b[0] = (byte) 0xFF;
        b[1] = (byte) 0xD8;
        b[2] = (byte) 0xFF;
        return new MockMultipartFile("image", "parade.jpg", "image/jpeg", b);
    }

    private Platoon platoon(String code, String name) {
        Platoon p = new Platoon();
        p.setCode(code);
        p.setName(name);
        return platoons.save(p);
    }

    private void user(String username, Role role, Platoon p, boolean mustChange) {
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setFullName("مستخدم " + username);
        u.setRole(role);
        u.setPlatoon(p);
        u.setPasswordHash(encoder.encode(PW));
        u.setMustChangePassword(mustChange);
        users.save(u);
    }

    private Soldier soldier(String number, String name, Platoon p) {
        Soldier s = new Soldier();
        s.setMilitaryNumber(number);
        s.setFullName(name);
        s.setMilitaryRank("جندي");
        s.setPlatoon(p);
        return soldiers.save(s);
    }
}
