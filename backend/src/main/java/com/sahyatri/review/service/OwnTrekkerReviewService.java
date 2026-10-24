package com.sahyatri.review.service;

import com.sahyatri.auth.entity.User;
import com.sahyatri.review.dto.OwnTrekkerReviewRequest;
import com.sahyatri.review.dto.OwnTrekkerReviewResponse;
import com.sahyatri.review.entity.Review;
import com.sahyatri.review.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Reviews from a guide's own trekkers, who didn't book with us (§7.21.5). Anyone can write one, without an email or
 * phone; each waits hidden until an admin approves it, and never counts towards a rating.
 */
@Service
public class OwnTrekkerReviewService {

    private final ReviewService reviewService;
    private final ReviewRepository reviews;

    public OwnTrekkerReviewService(ReviewService reviewService, ReviewRepository reviews) {
        this.reviewService = reviewService;
        this.reviews = reviews;
    }

    @Transactional
    public OwnTrekkerReviewResponse submit(String guideKey, OwnTrekkerReviewRequest req) {
        User guide = reviewService.requireGuide(guideKey);
        Review review = Review.ownTrekker(guide.getId());
        review.setAuthorName(ReviewService.firstName(req.name()));
        review.setTrekName(ReviewService.blankToNull(req.trekName()));
        review.rate(req.rating(), ReviewService.blankToNull(req.body()));
        review.rateDetails(req.safetyRating(), req.routeRating(), req.communicationRating(), req.careRating(),
                req.tags());
        review.setOtherTag(ReviewService.blankToNull(req.otherTag()));
        Review saved = reviews.saveAndFlush(review);
        return new OwnTrekkerReviewResponse(reviewService.publicViews(List.of(saved), false).getFirst());
    }
}
