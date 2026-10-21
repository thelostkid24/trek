package com.sahyatri.guide.dto;

/** EXPIRED once past valid-to, whether or not it was verified; otherwise VERIFIED or PENDING (an admin's check). */
public enum CertificationStatus {
    VERIFIED,
    EXPIRED,
    PENDING
}
