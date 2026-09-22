package com.sahyatri.account.service;

import com.sahyatri.auth.dto.UserResponse;
import com.sahyatri.auth.entity.User;
import com.sahyatri.auth.repository.UserRepository;
import com.sahyatri.auth.service.AuthService;
import com.sahyatri.auth.service.CurrentUser;
import com.sahyatri.common.storage.AvatarFiles;
import com.sahyatri.common.storage.FileStorage;
import com.sahyatri.common.storage.Images;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.util.UUID;

/** Profile photos: a 512×512 centre crop, re-encoded by {@link Images}. */
@Service
public class AvatarService {

    static final int SIZE = 512;

    private final UserRepository users;
    private final CurrentUser currentUser;
    private final FileStorage storage;
    private final AuthService auth;

    public AvatarService(UserRepository users, CurrentUser currentUser, FileStorage storage, AuthService auth) {
        this.users = users;
        this.currentUser = currentUser;
        this.storage = storage;
        this.auth = auth;
    }

    public UserResponse upload(UUID userId, MultipartFile file) {
        User user = currentUser.require(userId);
        byte[] jpeg = toAvatarJpeg(Images.read(file));

        UUID key = UUID.randomUUID();
        storage.put(AvatarFiles.storageKey(key), jpeg);
        String previous = user.getAvatarKey();
        user.setAvatarKey(key.toString());
        user = users.save(user);
        deleteQuietly(previous);
        return auth.toResponse(user);
    }

    public UserResponse remove(UUID userId) {
        User user = currentUser.require(userId);
        String previous = user.getAvatarKey();
        if (previous != null) {
            user.setAvatarKey(null);
            user = users.save(user);
            deleteQuietly(previous);
        }
        return auth.toResponse(user);
    }

    static byte[] toAvatarJpeg(byte[] bytes) {
        BufferedImage source = Images.decode(bytes);
        int side = Math.min(source.getWidth(), source.getHeight());
        int x = (source.getWidth() - side) / 2;
        int y = (source.getHeight() - side) / 2;
        return Images.encodeJpeg(Images.draw(source, x, y, side, side, SIZE, SIZE));
    }

    private void deleteQuietly(String avatarKey) {
        if (avatarKey == null) {
            return;
        }
        try {
            storage.delete(AvatarFiles.storageKey(UUID.fromString(avatarKey)));
        } catch (RuntimeException ignored) {
            // An orphaned file is harmless; the account change already succeeded.
        }
    }
}
