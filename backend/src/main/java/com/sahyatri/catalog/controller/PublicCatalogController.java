package com.sahyatri.catalog.controller;

import com.sahyatri.catalog.dto.DepartureDetail;
import com.sahyatri.catalog.dto.DepartureSummary;
import com.sahyatri.catalog.service.CatalogService;
import com.sahyatri.common.web.ItemsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Contract: docs/TRD.md §7.5. */
@RestController
@RequestMapping("/api/public/departures")
public class PublicCatalogController {

    private final CatalogService catalog;

    public PublicCatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public ItemsResponse<DepartureSummary> list(@RequestParam(required = false) String month,
                                                @RequestParam(required = false) String difficulty) {
        return new ItemsResponse<>(catalog.list(month, difficulty));
    }

    @GetMapping("/{id}")
    public DepartureDetail get(@PathVariable UUID id) {
        return catalog.get(id);
    }
}
