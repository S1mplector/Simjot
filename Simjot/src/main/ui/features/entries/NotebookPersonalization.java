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
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
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
    private static final Map<String, BufferedImage> IMAGE_CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
            return size() > IMAGE_CACHE_MAX;
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
        BufferedImage cover = loadImage(coverOrBackground(nb));
        if (cover == null) return;

        Shape coverShape = notebookCoverShape(x, y, size);
        Shape oldClip = g2.getClip();
        java.awt.Composite oldComposite = g2.getComposite();
        g2.setClip(coverShape);
        g2.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, Math.min(1f, alpha))));
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        java.awt.Rectangle b = coverShape.getBounds();
        double scale = Math.max(b.width / (double) cover.getWidth(), b.height / (double) cover.getHeight());
        int drawW = Math.max(1, (int) Math.ceil(cover.getWidth() * scale));
        int drawH = Math.max(1, (int) Math.ceil(cover.getHeight() * scale));
        int drawX = b.x + (b.width - drawW) / 2;
        int drawY = b.y + (b.height - drawH) / 2;
        g2.drawImage(cover, drawX, drawY, drawW, drawH, observer);

        g2.setComposite(oldComposite);
        g2.setClip(oldClip);
    }

    private static Shape notebookCoverShape(int x, int y, int size) {
        float s = size / 72f;
        Path2D.Float cover = new Path2D.Float();
        cover.moveTo(x + 20f * s, y + 11f * s);
        cover.lineTo(x + 54f * s, y + 5f * s);
        cover.lineTo(x + 64f * s, y + 50f * s);
        cover.lineTo(x + 31f * s, y + 58f * s);
        cover.lineTo(x + 18f * s, y + 48f * s);
        cover.closePath();
        return cover;
    }

    public static Color withAlpha(Color color, int alpha) {
        if (color == null) color = NotebookInfo.defaultAccentFor(NotebookInfo.Type.JOURNAL);
        int safe = Math.max(0, Math.min(255, alpha));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), safe);
    }
}
