package com.sahyatri.account.repository;

import com.sahyatri.account.entity.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, UUID> {

    Optional<EmailVerification> findByTokenHash(String tokenHash);

    Optional<EmailVerification> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<EmailVerification> findFirstByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(UUID userId, Instant after);

    long countByUserIdAndCreatedAtAfter(UUID userId, Instant after);
}
