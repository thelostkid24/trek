package com.sahyatri.guide.dto;

import jakarta.validation.constraints.Size;

/** An admin verifies or unverifies a certificate; the note goes to the audit log only. */
public record VerifyRequest(@Size(max = 500) String note) {
}
