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

    // Phase 1 CTA state (buttons under the message panel)
    private boolean ctaVisible = false;
    private Rectangle chatBtnRect = null;    // component coordinate space
    private Rectangle notNowBtnRect = null;  // component coordinate space
    private boolean chatHover = false;
    private boolean notHover = false;

    // Phase 2: inline chat mode
    private boolean chatMode = false;
    private java.util.List<ChatLine> chatHistory = new java.util.ArrayList<>();
    private JTextField chatInput;
    private Rectangle chatExitRect = null; // small "x" inside panel to end chat
    private int streamingAssistantIndex = -1; // index of assistant line being streamed
    private Rectangle lastPanelRect = null; // updated during paint for layout
    // Streaming typing indicator state
    private boolean typingInProgress = false;
    private int typingTick = 0; // advances in anim timer to animate dots

    public SimOverlay() {
        setOpaque(false);
        setVisible(true);
        // Listen for speak events
        try { SimEventBus.get().addListener(this); } catch (Throwable ignored) {}

        // Drag to move
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (isInCloseHotspot(e.getPoint())) {
                    // Dispose or exit chat depending on mode
                    if (chatMode) {
                        endChatMode();
                    } else {
                        startDisposeSequence();
                    }
                    return;
                }
                // Chat inline exit box inside panel
                if (chatMode && chatExitRect != null && chatExitRect.contains(e.getPoint())) {
                    endChatMode();
                    return;
                }
                // CTA button clicks (when visible)
                if (ctaVisible) {
                    if (chatBtnRect != null && chatBtnRect.contains(e.getPoint())) {
                        // Phase 2: enter chat mode and seed with current message
                        startChatMode();
                        return;
                    }
                    if (notNowBtnRect != null && notNowBtnRect.contains(e.getPoint())) {
                        ctaVisible = false;
                        // Phase 1: dismiss overlay on Not now
                        startDisposeSequence();
                        return;
                    }
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
                boolean hand = false;
                chatHover = false; notHover = false;
                if (isInCloseHotspot(e.getPoint())) {
                    hand = true;
                } else if (ctaVisible && !chatMode) {
                    if (chatBtnRect != null && chatBtnRect.contains(e.getPoint())) { chatHover = true; hand = true; }
                    if (notNowBtnRect != null && notNowBtnRect.contains(e.getPoint())) { notHover = true; hand = true; }
                }
                setCursor(hand ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
                if (ctaVisible || chatMode) repaint();
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

            // advance typing animation
            typingTick = (typingTick + 1) & 0xFFFF;
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
        if (!chatMode) {
            // Show CTA when not in chat
            ctaVisible = true;
            chatBtnRect = null; notNowBtnRect = null;
        }
        repaint();
    }

    @Override
    public void onSpeak(String message) {
        if (chatMode) {
            final String t = message == null ? "" : message;
            SwingUtilities.invokeLater(() -> {
                try { System.out.println("[SimOverlay] chat onSpeak append len=" + t.length()); } catch (Throwable ignored) {}
                if (streamingAssistantIndex < 0 || streamingAssistantIndex >= chatHistory.size()) {
                    chatHistory.add(new ChatLine(Role.ASSISTANT, t));
                    streamingAssistantIndex = chatHistory.size() - 1;
                } else {
                    ChatLine cl = chatHistory.get(streamingAssistantIndex);
                    cl.text = cl.text + t;
                }
                // rely on anim timer for repaint to reduce per-token repaints
            });
        } else {
            // Ensure UI updates occur on the EDT
            SwingUtilities.invokeLater(() -> showMessage(message));
        }
    }

    @Override
    public void onSpeakStart() {
        if (chatMode) {
            SwingUtilities.invokeLater(() -> {
                chatHistory.add(new ChatLine(Role.ASSISTANT, ""));
                streamingAssistantIndex = chatHistory.size() - 1;
                typingInProgress = true;
                capChatHistory();
                repaint();
            });
        } else {
            // Show a placeholder immediately so the overlay appears even if no tokens are emitted
            SwingUtilities.invokeLater(() -> showMessage("…"));
        }
    }

    @Override
    public void onSpeakEnd() {
        typingInProgress = false;
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

        // Text/Chat panel expand animation
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

                // Chat exit button (visible only in chat mode)
                if (chatMode) {
                    int ex = panelRect.x + panelRect.width - 18;
                    int ey = panelRect.y + 8;
                    chatExitRect = new Rectangle(ex, ey, 12, 12);
                    g2.setColor(new Color(0,0,0,90));
                    g2.drawRect(chatExitRect.x, chatExitRect.y, chatExitRect.width, chatExitRect.height);
                    g2.drawLine(ex + 2, ey + 2, ex + 10, ey + 10);
                    g2.drawLine(ex + 2, ey + 10, ex + 10, ey + 2);
                } else {
                    chatExitRect = null;
                }

                // Store for input layout
                lastPanelRect = panelRect;
                // Defer layout to after paint to avoid re-entrancy
                if (chatMode) SwingUtilities.invokeLater(this::layoutInputField);
            }
        }

        // Message or Chat content (reveals with panel)
        if (panelT > 0f && !chatMode) {
            g2.setColor(new Color(20, 20, 20));
            g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 14f));
            int tx = rightX + 12;
            int ty = panelY + 20;
            int tw = Math.max(0, (int)Math.round((rightW - 24) * Math.min(1f, panelT)));
            for (String line : wrapText(message, tw, g2)) {
                g2.drawString(line, tx, ty);
                ty += 18;
            }
            // CTA row (Phase 1): appears below the message once panel is mostly expanded
            if (ctaVisible && tw > 60) {
                int spacing = 10;
                ty += spacing;
                // Primary button: Chat more
                String chatLabel = "Chat more";
                Font btnFont = g2.getFont().deriveFont(Font.BOLD, 12f);
                FontMetrics fm = g2.getFontMetrics(btnFont);
                int chatTextW = fm.stringWidth(chatLabel);
                int chatPadX = 12, chatPadY = 6;
                int chatH = fm.getAscent() + fm.getDescent() + chatPadY * 2;
                int chatW = chatTextW + chatPadX * 2;
                int chatX = tx;
                int chatY = ty;
                // Secondary button: Not now
                String notLabel = "Not now";
                int notTextW = fm.stringWidth(notLabel);
                int notPadX = 10, notPadY = 6;
                int notH = chatH;
                int notW = notTextW + notPadX * 2;
                int notX = chatX + chatW + 10;
                int notY = ty;

                // Draw primary button
                g2.setFont(btnFont);
                Shape r1 = new RoundRectangle2D.Float(chatX, chatY, chatW, chatH, 10, 10);
                Color c1 = chatHover ? new Color(0, 122, 204) : new Color(0, 102, 180);
                g2.setColor(c1);
                g2.fill(r1);
                g2.setColor(new Color(255,255,255));
                g2.drawString(chatLabel, chatX + chatPadX, chatY + chatPadY + fm.getAscent() - 2);

                // Draw secondary ghost button
                Shape r2 = new RoundRectangle2D.Float(notX, notY, notW, notH, 10, 10);
                g2.setColor(new Color(0,0,0, chatHover || notHover ? 140 : 120));
                g2.draw(r2);
                g2.setColor(new Color(30,30,30));
                g2.drawString(notLabel, notX + notPadX, notY + notPadY + fm.getAscent() - 2);

                // Store hit rects in component coordinates (accounting for entrance offset)
                int offsetY = entranceOffsetY;
                chatBtnRect = new Rectangle(chatX, chatY + offsetY, chatW, chatH);
                notNowBtnRect = new Rectangle(notX, notY + offsetY, notW, notH);
            } else {
                chatBtnRect = null; notNowBtnRect = null;
            }
        }

        // Chat mode content
        if (panelT > 0f && chatMode) {
            // Layout
            Rectangle pr = lastPanelRect != null ? lastPanelRect : new Rectangle(rightX, panelY, rightW, panelH);
            int pad = 10;
            int contentX = pr.x + pad;
            int contentY = pr.y + 18; // leave room for exit
            int contentW = pr.width - pad*2;
            int contentH = pr.height - 50; // leave space for input

            // Draw history from bottom so latest is visible
            g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 13f));
            int lh = 18;
            int maxTextW = (int)(contentW * 0.9);
            java.util.List<Integer> toDraw = new java.util.ArrayList<>();
            int used = 0;
            for (int i = chatHistory.size() - 1; i >= 0; i--) {
                ChatLine line = chatHistory.get(i);
                if (line == null || line.text == null) continue;
                java.util.List<String> rows = wrapText(line.text, maxTextW, g2);
                int boxH = Math.max(lh + 8, rows.size() * lh + 8);
                int hWithGap = boxH + 8;
                if (used + hWithGap > contentH - 8) break;
                toDraw.add(i);
                used += hWithGap;
            }
            // draw in chronological order of selected subset
            int y = contentY + contentH - used;
            for (int k = toDraw.size() - 1; k >= 0; k--) {
                int idx = toDraw.get(k);
                ChatLine line = chatHistory.get(idx);
                java.util.List<String> rows = wrapText(line.text, maxTextW, g2);
                int boxH = Math.max(lh + 8, rows.size() * lh + 8);
                int boxW = Math.min(contentW, Math.max(80, maxWidth(rows, g2) + 16));
                int bx = line.role == Role.USER ? (contentX + contentW - boxW) : contentX;
                int by = y;
                RoundRectangle2D rr = new RoundRectangle2D.Float(bx, by, boxW, boxH, 12, 12);
                g2.setColor(line.role == Role.USER ? new Color(230,243,255) : new Color(245,245,245));
                g2.fill(rr);
                g2.setColor(new Color(0,0,0,60));
                g2.draw(rr);
                g2.setColor(new Color(20,20,20));
                int tx = bx + 8;
                int ty = by + 16;
                for (String rline : rows) { g2.drawString(rline, tx, ty); ty += lh; }
                // Typing indicator for the current assistant streaming line
                if (typingInProgress && idx == streamingAssistantIndex && line.role == Role.ASSISTANT) {
                    int dots = (typingTick / 20) % 4; // 0..3
                    String ellipsis = dots == 0 ? "" : ".".repeat(dots);
                    if (!ellipsis.isEmpty()) {
                        String last = rows.isEmpty() ? "" : rows.get(rows.size() - 1);
                        int tw = g2.getFontMetrics().stringWidth(last + " ");
                        int ey = by + 16 + (rows.size() - 1) * lh;
                        g2.setColor(new Color(120,120,120));
                        g2.drawString(ellipsis, tx + tw, ey);
                        g2.setColor(new Color(20,20,20));
                    }
                }
                y += boxH + 8;
            }

            // Input background
            int inputY = pr.y + pr.height - 34;
            RoundRectangle2D inputBg = new RoundRectangle2D.Float(pr.x + pad, inputY, pr.width - pad*2, 24, 10, 10);
            g2.setColor(new Color(250,250,250));
            g2.fill(inputBg);
            g2.setColor(new Color(0,0,0,90));
            g2.draw(inputBg);
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
        endChatModeInternal(false);
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

    // --- Chat helpers ---
    private enum Role { USER, ASSISTANT }
    private static final class ChatLine {
        final Role role; String text;
        ChatLine(Role r, String t){ this.role = r; this.text = t == null ? "" : t; }
    }

    private void startChatMode() {
        chatMode = true;
        ctaVisible = false;
        streamingAssistantIndex = -1;
        // Seed with current assistant message if any
        if (message != null && !message.isBlank()) {
            chatHistory.add(new ChatLine(Role.ASSISTANT, message));
        }
        ensureInputField();
        revalidate(); repaint();
        // Entering chat should be user-driven only; do not auto-trigger follow-ups here to avoid interference
    }

    private void endChatMode() {
        // Emit end event and dispose panel with animation
        try { SimEventBus.get().emitChatEnded(); } catch (Throwable ignored) {}
        endChatModeInternal(true);
        startDisposeSequence();
    }

    private void endChatModeInternal(boolean removeInput) {
        chatMode = false;
        streamingAssistantIndex = -1;
        if (removeInput && chatInput != null) {
            try { remove(chatInput); } catch (Throwable ignored) {}
            chatInput = null;
        }
    }

    private void ensureInputField() {
        if (chatInput != null) {
            layoutInputField();
            return;
        }
        chatInput = new JTextField();
        chatInput.setOpaque(false);
        chatInput.setBorder(javax.swing.BorderFactory.createEmptyBorder(2,8,2,8));
        chatInput.setForeground(new Color(20,20,20));
        chatInput.setCaretColor(new Color(20,20,20));
        chatInput.setFont(getFont().deriveFont(Font.PLAIN, 13f));
        chatInput.addActionListener(e -> submitChat());
        add(chatInput);
        layoutInputField();
        SwingUtilities.invokeLater(() -> chatInput.requestFocusInWindow());
    }

    private void layoutInputField() {
        if (lastPanelRect == null) return;
        int pad = 10;
        int x = lastPanelRect.x + pad + 4;
        int y = lastPanelRect.y + lastPanelRect.height - 34 + entranceOffsetY;
        int w = lastPanelRect.width - pad*2 - 8;
        int h = 24;
        chatInput.setBounds(x, y, w, h);
    }

    private void submitChat() {
        if (chatInput == null) return;
        String txt = chatInput.getText();
        if (txt == null) txt = "";
        txt = txt.trim();
        if (txt.isEmpty()) return;
        chatHistory.add(new ChatLine(Role.USER, txt));
        chatInput.setText("");
        repaint();
        try { SimEventBus.get().emitChatMessage(txt); } catch (Throwable ignored) {}
    }

    private int maxWidth(java.util.List<String> rows, Graphics2D g2) {
        int m = 0; for (String r : rows) { m = Math.max(m, g2.getFontMetrics().stringWidth(r)); } return m;
    }

    private void capChatHistory() {
        // keep last 50 lines to avoid unbounded growth
        int max = 50;
        if (chatHistory.size() > max) {
            int drop = chatHistory.size() - max;
            for (int i = 0; i < drop; i++) chatHistory.remove(0);
            // adjust streaming index
            if (streamingAssistantIndex >= 0) streamingAssistantIndex = Math.max(0, streamingAssistantIndex - drop);
        }
    }

    // (duplicate speak handlers removed; see unified overrides earlier in file)
}
