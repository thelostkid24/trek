package com.sahyatri.admin.service;

import com.sahyatri.admin.dto.GuideResponse;
import com.sahyatri.auth.entity.Role;
import com.sahyatri.auth.entity.User;
import com.sahyatri.auth.repository.UserRepository;
import com.sahyatri.auth.service.CurrentUser;
import com.sahyatri.common.audit.AuditLog;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.common.storage.AvatarFiles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Guides are existing accounts an admin promotes. Self-service guide onboarding comes later. */
@Service
public class GuideAdminService {

    private final UserRepository users;
    private final AuditLog audit;
    private final AvatarFiles avatars;
    private final CurrentUser currentUser;

    public GuideAdminService(UserRepository users, AuditLog audit, AvatarFiles avatars, CurrentUser currentUser) {
        this.currentUser = currentUser;
        this.users = users;
        this.audit = audit;
        this.avatars = avatars;
    }

    @Transactional(readOnly = true)
    public List<GuideResponse> list() {
        return users.findByRoleOrderByFullNameAsc(Role.GUIDE).stream().map(this::toResponse).toList();
    }

    @Transactional
    public GuideResponse promote(UUID adminId, String email) {
        currentUser.require(adminId);
        User user = users.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .filter(u -> !u.isDisabled())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND",
                        "No active account uses this email"));
        switch (user.getRole()) {
            case GUIDE -> throw ApiException.conflict("ALREADY_GUIDE", "This account is already a guide");
            case ADMIN -> throw ApiException.conflict("ROLE_NOT_PROMOTABLE", "Admins can't be made guides");
            case TREKKER -> user.setRole(Role.GUIDE);
        }
        users.saveAndFlush(user);
        audit.record(adminId, "USER_PROMOTED", "USER", user.getId(), Map.of("role", Role.GUIDE.name()));
        return toResponse(user);
    }

    private GuideResponse toResponse(User u) {
        return new GuideResponse(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(),
                avatars.url(u.getAvatarKey()), u.getCreatedAt());
    }
}
