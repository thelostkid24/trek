package com.sahyatri.guide.service;

import com.sahyatri.common.audit.AuditLog;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.guide.entity.GuideDetails;
import com.sahyatri.guide.entity.GuideSlugHistory;
import com.sahyatri.guide.repository.GuideDetailsRepository;
import com.sahyatri.guide.repository.GuideSlugHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The guide profile's URL, /guides/{slug} (§7.21). Made from the name when a guide gets their first profile row;
 * only an admin changes it. Every slug a guide had keeps resolving to them, and no other guide may take it.
 */
@Service
public class GuideSlugService {

    static final int MAX_LENGTH = 60;
    private static final int BASE_LENGTH = 50;

    private final GuideDetailsRepository details;
    private final GuideSlugHistoryRepository history;
    private final AuditLog audit;

    public GuideSlugService(GuideDetailsRepository details, GuideSlugHistoryRepository history, AuditLog audit) {
        this.details = details;
        this.history = history;
        this.audit = audit;
    }

    /** The guide's profile row, created if missing, with a slug from {@code fullName} if it has none yet. */
    @Transactional
    public GuideDetails ensure(UUID guideId, String fullName) {
        GuideDetails d = details.findById(guideId).orElseGet(() -> new GuideDetails(guideId));
        if (d.getSlug() == null) {
            d.setSlug(uniqueFrom(fullName));
            d = details.saveAndFlush(d);
        }
        return d;
    }

    /** Admin only. The old slug goes to history so links to it keep working. */
    @Transactional
    public String change(UUID adminId, UUID guideId, String fullName, String slug) {
        GuideDetails d = ensure(guideId, fullName);
        String previous = d.getSlug();
        if (slug.equals(previous)) {
            return previous;
        }
        Optional<GuideSlugHistory> old = history.findById(slug);
        if (details.existsBySlug(slug) || old.filter(h -> !h.getGuideId().equals(guideId)).isPresent()) {
            throw ApiException.conflict("SLUG_TAKEN", "Another guide uses or used this address");
        }
        // Taking back one of their own old slugs: it's current again, not history.
        old.ifPresent(history::delete);
        history.saveAndFlush(new GuideSlugHistory(previous, guideId));
        d.setSlug(slug);
        details.saveAndFlush(d);
        audit.record(adminId, "GUIDE_SLUG_CHANGED", "USER", guideId, Map.of("from", previous, "to", slug));
        return slug;
    }

    /** The guide a profile URL names: their id, their current slug or one they used before. */
    @Transactional(readOnly = true)
    public Optional<UUID> resolve(String key) {
        try {
            return Optional.of(UUID.fromString(key));
        } catch (IllegalArgumentException notAnId) {
            String slug = key.toLowerCase(Locale.ROOT);
            return details.findBySlug(slug).map(GuideDetails::getUserId)
                    .or(() -> history.findById(slug).map(GuideSlugHistory::getGuideId));
        }
    }

    private String uniqueFrom(String fullName) {
        String base = base(fullName);
        String slug = base;
        for (int n = 2; taken(slug); n++) {
            slug = base + "-" + n;
        }
        return slug;
    }

    private boolean taken(String slug) {
        return details.existsBySlug(slug) || history.existsById(slug);
    }

    /** "Shrey Rāi" → "shrey-rai" (accents dropped); "guide" when nothing usable is left. */
    static String base(String fullName) {
        String ascii = Normalizer.normalize(fullName == null ? "" : fullName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        if (slug.length() > BASE_LENGTH) {
            slug = slug.substring(0, BASE_LENGTH);
        }
        slug = slug.replaceAll("^-+|-+$", "");
        return slug.isEmpty() ? "guide" : slug;
    }
}
