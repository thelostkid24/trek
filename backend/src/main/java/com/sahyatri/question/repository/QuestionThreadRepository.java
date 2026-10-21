package com.sahyatri.question.repository;

import com.sahyatri.question.entity.QuestionThread;
import com.sahyatri.question.entity.ThreadStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface QuestionThreadRepository extends JpaRepository<QuestionThread, UUID> {

    List<QuestionThread> findByGuideIdOrderByLastMessageAtDesc(UUID guideId);

    List<QuestionThread> findByAskerIdOrderByLastMessageAtDesc(UUID askerId);

    List<QuestionThread> findAllByOrderByLastMessageAtDesc(Limit limit);

    List<QuestionThread> findByStatusOrderByLastMessageAtDesc(ThreadStatus status, Limit limit);

    long countByAskerIdAndCreatedAtAfter(UUID askerId, Instant after);

    long countByVisitorEmailAndCreatedAtAfter(String visitorEmail, Instant after);
}
