package main.ui.features.home;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.AffineTransform;
import java.time.LocalTime;
import java.time.ZoneId;
import javax.swing.*;

public class AnalogClockPanel extends JPanel {
    private static final long serialVersionUID = 1L;

	@SuppressWarnings("unused")
	public AnalogClockPanel() {
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
        int r = Math.max(20, size / 2 - 6); // min sensible radius

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // Soft drop shadow behind the dial
        Graphics2D sh = (Graphics2D) g2.create();
        sh.setComposite(AlphaComposite.SrcOver.derive(0.18f));
        sh.setPaint(new RadialGradientPaint(new Point(cx, cy + r/3), r,
                new float[]{0f, 1f}, new Color[]{new Color(0,0,0,60), new Color(0,0,0,0)}));
        sh.fillOval(cx - r - 4, cy - r + 8, (r * 2) + 8, (r * 2) - 6);
        sh.dispose();

        // Bezel (thin metallic ring)
        Paint bezel = new LinearGradientPaint(cx, cy - r, cx, cy + r,
                new float[]{0f, 0.5f, 1f},
                new Color[]{new Color(230,230,230), new Color(190,190,190), new Color(220,220,220)});
        g2.setPaint(bezel);
        g2.fillOval(cx - r, cy - r, r*2, r*2);

        // Inner dial
        int dialInset = Math.max(4, (int)(r * 0.06));
        int dialR = r - dialInset;
        Point2D center = new Point2D.Float(cx, cy);
        RadialGradientPaint dialPaint = new RadialGradientPaint(center, dialR,
                new float[]{0f, 0.85f, 1f},
                new Color[]{new Color(255,255,255), new Color(242,242,242), new Color(230,230,230)});
        g2.setPaint(dialPaint);
        g2.fillOval(cx - dialR, cy - dialR, dialR*2, dialR*2);

        // Dial inner ring
        g2.setColor(new Color(180,180,180));
        g2.setStroke(new BasicStroke(Math.max(1f, r * 0.02f)));
        g2.drawOval(cx - dialR, cy - dialR, dialR*2, dialR*2);

        // Tick marks
        int tickOuter = dialR - Math.max(2, (int)(r * 0.02));
        for (int i = 0; i < 60; i++) {
            double a = Math.toRadians(i * 6 - 90);
            boolean hour = (i % 5 == 0);
            int len = hour ? Math.max(12, (int)(r * 0.10)) : Math.max(5, (int)(r * 0.05));
            int inner = tickOuter - len;
            int x1 = cx + (int)(inner * Math.cos(a));
            int y1 = cy + (int)(inner * Math.sin(a));
            int x2 = cx + (int)(tickOuter * Math.cos(a));
            int y2 = cy + (int)(tickOuter * Math.sin(a));
            if (hour) {
                g2.setColor(new Color(80,80,80));
                g2.setStroke(new BasicStroke(Math.max(2f, r * 0.02f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            } else {
                g2.setColor(new Color(120,120,120,160));
                g2.setStroke(new BasicStroke(Math.max(1f, r * 0.013f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            }
            g2.drawLine(x1, y1, x2, y2);
        }

        // Time
        LocalTime now = LocalTime.now(ZoneId.systemDefault());
        int hours = now.getHour() % 12;
        int minutes = now.getMinute();
        int seconds = now.getSecond();

        // Angles
        // Note: Hour/Minute hands are Path2D shapes defined along +Y (down), so rotate by an extra -90°
        double hourAngle = Math.toRadians((hours + minutes / 60.0 + seconds / 3600.0) * 30 - 180);
        double minuteAngle = Math.toRadians((minutes + seconds / 60.0) * 6 - 180);
        double secondAngle = Math.toRadians(seconds * 6 - 90);

        // Hands lengths
        int hourLen = (int)(dialR * 0.55);
        int minuteLen = (int)(dialR * 0.78);
        int secondLen = (int)(dialR * 0.84);

        // Hour hand (tapered)
        {
            int base = (int)(dialR * 0.10);
            int tipW = Math.max(3, (int)(r * 0.04));
            int baseW = Math.max(tipW + 2, (int)(r * 0.07));
            java.awt.geom.Path2D p = new java.awt.geom.Path2D.Double();
            // Build in hand-local coords, then rotate/translate via AffineTransform
            p.moveTo(-baseW/2.0, 0);
            p.lineTo(baseW/2.0, 0);
            p.lineTo(tipW/2.0, hourLen);
            p.lineTo(-tipW/2.0, hourLen);
            p.closePath();
            AffineTransform at = new AffineTransform();
            at.translate(cx, cy);
            at.rotate(hourAngle);
            at.translate(0, -base);
            Shape shp = at.createTransformedShape(p);
            // Fill
            g2.setPaint(new LinearGradientPaint(cx, cy - hourLen, cx, cy + hourLen,
                    new float[]{0f,1f}, new Color[]{new Color(70,70,70), new Color(30,30,30)}));
            g2.fill(shp);
            g2.setColor(new Color(20,20,20));
            g2.setStroke(new BasicStroke(1.2f));
            g2.draw(shp);
        }

        // Minute hand (slimmer taper)
        {
            int base = (int)(dialR * 0.12);
            int tipW = Math.max(2, (int)(r * 0.03));
            int baseW = Math.max(tipW + 2, (int)(r * 0.055));
            java.awt.geom.Path2D p = new java.awt.geom.Path2D.Double();
            p.moveTo(-baseW/2.0, 0);
            p.lineTo(baseW/2.0, 0);
            p.lineTo(tipW/2.0, minuteLen);
            p.lineTo(-tipW/2.0, minuteLen);
            p.closePath();
            AffineTransform at = new AffineTransform();
            at.translate(cx, cy);
            at.rotate(minuteAngle);
            at.translate(0, -base);
            Shape shp = at.createTransformedShape(p);
            g2.setPaint(new LinearGradientPaint(cx, cy - minuteLen, cx, cy + minuteLen,
                    new float[]{0f,1f}, new Color[]{new Color(90,90,90), new Color(40,40,40)}));
            g2.fill(shp);
            g2.setColor(new Color(25,25,25));
            g2.setStroke(new BasicStroke(1.0f));
            g2.draw(shp);
        }

        // Second hand (red, with tail and counterweight)
        {
            int tail = (int)(dialR * 0.20);
            int cwR = Math.max(3, (int)(r * 0.045));
            double cos = Math.cos(secondAngle), sin = Math.sin(secondAngle);
            int xTip = cx + (int)(secondLen * cos);
            int yTip = cy + (int)(secondLen * sin);
            int xTail = cx - (int)(tail * cos);
            int yTail = cy - (int)(tail * sin);
            g2.setStroke(new BasicStroke(Math.max(1f, r * 0.012f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(200, 40, 40));
            g2.drawLine(xTail, yTail, xTip, yTip);
            // Counterweight circle
            Paint redGlass = new RadialGradientPaint(new Point(cx, cy), cwR,
                    new float[]{0f,1f}, new Color[]{new Color(255, 80, 80), new Color(180,20,20)});
            g2.setPaint(redGlass);
            g2.fillOval(cx - cwR, cy - cwR, cwR*2, cwR*2);
            g2.setColor(new Color(120,0,0,180));
            g2.drawOval(cx - cwR, cy - cwR, cwR*2, cwR*2);
        }

        // Center cap (metallic)
        int capR = Math.max(3, (int)(r * 0.06));
        Paint cap = new RadialGradientPaint(new Point(cx - capR/3, cy - capR/3), capR,
                new float[]{0f, 0.65f, 1f},
                new Color[]{new Color(255,255,255), new Color(210,210,210), new Color(160,160,160)});
        g2.setPaint(cap);
        g2.fillOval(cx - capR, cy - capR, capR*2, capR*2);
        g2.setColor(new Color(90,90,90));
        g2.drawOval(cx - capR, cy - capR, capR*2, capR*2);

        // Glass highlight arc
        Graphics2D hg = (Graphics2D) g2.create();
        hg.setClip(new java.awt.geom.Ellipse2D.Double(cx - dialR, cy - dialR, dialR*2, dialR*2));
        hg.setPaint(new GradientPaint(0, cy - dialR, new Color(255,255,255,120), 0, cy, new Color(255,255,255,0)));
        hg.fillRoundRect(cx - dialR + 4, cy - dialR + 4, dialR*2 - 8, dialR - 6, dialR, dialR);
        hg.dispose();

        g2.dispose();
    }
}
