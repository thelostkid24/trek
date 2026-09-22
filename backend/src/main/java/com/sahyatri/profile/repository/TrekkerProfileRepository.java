package com.sahyatri.profile.repository;

import com.sahyatri.profile.entity.TrekkerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TrekkerProfileRepository extends JpaRepository<TrekkerProfile, UUID> {
}
