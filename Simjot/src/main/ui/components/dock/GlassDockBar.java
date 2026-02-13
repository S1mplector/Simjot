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
    private float[] itemX;
    private float[] itemY;
    private float[] itemSize;
    private float dockX;
    private float dockWidth;
    private float dockHeight;
    private Point mousePoint;
    private boolean mouseInside;
    private float expandProgress;
    private long lastHoverNanos;
    private long lastFrameNanos;
    private Timer animationTimer;
    
    // Visual constants
    private static final int ITEM_SIZE = 72;
    private static final int ITEM_SPACING = 16;
    private static final int DOCK_PADDING_H = 28;
    private static final int DOCK_PADDING_V = 18;
    private static final int ICON_SIZE = 42;
    private static final float MAX_SCALE = 1.12f;
    private static final float MAX_GLOW = 1f;
    private static final float COLLAPSED_ITEM_SCALE = 0.9f;
    private static final float STACK_SPREAD_X = 1.5f;
    private static final float STACK_SPREAD_Y = 2.2f;
    private static final float ANIMATION_RATE = 10.5f;
    private static final long COLLAPSE_LINGER_NS = 170_000_000L;
    private static final float LAYOUT_EPSILON = 0.001f;
    private static final float HOVER_ZONE_MARGIN = 14f;
    private static final int LABEL_AREA = 18;
    private static final int CORNER_RADIUS = 36;
    private static final Font LABEL_FONT = new Font("SF Pro Text", Font.PLAIN, 11);
    
    // Text colors
    private static final Color TEXT_COLOR = new Color(50, 55, 65);
    private static final Color TEXT_SHADOW = new Color(255, 255, 255, 180);

    public GlassDockBar() {
        setOpaque(false);
        setLayout(null); // Custom positioning
        setDoubleBuffered(true);
        
        // Animation timer for smooth transitions
        animationTimer = new Timer(16, e -> animateItems());
        animationTimer.setCoalesce(true);
        animationTimer.setRepeats(true);
        animationTimer.start();
        
        // Mouse tracking
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                mousePoint = e.getPoint();
                mouseInside = true;
                updateHoveredItem(mousePoint);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                mousePoint = e.getPoint();
                mouseInside = true;
                updateHoveredItem(mousePoint);
            }
        });
        
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                mouseInside = true;
                mousePoint = e.getPoint();
                updateHoveredItem(mousePoint);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                mouseInside = false;
                mousePoint = null;
                hoveredIndex = -1;
                setCursor(Cursor.getDefaultCursor());
            }
            
            @Override
            public void mouseClicked(MouseEvent e) {
                updateLayoutCache(getWidth(), expandProgress);
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
        itemX = new float[items.size()];
        itemY = new float[items.size()];
        itemSize = new float[items.size()];
        for (int i = 0; i < items.size(); i++) {
            itemScales[i] = 1f;
            itemGlows[i] = 0f;
        }
        updatePreferredSize();
        updateLayoutCache(getPreferredSize().width, expandProgress);
        repaint();
    }
    
    private void updatePreferredSize() {
        int width = DOCK_PADDING_H * 2 + items.size() * ITEM_SIZE + Math.max(0, items.size() - 1) * ITEM_SPACING;
        int height = DOCK_PADDING_V * 2 + ITEM_SIZE + LABEL_AREA; // Extra for label
        setPreferredSize(new Dimension(width, height));
        setMinimumSize(new Dimension(width, height));
        setMaximumSize(new Dimension(width, height));
    }
    
    private void updateHoveredItem(Point p) {
        updateLayoutCache(getWidth(), expandProgress);
        hoveredIndex = getItemAtPoint(p);
        Cursor targetCursor = hoveredIndex >= 0 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor();
        if (getCursor() != targetCursor) {
            setCursor(targetCursor);
        }
    }
    
    private int getItemAtPoint(Point p) {
        if (p == null || itemX == null || itemY == null || itemSize == null) {
            return -1;
        }
        for (int i = items.size() - 1; i >= 0; i--) {
            float x = itemX[i];
            float y = itemY[i];
            float size = itemSize[i];
            if (p.x >= x && p.x < x + size && p.y >= y && p.y < y + size) {
                return i;
            }
        }
        return -1;
    }
    
    private void animateItems() {
        if (itemScales == null) return;
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
        }
        float dt = Math.min(0.05f, (now - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = now;

        updateLayoutCache(getWidth(), expandProgress);
        boolean inActiveZone = mouseInside && mousePoint != null && isInActiveZone(mousePoint);
        if (inActiveZone) {
            lastHoverNanos = now;
        }
        boolean shouldExpand = inActiveZone || (now - lastHoverNanos) < COLLAPSE_LINGER_NS;
        float targetExpand = shouldExpand ? 1f : 0f;

        boolean needsRepaint = false;
        float expandDiff = targetExpand - expandProgress;
        if (Math.abs(expandDiff) > LAYOUT_EPSILON) {
            float t = 1f - (float)Math.exp(-ANIMATION_RATE * dt);
            expandProgress += expandDiff * t;
            needsRepaint = true;
        } else {
            expandProgress = targetExpand;
        }

        updateLayoutCache(getWidth(), expandProgress);
        int newHoveredIndex = (shouldExpand && mousePoint != null) ? getItemAtPoint(mousePoint) : -1;
        if (newHoveredIndex != hoveredIndex) {
            hoveredIndex = newHoveredIndex;
            Cursor targetCursor = hoveredIndex >= 0 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor();
            if (getCursor() != targetCursor) {
                setCursor(targetCursor);
            }
            needsRepaint = true;
        }

        float itemT = 1f - (float)Math.exp(-(ANIMATION_RATE + 1.5f) * dt);
        for (int i = 0; i < items.size(); i++) {
            float targetScale = (i == hoveredIndex) ? MAX_SCALE : 1f;
            float targetGlow = (i == hoveredIndex) ? MAX_GLOW : 0f;
            float scaleDiff = targetScale - itemScales[i];
            float glowDiff = targetGlow - itemGlows[i];
            
            if (Math.abs(scaleDiff) > LAYOUT_EPSILON) {
                itemScales[i] += scaleDiff * itemT;
                needsRepaint = true;
            } else {
                itemScales[i] = targetScale;
            }
            
            if (Math.abs(glowDiff) > LAYOUT_EPSILON) {
                itemGlows[i] += glowDiff * itemT;
                needsRepaint = true;
            } else {
                itemGlows[i] = targetGlow;
            }
        }
        
        if (needsRepaint) {
            repaint();
        }
    }

    private boolean isInActiveZone(Point p) {
        if (dockWidth <= 0f || dockHeight <= 0f) {
            return false;
        }
        float margin = HOVER_ZONE_MARGIN;
        RoundRectangle2D.Float activeZone = new RoundRectangle2D.Float(
            dockX - margin,
            -margin * 0.5f,
            dockWidth + margin * 2f,
            dockHeight + LABEL_AREA + margin,
            CORNER_RADIUS + margin,
            CORNER_RADIUS + margin
        );
        return activeZone.contains(p);
    }

    private void updateLayoutCache(int componentWidth, float progress) {
        if (items.isEmpty() || itemX == null || componentWidth <= 0) {
            return;
        }
        float t = easeInOut(progress);
        float itemExpandedSize = ITEM_SIZE;
        float itemCollapsedSize = ITEM_SIZE * COLLAPSED_ITEM_SCALE;
        float expandedWidth = DOCK_PADDING_H * 2f + items.size() * itemExpandedSize + Math.max(0, items.size() - 1) * ITEM_SPACING;
        float collapsedWidth = DOCK_PADDING_H * 2f + itemCollapsedSize + 18f;
        dockWidth = lerp(collapsedWidth, expandedWidth, t);
        dockHeight = DOCK_PADDING_V * 2f + ITEM_SIZE + 8f;
        dockX = (componentWidth - dockWidth) * 0.5f;

        float expandedStartX = dockX + DOCK_PADDING_H;
        float expandedY = DOCK_PADDING_V;
        float collapsedStartX = dockX + (dockWidth - itemCollapsedSize) * 0.5f;
        float collapsedY = DOCK_PADDING_V + 4f;
        float centerIndex = (items.size() - 1) * 0.5f;

        for (int i = 0; i < items.size(); i++) {
            float stackedOffsetX = (i - centerIndex) * STACK_SPREAD_X;
            float stackedOffsetY = (items.size() - 1 - i) * STACK_SPREAD_Y;
            float collapsedX = collapsedStartX + stackedOffsetX;
            float collapsedItemY = collapsedY + stackedOffsetY;
            float expandedX = expandedStartX + i * (ITEM_SIZE + ITEM_SPACING);
            itemX[i] = lerp(collapsedX, expandedX, t);
            itemY[i] = lerp(collapsedItemY, expandedY, t);
            itemSize[i] = lerp(itemCollapsedSize, itemExpandedSize, t);
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float easeInOut(float t) {
        float clamped = Math.max(0f, Math.min(1f, t));
        return clamped * clamped * (3f - 2f * clamped);
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        updateLayoutCache(getWidth(), expandProgress);
        
        // Draw dock background
        drawDockBackground(g2);
        
        // Draw items
        drawItems(g2);
        
        g2.dispose();
    }
    
    private void drawDockBackground(Graphics2D g2) {
        if (dockWidth <= 0f || dockHeight <= 0f) {
            return;
        }
        // Calculate dock height to encompass items but not labels
        float dockH = dockHeight;
        
        // Main shape - pill-like with equal corner radius
        RoundRectangle2D.Float shape = new RoundRectangle2D.Float(dockX, 0, dockWidth, dockH, CORNER_RADIUS, CORNER_RADIUS);
        
        // Soft outer shadow - ensure all corners stay rounded
        int shadowLayers = 8;
        for (int i = shadowLayers; i > 0; i--) {
            int alpha = NativeAccess.aeroOuterGlowAlpha(i, shadowLayers, 18);
            g2.setColor(new Color(0, 0, 0, alpha));
            float spread = (shadowLayers - i) * 1.2f;
            // Corner radius grows with spread to maintain roundness
            float cornerR = CORNER_RADIUS + spread * 0.5f;
            g2.fill(new RoundRectangle2D.Float(
                dockX - spread, 
                spread * 0.3f, 
                dockWidth + spread * 2, 
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
            dockX, 0, new Color(255, 255, 255, 35),
            dockX, dockH * 0.35f, new Color(255, 255, 255, 0)
        );
        g2.setPaint(gloss);
        g2.fill(new RoundRectangle2D.Float(dockX + 1, 1, dockWidth - 2, dockH * 0.35f, CORNER_RADIUS - 1, CORNER_RADIUS - 1));
        
        // Border - subtle
        g2.setColor(new Color(255, 255, 255, 45));
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new RoundRectangle2D.Float(dockX + 0.5f, 0.5f, dockWidth - 1, dockH - 1, CORNER_RADIUS, CORNER_RADIUS));
    }
    
    private void drawItems(Graphics2D g2) {
        if (items.isEmpty() || itemScales == null || itemX == null || itemY == null || itemSize == null) return;

        g2.setFont(LABEL_FONT);
        FontMetrics labelFm = g2.getFontMetrics();

        float labelAlpha = Math.max(0f, Math.min(1f, (expandProgress - 0.22f) / 0.78f));

        for (int i = 0; i < items.size(); i++) {
            DockItem item = items.get(i);
            float scale = itemScales[i];
            float glow = itemGlows[i];

            float x = itemX[i];
            float y = itemY[i];
            float baseSize = itemSize[i];

            // Calculate scaled position (scale from center-bottom for bounce effect)
            float scaledSize = baseSize * scale;
            float offsetX = (baseSize - scaledSize) * 0.5f;
            float offsetY = baseSize - scaledSize; // Anchor to bottom

            float drawX = x + offsetX;
            float drawY = y + offsetY;

            // Draw item background with glow
            drawItemBackground(g2, drawX, drawY, scaledSize, glow);

            // Draw icon with hover transform (scale + tilt)
            float iconBaseScale = baseSize / ITEM_SIZE;
            int iconScaledSize = Math.max(22, Math.round(ICON_SIZE * iconBaseScale * scale));
            int iconX = Math.round(drawX + (scaledSize - iconScaledSize) * 0.5f);
            int iconY = Math.round(drawY + (scaledSize - iconScaledSize) * 0.5f - 4f * iconBaseScale);

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
            drawItemLabel(g2, labelFm, item.label, x, y + baseSize + 4f, baseSize, glow, labelAlpha);
        }
    }
    
    private void drawItemBackground(Graphics2D g2, float x, float y, float size, float glow) {
        float radius = 22f; // Very rounded corners for item buttons
        RoundRectangle2D.Float shape = new RoundRectangle2D.Float(x, y, size, size, radius, radius);
        
        // Glow effect when hovered - using native aero for smooth falloff
        if (glow > 0.01f) {
            int glowLayers = 6;
            for (int i = 0; i < glowLayers; i++) {
                int baseAlpha = NativeAccess.aeroOuterGlowAlpha(glowLayers - i, glowLayers, 70);
                int alpha = (int)(baseAlpha * glow);
                int glowColor = NativeAccess.aeroLerpColor(0x006496FF, 0x5A6496FF, glow);
                g2.setColor(new Color((glowColor & 0xFFFFFF) | (alpha << 24), true));
                float layer = i;
                float cornerR = radius + layer;
                g2.fill(new RoundRectangle2D.Float(x - layer, y - layer, size + layer * 2f, size + layer * 2f, cornerR, cornerR));
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
    
    private void drawItemLabel(Graphics2D g2, FontMetrics fm, String label, float x, float y, float width, float glow, float visibility) {
        if (visibility <= 0.01f) {
            return;
        }
        int textWidth = fm.stringWidth(label);
        int textX = Math.round(x + (width - textWidth) * 0.5f);
        int textY = Math.round(y + fm.getAscent());

        // Text shadow for readability
        int shadowAlpha = Math.round(TEXT_SHADOW.getAlpha() * visibility);
        g2.setColor(new Color(TEXT_SHADOW.getRed(), TEXT_SHADOW.getGreen(), TEXT_SHADOW.getBlue(), shadowAlpha));
        g2.drawString(label, textX, textY + 1);

        // Main text
        float alpha = (0.7f + 0.3f * glow) * visibility;
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
