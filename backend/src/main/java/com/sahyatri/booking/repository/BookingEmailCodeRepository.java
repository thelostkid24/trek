package com.sahyatri.booking.repository;

import com.sahyatri.booking.entity.BookingEmailCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface BookingEmailCodeRepository extends JpaRepository<BookingEmailCode, UUID> {

    Optional<BookingEmailCode> findFirstByEmailOrderByCreatedAtDesc(String email);

    Optional<BookingEmailCode> findFirstByEmailAndCreatedAtAfterOrderByCreatedAtAsc(String email, Instant after);

    long countByEmailAndCreatedAtAfter(String email, Instant after);
}
