package com.sahyatri.guide.repository;

import com.sahyatri.guide.entity.GuideDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GuideDetailsRepository extends JpaRepository<GuideDetails, UUID> {
}
