-- Guide certifications (docs/TRD.md §6.20, §7.21.3): one row per course or certificate, entered by the guide and
-- verified by an admin against the original document. Tiers are worked out at read time and never stored (law 8).

CREATE TABLE guide_certifications (
    id           UUID PRIMARY KEY,
    guide_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type         TEXT        NOT NULL CHECK (type IN ('BMC', 'AMC', 'SEARCH_RESCUE', 'MOI',
                                                      'WILDERNESS_FIRST_RESPONDER', 'FIRST_AID',
                                                      'POLICE_VERIFICATION', 'INSURANCE', 'OTHER')),
    title        TEXT CHECK (length(title) BETWEEN 1 AND 120),
    issuer       TEXT CHECK (length(issuer) BETWEEN 1 AND 160),
    year         INT CHECK (year BETWEEN 1950 AND 2100),
    valid_to     DATE,
    number       TEXT CHECK (length(number) BETWEEN 1 AND 60),
    -- Private: guide-certificates/<uuid>.pdf|.jpg in storage, never served publicly.
    document_key TEXT,
    verified_at  TIMESTAMPTZ,
    verified_by  UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT guide_certifications_other_title CHECK (type <> 'OTHER' OR title IS NOT NULL),
    CONSTRAINT guide_certifications_verifier CHECK (verified_at IS NOT NULL OR verified_by IS NULL)
);

CREATE INDEX guide_certifications_guide_idx ON guide_certifications (guide_id, created_at);

-- Copy: the §7.12 course columns become rows. Admins entered them, so they count as verified (by nobody in
-- particular). The old columns stay until a later migration drops them. Safe to run twice.
INSERT INTO guide_certifications (id, guide_id, type, issuer, number, verified_at, created_at, updated_at)
SELECT gen_random_uuid(), gp.user_id, 'BMC', NULLIF(left(trim(gp.bmc_institute), 160), ''),
       NULLIF(left(trim(gp.bmc_certificate_number), 60), ''), now(), now(), now()
FROM guide_profiles gp
WHERE (NULLIF(trim(gp.bmc_institute), '') IS NOT NULL OR NULLIF(trim(gp.bmc_certificate_number), '') IS NOT NULL)
  AND NOT EXISTS (SELECT 1 FROM guide_certifications c WHERE c.guide_id = gp.user_id AND c.type = 'BMC');

INSERT INTO guide_certifications (id, guide_id, type, issuer, number, verified_at, created_at, updated_at)
SELECT gen_random_uuid(), gp.user_id, 'AMC', NULLIF(left(trim(gp.amc_institute), 160), ''),
       NULLIF(left(trim(gp.amc_certificate_number), 60), ''), now(), now(), now()
FROM guide_profiles gp
WHERE (NULLIF(trim(gp.amc_institute), '') IS NOT NULL OR NULLIF(trim(gp.amc_certificate_number), '') IS NOT NULL)
  AND NOT EXISTS (SELECT 1 FROM guide_certifications c WHERE c.guide_id = gp.user_id AND c.type = 'AMC');

INSERT INTO guide_certifications (id, guide_id, type, title, number, verified_at, created_at, updated_at)
SELECT gen_random_uuid(), gp.user_id, 'OTHER', left(trim(gp.certification), 120),
       NULLIF(left(trim(gp.certification_number), 60), ''), now(), now(), now()
FROM guide_profiles gp
WHERE NULLIF(trim(gp.certification), '') IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM guide_certifications c WHERE c.guide_id = gp.user_id AND c.type = 'OTHER');
