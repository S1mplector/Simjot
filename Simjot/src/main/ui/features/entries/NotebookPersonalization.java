/*
 * SIMJOT - MIT License
 *
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 *
 * See LICENSE.md for full terms.
 */

package main.ui.features.entries;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import main.core.service.NotebookStore;
import main.infrastructure.backup.NotebookInfo;
import main.infrastructure.io.ResourceLoader;

/** Lightweight notebook-level visual preferences used by editors and notebook tiles. */
public final class NotebookPersonalization {
    public static final String PRESET_DEFAULT = "Default";
    public static final String PRESET_JOURNAL = "Journal";
    public static final String PRESET_DRAFT = "Draft";
    public static final String PRESET_POEM = "Poem";

    private static final int IMAGE_CACHE_MAX = 16;
    private static final int COVER_RENDER_CACHE_MAX = 32;
    private static final String NOTEBOOK_MASK_RESOURCE = "img/icons/original/notebook_nopen.png";
    private static final int LINE_ALPHA_THRESHOLD = 24;
    private static final int LINE_DILATION_RADIUS = 3;
    private static CoverMask coverMask;

    private static final Map<String, BufferedImage> IMAGE_CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
            return size() > IMAGE_CACHE_MAX;
        }
    };
    private static final Map<String, BufferedImage> COVER_RENDER_CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
            return size() > COVER_RENDER_CACHE_MAX;
        }
    };

    private NotebookPersonalization() {}

    public static NotebookInfo resolveForFolder(File folder) {
        if (folder == null) return null;
        try {
            java.nio.file.Path current = folder.toPath().toAbsolutePath().normalize();
            for (NotebookInfo nb : new NotebookStore().list()) {
                if (nb == null || nb.getFolder() == null) continue;
                java.nio.file.Path candidate = nb.getFolder().toPath().toAbsolutePath().normalize();
                if (candidate.equals(current)) return nb;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static String backgroundOrDefault(NotebookInfo nb, String fallback) {
        String path = nb == null ? null : nb.getBackgroundImagePath();
        return path == null || path.isBlank() ? fallback : path;
    }

    public static String fontOrDefault(NotebookInfo nb, String fallback) {
        String font = nb == null ? null : nb.getEditorFontFamily();
        return font == null || font.isBlank() || PRESET_DEFAULT.equalsIgnoreCase(font) ? fallback : font;
    }

    public static String stylePreset(NotebookInfo nb) {
        String preset = nb == null ? null : nb.getEditorStylePreset();
        return preset == null || preset.isBlank() ? PRESET_DEFAULT : preset;
    }

    public static boolean hasNotebookStyle(NotebookInfo nb) {
        return nb != null
                && nb.getEditorStylePreset() != null
                && !nb.getEditorStylePreset().isBlank()
                && !PRESET_DEFAULT.equalsIgnoreCase(nb.getEditorStylePreset());
    }

    public static String coverOrBackground(NotebookInfo nb) {
        if (nb == null) return null;
        String cover = nb.getCoverImagePath();
        if (cover != null && !cover.isBlank()) return cover;
        String icon = nb.getCustomIconPath();
        if (icon != null && !icon.isBlank()) return icon;
        String background = nb.getBackgroundImagePath();
        return background == null || background.isBlank() ? null : background;
    }

    public static float adjustLineSpacing(float spacing, NotebookInfo nb) {
        String preset = stylePreset(nb);
        if (PRESET_JOURNAL.equalsIgnoreCase(preset)) return Math.max(spacing, 0.18f);
        if (PRESET_DRAFT.equalsIgnoreCase(preset)) return Math.max(0f, spacing - 0.04f);
        if (PRESET_POEM.equalsIgnoreCase(preset)) return Math.max(spacing, 0.28f);
        return spacing;
    }

    public static float paragraphSpaceAbove(NotebookInfo nb, boolean typographyPolishEnabled) {
        String preset = stylePreset(nb);
        if (PRESET_DRAFT.equalsIgnoreCase(preset)) return 0f;
        if (PRESET_POEM.equalsIgnoreCase(preset)) return 4f;
        if (PRESET_JOURNAL.equalsIgnoreCase(preset)) return 2f;
        return typographyPolishEnabled ? 2f : 0f;
    }

    public static float paragraphSpaceBelow(NotebookInfo nb, boolean typographyPolishEnabled) {
        String preset = stylePreset(nb);
        if (PRESET_DRAFT.equalsIgnoreCase(preset)) return 2f;
        if (PRESET_POEM.equalsIgnoreCase(preset)) return 10f;
        if (PRESET_JOURNAL.equalsIgnoreCase(preset)) return 8f;
        return typographyPolishEnabled ? 6f : 0f;
    }

    public static BufferedImage loadImage(String path) {
        if (path == null || path.isBlank()) return null;
        BufferedImage cached = IMAGE_CACHE.get(path);
        if (cached != null) return cached;
        try {
            BufferedImage img;
            if (path.startsWith("img/")) {
                java.awt.Image awt = ResourceLoader.createImage(path);
                if (awt == null) return null;
                int w = Math.max(1, new javax.swing.ImageIcon(awt).getIconWidth());
                int h = Math.max(1, new javax.swing.ImageIcon(awt).getIconHeight());
                img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = img.createGraphics();
                g2.drawImage(awt, 0, 0, null);
                g2.dispose();
            } else {
                img = ImageIO.read(new File(path));
            }
            if (img != null) IMAGE_CACHE.put(path, img);
            return img;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void paintNotebookCover(Graphics2D g2, Component observer, NotebookInfo nb,
                                          int x, int y, int size, float alpha) {
        if (g2 == null || nb == null || alpha <= 0.001f || size <= 0) return;
        String coverPath = coverOrBackground(nb);
        if (coverPath == null || coverPath.isBlank()) return;

        BufferedImage maskedCover = maskedCoverImage(coverPath);
        if (maskedCover == null) return;
        java.awt.Composite oldComposite = g2.getComposite();
        g2.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, Math.min(1f, alpha))));
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.drawImage(maskedCover, x, y, size, size, observer);
        g2.setComposite(oldComposite);
    }

    private static BufferedImage maskedCoverImage(String coverPath) {
        CoverMask mask = coverMask();
        if (mask == null) return null;
        BufferedImage cover = loadImage(coverPath);
        if (cover == null) return null;
        String key = coverCacheKey(coverPath);
        BufferedImage cached = COVER_RENDER_CACHE.get(key);
        if (cached != null) return cached;

        BufferedImage rendered = new BufferedImage(mask.width(), mask.height(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = rendered.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        Rectangle b = mask.bounds;
        double scale = Math.max(b.width / (double) cover.getWidth(), b.height / (double) cover.getHeight());
        int drawW = Math.max(1, (int) Math.ceil(cover.getWidth() * scale));
        int drawH = Math.max(1, (int) Math.ceil(cover.getHeight() * scale));
        int drawX = b.x + (b.width - drawW) / 2;
        int drawY = b.y + (b.height - drawH) / 2;
        g2.drawImage(cover, drawX, drawY, drawW, drawH, null);
        g2.setComposite(AlphaComposite.DstIn);
        g2.drawImage(mask.alpha, 0, 0, null);
        g2.dispose();

        COVER_RENDER_CACHE.put(key, rendered);
        return rendered;
    }

    private static String coverCacheKey(String coverPath) {
        String trimmed = coverPath == null ? "" : coverPath.trim();
        if (trimmed.startsWith("img/")) return trimmed;
        try {
            File f = new File(trimmed);
            if (f.exists()) {
                return f.getAbsolutePath() + "|" + f.lastModified() + "|" + f.length();
            }
        } catch (Throwable ignored) {}
        return trimmed;
    }

    private static CoverMask coverMask() {
        CoverMask cached = coverMask;
        if (cached != null) return cached;
        synchronized (NotebookPersonalization.class) {
            if (coverMask == null) {
                coverMask = buildCoverMask();
            }
            return coverMask;
        }
    }

    private static CoverMask buildCoverMask() {
        BufferedImage icon = loadResourceImage(NOTEBOOK_MASK_RESOURCE);
        if (icon == null) return null;
        int w = icon.getWidth();
        int h = icon.getHeight();
        if (w <= 0 || h <= 0) return null;

        boolean[] line = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = (icon.getRGB(x, y) >>> 24) & 0xFF;
                if (a > LINE_ALPHA_THRESHOLD) {
                    line[y * w + x] = true;
                }
            }
        }

        boolean[] boundary = dilate(line, w, h, LINE_DILATION_RADIUS);
        boolean[] outside = floodOutside(boundary, w, h);
        int minX = w, minY = h, maxX = -1, maxY = -1, count = 0;
        int[] pixels = new int[w * h];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                if (!boundary[idx] && !outside[idx]) {
                    pixels[idx] = 0xFFFFFFFF;
                    count++;
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }

        if (count <= 0 || maxX < minX || maxY < minY) return null;
        BufferedImage alpha = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        alpha.setRGB(0, 0, w, h, pixels, 0, w);
        return new CoverMask(alpha, new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1));
    }

    private static BufferedImage loadResourceImage(String resourcePath) {
        try {
            java.awt.Image awt = ResourceLoader.createImage(resourcePath);
            if (awt == null) return null;
            int w = Math.max(1, new javax.swing.ImageIcon(awt).getIconWidth());
            int h = Math.max(1, new javax.swing.ImageIcon(awt).getIconHeight());
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            g2.drawImage(awt, 0, 0, null);
            g2.dispose();
            return img;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean[] dilate(boolean[] src, int w, int h, int radius) {
        boolean[] out = new boolean[src.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!src[y * w + x]) continue;
                int y0 = Math.max(0, y - radius);
                int y1 = Math.min(h - 1, y + radius);
                int x0 = Math.max(0, x - radius);
                int x1 = Math.min(w - 1, x + radius);
                for (int yy = y0; yy <= y1; yy++) {
                    int off = yy * w;
                    for (int xx = x0; xx <= x1; xx++) {
                        out[off + xx] = true;
                    }
                }
            }
        }
        return out;
    }

    private static boolean[] floodOutside(boolean[] boundary, int w, int h) {
        boolean[] outside = new boolean[boundary.length];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int x = 0; x < w; x++) {
            enqueueIfOpen(x, 0, w, boundary, outside, q);
            enqueueIfOpen(x, h - 1, w, boundary, outside, q);
        }
        for (int y = 1; y < h - 1; y++) {
            enqueueIfOpen(0, y, w, boundary, outside, q);
            enqueueIfOpen(w - 1, y, w, boundary, outside, q);
        }

        while (!q.isEmpty()) {
            int idx = q.removeFirst();
            int x = idx % w;
            int y = idx / w;
            if (x > 0) enqueueIfOpen(x - 1, y, w, boundary, outside, q);
            if (x + 1 < w) enqueueIfOpen(x + 1, y, w, boundary, outside, q);
            if (y > 0) enqueueIfOpen(x, y - 1, w, boundary, outside, q);
            if (y + 1 < h) enqueueIfOpen(x, y + 1, w, boundary, outside, q);
        }
        return outside;
    }

    private static void enqueueIfOpen(int x, int y, int w, boolean[] boundary, boolean[] outside, ArrayDeque<Integer> q) {
        int idx = y * w + x;
        if (boundary[idx] || outside[idx]) return;
        outside[idx] = true;
        q.addLast(idx);
    }

    private record CoverMask(BufferedImage alpha, Rectangle bounds) {
        private int width() { return alpha.getWidth(); }
        private int height() { return alpha.getHeight(); }
    }

    public static Color withAlpha(Color color, int alpha) {
        if (color == null) color = NotebookInfo.defaultAccentFor(NotebookInfo.Type.JOURNAL);
        int safe = Math.max(0, Math.min(255, alpha));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), safe);
    }
}
