package com.sahyatri.catalog.repository;

import com.sahyatri.catalog.entity.TrackPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrackPhotoRepository extends JpaRepository<TrackPhoto, UUID> {

    long countByTrackId(UUID trackId);

    /** Photo ids of several tracks, oldest first within each, so a track's first row is its cover. */
    @Query("""
            select p.track.id as trackId, p.id as photoId from TrackPhoto p
            where p.track.id in :trackIds order by p.createdAt, p.id""")
    List<TrackCover> findCovers(Collection<UUID> trackIds);

    /** Which photo belongs to which track, without loading either entity. */
    interface TrackCover {
        UUID getTrackId();

        UUID getPhotoId();
    }

    Optional<TrackPhoto> findByIdAndTrackId(UUID id, UUID trackId);
}
