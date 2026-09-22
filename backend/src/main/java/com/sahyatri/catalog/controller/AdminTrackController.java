package com.sahyatri.catalog.controller;

import com.sahyatri.catalog.dto.TrackListingRequest;
import com.sahyatri.catalog.dto.TrackPhotoResponse;
import com.sahyatri.catalog.dto.TrackRequest;
import com.sahyatri.catalog.dto.TrackResponse;
import com.sahyatri.catalog.service.TrackAdminService;
import com.sahyatri.catalog.service.TrackPhotoService;
import com.sahyatri.common.web.ItemsResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/** Contract: docs/TRD.md §7.5 (photos §7.8). ADMIN role enforced by SecurityConfig (/api/admin/**). */
@RestController
@RequestMapping("/api/admin/tracks")
public class AdminTrackController {

    private final TrackAdminService tracks;
    private final TrackPhotoService photos;

    public AdminTrackController(TrackAdminService tracks, TrackPhotoService photos) {
        this.tracks = tracks;
        this.photos = photos;
    }

    @GetMapping
    public ItemsResponse<TrackResponse> list() {
        return new ItemsResponse<>(tracks.list());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TrackResponse create(@Valid @RequestBody TrackRequest req) {
        return tracks.create(req);
    }

    @PutMapping("/{id}")
    public TrackResponse update(@PathVariable UUID id, @Valid @RequestBody TrackRequest req) {
        return tracks.update(id, req);
    }

    @PutMapping("/{id}/listed")
    public TrackResponse setListed(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                   @Valid @RequestBody TrackListingRequest req) {
        return tracks.setListed(UUID.fromString(jwt.getSubject()), id, req.listed());
    }

    @PostMapping(path = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TrackPhotoResponse uploadPhoto(@PathVariable UUID id, @RequestPart("file") MultipartFile file,
                                          @RequestParam(required = false) String caption) {
        return photos.upload(id, file, caption);
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePhoto(@PathVariable UUID id, @PathVariable UUID photoId) {
        photos.delete(id, photoId);
    }
}
