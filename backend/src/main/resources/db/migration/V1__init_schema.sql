-- =====================================================================
-- النظام العسكري الذكي لإدارة الحضور والانضباط
-- Military Smart Attendance and Discipline System — initial schema
-- =====================================================================

-- ---------- Platoons (الفصائل) ----------
CREATE TABLE platoon (
    id            BIGSERIAL PRIMARY KEY,
    code          VARCHAR(20)  NOT NULL UNIQUE,
    name          VARCHAR(100) NOT NULL,
    company_name  VARCHAR(100) NOT NULL DEFAULT 'السرية',
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------- Users (المستخدمون) ----------
CREATE TABLE app_user (
    id                    BIGSERIAL PRIMARY KEY,
    username              VARCHAR(50)  NOT NULL UNIQUE,
    password_hash         VARCHAR(100) NOT NULL,
    full_name             VARCHAR(120) NOT NULL,
    military_rank         VARCHAR(50),
    role                  VARCHAR(30)  NOT NULL,
    platoon_id            BIGINT REFERENCES platoon(id) ON DELETE SET NULL,
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    must_change_password  BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_attempts       INT          NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    last_login_at         TIMESTAMPTZ,
    password_changed_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_user_role CHECK (role IN ('SENIOR','GENERAL_SENIOR','PLATOON_LEADER','COMPANY_AGENT')),
    -- Senior and platoon leader are always bound to one platoon
    CONSTRAINT chk_user_platoon CHECK (role NOT IN ('SENIOR','PLATOON_LEADER') OR platoon_id IS NOT NULL)
);
CREATE INDEX idx_user_platoon ON app_user(platoon_id);

-- ---------- Refresh tokens (rotating, stored hashed) ----------
CREATE TABLE refresh_token (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    token_hash   VARCHAR(64)  NOT NULL UNIQUE,
    expires_at   TIMESTAMPTZ  NOT NULL,
    revoked      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_ip   VARCHAR(64)
);
CREATE INDEX idx_refresh_user ON refresh_token(user_id);

-- ---------- Soldiers (الجنود) ----------
CREATE TABLE soldier (
    id                 BIGSERIAL PRIMARY KEY,
    military_number    VARCHAR(30)  NOT NULL UNIQUE,
    full_name          VARCHAR(120) NOT NULL,
    military_rank      VARCHAR(50)  NOT NULL,
    platoon_id         BIGINT       NOT NULL REFERENCES platoon(id),
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    biometric_consent  BOOLEAN      NOT NULL DEFAULT FALSE,
    consent_at         TIMESTAMPTZ,
    consent_by         BIGINT REFERENCES app_user(id),
    deactivated_at     TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_soldier_platoon_active ON soldier(platoon_id, active);

-- ---------- Face embeddings (البصمات الرقمية — AES-256-GCM encrypted, no images) ----------
CREATE TABLE face_embedding (
    id              BIGSERIAL PRIMARY KEY,
    soldier_id      BIGINT       NOT NULL REFERENCES soldier(id) ON DELETE CASCADE,
    embedding_enc   BYTEA        NOT NULL,
    model_name      VARCHAR(50)  NOT NULL,
    det_score       REAL         NOT NULL,
    created_by      BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_face_soldier ON face_embedding(soldier_id);

-- ---------- Attendance sessions (جلسات التحضير) ----------
CREATE TABLE attendance_session (
    id             BIGSERIAL PRIMARY KEY,
    platoon_id     BIGINT       NOT NULL REFERENCES platoon(id),
    session_type   VARCHAR(30)  NOT NULL,
    title          VARCHAR(120),
    session_date   DATE         NOT NULL,
    scheduled_at   TIMESTAMPTZ,
    status         VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    opened_by      BIGINT       NOT NULL REFERENCES app_user(id),
    opened_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    closed_by      BIGINT REFERENCES app_user(id),
    closed_at      TIMESTAMPTZ,
    approved_by    BIGINT REFERENCES app_user(id),
    approved_at    TIMESTAMPTZ,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_session_status CHECK (status IN ('OPEN','CLOSED','APPROVED')),
    CONSTRAINT chk_session_type CHECK (session_type IN ('MORNING_PARADE','TRAINING','SHIFT','EVENING_PARADE','OTHER'))
);
-- Only one open session per platoon at a time
CREATE UNIQUE INDEX uq_one_open_session_per_platoon ON attendance_session(platoon_id) WHERE status = 'OPEN';
CREATE INDEX idx_session_platoon_date ON attendance_session(platoon_id, session_date);
CREATE INDEX idx_session_status ON attendance_session(status);

-- ---------- Attendance records (سجلات الحضور) ----------
CREATE TABLE attendance_record (
    id            BIGSERIAL PRIMARY KEY,
    session_id    BIGINT       NOT NULL REFERENCES attendance_session(id) ON DELETE CASCADE,
    soldier_id    BIGINT       NOT NULL REFERENCES soldier(id),
    status        VARCHAR(20)  NOT NULL,
    method        VARCHAR(20)  NOT NULL,
    similarity    REAL,
    note          VARCHAR(500),
    recorded_by   BIGINT REFERENCES app_user(id),
    recorded_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by    BIGINT REFERENCES app_user(id),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_record_session_soldier UNIQUE (session_id, soldier_id),
    CONSTRAINT chk_record_status CHECK (status IN ('PRESENT','LATE','EXCUSED','ABSENT')),
    CONSTRAINT chk_record_method CHECK (method IN ('FACE','MANUAL','AUTO')),
    CONSTRAINT chk_similarity CHECK (similarity IS NULL OR (similarity >= -1 AND similarity <= 1))
);
CREATE INDEX idx_record_soldier ON attendance_record(soldier_id);

-- ---------- System settings (إعدادات النظام) ----------
CREATE TABLE system_setting (
    setting_key    VARCHAR(60)  PRIMARY KEY,
    setting_value  VARCHAR(200) NOT NULL,
    updated_by     BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO system_setting(setting_key, setting_value) VALUES
    ('recognition.threshold',      '0.45'),
    ('recognition.minDetScore',    '0.60'),
    ('enrollment.minDetScore',     '0.70'),
    ('enrollment.maxFaces',        '5'),
    ('enrollment.duplicateThreshold', '0.60'),
    ('attendance.lateGraceMinutes','10'),
    ('audit.retentionDays',        '730');

-- ---------- Audit log (سجل العمليات) — append-only ----------
CREATE TABLE audit_log (
    id           BIGSERIAL PRIMARY KEY,
    occurred_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    user_id      BIGINT,
    username     VARCHAR(50),
    action       VARCHAR(50)  NOT NULL,
    entity_type  VARCHAR(40),
    entity_id    VARCHAR(40),
    details      VARCHAR(1000),
    ip_address   VARCHAR(64),
    success      BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_audit_time ON audit_log(occurred_at DESC);
CREATE INDEX idx_audit_user ON audit_log(user_id);
CREATE INDEX idx_audit_action ON audit_log(action);

-- The application can never rewrite history: UPDATE is always rejected,
-- DELETE is only allowed for rows past the retention window (purge job).
CREATE OR REPLACE FUNCTION audit_log_protect() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'audit_log is append-only';
    END IF;
    IF TG_OP = 'DELETE' AND OLD.occurred_at > now() - INTERVAL '365 days' THEN
        RAISE EXCEPTION 'audit_log rows younger than 365 days cannot be deleted';
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_log_protect
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_protect();
