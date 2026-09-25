package com.sahyatri.auth.dto;

import com.sahyatri.common.acquisition.AcquisitionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record GoogleRequest(@NotBlank String idToken, @Valid AcquisitionRequest acquisition) {
}
