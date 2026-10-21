package com.sahyatri.guide.repository;

import com.sahyatri.guide.entity.GuideRouteNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface GuideRouteNoteRepository extends JpaRepository<GuideRouteNote, GuideRouteNote.Key> {

    List<GuideRouteNote> findByGuideId(UUID guideId);

    @Modifying(flushAutomatically = true)
    @Query("delete from GuideRouteNote x where x.guideId = :guideId")
    void deleteForGuide(UUID guideId);
}
