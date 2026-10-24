package com.sahyatri.auth.repository;

import com.sahyatri.auth.entity.Role;
import com.sahyatri.auth.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    Optional<User> findByGoogleSubject(String googleSubject);

    boolean existsByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(UUID id);

    List<User> findByRoleOrderByFullNameAsc(Role role);

    List<User> findByEmailIn(Collection<String> emails);

    /** Bumps {@code last_seen_at} unless it is newer than {@code staleBefore}, so it writes at most once an hour. */
    @Transactional
    @Modifying
    @Query(value = """
            UPDATE users SET last_seen_at = :now
            WHERE id = :id AND (last_seen_at IS NULL OR last_seen_at < :staleBefore)""", nativeQuery = true)
    int markSeen(@Param("id") UUID id, @Param("now") Instant now, @Param("staleBefore") Instant staleBefore);
}
