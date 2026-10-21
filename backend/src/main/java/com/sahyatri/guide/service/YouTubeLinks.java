package com.sahyatri.guide.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The video id in a YouTube link (§7.21.4): youtube.com/watch?v=…, youtu.be/…, /shorts/…, /embed/…, /live/…, on
 * www., m. and music. hosts and youtube-nocookie.com. Anything else, including other sites, is refused.
 */
final class YouTubeLinks {

    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Set<String> HOSTS = Set.of("youtube.com", "www.youtube.com", "m.youtube.com",
            "music.youtube.com", "youtube-nocookie.com", "www.youtube-nocookie.com");
    private static final Set<String> PATH_PREFIXES = Set.of("shorts", "embed", "live", "v");

    private YouTubeLinks() {
    }

    static Optional<String> videoId(String link) {
        String text = link.trim();
        if (!text.matches("(?i)^https?://.*")) {
            text = "https://" + text;
        }
        URI uri;
        try {
            uri = new URI(text);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath();
        String[] parts = path.replaceFirst("^/+", "").split("/");
        String candidate = null;
        if (host.equals("youtu.be")) {
            candidate = parts[0];
        } else if (HOSTS.contains(host)) {
            if (path.equals("/watch")) {
                candidate = query(uri.getRawQuery(), "v");
            } else if (parts.length >= 2 && PATH_PREFIXES.contains(parts[0])) {
                candidate = parts[1];
            }
        }
        return candidate != null && ID.matcher(candidate).matches() ? Optional.of(candidate) : Optional.empty();
    }

    private static String query(String rawQuery, String name) {
        if (rawQuery == null) {
            return null;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }
}
