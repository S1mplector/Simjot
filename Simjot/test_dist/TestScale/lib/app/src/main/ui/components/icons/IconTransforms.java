/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.components.icons;

import java.awt.Component;
import java.awt.Graphics2D;

/**
 * Small utility for drawing icons with simple transforms (rotation/flip) while
 * keeping the call sites compact.
 */
public final class IconTransforms {
    private static final double EPSILON = 0.0001;

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
        if (Math.abs(radians) < EPSILON) {
            return ImageIconRenderer.draw(g2, resourcePath, x, y, size, obs, shadow);
        }
        double cx = x + size / 2.0;
        double cy = y + size / 2.0;
        g2.rotate(radians, cx, cy);
        try {
            return ImageIconRenderer.draw(g2, resourcePath, x, y, size, obs, shadow);
        } finally {
            // Revert without allocating/copying a full AffineTransform per icon draw.
            g2.rotate(-radians, cx, cy);
         }
    }
}
