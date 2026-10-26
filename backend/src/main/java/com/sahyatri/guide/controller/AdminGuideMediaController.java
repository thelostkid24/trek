package com.sahyatri.guide.controller;

import com.sahyatri.guide.dto.GuideMediaItem;
import com.sahyatri.guide.dto.GuideMediaList;
import com.sahyatri.guide.service.GuideMediaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/** A guide's gallery, managed by an admin. Contract: docs/TRD.md §7.21.4, §7.23. ADMIN only (/api/admin/**). */
@RestController
@RequestMapping("/api/admin/guides/{id}/media")
public class AdminGuideMediaController {

    private final GuideMediaService media;

    public AdminGuideMediaController(GuideMediaService media) {
        this.media = media;
    }

    @GetMapping
    public GuideMediaList list(@PathVariable UUID id) {
        media.requireGuide(id);
        return media.list(id);
    }

    @PostMapping(path = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public GuideMediaItem uploadPhoto(@PathVariable UUID id, @RequestPart("file") MultipartFile file,
                                      @RequestParam(required = false) String caption) {
        media.requireGuide(id);
        return media.uploadPhoto(id, file, caption);
    }

    @DeleteMapping("/{mediaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @PathVariable UUID mediaId) {
        media.delete(id, mediaId);
    }
}
