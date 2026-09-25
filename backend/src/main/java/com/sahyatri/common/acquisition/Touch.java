package com.sahyatri.common.acquisition;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Where a visit came from, stored on {@code users} (first touch) and {@code bookings} (last touch). The owner
 * maps {@code seenAt} to its own column. Immutable once built.
 */
@Embeddable
public class Touch {

    static final int TAG_MAX = 200;
    static final int CLICK_ID_MAX = 500;
    static final int REFERRER_MAX = 1000;
    static final int PATH_MAX = 500;

    private static final Pattern HTTP_URL = Pattern.compile("^https?://\\S+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}");

    private String utmSource;
    private String utmMedium;
    private String utmCampaign;
    private String utmTerm;
    private String utmContent;
    private String gclid;
    private String fbclid;
    private String referrer;
    private String landingPath;
    private Instant seenAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type")
    private DeviceType deviceType;

    protected Touch() {
    }

    /**
     * Cleans what the browser sent: trims, drops control characters, truncates to the column limits, keeps only
     * http(s) referrers and paths starting with "/". Campaign tags are lower-cased so "Instagram" and
     * "instagram" group together. A time in the future becomes now. Null when there is nothing to keep.
     */
    public static Touch from(TouchRequest req, DeviceType device) {
        if (req == null && device == null) return null;
        Touch touch = new Touch();
        touch.deviceType = device;
        if (req != null) {
            touch.utmSource = tag(req.utmSource());
            touch.utmMedium = tag(req.utmMedium());
            touch.utmCampaign = tag(req.utmCampaign());
            touch.utmTerm = tag(req.utmTerm());
            touch.utmContent = tag(req.utmContent());
            touch.gclid = clean(req.gclid(), CLICK_ID_MAX);
            touch.fbclid = clean(req.fbclid(), CLICK_ID_MAX);
            String referrer = clean(req.referrer(), REFERRER_MAX);
            touch.referrer = referrer != null && HTTP_URL.matcher(referrer).matches() ? referrer : null;
            String path = clean(req.landingPath(), PATH_MAX);
            touch.landingPath = path != null && path.startsWith("/") ? path : null;
            Instant now = Instant.now();
            touch.seenAt = req.seenAt() == null || req.seenAt().isAfter(now) ? now : req.seenAt();
        }
        return touch;
    }

    private static String tag(String value) {
        String cleaned = clean(value, TAG_MAX);
        return cleaned == null ? null : cleaned.toLowerCase(Locale.ROOT);
    }

    private static String clean(String value, int max) {
        if (value == null) return null;
        String cleaned = CONTROL.matcher(value).replaceAll("").trim();
        if (cleaned.isEmpty()) return null;
        if (cleaned.length() <= max) return cleaned;
        // Never cut a surrogate pair in half.
        int end = Character.isHighSurrogate(cleaned.charAt(max - 1)) ? max - 1 : max;
        return cleaned.substring(0, end);
    }

    public String getUtmSource() {
        return utmSource;
    }

    public String getUtmMedium() {
        return utmMedium;
    }

    public String getUtmCampaign() {
        return utmCampaign;
    }

    public String getUtmTerm() {
        return utmTerm;
    }

    public String getUtmContent() {
        return utmContent;
    }

    public String getGclid() {
        return gclid;
    }

    public String getFbclid() {
        return fbclid;
    }

    public String getReferrer() {
        return referrer;
    }

    public String getLandingPath() {
        return landingPath;
    }

    public Instant getSeenAt() {
        return seenAt;
    }

    public DeviceType getDeviceType() {
        return deviceType;
    }
}
