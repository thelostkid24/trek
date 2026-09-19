package com.sahyatri.catalog.controller;

import com.sahyatri.catalog.dto.AdminDepartureResponse;
import com.sahyatri.catalog.dto.CancelDepartureRequest;
import com.sahyatri.catalog.dto.DepartureRequest;
import com.sahyatri.catalog.service.DepartureAdminService;
import com.sahyatri.common.web.ItemsResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/admin/departures")
public class AdminDepartureController {

    private final DepartureAdminService departures;

    public AdminDepartureController(DepartureAdminService departures) {
        this.departures = departures;
    }

    @GetMapping
    public ItemsResponse<AdminDepartureResponse> list() {
        return new ItemsResponse<>(departures.list());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminDepartureResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DepartureRequest req) {
        return departures.create(adminId(jwt), req);
    }

    @PutMapping("/{id}")
    public AdminDepartureResponse update(@PathVariable UUID id, @Valid @RequestBody DepartureRequest req) {
        return departures.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        departures.delete(id);
    }

    @PostMapping("/{id}/publish")
    public AdminDepartureResponse publish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return departures.publish(adminId(jwt), id);
    }

    @PostMapping("/{id}/cancel")
    public AdminDepartureResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                         @Valid @RequestBody CancelDepartureRequest req) {
        return departures.cancel(adminId(jwt), id, req);
    }

    private static UUID adminId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
