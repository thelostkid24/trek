package com.sahyatri.catalog.repository;

import com.sahyatri.catalog.entity.Track;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrackRepository extends JpaRepository<Track, UUID> {

    List<Track> findAllByOrderByNameAsc();

    List<Track> findByListedTrue();

    Optional<Track> findBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);
}
