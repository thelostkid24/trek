package com.sahyatri.catalog.service;

import com.sahyatri.auth.entity.Role;
import com.sahyatri.auth.entity.User;
import com.sahyatri.auth.repository.UserRepository;
import com.sahyatri.catalog.dto.CatalogDeparture;
import com.sahyatri.catalog.dto.CatalogTrek;
import com.sahyatri.catalog.dto.DepartureDetail;
import com.sahyatri.catalog.dto.DepartureSummary;
import com.sahyatri.catalog.dto.GuideBrief;
import com.sahyatri.catalog.dto.GuideCard;
import com.sahyatri.catalog.dto.GuideProfile;
import com.sahyatri.catalog.dto.TrackBrief;
import com.sahyatri.catalog.dto.TrackDetail;
import com.sahyatri.catalog.dto.TrekDeparture;
import com.sahyatri.catalog.dto.TrekPage;
import com.sahyatri.catalog.entity.Departure;
import com.sahyatri.catalog.entity.DepartureStatus;
import com.sahyatri.catalog.entity.Difficulty;
import com.sahyatri.catalog.entity.Track;
import com.sahyatri.catalog.entity.TrackPhoto;
import com.sahyatri.catalog.repository.DepartureRepository;
import com.sahyatri.catalog.repository.TrackPhotoRepository;
import com.sahyatri.catalog.repository.TrackRepository;
import com.sahyatri.common.config.BookingProperties;
import com.sahyatri.common.config.CatalogProperties;
import com.sahyatri.common.config.CharityProperties;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.common.storage.AvatarFiles;
import com.sahyatri.common.storage.TrackPhotoFiles;
import com.sahyatri.guide.dto.GuideDetailsResponse;
import com.sahyatri.guide.service.GuideDetailsService;
import com.sahyatri.profile.entity.TrekkerProfile;
import com.sahyatri.profile.repository.TrekkerProfileRepository;
import com.sahyatri.review.dto.RatingSummary;
import com.sahyatri.review.service.ReviewService;
import com.sahyatri.snow.service.SnowReportService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Public reads of the catalog. Drafts are never visible. */
@Service
public class CatalogService {

    private static final LocalDate FAR_FUTURE = LocalDate.of(9999, 1, 1);

    private final DepartureRepository departures;
    private final TrackRepository tracks;
    private final TrackPhotoRepository photos;
    private final UserRepository users;
    private final TrekkerProfileRepository profiles;
    private final CatalogProperties props;
    private final AvatarFiles avatars;
    private final TrackPhotoFiles photoFiles;
    private final GuideDetailsService guideDetails;
    private final ReviewService reviews;
    private final TrekContentService content;
    private final SnowReportService snowReports;
    private final BookingProperties bookingProps;
    private final CharityProperties charity;

    public CatalogService(DepartureRepository departures, TrackRepository tracks, TrackPhotoRepository photos,
                          UserRepository users, TrekkerProfileRepository profiles, CatalogProperties props,
                          AvatarFiles avatars, TrackPhotoFiles photoFiles, GuideDetailsService guideDetails,
                          ReviewService reviews, TrekContentService content, SnowReportService snowReports,
                          BookingProperties bookingProps, CharityProperties charity) {
        this.departures = departures;
        this.tracks = tracks;
        this.photos = photos;
        this.users = users;
        this.profiles = profiles;
        this.props = props;
        this.avatars = avatars;
        this.photoFiles = photoFiles;
        this.guideDetails = guideDetails;
        this.reviews = reviews;
        this.content = content;
        this.snowReports = snowReports;
        this.bookingProps = bookingProps;
        this.charity = charity;
    }

    public LocalDate today() {
        return LocalDate.now(props.zone());
    }

    /** Published and before the booking cutoff, whether or not seats are left. */
    public boolean isOpenForBooking(Departure d) {
        return d.getStatus() == DepartureStatus.PUBLISHED
                && !today().isAfter(d.getStartDate().minusDays(props.bookingCutoffDays()));
    }

    /** Open for booking with at least one seat left. */
    public boolean isBookable(Departure d) {
        return isOpenForBooking(d) && d.seatsLeft() > 0;
    }

    @Transactional(readOnly = true)
    public List<DepartureSummary> list(String month, String difficulty) {
        LocalDate today = today();
        LocalDate from = today;
        LocalDate until = FAR_FUTURE;
        if (month != null && !month.isBlank()) {
            YearMonth ym = parseMonth(month);
            from = later(today, ym.atDay(1));
            until = ym.plusMonths(1).atDay(1);
        }
        Difficulty level = parseDifficulty(difficulty);
        return departures.findListed(DepartureStatus.PUBLISHED, from, until).stream()
                .filter(d -> level == null || d.getTrack().getDifficulty() == level)
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public DepartureDetail get(UUID id) {
        Departure d = departures.findWithTrackAndGuide(id)
                .filter(dep -> !dep.isDraft())
                .orElseThrow(CatalogService::departureNotFound);
        GuideCard guide = guideCards(List.of(d)).get(d.getGuide().getId());
        return new DepartureDetail(d.getId(), TrackDetail.of(d.getTrack(), photoFiles), guide, d.getStartDate(),
                d.getEndDate(), d.getPricePaise(), d.getMaxGroupSize(), d.seatsLeft(), isBookable(d), d.getStatus());
    }

    /**
     * The public catalog: every track with an upcoming published departure, then listed tracks with no dates yet.
     * Treks with dates come soonest first; the rest by name.
     */
    @Transactional(readOnly = true)
    public List<CatalogTrek> catalog() {
        Map<UUID, List<Departure>> byTrack = new LinkedHashMap<>();
        Map<UUID, Track> trackById = new LinkedHashMap<>();
        for (Departure d : departures.findListed(DepartureStatus.PUBLISHED, today(), FAR_FUTURE)) {
            byTrack.computeIfAbsent(d.getTrack().getId(), id -> new ArrayList<>()).add(d);
            trackById.putIfAbsent(d.getTrack().getId(), d.getTrack());
        }
        tracks.findByListedTrue().stream()
                .sorted(Comparator.comparing(Track::getName))
                .forEach(t -> trackById.putIfAbsent(t.getId(), t));

        Map<UUID, String> covers = new HashMap<>();
        for (TrackPhotoRepository.TrackCover c : photos.findCovers(trackById.keySet())) {
            covers.putIfAbsent(c.getTrackId(), photoFiles.url(c.getPhotoId()));
        }
        return trackById.values().stream().map(t -> new CatalogTrek(t.getSlug(), t.getName(), t.getRegion(),
                t.getDifficulty(), t.getDurationDays(), t.getSummary(), t.getMaxAltitudeM(), t.getSeasonLabel(),
                covers.get(t.getId()), byTrack.getOrDefault(t.getId(), List.of()).stream()
                        .map(d -> new CatalogDeparture(d.getId(), d.getStartDate(), d.getEndDate(), d.getPricePaise(),
                                d.getMaxGroupSize(), d.seatsLeft(), isBookable(d), guideBrief(d)))
                        .toList()))
                .toList();
    }

    /**
     * The trek page: route facts, itinerary, every upcoming published departure (whoever guides it), the page's
     * lists, the newest snow report, recent crowd counts, refund tiers and the charity share.
     */
    @Transactional(readOnly = true)
    public TrekPage trek(String slug) {
        Track track = tracks.findBySlug(slug).orElseThrow(CatalogService::trackNotFound);
        List<Departure> listed = departures.findListedForTrack(slug, DepartureStatus.PUBLISHED, today());
        Map<UUID, GuideCard> guides = guideCards(listed);
        List<TrekDeparture> rows = listed.stream()
                .map(d -> new TrekDeparture(d.getId(), d.getStartDate(), d.getEndDate(), d.getPricePaise(),
                        d.getMaxGroupSize(), d.seatsLeft(), isBookable(d), guides.get(d.getGuide().getId())))
                .toList();
        return new TrekPage(TrackDetail.of(track, photoFiles), rows, content.forTrack(track.getId()),
                snowReports.latest(track.getId()).orElse(null), snowReports.crowd(track.getId()),
                bookingProps.refundTiers().stream()
                        .map(t -> new TrekPage.RefundTier(t.minDaysBefore(), t.refundBps())).toList(),
                charity.name() == null ? null : new TrekPage.Charity(charity.name(), charity.bps()));
    }

    @Transactional(readOnly = true)
    public GuideProfile guide(UUID id) {
        User guide = users.findById(id)
                .filter(u -> u.getRole() == Role.GUIDE && !u.isDisabled())
                .orElseThrow(CatalogService::guideNotFound);
        TrekkerProfile profile = profiles.findById(id).orElse(null);

        List<DepartureRepository.LedCount> counts =
                departures.countByGuideAndTrack(Set.of(id), DepartureStatus.COMPLETED);
        Map<UUID, Track> ledTracks = tracks.findAllById(counts.stream().map(DepartureRepository.LedCount::getTrackId).toList())
                .stream().collect(Collectors.toMap(Track::getId, Function.identity()));
        List<GuideProfile.TrekLed> treks = counts.stream()
                .map(c -> new GuideProfile.TrekLed(TrackBrief.of(ledTracks.get(c.getTrackId())), c.getTimes()))
                .sorted(Comparator.comparingLong(GuideProfile.TrekLed::times).reversed())
                .toList();
        List<DepartureSummary> upcoming = departures.findListedForGuide(id, DepartureStatus.PUBLISHED, today())
                .stream().map(this::toSummary).toList();
        GuideDetailsResponse details = guideDetails.forGuides(Set.of(id)).get(id);
        RatingSummary rating = reviews.ratings(Set.of(id)).getOrDefault(id, RatingSummary.NONE);

        return new GuideProfile(guide.getId(), guide.getFullName(), avatars.url(guide.getAvatarKey()),
                profile == null ? null : profile.getHomeCity(), profile == null ? null : profile.getBio(),
                treks.stream().mapToLong(GuideProfile.TrekLed::times).sum(), details.yearsLeading(),
                details.languages(), details.certification(), details.certificationNumber(), details.quote(),
                rating.average(), rating.count(), reviews.forGuide(id), treks, upcoming);
    }

    /**
     * Each departure's guide with their home city, how often they've completed that departure's trek, their
     * credentials and rating.
     */
    private Map<UUID, GuideCard> guideCards(List<Departure> list) {
        Set<UUID> guideIds = list.stream().map(d -> d.getGuide().getId()).collect(Collectors.toSet());
        if (guideIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> homes = new HashMap<>();
        profiles.findAllById(guideIds).forEach(p -> homes.put(p.getUserId(), p.getHomeCity()));
        Map<String, Long> led = new HashMap<>();
        departures.countByGuideAndTrack(guideIds, DepartureStatus.COMPLETED)
                .forEach(c -> led.put(c.getGuideId() + "/" + c.getTrackId(), c.getTimes()));
        Map<UUID, GuideDetailsResponse> details = guideDetails.forGuides(guideIds);
        Map<UUID, RatingSummary> ratings = reviews.ratings(guideIds);

        Map<UUID, GuideCard> cards = new HashMap<>();
        for (Departure d : list) {
            User g = d.getGuide();
            // One trek per list (trek page) or one departure (departure page), so the key is unique per guide.
            GuideDetailsResponse gd = details.get(g.getId());
            RatingSummary rating = ratings.getOrDefault(g.getId(), RatingSummary.NONE);
            cards.putIfAbsent(g.getId(), new GuideCard(g.getId(), g.getFullName(), avatars.url(g.getAvatarKey()),
                    homes.get(g.getId()), led.getOrDefault(g.getId() + "/" + d.getTrack().getId(), 0L),
                    gd.yearsLeading(), gd.languages(), gd.certification(), gd.certificationNumber(), gd.quote(),
                    rating.average(), rating.count()));
        }
        return cards;
    }

    public static ApiException trackNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "TRACK_NOT_FOUND", "Track not found");
    }

    public static ApiException guideNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "GUIDE_NOT_FOUND", "Guide not found");
    }

    public static ApiException departureNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "DEPARTURE_NOT_FOUND", "Departure not found");
    }

    private DepartureSummary toSummary(Departure d) {
        return new DepartureSummary(d.getId(), TrackBrief.of(d.getTrack()), guideBrief(d), d.getStartDate(),
                d.getEndDate(), d.getPricePaise(), d.getMaxGroupSize(), d.seatsLeft(), isBookable(d));
    }

    public GuideBrief guideBrief(Departure d) {
        var guide = d.getGuide();
        return new GuideBrief(guide.getId(), guide.getFullName(), avatars.url(guide.getAvatarKey()));
    }

    private static YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.validation("month", "must look like 2026-10");
        }
    }

    private static Difficulty parseDifficulty(String difficulty) {
        if (difficulty == null || difficulty.isBlank()) {
            return null;
        }
        try {
            return Difficulty.valueOf(difficulty.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.validation("difficulty", "must be EASY, EASY_MODERATE, MODERATE or CHALLENGING");
        }
    }

    private static LocalDate later(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }
}
