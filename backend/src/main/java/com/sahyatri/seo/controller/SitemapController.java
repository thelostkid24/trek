package com.sahyatri.seo.controller;

import com.sahyatri.seo.service.SitemapService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Contract: docs/TRD.md §7.16. The sitemap is referenced from the site's robots.txt, since the site and the API are on
 * different hosts. The API host's own robots.txt lets crawlers fetch only the sitemap and public photos.
 */
@RestController
public class SitemapController {

    private final SitemapService sitemap;

    public SitemapController(SitemapService sitemap) {
        this.sitemap = sitemap;
    }

    @GetMapping("/api/public/sitemap.xml")
    public ResponseEntity<String> sitemap() {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(sitemap.sitemap());
    }

    @GetMapping("/robots.txt")
    public ResponseEntity<String> robots() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .body("""
                        User-agent: *
                        Allow: /api/public/sitemap.xml
                        Allow: /api/public/files/
                        Disallow: /
                        """);
    }
}
