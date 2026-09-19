package com.sahyatri.auth.dto;

import com.sahyatri.auth.entity.AuthMethod;
import com.sahyatri.auth.entity.Role;
import com.sahyatri.auth.entity.User;
import com.sahyatri.common.storage.AvatarFiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String fullName,
        String email,
        String phone,
        String avatarUrl,
        Role role,
        boolean emailVerified,
        boolean phoneVerified,
        boolean guest,
        List<AuthMethod> authMethods,
        Instant createdAt) {

    public static UserResponse of(User user, AvatarFiles avatars) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                avatars.url(user.getAvatarKey()),
                user.getRole(),
                user.getEmailVerifiedAt() != null,
                user.getPhoneVerifiedAt() != null,
                user.isGuest(),
                user.authMethods(),
                user.getCreatedAt());
    }
}
