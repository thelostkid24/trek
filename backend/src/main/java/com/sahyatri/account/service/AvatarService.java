package com.sahyatri.account.service;

import com.sahyatri.auth.dto.UserResponse;
import com.sahyatri.auth.entity.User;
import com.sahyatri.auth.repository.UserRepository;
import com.sahyatri.auth.service.AuthService;
import com.sahyatri.auth.service.CurrentUser;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.common.storage.AvatarFiles;
import com.sahyatri.common.storage.FileStorage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.UUID;

/**
 * Profile photos. Uploads are decoded and re-encoded server-side, so what we store and serve is always a clean
 * 512×512 JPEG with no metadata (EXIF location, embedded payloads) from the original file.
 */
@Service
public class AvatarService {

    static final long MAX_BYTES = 5 * 1024 * 1024;
    static final int SIZE = 512;
    /** Refuse to decode absurd dimensions (decompression bombs) before allocating pixels. */
    static final int MAX_DIMENSION = 10_000;

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
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE", "Photo must be 5 MB or smaller");
        }
        byte[] jpeg = toAvatarJpeg(read(file));

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

    private static byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw unsupported();
        }
    }

    static byte[] toAvatarJpeg(byte[] bytes) {
        if (!isJpeg(bytes) && !isPng(bytes)) {
            throw unsupported();
        }
        BufferedImage source = decode(bytes);
        int side = Math.min(source.getWidth(), source.getHeight());
        int x = (source.getWidth() - side) / 2;
        int y = (source.getHeight() - side) / 2;

        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE); // transparent PNG areas
            g.fillRect(0, 0, SIZE, SIZE);
            g.drawImage(source, 0, 0, SIZE, SIZE, x, y, x + side, y + side, null);
        } finally {
            g.dispose();
        }
        return encodeJpeg(out);
    }

    private static BufferedImage decode(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw unsupported();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                if (reader.getWidth(0) > MAX_DIMENSION || reader.getHeight(0) > MAX_DIMENSION) {
                    throw unsupported();
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException api) {
                throw api;
            }
            throw unsupported();
        }
    }

    private static byte[] encodeJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(0.85f);
            writer.write(null, new IIOImage(image, null, null), params);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
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

    private static boolean isJpeg(byte[] b) {
        return b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] b) {
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        if (b.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (b[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static ApiException unsupported() {
        return new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_IMAGE", "Upload a JPEG or PNG photo");
    }
}
