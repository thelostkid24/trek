package com.sahyatri.catalog.controller;

import com.sahyatri.catalog.service.CatalogService;
import com.sahyatri.catalog.service.GuideCardService;
import com.sahyatri.guide.service.GuideSlugService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** Contract: docs/TRD.md §7.21.2. The guide page's link-preview image ({@code og:image}). */
@RestController
public class GuideCardController {

    private final GuideCardService cards;
    private final GuideSlugService slugs;

    public GuideCardController(GuideCardService cards, GuideSlugService slugs) {
        this.cards = cards;
        this.slugs = slugs;
    }

    /** {@code key}: the guide's id, slug or a slug they used before. */
    @GetMapping("/api/public/guides/{key}/card.png")
    public ResponseEntity<byte[]> card(@PathVariable String key) {
        byte[] png = cards.card(slugs.resolve(key).orElseThrow(CatalogService::guideNotFound));
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(png);
    }
}
