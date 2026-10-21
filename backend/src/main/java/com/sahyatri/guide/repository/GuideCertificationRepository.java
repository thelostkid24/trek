package com.sahyatri.guide.repository;

import com.sahyatri.catalog.entity.DepartureStatus;
import com.sahyatri.guide.entity.GuideCertification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GuideCertificationRepository extends JpaRepository<GuideCertification, UUID> {

    List<GuideCertification> findByGuideIdOrderByCreatedAtAsc(UUID guideId);

    Optional<GuideCertification> findByIdAndGuideId(UUID id, UUID guideId);

    long countByGuideId(UUID guideId);

    /** Departures the guide has completed with us; tiers count only these, not treks led before (§7.21.3). */
    @Query("select count(d) from Departure d where d.guide.id = :guideId and d.status = :status")
    long countDepartures(UUID guideId, DepartureStatus status);
}
