package com.sahyatri.guide.service;

import com.sahyatri.account.service.AvatarService;
import com.sahyatri.auth.entity.Role;
import com.sahyatri.auth.entity.User;
import com.sahyatri.auth.repository.UserRepository;
import com.sahyatri.auth.service.CurrentUser;
import com.sahyatri.catalog.entity.DepartureStatus;
import com.sahyatri.catalog.entity.Track;
import com.sahyatri.catalog.repository.DepartureRepository;
import com.sahyatri.catalog.repository.TrackRepository;
import com.sahyatri.common.config.CatalogProperties;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.common.storage.AvatarFiles;
import com.sahyatri.common.storage.FileStorage;
import com.sahyatri.common.storage.GuideCoverFiles;
import com.sahyatri.common.storage.Images;
import com.sahyatri.guide.dto.GuidePageExtras;
import com.sahyatri.guide.dto.Milestone;
import com.sahyatri.guide.dto.OwnGuideProfile;
import com.sahyatri.guide.dto.OwnGuideProfileRequest;
import com.sahyatri.guide.dto.RouteNote;
import com.sahyatri.guide.dto.RouteOption;
import com.sahyatri.guide.dto.Specialty;
import com.sahyatri.guide.entity.GuideDetails;
import com.sahyatri.guide.entity.GuideMilestone;
import com.sahyatri.guide.entity.GuideProfileSpecialty;
import com.sahyatri.guide.entity.GuideRouteNote;
import com.sahyatri.guide.entity.GuideSpecialty;
import com.sahyatri.guide.repository.GuideDetailsRepository;
import com.sahyatri.guide.repository.GuideMilestoneRepository;
import com.sahyatri.guide.repository.GuidePriorTrekRepository;
import com.sahyatri.guide.repository.GuideProfileSpecialtyRepository;
import com.sahyatri.guide.repository.GuideRouteNoteRepository;
import com.sahyatri.guide.repository.GuideSpecialtyRepository;
import com.sahyatri.profile.entity.TrekkerProfile;
import com.sahyatri.profile.repository.TrekkerProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The guide's own profile (§7.21): name, photo, cover, home, intro, bio, languages, the year they started, their
 * specialties, a note per route and their career milestones. The guide is always the token's subject, never an id
 * from the request. Course credentials, the slug and treks led before Sahyātri stay with admins (§7.12).
 */
@Service
public class OwnGuideProfileService {

    static final int COVER_WIDTH = 1800;
    static final int COVER_HEIGHT = 600;

    private final CurrentUser currentUser;
    private final UserRepository users;
    private final TrekkerProfileRepository profiles;
    private final GuideDetailsRepository details;
    private final GuideSlugService slugs;
    private final GuideSpecialtyRepository specialties;
    private final GuideProfileSpecialtyRepository picked;
    private final GuideMilestoneRepository milestones;
    private final GuideRouteNoteRepository routeNotes;
    private final GuidePriorTrekRepository priorTreks;
    private final DepartureRepository departures;
    private final TrackRepository tracks;
    private final AvatarService avatars;
    private final AvatarFiles avatarFiles;
    private final FileStorage storage;
    private final GuideCoverFiles coverFiles;
    private final CatalogProperties catalog;

    public OwnGuideProfileService(CurrentUser currentUser, UserRepository users, TrekkerProfileRepository profiles,
                                  GuideDetailsRepository details, GuideSlugService slugs,
                                  GuideSpecialtyRepository specialties, GuideProfileSpecialtyRepository picked,
                                  GuideMilestoneRepository milestones, GuideRouteNoteRepository routeNotes,
                                  GuidePriorTrekRepository priorTreks, DepartureRepository departures,
                                  TrackRepository tracks, AvatarService avatars, AvatarFiles avatarFiles,
                                  FileStorage storage, GuideCoverFiles coverFiles, CatalogProperties catalog) {
        this.currentUser = currentUser;
        this.users = users;
        this.profiles = profiles;
        this.details = details;
        this.slugs = slugs;
        this.specialties = specialties;
        this.picked = picked;
        this.milestones = milestones;
        this.routeNotes = routeNotes;
        this.priorTreks = priorTreks;
        this.departures = departures;
        this.tracks = tracks;
        this.avatars = avatars;
        this.avatarFiles = avatarFiles;
        this.storage = storage;
        this.coverFiles = coverFiles;
        this.catalog = catalog;
    }

    @Transactional
    public OwnGuideProfile get(UUID guideId) {
        return toResponse(requireGuide(guideId));
    }

    @Transactional
    public OwnGuideProfile update(UUID guideId, OwnGuideProfileRequest req) {
        User guide = requireGuide(guideId);
        int thisYear = today().getYear();
        if (req.leadingSince() != null && req.leadingSince() > thisYear) {
            throw ApiException.validation("leading_since", "can't be in the future");
        }
        List<Milestone> newMilestones = req.milestones() == null ? List.of() : req.milestones();
        for (int i = 0; i < newMilestones.size(); i++) {
            if (newMilestones.get(i).year() > thisYear) {
                throw ApiException.validation("milestones[" + i + "].year", "can't be in the future");
            }
        }
        List<UUID> specialtyIds = req.specialtyIds() == null ? List.of() : req.specialtyIds();
        if (new HashSet<>(specialtyIds).size() != specialtyIds.size()) {
            throw ApiException.validation("specialty_ids", "lists a specialty twice");
        }
        if (specialties.findAllById(specialtyIds).size() != specialtyIds.size()) {
            throw ApiException.validation("specialty_ids", "names a specialty that doesn't exist");
        }
        List<RouteNote> notes = req.routeNotes() == null ? List.of() : req.routeNotes();
        Set<UUID> routeIds = routeIds(guideId);
        Set<UUID> noted = new HashSet<>();
        for (int i = 0; i < notes.size(); i++) {
            UUID trackId = notes.get(i).trackId();
            if (!noted.add(trackId)) {
                throw ApiException.validation("route_notes[" + i + "].track_id", "lists a trek twice");
            }
            if (!routeIds.contains(trackId)) {
                throw ApiException.validation("route_notes[" + i + "].track_id", "isn't a trek you lead");
            }
        }

        guide.setFullName(req.fullName().trim());
        users.save(guide);
        TrekkerProfile profile = profiles.findById(guideId).orElseGet(() -> TrekkerProfile.newFor(guideId));
        profile.setHomeCity(blankToNull(req.homeCity()));
        profile.setBio(blankToNull(req.bio()));
        profiles.saveAndFlush(profile);
        GuideDetails d = slugs.ensure(guideId, guide.getFullName());
        d.updateOwn(req.leadingSince(), blankToNull(req.languages()), blankToNull(req.quote()));
        details.saveAndFlush(d);

        picked.deleteForGuide(guideId);
        picked.saveAllAndFlush(IntStream.range(0, specialtyIds.size())
                .mapToObj(i -> new GuideProfileSpecialty(guideId, specialtyIds.get(i), i)).toList());
        milestones.deleteForGuide(guideId);
        milestones.saveAllAndFlush(IntStream.range(0, newMilestones.size())
                .mapToObj(i -> new GuideMilestone(guideId, newMilestones.get(i).year(),
                        newMilestones.get(i).text().trim(), i)).toList());
        routeNotes.deleteForGuide(guideId);
        routeNotes.saveAllAndFlush(notes.stream()
                .map(n -> new GuideRouteNote(guideId, n.trackId(), n.note().trim())).toList());
        return toResponse(guide);
    }

    public OwnGuideProfile uploadPhoto(UUID guideId, MultipartFile file) {
        return get(avatars.replace(requireGuide(guideId), file).getId());
    }

    public OwnGuideProfile removePhoto(UUID guideId) {
        return get(avatars.clear(requireGuide(guideId)).getId());
    }

    /** A 3:1 centre crop at 1800×600, re-encoded like every other upload; the old file is deleted. */
    @Transactional
    public OwnGuideProfile uploadCover(UUID guideId, MultipartFile file) {
        User guide = requireGuide(guideId);
        byte[] jpeg = toCoverJpeg(Images.read(file));
        UUID key = UUID.randomUUID();
        storage.put(GuideCoverFiles.storageKey(key), jpeg);
        GuideDetails d = slugs.ensure(guideId, guide.getFullName());
        String previous = d.getCoverKey();
        d.setCoverKey(key.toString());
        details.saveAndFlush(d);
        deleteCoverQuietly(previous);
        return toResponse(guide);
    }

    @Transactional
    public OwnGuideProfile removeCover(UUID guideId) {
        User guide = requireGuide(guideId);
        GuideDetails d = slugs.ensure(guideId, guide.getFullName());
        String previous = d.getCoverKey();
        if (previous != null) {
            d.setCoverKey(null);
            details.saveAndFlush(d);
            deleteCoverQuietly(previous);
        }
        return toResponse(guide);
    }

    /** The parts of the guide's own profile the public page shows. Read-only; no row is created. */
    @Transactional(readOnly = true)
    public GuidePageExtras pageExtras(UUID guideId) {
        String coverKey = details.findById(guideId).map(GuideDetails::getCoverKey).orElse(null);
        Map<UUID, String> names = specialties.findAll().stream()
                .collect(Collectors.toMap(GuideSpecialty::getId, GuideSpecialty::getName));
        List<String> picks = picked.findByGuideId(guideId).stream()
                .sorted(Comparator.comparingInt(GuideProfileSpecialty::getPosition))
                .map(s -> names.get(s.getSpecialtyId())).toList();
        List<Milestone> career = milestones.findByGuideIdOrderByPositionAsc(guideId).stream()
                .map(m -> new Milestone(m.getYear(), m.getText())).toList();
        Map<UUID, String> notes = routeNotes.findByGuideId(guideId).stream()
                .collect(Collectors.toMap(GuideRouteNote::getTrackId, GuideRouteNote::getNote));
        return new GuidePageExtras(coverFiles.url(coverKey), picks, career, notes);
    }

    static byte[] toCoverJpeg(byte[] bytes) {
        BufferedImage source = Images.decode(bytes);
        int w = source.getWidth();
        int h = source.getHeight();
        // Widest 3:1 window that fits, centred.
        int cropW = Math.min(w, h * 3);
        int cropH = cropW / 3;
        return Images.encodeJpeg(Images.draw(source, (w - cropW) / 2, (h - cropH) / 2, cropW, cropH,
                COVER_WIDTH, COVER_HEIGHT));
    }

    private void deleteCoverQuietly(String coverKey) {
        if (coverKey == null) {
            return;
        }
        try {
            storage.delete(GuideCoverFiles.storageKey(UUID.fromString(coverKey)));
        } catch (RuntimeException ignored) {
            // An orphaned file is harmless; the profile change already succeeded.
        }
    }

    private OwnGuideProfile toResponse(User guide) {
        UUID id = guide.getId();
        GuideDetails d = slugs.ensure(id, guide.getFullName());
        TrekkerProfile p = profiles.findById(id).orElse(null);
        List<UUID> specialtyIds = picked.findByGuideId(id).stream()
                .sorted(Comparator.comparingInt(GuideProfileSpecialty::getPosition))
                .map(GuideProfileSpecialty::getSpecialtyId).toList();
        List<Milestone> career = milestones.findByGuideIdOrderByPositionAsc(id).stream()
                .map(m -> new Milestone(m.getYear(), m.getText())).toList();
        List<RouteNote> notes = routeNotes.findByGuideId(id).stream()
                .map(n -> new RouteNote(n.getTrackId(), n.getNote())).toList();
        List<Specialty> options = specialties.findAllByOrderByPositionAscNameAsc().stream()
                .map(s -> new Specialty(s.getId(), s.getName())).toList();
        List<RouteOption> routes = tracks.findAllById(routeIds(id)).stream()
                .sorted(Comparator.comparing(Track::getName))
                .map(t -> new RouteOption(t.getId(), t.getName())).toList();
        return new OwnGuideProfile(id, d.getSlug(), guide.getFullName(), avatarFiles.url(guide.getAvatarKey()),
                coverFiles.url(d.getCoverKey()), p == null ? null : p.getHomeCity(), p == null ? null : p.getBio(),
                d.getQuote(), d.getLanguages(), d.getLeadingSince(), specialtyIds, career, notes, options, routes);
    }

    /** Treks the guide has completed with us, led before Sahyātri, or leads on an upcoming published date. */
    private Set<UUID> routeIds(UUID guideId) {
        Set<UUID> ids = new LinkedHashSet<>();
        departures.countByGuideAndTrack(Set.of(guideId), DepartureStatus.COMPLETED).forEach(c -> ids.add(c.getTrackId()));
        priorTreks.findByGuideIdIn(Set.of(guideId)).forEach(p -> ids.add(p.getTrackId()));
        departures.findListedForGuide(guideId, DepartureStatus.PUBLISHED, today())
                .forEach(dep -> ids.add(dep.getTrack().getId()));
        return ids;
    }

    private User requireGuide(UUID guideId) {
        User user = currentUser.require(guideId);
        if (user.getRole() != Role.GUIDE) {
            throw ApiException.forbidden("Guides only");
        }
        return user;
    }

    private LocalDate today() {
        return LocalDate.now(catalog.zone());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
