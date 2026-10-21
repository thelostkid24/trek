package com.sahyatri.review.repository;

import com.sahyatri.review.entity.ReviewReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewReportRepository extends JpaRepository<ReviewReport, UUID> {

    Optional<ReviewReport> findByReviewIdAndReporterIdAndResolvedAtIsNull(UUID reviewId, UUID reporterId);

    List<ReviewReport> findByReviewIdInAndResolvedAtIsNullOrderByCreatedAt(Collection<UUID> reviewIds);

    List<ReviewReport> findByReviewIdAndResolvedAtIsNull(UUID reviewId);

    List<ReviewReport> findByReporterIdAndResolvedAtIsNull(UUID reporterId);
}
