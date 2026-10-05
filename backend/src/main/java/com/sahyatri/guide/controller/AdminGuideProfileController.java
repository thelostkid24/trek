package com.sahyatri.guide.controller;

import com.sahyatri.guide.dto.GuideProfileRequest;
import com.sahyatri.guide.dto.GuideProfileResponse;
import com.sahyatri.guide.service.GuideProfileService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/** Contract: docs/TRD.md §7.12. ADMIN only (/api/admin/**). */
@RestController
@RequestMapping("/api/admin/guides/{id}")
public class AdminGuideProfileController {

    private final GuideProfileService profiles;

    public AdminGuideProfileController(GuideProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/profile")
    public GuideProfileResponse get(@PathVariable UUID id) {
        return profiles.get(id);
    }

    @PutMapping("/profile")
    public GuideProfileResponse update(@PathVariable UUID id, @Valid @RequestBody GuideProfileRequest req) {
        return profiles.update(id, req);
    }

    @PutMapping(path = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public GuideProfileResponse uploadPhoto(@PathVariable UUID id, @RequestPart("file") MultipartFile file) {
        return profiles.uploadPhoto(id, file);
    }

    @DeleteMapping("/avatar")
    public GuideProfileResponse removePhoto(@PathVariable UUID id) {
        return profiles.removePhoto(id);
    }
}
