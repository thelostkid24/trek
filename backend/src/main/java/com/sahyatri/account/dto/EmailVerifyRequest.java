package com.sahyatri.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailVerifyRequest(@NotBlank @Size(max = 200) String token) {
}
