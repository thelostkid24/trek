package com.sahyatri.auth.repository;

import com.sahyatri.auth.entity.OtpChallenge;
import com.sahyatri.auth.entity.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, UUID> {

    /** Latest code for the phone, any purpose — resend and hourly limits apply across purposes. */
    Optional<OtpChallenge> findFirstByPhoneOrderByCreatedAtDesc(String phone);

    Optional<OtpChallenge> findFirstByPhoneAndPurposeOrderByCreatedAtDesc(String phone, OtpPurpose purpose);

    Optional<OtpChallenge> findFirstByPhoneAndCreatedAtAfterOrderByCreatedAtAsc(String phone, Instant after);

    long countByPhoneAndCreatedAtAfter(String phone, Instant after);
}
