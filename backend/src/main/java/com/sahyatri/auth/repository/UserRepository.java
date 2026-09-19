package com.sahyatri.auth.repository;

import com.sahyatri.auth.entity.Role;
import com.sahyatri.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    Optional<User> findByGoogleSubject(String googleSubject);

    boolean existsByEmail(String email);

    List<User> findByRoleOrderByFullNameAsc(Role role);

    List<User> findByEmailIn(Collection<String> emails);
}
