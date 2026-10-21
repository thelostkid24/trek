package com.sahyatri.guide.entity;

/** Kinds of certificate a guide can hold (§7.21.3), in the order the page lists them. */
public enum CertificationType {
    /** Basic Mountaineering Course (IMF-recognised institute). */
    BMC,
    /** Advanced Mountaineering Course. */
    AMC,
    SEARCH_RESCUE,
    /** Method of Instruction course. */
    MOI,
    WILDERNESS_FIRST_RESPONDER,
    FIRST_AID,
    POLICE_VERIFICATION,
    INSURANCE,
    /** Anything else; needs a title. */
    OTHER;

    /** Certificate numbers are public only for the two mountaineering courses, as on the §7.12 page. */
    public boolean publicNumber() {
        return this == BMC || this == AMC;
    }
}
