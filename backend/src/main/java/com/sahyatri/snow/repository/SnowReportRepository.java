package com.sahyatri.snow.repository;

import com.sahyatri.snow.entity.SnowReport;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SnowReportRepository extends JpaRepository<SnowReport, UUID> {

    /** Row lock so two photo uploads for one report can't both pass the "no photo yet" check. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SnowReport r where r.id = :id")
    Optional<SnowReport> findByIdForUpdate(UUID id);

    /** Newest first: by the day observed, then by when it was filed. */
    List<SnowReport> findByTrackIdOrderByReportedOnDescCreatedAtDesc(UUID trackId, Limit limit);

    List<SnowReport> findByTrackIdAndCrowdTentsNotNullOrderByReportedOnDescCreatedAtDesc(UUID trackId, Limit limit);

    default Optional<SnowReport> findLatest(UUID trackId) {
        return findByTrackIdOrderByReportedOnDescCreatedAtDesc(trackId, Limit.of(1)).stream().findFirst();
    }
}
