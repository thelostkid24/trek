package com.sahyatri.guide.service;

import com.sahyatri.account.service.AvatarService;
import com.sahyatri.auth.entity.Role;
import com.sahyatri.auth.entity.User;
import com.sahyatri.auth.repository.UserRepository;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.common.storage.AvatarFiles;
import com.sahyatri.guide.dto.GuideProfileRequest;
import com.sahyatri.guide.dto.GuideProfileResponse;
import com.sahyatri.profile.entity.TrekkerProfile;
import com.sahyatri.profile.repository.TrekkerProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * A guide's public profile (§7.12): name and photo on the account, home city and bio on the profile row the guide
 * pages already read. An admin sets them here; the guide edits the same fields from /guide (§7.21).
 */
@Service
public class GuideProfileService {

    private final UserRepository users;
    private final TrekkerProfileRepository profiles;
    private final AvatarService avatars;
    private final AvatarFiles avatarFiles;
    private final GuideSlugService slugs;

    public GuideProfileService(UserRepository users, TrekkerProfileRepository profiles, AvatarService avatars,
                               AvatarFiles avatarFiles, GuideSlugService slugs) {
        this.users = users;
        this.profiles = profiles;
        this.avatars = avatars;
        this.avatarFiles = avatarFiles;
        this.slugs = slugs;
    }

    @Transactional
    public GuideProfileResponse get(UUID guideId) {
        return toResponse(requireGuide(guideId));
    }

    @Transactional
    public GuideProfileResponse update(UUID guideId, GuideProfileRequest req) {
        User guide = requireGuide(guideId);
        guide.setFullName(req.fullName().trim());
        users.save(guide);
        TrekkerProfile profile = profiles.findById(guideId).orElseGet(() -> TrekkerProfile.newFor(guideId));
        profile.setHomeCity(blankToNull(req.homeCity()));
        profile.setBio(blankToNull(req.bio()));
        profiles.saveAndFlush(profile);
        return toResponse(guide);
    }

    /** A new profile URL; the old one keeps redirecting (§7.21). */
    @Transactional
    public GuideProfileResponse changeSlug(UUID adminId, UUID guideId, String slug) {
        User guide = requireGuide(guideId);
        slugs.change(adminId, guideId, guide.getFullName(), slug);
        return toResponse(guide);
    }

    public GuideProfileResponse uploadPhoto(UUID guideId, MultipartFile file) {
        return toResponse(avatars.replace(requireGuide(guideId), file));
    }

    public GuideProfileResponse removePhoto(UUID guideId) {
        return toResponse(avatars.clear(requireGuide(guideId)));
    }

    private GuideProfileResponse toResponse(User guide) {
        TrekkerProfile p = profiles.findById(guide.getId()).orElse(null);
        String slug = slugs.ensure(guide.getId(), guide.getFullName()).getSlug();
        return new GuideProfileResponse(guide.getId(), slug, guide.getFullName(), avatarFiles.url(guide.getAvatarKey()),
                p == null ? null : p.getHomeCity(), p == null ? null : p.getBio());
    }

    private User requireGuide(UUID guideId) {
        return users.findById(guideId)
                .filter(u -> u.getRole() == Role.GUIDE)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GUIDE_NOT_FOUND", "Guide not found"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
