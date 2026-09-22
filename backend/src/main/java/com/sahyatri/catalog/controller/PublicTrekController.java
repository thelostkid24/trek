package com.sahyatri.catalog.controller;

import com.sahyatri.catalog.dto.CatalogTrek;
import com.sahyatri.catalog.dto.GuideProfile;
import com.sahyatri.catalog.dto.TrekPage;
import com.sahyatri.catalog.service.CatalogService;
import com.sahyatri.common.web.ItemsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Contract: docs/TRD.md §7.7 (catalog §7.9). Public trek catalog, trek and guide pages. */
@RestController
@RequestMapping("/api/public")
public class PublicTrekController {

    private final CatalogService catalog;

    public PublicTrekController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/tracks")
    public ItemsResponse<CatalogTrek> catalog() {
        return new ItemsResponse<>(catalog.catalog());
    }

    @GetMapping("/tracks/{slug}")
    public TrekPage trek(@PathVariable String slug) {
        return catalog.trek(slug);
    }

    @GetMapping("/guides/{id}")
    public GuideProfile guide(@PathVariable UUID id) {
        return catalog.guide(id);
    }
}
