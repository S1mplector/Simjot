package main.ui.sim;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import main.core.sim.SimEventBus;

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

    // Orb/emotion configuration
    private static final int ORB_COUNT = 5;
    private static final int ORB_RADIUS = 16; // circle radius
    private static final int ORB_RING_RADIUS = 42; // distance from center
    private static final String[] EMOJIS = new String[]{"🙂","😐","😢","😠","😌"};
    private static final Color[] ORB_COLORS = new Color[]{
            new Color(255, 214, 102), // warm happy
            new Color(180, 180, 200), // neutral
            new Color(120, 170, 255), // sad/blue
            new Color(255, 115, 115), // angry/red
            new Color(140, 220, 170)  // calm/green
    };

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
        showMessage(message);
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

        // Outer container background (subtle)
        g2.setColor(new Color(255, 255, 255, 255));
        g2.fillRoundRect(0, 0, w - 1, h - 1, 18, 18);
        g2.setColor(new Color(0, 0, 0, 140));
        g2.drawRoundRect(0, 0, w - 1, h - 1, 18, 18);

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

        // Draw spinning orbs in a ring with per-orb progress
        for (int i = 0; i < ORB_COUNT; i++) {
            double p = clamp01(orbT[i]);
            if (p <= 0.0) continue; // not visible yet
            double eased = easeOutCubic((float)p);
            double theta = spinAngle + (Math.PI * 2 * i / ORB_COUNT);
            int radial = (int)Math.round(ORB_RING_RADIUS * eased);
            int ox = (int) Math.round(centerX + radial * Math.cos(theta));
            int oy = (int) Math.round(centerY + radial * Math.sin(theta));
            int d = ORB_RADIUS * 2;
            // Orb shadow/glow
            g2.setColor(new Color(0, 0, 0, (int)(40 * p)));
            g2.fillOval(ox - ORB_RADIUS, oy - ORB_RADIUS + 2, d, d);
            // Orb body
            g2.setColor(ORB_COLORS[i % ORB_COLORS.length]);
            g2.fillOval(ox - ORB_RADIUS, oy - ORB_RADIUS, d, d);
            g2.setColor(new Color(0, 0, 0, 90));
            g2.drawOval(ox - ORB_RADIUS, oy - ORB_RADIUS, d, d);
            // Emoji centered with alpha
            Composite old = g2.getComposite();
            g2.setComposite(AlphaComposite.SrcOver.derive((float)p));
            g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 14f));
            String emo = EMOJIS[i % EMOJIS.length];
            FontMetrics fm = g2.getFontMetrics();
            int ttw = fm.stringWidth(emo);
            int tth = fm.getAscent();
            g2.drawString(emo, ox - ttw / 2, oy + tth / 2 - 4);
            g2.setComposite(old);
        }

        // Text panel expand animation
        int panelY = padding;
        int panelH = h - padding * 2;
        if (panelT > 0f) {
            float pe = easeOutBack(panelT);
            int currentW = Math.max(0, Math.min(rightW, (int)Math.round(rightW * pe)));
            if (currentW > 0) {
                g2.setColor(new Color(255, 255, 255, 255));
                g2.fillRoundRect(rightX, panelY, currentW, panelH, 14, 14);
                g2.setColor(new Color(0, 0, 0, 120));
                g2.drawRoundRect(rightX, panelY, currentW, panelH, 14, 14);
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
