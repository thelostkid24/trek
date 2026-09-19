package com.sahyatri.catalog.controller;

import com.sahyatri.catalog.dto.TrackRequest;
import com.sahyatri.catalog.dto.TrackResponse;
import com.sahyatri.catalog.service.TrackAdminService;
import com.sahyatri.common.web.ItemsResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Contract: docs/TRD.md §7.5. ADMIN role enforced by SecurityConfig (/api/admin/**). */
@RestController
@RequestMapping("/api/admin/tracks")
public class AdminTrackController {

    private final TrackAdminService tracks;

    public AdminTrackController(TrackAdminService tracks) {
        this.tracks = tracks;
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
}
