package com.sahyatri.guide.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** The guide's line about a route they lead. */
public record RouteNote(@NotNull UUID trackId, @NotBlank @Size(max = 200) String note) {
}
