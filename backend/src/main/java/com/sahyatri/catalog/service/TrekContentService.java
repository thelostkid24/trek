package com.sahyatri.catalog.service;

import com.sahyatri.catalog.dto.ContentItem;
import com.sahyatri.catalog.entity.ContentKind;
import com.sahyatri.catalog.entity.TrekContentItem;
import com.sahyatri.catalog.repository.TrekContentItemRepository;
import com.sahyatri.common.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The lists on a trek page (§7.11): what's included, safety, FAQs and so on. Shared lists show on every trek, then
 * the trek's own items follow. Every kind is always present in a response, empty when there is nothing.
 */
@Service
public class TrekContentService {

    private final TrekContentItemRepository items;
    private final TrackAdminService tracks;

    public TrekContentService(TrekContentItemRepository items, TrackAdminService tracks) {
        this.items = items;
        this.tracks = tracks;
    }

    /** Shared lists (trackId null) or one track's own lists, for the admin editor. */
    @Transactional(readOnly = true)
    public Map<ContentKind, List<ContentItem>> own(UUID trackId) {
        if (trackId != null) {
            tracks.require(trackId);
        }
        return group(trackId == null
                ? items.findByTrackIdIsNullOrderByKindAscPositionAsc()
                : items.findByTrackIdOrderByKindAscPositionAsc(trackId));
    }

    /** What the trek page shows: shared items first, then the track's own. */
    @Transactional(readOnly = true)
    public Map<ContentKind, List<ContentItem>> forTrack(UUID trackId) {
        return group(items.findForTrack(trackId));
    }

    /** Replaces one list of the shared content (trackId null) or of a track. */
    @Transactional
    public Map<ContentKind, List<ContentItem>> replace(UUID trackId, ContentKind kind, List<ContentItem> list) {
        List<ContentItem> clean = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            ContentItem item = list.get(i);
            String badge = TrackAdminService.blankToNull(item.badge());
            String title = TrackAdminService.blankToNull(item.title());
            if (kind.needsTitle() && title == null) {
                throw ApiException.validation("items[" + i + "].title", "must not be blank");
            }
            if (!kind.allowsBadge() && badge != null) {
                throw ApiException.validation("items[" + i + "].badge", "only cards have a badge");
            }
            clean.add(new ContentItem(badge, title, item.body().trim()));
        }
        if (trackId == null) {
            items.deleteShared(kind);
        } else {
            tracks.require(trackId);
            items.deleteForTrack(trackId, kind);
        }
        for (int i = 0; i < clean.size(); i++) {
            ContentItem c = clean.get(i);
            items.save(new TrekContentItem(trackId, kind, i, c.badge(), c.title(), c.body()));
        }
        items.flush();
        return own(trackId);
    }

    private static Map<ContentKind, List<ContentItem>> group(List<TrekContentItem> rows) {
        Map<ContentKind, List<ContentItem>> byKind = new EnumMap<>(ContentKind.class);
        for (ContentKind kind : ContentKind.values()) {
            byKind.put(kind, new ArrayList<>());
        }
        rows.forEach(r -> byKind.get(r.getKind()).add(ContentItem.of(r)));
        return byKind;
    }
}
