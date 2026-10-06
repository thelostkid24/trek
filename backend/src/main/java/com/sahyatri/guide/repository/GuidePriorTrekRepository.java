package com.sahyatri.guide.repository;

import com.sahyatri.guide.entity.GuidePriorTrek;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface GuidePriorTrekRepository extends JpaRepository<GuidePriorTrek, GuidePriorTrek.Key> {

    List<GuidePriorTrek> findByGuideIdIn(Collection<UUID> guideIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from GuidePriorTrek p where p.guideId = :guideId")
    void deleteForGuide(UUID guideId);
}
