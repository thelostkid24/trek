package com.sahyatri.catalog.controller;

import com.sahyatri.catalog.dto.ContentItem;
import com.sahyatri.catalog.dto.ContentListRequest;
import com.sahyatri.catalog.entity.ContentKind;
import com.sahyatri.catalog.service.TrekContentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Contract: docs/TRD.md §7.11. Shared lists and each track's own. ADMIN only (/api/admin/**). */
@RestController
@RequestMapping("/api/admin")
public class AdminContentController {

    private final TrekContentService content;

    public AdminContentController(TrekContentService content) {
        this.content = content;
    }

    @GetMapping("/content")
    public Map<ContentKind, List<ContentItem>> shared() {
        return content.own(null);
    }

    @PutMapping("/content/{kind}")
    public Map<ContentKind, List<ContentItem>> replaceShared(@PathVariable ContentKind kind,
                                                             @Valid @RequestBody ContentListRequest req) {
        return content.replace(null, kind, req.items());
    }

    @GetMapping("/tracks/{id}/content")
    public Map<ContentKind, List<ContentItem>> track(@PathVariable UUID id) {
        return content.own(id);
    }

    @PutMapping("/tracks/{id}/content/{kind}")
    public Map<ContentKind, List<ContentItem>> replaceForTrack(@PathVariable UUID id, @PathVariable ContentKind kind,
                                                               @Valid @RequestBody ContentListRequest req) {
        return content.replace(id, kind, req.items());
    }
}
