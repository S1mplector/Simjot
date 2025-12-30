/*
 * SIMJOT - PROPRIETARY
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 * 
 * This source code is licensed under the Simjot Source-Available Personal Use License.
 * You may view and study this code for personal, non-commercial use.
 * Distribution, commercial use, and derivative works are strictly prohibited.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.util;

/**
 * Utility class for accent color extraction and manipulation.
 * @author S1mplector
 */

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public final class AccentColorUtil {
    private AccentColorUtil() {}

    public static Color extractAccent(Image src) {
        if (src == null) return defaultAccent();
        int sw = 64, sh = 64;
        BufferedImage img = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, sw, sh, null);
        g.dispose();

        // Hue histogram weighted by saturation^2 * brightness
        float[] hueWeight = new float[36]; // 10-degree bins
        float[][] sumRGB = new float[36][3];
        int[] count = new int[36];

        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < sw; x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a < 20) continue; // skip transparent
                int r = (argb >>> 16) & 0xFF;
                int g2 = (argb >>> 8) & 0xFF;
                int b = (argb) & 0xFF;
                // skip near grey and extremes
                int max = Math.max(r, Math.max(g2, b));
                int min = Math.min(r, Math.min(g2, b));
                if (max - min < 18) continue; // low chroma
                if (max < 24 || min > 232) continue; // too dark/bright
                float[] hsb = Color.RGBtoHSB(r, g2, b, null);
                float s = hsb[1];
                float v = hsb[2];
                if (s < 0.2f || v < 0.25f) continue; // avoid dull colors
                int bin = Math.min(35, Math.max(0, (int) Math.floor(hsb[0] * 36f)));
                float w = s * s * (0.5f + 0.5f * v);
                hueWeight[bin] += w;
                sumRGB[bin][0] += r * w;
                sumRGB[bin][1] += g2 * w;
                sumRGB[bin][2] += b * w;
                count[bin]++;
            }
        }
        int best = -1;
        float bestW = 0f;
        for (int i = 0; i < 36; i++) {
            if (hueWeight[i] > bestW) { bestW = hueWeight[i]; best = i; }
        }
        if (best < 0 || bestW <= 0) return defaultAccent();
        float r = sumRGB[best][0] / bestW;
        float g3 = sumRGB[best][1] / bestW;
        float b = sumRGB[best][2] / bestW;
        Color c = new Color(Math.min(255, Math.max(0, Math.round(r))),
                            Math.min(255, Math.max(0, Math.round(g3))),
                            Math.min(255, Math.max(0, Math.round(b))));
        // Boost saturation slightly
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        hsb[1] = Math.min(1f, hsb[1] * 1.1f + 0.05f);
        hsb[2] = Math.min(1f, hsb[2] * 1.02f);
        return Color.getHSBColor(hsb[0], hsb[1], hsb[2]);
    }

    public static Color lighten(Color c, float amount) {
        amount = clamp01(amount);
        int r = (int)(c.getRed() + (255 - c.getRed()) * amount);
        int g = (int)(c.getGreen() + (255 - c.getGreen()) * amount);
        int b = (int)(c.getBlue() + (255 - c.getBlue()) * amount);
        return new Color(r, g, b);
    }

    public static Color darken(Color c, float amount) {
        amount = clamp01(amount);
        int r = (int)(c.getRed() * (1 - amount));
        int g = (int)(c.getGreen() * (1 - amount));
        int b = (int)(c.getBlue() * (1 - amount));
        return new Color(r, g, b);
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    public static Color defaultAccent() {
        return new Color(0, 120, 215);
    }
}
