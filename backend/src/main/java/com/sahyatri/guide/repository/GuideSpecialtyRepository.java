package com.sahyatri.guide.repository;

import com.sahyatri.guide.entity.GuideSpecialty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GuideSpecialtyRepository extends JpaRepository<GuideSpecialty, UUID> {

    List<GuideSpecialty> findAllByOrderByPositionAscNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
