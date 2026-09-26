package com.sahyatri.insights.service;

import com.sahyatri.common.exception.ApiException;
import com.sahyatri.insights.dto.CountRow;
import com.sahyatri.insights.dto.DailyPoint;
import com.sahyatri.insights.dto.FunnelStep;
import com.sahyatri.insights.dto.GuideRow;
import com.sahyatri.insights.dto.Headline;
import com.sahyatri.insights.dto.InsightsResponse;
import com.sahyatri.insights.dto.MarketingReach;
import com.sahyatri.insights.dto.PaymentSummary;
import com.sahyatri.insights.dto.SourceRow;
import com.sahyatri.insights.dto.TrekRow;
import com.sahyatri.insights.dto.UpcomingDeparture;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Read-only aggregates for the admin Insights dashboard (§7.15). Returns counts and sums only, never a person.
 * "Accounts" means trekker accounts; days are IST.
 */
@Service
public class InsightsService {

    public static final int MAX_DAYS = 365;
    static final int UPCOMING_DAYS = 60;
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /**
     * Where a row came from, for a table alias with the §6.11 touch columns: the UTM source, else the ad network
     * its click id belongs to, else the referring site, else "direct". Rows made before tracking existed (no
     * device type, nothing else) are "unknown".
     */
    private static String source(String t) {
        return """
                CASE WHEN %1$s.device_type IS NULL AND %1$s.utm_source IS NULL AND %1$s.referrer IS NULL
                          AND %1$s.gclid IS NULL AND %1$s.fbclid IS NULL THEN 'unknown'
                     ELSE COALESCE(%1$s.utm_source,
                                   CASE WHEN %1$s.gclid IS NOT NULL THEN 'google-ads'
                                        WHEN %1$s.fbclid IS NOT NULL THEN 'meta-ads' END,
                                   lower(substring(%1$s.referrer from '^https?://(?:www\\.)?([^/:?#]+)')),
                                   'direct') END""".formatted(t);
    }

    private final NamedParameterJdbcTemplate jdbc;

    public InsightsService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public InsightsResponse get(int days) {
        if (days < 1 || days > MAX_DAYS) {
            throw ApiException.validation("days", "must be between 1 and " + MAX_DAYS);
        }
        Instant to = Instant.now();
        Instant from = to.minus(Duration.ofDays(days));
        LocalDate today = LocalDate.now(IST);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("from", Timestamp.from(from))
                .addValue("fromDay", LocalDate.ofInstant(from, IST))
                .addValue("today", today)
                .addValue("upcomingEnd", today.plusDays(UPCOMING_DAYS));
        return new InsightsResponse(days, from, to,
                headline(p), daily(p), funnel(p),
                sources(p), campaigns(p),
                counts("SELECT COALESCE(heard_from, 'NO_ANSWER') AS k, count(*) AS n FROM users "
                        + "WHERE role = 'TREKKER' AND created_at >= :from GROUP BY 1 ORDER BY 2 DESC, 1", p),
                counts("SELECT COALESCE(signup_method, 'UNKNOWN') AS k, count(*) AS n FROM users "
                        + "WHERE role = 'TREKKER' AND created_at >= :from GROUP BY 1 ORDER BY 2 DESC, 1", p),
                counts("SELECT COALESCE(device_type, 'UNKNOWN') AS k, count(*) AS n FROM users "
                        + "WHERE role = 'TREKKER' AND created_at >= :from GROUP BY 1 ORDER BY 2 DESC, 1", p),
                payments(p), treks(p), upcoming(p), guides(), marketingReach());
    }

    private Headline headline(MapSqlParameterSource p) {
        return jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM users WHERE role = 'TREKKER' AND created_at >= :from) AS new_accounts,
                       (SELECT count(*) FROM bookings WHERE created_at >= :from) AS held,
                       (SELECT count(*) FROM bookings WHERE confirmed_at >= :from) AS confirmed,
                       (SELECT COALESCE(sum(amount_paise), 0) FROM bookings WHERE confirmed_at >= :from) AS gross,
                       (SELECT count(*) FILTER (WHERE confirmed_at IS NOT NULL) * 10000 / NULLIF(count(*), 0)
                        FROM bookings WHERE created_at >= :from AND status <> 'HELD') AS hold_to_paid_bps,
                       (SELECT avg(seats) FROM bookings WHERE confirmed_at >= :from) AS avg_group,
                       (SELECT count(*) FROM bookings WHERE cancelled_at >= :from) AS cancellations""",
                p, (rs, i) -> new Headline(rs.getLong("new_accounts"), rs.getLong("held"), rs.getLong("confirmed"),
                        rs.getLong("gross"), intOrNull(rs, "hold_to_paid_bps"), doubleOrNull(rs, "avg_group"),
                        rs.getLong("cancellations")));
    }

    private List<DailyPoint> daily(MapSqlParameterSource p) {
        return jdbc.query("""
                WITH a AS (SELECT (created_at AT TIME ZONE 'Asia/Kolkata')::date AS day, count(*) AS n
                           FROM users WHERE role = 'TREKKER' AND created_at >= :from GROUP BY 1),
                     c AS (SELECT (confirmed_at AT TIME ZONE 'Asia/Kolkata')::date AS day, count(*) AS n
                           FROM bookings WHERE confirmed_at >= :from GROUP BY 1)
                SELECT d::date AS day, COALESCE(a.n, 0) AS accounts, COALESCE(c.n, 0) AS confirmed
                FROM generate_series(CAST(:fromDay AS date), CAST(:today AS date), interval '1 day') AS d
                LEFT JOIN a ON a.day = d::date LEFT JOIN c ON c.day = d::date
                ORDER BY 1""",
                p, (rs, i) -> new DailyPoint(rs.getObject("day", LocalDate.class), rs.getLong("accounts"),
                        rs.getLong("confirmed")));
    }

    /** One cohort: trekkers who signed up in the window, and how far each has got since. */
    private List<FunnelStep> funnel(MapSqlParameterSource p) {
        return jdbc.queryForObject("""
                WITH cohort AS (SELECT id FROM users WHERE role = 'TREKKER' AND created_at >= :from)
                SELECT (SELECT count(*) FROM cohort) AS accounts,
                       (SELECT count(DISTINCT b.user_id) FROM bookings b JOIN cohort c ON c.id = b.user_id) AS held,
                       (SELECT count(DISTINCT b.user_id) FROM bookings b JOIN cohort c ON c.id = b.user_id
                          WHERE b.confirmed_at IS NOT NULL) AS paid,
                       (SELECT count(DISTINCT b.user_id) FROM bookings b JOIN cohort c ON c.id = b.user_id
                          JOIN departures d ON d.id = b.departure_id
                          WHERE b.status = 'CONFIRMED' AND d.status = 'COMPLETED') AS trekked,
                       (SELECT count(DISTINCT r.user_id) FROM reviews r JOIN cohort c ON c.id = r.user_id) AS reviewed""",
                p, (rs, i) -> List.of(
                        new FunnelStep("ACCOUNT", "Signed up", rs.getLong("accounts")),
                        new FunnelStep("HELD", "Held seats", rs.getLong("held")),
                        new FunnelStep("PAID", "Paid", rs.getLong("paid")),
                        new FunnelStep("TREKKED", "Trekked", rs.getLong("trekked")),
                        new FunnelStep("REVIEWED", "Reviewed", rs.getLong("reviewed"))));
    }

    private List<SourceRow> sources(MapSqlParameterSource p) {
        return sourceRows(source("u"), source("b"), "", p);
    }

    private List<SourceRow> campaigns(MapSqlParameterSource p) {
        return sourceRows("u.utm_campaign", "b.utm_campaign", "WHERE s IS NOT NULL", p);
    }

    private List<SourceRow> sourceRows(String userKey, String bookingKey, String where, MapSqlParameterSource p) {
        return jdbc.query("""
                WITH a AS (SELECT %s AS s, count(*) AS accounts FROM users u
                           WHERE u.role = 'TREKKER' AND u.created_at >= :from GROUP BY 1),
                     b AS (SELECT %s AS s, count(*) AS confirmed, sum(b.amount_paise) AS gross FROM bookings b
                           WHERE b.confirmed_at >= :from GROUP BY 1),
                     j AS (SELECT COALESCE(a.s, b.s) AS s, COALESCE(a.accounts, 0) AS accounts,
                                  COALESCE(b.confirmed, 0) AS confirmed, COALESCE(b.gross, 0) AS gross
                           FROM a FULL JOIN b ON a.s = b.s)
                SELECT * FROM j %s ORDER BY gross DESC, accounts DESC, s LIMIT 20""".formatted(userKey, bookingKey, where),
                p, (rs, i) -> new SourceRow(rs.getString("s"), rs.getLong("accounts"), rs.getLong("confirmed"),
                        rs.getLong("gross")));
    }

    private PaymentSummary payments(MapSqlParameterSource p) {
        List<CountRow> methods = counts("""
                SELECT COALESCE(method, 'UNKNOWN') AS k, count(*) AS n FROM payments
                WHERE created_at >= :from AND status = 'PAID' GROUP BY 1 ORDER BY 2 DESC, 1""", p);
        List<CountRow> failures = counts("""
                SELECT COALESCE(failure_reason, failure_code, 'Unknown') AS k, count(*) AS n FROM payments
                WHERE created_at >= :from AND status = 'FAILED' GROUP BY 1 ORDER BY 2 DESC, 1 LIMIT 5""", p);
        return jdbc.queryForObject("""
                SELECT count(*) AS attempts,
                       count(*) FILTER (WHERE status = 'PAID') AS paid,
                       count(*) FILTER (WHERE status = 'FAILED') AS failed,
                       count(*) FILTER (WHERE status = 'PAID') * 10000
                           / NULLIF(count(*) FILTER (WHERE status <> 'CREATED'), 0) AS success_bps
                FROM payments WHERE created_at >= :from""",
                p, (rs, i) -> new PaymentSummary(rs.getLong("attempts"), rs.getLong("paid"), rs.getLong("failed"),
                        intOrNull(rs, "success_bps"), methods, failures));
    }

    private List<TrekRow> treks(MapSqlParameterSource p) {
        return jdbc.query("""
                WITH sold AS (SELECT d.track_id, count(*) AS confirmed, sum(b.seats) AS seats,
                                     sum(b.amount_paise) AS gross
                              FROM bookings b JOIN departures d ON d.id = b.departure_id
                              WHERE b.confirmed_at >= :from GROUP BY 1),
                     rated AS (SELECT track_id, avg(rating) AS avg_rating, count(*) AS reviews
                               FROM reviews GROUP BY 1)
                SELECT t.id, t.name, t.slug, sold.confirmed, sold.seats, sold.gross, rated.avg_rating,
                       COALESCE(rated.reviews, 0) AS reviews
                FROM sold JOIN tracks t ON t.id = sold.track_id LEFT JOIN rated ON rated.track_id = t.id
                ORDER BY sold.gross DESC, t.name LIMIT 10""",
                p, (rs, i) -> new TrekRow(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("slug"),
                        rs.getLong("confirmed"), rs.getLong("seats"), rs.getLong("gross"),
                        doubleOrNull(rs, "avg_rating"), rs.getLong("reviews")));
    }

    private List<UpcomingDeparture> upcoming(MapSqlParameterSource p) {
        return jdbc.query("""
                SELECT d.id, t.name AS track_name, d.start_date, d.seats_taken, d.max_group_size,
                       g.full_name AS guide_name
                FROM departures d JOIN tracks t ON t.id = d.track_id JOIN users g ON g.id = d.guide_id
                WHERE d.status = 'PUBLISHED' AND d.start_date BETWEEN :today AND :upcomingEnd
                ORDER BY d.start_date, t.name LIMIT 30""",
                p, (rs, i) -> new UpcomingDeparture(rs.getObject("id", UUID.class), rs.getString("track_name"),
                        rs.getObject("start_date", LocalDate.class), rs.getInt("seats_taken"),
                        rs.getInt("max_group_size"), rs.getString("guide_name")));
    }

    private List<GuideRow> guides() {
        return jdbc.query("""
                WITH led AS (SELECT guide_id, count(*) AS completed,
                                    avg(seats_taken * 10000.0 / max_group_size) AS fill_bps
                             FROM departures WHERE status = 'COMPLETED' GROUP BY 1),
                     rated AS (SELECT guide_id, avg(rating) AS avg_rating, count(*) AS reviews
                               FROM reviews GROUP BY 1)
                SELECT u.id, u.full_name, COALESCE(led.completed, 0) AS completed, round(led.fill_bps) AS fill_bps,
                       rated.avg_rating, COALESCE(rated.reviews, 0) AS reviews
                FROM users u LEFT JOIN led ON led.guide_id = u.id LEFT JOIN rated ON rated.guide_id = u.id
                WHERE u.role = 'GUIDE'
                ORDER BY rated.avg_rating DESC NULLS LAST, completed DESC, u.full_name""",
                new MapSqlParameterSource(), (rs, i) -> new GuideRow(rs.getObject("id", UUID.class),
                        rs.getString("full_name"), rs.getLong("completed"), intOrNull(rs, "fill_bps"),
                        doubleOrNull(rs, "avg_rating"), rs.getLong("reviews")));
    }

    private MarketingReach marketingReach() {
        return jdbc.queryForObject("""
                SELECT count(*) AS accounts,
                       count(marketing_email_consent_at) AS email,
                       count(marketing_whatsapp_consent_at) AS whatsapp
                FROM users WHERE role = 'TREKKER' AND status = 'ACTIVE'""",
                new MapSqlParameterSource(), (rs, i) -> new MarketingReach(rs.getLong("accounts"),
                        rs.getLong("email"), rs.getLong("whatsapp")));
    }

    private List<CountRow> counts(String sql, MapSqlParameterSource p) {
        RowMapper<CountRow> row = (rs, i) -> new CountRow(rs.getString("k"), rs.getLong("n"));
        return jdbc.query(sql, p, row);
    }

    private static Integer intOrNull(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : Math.toIntExact(value);
    }

    private static Double doubleOrNull(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : Math.round(value * 100) / 100.0;
    }
}
