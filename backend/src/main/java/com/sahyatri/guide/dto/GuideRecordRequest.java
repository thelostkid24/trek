package com.sahyatri.guide.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sahyatri.guide.entity.PledgeKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Replaces a guide's record (§7.21.8). Null lists are empty: routes left out are removed. */
public record GuideRecordRequest(
        @JsonProperty("treks_last_3y") @Min(0) @Max(1000) Integer treksLast3y,
        @Size(max = 50) List<@NotNull @Valid RouteRequest> routes,
        @Size(max = 2) List<@NotNull PledgeKind> pledges,
        @Size(max = 7) List<@NotNull @Valid StatPick> stats) {
}
