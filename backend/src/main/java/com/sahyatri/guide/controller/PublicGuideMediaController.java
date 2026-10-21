package com.sahyatri.guide.controller;

import com.sahyatri.common.storage.GuideMediaFiles;
import com.sahyatri.guide.dto.GuideMediaList;
import com.sahyatri.guide.service.GuideMediaService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Contract: docs/TRD.md §7.21.4. The guide page's photos and videos. */
@RestController
public class PublicGuideMediaController {

    private final GuideMediaService media;

    public PublicGuideMediaController(GuideMediaService media) {
        this.media = media;
    }

    /** {@code key}: the guide's id, slug or a slug they used before. */
    @GetMapping("/api/public/guides/{key}/media")
    public GuideMediaList list(@PathVariable String key) {
        return media.publicList(key);
    }

    /** 302 to a short-lived storage URL, so the browser streams (and seeks in) the video from storage itself. */
    @GetMapping(GuideMediaFiles.VIDEO_PATH + "{id}")
    public ResponseEntity<Void> play(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, media.playUrl(id))
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
