/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.components.editor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;

import javax.swing.JViewport;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.JTextComponent;

/**
 * Viewport overlay that renders faint ruled lines, a margin guide,
 * and a soft vignette behind the editor content.
 */
public class PaperFeelViewport extends JViewport {
    private final JTextComponent editor;
    private boolean paperFeelEnabled = true;
    private boolean vignetteEnabled = true;

    private final Color lineColor = new Color(60, 60, 60, 28);
    private final Color marginColor = new Color(200, 120, 120, 70);
    private final Color vignetteEdge = new Color(0, 0, 0, 22);

    private java.awt.image.BufferedImage topVignette, bottomVignette, leftVignette, rightVignette;
    private int cachedW = -1;
    private int cachedH = -1;

    public PaperFeelViewport(JTextComponent editor) {
        this.editor = editor;
        setOpaque(false);
    }

    public void setPaperFeelEnabled(boolean enabled) {
        this.paperFeelEnabled = enabled;
        repaint();
    }

    public void setVignetteEnabled(boolean enabled) {
        this.vignetteEnabled = enabled;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (isOpaque()) {
            super.paintComponent(g);
        }
        if (!paperFeelEnabled || editor == null) return;

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Point viewPos = getViewPosition();
        Insets margin = editor.getMargin();
        if (margin == null) margin = new Insets(0, 0, 0, 0);

        if (editor.getFont() == null) {
            g2.dispose();
            return;
        }

        int lineHeight = resolveLineHeight(editor);
        if (lineHeight > 0) {
            int baseY = margin.top + editor.getFontMetrics(editor.getFont()).getAscent() + 2 - viewPos.y;
            int start = baseY;
            if (start < 0) {
                int n = (int) Math.ceil((-start) / (double) lineHeight);
                start += n * lineHeight;
            }
            g2.setColor(lineColor);
            g2.setStroke(new BasicStroke(1f));
            for (int y = start; y < h; y += lineHeight) {
                g2.drawLine(0, y, w, y);
            }
        }

        int marginX = margin.left - 12;
        if (marginX >= 16 && marginX < w) {
            g2.setColor(marginColor);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawLine(marginX, 0, marginX, h);
        }

        if (vignetteEnabled) {
            paintVignette(g2, w, h);
        }
        g2.dispose();
    }

    private int resolveLineHeight(JTextComponent editor) {
        int baseHeight = editor.getFontMetrics(editor.getFont()).getHeight();
        float spacing = 0f;
        try {
            if (editor.getDocument() instanceof StyledDocument sd) {
                Element para = sd.getParagraphElement(0);
                if (para != null) {
                    spacing = Math.max(0f, StyleConstants.getLineSpacing(para.getAttributes()));
                }
            }
        } catch (Throwable ignored) {}
        int lineHeight = Math.round(baseHeight * (1f + spacing));
        return Math.max(lineHeight, baseHeight + 2);
    }

    private void paintVignette(Graphics2D g2, int w, int h) {
        int fade = Math.max(22, Math.min(80, Math.min(w, h) / 6));

        if (w != cachedW || h != cachedH || topVignette == null) {
            java.awt.GraphicsConfiguration gc = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration();

            topVignette = gc.createCompatibleImage(w, fade, java.awt.Transparency.TRANSLUCENT);
            bottomVignette = gc.createCompatibleImage(w, fade, java.awt.Transparency.TRANSLUCENT);
            leftVignette = gc.createCompatibleImage(fade, h, java.awt.Transparency.TRANSLUCENT);
            rightVignette = gc.createCompatibleImage(fade, h, java.awt.Transparency.TRANSLUCENT);

            Graphics2D tg = topVignette.createGraphics();
            tg.setPaint(new GradientPaint(0, 0, vignetteEdge, 0, fade, new Color(0, 0, 0, 0)));
            tg.fillRect(0, 0, w, fade);
            tg.dispose();

            Graphics2D bg = bottomVignette.createGraphics();
            bg.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 0), 0, fade, vignetteEdge));
            bg.fillRect(0, 0, w, fade);
            bg.dispose();

            Graphics2D lg = leftVignette.createGraphics();
            lg.setPaint(new GradientPaint(0, 0, vignetteEdge, fade, 0, new Color(0, 0, 0, 0)));
            lg.fillRect(0, 0, fade, h);
            lg.dispose();

            Graphics2D rg = rightVignette.createGraphics();
            rg.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 0), fade, 0, vignetteEdge));
            rg.fillRect(0, 0, fade, h);
            rg.dispose();

            cachedW = w;
            cachedH = h;
        }

        g2.drawImage(topVignette, 0, 0, null);
        g2.drawImage(bottomVignette, 0, h - fade, null);
        g2.drawImage(leftVignette, 0, 0, null);
        g2.drawImage(rightVignette, w - fade, 0, null);
    }
}
