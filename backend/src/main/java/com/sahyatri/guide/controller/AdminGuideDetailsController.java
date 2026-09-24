package com.sahyatri.guide.controller;

import com.sahyatri.guide.dto.GuideDetailsRequest;
import com.sahyatri.guide.dto.GuideDetailsResponse;
import com.sahyatri.guide.service.GuideDetailsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Contract: docs/TRD.md §7.12. ADMIN only (/api/admin/**). */
@RestController
@RequestMapping("/api/admin/guides/{id}/details")
public class AdminGuideDetailsController {

    private final GuideDetailsService details;

    public AdminGuideDetailsController(GuideDetailsService details) {
        this.details = details;
    }

    @GetMapping
    public GuideDetailsResponse get(@PathVariable UUID id) {
        return details.get(id);
    }

    @PutMapping
    public GuideDetailsResponse update(@PathVariable UUID id, @Valid @RequestBody GuideDetailsRequest req) {
        return details.update(id, req);
    }
}
