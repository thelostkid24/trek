package com.sahyatri.guide.repository;

import com.sahyatri.guide.entity.GuideProfileStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface GuideProfileStatRepository extends JpaRepository<GuideProfileStat, GuideProfileStat.Key> {

    List<GuideProfileStat> findByGuideIdOrderByPositionAsc(UUID guideId);

    @Modifying(flushAutomatically = true)
    @Query("delete from GuideProfileStat s where s.guideId = :guideId")
    void deleteForGuide(UUID guideId);
}
