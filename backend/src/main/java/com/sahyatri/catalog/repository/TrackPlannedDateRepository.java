package com.sahyatri.catalog.repository;

import com.sahyatri.catalog.entity.TrackPlannedDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TrackPlannedDateRepository extends JpaRepository<TrackPlannedDate, UUID> {

    List<TrackPlannedDate> findByTrackIdOrderByStartDateAsc(UUID trackId);

    List<TrackPlannedDate> findByTrackIdAndStartDateGreaterThanEqualOrderByStartDateAsc(UUID trackId, LocalDate from);

    @Modifying(flushAutomatically = true)
    @Query("delete from TrackPlannedDate d where d.trackId = :trackId")
    void deleteForTrack(UUID trackId);
}
