package com.sahyatri.review.controller;

import com.sahyatri.booking.dto.EmailCodeResponse;
import com.sahyatri.review.dto.OwnTrekkerCodeRequest;
import com.sahyatri.review.dto.OwnTrekkerReviewRequest;
import com.sahyatri.review.dto.OwnTrekkerReviewResponse;
import com.sahyatri.review.dto.ReviewPage;
import com.sahyatri.review.dto.ReviewRequest;
import com.sahyatri.review.dto.ReviewResponse;
import com.sahyatri.review.dto.ReviewableBooking;
import com.sahyatri.review.dto.ReviewsSummary;
import com.sahyatri.review.entity.ReviewSource;
import com.sahyatri.review.service.OwnTrekkerReviewService;
import com.sahyatri.review.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Contract: docs/TRD.md §7.14 and §7.21.5. {@code key}: a guide's id, slug or old slug. */
@RestController
public class ReviewController {

    private final ReviewService reviews;
    private final OwnTrekkerReviewService ownTrekkers;

    public ReviewController(ReviewService reviews, OwnTrekkerReviewService ownTrekkers) {
        this.reviews = reviews;
        this.ownTrekkers = ownTrekkers;
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

    @GetMapping("/api/trekker/guides/{key}/review-booking")
    public ReviewableBooking reviewableBooking(@AuthenticationPrincipal Jwt jwt, @PathVariable String key) {
        return reviews.reviewableBooking(UUID.fromString(jwt.getSubject()), key);
    }

    @GetMapping("/api/public/guides/{key}/reviews")
    public ReviewPage forGuide(@PathVariable String key, @RequestParam(required = false) ReviewSource source,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "20") int size) {
        return reviews.page(reviews.requireGuide(key).getId(), source, page, size);
    }

    @GetMapping("/api/public/guides/{key}/reviews-summary")
    public ReviewsSummary summary(@PathVariable String key) {
        return reviews.summary(reviews.requireGuide(key).getId());
    }

    @PostMapping("/api/public/guides/{key}/reviews/code")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EmailCodeResponse sendCode(@PathVariable String key, @Valid @RequestBody OwnTrekkerCodeRequest req) {
        return ownTrekkers.sendCode(key, req);
    }

    @PostMapping("/api/public/guides/{key}/reviews")
    public OwnTrekkerReviewResponse submit(@PathVariable String key, @Valid @RequestBody OwnTrekkerReviewRequest req) {
        return ownTrekkers.submit(key, req);
    }
}
