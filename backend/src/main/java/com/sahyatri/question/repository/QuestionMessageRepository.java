package com.sahyatri.question.repository;

import com.sahyatri.question.entity.QuestionMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface QuestionMessageRepository extends JpaRepository<QuestionMessage, UUID> {

    List<QuestionMessage> findByThreadIdOrderByCreatedAtAsc(UUID threadId);

    List<QuestionMessage> findByThreadIdInOrderByCreatedAtAsc(Collection<UUID> threadIds);
}
