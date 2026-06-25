/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.components.buttons;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.Timer;

import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.icons.IconTransforms;
import main.ui.components.icons.ImageIconRenderer;
import main.ui.theme.aero.AeroTheme;

/**
 * Compact icon-first button styled like the main menu tiles:
 * shows only the icon at rest, then reveals a soft overlay + caption on hover/press.
 */
public class ToolbarMenuIconButton extends ToolbarIconButton {
    private final String iconResourcePath;
    private final String caption;
    private final boolean vectorChevronIcon;
    private boolean hovering = false;
    private double iconRotationRadians = 0.0;
    private Timer rotationTimer;
    private double rotationFromRadians = 0.0;
    private double rotationToRadians = 0.0;
    private long rotationStartMs = 0L;
    private int rotationDurationMs = 180;

    public ToolbarMenuIconButton(String caption, String iconId) {
        super(iconId);
        this.caption = caption == null ? "" : caption;
        String normalizedIconId = iconId == null ? "" : iconId.toLowerCase();
        this.iconResourcePath = ImageIconRenderer.mapIdToResource(normalizedIconId);
        this.vectorChevronIcon = "forward".equals(normalizedIconId);
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setForeground(AeroTheme.TEXT_PRIMARY);
        setFont(new Font("SansSerif", Font.BOLD, 13));
        Dimension d = new Dimension(46, 46);
        setPreferredSize(d);
        setMinimumSize(d);
        setMaximumSize(new Dimension(70, 46));

        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hovering = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { hovering = false; repaint(); }
        });
    }

    /** Rotate the icon around its center. Use Math.PI to flip 180 degrees. */
    public void setIconRotationRadians(double radians) {
        if (Double.isNaN(radians) || Double.isInfinite(radians)) return;
        if (rotationTimer != null && rotationTimer.isRunning()) rotationTimer.stop();
        if (this.iconRotationRadians != radians) {
            this.iconRotationRadians = radians;
            repaint();
        }
    }

    /** Smoothly rotate the icon around its center. */
    public void animateIconRotationRadians(double radians) {
        animateIconRotationRadians(radians, 180);
    }

    /** Smoothly rotate the icon around its center using the given duration in milliseconds. */
    public void animateIconRotationRadians(double radians, int durationMs) {
        if (Double.isNaN(radians) || Double.isInfinite(radians)) return;
        int safeDuration = Math.max(60, durationMs);
        if (rotationTimer != null && rotationTimer.isRunning()
                && Math.abs(rotationToRadians - radians) < 0.0001) {
            return;
        }
        if (Math.abs(this.iconRotationRadians - radians) < 0.0001 && (rotationTimer == null || !rotationTimer.isRunning())) {
            return;
        }
        if (rotationTimer == null) {
            rotationTimer = new Timer(16, e -> stepRotationAnimation());
            rotationTimer.setRepeats(true);
        } else if (rotationTimer.isRunning()) {
            rotationTimer.stop();
        }
        rotationFromRadians = iconRotationRadians;
        rotationToRadians = radians;
        rotationDurationMs = safeDuration;
        rotationStartMs = System.currentTimeMillis();
        rotationTimer.start();
    }

    private void stepRotationAnimation() {
        long elapsed = System.currentTimeMillis() - rotationStartMs;
        double p = Math.min(1.0, elapsed / (double) rotationDurationMs);
        // Ease in/out cubic for a clean, non-snappy flip.
        double e = (p < 0.5)
                ? (4.0 * p * p * p)
                : (1.0 - Math.pow(-2.0 * p + 2.0, 3.0) / 2.0);
        iconRotationRadians = rotationFromRadians + (rotationToRadians - rotationFromRadians) * e;
        repaint();
        if (p >= 1.0) {
            iconRotationRadians = rotationToRadians;
            rotationTimer.stop();
            repaint();
        }
    }

    @Override
    protected void paintComponent(java.awt.Graphics g) {
        java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();

        FrostedGlassPanel.paintAncestorBackground(this, g2);

        boolean pressed = getModel().isArmed() && getModel().isPressed();
        if (hovering || pressed) {
            Shape plate = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, 10, 10);
            Color top = pressed ? new Color(235, 238, 243, 220) : new Color(245, 248, 252, 210);
            Color bot = pressed ? new Color(215, 220, 230, 220) : new Color(225, 230, 238, 210);
            g2.setPaint(new GradientPaint(0, 0, top, 0, h, bot));
            g2.fill(plate);
            g2.setColor(new Color(170, 180, 195, 200));
            g2.draw(plate);
        }

        // Icon
        int iconSize = Math.min(w, h) - 12;
        int ix = (w - iconSize) / 2;
        int iy = (h - iconSize) / 2;
        String res = iconResourcePath;
        if (vectorChevronIcon) {
            paintVectorChevronIcon(g2, ix, iy, iconSize, pressed);
        } else if (res != null) {
            if (Math.abs(iconRotationRadians) > 0.0001) {
                IconTransforms.drawRotated(g2, res, ix, iy, iconSize, this, true, iconRotationRadians);
            } else {
                ImageIconRenderer.draw(g2, res, ix, iy, iconSize, this, true);
            }
        }

        // Caption appears on hover/press as inline overlay
        if (hovering || pressed) {
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int textW = fm.stringWidth(caption);
            int textX = (w - textW) / 2;
            int textY = h - 6;
            g2.setColor(new Color(0, 0, 0, 35));
            g2.drawString(caption, textX + 1, textY + 1);
            g2.setColor(new Color(30, 30, 30, 190));
            g2.drawString(caption, textX, textY);
        }

        g2.dispose();
    }

    private void paintVectorChevronIcon(java.awt.Graphics2D g2, int x, int y, int size, boolean pressed) {
        java.awt.Graphics2D gi = (java.awt.Graphics2D) g2.create();
        gi.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        double cx = x + size / 2.0;
        double cy = y + size / 2.0;
        if (Math.abs(iconRotationRadians) > 0.0001) {
            gi.rotate(iconRotationRadians, cx, cy);
        }

        float stroke = Math.max(2.4f, size * 0.14f);
        int left = x + Math.round(size * 0.32f);
        int right = x + Math.round(size * 0.68f);
        int top = y + Math.round(size * 0.28f);
        int mid = y + Math.round(size * 0.50f);
        int bot = y + Math.round(size * 0.72f);

        Path2D.Float chevron = new Path2D.Float();
        chevron.moveTo(left, top);
        chevron.lineTo(right, mid);
        chevron.lineTo(left, bot);

        gi.translate(0.8, 0.8);
        gi.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        gi.setColor(new Color(255, 255, 255, 105));
        gi.draw(chevron);
        gi.translate(-0.8, -0.8);

        Color iconColor;
        if (!isEnabled()) {
            iconColor = new Color(140, 148, 160, 165);
        } else if (pressed) {
            iconColor = new Color(44, 55, 72, 238);
        } else if (hovering) {
            iconColor = new Color(52, 64, 86, 228);
        } else {
            iconColor = new Color(62, 72, 92, 214);
        }
        gi.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        gi.setColor(iconColor);
        gi.draw(chevron);
        gi.dispose();
    }

    @Override
    public void removeNotify() {
        if (rotationTimer != null) rotationTimer.stop();
        super.removeNotify();
    }
}
