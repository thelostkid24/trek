package com.sahyatri.guide.dto;

/**
 * EXPIRED once past valid-to, whether or not it was verified; otherwise VERIFIED (an admin's check), IN_COURSE (the
 * guide is still doing it) or PENDING.
 */
public enum CertificationStatus {
    VERIFIED,
    EXPIRED,
    IN_COURSE,
    PENDING
}
