package com.sahyatri.guide.controller;

import com.sahyatri.guide.dto.GuideRecord;
import com.sahyatri.guide.dto.GuideRecordRequest;
import com.sahyatri.guide.service.GuideRecordService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Contract: docs/TRD.md §7.21.8. ADMIN only (/api/admin/**). */
@RestController
@RequestMapping("/api/admin/guides/{id}/record")
public class AdminGuideRecordController {

    private final GuideRecordService record;

    public AdminGuideRecordController(GuideRecordService record) {
        this.record = record;
    }

    @GetMapping
    public GuideRecord get(@PathVariable UUID id) {
        return record.get(id);
    }

    @PutMapping
    public GuideRecord update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                              @Valid @RequestBody GuideRecordRequest req) {
        return record.update(UUID.fromString(jwt.getSubject()), id, req);
    }
}
