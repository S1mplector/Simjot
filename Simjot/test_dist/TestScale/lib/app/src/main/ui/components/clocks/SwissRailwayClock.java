/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.components.clocks;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.time.LocalTime;
import java.time.ZoneId;

import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * Swiss railway station clock style - clean, bold hour markers,
 * distinctive red second hand with circular tip.
 */
public class SwissRailwayClock extends JPanel {
    private static final long serialVersionUID = 1L;

    @SuppressWarnings("unused")
    public SwissRailwayClock() {
        setPreferredSize(new Dimension(100, 100));
        setOpaque(false);
        Timer timer = new Timer(1000, e -> repaint());
        timer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int size = Math.min(getWidth(), getHeight());
        int cx = getWidth() / 2;
        int cy = getHeight() / 2;
        int r = Math.max(20, size / 2 - 6);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

        // Clean white dial
        g2.setColor(new Color(252, 252, 252));
        g2.fillOval(cx - r, cy - r, r * 2, r * 2);

        // Thin black border
        g2.setColor(new Color(40, 40, 40));
        g2.setStroke(new BasicStroke(Math.max(2f, r * 0.025f)));
        g2.drawOval(cx - r, cy - r, r * 2, r * 2);

        // Hour markers - bold rectangles
        int markerOuterDist = (int)(r * 0.88);
        int hourMarkerLen = (int)(r * 0.15);
        int hourMarkerWidth = Math.max(4, (int)(r * 0.06));
        int minMarkerLen = (int)(r * 0.08);
        int minMarkerWidth = Math.max(2, (int)(r * 0.025));

        g2.setColor(new Color(30, 30, 30));
        for (int i = 0; i < 60; i++) {
            double angle = Math.toRadians(i * 6 - 90);
            boolean isHour = i % 5 == 0;

            int len = isHour ? hourMarkerLen : minMarkerLen;
            int width = isHour ? hourMarkerWidth : minMarkerWidth;

            AffineTransform old = g2.getTransform();
            g2.translate(cx, cy);
            g2.rotate(angle + Math.PI / 2);

            int y1 = markerOuterDist - len;
            g2.fillRect(-width / 2, y1, width, len);

            g2.setTransform(old);
        }

        // Time
        LocalTime now = LocalTime.now(ZoneId.systemDefault());
        int hours = now.getHour() % 12;
        int minutes = now.getMinute();
        int seconds = now.getSecond();

        double hourAngle = Math.toRadians((hours + minutes / 60.0) * 30 - 90);
        double minuteAngle = Math.toRadians((minutes + seconds / 60.0) * 6 - 90);
        double secondAngle = Math.toRadians(seconds * 6 - 90);

        int hourLen = (int)(r * 0.52);
        int minuteLen = (int)(r * 0.72);
        int secondLen = (int)(r * 0.68);

        // Hour hand - tapered black
        drawTaperedHand(g2, cx, cy, hourAngle, hourLen, (int)(r * 0.12), (int)(r * 0.06), new Color(30, 30, 30));

        // Minute hand - longer, thinner taper
        drawTaperedHand(g2, cx, cy, minuteAngle, minuteLen, (int)(r * 0.10), (int)(r * 0.04), new Color(30, 30, 30));

        // Second hand - characteristic Swiss style with red circle
        Color swissRed = new Color(220, 35, 35);
        int tailLen = (int)(r * 0.18);
        double cos = Math.cos(secondAngle), sin = Math.sin(secondAngle);

        // Tail
        int xTail = cx - (int)(tailLen * cos);
        int yTail = cy - (int)(tailLen * sin);
        g2.setColor(swissRed);
        g2.setStroke(new BasicStroke(Math.max(2f, r * 0.025f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(cx, cy, xTail, yTail);

        // Main shaft to just before the circle
        int shaftLen = secondLen - (int)(r * 0.12);
        int xShaft = cx + (int)(shaftLen * cos);
        int yShaft = cy + (int)(shaftLen * sin);
        g2.drawLine(cx, cy, xShaft, yShaft);

        // Characteristic circular tip
        int circleR = Math.max(4, (int)(r * 0.08));
        int xCircle = cx + (int)(secondLen * cos);
        int yCircle = cy + (int)(secondLen * sin);
        g2.fillOval(xCircle - circleR, yCircle - circleR, circleR * 2, circleR * 2);

        // Center cap
        int capR = Math.max(3, (int)(r * 0.05));
        g2.setColor(new Color(30, 30, 30));
        g2.fillOval(cx - capR, cy - capR, capR * 2, capR * 2);

        g2.dispose();
    }

    private void drawTaperedHand(Graphics2D g2, int cx, int cy, double angle, int len, int baseW, int tipW, Color color) {
        Path2D p = new Path2D.Double();
        p.moveTo(-baseW / 2.0, 0);
        p.lineTo(baseW / 2.0, 0);
        p.lineTo(tipW / 2.0, len);
        p.lineTo(-tipW / 2.0, len);
        p.closePath();

        AffineTransform at = new AffineTransform();
        at.translate(cx, cy);
        at.rotate(angle + Math.PI / 2);
        Shape shp = at.createTransformedShape(p);

        g2.setColor(color);
        g2.fill(shp);
    }
}
