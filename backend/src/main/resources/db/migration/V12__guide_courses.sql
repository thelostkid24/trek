-- The two IMF-recognised mountaineering courses, each with the institute that ran it and the certificate number.
-- See docs/TRD.md §6 and §7.12. The existing certification columns stay for any other certificate.
ALTER TABLE guide_profiles
    ADD COLUMN bmc_institute          TEXT,
    ADD COLUMN bmc_certificate_number TEXT,
    ADD COLUMN amc_institute          TEXT,
    ADD COLUMN amc_certificate_number TEXT;
