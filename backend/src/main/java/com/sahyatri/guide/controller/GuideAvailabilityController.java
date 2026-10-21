package com.sahyatri.guide.controller;

import com.sahyatri.common.web.ItemsResponse;
import com.sahyatri.guide.dto.AvailabilityRange;
import com.sahyatri.guide.dto.AvailabilityRequest;
import com.sahyatri.guide.service.GuideAvailabilityService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Contract: docs/TRD.md §7.21.7. GUIDE only (/api/guide/**); the guide is the token's subject. */
@RestController
@RequestMapping("/api/guide/availability")
public class GuideAvailabilityController {

    private final GuideAvailabilityService availability;

    public GuideAvailabilityController(GuideAvailabilityService availability) {
        this.availability = availability;
    }

    @GetMapping
    public ItemsResponse<AvailabilityRange> list(@AuthenticationPrincipal Jwt jwt) {
        return new ItemsResponse<>(availability.list(userId(jwt)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AvailabilityRange create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AvailabilityRequest req) {
        return availability.create(userId(jwt), req);
    }

    @PutMapping("/{id}")
    public AvailabilityRange update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                    @Valid @RequestBody AvailabilityRequest req) {
        return availability.update(userId(jwt), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        availability.delete(userId(jwt), id);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
