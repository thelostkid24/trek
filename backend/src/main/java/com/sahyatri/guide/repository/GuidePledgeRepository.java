package com.sahyatri.guide.repository;

import com.sahyatri.guide.entity.GuidePledge;
import com.sahyatri.guide.entity.PledgeKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface GuidePledgeRepository extends JpaRepository<GuidePledge, GuidePledge.Key> {

    List<GuidePledge> findByGuideId(UUID guideId);

    @Modifying(flushAutomatically = true)
    @Query("delete from GuidePledge p where p.guideId = :guideId")
    void deleteForGuide(UUID guideId);

    /** The guide's pledges in the page's order. */
    default List<PledgeKind> kindsFor(UUID guideId) {
        return findByGuideId(guideId).stream().map(GuidePledge::getKind).sorted().toList();
    }
}
