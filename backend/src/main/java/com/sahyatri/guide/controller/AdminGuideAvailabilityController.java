package com.sahyatri.guide.controller;

import com.sahyatri.common.web.ItemsResponse;
import com.sahyatri.guide.dto.GuideAvailabilityItem;
import com.sahyatri.guide.service.GuideAvailabilityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Contract: docs/TRD.md §7.21.7. ADMIN only (/api/admin/**). */
@RestController
@RequestMapping("/api/admin/guides/availability")
public class AdminGuideAvailabilityController {

    private final GuideAvailabilityService availability;

    public AdminGuideAvailabilityController(GuideAvailabilityService availability) {
        this.availability = availability;
    }

    @GetMapping
    public ItemsResponse<GuideAvailabilityItem> inWindow(@RequestParam(required = false) LocalDate from,
                                                          @RequestParam(required = false) LocalDate to) {
        return new ItemsResponse<>(availability.inWindow(from, to));
    }
}
