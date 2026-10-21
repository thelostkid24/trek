package com.sahyatri.guide.repository;

import com.sahyatri.guide.entity.GuideMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GuideMediaRepository extends JpaRepository<GuideMedia, UUID> {

    List<GuideMedia> findByGuideIdOrderByPositionAscCreatedAtAsc(UUID guideId);

    Optional<GuideMedia> findByIdAndGuideId(UUID id, UUID guideId);

    long countByGuideId(UUID guideId);

    boolean existsByGuideIdAndYoutubeId(UUID guideId, String youtubeId);

    @Query("select coalesce(max(m.position) + 1, 0) from GuideMedia m where m.guideId = :guideId")
    int nextPosition(UUID guideId);
}
