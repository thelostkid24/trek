package com.sahyatri.common.storage;

import com.sahyatri.common.exception.ApiException;
import org.springframework.http.HttpStatus;
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

/**
 * Uploaded photos are decoded and re-encoded server-side, so what we store and serve is always a clean JPEG with no
 * metadata (EXIF location, embedded payloads) from the original file. Only JPEG and PNG are accepted.
 */
public final class Images {

    public static final long MAX_BYTES = 5 * 1024 * 1024;
    /** Refuse to decode absurd dimensions (decompression bombs) before allocating pixels. */
    static final int MAX_DIMENSION = 10_000;

    private Images() {
    }

    /** The upload's bytes; {@code FILE_TOO_LARGE} over 5 MB. */
    public static byte[] read(MultipartFile file) {
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE", "Photo must be 5 MB or smaller");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw unsupported();
        }
    }

    /** {@code UNSUPPORTED_IMAGE} unless the bytes are a JPEG or PNG we can decode. */
    public static BufferedImage decode(byte[] bytes) {
        if (!isJpeg(bytes) && !isPng(bytes)) {
            throw unsupported();
        }
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

    /** Draws the source region {@code (x, y, w, h)} into a new opaque {@code width × height} image. */
    public static BufferedImage draw(BufferedImage source, int x, int y, int w, int h, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE); // transparent PNG areas
            g.fillRect(0, 0, width, height);
            g.drawImage(source, 0, 0, width, height, x, y, x + w, y + h, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    public static byte[] encodeJpeg(BufferedImage image) {
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
