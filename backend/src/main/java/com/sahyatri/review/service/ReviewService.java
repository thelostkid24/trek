package com.sahyatri.review.service;

import com.sahyatri.auth.entity.Role;
import com.sahyatri.auth.entity.User;
import com.sahyatri.auth.repository.UserRepository;
import com.sahyatri.booking.entity.Booking;
import com.sahyatri.booking.entity.BookingStatus;
import com.sahyatri.booking.repository.BookingRepository;
import com.sahyatri.catalog.entity.Departure;
import com.sahyatri.catalog.entity.DepartureStatus;
import com.sahyatri.catalog.repository.DepartureRepository;
import com.sahyatri.catalog.service.CatalogService;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.guide.service.GuideSlugService;
import com.sahyatri.review.dto.PublicReview;
import com.sahyatri.review.dto.RatingSummary;
import com.sahyatri.review.dto.ReviewPage;
import com.sahyatri.review.dto.ReviewRequest;
import com.sahyatri.review.dto.ReviewResponse;
import com.sahyatri.review.dto.ReviewableBooking;
import com.sahyatri.review.dto.ReviewsSummary;
import com.sahyatri.review.entity.Review;
import com.sahyatri.review.entity.ReviewSource;
import com.sahyatri.review.repository.ReviewQueries;
import com.sahyatri.review.repository.ReviewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Guide reviews (§7.14, §7.21.5). A trekker reviews a confirmed booking once its departure is completed. Ratings are
 * averaged on every read, like every other guide figure (law 8), from visible BOOKING reviews only: reviews from a
 * guide's own trekkers show on the profile but never count.
 */
@Service
public class ReviewService {

    static final int PUBLIC_LIMIT = 20;
    static final int MAX_PAGE_SIZE = 50;
    /** A sub-rating bar shows from this many answers. */
    static final int MIN_BAR_ANSWERS = 5;
    /** A tag is "mentioned often" from this many picks; at most this many show. */
    static final int MIN_MENTIONS = 2;
    static final int MAX_MENTIONS = 6;

    private final ReviewRepository reviews;
    private final ReviewQueries queries;
    private final BookingRepository bookings;
    private final DepartureRepository departures;
    private final UserRepository users;
    private final GuideSlugService slugs;

    public ReviewService(ReviewRepository reviews, ReviewQueries queries, BookingRepository bookings,
                         DepartureRepository departures, UserRepository users, GuideSlugService slugs) {
        this.reviews = reviews;
        this.queries = queries;
        this.bookings = bookings;
        this.departures = departures;
        this.users = users;
        this.slugs = slugs;
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
        review.rate(req.rating(), blankToNull(req.body()));
        review.rateDetails(req.safetyRating(), req.routeRating(), req.communicationRating(), req.careRating(),
                req.tags());
        return ReviewResponse.of(reviews.saveAndFlush(review));
    }

    /** A guide's latest visible reviews, newest first (the GuideProfile's list). */
    @Transactional(readOnly = true)
    public List<PublicReview> forGuide(UUID guideId) {
        return page(guideId, null, 0, PUBLIC_LIMIT).items();
    }

    /** One page of a guide's visible reviews; {@code source} null = all. */
    @Transactional(readOnly = true)
    public ReviewPage page(UUID guideId, ReviewSource source, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.clamp(size, 1, MAX_PAGE_SIZE);
        Page<Review> rows = reviews.findVisible(guideId,
                source == null ? EnumSet.allOf(ReviewSource.class) : EnumSet.of(source), PageRequest.of(p, s));
        return new ReviewPage(publicViews(rows.getContent(), false), rows.getTotalElements(), p, s);
    }

    /** The profile's review header (§7.21.5). */
    @Transactional(readOnly = true)
    public ReviewsSummary summary(UUID guideId) {
        Map<String, Long> counts = queries.visibleCounts(guideId);
        long booking = counts.getOrDefault(ReviewSource.BOOKING.name(), 0L);
        long own = counts.getOrDefault(ReviewSource.OWN_TREKKER.name(), 0L);
        RatingSummary rating = ratings(List.of(guideId)).getOrDefault(guideId, RatingSummary.NONE);
        List<String> order = List.of("OVERALL", "SAFETY", "ROUTE", "COMMUNICATION", "CARE");
        List<ReviewsSummary.Category> categories = queries.categories(guideId).stream()
                .filter(c -> c.key().equals("OVERALL") ? c.answers() > 0 : c.answers() >= MIN_BAR_ANSWERS)
                .sorted(Comparator.comparingInt(c -> order.indexOf(c.key())))
                .toList();
        List<ReviewsSummary.Mention> mentions = queries.tagCounts(guideId).stream()
                .filter(m -> m.count() >= MIN_MENTIONS).limit(MAX_MENTIONS).toList();
        return new ReviewsSummary(rating.average(), rating.count(),
                new ReviewsSummary.Counts(booking + own, booking, own), categories, mentions);
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

    /**
     * Where a signed-in trekker who opens a guide's review link goes: their latest completed booking with that guide,
     * reviewed as a "Verified trek" (§7.21.5).
     */
    @Transactional(readOnly = true)
    public ReviewableBooking reviewableBooking(UUID userId, String guideKey) {
        UUID guideId = requireGuide(guideKey).getId();
        UUID bookingId = queries.reviewableBooking(userId, guideId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "NO_REVIEWABLE_BOOKING", "No completed trek with this guide"));
        return new ReviewableBooking(bookingId, reviews.findByBookingId(bookingId).isPresent());
    }

    /** An active guide named by id, slug or an old slug. */
    @Transactional(readOnly = true)
    public User requireGuide(String key) {
        return slugs.resolve(key).flatMap(users::findById)
                .filter(u -> u.getRole() == Role.GUIDE && !u.isDisabled())
                .orElseThrow(CatalogService::guideNotFound);
    }

    /** Public views of these reviews, each with its departure's trek and date. */
    List<PublicReview> publicViews(List<Review> rows, boolean showHiddenReply) {
        Map<UUID, Departure> byId = departures.findAllById(rows.stream().map(Review::getDepartureId)
                        .filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Departure::getId, Function.identity()));
        return rows.stream().map(r -> {
            Departure d = r.getDepartureId() == null ? null : byId.get(r.getDepartureId());
            return PublicReview.of(r, d == null ? null : d.getTrack().getName(), d == null ? null : d.getStartDate(),
                    showHiddenReply);
        }).toList();
    }

    private Booking requireOwnBooking(UUID userId, UUID bookingId) {
        return bookings.findForUser(bookingId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking not found"));
    }

    static String firstName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "A trekker";
        }
        return fullName.trim().split("\\s+")[0];
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
