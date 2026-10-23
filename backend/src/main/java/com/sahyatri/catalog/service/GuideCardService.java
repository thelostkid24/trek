package com.sahyatri.catalog.service;

import com.sahyatri.auth.entity.User;
import com.sahyatri.auth.repository.UserRepository;
import com.sahyatri.catalog.dto.GuideProfile;
import com.sahyatri.common.config.AppProperties;
import com.sahyatri.common.storage.AvatarFiles;
import com.sahyatri.common.storage.FileStorage;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * The guide page's link preview (§7.21.2): a 1200×630 PNG drawn with Java2D on each request, from the same numbers as
 * the page. Logical fonts only, so it draws wherever the JRE has fontconfig.
 */
@Service
public class GuideCardService {

    static final int WIDTH = 1200;
    static final int HEIGHT = 630;
    private static final int PAD = 72;
    private static final int PHOTO = 260;

    private static final Color BG = new Color(0x143626);
    private static final Color BG_DEEP = new Color(0x0b2118);
    private static final Color RIDGE_FAR = new Color(0x1b4a33);
    private static final Color RIDGE_NEAR = new Color(0x245f42);
    private static final Color LIGHT = new Color(0xeef7f2);
    private static final Color MUTED = new Color(0xaed6c0);
    private static final Color ACCENT = new Color(0xe79558);

    private final CatalogService catalog;
    private final UserRepository users;
    private final FileStorage storage;
    private final String siteHost;

    public GuideCardService(CatalogService catalog, UserRepository users, FileStorage storage, AppProperties props) {
        this.catalog = catalog;
        this.users = users;
        this.storage = storage;
        this.siteHost = props.frontendBaseUrl().replaceFirst("^https?://", "").replaceFirst("/+$", "");
    }

    /** {@code GUIDE_NOT_FOUND} like the page. */
    public byte[] card(UUID guideId) {
        GuideProfile g = catalog.guide(guideId);
        BufferedImage photo = users.findById(guideId).map(User::getAvatarKey).map(this::avatar).orElse(null);

        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D gr = img.createGraphics();
        try {
            gr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            gr.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            gr.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            gr.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            background(gr);

            String name = g.fullName() == null || g.fullName().isBlank() ? "Local guide" : g.fullName().strip();
            int photoY = (HEIGHT - PHOTO) / 2 - 20;
            photo(gr, photo, name, PAD, photoY);

            int x = PAD + PHOTO + 56;
            int maxWidth = WIDTH - x - PAD;
            String home = g.homeCity() == null || g.homeCity().isBlank() ? "" : " · " + g.homeCity().strip();
            gr.setColor(ACCENT);
            gr.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
            gr.drawString(fit(gr, ("Mountain guide" + home).toUpperCase(Locale.ROOT), maxWidth), x, photoY + 28);

            gr.setColor(LIGHT);
            int size = 76;
            gr.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, size));
            while (size > 44 && gr.getFontMetrics().stringWidth(name) > maxWidth) {
                gr.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, size -= 4));
            }
            gr.drawString(fit(gr, name, maxWidth), x, photoY + 28 + 24 + gr.getFontMetrics().getAscent());

            String numbers = numbers(g);
            if (!numbers.isEmpty()) {
                gr.setColor(MUTED);
                gr.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 28));
                gr.drawString(fit(gr, numbers, maxWidth), x, photoY + 190);
            }
            if (verified(g)) {
                badge(gr, x, photoY + 222);
            }

            gr.setColor(MUTED);
            gr.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 24));
            String link = siteHost + "/guides/" + (g.slug() == null ? g.id() : g.slug());
            FontMetrics fm = gr.getFontMetrics();
            String fitted = fit(gr, link, WIDTH - 2 * PAD);
            gr.drawString(fitted, WIDTH - PAD - fm.stringWidth(fitted), HEIGHT - 52);
        } finally {
            gr.dispose();
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * With picked stats (§7.21.8): "NIM & ABVIMAS trained · 4 yrs · 21 treks", the third part the first picked stat
     * with a short form. Otherwise "4.9 · 38 reviews · 52 treks". Empty parts are left out.
     */
    static String numbers(GuideProfile g) {
        List<String> parts = new ArrayList<>();
        if (g.stats() != null) {
            if (!g.trainedAt().isEmpty()) {
                parts.add(String.join(" & ", g.trainedAt()) + " trained");
            }
            if (g.yearsLeading() != null) {
                parts.add(g.yearsLeading() + (g.yearsLeading() == 1 ? " yr" : " yrs"));
            }
            g.stats().stream().map(GuideProfile.Stat::brief).filter(Objects::nonNull).findFirst().ifPresent(parts::add);
            return String.join(" · ", parts);
        }
        if (g.rating() != null) {
            parts.add(String.format(Locale.ROOT, "%.1f", g.rating()));
        }
        if (g.reviewCount() > 0) {
            parts.add(g.reviewCount() + (g.reviewCount() == 1 ? " review" : " reviews"));
        }
        if (g.treksLed() > 0) {
            parts.add(g.treksLed() + (g.treksLed() == 1 ? " trek" : " treks"));
        }
        return String.join(" · ", parts);
    }

    /** A verified certificate still valid today (§7.21.3), as {@code isCertified} in the frontend's lib/trek.ts. */
    static boolean verified(GuideProfile g) {
        return g.certified();
    }

    private BufferedImage avatar(String key) {
        try {
            return storage.get(AvatarFiles.storageKey(UUID.fromString(key)))
                    .map(bytes -> {
                        try {
                            return ImageIO.read(new ByteArrayInputStream(bytes));
                        } catch (IOException e) {
                            return null;
                        }
                    })
                    .orElse(null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Dark green with two calm ridgelines along the bottom. */
    private static void background(Graphics2D gr) {
        gr.setPaint(new GradientPaint(0, 0, BG, 0, HEIGHT, BG_DEEP));
        gr.fillRect(0, 0, WIDTH, HEIGHT);
        gr.setColor(RIDGE_FAR);
        gr.fill(ridge(new int[][]{{0, 520}, {180, 450}, {330, 500}, {560, 410}, {760, 480}, {950, 420}, {1200, 490}}));
        gr.setColor(RIDGE_NEAR);
        gr.fill(ridge(new int[][]{{0, 580}, {220, 530}, {420, 570}, {640, 520}, {880, 575}, {1060, 540}, {1200, 565}}));
        gr.setColor(new Color(11, 33, 24, 150));
        gr.fillRect(0, HEIGHT - 90, WIDTH, 90);
    }

    private static Path2D ridge(int[][] points) {
        Path2D p = new Path2D.Double();
        p.moveTo(0, HEIGHT);
        for (int[] pt : points) {
            p.lineTo(pt[0], pt[1]);
        }
        p.lineTo(WIDTH, HEIGHT);
        p.closePath();
        return p;
    }

    /** A rounded square: the photo centre-cropped, else initials. */
    private static void photo(Graphics2D gr, BufferedImage photo, String name, int x, int y) {
        RoundRectangle2D frame = new RoundRectangle2D.Double(x, y, PHOTO, PHOTO, 36, 36);
        if (photo != null) {
            int side = Math.min(photo.getWidth(), photo.getHeight());
            int sx = (photo.getWidth() - side) / 2;
            int sy = (photo.getHeight() - side) / 2;
            gr.setClip(frame);
            gr.drawImage(photo, x, y, x + PHOTO, y + PHOTO, sx, sy, sx + side, sy + side, null);
            gr.setClip(null);
        } else {
            gr.setColor(RIDGE_NEAR);
            gr.fill(frame);
            gr.setColor(LIGHT);
            gr.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 104));
            String initials = initials(name);
            FontMetrics fm = gr.getFontMetrics();
            gr.drawString(initials, x + (PHOTO - fm.stringWidth(initials)) / 2,
                    y + (PHOTO - fm.getHeight()) / 2 + fm.getAscent());
        }
        gr.setColor(new Color(238, 247, 242, 60));
        gr.setStroke(new BasicStroke(3));
        gr.draw(frame);
    }

    static String initials(String name) {
        StringBuilder sb = new StringBuilder();
        for (String part : name.strip().split("\\s+")) {
            if (!part.isEmpty() && sb.length() < 2) {
                sb.appendCodePoint(Character.toUpperCase(part.codePointAt(0)));
            }
        }
        return sb.isEmpty() ? "G" : sb.toString();
    }

    private static void badge(Graphics2D gr, int x, int y) {
        String text = "Verified by Empty Valley";
        gr.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        FontMetrics fm = gr.getFontMetrics();
        int w = fm.stringWidth(text) + 32 + 28;
        int h = 44;
        gr.setColor(new Color(238, 247, 242, 30));
        gr.fill(new RoundRectangle2D.Double(x, y, w, h, h, h));
        gr.setColor(ACCENT);
        gr.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int cx = x + 18;
        int cy = y + h / 2;
        gr.drawPolyline(new int[]{cx, cx + 6, cx + 18}, new int[]{cy, cy + 6, cy - 7}, 3);
        gr.setColor(LIGHT);
        gr.drawString(text, x + 46, y + (h - fm.getHeight()) / 2 + fm.getAscent());
    }

    /** Cuts {@code text} with "…" to fit {@code width} in the current font. */
    private static String fit(Graphics2D gr, String text, int width) {
        FontMetrics fm = gr.getFontMetrics();
        if (fm.stringWidth(text) <= width) {
            return text;
        }
        String cut = text;
        while (!cut.isEmpty() && fm.stringWidth(cut + "…") > width) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut.stripTrailing() + "…";
    }
}
