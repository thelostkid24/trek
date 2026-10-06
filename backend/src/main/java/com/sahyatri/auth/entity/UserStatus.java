package com.sahyatri.auth.entity;

public enum UserStatus {
    ACTIVE, DISABLED,
    /** Erased by its owner (§7.18). The row stays for the bookings, payments and reviews that point at it. */
    DELETED
}
