package com.sahyatri.savedguide.controller;

import com.sahyatri.common.web.ItemsResponse;
import com.sahyatri.savedguide.dto.SavedGuideItem;
import com.sahyatri.savedguide.service.SavedGuideService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Contract: docs/TRD.md §7.21.7. TREKKER only (/api/trekker/**); the trekker is the token's subject. */
@RestController
@RequestMapping("/api/trekker/saved-guides")
public class SavedGuideController {

    private final SavedGuideService saved;

    public SavedGuideController(SavedGuideService saved) {
        this.saved = saved;
    }

    @GetMapping
    public ItemsResponse<SavedGuideItem> list(@AuthenticationPrincipal Jwt jwt) {
        return new ItemsResponse<>(saved.list(userId(jwt)));
    }

    @PutMapping("/{guideId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID guideId) {
        saved.save(userId(jwt), guideId);
    }

    @DeleteMapping("/{guideId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID guideId) {
        saved.remove(userId(jwt), guideId);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
