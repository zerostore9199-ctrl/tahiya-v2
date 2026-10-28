-- =====================================================================
-- V2 — profile photos for soldiers and users + clean-up of demo labels
-- =====================================================================
-- Only the *profile* photo is stored (a cropped 384px JPEG, encrypted with AES-256-GCM).
-- Photos captured during attendance taking are never stored.
CREATE TABLE profile_photo (
    id           BIGSERIAL PRIMARY KEY,
    soldier_id   BIGINT UNIQUE REFERENCES soldier(id)  ON DELETE CASCADE,
    user_id      BIGINT UNIQUE REFERENCES app_user(id) ON DELETE CASCADE,
    content_enc  BYTEA        NOT NULL,
    content_type VARCHAR(30)  NOT NULL DEFAULT 'image/jpeg',
    size_bytes   INT          NOT NULL,
    uploaded_by  BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_photo_one_owner CHECK ((soldier_id IS NOT NULL)::int + (user_id IS NOT NULL)::int = 1)
);

-- Version stamp kept next to the owner so lists can show "has photo" and cache-bust
-- without ever loading the image bytes.
ALTER TABLE soldier  ADD COLUMN photo_updated_at TIMESTAMPTZ;
ALTER TABLE app_user ADD COLUMN photo_updated_at TIMESTAMPTZ;

-- Earlier builds seeded demo users with a "— تجريبي" suffix in their names.
UPDATE app_user SET full_name = btrim(replace(full_name, '— تجريبي', '')) WHERE full_name LIKE '%— تجريبي%';
