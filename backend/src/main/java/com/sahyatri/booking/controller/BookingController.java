package com.sahyatri.booking.controller;

import com.sahyatri.booking.dto.BookingRequest;
import com.sahyatri.booking.dto.BookingResponse;
import com.sahyatri.booking.dto.CancellationQuote;
import com.sahyatri.booking.dto.TravellersRequest;
import com.sahyatri.booking.service.BookingService;
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

/** Contract: docs/TRD.md §7.6. TREKKER role enforced by SecurityConfig (/api/trekker/**). */
@RestController
@RequestMapping("/api/trekker/bookings")
public class BookingController {

    private final BookingService bookings;

    public BookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BookingRequest req) {
        return bookings.create(userId(jwt), req);
    }

    @GetMapping
    public ItemsResponse<BookingResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return new ItemsResponse<>(bookings.list(userId(jwt)));
    }

    @GetMapping("/{id}")
    public BookingResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return bookings.get(userId(jwt), id);
    }

    @PutMapping("/{id}/travellers")
    public BookingResponse updateTravellers(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                            @Valid @RequestBody TravellersRequest req) {
        return bookings.updateTravellers(userId(jwt), id, req);
    }

    @DeleteMapping("/{id}/hold")
    public BookingResponse releaseHold(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return bookings.releaseHold(userId(jwt), id);
    }

    @GetMapping("/{id}/cancellation-quote")
    public CancellationQuote quote(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return bookings.quote(userId(jwt), id);
    }

    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return bookings.cancel(userId(jwt), id);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
