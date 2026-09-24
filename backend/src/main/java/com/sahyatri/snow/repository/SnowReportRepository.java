package com.sahyatri.snow.repository;

import com.sahyatri.snow.entity.SnowReport;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SnowReportRepository extends JpaRepository<SnowReport, UUID> {

    /** Newest first: by the day observed, then by when it was filed. */
    List<SnowReport> findByTrackIdOrderByReportedOnDescCreatedAtDesc(UUID trackId, Limit limit);

    List<SnowReport> findByTrackIdAndCrowdTentsNotNullOrderByReportedOnDescCreatedAtDesc(UUID trackId, Limit limit);

    default Optional<SnowReport> findLatest(UUID trackId) {
        return findByTrackIdOrderByReportedOnDescCreatedAtDesc(trackId, Limit.of(1)).stream().findFirst();
    }
}
