package com.sahyatri.guide.repository;

import com.sahyatri.guide.entity.GuideProfileSpecialty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface GuideProfileSpecialtyRepository extends JpaRepository<GuideProfileSpecialty, GuideProfileSpecialty.Key> {

    List<GuideProfileSpecialty> findByGuideId(UUID guideId);

    @Modifying(flushAutomatically = true)
    @Query("delete from GuideProfileSpecialty x where x.guideId = :guideId")
    void deleteForGuide(UUID guideId);
}
