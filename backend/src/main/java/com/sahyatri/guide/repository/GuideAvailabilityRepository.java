package com.sahyatri.guide.repository;

import com.sahyatri.guide.entity.GuideAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GuideAvailabilityRepository extends JpaRepository<GuideAvailability, UUID> {

    Optional<GuideAvailability> findByIdAndGuideId(UUID id, UUID guideId);

    List<GuideAvailability> findByGuideIdAndEndDateGreaterThanEqualOrderByStartDateAsc(UUID guideId, LocalDate from);

    /** A guide's ranges sharing at least a day with [start, end]. */
    @Query("""
            select a from GuideAvailability a
            where a.guideId = :guideId and a.startDate <= :end and a.endDate >= :start
            order by a.startDate""")
    List<GuideAvailability> findOverlapping(UUID guideId, LocalDate start, LocalDate end);

    /** Every guide's ranges sharing at least a day with [from, to]. */
    @Query("""
            select a from GuideAvailability a
            where a.startDate <= :to and a.endDate >= :from
            order by a.startDate, a.guideId""")
    List<GuideAvailability> findInWindow(LocalDate from, LocalDate to);
}
