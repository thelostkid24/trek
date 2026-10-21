package com.sahyatri.review.service;

import com.sahyatri.auth.entity.OtpPurpose;
import com.sahyatri.auth.entity.User;
import com.sahyatri.auth.service.OtpService;
import com.sahyatri.booking.dto.EmailCodeResponse;
import com.sahyatri.booking.service.BookingEmailCodeService;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.review.dto.OwnTrekkerCodeRequest;
import com.sahyatri.review.dto.OwnTrekkerReviewRequest;
import com.sahyatri.review.dto.OwnTrekkerReviewResponse;
import com.sahyatri.review.dto.ReviewChannel;
import com.sahyatri.review.entity.Review;
import com.sahyatri.review.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Reviews from a guide's own trekkers, who didn't book with us (§7.21.5). They prove an email (the checkout code,
 * §7.6) or a phone (an OTP with purpose REVIEW, §7.2) and get one review per guide and identity; posting again
 * rewrites it. These reviews are "Group verified" and never count towards a rating.
 */
@Service
public class OwnTrekkerReviewService {

    private final ReviewService reviewService;
    private final ReviewRepository reviews;
    private final BookingEmailCodeService emailCodes;
    private final OtpService otps;

    public OwnTrekkerReviewService(ReviewService reviewService, ReviewRepository reviews,
                                   BookingEmailCodeService emailCodes, OtpService otps) {
        this.reviewService = reviewService;
        this.reviews = reviews;
        this.emailCodes = emailCodes;
        this.otps = otps;
    }

    @Transactional
    public EmailCodeResponse sendCode(String guideKey, OwnTrekkerCodeRequest req) {
        User guide = reviewService.requireGuide(guideKey);
        if (req.channel() == ReviewChannel.EMAIL) {
            if (req.email() == null || req.email().isBlank()) {
                throw ApiException.validation("email", "must not be blank");
            }
            return emailCodes.sendForReview(req.email(), guide.getFullName());
        }
        if (req.phone() == null || req.phone().isBlank()) {
            throw ApiException.validation("phone", "must not be blank");
        }
        var sent = otps.request(req.phone(), OtpPurpose.REVIEW);
        return new EmailCodeResponse(sent.expiresIn(), sent.resendAfter());
    }

    /** Checks the code first, so a wrong one writes nothing (its failed attempt is still counted). */
    @Transactional(noRollbackFor = ApiException.class)
    public OwnTrekkerReviewResponse submit(String guideKey, OwnTrekkerReviewRequest req) {
        User guide = reviewService.requireGuide(guideKey);
        String email = ReviewService.blankToNull(req.email());
        String phone = ReviewService.blankToNull(req.phone());
        if ((email == null) == (phone == null)) {
            throw ApiException.validation(email == null ? "email" : "phone", "give an email or a phone, not both");
        }
        if (email != null) {
            email = email.toLowerCase(Locale.ROOT);
            emailCodes.check(email, req.code());
        } else {
            otps.verify(phone, req.code(), OtpPurpose.REVIEW);
        }

        String e = email;
        Review review = (email != null ? reviews.findByGuideIdAndReviewerEmail(guide.getId(), email)
                : reviews.findByGuideIdAndReviewerPhone(guide.getId(), phone)).orElse(null);
        boolean created = review == null;
        if (created) {
            review = Review.ownTrekker(guide.getId(), e, phone);
        }
        review.setAuthorName(ReviewService.firstName(req.name()));
        review.setTrekName(ReviewService.blankToNull(req.trekName()));
        review.rate(req.rating(), ReviewService.blankToNull(req.body()));
        review.rateDetails(req.safetyRating(), req.routeRating(), req.communicationRating(), req.careRating(),
                req.tags());
        Review saved = reviews.saveAndFlush(review);
        return new OwnTrekkerReviewResponse(reviewService.publicViews(List.of(saved), false).getFirst(), created);
    }
}
