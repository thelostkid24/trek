package com.sahyatri.review.service;

import com.sahyatri.common.audit.AuditLog;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.review.dto.AdminReviewItem;
import com.sahyatri.review.dto.PublicReview;
import com.sahyatri.review.entity.Review;
import com.sahyatri.review.entity.ReviewReport;
import com.sahyatri.review.repository.ReviewQueries;
import com.sahyatri.review.repository.ReviewReportRepository;
import com.sahyatri.review.repository.ReviewRepository;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Review moderation (§7.21.5). Everything goes live at once; an admin can hide a review or a guide's reply with a
 * stored reason, and unhide it. Every change is audit-logged and closes the review's open reports.
 */
@Service
public class AdminReviewService {

    public enum View { RECENT, REPORTED, HIDDEN }

    static final int LIST_LIMIT = 100;

    private final ReviewService reviewService;
    private final ReviewRepository reviews;
    private final ReviewReportRepository reports;
    private final ReviewQueries queries;
    private final AuditLog audit;

    public AdminReviewService(ReviewService reviewService, ReviewRepository reviews, ReviewReportRepository reports,
                              ReviewQueries queries, AuditLog audit) {
        this.reviewService = reviewService;
        this.reviews = reviews;
        this.reports = reports;
        this.queries = queries;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AdminReviewItem> list(View view) {
        Limit limit = Limit.of(LIST_LIMIT);
        List<Review> rows = switch (view) {
            case RECENT -> reviews.findRecent(limit);
            case REPORTED -> reviews.findReported(limit);
            case HIDDEN -> reviews.findHidden(limit);
        };
        return items(rows);
    }

    @Transactional
    public AdminReviewItem hide(UUID adminId, UUID reviewId, String reason) {
        Review r = require(reviewId);
        r.hide(reason.trim(), Instant.now());
        return changed(adminId, r, "REVIEW_HIDDEN", Map.of("reason", reason.trim()));
    }

    @Transactional
    public AdminReviewItem unhide(UUID adminId, UUID reviewId) {
        Review r = require(reviewId);
        if (r.getHiddenAt() == null) {
            return items(List.of(r)).getFirst();
        }
        String reason = r.getHiddenReason();
        r.unhide();
        return changed(adminId, r, "REVIEW_UNHIDDEN", Map.of("was", reason));
    }

    @Transactional
    public AdminReviewItem hideReply(UUID adminId, UUID reviewId, String reason) {
        Review r = require(reviewId);
        if (r.getReplyBody() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "REPLY_NOT_FOUND", "This review has no reply");
        }
        r.hideReply(reason.trim(), Instant.now());
        return changed(adminId, r, "REVIEW_REPLY_HIDDEN", Map.of("reason", reason.trim()));
    }

    @Transactional
    public AdminReviewItem unhideReply(UUID adminId, UUID reviewId) {
        Review r = require(reviewId);
        if (r.getReplyHiddenAt() == null) {
            return items(List.of(r)).getFirst();
        }
        String reason = r.getReplyHiddenReason();
        r.unhideReply();
        return changed(adminId, r, "REVIEW_REPLY_UNHIDDEN", Map.of("was", reason));
    }

    /** Closes the open reports without hiding anything. */
    @Transactional
    public AdminReviewItem dismissReports(UUID adminId, UUID reviewId) {
        Review r = require(reviewId);
        int closed = resolveReports(r.getId());
        audit.record(adminId, "REVIEW_REPORTS_DISMISSED", "REVIEW", r.getId(), Map.of("reports", closed));
        return items(List.of(r)).getFirst();
    }

    private AdminReviewItem changed(UUID adminId, Review r, String action, Map<String, ?> data) {
        reviews.saveAndFlush(r);
        resolveReports(r.getId());
        audit.record(adminId, action, "REVIEW", r.getId(), data);
        return items(List.of(r)).getFirst();
    }

    private int resolveReports(UUID reviewId) {
        List<ReviewReport> open = reports.findByReviewIdAndResolvedAtIsNull(reviewId);
        Instant now = Instant.now();
        open.forEach(p -> p.resolve(now));
        reports.saveAllAndFlush(open);
        return open.size();
    }

    private List<AdminReviewItem> items(List<Review> rows) {
        List<PublicReview> views = reviewService.publicViews(rows, true);
        Map<UUID, String[]> guides = queries.guideNames(rows.stream().map(Review::getGuideId)
                .collect(Collectors.toSet()));
        Map<UUID, String> contacts = queries.bookingContacts(rows.stream().map(Review::getBookingId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<UUID, List<ReviewReport>> open = reports.findByReviewIdInAndResolvedAtIsNullOrderByCreatedAt(
                rows.stream().map(Review::getId).toList()).stream()
                .collect(Collectors.groupingBy(ReviewReport::getReviewId));
        Map<UUID, String> reporters = queries.userNames(open.values().stream().flatMap(List::stream)
                .map(ReviewReport::getReporterId).collect(Collectors.toSet()));
        List<AdminReviewItem> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Review r = rows.get(i);
            String[] guide = guides.getOrDefault(r.getGuideId(), new String[2]);
            String contact = r.getReviewerEmail() != null ? r.getReviewerEmail()
                    : r.getReviewerPhone() != null ? r.getReviewerPhone() : contacts.get(r.getBookingId());
            List<AdminReviewItem.Report> rs = open.getOrDefault(r.getId(), List.of()).stream()
                    .map(p -> new AdminReviewItem.Report(p.getReason(), reporters.get(p.getReporterId()),
                            p.getCreatedAt()))
                    .toList();
            out.add(new AdminReviewItem(views.get(i), r.getGuideId(), guide[0], guide[1], contact, r.getHiddenAt(),
                    r.getHiddenReason(), r.getReplyHiddenAt(), r.getReplyHiddenReason(), rs));
        }
        return out;
    }

    private Review require(UUID id) {
        return reviews.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "Review not found"));
    }
}
