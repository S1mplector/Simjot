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

package main.ui.features.entries;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import main.infrastructure.io.ResourceLoader;

/**
 * Helper to paint a cached, opacity-controlled background image with smart fallbacks.
 * Keeps per-instance cache so each editor instance scales once per size/opacity change.
 */
public class BackgroundPainter {
    private Image backgroundImage;
    private BufferedImage cachedScaled;
    private int cachedPanelW = -1;
    private int cachedPanelH = -1;
    private int cachedX = 0;
    private int cachedY = 0;
    private float cachedOpacity = -1f;
    private String cachedBgPath = null;

    /**
     * Paints the background based on the given path and opacity.
     * - If path is null/empty: paints theme fallback gradient.
     * - If path starts with "res:": loads via ResourceLoader.
     * - Else: loads from filesystem.
     *
     * @param g target graphics
     * @param comp component for sizing and image observers
     * @param bgPath background image path or resource key (may be null)
     * @param opacity 0..1 opacity
     * @param drawWhiteFirst if true, fills white before painting (e.g., for poems)
     */
    public void paint(Graphics g, JComponent comp, String bgPath, float opacity, boolean drawWhiteFirst) {
        if (drawWhiteFirst) {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, comp.getWidth(), comp.getHeight());
        }

        if (bgPath != null && !bgPath.isEmpty()) {
            // Invalidate cached image if the path changed
            if (cachedBgPath == null || !cachedBgPath.equals(bgPath)) {
                backgroundImage = null;
                cachedScaled = null;
                cachedPanelW = cachedPanelH = -1;
                cachedOpacity = -1f;
                cachedBgPath = bgPath;
            }
            // Load if needed
            if (backgroundImage == null) {
                if (bgPath.startsWith("res:")) {
                    String resPath = bgPath.substring(4);
                    backgroundImage = ResourceLoader.createImage("Simjot/" + resPath);
                } else {
                    backgroundImage = new ImageIcon(bgPath).getImage();
                }
            }

            if (backgroundImage != null) {
                int panelW = comp.getWidth();
                int panelH = comp.getHeight();
                if (cachedScaled == null || panelW != cachedPanelW || panelH != cachedPanelH || opacity != cachedOpacity) {
                    int imgW = backgroundImage.getWidth(comp);
                    int imgH = backgroundImage.getHeight(comp);
                    if (imgW > 0 && imgH > 0) {
                        double scale = Math.max((double) panelW / imgW, (double) panelH / imgH);
                        int drawW = (int) Math.round(imgW * scale);
                        int drawH = (int) Math.round(imgH * scale);
                        cachedX = (panelW - drawW) / 2;
                        cachedY = (panelH - drawH) / 2;
                        cachedPanelW = panelW;
                        cachedPanelH = panelH;
                        cachedOpacity = opacity;

                        BufferedImage tmp = new BufferedImage(drawW, drawH, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D cg = tmp.createGraphics();
                        cg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
                        cg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        cg.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                        cg.drawImage(backgroundImage, 0, 0, drawW, drawH, comp);
                        cg.dispose();
                        cachedScaled = tmp;
                    }
                }
                if (cachedScaled != null) {
                    g.drawImage(cachedScaled, cachedX, cachedY, comp);
                    return;
                }
            }
        }
        // Fallback: solid background (no gradient)
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, comp.getWidth(), comp.getHeight());
        
    }
}
