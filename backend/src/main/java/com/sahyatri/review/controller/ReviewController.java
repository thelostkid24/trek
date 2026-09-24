package com.sahyatri.review.controller;

import com.sahyatri.common.web.ItemsResponse;
import com.sahyatri.review.dto.PublicReview;
import com.sahyatri.review.dto.ReviewRequest;
import com.sahyatri.review.dto.ReviewResponse;
import com.sahyatri.review.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Contract: docs/TRD.md §7.14. */
@RestController
public class ReviewController {

    private final ReviewService reviews;

    public ReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping("/api/trekker/bookings/{id}/review")
    public ReviewResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return reviews.get(UUID.fromString(jwt.getSubject()), id);
    }

    @PutMapping("/api/trekker/bookings/{id}/review")
    public ReviewResponse write(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                @Valid @RequestBody ReviewRequest req) {
        return reviews.write(UUID.fromString(jwt.getSubject()), id, req);
    }

    @GetMapping("/api/public/guides/{id}/reviews")
    public ItemsResponse<PublicReview> forGuide(@PathVariable UUID id) {
        return new ItemsResponse<>(reviews.forGuide(id));
    }
}
