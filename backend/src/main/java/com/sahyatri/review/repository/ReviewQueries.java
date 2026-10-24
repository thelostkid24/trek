package com.sahyatri.review.repository;

import com.sahyatri.review.dto.GuideReviewsResponse;
import com.sahyatri.review.dto.ReviewsSummary;
import com.sahyatri.review.entity.ReviewTag;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Read-side SQL for reviews that doesn't fit an entity: aggregates and lookups across features. */
@Repository
public class ReviewQueries {

    /** Visible BOOKING reviews only (§7.21.5). */
    private static final String COUNTED = "guide_id = :guideId AND source = 'BOOKING' AND hidden_at IS NULL";

    private final NamedParameterJdbcTemplate jdbc;

    public ReviewQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Visible reviews per source. */
    public Map<String, Long> visibleCounts(UUID guideId) {
        Map<String, Long> counts = new HashMap<>();
        jdbc.query("""
                SELECT source, count(*) AS n FROM reviews
                WHERE guide_id = :guideId AND hidden_at IS NULL AND approved_at IS NOT NULL
                GROUP BY source""", Map.of("guideId", guideId),
                rs -> {
                    counts.put(rs.getString("source"), rs.getLong("n"));
                });
        return counts;
    }

    /** Overall plus each sub-rating: average over those who answered it, and how many did. Unanswered = absent. */
    public List<ReviewsSummary.Category> categories(UUID guideId) {
        return jdbc.query("""
                SELECT key, avg(v) AS average, count(v) AS answers FROM (
                    SELECT unnest(ARRAY['OVERALL', 'SAFETY', 'ROUTE', 'COMMUNICATION', 'CARE']) AS key,
                           unnest(ARRAY[rating, safety_rating, route_rating, communication_rating, care_rating]) AS v
                    FROM reviews WHERE %s) x
                WHERE v IS NOT NULL GROUP BY key""".formatted(COUNTED), Map.of("guideId", guideId),
                (rs, i) -> new ReviewsSummary.Category(rs.getString("key"),
                        Math.round(rs.getDouble("average") * 10) / 10.0, rs.getLong("answers")));
    }

    /** How often each tag was picked. */
    public List<ReviewsSummary.Mention> tagCounts(UUID guideId) {
        return jdbc.query("""
                SELECT t.tag, count(*) AS n FROM review_tags t JOIN reviews r ON r.id = t.review_id
                WHERE r.guide_id = :guideId AND r.source = 'BOOKING' AND r.hidden_at IS NULL
                GROUP BY t.tag ORDER BY n DESC, t.tag""",
                Map.of("guideId", guideId),
                (rs, i) -> new ReviewsSummary.Mention(ReviewTag.valueOf(rs.getString("tag")), rs.getLong("n")));
    }

    /** The guide's completed departures, latest first (for "Which trek?"). */
    public List<GuideReviewsResponse.CompletedDeparture> completedDepartures(UUID guideId) {
        return jdbc.query("""
                SELECT d.id, t.name, d.start_date FROM departures d JOIN tracks t ON t.id = d.track_id
                WHERE d.guide_id = :guideId AND d.status = 'COMPLETED'
                ORDER BY d.start_date DESC LIMIT 50""", Map.of("guideId", guideId),
                (rs, i) -> new GuideReviewsResponse.CompletedDeparture(rs.getObject("id", UUID.class),
                        rs.getString("name"), rs.getObject("start_date", LocalDate.class)));
    }

    /** The trekker's latest confirmed booking on a completed departure this guide led. */
    public Optional<UUID> reviewableBooking(UUID userId, UUID guideId) {
        return jdbc.query("""
                SELECT b.id FROM bookings b JOIN departures d ON d.id = b.departure_id
                WHERE b.user_id = :userId AND d.guide_id = :guideId AND b.status = 'CONFIRMED'
                  AND d.status = 'COMPLETED'
                ORDER BY d.start_date DESC LIMIT 1""", Map.of("userId", userId, "guideId", guideId),
                (rs, i) -> rs.getObject("id", UUID.class)).stream().findFirst();
    }

    /** Name and slug per guide. */
    public Map<UUID, String[]> guideNames(Collection<UUID> guideIds) {
        Map<UUID, String[]> out = new HashMap<>();
        if (guideIds.isEmpty()) {
            return out;
        }
        jdbc.query("""
                SELECT u.id, u.full_name, gp.slug FROM users u LEFT JOIN guide_profiles gp ON gp.user_id = u.id
                WHERE u.id IN (:ids)""", new MapSqlParameterSource("ids", guideIds),
                rs -> {
                    out.put(rs.getObject("id", UUID.class), new String[]{rs.getString("full_name"), rs.getString("slug")});
                });
        return out;
    }

    /** Full names of users. */
    public Map<UUID, String> userNames(Collection<UUID> ids) {
        Map<UUID, String> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        jdbc.query("SELECT id, full_name FROM users WHERE id IN (:ids)", new MapSqlParameterSource("ids", ids),
                rs -> {
                    out.put(rs.getObject("id", UUID.class), rs.getString("full_name"));
                });
        return out;
    }

    /** Booking contact email per booking, so admins can see who wrote a verified review. */
    public Map<UUID, String> bookingContacts(Collection<UUID> bookingIds) {
        Map<UUID, String> out = new HashMap<>();
        if (bookingIds.isEmpty()) {
            return out;
        }
        jdbc.query("SELECT id, contact_email FROM bookings WHERE id IN (:ids)",
                new MapSqlParameterSource("ids", bookingIds),
                rs -> {
                    out.put(rs.getObject("id", UUID.class), rs.getString("contact_email"));
                });
        return out;
    }
}
