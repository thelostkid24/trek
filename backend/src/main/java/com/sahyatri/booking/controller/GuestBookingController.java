package com.sahyatri.booking.controller;

import com.sahyatri.booking.dto.GuestBookingRequest;
import com.sahyatri.booking.dto.GuestBookingResponse;
import com.sahyatri.booking.service.BookingService;
import com.sahyatri.common.security.RefreshCookie;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contract: docs/TRD.md §7.6. Public: guest checkout signs the guest in. Signed-in trekkers use
 * {@code POST /api/trekker/bookings} instead, or this would replace their session.
 */
@RestController
@RequestMapping("/api/public/bookings")
public class GuestBookingController {

    private final BookingService bookings;
    private final RefreshCookie cookie;

    public GuestBookingController(BookingService bookings, RefreshCookie cookie) {
        this.bookings = bookings;
        this.cookie = cookie;
    }

    @PostMapping
    public ResponseEntity<GuestBookingResponse> create(@Valid @RequestBody GuestBookingRequest req) {
        BookingService.GuestBooking result = bookings.createForGuest(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookie.set(result.session().refreshToken()))
                .body(new GuestBookingResponse(result.booking(), result.session().body()));
    }
}
