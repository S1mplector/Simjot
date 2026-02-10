/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.icons;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;

/**
 * Small utility for drawing icons with simple transforms (rotation/flip) while
 * keeping the call sites compact.
 */
public final class IconTransforms {
    private IconTransforms() {}

    public static boolean drawRotated(Graphics2D g2,
                                      String resourcePath,
                                      int x,
                                      int y,
                                      int size,
                                      Component obs,
                                      boolean shadow,
                                      double radians) {
        if (g2 == null || resourcePath == null || size <= 0) return false;
        if (Math.abs(radians) < 0.0001) {
            return ImageIconRenderer.draw(g2, resourcePath, x, y, size, obs, shadow);
        }
        AffineTransform old = g2.getTransform();
        try {
            double cx = x + size / 2.0;
            double cy = y + size / 2.0;
            g2.rotate(radians, cx, cy);
            return ImageIconRenderer.draw(g2, resourcePath, x, y, size, obs, shadow);
        } finally {
            g2.setTransform(old);
        }
    }
}
