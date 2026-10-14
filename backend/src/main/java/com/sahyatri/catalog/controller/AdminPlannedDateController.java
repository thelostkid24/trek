package com.sahyatri.catalog.controller;

import com.sahyatri.catalog.dto.PlannedDate;
import com.sahyatri.catalog.dto.PlannedDateListRequest;
import com.sahyatri.catalog.service.PlannedDateService;
import com.sahyatri.common.web.ItemsResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Contract: docs/TRD.md §7.20. A track's planned dates. ADMIN only (/api/admin/**). */
@RestController
@RequestMapping("/api/admin/tracks/{id}/planned-dates")
public class AdminPlannedDateController {

    private final PlannedDateService dates;

    public AdminPlannedDateController(PlannedDateService dates) {
        this.dates = dates;
    }

    @GetMapping
    public ItemsResponse<PlannedDate> list(@PathVariable UUID id) {
        return new ItemsResponse<>(dates.all(id));
    }

    @PutMapping
    public ItemsResponse<PlannedDate> replace(@PathVariable UUID id, @Valid @RequestBody PlannedDateListRequest req) {
        return new ItemsResponse<>(dates.replace(id, req.items()));
    }
}
