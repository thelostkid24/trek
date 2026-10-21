package com.sahyatri.guide.controller;

import com.sahyatri.guide.dto.GuideMediaItem;
import com.sahyatri.guide.dto.GuideMediaList;
import com.sahyatri.guide.dto.MediaCaptionRequest;
import com.sahyatri.guide.dto.MediaOrderRequest;
import com.sahyatri.guide.dto.YouTubeVideoRequest;
import com.sahyatri.guide.service.GuideMediaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/** Contract: docs/TRD.md §7.21.4. GUIDE only (/api/guide/**); the guide is the token's subject. */
@RestController
@RequestMapping("/api/guide/media")
public class GuideMediaController {

    private final GuideMediaService media;

    public GuideMediaController(GuideMediaService media) {
        this.media = media;
    }

    @GetMapping
    public GuideMediaList list(@AuthenticationPrincipal Jwt jwt) {
        return media.list(userId(jwt));
    }

    @PostMapping(path = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public GuideMediaItem uploadPhoto(@AuthenticationPrincipal Jwt jwt, @RequestPart("file") MultipartFile file,
                                      @RequestParam(required = false) String caption) {
        return media.uploadPhoto(userId(jwt), file, caption);
    }

    @PostMapping("/videos")
    @ResponseStatus(HttpStatus.CREATED)
    public GuideMediaItem addYouTube(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody YouTubeVideoRequest req) {
        return media.addYouTube(userId(jwt), req);
    }

    @PutMapping("/order")
    public GuideMediaList reorder(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody MediaOrderRequest req) {
        return media.reorder(userId(jwt), req);
    }

    @PutMapping("/{id}")
    public GuideMediaItem updateCaption(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                        @Valid @RequestBody MediaCaptionRequest req) {
        return media.updateCaption(userId(jwt), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        media.delete(userId(jwt), id);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
