package com.sahyatri.guide.repository;

import com.sahyatri.guide.entity.GuideMedia;
import com.sahyatri.guide.entity.GuideMediaStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GuideMediaRepository extends JpaRepository<GuideMedia, UUID> {

    List<GuideMedia> findByGuideIdOrderByPositionAscCreatedAtAsc(UUID guideId);

    List<GuideMedia> findByGuideIdAndStatusOrderByPositionAscCreatedAtAsc(UUID guideId, GuideMediaStatus status);

    Optional<GuideMedia> findByIdAndGuideId(UUID id, UUID guideId);

    Optional<GuideMedia> findByIdAndStatus(UUID id, GuideMediaStatus status);

    long countByGuideId(UUID guideId);

    @Query("select coalesce(max(m.position) + 1, 0) from GuideMedia m where m.guideId = :guideId")
    int nextPosition(UUID guideId);

    @Query("select m.id from GuideMedia m where m.status = :status and m.createdAt < :before")
    List<UUID> findIdsByStatusCreatedBefore(GuideMediaStatus status, Instant before);

    /** Row lock for the cleanup job. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from GuideMedia m where m.id = :id")
    Optional<GuideMedia> findByIdForUpdate(UUID id);
}
