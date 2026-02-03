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
    private static final int DOCK_PADDING_H = 24;
    private static final int DOCK_PADDING_V = 14;
    private static final int ICON_SIZE = 42;
    private static final float MAX_SCALE = 1.12f;
    private static final float MAX_GLOW = 1f;
    private static final int CORNER_RADIUS = 28;
    
    // Glass colors - more transparent
    private static final Color GLASS_BG = new Color(255, 255, 255, 28);
    private static final Color GLASS_BORDER = new Color(255, 255, 255, 70);
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
            Rectangle bounds = new Rectangle(x, y, ITEM_SIZE, ITEM_SIZE);
            if (bounds.contains(p)) {
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
        RoundRectangle2D.Float shape = new RoundRectangle2D.Float(0, 4, w, h - 4, CORNER_RADIUS, CORNER_RADIUS);
        
        // Outer shadow using native aero math for smooth falloff
        int shadowLayers = 10;
        for (int i = 0; i < shadowLayers; i++) {
            int alpha = NativeAccess.aeroOuterGlowAlpha(shadowLayers - i, shadowLayers, 35);
            g2.setColor(new Color(0, 0, 0, alpha));
            g2.fill(new RoundRectangle2D.Float(-i, 4 + i, w + i * 2, h - 4, CORNER_RADIUS + i, CORNER_RADIUS + i));
        }
        
        // Glass background - frosted effect
        g2.setColor(GLASS_BG);
        g2.fill(shape);
        
        // Inner gradient for depth using native color lerp
        int topColor = NativeAccess.aeroLerpColor(0x23FFFFFF, 0x14C8D4DC, 0.5f);
        int bottomColor = NativeAccess.aeroLerpColor(0x14C8D4DC, 0x0AC8D4DC, 0.5f);
        GradientPaint innerGradient = new GradientPaint(
            0, 4, new Color(topColor, true),
            0, h, new Color(bottomColor, true)
        );
        g2.setPaint(innerGradient);
        g2.fill(shape);
        
        // Top highlight (gloss) - brighter for glass effect
        GradientPaint gloss = new GradientPaint(
            0, 4, new Color(255, 255, 255, 70),
            0, h * 0.45f, new Color(255, 255, 255, 0)
        );
        g2.setPaint(gloss);
        g2.fill(new RoundRectangle2D.Float(1, 5, w - 2, h * 0.4f, CORNER_RADIUS - 2, CORNER_RADIUS - 2));
        
        // Border with subtle glow
        g2.setColor(GLASS_BORDER);
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(new RoundRectangle2D.Float(0.5f, 4.5f, w - 1, h - 5, CORNER_RADIUS, CORNER_RADIUS));
        
        // Inner light stroke for depth
        g2.setColor(new Color(255, 255, 255, 50));
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new RoundRectangle2D.Float(1.5f, 5.5f, w - 3, h - 7, CORNER_RADIUS - 2, CORNER_RADIUS - 2));
    }
    
    private void drawItems(Graphics2D g2) {
        if (items.isEmpty() || itemScales == null) return;
        
        int startX = DOCK_PADDING_H;
        int baseY = DOCK_PADDING_V;
        
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
            
            // Draw icon
            int iconScaledSize = (int)(ICON_SIZE * scale);
            int iconX = itemX + (scaledSize - iconScaledSize) / 2;
            int iconY = itemY + (scaledSize - iconScaledSize) / 2 - 4;
            
            String resPath = ImageIconRenderer.mapIdToResource(item.iconId);
            if (resPath != null) {
                ImageIconRenderer.draw(g2, resPath, iconX, iconY, iconScaledSize, this, true);
            }
            
            // Draw label
            drawItemLabel(g2, item.label, x, baseY + ITEM_SIZE + 4, ITEM_SIZE, glow);
        }
    }
    
    private void drawItemBackground(Graphics2D g2, int x, int y, int size, float glow) {
        int radius = 14;
        RoundRectangle2D.Float shape = new RoundRectangle2D.Float(x, y, size, size, radius, radius);
        
        // Glow effect when hovered - using native aero for smooth falloff
        if (glow > 0.01f) {
            int glowLayers = 8;
            for (int i = 0; i < glowLayers; i++) {
                int baseAlpha = NativeAccess.aeroOuterGlowAlpha(glowLayers - i, glowLayers, 90);
                int alpha = (int)(baseAlpha * glow);
                // Blend accent color using native lerp
                int glowColor = NativeAccess.aeroLerpColor(0x006496FF, 0x5A6496FF, glow);
                g2.setColor(new Color((glowColor & 0xFFFFFF) | (alpha << 24), true));
                g2.fill(new RoundRectangle2D.Float(x - i, y - i, size + i * 2, size + i * 2, radius + i, radius + i));
            }
        }
        
        // Item background - lerp between base and hover states
        int baseBg = 0x28FFFFFF;
        int hoverBg = 0x46FFFFFF;
        int bgColor = NativeAccess.aeroLerpColor(baseBg, hoverBg, glow);
        g2.setColor(new Color(bgColor, true));
        g2.fill(shape);
        
        // Top gloss - stronger on hover
        int glossAlpha = (int)(80 + 50 * glow);
        GradientPaint itemGloss = new GradientPaint(
            x, y, new Color(255, 255, 255, glossAlpha),
            x, y + size * 0.45f, new Color(255, 255, 255, 0)
        );
        g2.setPaint(itemGloss);
        g2.fill(new RoundRectangle2D.Float(x + 1, y + 1, size - 2, size * 0.4f, radius - 2, radius - 2));
        
        // Border - brighter on hover
        int borderAlpha = (int)(50 + 80 * glow);
        g2.setColor(new Color(255, 255, 255, borderAlpha));
        g2.setStroke(new BasicStroke(1f));
        g2.draw(shape);
    }
    
    private void drawItemLabel(Graphics2D g2, String label, int x, int y, int width, float glow) {
        Font font = new Font("SF Pro Text", Font.PLAIN, 11);
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        
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
        final String iconId;
        final Runnable action;
        
        DockItem(String label, String iconId, Runnable action) {
            this.label = label;
            this.iconId = iconId;
            this.action = action;
        }
    }
}
