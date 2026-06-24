/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.components.scrollbar;

import java.awt.Adjustable;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicScrollBarUI;

import main.ui.theme.Theme;
import main.ui.theme.aero.AeroTheme;

public class AeroScrollBarUI extends BasicScrollBarUI {
    private static final int BAR_THICKNESS = 12;

    @Override
    public void installUI(JComponent c) {
        super.installUI(c);
        if (scrollbar != null) {
            scrollbar.setOpaque(false);
            scrollbar.setBorder(null);
        }
    }

    @Override
    protected void configureScrollBarColors() {
        this.trackColor = new Color(255, 255, 255, 0);
        this.thumbColor = new Color(255, 255, 255, 0);
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
            trench.x += 3;
            trench.width = Math.max(7, trench.width - 6);
        } else {
            trench.y += 3;
            trench.height = Math.max(7, trench.height - 6);
        }

        Color accent = resolveAccent();
        ShapeInfo shape = shapeFor(trench);
        boolean plain = Theme.isPlainWhite();

        g2.setPaint(new LinearGradientPaint(
                shape.startX, shape.startY, shape.endX, shape.endY,
                new float[]{0f, 0.5f, 1f},
                new Color[]{
                        plain
                                ? new Color(255, 255, 255, 52)
                                : AeroTheme.withAlpha(AeroTheme.lift(accent, 0.94f), 48),
                        plain
                                ? new Color(246, 249, 252, 34)
                                : AeroTheme.withAlpha(AeroTheme.blend(AeroTheme.lift(accent, 0.88f), new Color(244, 248, 252), 0.68f), 34),
                        plain
                                ? new Color(224, 231, 238, 42)
                                : AeroTheme.withAlpha(AeroTheme.blend(AeroTheme.sink(accent, 0.08f), new Color(224, 234, 244), 0.76f), 40)
                }
        ));
        g2.fill(shape.rect);

        paintTrackGloss(g2, trench, shape, accent);

        g2.setColor(plain
                ? new Color(144, 156, 168, 34)
                : AeroTheme.withAlpha(AeroTheme.blend(accent, new Color(132, 145, 160), 0.82f), 42));
        g2.setStroke(new BasicStroke(1.0f));
        g2.draw(shape.rect);

        Rectangle inner = inset(trench, 1, 1);
        if (inner.width > 2 && inner.height > 2) {
            ShapeInfo innerShape = shapeFor(inner);
            g2.setColor(new Color(255, 255, 255, plain ? 62 : 72));
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
            body.x += 2;
            body.width = Math.max(8, body.width - 4);
            body.y += 1;
            body.height = Math.max(16, body.height - 2);
        } else {
            body.y += 2;
            body.height = Math.max(8, body.height - 4);
            body.x += 1;
            body.width = Math.max(16, body.width - 2);
        }

        ShapeInfo shape = shapeFor(body);
        Color accent = resolveAccent();
        boolean rollover = isThumbRollover();
        boolean dragging = isDragging;
        boolean active = rollover || dragging;
        float activeMix = dragging ? 1f : (rollover ? 0.86f : 0f);
        boolean plain = Theme.isPlainWhite();

        Color glassTop = new Color(255, 255, 255, plain ? 186 : 174);
        Color glassMid = new Color(248, 250, 252, plain ? 150 : 140);
        Color glassBottom = new Color(224, 231, 238, plain ? 118 : 108);

        Color accentTop = AeroTheme.withAlpha(AeroTheme.lift(accent, 0.72f), dragging ? 218 : 198);
        Color accentMid = AeroTheme.withAlpha(AeroTheme.blend(accent, Color.WHITE, 0.44f), dragging ? 206 : 186);
        Color accentBottom = AeroTheme.withAlpha(AeroTheme.sink(accent, dragging ? 0.06f : 0.02f), dragging ? 196 : 172);

        Color outerTop = AeroTheme.blend(glassTop, accentTop, activeMix);
        Color outerMid = AeroTheme.blend(glassMid, accentMid, activeMix);
        Color outerBottom = AeroTheme.blend(glassBottom, accentBottom, activeMix);

        g2.setPaint(new LinearGradientPaint(
                shape.startX, shape.startY, shape.endX, shape.endY,
                new float[]{0f, 0.48f, 1f},
                new Color[]{outerTop, outerMid, outerBottom}
        ));
        g2.fill(shape.rect);

        if (active) {
            paintAccentGlow(g2, body, shape.rect, accent, activeMix);
        }
        paintThumbHighlights(g2, body, shape, rollover, dragging);

        g2.setColor(plain
                ? (active
                        ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), dragging ? 220 : 188)
                        : new Color(118, 128, 138, 72))
                : (active
                        ? AeroTheme.withAlpha(AeroTheme.sink(accent, dragging ? 0.28f : 0.18f), dragging ? 226 : 196)
                        : new Color(118, 128, 138, 70)));
        g2.setStroke(new BasicStroke(1.0f));
        g2.draw(shape.rect);

        Rectangle inner = inset(body, 1, 1);
        if (inner.width > 2 && inner.height > 2) {
            ShapeInfo innerShape = shapeFor(inner);
            g2.setColor(new Color(255, 255, 255, active ? (dragging ? 178 : 206) : 124));
            g2.draw(innerShape.rect);
        }

        g2.dispose();
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
        return new InvisibleArrowButton(orientation);
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return new InvisibleArrowButton(orientation);
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
                        new Color(255, 255, 255, Theme.isPlainWhite() ? 58 : 68),
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
                                AeroTheme.withAlpha(AeroTheme.lift(accent, 0.86f), 12),
                                AeroTheme.withAlpha(AeroTheme.blend(accent, Color.WHITE, 0.72f), 8),
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
                        new Color(255, 255, 255, dragging ? 154 : (rollover ? 214 : 174)),
                        new Color(255, 255, 255, rollover ? 72 : 42),
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
                            new Color(255, 255, 255, dragging ? 36 : (rollover ? 66 : 46))
                    }
            ));
            g2.fill(lowerShape.rect);
        }
    }

    private void paintAccentGlow(Graphics2D g2, Rectangle body, Shape clipShape, Color accent, float activeMix) {
        Shape oldClip = g2.getClip();
        Composite oldComposite = g2.getComposite();
        g2.clip(clipShape);
        g2.setComposite(AlphaComposite.SrcOver.derive(Math.min(1f, 0.26f + activeMix * 0.34f)));
        Point2D center = scrollbar.getOrientation() == Adjustable.VERTICAL
                ? new Point2D.Float(body.x + body.width * 0.45f, body.y + body.height * 0.24f)
                : new Point2D.Float(body.x + body.width * 0.24f, body.y + body.height * 0.45f);
        float radius = Math.max(18f, Math.max(body.width, body.height) * 0.68f);
        g2.setPaint(new RadialGradientPaint(
                center,
                radius,
                new float[]{0f, 0.42f, 1f},
                new Color[]{
                        AeroTheme.withAlpha(AeroTheme.blend(accent, Color.WHITE, 0.58f), Math.round(96f * activeMix)),
                        AeroTheme.withAlpha(AeroTheme.blend(accent, Color.WHITE, 0.32f), Math.round(54f * activeMix)),
                        AeroTheme.withAlpha(accent, 0)
                }
        ));
        g2.fill(clipShape);
        g2.setComposite(oldComposite);
        g2.setClip(oldClip);
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

    private static final class InvisibleArrowButton extends JButton {
        InvisibleArrowButton(int direction) {
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFocusable(false);
            setPreferredSize(new Dimension(0, 0));
            setMinimumSize(new Dimension(0, 0));
            setMaximumSize(new Dimension(0, 0));
        }

        @Override
        protected void paintComponent(Graphics g) {
            // Arrow buttons are intentionally hidden; scrolling remains available through the thumb, wheel, and trackpad.
        }
    }
}
