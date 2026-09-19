package com.sahyatri.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleRequest(@NotBlank String idToken) {
}
