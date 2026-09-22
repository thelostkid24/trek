package com.sahyatri.profile.controller;

import com.sahyatri.profile.dto.TrekkerProfileRequest;
import com.sahyatri.profile.dto.TrekkerProfileResponse;
import com.sahyatri.profile.service.TrekkerProfileService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Contract: docs/TRD.md §7.3. TREKKER role enforced by SecurityConfig (/api/trekker/**). */
@RestController
@RequestMapping("/api/trekker/profile")
public class TrekkerProfileController {

    private final TrekkerProfileService profiles;

    public TrekkerProfileController(TrekkerProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public TrekkerProfileResponse get(@AuthenticationPrincipal Jwt jwt) {
        return profiles.get(UUID.fromString(jwt.getSubject()));
    }

    @PutMapping
    public TrekkerProfileResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TrekkerProfileRequest req) {
        return profiles.update(UUID.fromString(jwt.getSubject()), req);
    }
}
