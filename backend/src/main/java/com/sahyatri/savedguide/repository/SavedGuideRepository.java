package com.sahyatri.savedguide.repository;

import com.sahyatri.savedguide.entity.SavedGuide;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SavedGuideRepository extends JpaRepository<SavedGuide, SavedGuide.Key> {

    List<SavedGuide> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Saving twice keeps the first save (and its time). */
    @Modifying
    @Query(value = """
            INSERT INTO saved_guides (user_id, guide_id, created_at) VALUES (:userId, :guideId, :now)
            ON CONFLICT (user_id, guide_id) DO NOTHING""", nativeQuery = true)
    void save(UUID userId, UUID guideId, Instant now);

    @Modifying
    @Query("delete from SavedGuide s where s.userId = :userId and s.guideId = :guideId")
    void remove(UUID userId, UUID guideId);
}
