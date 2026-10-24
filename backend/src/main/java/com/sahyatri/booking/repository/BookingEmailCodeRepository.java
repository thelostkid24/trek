package com.sahyatri.booking.repository;

import com.sahyatri.booking.entity.BookingEmailCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface BookingEmailCodeRepository extends JpaRepository<BookingEmailCode, UUID> {

    Optional<BookingEmailCode> findFirstByEmailOrderByCreatedAtDesc(String email);

    Optional<BookingEmailCode> findFirstByEmailAndCreatedAtAfterOrderByCreatedAtAsc(String email, Instant after);

    long countByEmailAndCreatedAtAfter(String email, Instant after);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from BookingEmailCode c where c.id = :id")
    Optional<BookingEmailCode> findByIdForUpdate(UUID id);
}
