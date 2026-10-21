package com.sahyatri.review.repository;

import com.sahyatri.review.entity.Review;
import com.sahyatri.review.entity.ReviewSource;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Optional<Review> findByBookingId(UUID bookingId);

    Optional<Review> findByGuideIdAndReviewerEmail(UUID guideId, String email);

    Optional<Review> findByGuideIdAndReviewerPhone(UUID guideId, String phone);

    /** Every review of a guide, hidden ones too (the guide's own list). */
    List<Review> findByGuideIdOrderByCreatedAtDesc(UUID guideId, Limit limit);

    /** What the public sees: not hidden, from the given sources, newest first. */
    @Query("""
            select r from Review r where r.guideId = :guideId and r.hiddenAt is null and r.source in :sources
            order by r.createdAt desc""")
    Page<Review> findVisible(UUID guideId, Collection<ReviewSource> sources, Pageable page);

    @Query("select r from Review r order by r.createdAt desc")
    List<Review> findRecent(Limit limit);

    @Query("""
            select r from Review r where r.hiddenAt is not null or r.replyHiddenAt is not null
            order by r.createdAt desc""")
    List<Review> findHidden(Limit limit);

    @Query("""
            select r from Review r where r.id in (select p.reviewId from ReviewReport p where p.resolvedAt is null)
            order by r.createdAt desc""")
    List<Review> findReported(Limit limit);

    long countByGuideIdAndReplyCreatedAtAfter(UUID guideId, Instant after);

    /**
     * Average rating and count per guide from visible BOOKING reviews only, worked out on every read (law 8). Reviews
     * from a guide's own trekkers never count (§7.21.5).
     */
    @Query("""
            select r.guideId as guideId, avg(r.rating) as average, count(r) as count from Review r
            where r.guideId in :guideIds and r.source = com.sahyatri.review.entity.ReviewSource.BOOKING
              and r.hiddenAt is null
            group by r.guideId""")
    List<GuideRating> ratingsFor(Collection<UUID> guideIds);

    interface GuideRating {
        UUID getGuideId();

        double getAverage();

        long getCount();
    }
}
