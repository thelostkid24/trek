package com.sahyatri.guide.repository;

import com.sahyatri.guide.entity.GuideMilestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface GuideMilestoneRepository extends JpaRepository<GuideMilestone, UUID> {

    List<GuideMilestone> findByGuideIdOrderByPositionAsc(UUID guideId);

    @Modifying(flushAutomatically = true)
    @Query("delete from GuideMilestone m where m.guideId = :guideId")
    void deleteForGuide(UUID guideId);
}
