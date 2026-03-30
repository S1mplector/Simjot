/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.scrollbar;

import java.awt.Adjustable;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.SwingConstants;
import javax.swing.plaf.basic.BasicScrollBarUI;

import main.ui.theme.Theme;
import main.ui.theme.aero.AeroTheme;

public class AeroScrollBarUI extends BasicScrollBarUI {
    private static final int BAR_THICKNESS = 15;
    private static final int ARROW_SIZE = 17;

    @Override
    protected void configureScrollBarColors() {
        this.trackColor = new Color(244, 248, 253);
        this.thumbColor = new Color(201, 219, 243);
    }

    @Override
    public Dimension getPreferredSize(JComponent c) {
        Dimension base = super.getPreferredSize(c);
        if (scrollbar != null && scrollbar.getOrientation() == Adjustable.VERTICAL) {
            return new Dimension(BAR_THICKNESS, base.height);
        }
        return new Dimension(base.width, BAR_THICKNESS);
    }

    @Override
    protected Dimension getMinimumThumbSize() {
        if (scrollbar != null && scrollbar.getOrientation() == Adjustable.VERTICAL) {
            return new Dimension(BAR_THICKNESS - 3, 34);
        }
        return new Dimension(34, BAR_THICKNESS - 3);
    }

    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Rectangle trench = new Rectangle(trackBounds);
        if (scrollbar.getOrientation() == Adjustable.VERTICAL) {
            trench.x += 2;
            trench.width = Math.max(8, trench.width - 4);
        } else {
            trench.y += 2;
            trench.height = Math.max(8, trench.height - 4);
        }

        Color accent = resolveAccent();
        ShapeInfo shape = shapeFor(trench);

        if (Theme.isPlainWhite()) {
            g2.setPaint(new LinearGradientPaint(
                    shape.startX, shape.startY, shape.endX, shape.endY,
                    new float[]{0f, 0.48f, 1f},
                    new Color[]{
                            new Color(251, 252, 254),
                            new Color(241, 244, 249),
                            new Color(228, 233, 241)
                    }
            ));
        } else {
            g2.setPaint(new LinearGradientPaint(
                    shape.startX, shape.startY, shape.endX, shape.endY,
                    new float[]{0f, 0.44f, 1f},
                    new Color[]{
                            AeroTheme.withAlpha(AeroTheme.lift(accent, 0.90f), 116),
                            AeroTheme.withAlpha(AeroTheme.blend(accent, new Color(233, 240, 249), 0.70f), 134),
                            AeroTheme.withAlpha(AeroTheme.sink(accent, 0.16f), 146)
                    }
            ));
        }
        g2.fill(shape.rect);

        paintTrackGloss(g2, trench, shape, accent);

        g2.setColor(Theme.isPlainWhite()
                ? new Color(196, 203, 214, 170)
                : AeroTheme.withAlpha(AeroTheme.blend(accent, new Color(144, 160, 186), 0.70f), 170));
        g2.setStroke(new BasicStroke(1.0f));
        g2.draw(shape.rect);

        Rectangle inner = inset(trench, 1, 1);
        if (inner.width > 2 && inner.height > 2) {
            ShapeInfo innerShape = shapeFor(inner);
            g2.setColor(new Color(255, 255, 255, Theme.isPlainWhite() ? 120 : 102));
            g2.draw(innerShape.rect);
        }

        g2.dispose();
    }

    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
        if (!c.isEnabled() || thumbBounds.width <= 0 || thumbBounds.height <= 0) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Rectangle body = new Rectangle(thumbBounds);
        if (scrollbar.getOrientation() == Adjustable.VERTICAL) {
            body.x += 1;
            body.width = Math.max(8, body.width - 2);
            body.y += 1;
            body.height = Math.max(16, body.height - 2);
        } else {
            body.y += 1;
            body.height = Math.max(8, body.height - 2);
            body.x += 1;
            body.width = Math.max(16, body.width - 2);
        }

        ShapeInfo shape = shapeFor(body);
        Color accent = resolveAccent();
        boolean rollover = isThumbRollover();
        boolean dragging = isDragging;

        Color outerTop = Theme.isPlainWhite()
                ? new Color(247, 249, 252)
                : AeroTheme.blend(AeroTheme.lift(accent, 0.88f), new Color(244, 248, 252), 0.54f);
        Color outerMid = Theme.isPlainWhite()
                ? new Color(223, 231, 241)
                : AeroTheme.blend(AeroTheme.lift(accent, 0.68f), new Color(215, 228, 244), 0.42f);
        Color outerBottom = Theme.isPlainWhite()
                ? new Color(187, 203, 224)
                : AeroTheme.blend(AeroTheme.sink(accent, dragging ? 0.04f : 0.10f), new Color(182, 202, 230), 0.50f);

        if (rollover) {
            outerTop = AeroTheme.lift(outerTop, 0.08f);
            outerMid = AeroTheme.lift(outerMid, 0.10f);
            outerBottom = AeroTheme.lift(outerBottom, 0.06f);
        }
        if (dragging) {
            outerTop = AeroTheme.sink(outerTop, 0.04f);
            outerMid = AeroTheme.sink(outerMid, 0.05f);
            outerBottom = AeroTheme.sink(outerBottom, 0.10f);
        }

        g2.setPaint(new LinearGradientPaint(
                shape.startX, shape.startY, shape.endX, shape.endY,
                new float[]{0f, 0.48f, 1f},
                new Color[]{outerTop, outerMid, outerBottom}
        ));
        g2.fill(shape.rect);

        paintThumbHighlights(g2, body, shape, rollover, dragging);
        paintGrip(g2, body, rollover, dragging);

        g2.setColor(Theme.isPlainWhite()
                ? new Color(149, 166, 193, dragging ? 232 : 214)
                : AeroTheme.withAlpha(AeroTheme.sink(accent, dragging ? 0.36f : 0.28f), dragging ? 234 : 214));
        g2.setStroke(new BasicStroke(1.0f));
        g2.draw(shape.rect);

        Rectangle inner = inset(body, 1, 1);
        if (inner.width > 2 && inner.height > 2) {
            ShapeInfo innerShape = shapeFor(inner);
            g2.setColor(new Color(255, 255, 255, rollover ? 168 : 132));
            g2.draw(innerShape.rect);
        }

        g2.dispose();
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
        return new ArrowButton(orientation);
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return new ArrowButton(orientation);
    }

    private void paintTrackGloss(Graphics2D g2, Rectangle trench, ShapeInfo shape, Color accent) {
        Rectangle gloss = new Rectangle(trench);
        if (scrollbar.getOrientation() == Adjustable.VERTICAL) {
            gloss.height = Math.max(4, Math.round(trench.height * 0.38f));
        } else {
            gloss.width = Math.max(4, Math.round(trench.width * 0.38f));
        }
        ShapeInfo glossShape = shapeFor(gloss);
        g2.setPaint(new LinearGradientPaint(
                glossShape.startX, glossShape.startY, glossShape.endX, glossShape.endY,
                new float[]{0f, 1f},
                new Color[]{
                        new Color(255, 255, 255, Theme.isPlainWhite() ? 148 : 124),
                        new Color(255, 255, 255, 0)
                }
        ));
        g2.fill(glossShape.rect);

        if (!Theme.isPlainWhite()) {
            Rectangle core = inset(trench, scrollbar.getOrientation() == Adjustable.VERTICAL ? 3 : 1,
                    scrollbar.getOrientation() == Adjustable.VERTICAL ? 1 : 3);
            if (core.width > 3 && core.height > 3) {
                ShapeInfo coreShape = shapeFor(core);
                g2.setPaint(new LinearGradientPaint(
                        coreShape.startX, coreShape.startY, coreShape.endX, coreShape.endY,
                        new float[]{0f, 0.5f, 1f},
                        new Color[]{
                                AeroTheme.withAlpha(AeroTheme.lift(accent, 0.86f), 42),
                                AeroTheme.withAlpha(AeroTheme.blend(accent, Color.WHITE, 0.72f), 26),
                                new Color(255, 255, 255, 0)
                        }
                ));
                g2.fill(coreShape.rect);
            }
        }
    }

    private void paintThumbHighlights(Graphics2D g2, Rectangle body, ShapeInfo shape, boolean rollover, boolean dragging) {
        Rectangle gloss = new Rectangle(body);
        if (scrollbar.getOrientation() == Adjustable.VERTICAL) {
            gloss.height = Math.max(5, Math.round(body.height * 0.42f));
        } else {
            gloss.width = Math.max(5, Math.round(body.width * 0.42f));
        }
        ShapeInfo glossShape = shapeFor(gloss);
        g2.setPaint(new LinearGradientPaint(
                glossShape.startX, glossShape.startY, glossShape.endX, glossShape.endY,
                new float[]{0f, 0.8f, 1f},
                new Color[]{
                        new Color(255, 255, 255, dragging ? 154 : (rollover ? 198 : 174)),
                        new Color(255, 255, 255, 46),
                        new Color(255, 255, 255, 0)
                }
        ));
        g2.fill(glossShape.rect);

        Rectangle lower = inset(body, scrollbar.getOrientation() == Adjustable.VERTICAL ? 2 : 1,
                scrollbar.getOrientation() == Adjustable.VERTICAL ? 1 : 2);
        if (lower.width > 2 && lower.height > 2) {
            ShapeInfo lowerShape = shapeFor(lower);
            g2.setPaint(new LinearGradientPaint(
                    lowerShape.startX, lowerShape.startY, lowerShape.endX, lowerShape.endY,
                    new float[]{0f, 1f},
                    new Color[]{
                            new Color(255, 255, 255, 0),
                            new Color(255, 255, 255, dragging ? 38 : 54)
                    }
            ));
            g2.fill(lowerShape.rect);
        }
    }

    private void paintGrip(Graphics2D g2, Rectangle body, boolean rollover, boolean dragging) {
        int lines = 3;
        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Color dark = new Color(112, 129, 158, dragging ? 168 : 134);
        Color light = new Color(255, 255, 255, rollover ? 164 : 132);

        if (scrollbar.getOrientation() == Adjustable.VERTICAL) {
            if (body.height < 24) return;
            int startY = body.y + body.height / 2 - 4;
            int x1 = body.x + 4;
            int x2 = body.x + body.width - 5;
            for (int i = 0; i < lines; i++) {
                int y = startY + i * 4;
                g2.setColor(light);
                g2.drawLine(x1, y, x2, y);
                g2.setColor(dark);
                g2.drawLine(x1, y + 1, x2, y + 1);
            }
        } else {
            if (body.width < 24) return;
            int startX = body.x + body.width / 2 - 4;
            int y1 = body.y + 4;
            int y2 = body.y + body.height - 5;
            for (int i = 0; i < lines; i++) {
                int x = startX + i * 4;
                g2.setColor(light);
                g2.drawLine(x, y1, x, y2);
                g2.setColor(dark);
                g2.drawLine(x + 1, y1, x + 1, y2);
            }
        }
    }

    private ShapeInfo shapeFor(Rectangle bounds) {
        int arc = scrollbar != null && scrollbar.getOrientation() == Adjustable.VERTICAL
                ? Math.min(bounds.width, 10)
                : Math.min(bounds.height, 10);
        return new ShapeInfo(
                new RoundRectangle2D.Float(bounds.x, bounds.y, bounds.width, bounds.height, arc, arc),
                scrollbar != null && scrollbar.getOrientation() == Adjustable.VERTICAL
                        ? bounds.x
                        : bounds.x,
                scrollbar != null && scrollbar.getOrientation() == Adjustable.VERTICAL
                        ? bounds.y
                        : bounds.y,
                scrollbar != null && scrollbar.getOrientation() == Adjustable.VERTICAL
                        ? bounds.x
                        : bounds.x + bounds.width,
                scrollbar != null && scrollbar.getOrientation() == Adjustable.VERTICAL
                        ? bounds.y + bounds.height
                        : bounds.y
        );
    }

    private Rectangle inset(Rectangle source, int dx, int dy) {
        return new Rectangle(
                source.x + dx,
                source.y + dy,
                Math.max(0, source.width - (dx * 2)),
                Math.max(0, source.height - (dy * 2))
        );
    }

    private Color resolveAccent() {
        if (Theme.isPlainWhite()) {
            return new Color(175, 198, 229);
        }
        return AeroTheme.resolveChromeAccent();
    }

    private record ShapeInfo(RoundRectangle2D.Float rect, float startX, float startY, float endX, float endY) {
    }

    private static final class ArrowButton extends JButton {
        private final int direction;

        ArrowButton(int direction) {
            this.direction = direction;
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setPreferredSize(new Dimension(ARROW_SIZE, ARROW_SIZE));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            boolean hover = getModel().isRollover();
            boolean pressed = getModel().isPressed();

            Color accent = Theme.isPlainWhite()
                    ? new Color(170, 191, 218)
                    : AeroTheme.resolveChromeAccent();
            Color top = Theme.isPlainWhite()
                    ? new Color(251, 252, 254)
                    : AeroTheme.blend(AeroTheme.lift(accent, 0.90f), new Color(250, 252, 255), 0.72f);
            Color bottom = Theme.isPlainWhite()
                    ? new Color(220, 228, 239)
                    : AeroTheme.blend(AeroTheme.sink(accent, 0.14f), new Color(211, 223, 241), 0.62f);

            if (hover) {
                top = AeroTheme.lift(top, 0.06f);
                bottom = AeroTheme.lift(bottom, 0.08f);
            }
            if (pressed) {
                top = AeroTheme.sink(top, 0.08f);
                bottom = AeroTheme.sink(bottom, 0.14f);
            }

            g2.setColor(new Color(0, 0, 0, hover ? 26 : 18));
            g2.fillRoundRect(1, 2, w - 2, h - 2, 7, 7);

            g2.setPaint(new LinearGradientPaint(
                    0f, 0f, 0f, h,
                    new float[]{0f, 0.45f, 1f},
                    new Color[]{top, AeroTheme.blend(top, bottom, 0.34f), bottom}
            ));
            g2.fillRoundRect(0, 0, w - 2, h - 2, 7, 7);

            g2.setPaint(new GradientPaint(0, 1, new Color(255, 255, 255, 185), 0, Math.max(2, h / 2), new Color(255, 255, 255, 0)));
            g2.fillRoundRect(1, 1, w - 4, Math.max(4, h / 2 - 1), 6, 6);

            g2.setColor(Theme.isPlainWhite()
                    ? new Color(167, 179, 194)
                    : AeroTheme.withAlpha(AeroTheme.sink(accent, 0.26f), 188));
            g2.drawRoundRect(0, 0, w - 3, h - 3, 7, 7);
            g2.setColor(new Color(255, 255, 255, hover ? 136 : 102));
            g2.drawRoundRect(1, 1, w - 5, h - 5, 6, 6);

            Path2D arrow = buildArrow(w, h, pressed);
            g2.setColor(new Color(255, 255, 255, 122));
            g2.translate(0, 1);
            g2.draw(arrow);
            g2.translate(0, -1);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(Theme.isPlainWhite()
                    ? new Color(95, 111, 139)
                    : AeroTheme.sink(accent, 0.42f));
            g2.fill(arrow);

            g2.dispose();
        }

        private Path2D buildArrow(int w, int h, boolean pressed) {
            int shift = pressed ? 1 : 0;
            float cx = (w - 2) / 2f + shift;
            float cy = (h - 2) / 2f + shift;
            Path2D path = new Path2D.Float();
            switch (direction) {
                case SwingConstants.NORTH -> {
                    path.moveTo(cx - 4, cy + 2);
                    path.lineTo(cx, cy - 2);
                    path.lineTo(cx + 4, cy + 2);
                }
                case SwingConstants.SOUTH -> {
                    path.moveTo(cx - 4, cy - 2);
                    path.lineTo(cx, cy + 2);
                    path.lineTo(cx + 4, cy - 2);
                }
                case SwingConstants.WEST -> {
                    path.moveTo(cx + 2, cy - 4);
                    path.lineTo(cx - 2, cy);
                    path.lineTo(cx + 2, cy + 4);
                }
                default -> {
                    path.moveTo(cx - 2, cy - 4);
                    path.lineTo(cx + 2, cy);
                    path.lineTo(cx - 2, cy + 4);
                }
            }
            path.closePath();
            return path;
        }
    }
}
