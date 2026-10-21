package com.sahyatri.catalog.controller;

import com.sahyatri.catalog.dto.CatalogTrek;
import com.sahyatri.catalog.dto.GuideListItem;
import com.sahyatri.catalog.dto.GuideProfile;
import com.sahyatri.catalog.dto.TrekPage;
import com.sahyatri.catalog.service.CatalogService;
import com.sahyatri.common.web.ItemsResponse;
import com.sahyatri.guide.service.GuideSlugService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Contract: docs/TRD.md §7.7 (catalog §7.9, guides §7.17). Public trek catalog, trek and guide pages. */
@RestController
@RequestMapping("/api/public")
public class PublicTrekController {

    private final CatalogService catalog;
    private final GuideSlugService slugs;

    public PublicTrekController(CatalogService catalog, GuideSlugService slugs) {
        this.catalog = catalog;
        this.slugs = slugs;
    }

    @GetMapping("/tracks")
    public ItemsResponse<CatalogTrek> catalog() {
        return new ItemsResponse<>(catalog.catalog());
    }

    @GetMapping("/tracks/{slug}")
    public TrekPage trek(@PathVariable String slug) {
        return catalog.trek(slug);
    }

    @GetMapping("/guides")
    public ItemsResponse<GuideListItem> guides() {
        return new ItemsResponse<>(catalog.guides());
    }

    /** {@code key}: the guide's id, slug or a slug they used before (§7.21). */
    @GetMapping("/guides/{key}")
    public GuideProfile guide(@PathVariable String key) {
        return catalog.guide(slugs.resolve(key).orElseThrow(CatalogService::guideNotFound));
    }
}
