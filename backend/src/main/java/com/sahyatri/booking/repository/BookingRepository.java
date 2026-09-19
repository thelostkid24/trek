package com.sahyatri.booking.repository;

import com.sahyatri.booking.entity.Booking;
import com.sahyatri.booking.entity.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    /** Lock the departure first (lock order: departure, booking, payment). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.departure.id = :departureId and b.status in :statuses order by b.createdAt")
    List<Booking> findByDepartureForUpdate(UUID departureId, Collection<BookingStatus> statuses);

    @Query("select b.departure.id from Booking b where b.id = :id")
    Optional<UUID> findDepartureId(UUID id);

    @Query("""
            select b from Booking b join fetch b.departure d join fetch d.track join fetch d.guide
            where b.userId = :userId order by b.createdAt desc""")
    List<Booking> findForUser(UUID userId);

    @Query("""
            select b from Booking b join fetch b.departure d join fetch d.track join fetch d.guide
            where b.id = :id and b.userId = :userId""")
    Optional<Booking> findForUser(UUID id, UUID userId);

    boolean existsByDepartureIdAndUserIdAndStatusIn(UUID departureId, UUID userId, Collection<BookingStatus> statuses);

    Optional<Booking> findFirstByDepartureIdAndUserIdAndStatusIn(UUID departureId, UUID userId,
                                                                 Collection<BookingStatus> statuses);

    @Query("select b.id from Booking b where b.status = :status and b.holdExpiresAt <= :now order by b.holdExpiresAt")
    List<UUID> findExpiredHoldIds(BookingStatus status, Instant now);
}
