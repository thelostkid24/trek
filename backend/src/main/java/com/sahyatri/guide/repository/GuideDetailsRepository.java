package com.sahyatri.guide.repository;

import com.sahyatri.guide.entity.GuideDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GuideDetailsRepository extends JpaRepository<GuideDetails, UUID> {

    Optional<GuideDetails> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
