package com.sahyatri.guide.controller;

import com.sahyatri.guide.dto.GuideMediaList;
import com.sahyatri.guide.service.GuideMediaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Contract: docs/TRD.md §7.21.4. The guide page's photos and YouTube videos. */
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
}
