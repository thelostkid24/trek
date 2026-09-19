package com.sahyatri.payment.repository;

import com.sahyatri.payment.entity.Payment;
import com.sahyatri.payment.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /** Lock the departure and booking first (lock order: departure, booking, payment). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.bookingId = :bookingId order by p.createdAt")
    List<Payment> findByBookingIdForUpdate(UUID bookingId);

    Optional<Payment> findByIdAndUserId(UUID id, UUID userId);

    @Query("select p.bookingId from Payment p where p.id = :id")
    Optional<UUID> findBookingId(UUID id);

    /** Id only: loading the entity before its locked read would hand back stale state. */
    @Query("select p.id from Payment p where p.razorpayOrderId = :razorpayOrderId")
    Optional<UUID> findIdByRazorpayOrderId(String razorpayOrderId);

    @Query("select p.id from Payment p where p.bookingId = :bookingId and p.status = :status order by p.createdAt")
    List<UUID> findIdsByBookingIdAndStatus(UUID bookingId, PaymentStatus status);

    List<Payment> findByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    List<Payment> findByBookingIdAndStatus(UUID bookingId, PaymentStatus status);

    @Query("""
            select p.id from Payment p, Booking b
            where b.id = p.bookingId and p.status = :status and p.createdAt <= :before
              and b.status <> com.sahyatri.booking.entity.BookingStatus.HELD""")
    List<UUID> findStaleOrphanIds(PaymentStatus status, Instant before);
}
