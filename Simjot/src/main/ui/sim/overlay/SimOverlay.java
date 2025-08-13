package main.ui.sim.overlay;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import javax.swing.*;
import main.core.sim.api.SimEventBus;
import main.ui.theme.aero.AeroPainters;

/**
 * Minimal overlay for Sim. Added to the JFrame layered pane.
 */
public class SimOverlay extends JComponent implements SimEventBus.Listener {
    private String message = "Hi, I’m Sim.";
    private Point dragAnchor = null;
    private static final int CLOSE_SIZE = 14; // hit area for close (top-right)

    // Animation state
    private Timer animTimer;
    private float entranceAlpha = 0f;   // 0..1 (eased)
    private float entranceT = 0f;       // 0..1 (time progress)
    private int entranceOffsetY = 12;   // slides up as it fades in
    private double spinAngle = 0.0;     // radians, increases over time
    private boolean animatingIn = false;

    // Staged sequence state
    private boolean entryInProgress = false;
    private boolean panelAppearing = false;
    private boolean disposeInProgress = false;
    private int revealIndex = 0;  // next orb to activate
    private int hideIndex = -1;   // next orb to hide (dispose)
    private double[] orbT = new double[ORB_COUNT]; // 0..1 progress per orb
    private float panelT = 0f; // 0..1 expansion of text panel

    // Heart animation (reuse style from HeaderPanel but simplified: pulse scale with small spring)
    private float heartScale = 1f;
    private double heartPhase = 0;       // continuous phase
    private double lastBeatValue = 0;    // for peak detection
    private float heartSpring = 0f;      // overshoot after peak

    // Orb/emotion configuration
    private static final int ORB_COUNT = 5;
    private static final int ORB_RADIUS = 16; // circle radius
    private static final int ORB_RING_RADIUS = 42; // distance from center
    private static final int HEART_CLEARANCE = 8; // extra spacing between heart and orbs

    public SimOverlay() {
        setOpaque(false);
        setVisible(true);
        // Listen for speak events
        try { SimEventBus.get().addListener(this); } catch (Throwable ignored) {}

        // Drag to move
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (isInCloseHotspot(e.getPoint())) {
                    // Dispose with animation
                    startDisposeSequence();
                    return;
                }
                dragAnchor = e.getPoint();
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }
            @Override public void mouseReleased(MouseEvent e) {
                dragAnchor = null;
                setCursor(Cursor.getDefaultCursor());
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (dragAnchor == null) return;
                Point p = getLocation();
                int nx = p.x + e.getX() - dragAnchor.x;
                int ny = p.y + e.getY() - dragAnchor.y;
                setLocation(nx, ny);
                getParent().repaint();
            }
            @Override public void mouseMoved(MouseEvent e) {
                setCursor(isInCloseHotspot(e.getPoint()) ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);

        // 60 FPS animation driver for entrance + spin
        animTimer = new Timer(16, e -> {
            boolean needsRepaint = false;
            // entrance animation: ease-out fade and slide up
            if (animatingIn && entranceAlpha < 1f) {
                entranceT = Math.min(1f, entranceT + 0.08f);
                entranceAlpha = easeOutCubic(entranceT);
                entranceOffsetY = (int)Math.round((1.0 - entranceAlpha) * 12);
                if (entranceT >= 1f) { animatingIn = false; }
                needsRepaint = true;
            }

            // Entry staged sequence: reveal orbs one-by-one, then expand panel
            if (entryInProgress) {
                if (revealIndex < ORB_COUNT) {
                    orbT[revealIndex] = Math.min(1.0, orbT[revealIndex] + 0.10);
                    if (orbT[revealIndex] >= 1.0) {
                        revealIndex++;
                    }
                    needsRepaint = true;
                } else {
                    // All orbs in orbit, start panel expand
                    panelAppearing = true;
                }
                if (panelAppearing && panelT < 1f) {
                    panelT = Math.min(1f, panelT + 0.12f);
                    needsRepaint = true;
                }
                if (revealIndex >= ORB_COUNT && panelT >= 1f) {
                    entryInProgress = false;
                }
            }

            // Dispose sequence: collapse panel then hide orbs in reverse
            if (disposeInProgress) {
                if (panelT > 0f) {
                    panelT = Math.max(0f, panelT - 0.16f);
                    needsRepaint = true;
                } else if (hideIndex >= 0) {
                    orbT[hideIndex] = Math.max(0.0, orbT[hideIndex] - 0.14);
                    if (orbT[hideIndex] <= 0.0) hideIndex--;
                    needsRepaint = true;
                } else {
                    disposeInProgress = false;
                    setVisible(false);
                }
            }
            // continuous spin for orbs
            double speed = Math.PI / 120; // ~1.5 sec per rotation
            spinAngle += speed;
            if (spinAngle > Math.PI * 2) spinAngle -= Math.PI * 2;

            // Heart pulse animation (cosine ease-in-out + small spring at peaks)
            heartPhase += 0.05; // speed similar to header
            double eased = (1 - Math.cos(heartPhase)) * 0.5; // 0..1
            boolean justPeaked = (eased > 0.98 && lastBeatValue <= 0.98);
            if (justPeaked) {
                heartSpring = 0.08f; // overshoot amount
            }
            if (eased < 0.5) {
                // allow next peak
            }
            lastBeatValue = eased;
            if (heartSpring > 0f) {
                heartSpring *= 0.90f; // damping
                if (heartSpring < 0.001f) heartSpring = 0f;
            }
            float baseAmp = 0.06f; // subtle
            heartScale = 1f + baseAmp * (float)(eased * 2 - 1) + heartSpring;

            needsRepaint = true;
            if (needsRepaint) repaint();
        });
        animTimer.setRepeats(true);
        try { animTimer.start(); } catch (Throwable ignored) {}
    }

    public void showMessage(String msg) {
        this.message = msg == null ? "" : msg;
        // Start entrance animation each time Sim speaks
        animatingIn = true;
        entranceAlpha = 0f;
        entranceT = 0f;
        entranceOffsetY = 12;
        startEntrySequence();
        if (!isVisible()) setVisible(true);
        repaint();
    }

    @Override
    public void onSpeak(String message) {
        // Ensure UI updates occur on the EDT
        SwingUtilities.invokeLater(() -> showMessage(message));
    }

    @Override
    public void onSpeakStart() {
        // Show a placeholder immediately so the overlay appears even if no tokens are emitted
        SwingUtilities.invokeLater(() -> showMessage("…"));
    }

    /** Unsubscribe from event bus and hide overlay. */
    public void disposeOverlay() {
        try { SimEventBus.get().removeListener(this); } catch (Throwable ignored) {}
        stopAnimTimer();
        setVisible(false);
    }

    private void stopAnimTimer() {
        try { if (animTimer != null) animTimer.stop(); } catch (Throwable ignored) {}
    }

    @Override
    public Dimension getPreferredSize() {
        // Wider to fit orbs + text panel
        return new Dimension(420, 160);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();

        // Apply entrance fade and vertical offset
        Composite oldComp = g2.getComposite();
        float a = Math.max(0.85f, Math.min(1f, entranceAlpha));
        g2.setComposite(AlphaComposite.SrcOver.derive(a));
        g2.translate(0, entranceOffsetY);

        // No outer frame/background — keep canvas transparent behind orbs and panel

        // Close icon (simple ×)
        int cx = w - CLOSE_SIZE - 6;
        int cy = 6;
        g2.setColor(new Color(0,0,0,90));
        g2.drawRect(cx, cy, CLOSE_SIZE, CLOSE_SIZE);
        g2.drawLine(cx + 3, cy + 3, cx + CLOSE_SIZE - 3, cy + CLOSE_SIZE - 3);
        g2.drawLine(cx + 3, cy + CLOSE_SIZE - 3, cx + CLOSE_SIZE - 3, cy + 3);

        // Layout: left area for orbs, right area for text panel
        int padding = 14;
        int leftW = 140; // space for orbs
        int rightX = padding + leftW + 10;
        int rightW = w - rightX - padding;
        int centerX = padding + leftW / 2;
        int centerY = h / 2 + 6; // slightly lower to balance header

        // Beating heart at center of spinning orbs (reused style, simplified)
        {
            Graphics2D gh = (Graphics2D) g2.create();
            gh.translate(centerX, centerY);
            gh.scale(heartScale, heartScale);
            Shape heart = createHeartShape();
            Rectangle hb = heart.getBounds();
            // Soft shadow
            Graphics2D gShadow = (Graphics2D) gh.create();
            gShadow.translate(0, 3);
            gShadow.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));
            gShadow.setColor(new Color(0,0,0,60));
            gShadow.fill(heart);
            gShadow.dispose();
            // Gradient fill (bluish, matching header)
            float cxh = hb.x + hb.width * 0.45f;
            float cyh = hb.y + hb.height * 0.35f;
            float rh = Math.max(hb.width, hb.height) * 0.75f;
            RadialGradientPaint heartPaint = new RadialGradientPaint(
                    new Point2D.Float(cxh, cyh), rh,
                    new float[]{0f, 1f},
                    new Color[]{new Color(153,209,255,210), new Color(0,84,153,190)}
            );
            gh.setPaint(heartPaint);
            gh.fill(heart);
            // Outline
            gh.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            gh.setColor(new Color(255,255,255,28));
            gh.draw(heart);
            gh.dispose();
        }

        // Draw spinning orbs in a ring with per-orb progress
        for (int i = 0; i < ORB_COUNT; i++) {
            double p = clamp01(orbT[i]);
            if (p <= 0.0) continue; // not visible yet
            double eased = easeOutCubic((float)p);
            double theta = spinAngle + (Math.PI * 2 * i / ORB_COUNT);
            int radial = (int)Math.round((ORB_RING_RADIUS + HEART_CLEARANCE) * eased);
            int ox = (int) Math.round(centerX + radial * Math.cos(theta));
            int oy = (int) Math.round(centerY + radial * Math.sin(theta));
            int d = ORB_RADIUS * 2;
            // Orb shadow/glow
            g2.setColor(new Color(0, 0, 0, (int)(40 * p)));
            g2.fillOval(ox - ORB_RADIUS, oy - ORB_RADIUS + 2, d, d);
            // Orb body (metallic silver gradient)
            float gx = ox - ORB_RADIUS;
            float gy = oy - ORB_RADIUS;
            RadialGradientPaint metallic = new RadialGradientPaint(
                    new Point2D.Float(gx + ORB_RADIUS * 0.6f, gy + ORB_RADIUS * 0.4f),
                    ORB_RADIUS,
                    new float[]{0f, 0.6f, 1f},
                    new Color[]{
                            new Color(255,255,255, 230), // bright specular
                            new Color(200,200,205, 255),  // mid silver
                            new Color(150,150,155, 255)   // edge dark
                    }
            );
            Paint oldPaint = g2.getPaint();
            g2.setPaint(metallic);
            g2.fillOval(ox - ORB_RADIUS, oy - ORB_RADIUS, d, d);
            g2.setPaint(oldPaint);
            // Thin dark edge for definition
            g2.setColor(new Color(60, 60, 65, 160));
            g2.drawOval(ox - ORB_RADIUS, oy - ORB_RADIUS, d, d);
            // Vector symbol centered with alpha
            Composite old = g2.getComposite();
            g2.setComposite(AlphaComposite.SrcOver.derive((float)p));
            drawEmotionSymbol(g2, ox, oy, ORB_RADIUS - 4, i);
            g2.setComposite(old);
        }

        // Text panel expand animation
        int panelY = padding;
        int panelH = h - padding * 2;
        if (panelT > 0f) {
            float pe = easeOutBack(panelT);
            int currentW = Math.max(0, Math.min(rightW, (int)Math.round(rightW * pe)));
            if (currentW > 0) {
                int arc = 14;
                Rectangle panelRect = new Rectangle(rightX, panelY, currentW, panelH);
                // Base fill
                g2.setColor(new Color(255, 255, 255, 255));
                g2.fillRoundRect(panelRect.x, panelRect.y, panelRect.width, panelRect.height, arc, arc);
                // Border
                g2.setColor(new Color(0, 0, 0, 120));
                g2.drawRoundRect(panelRect.x, panelRect.y, panelRect.width, panelRect.height, arc, arc);
                // Inner shadow for depth
                AeroPainters.paintInnerShadow(g2, panelRect, arc, new Color(0, 0, 0), 8, 70);
                // Gloss highlight band (top)
                Shape oldClip = g2.getClip();
                g2.setClip(new Rectangle(panelRect.x + 2, panelRect.y + 2, panelRect.width - 4, Math.max(0, panelRect.height / 2 - 2)));
                GradientPaint gloss = new GradientPaint(
                        panelRect.x, panelRect.y,
                        new Color(255, 255, 255, 110),
                        panelRect.x, panelRect.y + panelRect.height / 2f,
                        new Color(255, 255, 255, 0)
                );
                Paint oldPaint = g2.getPaint();
                g2.setPaint(gloss);
                g2.fillRoundRect(panelRect.x, panelRect.y, panelRect.width, panelRect.height, arc, arc);
                g2.setPaint(oldPaint);
                g2.setClip(oldClip);
            }
        }

        // Message text (reveals with panel)
        if (panelT > 0f) {
            g2.setColor(new Color(20, 20, 20));
            g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 14f));
            int tx = rightX + 12;
            int ty = panelY + 20;
            int tw = Math.max(0, (int)Math.round((rightW - 24) * Math.min(1f, panelT)));
            for (String line : wrapText(message, tw, g2)) {
                g2.drawString(line, tx, ty);
                ty += 18;
            }
        }

        // restore
        g2.setComposite(oldComp);
        g2.dispose();
    }

    private static float easeOutCubic(float t) {
        // clamp and ease
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        double u = 1 - t;
        return (float)(1 - u*u*u);
    }

    private static float clamp01(double v) {
        if (v <= 0.0) return 0f;
        if (v >= 1.0) return 1f;
        return (float)v;
    }

    private static float easeOutBack(float t) {
        // classic easeOutBack overshoot
        t = clamp01(t);
        double s = 1.70158;
        double u = t - 1;
        return (float)(1 + u*u*((s + 1)*u + s));
    }

    // Heart vector path (same proportions as HeaderPanel)
    private Shape createHeartShape() {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(0, -14);
        path.curveTo(-18, -35, -42, -7, 0, 22);
        path.curveTo(42, -7, 18, -35, 0, -14);
        path.closePath();
        return path;
    }

    // Draw abstract vector symbols for emotions (index-based)
    // 0: joy (sunburst), 1: neutral (square), 2: low mood (droplet),
    // 3: anger (bolt), 4: calm (wave)
    private void drawEmotionSymbol(Graphics2D g2, int cx, int cy, int r, int idx) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Stroke oldStroke = g2.getStroke();
        g2.setColor(new Color(0, 0, 0, 180));
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (idx % ORB_COUNT) {
            case 0: { // joy: sunburst
                int inner = Math.max(2, r/4);
                // center dot
                g2.fill(new Ellipse2D.Double(cx - inner/2.0, cy - inner/2.0, inner, inner));
                // rays
                int rays = 8;
                for (int i = 0; i < rays; i++) {
                    double a = (Math.PI * 2 * i) / rays;
                    int x1 = (int)Math.round(cx + (r*0.3) * Math.cos(a));
                    int y1 = (int)Math.round(cy + (r*0.3) * Math.sin(a));
                    int x2 = (int)Math.round(cx + (r*0.9) * Math.cos(a));
                    int y2 = (int)Math.round(cy + (r*0.9) * Math.sin(a));
                    g2.drawLine(x1, y1, x2, y2);
                }
                break;
            }
            case 1: { // neutral: square
                int s = Math.max(3, r/2);
                g2.drawRect(cx - s/2, cy - s/2, s, s);
                break;
            }
            case 2: { // low mood: droplet
                Path2D drop = new Path2D.Double();
                drop.moveTo(cx, cy - r/2.0);
                drop.curveTo(cx + r/3.0, cy - r/6.0, cx + r/3.0, cy + r/3.0, cx, cy + r/2.0);
                drop.curveTo(cx - r/3.0, cy + r/3.0, cx - r/3.0, cy - r/6.0, cx, cy - r/2.0);
                g2.draw(drop);
                break;
            }
            case 3: { // anger: fire (flame)
                // Outer flame silhouette
                Path2D flame = new Path2D.Double();
                double topY = cy - r * 0.55;
                double bottomY = cy + r * 0.5;
                double leftX = cx - r * 0.35;
                double rightX = cx + r * 0.35;
                flame.moveTo(cx, topY);
                flame.curveTo(cx + r * 0.25, cy - r * 0.45,
                               rightX, cy - r * 0.05,
                               cx + r * 0.15, cy + r * 0.15);
                flame.curveTo(cx + r * 0.05, cy + r * 0.35,
                               cx + r * 0.05, bottomY,
                               cx, bottomY);
                flame.curveTo(cx - r * 0.05, bottomY,
                               cx - r * 0.05, cy + r * 0.35,
                               cx - r * 0.15, cy + r * 0.15);
                flame.curveTo(leftX,  cy - r * 0.05,
                               cx - r * 0.25, cy - r * 0.45,
                               cx, topY);
                // Fill then outline for clarity at small sizes
                Paint oldPaint = g2.getPaint();
                g2.setPaint(new Color(0, 0, 0, 160));
                g2.fill(flame);
                g2.setPaint(oldPaint);
                g2.draw(flame);
                break;
            }
            default: { // calm: wave
                Path2D wave = new Path2D.Double();
                double amplitude = r * 0.3;
                double length = r * 1.4;
                double startX = cx - length/2.0;
                double step = length / 4.0;
                wave.moveTo(startX, cy);
                wave.curveTo(startX + step*0.5, cy - amplitude, startX + step*1.5, cy + amplitude, startX + step*2.0, cy);
                wave.curveTo(startX + step*2.5, cy - amplitude, startX + step*3.5, cy + amplitude, startX + step*4.0, cy);
                g2.draw(wave);
                break;
            }
        }
        g2.setStroke(oldStroke);
    }

    private void startEntrySequence() {
        entryInProgress = true;
        disposeInProgress = false;
        panelAppearing = false;
        revealIndex = 0;
        hideIndex = -1;
        panelT = 0f;
        for (int i = 0; i < ORB_COUNT; i++) orbT[i] = 0.0;
    }

    private void startDisposeSequence() {
        disposeInProgress = true;
        entryInProgress = false;
        panelAppearing = false;
        hideIndex = ORB_COUNT - 1;
    }

    private java.util.List<String> wrapText(String text, int maxWidth, Graphics2D g2) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String w : words) {
            String candidate = current.length() == 0 ? w : current + " " + w;
            if (g2.getFontMetrics().stringWidth(candidate) > maxWidth) {
                if (current.length() > 0) lines.add(current.toString());
                current = new StringBuilder(w);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (current.length() > 0) lines.add(current.toString());
        return lines;
    }

    private boolean isInCloseHotspot(Point p) {
        int w = getWidth();
        Rectangle r = new Rectangle(w - CLOSE_SIZE - 6, 6, CLOSE_SIZE, CLOSE_SIZE);
        return r.contains(p);
    }
}
