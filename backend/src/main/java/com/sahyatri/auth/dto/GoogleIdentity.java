package com.sahyatri.auth.dto;

/** Claims we use from a verified Google ID token. */
public record GoogleIdentity(String subject, String email, boolean emailVerified, String name) {
}
