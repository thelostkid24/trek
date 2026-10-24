package com.sahyatri.review.controller;

import com.sahyatri.common.web.ItemsResponse;
import com.sahyatri.review.dto.AdminReviewItem;
import com.sahyatri.review.dto.ReasonRequest;
import com.sahyatri.review.service.AdminReviewService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Contract: docs/TRD.md §7.21.5. ADMIN only. */
@RestController
@RequestMapping("/api/admin/reviews")
public class AdminReviewController {

    private final AdminReviewService reviews;

    public AdminReviewController(AdminReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping
    public ItemsResponse<AdminReviewItem> list(
            @RequestParam(defaultValue = "RECENT") AdminReviewService.View view) {
        return new ItemsResponse<>(reviews.list(view));
    }

    @PutMapping("/{id}/approved")
    public AdminReviewItem approve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return reviews.approve(userId(jwt), id);
    }

    @PutMapping("/{id}/hidden")
    public AdminReviewItem hide(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                @Valid @RequestBody ReasonRequest req) {
        return reviews.hide(userId(jwt), id, req.reason());
    }

    @DeleteMapping("/{id}/hidden")
    public AdminReviewItem unhide(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return reviews.unhide(userId(jwt), id);
    }

    @PutMapping("/{id}/reply/hidden")
    public AdminReviewItem hideReply(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                     @Valid @RequestBody ReasonRequest req) {
        return reviews.hideReply(userId(jwt), id, req.reason());
    }

    @DeleteMapping("/{id}/reply/hidden")
    public AdminReviewItem unhideReply(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return reviews.unhideReply(userId(jwt), id);
    }

    @PostMapping("/{id}/reports/dismiss")
    public AdminReviewItem dismissReports(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return reviews.dismissReports(userId(jwt), id);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
