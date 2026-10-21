package com.sahyatri.guide.repository;

import com.sahyatri.guide.entity.GuideSlugHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuideSlugHistoryRepository extends JpaRepository<GuideSlugHistory, String> {
}
