package com.sahyatri.savedguide.service;

import com.sahyatri.auth.entity.Role;
import com.sahyatri.auth.entity.User;
import com.sahyatri.auth.repository.UserRepository;
import com.sahyatri.catalog.service.CatalogService;
import com.sahyatri.common.storage.AvatarFiles;
import com.sahyatri.guide.dto.GuideDetailsResponse;
import com.sahyatri.guide.service.GuideDetailsService;
import com.sahyatri.profile.entity.TrekkerProfile;
import com.sahyatri.profile.repository.TrekkerProfileRepository;
import com.sahyatri.review.dto.RatingSummary;
import com.sahyatri.review.service.ReviewService;
import com.sahyatri.savedguide.dto.SavedGuideItem;
import com.sahyatri.savedguide.entity.SavedGuide;
import com.sahyatri.savedguide.repository.SavedGuideRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A trekker's saved guides ("My Fav", docs/TRD.md §7.21.7). The list is private: there is no public count and the
 * guide isn't told. Saves of a guide who is later disabled stay but aren't listed.
 */
@Service
public class SavedGuideService {

    private final SavedGuideRepository saved;
    private final UserRepository users;
    private final TrekkerProfileRepository profiles;
    private final GuideDetailsService guideDetails;
    private final ReviewService reviews;
    private final AvatarFiles avatars;

    public SavedGuideService(SavedGuideRepository saved, UserRepository users, TrekkerProfileRepository profiles,
                             GuideDetailsService guideDetails, ReviewService reviews, AvatarFiles avatars) {
        this.saved = saved;
        this.users = users;
        this.profiles = profiles;
        this.guideDetails = guideDetails;
        this.reviews = reviews;
        this.avatars = avatars;
    }

    /** Newest save first. */
    @Transactional(readOnly = true)
    public List<SavedGuideItem> list(UUID userId) {
        List<SavedGuide> rows = saved.findByUserIdOrderByCreatedAtDesc(userId);
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<UUID> ids = rows.stream().map(SavedGuide::getGuideId).collect(Collectors.toSet());
        Map<UUID, User> guides = users.findAllById(ids).stream()
                .filter(u -> u.getRole() == Role.GUIDE && !u.isDisabled())
                .collect(Collectors.toMap(User::getId, Function.identity()));
        if (guides.isEmpty()) {
            return List.of();
        }
        Map<UUID, TrekkerProfile> homes = new HashMap<>();
        profiles.findAllById(guides.keySet()).forEach(p -> homes.put(p.getUserId(), p));
        Map<UUID, GuideDetailsResponse> details = guideDetails.forGuides(guides.keySet());
        Map<UUID, RatingSummary> ratings = reviews.ratings(guides.keySet());
        return rows.stream()
                .filter(r -> guides.containsKey(r.getGuideId()))
                .map(r -> {
                    User g = guides.get(r.getGuideId());
                    TrekkerProfile home = homes.get(g.getId());
                    RatingSummary rating = ratings.getOrDefault(g.getId(), RatingSummary.NONE);
                    return new SavedGuideItem(g.getId(), details.get(g.getId()).slug(), g.getFullName(),
                            avatars.url(g.getAvatarKey()), home == null ? null : home.getHomeCity(),
                            rating.average(), rating.count(), r.getCreatedAt());
                })
                .toList();
    }

    /** Idempotent: saving a saved guide changes nothing. Only active guides can be saved. */
    @Transactional
    public void save(UUID userId, UUID guideId) {
        users.findById(guideId)
                .filter(u -> u.getRole() == Role.GUIDE && !u.isDisabled())
                .orElseThrow(CatalogService::guideNotFound);
        saved.save(userId, guideId, Instant.now());
    }

    /** Idempotent: unsaving a guide that isn't saved (or doesn't exist) is fine. */
    @Transactional
    public void remove(UUID userId, UUID guideId) {
        saved.remove(userId, guideId);
    }
}
