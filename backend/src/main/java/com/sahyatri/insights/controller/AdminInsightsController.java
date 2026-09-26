package com.sahyatri.insights.controller;

import com.sahyatri.insights.dto.InsightsResponse;
import com.sahyatri.insights.service.InsightsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Contract: docs/TRD.md §7.15. ADMIN role enforced by SecurityConfig (/api/admin/**). */
@RestController
@RequestMapping("/api/admin/insights")
public class AdminInsightsController {

    private final InsightsService insights;

    public AdminInsightsController(InsightsService insights) {
        this.insights = insights;
    }

    @GetMapping
    public InsightsResponse get(@RequestParam(defaultValue = "30") int days) {
        return insights.get(days);
    }
}
