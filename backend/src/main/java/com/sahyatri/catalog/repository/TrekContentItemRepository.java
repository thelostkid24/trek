package com.sahyatri.catalog.repository;

import com.sahyatri.catalog.entity.ContentKind;
import com.sahyatri.catalog.entity.TrekContentItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface TrekContentItemRepository extends JpaRepository<TrekContentItem, UUID> {

    List<TrekContentItem> findByTrackIdIsNullOrderByKindAscPositionAsc();

    List<TrekContentItem> findByTrackIdOrderByKindAscPositionAsc(UUID trackId);

    /** Shared items and the track's own, shared first within each kind. */
    @Query("""
            select i from TrekContentItem i where i.trackId is null or i.trackId = :trackId
            order by i.kind, case when i.trackId is null then 0 else 1 end, i.position""")
    List<TrekContentItem> findForTrack(UUID trackId);

    @Modifying(flushAutomatically = true)
    @Query("delete from TrekContentItem i where i.trackId is null and i.kind = :kind")
    void deleteShared(ContentKind kind);

    @Modifying(flushAutomatically = true)
    @Query("delete from TrekContentItem i where i.trackId = :trackId and i.kind = :kind")
    void deleteForTrack(UUID trackId, ContentKind kind);
}
