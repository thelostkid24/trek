package com.sahyatri.review.repository;

import com.sahyatri.review.entity.Review;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Optional<Review> findByBookingId(UUID bookingId);

    List<Review> findByGuideIdOrderByCreatedAtDesc(UUID guideId, Limit limit);

    /** Average rating and count per guide, worked out on every read (never stored). */
    @Query("""
            select r.guideId as guideId, avg(r.rating) as average, count(r) as count from Review r
            where r.guideId in :guideIds group by r.guideId""")
    List<GuideRating> ratingsFor(Collection<UUID> guideIds);

    interface GuideRating {
        UUID getGuideId();

        double getAverage();

        long getCount();
    }
}
