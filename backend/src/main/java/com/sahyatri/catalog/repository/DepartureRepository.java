package com.sahyatri.catalog.repository;

import com.sahyatri.catalog.entity.Departure;
import com.sahyatri.catalog.entity.DepartureStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepartureRepository extends JpaRepository<Departure, UUID> {

    /** Row lock for every status or seat change. Lock order: departure, then booking, then payment. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Departure d where d.id = :id")
    Optional<Departure> findByIdForUpdate(UUID id);

    @Query("""
            select d from Departure d join fetch d.track join fetch d.guide
            where d.status = :status and d.startDate >= :from and d.startDate < :until
            order by d.startDate, d.track.name""")
    List<Departure> findListed(DepartureStatus status, LocalDate from, LocalDate until);

    @Query("select d from Departure d join fetch d.track join fetch d.guide where d.id = :id")
    Optional<Departure> findWithTrackAndGuide(UUID id);

    @Query("select d from Departure d join fetch d.track join fetch d.guide order by d.startDate desc, d.createdAt desc")
    List<Departure> findAllForAdmin();

    @Query("""
            select d from Departure d join fetch d.track t join fetch d.guide
            where t.slug = :slug and d.status = :status and d.startDate >= :from
            order by d.startDate, d.createdAt""")
    List<Departure> findListedForTrack(String slug, DepartureStatus status, LocalDate from);

    @Query("""
            select d from Departure d join fetch d.track join fetch d.guide
            where d.guide.id = :guideId and d.status = :status and d.startDate >= :from
            order by d.startDate, d.createdAt""")
    List<Departure> findListedForGuide(UUID guideId, DepartureStatus status, LocalDate from);

    /** How many departures of each track each guide has run to the end. */
    @Query("""
            select d.guide.id as guideId, d.track.id as trackId, count(d) as times from Departure d
            where d.guide.id in :guideIds and d.status = :status
            group by d.guide.id, d.track.id""")
    List<LedCount> countByGuideAndTrack(Collection<UUID> guideIds, DepartureStatus status);

    interface LedCount {
        UUID getGuideId();

        UUID getTrackId();

        long getTimes();
    }

    boolean existsByTrackId(UUID trackId);

    @Query("select d.id from Departure d where d.status = :status and d.startDate <= :date and d.seatsTaken = 0")
    List<UUID> findUnsoldStartedIds(DepartureStatus status, LocalDate date);

    @Query("select d.id from Departure d where d.status = :status and d.endDate < :date")
    List<UUID> findEndedIds(DepartureStatus status, LocalDate date);
}
