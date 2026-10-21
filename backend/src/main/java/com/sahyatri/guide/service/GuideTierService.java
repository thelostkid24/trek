package com.sahyatri.guide.service;

import com.sahyatri.catalog.entity.DepartureStatus;
import com.sahyatri.common.config.GuideTierProperties;
import com.sahyatri.common.config.GuideTierProperties.Threshold;
import com.sahyatri.guide.dto.GuideTier;
import com.sahyatri.guide.entity.CertificationType;
import com.sahyatri.guide.entity.GuideCertification;
import com.sahyatri.guide.repository.GuideCertificationRepository;
import com.sahyatri.review.dto.RatingSummary;
import com.sahyatri.review.service.ReviewService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Guide tiers (§7.21.3), worked out on every read and never stored (law 8). Counts only departures completed with
 * us and the §7.14 ratings; treks led before Sahyātri don't count. The highest tier met wins.
 */
@Service
public class GuideTierService {

    private final GuideTierProperties props;
    private final GuideCertificationRepository certifications;
    private final ReviewService reviews;

    public GuideTierService(GuideTierProperties props, GuideCertificationRepository certifications,
                            ReviewService reviews) {
        this.props = props;
        this.certifications = certifications;
        this.reviews = reviews;
    }

    /** The guide's tier given their certificates (already loaded), or null. */
    public GuideTier tierFor(UUID guideId, List<GuideCertification> certs, LocalDate today) {
        long treks = certifications.countDepartures(guideId, DepartureStatus.COMPLETED);
        RatingSummary rating = reviews.ratings(Set.of(guideId)).getOrDefault(guideId, RatingSummary.NONE);
        return tierFor(props, holds(certs, CertificationType.BMC, today), holds(certs, CertificationType.AMC, today),
                treks, rating);
    }

    public static GuideTier tierFor(GuideTierProperties p, boolean verifiedBmc, boolean verifiedAmc, long treks,
                             RatingSummary rating) {
        if (verifiedAmc && meets(p.summit(), treks, rating)) {
            return GuideTier.SUMMIT_GUIDE;
        }
        if (meets(p.senior(), treks, rating)) {
            return GuideTier.SENIOR_GUIDE;
        }
        if (verifiedBmc && meets(p.trail(), treks, rating)) {
            return GuideTier.TRAIL_GUIDE;
        }
        return null;
    }

    private static boolean meets(Threshold t, long treks, RatingSummary rating) {
        if (treks < t.minTreks()) {
            return false;
        }
        if (t.minReviews() <= 0) {
            return true;
        }
        return rating.count() >= t.minReviews() && rating.average() != null && rating.average() >= t.minRating();
    }

    private static boolean holds(List<GuideCertification> certs, CertificationType type, LocalDate today) {
        return certs.stream().anyMatch(c -> c.getType() == type && c.isVerified() && !c.isExpired(today));
    }
}
