package com.sahyatri.guide.dto;

import com.sahyatri.guide.entity.PledgeKind;

import java.util.List;

/**
 * The public page's certificates and tier (§7.21.3). {@code tier} is null when none is met; {@code verified} is true
 * when any certificate is verified and unexpired. {@code items} holds every certificate with its status;
 * {@code pledges} the safety pledges an admin recorded.
 */
public record GuideCredentials(GuideTier tier, boolean verified, List<PublicCertification> items,
                               List<PledgeKind> pledges) {
}
