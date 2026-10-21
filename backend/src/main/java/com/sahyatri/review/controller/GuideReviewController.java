package com.sahyatri.review.controller;

import com.sahyatri.review.dto.GuideReviewItem;
import com.sahyatri.review.dto.GuideReviewsResponse;
import com.sahyatri.review.dto.ReasonRequest;
import com.sahyatri.review.dto.ReplyRequest;
import com.sahyatri.review.service.GuideReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Contract: docs/TRD.md §7.21.5. GUIDE only; the guide is the token's subject. */
@RestController
@RequestMapping("/api/guide/reviews")
public class GuideReviewController {

    private final GuideReviewService reviews;

    public GuideReviewController(GuideReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping
    public GuideReviewsResponse list(@AuthenticationPrincipal Jwt jwt) {
        return reviews.list(userId(jwt));
    }

    @PutMapping("/{id}/reply")
    public GuideReviewItem reply(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                 @Valid @RequestBody ReplyRequest req) {
        return reviews.reply(userId(jwt), id, req.body());
    }

    @DeleteMapping("/{id}/reply")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteReply(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        reviews.deleteReply(userId(jwt), id);
    }

    @PostMapping("/{id}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void report(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                       @Valid @RequestBody ReasonRequest req) {
        reviews.report(userId(jwt), id, req.reason());
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
