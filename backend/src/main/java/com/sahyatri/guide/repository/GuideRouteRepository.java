package com.sahyatri.guide.repository;

import com.sahyatri.guide.entity.GuideRoute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface GuideRouteRepository extends JpaRepository<GuideRoute, UUID> {

    List<GuideRoute> findByGuideIdOrderByPositionAsc(UUID guideId);

    List<GuideRoute> findByGuideIdIn(Collection<UUID> guideIds);
}
