package com.sahyatri.guide.controller;

import com.sahyatri.guide.dto.OwnGuideProfile;
import com.sahyatri.guide.dto.OwnGuideProfileRequest;
import com.sahyatri.guide.service.OwnGuideProfileService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/** Contract: docs/TRD.md §7.21. GUIDE only (/api/guide/**); the guide is the token's subject. */
@RestController
@RequestMapping("/api/guide/profile")
public class OwnGuideProfileController {

    private final OwnGuideProfileService profiles;

    public OwnGuideProfileController(OwnGuideProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public OwnGuideProfile get(@AuthenticationPrincipal Jwt jwt) {
        return profiles.get(userId(jwt));
    }

    @PutMapping
    public OwnGuideProfile update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OwnGuideProfileRequest req) {
        return profiles.update(userId(jwt), req);
    }

    @PutMapping(path = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public OwnGuideProfile uploadPhoto(@AuthenticationPrincipal Jwt jwt, @RequestPart("file") MultipartFile file) {
        return profiles.uploadPhoto(userId(jwt), file);
    }

    @DeleteMapping("/avatar")
    public OwnGuideProfile removePhoto(@AuthenticationPrincipal Jwt jwt) {
        return profiles.removePhoto(userId(jwt));
    }

    @PutMapping(path = "/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public OwnGuideProfile uploadCover(@AuthenticationPrincipal Jwt jwt, @RequestPart("file") MultipartFile file) {
        return profiles.uploadCover(userId(jwt), file);
    }

    @DeleteMapping("/cover")
    public OwnGuideProfile removeCover(@AuthenticationPrincipal Jwt jwt) {
        return profiles.removeCover(userId(jwt));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
