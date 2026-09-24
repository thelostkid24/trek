package com.sahyatri.review.service;

import com.sahyatri.booking.entity.Booking;
import com.sahyatri.booking.entity.BookingStatus;
import com.sahyatri.booking.repository.BookingRepository;
import com.sahyatri.catalog.entity.Departure;
import com.sahyatri.catalog.entity.DepartureStatus;
import com.sahyatri.catalog.repository.DepartureRepository;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.review.dto.PublicReview;
import com.sahyatri.review.dto.RatingSummary;
import com.sahyatri.review.dto.ReviewRequest;
import com.sahyatri.review.dto.ReviewResponse;
import com.sahyatri.review.entity.Review;
import com.sahyatri.review.repository.ReviewRepository;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Guide reviews (§7.14). A trekker reviews a confirmed booking once its departure is completed. Ratings are
 * averaged on every read, like every other guide figure (law 8).
 */
@Service
public class ReviewService {

    static final int PUBLIC_LIMIT = 20;

    private final ReviewRepository reviews;
    private final BookingRepository bookings;
    private final DepartureRepository departures;

    public ReviewService(ReviewRepository reviews, BookingRepository bookings, DepartureRepository departures) {
        this.reviews = reviews;
        this.bookings = bookings;
        this.departures = departures;
    }

    @Transactional(readOnly = true)
    public ReviewResponse get(UUID userId, UUID bookingId) {
        requireOwnBooking(userId, bookingId);
        return reviews.findByBookingId(bookingId).map(ReviewResponse::of)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "No review yet"));
    }

    @Transactional
    public ReviewResponse write(UUID userId, UUID bookingId, ReviewRequest req) {
        Booking booking = requireOwnBooking(userId, bookingId);
        Departure d = booking.getDeparture();
        if (booking.getStatus() != BookingStatus.CONFIRMED || d.getStatus() != DepartureStatus.COMPLETED) {
            throw ApiException.conflict("REVIEW_NOT_ALLOWED", "You can review a trek once you've completed it");
        }
        Review review = reviews.findByBookingId(bookingId).orElseGet(() -> new Review(bookingId, userId, d.getId(),
                d.getGuide().getId(), d.getTrack().getId(), firstName(booking.getContactName())));
        review.rate(req.rating(), req.body() == null || req.body().isBlank() ? null : req.body().trim());
        return ReviewResponse.of(reviews.saveAndFlush(review));
    }

    /** A guide's latest reviews, newest first. */
    @Transactional(readOnly = true)
    public List<PublicReview> forGuide(UUID guideId) {
        List<Review> rows = reviews.findByGuideIdOrderByCreatedAtDesc(guideId, Limit.of(PUBLIC_LIMIT));
        Map<UUID, Departure> byId = departures.findAllById(rows.stream().map(Review::getDepartureId).toList())
                .stream().collect(Collectors.toMap(Departure::getId, Function.identity()));
        return rows.stream().map(r -> {
            Departure d = byId.get(r.getDepartureId());
            return new PublicReview(r.getRating(), r.getBody(), r.getAuthorName(), d.getTrack().getName(),
                    d.getStartDate(), r.getCreatedAt());
        }).toList();
    }

    @Transactional(readOnly = true)
    public Map<UUID, RatingSummary> ratings(Collection<UUID> guideIds) {
        Map<UUID, RatingSummary> byGuide = new HashMap<>();
        if (!guideIds.isEmpty()) {
            reviews.ratingsFor(guideIds).forEach(r -> byGuide.put(r.getGuideId(),
                    new RatingSummary(Math.round(r.getAverage() * 10) / 10.0, r.getCount())));
        }
        return byGuide;
    }

    private Booking requireOwnBooking(UUID userId, UUID bookingId) {
        return bookings.findForUser(bookingId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking not found"));
    }

    private static String firstName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "A trekker";
        }
        return fullName.trim().split("\\s+")[0];
    }
}
