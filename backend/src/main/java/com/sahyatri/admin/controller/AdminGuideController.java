package com.sahyatri.admin.controller;

import com.sahyatri.admin.dto.GuideResponse;
import com.sahyatri.admin.dto.PromoteGuideRequest;
import com.sahyatri.admin.service.GuideAdminService;
import com.sahyatri.common.web.ItemsResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Contract: docs/TRD.md §7.5. ADMIN role enforced by SecurityConfig (/api/admin/**). */
@RestController
@RequestMapping("/api/admin/guides")
public class AdminGuideController {

    private final GuideAdminService guides;

    public AdminGuideController(GuideAdminService guides) {
        this.guides = guides;
    }

    @GetMapping
    public ItemsResponse<GuideResponse> list() {
        return new ItemsResponse<>(guides.list());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GuideResponse promote(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PromoteGuideRequest req) {
        return guides.promote(UUID.fromString(jwt.getSubject()), req.email());
    }
}
