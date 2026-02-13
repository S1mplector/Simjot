/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoglu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.dock;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.Timer;

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
    private float[] scatterOffsetX;
    private float[] scatterOffsetY;
    private float[] scatterRotationDeg;
    private float[] scatterScale;
    private float scatterCloudRadiusX = 48f;
    private float scatterCloudRadiusY = 24f;
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
    private static final float SCATTER_COLLAPSED_SCALE = 0.85f;
    private static final float SCATTER_BASE_RADIUS_X = 40f;
    private static final float SCATTER_BASE_RADIUS_Y = 21f;
    private static final float SCATTER_EXTRA_RADIUS_PER_ITEM_X = 6f;
    private static final float SCATTER_EXTRA_RADIUS_PER_ITEM_Y = 3f;
    private static final float SCATTER_MIN_ICON_GAP = 6f;
    private static final float SCATTER_MAX_ROTATION_DEG = 15f;
    private static final float MIN_COLLAPSED_ITEM_SIZE = 46f;
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
        scatterOffsetX = new float[items.size()];
        scatterOffsetY = new float[items.size()];
        scatterRotationDeg = new float[items.size()];
        scatterScale = new float[items.size()];
        for (int i = 0; i < items.size(); i++) {
            itemScales[i] = 1f;
            itemGlows[i] = 0f;
        }
        rebuildScatterLayout();
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
        int[] drawOrder = buildDrawOrder();
        for (int i = drawOrder.length - 1; i >= 0; i--) {
            int idx = drawOrder[i];
            float x = itemX[idx];
            float y = itemY[idx];
            float size = itemSize[idx];
            if (p.x >= x && p.x < x + size && p.y >= y && p.y < y + size) {
                return idx;
            }
        }
        return -1;
    }

    private int[] buildDrawOrder() {
        int n = items.size();
        int[] order = new int[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        if (n <= 1 || itemY == null || itemSize == null) {
            return order;
        }

        // Front-most items should render last. Sort by baseline Y, then by X.
        for (int i = 1; i < n; i++) {
            int key = order[i];
            float keyY = itemY[key] + itemSize[key] * 0.45f;
            float keyX = itemX != null ? itemX[key] : key;
            int j = i - 1;
            while (j >= 0) {
                int idx = order[j];
                float idxY = itemY[idx] + itemSize[idx] * 0.45f;
                float idxX = itemX != null ? itemX[idx] : idx;
                if (idxY < keyY - 0.05f || (Math.abs(idxY - keyY) <= 0.05f && idxX <= keyX)) {
                    break;
                }
                order[j + 1] = idx;
                j--;
            }
            order[j + 1] = key;
        }

        // Keep hovered icon on top so hover glow never gets buried.
        if (hoveredIndex >= 0 && hoveredIndex < n) {
            int found = -1;
            for (int i = 0; i < n; i++) {
                if (order[i] == hoveredIndex) {
                    found = i;
                    break;
                }
            }
            if (found >= 0 && found < n - 1) {
                int v = order[found];
                System.arraycopy(order, found + 1, order, found, n - found - 1);
                order[n - 1] = v;
            }
        }
        return order;
    }

    private void rebuildScatterLayout() {
        int n = items.size();
        if (n <= 0 || scatterOffsetX == null || scatterOffsetY == null
                || scatterRotationDeg == null || scatterScale == null) {
            return;
        }

        java.util.Random random = new java.util.Random(0x51F15D0C7A11L ^ (n * 0x9E3779B97F4A7C15L));
        for (int i = 0; i < n; i++) {
            scatterRotationDeg[i] = (random.nextFloat() * 2f - 1f) * (SCATTER_MAX_ROTATION_DEG * 0.55f);
            scatterScale[i] = 0.96f + random.nextFloat() * 0.08f;
        }

        float[] bestX = new float[n];
        float[] bestY = new float[n];
        float[] trialX = new float[n];
        float[] trialY = new float[n];
        float bestOverlap = Float.MAX_VALUE;
        float phaseSeed = random.nextFloat() * (float) (Math.PI * 2.0);
        float baseRadiusX = SCATTER_BASE_RADIUS_X + Math.max(0, n - 4) * SCATTER_EXTRA_RADIUS_PER_ITEM_X;
        float baseRadiusY = SCATTER_BASE_RADIUS_Y + Math.max(0, n - 4) * SCATTER_EXTRA_RADIUS_PER_ITEM_Y;
        float bestRadiusX = baseRadiusX;
        float bestRadiusY = baseRadiusY;

        for (int attempt = 0; attempt < 7; attempt++) {
            float radiusX = baseRadiusX + attempt * 7f;
            float radiusY = baseRadiusY + attempt * 3.6f;

            for (int i = 0; i < n; i++) {
                float angle = phaseSeed + i * 2.3999632f;
                float radial = 0.28f + 0.64f * (float) Math.sqrt((i + 1f) / (n + 0.75f));
                float jitterX = (random.nextFloat() * 2f - 1f) * 3.6f;
                float jitterY = (random.nextFloat() * 2f - 1f) * 2.6f;
                trialX[i] = (float) Math.cos(angle) * radiusX * radial + jitterX;
                trialY[i] = (float) Math.sin(angle) * radiusY * radial + jitterY;
            }

            relaxScatterPoints(trialX, trialY, radiusX, radiusY);
            float overlap = computeMaxIconOverlap(trialX, trialY);
            if (overlap < bestOverlap) {
                bestOverlap = overlap;
                bestRadiusX = radiusX;
                bestRadiusY = radiusY;
                System.arraycopy(trialX, 0, bestX, 0, n);
                System.arraycopy(trialY, 0, bestY, 0, n);
            }
            if (overlap <= 0.40f) {
                break;
            }
        }

        // Hard fallback: guarantee non-overlapping positions.
        if (bestOverlap > 0.50f) {
            float maxIcon = 0f;
            for (int i = 0; i < n; i++) {
                maxIcon = Math.max(maxIcon, estimateCollapsedIconSize(i));
            }
            float spacing = maxIcon + SCATTER_MIN_ICON_GAP;
            float startX = -0.5f * spacing * (n - 1);
            float altY = Math.min(8f, spacing * 0.16f);
            for (int i = 0; i < n; i++) {
                bestX[i] = startX + i * spacing;
                bestY[i] = ((i & 1) == 0 ? -altY : altY);
            }
            bestRadiusX = Math.max(baseRadiusX, Math.abs(startX) + spacing * 0.55f + 6f);
            bestRadiusY = Math.max(baseRadiusY, altY + maxIcon * 0.45f + 4f);
        }

        System.arraycopy(bestX, 0, scatterOffsetX, 0, n);
        System.arraycopy(bestY, 0, scatterOffsetY, 0, n);
        scatterCloudRadiusX = bestRadiusX + 4f;
        scatterCloudRadiusY = bestRadiusY + 3f;
    }

    private void relaxScatterPoints(float[] x, float[] y, float radiusX, float radiusY) {
        int n = x.length;
        for (int iter = 0; iter < 72; iter++) {
            float maxOverlap = 0f;
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    float dx = x[j] - x[i];
                    float dy = y[j] - y[i];
                    float distSq = dx * dx + dy * dy;
                    float minSep = computeMinIconSeparation(i, j);
                    if (distSq < minSep * minSep) {
                        float dist = (float) Math.sqrt(Math.max(0.0001f, distSq));
                        float push = (minSep - dist) * 0.5f;
                        float nx = dx / dist;
                        float ny = dy / dist;
                        x[i] -= nx * push;
                        y[i] -= ny * push;
                        x[j] += nx * push;
                        y[j] += ny * push;
                        maxOverlap = Math.max(maxOverlap, minSep - dist);
                    }
                }
            }

            for (int i = 0; i < n; i++) {
                clampPointToEllipse(x, y, i, radiusX, radiusY);
            }
            if (maxOverlap <= 0.25f) {
                break;
            }
        }
    }

    private float computeMaxIconOverlap(float[] x, float[] y) {
        int n = x.length;
        float maxOverlap = 0f;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                float dx = x[j] - x[i];
                float dy = y[j] - y[i];
                float dist = (float) Math.sqrt(Math.max(0.0001f, dx * dx + dy * dy));
                float minSep = computeMinIconSeparation(i, j);
                maxOverlap = Math.max(maxOverlap, minSep - dist);
            }
        }
        return maxOverlap;
    }

    private void clampPointToEllipse(float[] x, float[] y, int index, float radiusX, float radiusY) {
        if (radiusX <= 0f || radiusY <= 0f) {
            return;
        }
        float nx = x[index] / radiusX;
        float ny = y[index] / radiusY;
        float len = (float) Math.sqrt(nx * nx + ny * ny);
        if (len > 1f) {
            float inv = 1f / len;
            x[index] *= inv;
            y[index] *= inv;
        }
    }

    private float computeMinIconSeparation(int i, int j) {
        float iconI = estimateCollapsedIconSize(i);
        float iconJ = estimateCollapsedIconSize(j);
        return (iconI + iconJ) * 0.5f + SCATTER_MIN_ICON_GAP;
    }

    private float estimateCollapsedIconSize(int index) {
        float collapsedItemBase = ITEM_SIZE * SCATTER_COLLAPSED_SCALE;
        float scale = (scatterScale != null && index >= 0 && index < scatterScale.length) ? scatterScale[index] : 1f;
        float collapsedSize = Math.max(MIN_COLLAPSED_ITEM_SIZE, collapsedItemBase * scale);
        float iconBaseScale = collapsedSize / ITEM_SIZE;
        float collapsedIconScale = 0.98f + (scale - 0.98f) * 0.35f;
        return Math.max(18f, ICON_SIZE * iconBaseScale * collapsedIconScale);
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
        if (scatterOffsetX == null || scatterOffsetX.length != items.size()) {
            rebuildScatterLayout();
        }
        float t = easeInOut(progress);
        float itemExpandedSize = ITEM_SIZE;
        float itemCollapsedBaseSize = ITEM_SIZE * SCATTER_COLLAPSED_SCALE;
        float expandedWidth = DOCK_PADDING_H * 2f + items.size() * itemExpandedSize + Math.max(0, items.size() - 1) * ITEM_SPACING;
        float cloudWidth = itemCollapsedBaseSize + scatterCloudRadiusX * 2f + 14f;
        float collapsedWidth = DOCK_PADDING_H * 2f + cloudWidth;
        dockWidth = lerp(collapsedWidth, expandedWidth, t);
        dockHeight = DOCK_PADDING_V * 2f + ITEM_SIZE + 8f;
        dockX = (componentWidth - dockWidth) * 0.5f;

        float expandedStartX = dockX + DOCK_PADDING_H;
        float expandedY = DOCK_PADDING_V;
        float collapsedCenterX = dockX + (dockWidth - itemCollapsedBaseSize) * 0.5f;
        float collapsedY = DOCK_PADDING_V + 6f;

        for (int i = 0; i < items.size(); i++) {
            float collapsedScale = (scatterScale != null && i < scatterScale.length) ? scatterScale[i] : 1f;
            float collapsedSize = Math.max(MIN_COLLAPSED_ITEM_SIZE, itemCollapsedBaseSize * collapsedScale);
            float centeredOffsetX = (itemCollapsedBaseSize - collapsedSize) * 0.5f;
            float centeredOffsetY = (itemCollapsedBaseSize - collapsedSize) * 0.5f;
            float sx = (scatterOffsetX != null && i < scatterOffsetX.length) ? scatterOffsetX[i] : 0f;
            float sy = (scatterOffsetY != null && i < scatterOffsetY.length) ? scatterOffsetY[i] : 0f;
            float collapsedX = collapsedCenterX + sx + centeredOffsetX;
            float collapsedItemY = collapsedY + sy + centeredOffsetY;
            float expandedX = expandedStartX + i * (ITEM_SIZE + ITEM_SPACING);
            itemX[i] = lerp(collapsedX, expandedX, t);
            itemY[i] = lerp(collapsedItemY, expandedY, t);
            itemSize[i] = lerp(collapsedSize, itemExpandedSize, t);
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

        float buttonReveal = easeInOut((expandProgress - 0.12f) / 0.62f);
        float iconScatterReveal = 1f - buttonReveal;
        float labelAlpha = Math.max(0f, Math.min(1f, (expandProgress - 0.32f) / 0.68f));

        int[] drawOrder = buildDrawOrder();
        for (int drawIdx = 0; drawIdx < drawOrder.length; drawIdx++) {
            int i = drawOrder[drawIdx];
            DockItem item = items.get(i);
            float scale = itemScales[i];
            float glow = itemGlows[i];

            float x = itemX[i];
            float y = itemY[i];
            float baseSize = itemSize[i];

            float alpha = Math.max(0.5f, 0.76f + buttonReveal * 0.24f);
            Composite oldComposite = g2.getComposite();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, alpha)));

            // Calculate scaled position (scale from center-bottom for bounce effect)
            float interactionScale = 1f + (scale - 1f) * buttonReveal;
            float scaledSize = baseSize * interactionScale;
            float offsetX = (baseSize - scaledSize) * 0.5f;
            float offsetY = baseSize - scaledSize; // Anchor to bottom

            float drawX = x + offsetX;
            float drawY = y + offsetY;

            if (buttonReveal > 0.02f) {
                // Draw item background only after expansion starts.
                float backgroundRadius = Math.max(16f, scaledSize * 0.30f);
                drawItemBackground(g2, drawX, drawY, scaledSize, glow * buttonReveal, backgroundRadius, buttonReveal);
            }

            float iconBaseScale = baseSize / ITEM_SIZE;
            float scatterScaleAdjust = (scatterScale != null && i < scatterScale.length) ? scatterScale[i] : 1f;
            float collapsedIconScale = 0.98f + (scatterScaleAdjust - 0.98f) * 0.35f;
            float iconPhaseScale = lerp(collapsedIconScale, 1f, buttonReveal);
            int iconScaledSize = Math.max(18, Math.round(ICON_SIZE * iconBaseScale * iconPhaseScale));
            int iconX = Math.round(drawX + (scaledSize - iconScaledSize) * 0.5f);
            int iconY = Math.round(drawY + (scaledSize - iconScaledSize) * 0.5f - 4f * iconBaseScale * buttonReveal);

            String resPath = item.resourcePath;
            if (resPath != null) {
                AffineTransform oldTransform = g2.getTransform();
                float iconCenterX = iconX + iconScaledSize / 2f;
                float iconCenterY = iconY + iconScaledSize / 2f;
                float scatterTiltDeg = (scatterRotationDeg != null && i < scatterRotationDeg.length) ? scatterRotationDeg[i] : 0f;
                float scatterTilt = (float) Math.toRadians(scatterTiltDeg) * iconScatterReveal;
                float hoverTilt = -0.08f * glow * buttonReveal;
                float totalTilt = scatterTilt + hoverTilt;
                float hoverScale = 1f + (0.08f * glow * buttonReveal);
                g2.translate(iconCenterX, iconCenterY);
                g2.rotate(totalTilt);
                g2.scale(hoverScale, hoverScale);
                g2.translate(-iconCenterX, -iconCenterY);
                ImageIconRenderer.draw(g2, resPath, iconX, iconY, iconScaledSize, this, true);
                g2.setTransform(oldTransform);
            }

            // Draw label
            if (labelAlpha > 0.01f) {
                drawItemLabel(g2, labelFm, item.label, x, y + baseSize + 4f, baseSize, glow, labelAlpha);
            }
            g2.setComposite(oldComposite);
        }
    }

    private void drawItemBackground(Graphics2D g2, float x, float y, float size, float glow, float radius, float reveal) {
        RoundRectangle2D.Float shape = new RoundRectangle2D.Float(x, y, size, size, radius, radius);
        float collapsed = 1f - Math.max(0f, Math.min(1f, expandProgress));
        float revealClamped = Math.max(0f, Math.min(1f, reveal));
        if (revealClamped <= 0.001f) {
            return;
        }
        
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
        
        // Item background; increase opacity while collapsed to block icon bleed-through.
        int collapsedBaseAlpha = Math.round((18 + (26 * collapsed)) * revealClamped);
        int baseBg = (Math.max(0, Math.min(255, collapsedBaseAlpha)) << 24) | 0x00FFFFFF;
        int hoverBg = ((Math.round(53 * revealClamped) & 0xFF) << 24) | 0x00FFFFFF;
        int bgColor = NativeAccess.aeroLerpColor(baseBg, hoverBg, glow);
        g2.setColor(new Color(bgColor, true));
        g2.fill(shape);
        
        // Top gloss - subtle
        int glossAlpha = Math.round((36 + 34 * glow) * revealClamped);
        GradientPaint itemGloss = new GradientPaint(
            x, y, new Color(255, 255, 255, glossAlpha),
            x, y + size * 0.4f, new Color(255, 255, 255, 0)
        );
        g2.setPaint(itemGloss);
        g2.fill(new RoundRectangle2D.Float(x + 1, y + 1, size - 2, size * 0.35f, radius - 1, radius - 1));
        
        // Border - subtle
        int borderAlpha = Math.round((28 + 42 * glow) * revealClamped);
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
