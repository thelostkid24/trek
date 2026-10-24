package com.sahyatri.review.service;

import com.sahyatri.common.exception.ApiException;
import com.sahyatri.review.dto.GuideReviewItem;
import com.sahyatri.review.dto.GuideReviewsResponse;
import com.sahyatri.review.dto.PublicReview;
import com.sahyatri.review.dto.RatingSummary;
import com.sahyatri.review.entity.Review;
import com.sahyatri.review.entity.ReviewReport;
import com.sahyatri.review.entity.ReviewSource;
import com.sahyatri.review.repository.ReviewQueries;
import com.sahyatri.review.repository.ReviewReportRepository;
import com.sahyatri.review.repository.ReviewRepository;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A guide's side of their reviews (§7.21.5): the Reviews tab, one public reply per review, and reporting a review to
 * admins. Guides never delete or hide reviews. Ownership comes from the token's subject.
 */
@Service
public class GuideReviewService {

    static final int LIST_LIMIT = 200;

    private final ReviewService reviewService;
    private final ReviewRepository reviews;
    private final ReviewReportRepository reports;
    private final ReviewQueries queries;

    public GuideReviewService(ReviewService reviewService, ReviewRepository reviews, ReviewReportRepository reports,
                              ReviewQueries queries) {
        this.reviewService = reviewService;
        this.reviews = reviews;
        this.reports = reports;
        this.queries = queries;
    }

    @Transactional(readOnly = true)
    public GuideReviewsResponse list(UUID guideId) {
        List<Review> rows = reviews.findApprovedForGuide(guideId, Limit.of(LIST_LIMIT));
        Set<UUID> reported = reports.findByReporterIdAndResolvedAtIsNull(guideId).stream()
                .map(ReviewReport::getReviewId).collect(Collectors.toSet());
        List<PublicReview> views = reviewService.publicViews(rows, true);
        List<GuideReviewItem> items = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Review r = rows.get(i);
            items.add(new GuideReviewItem(views.get(i), r.getHiddenReason(), r.getReplyHiddenReason(),
                    reported.contains(r.getId())));
        }
        RatingSummary rating = reviewService.ratings(List.of(guideId)).getOrDefault(guideId, RatingSummary.NONE);
        long own = queries.visibleCounts(guideId).getOrDefault(ReviewSource.OWN_TREKKER.name(), 0L);
        long repliedThisWeek = reviews.countByGuideIdAndReplyCreatedAtAfter(guideId,
                Instant.now().minus(Duration.ofDays(7)));
        String[] names = queries.guideNames(List.of(guideId)).getOrDefault(guideId, new String[2]);
        return new GuideReviewsResponse(names[1], names[0], rating.average(), rating.count(), own, repliedThisWeek,
                queries.completedDepartures(guideId), items);
    }

    @Transactional
    public GuideReviewItem reply(UUID guideId, UUID reviewId, String body) {
        Review r = requireOwn(guideId, reviewId);
        r.reply(body.trim(), Instant.now());
        return item(guideId, reviews.saveAndFlush(r));
    }

    @Transactional
    public void deleteReply(UUID guideId, UUID reviewId) {
        Review r = requireOwn(guideId, reviewId);
        r.removeReply();
        reviews.saveAndFlush(r);
    }

    /** Flags a review for admins. Reporting again while it's open just updates the reason. */
    @Transactional
    public void report(UUID guideId, UUID reviewId, String reason) {
        requireOwn(guideId, reviewId);
        ReviewReport report = reports.findByReviewIdAndReporterIdAndResolvedAtIsNull(reviewId, guideId)
                .orElseGet(() -> new ReviewReport(reviewId, guideId, reason.trim()));
        report.setReason(reason.trim());
        reports.save(report);
    }

    private GuideReviewItem item(UUID guideId, Review r) {
        boolean reported = reports.findByReviewIdAndReporterIdAndResolvedAtIsNull(r.getId(), guideId).isPresent();
        return new GuideReviewItem(reviewService.publicViews(List.of(r), true).getFirst(), r.getHiddenReason(),
                r.getReplyHiddenReason(), reported);
    }

    private Review requireOwn(UUID guideId, UUID reviewId) {
        return reviews.findById(reviewId).filter(r -> r.getGuideId().equals(guideId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "Review not found"));
    }
}
