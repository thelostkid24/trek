package com.sahyatri.common.storage;

import com.sahyatri.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.UUID;

/**
 * Certificate documents (§7.21.3): private files at {@code guide-certificates/<uuid>.pdf|.jpg}. Never served by
 * {@link PublicFileController}; only the owning guide and admins download them, through authenticated endpoints.
 * PDFs are kept as uploaded; photos are re-encoded to JPEG like every other image, which drops their metadata.
 */
public final class GuideCertificateFiles {

    public static final long MAX_BYTES = 5 * 1024 * 1024;
    private static final String PREFIX = "guide-certificates/";

    private GuideCertificateFiles() {
    }

    /** A stored document: its key and bytes. */
    public record Document(String key, byte[] content) {
    }

    /** {@code FILE_TOO_LARGE} over 5 MB, {@code UNSUPPORTED_DOCUMENT} unless it's a PDF, JPEG or PNG. */
    public static Document prepare(MultipartFile file) {
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE", "File must be 5 MB or smaller");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw unsupported();
        }
        UUID id = UUID.randomUUID();
        if (isPdf(bytes)) {
            return new Document(PREFIX + id + ".pdf", bytes);
        }
        BufferedImage image;
        try {
            image = Images.decode(bytes);
        } catch (ApiException notAnImage) {
            throw unsupported();
        }
        int w = image.getWidth();
        int h = image.getHeight();
        return new Document(PREFIX + id + ".jpg", Images.encodeJpeg(Images.draw(image, 0, 0, w, h, w, h)));
    }

    public static MediaType mediaType(String key) {
        return key.endsWith(".pdf") ? MediaType.APPLICATION_PDF : MediaType.IMAGE_JPEG;
    }

    /** "pdf" or "jpg", for the download's file name. */
    public static String extension(String key) {
        return key.substring(key.lastIndexOf('.') + 1);
    }

    private static boolean isPdf(byte[] b) {
        return b.length > 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-';
    }

    private static ApiException unsupported() {
        return new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_DOCUMENT", "Upload a PDF, JPEG or PNG");
    }
}
