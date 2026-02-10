/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoglu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.dock;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

import main.infrastructure.ffi.NativeAccess;
import main.ui.components.icons.ImageIconRenderer;

/**
 * A beautiful frosted glass dock bar inspired by macOS dock with Frutiger Aero aesthetics.
 * Features smooth hover animations, glass morphism, and elegant transitions.
 */
public class GlassDockBar extends JPanel {
    
    private final List<DockItem> items = new ArrayList<>();
    private int hoveredIndex = -1;
    private float[] itemScales;
    private float[] itemGlows;
    private Timer animationTimer;
    
    // Visual constants
    private static final int ITEM_SIZE = 72;
    private static final int ITEM_SPACING = 16;
    private static final int DOCK_PADDING_H = 28;
    private static final int DOCK_PADDING_V = 18;
    private static final int ICON_SIZE = 42;
    private static final float MAX_SCALE = 1.12f;
    private static final float MAX_GLOW = 1f;
    private static final int CORNER_RADIUS = 36;
    private static final Font LABEL_FONT = new Font("SF Pro Text", Font.PLAIN, 11);
    
    // Text colors
    private static final Color TEXT_COLOR = new Color(50, 55, 65);
    private static final Color TEXT_SHADOW = new Color(255, 255, 255, 180);

    public GlassDockBar() {
        setOpaque(false);
        setLayout(null); // Custom positioning
        
        // Animation timer for smooth transitions
        animationTimer = new Timer(16, e -> animateItems());
        animationTimer.start();
        
        // Mouse tracking
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateHoveredItem(e.getPoint());
            }
        });
        
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                hoveredIndex = -1;
            }
            
            @Override
            public void mouseClicked(MouseEvent e) {
                int idx = getItemAtPoint(e.getPoint());
                if (idx >= 0 && idx < items.size()) {
                    items.get(idx).action.run();
                }
            }
        });
    }
    
    public void addItem(String label, String iconId, Runnable action) {
        items.add(new DockItem(label, iconId, action));
        itemScales = new float[items.size()];
        itemGlows = new float[items.size()];
        for (int i = 0; i < items.size(); i++) {
            itemScales[i] = 1f;
            itemGlows[i] = 0f;
        }
        updatePreferredSize();
        repaint();
    }
    
    private void updatePreferredSize() {
        int width = DOCK_PADDING_H * 2 + items.size() * ITEM_SIZE + (items.size() - 1) * ITEM_SPACING;
        int height = DOCK_PADDING_V * 2 + ITEM_SIZE + 18; // Extra for label
        setPreferredSize(new Dimension(width, height));
        setMinimumSize(new Dimension(width, height));
        setMaximumSize(new Dimension(width, height));
    }
    
    private void updateHoveredItem(Point p) {
        hoveredIndex = getItemAtPoint(p);
        setCursor(hoveredIndex >= 0 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
    }
    
    private int getItemAtPoint(Point p) {
        int startX = DOCK_PADDING_H;
        int y = DOCK_PADDING_V;
        
        for (int i = 0; i < items.size(); i++) {
            int x = startX + i * (ITEM_SIZE + ITEM_SPACING);
            if (p.x >= x && p.x < x + ITEM_SIZE && p.y >= y && p.y < y + ITEM_SIZE) {
                return i;
            }
        }
        return -1;
    }
    
    private void animateItems() {
        if (itemScales == null) return;
        
        boolean needsRepaint = false;
        for (int i = 0; i < items.size(); i++) {
            float targetScale = (i == hoveredIndex) ? MAX_SCALE : 1f;
            float targetGlow = (i == hoveredIndex) ? MAX_GLOW : 0f;
            
            // Smooth spring animation
            float scaleDiff = targetScale - itemScales[i];
            float glowDiff = targetGlow - itemGlows[i];
            
            if (Math.abs(scaleDiff) > 0.001f) {
                itemScales[i] += scaleDiff * 0.2f;
                needsRepaint = true;
            } else {
                itemScales[i] = targetScale;
            }
            
            if (Math.abs(glowDiff) > 0.001f) {
                itemGlows[i] += glowDiff * 0.15f;
                needsRepaint = true;
            } else {
                itemGlows[i] = targetGlow;
            }
        }
        
        if (needsRepaint) {
            repaint();
        }
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        
        int w = getWidth();
        int h = getHeight();
        
        // Draw dock background
        drawDockBackground(g2, w, h);
        
        // Draw items
        drawItems(g2);
        
        g2.dispose();
    }
    
    private void drawDockBackground(Graphics2D g2, int w, int h) {
        // Calculate dock height to encompass items but not labels
        int dockH = DOCK_PADDING_V * 2 + ITEM_SIZE + 8;
        
        // Main shape - pill-like with equal corner radius
        RoundRectangle2D.Float shape = new RoundRectangle2D.Float(0, 0, w, dockH, CORNER_RADIUS, CORNER_RADIUS);
        
        // Soft outer shadow - ensure all corners stay rounded
        int shadowLayers = 8;
        for (int i = shadowLayers; i > 0; i--) {
            int alpha = NativeAccess.aeroOuterGlowAlpha(i, shadowLayers, 18);
            g2.setColor(new Color(0, 0, 0, alpha));
            float spread = (shadowLayers - i) * 1.2f;
            // Corner radius grows with spread to maintain roundness
            float cornerR = CORNER_RADIUS + spread * 0.5f;
            g2.fill(new RoundRectangle2D.Float(
                -spread, 
                spread * 0.3f, 
                w + spread * 2, 
                dockH + spread * 0.7f, 
                cornerR, cornerR));
        }
        
        // Pure glass background - very transparent
        g2.setColor(new Color(255, 255, 255, 15));
        g2.fill(shape);
        
        // Subtle frosted overlay
        GradientPaint frost = new GradientPaint(
            0, 0, new Color(255, 255, 255, 12),
            0, dockH, new Color(250, 252, 255, 6)
        );
        g2.setPaint(frost);
        g2.fill(shape);
        
        // Top gloss highlight
        GradientPaint gloss = new GradientPaint(
            0, 0, new Color(255, 255, 255, 35),
            0, dockH * 0.35f, new Color(255, 255, 255, 0)
        );
        g2.setPaint(gloss);
        g2.fill(new RoundRectangle2D.Float(1, 1, w - 2, dockH * 0.35f, CORNER_RADIUS - 1, CORNER_RADIUS - 1));
        
        // Border - subtle
        g2.setColor(new Color(255, 255, 255, 45));
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, dockH - 1, CORNER_RADIUS, CORNER_RADIUS));
    }
    
    private void drawItems(Graphics2D g2) {
        if (items.isEmpty() || itemScales == null) return;
        
        int startX = DOCK_PADDING_H;
        int baseY = DOCK_PADDING_V;
        g2.setFont(LABEL_FONT);
        FontMetrics labelFm = g2.getFontMetrics();
        
        for (int i = 0; i < items.size(); i++) {
            DockItem item = items.get(i);
            float scale = itemScales[i];
            float glow = itemGlows[i];
            
            int x = startX + i * (ITEM_SIZE + ITEM_SPACING);
            int y = baseY;
            
            // Calculate scaled position (scale from center-bottom for bounce effect)
            int scaledSize = (int)(ITEM_SIZE * scale);
            int offsetX = (ITEM_SIZE - scaledSize) / 2;
            int offsetY = ITEM_SIZE - scaledSize; // Anchor to bottom
            
            int itemX = x + offsetX;
            int itemY = y + offsetY;
            
            // Draw item background with glow
            drawItemBackground(g2, itemX, itemY, scaledSize, glow);
            
            // Draw icon with hover transform (scale + tilt)
            int iconScaledSize = (int)(ICON_SIZE * scale);
            int iconX = itemX + (scaledSize - iconScaledSize) / 2;
            int iconY = itemY + (scaledSize - iconScaledSize) / 2 - 4;
            
            String resPath = item.resourcePath;
            if (resPath != null) {
                // Apply subtle scale and tilt on hover
                if (glow > 0.01f) {
                    java.awt.geom.AffineTransform oldTransform = g2.getTransform();
                    
                    // Calculate icon center
                    float iconCenterX = iconX + iconScaledSize / 2f;
                    float iconCenterY = iconY + iconScaledSize / 2f;
                    
                    // Apply transforms: translate to center, rotate, scale, translate back
                    float extraScale = 1f + (0.08f * glow); // Up to 8% bigger
                    float tiltAngle = -0.08f * glow; // Tilt left (negative = counterclockwise)
                    
                    g2.translate(iconCenterX, iconCenterY);
                    g2.rotate(tiltAngle);
                    g2.scale(extraScale, extraScale);
                    g2.translate(-iconCenterX, -iconCenterY);
                    
                    ImageIconRenderer.draw(g2, resPath, iconX, iconY, iconScaledSize, this, true);
                    
                    g2.setTransform(oldTransform);
                } else {
                    ImageIconRenderer.draw(g2, resPath, iconX, iconY, iconScaledSize, this, true);
                }
            }
            
            // Draw label
            drawItemLabel(g2, labelFm, item.label, x, baseY + ITEM_SIZE + 4, ITEM_SIZE, glow);
        }
    }
    
    private void drawItemBackground(Graphics2D g2, int x, int y, int size, float glow) {
        int radius = 22; // Very rounded corners for item buttons
        RoundRectangle2D.Float shape = new RoundRectangle2D.Float(x, y, size, size, radius, radius);
        
        // Glow effect when hovered - using native aero for smooth falloff
        if (glow > 0.01f) {
            int glowLayers = 6;
            for (int i = 0; i < glowLayers; i++) {
                int baseAlpha = NativeAccess.aeroOuterGlowAlpha(glowLayers - i, glowLayers, 70);
                int alpha = (int)(baseAlpha * glow);
                int glowColor = NativeAccess.aeroLerpColor(0x006496FF, 0x5A6496FF, glow);
                g2.setColor(new Color((glowColor & 0xFFFFFF) | (alpha << 24), true));
                float cornerR = radius + i;
                g2.fill(new RoundRectangle2D.Float(x - i, y - i, size + i * 2, size + i * 2, cornerR, cornerR));
            }
        }
        
        // Item background - very subtle, lerp between base and hover states
        int baseBg = 0x18FFFFFF;  // More transparent base
        int hoverBg = 0x35FFFFFF;
        int bgColor = NativeAccess.aeroLerpColor(baseBg, hoverBg, glow);
        g2.setColor(new Color(bgColor, true));
        g2.fill(shape);
        
        // Top gloss - subtle
        int glossAlpha = (int)(50 + 40 * glow);
        GradientPaint itemGloss = new GradientPaint(
            x, y, new Color(255, 255, 255, glossAlpha),
            x, y + size * 0.4f, new Color(255, 255, 255, 0)
        );
        g2.setPaint(itemGloss);
        g2.fill(new RoundRectangle2D.Float(x + 1, y + 1, size - 2, size * 0.35f, radius - 1, radius - 1));
        
        // Border - subtle
        int borderAlpha = (int)(35 + 50 * glow);
        g2.setColor(new Color(255, 255, 255, borderAlpha));
        g2.setStroke(new BasicStroke(0.8f));
        g2.draw(shape);
    }
    
    private void drawItemLabel(Graphics2D g2, FontMetrics fm, String label, int x, int y, int width, float glow) {
        int textWidth = fm.stringWidth(label);
        int textX = x + (width - textWidth) / 2;
        int textY = y + fm.getAscent();
        
        // Text shadow for readability
        g2.setColor(TEXT_SHADOW);
        g2.drawString(label, textX, textY + 1);
        
        // Main text
        float alpha = 0.7f + 0.3f * glow;
        g2.setColor(new Color(
            TEXT_COLOR.getRed(), 
            TEXT_COLOR.getGreen(), 
            TEXT_COLOR.getBlue(), 
            (int)(255 * alpha)
        ));
        g2.drawString(label, textX, textY);
    }
    
    @Override
    public void removeNotify() {
        super.removeNotify();
        if (animationTimer != null) {
            animationTimer.stop();
        }
    }
    
    private static class DockItem {
        final String label;
        final String resourcePath;
        final Runnable action;
        
        DockItem(String label, String iconId, Runnable action) {
            this.label = label;
            this.resourcePath = ImageIconRenderer.mapIdToResource(iconId);
            this.action = action;
        }
    }
}
