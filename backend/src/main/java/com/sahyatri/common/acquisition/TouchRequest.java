package com.sahyatri.common.acquisition;

import java.time.Instant;

/**
 * One visit as the browser saw it: campaign tags and ad click ids from the landing URL, the outside referrer,
 * the landing path and when it happened. Values come from links we don't control, so they are cleaned and
 * truncated ({@link Touch#from}), never rejected.
 */
public record TouchRequest(
        String utmSource,
        String utmMedium,
        String utmCampaign,
        String utmTerm,
        String utmContent,
        String gclid,
        String fbclid,
        String referrer,
        String landingPath,
        Instant seenAt) {
}
