/*
 * SIMJOT - PROPRIETARY
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 * 
 * This source code is licensed under the Simjot Source-Available Personal Use License.
 * You may view and study this code for personal, non-commercial use.
 * Distribution, commercial use, and derivative works are strictly prohibited.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.sim.overlay;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import javax.swing.*;
import main.core.sim.api.SimEventBus;
import main.core.service.SettingsStore;
import main.ui.theme.aero.AeroPainters;
import main.ui.util.AccentColorUtil;

/**
 * Minimal overlay for Sim. Added to the JFrame layered pane.
 */
public class SimOverlay extends JComponent implements SimEventBus.Listener {
    private static final String DEFAULT_GREETING = "Hi, I’m Sim.";
    private String message = DEFAULT_GREETING;
    private boolean greetedOnce = false; // suppress repeating default greeting
    private Point dragAnchor = null;

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
    // Heart beacon: always-visible dimmed heart and hit area for calls
    private boolean alwaysVisibleBeacon = true; // keep overlay visible to show the dimmed heart
    private Shape lastHeartHitShape = null;     // transformed heart shape for hit-testing

    // Orb/emotion configuration
    private static final int ORB_COUNT = 5;
    private static final int ORB_RADIUS = 16; // circle radius
    private static final int ORB_RING_RADIUS = 42; // distance from center
    private static final int HEART_CLEARANCE = 8; // extra spacing between heart and orbs


    // Phase 2: inline chat mode
    private boolean chatMode = false;
    private java.util.List<ChatLine> chatHistory = new java.util.ArrayList<>();
    private JTextArea chatInput;
    private JLabel inputHint;
    // Close buttons removed; dismiss via typing 'bye'
    private int streamingAssistantIndex = -1; // index of assistant line being streamed
    private Rectangle lastPanelRect = null; // updated during paint for layout
    // Streaming typing indicator state
    private boolean typingInProgress = false;
    private int typingTick = 0; // advances in anim timer to animate dots

    // New: scrollable chat components
    private final ChatTranscriptModel transcript = new ChatTranscriptModel();
    private final ChatViewPanel chatView = new ChatViewPanel(transcript);
    // Only show message/panel when user has explicitly invoked or we're in chat
    private boolean userInvokedActive = false;

    public SimOverlay() {
        setOpaque(false);
        setVisible(true);
        setLayout(null); // we'll position children manually
        // Listen for speak events
        try { SimEventBus.get().addListener(this); } catch (Throwable ignored) {}

        // Integrate scrollable chat view but keep it hidden until chatMode
        chatView.setOpaque(false);
        chatView.getScrollPane().setVisible(false);
        add(chatView.getScrollPane());

        // Drag to move
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                // Close buttons removed; dismiss via heart toggle or typing 'bye'
                dragAnchor = e.getPoint();
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }
            @Override public void mouseReleased(MouseEvent e) {
                dragAnchor = null;
                setCursor(Cursor.getDefaultCursor());
            }
            @Override public void mouseClicked(MouseEvent e) {
                // Double-click on the heart to toggle: invoke when idle; deactivate when active
                if (e.getClickCount() == 2) {
                    if (lastHeartHitShape != null && lastHeartHitShape.contains(e.getPoint())) {
                        if (isBeaconIdle()) {
                            invokeFromHeart();
                        } else {
                            deactivateFromHeart();
                        }
                    }
                }
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
                // CTA removed
                // Heart beacon hover
                if (!hand && isBeaconIdle()) {
                    if (lastHeartHitShape != null && lastHeartHitShape.contains(e.getPoint())) {
                        hand = true;
                    }
                }
                setCursor(hand ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
                if (chatMode) repaint();
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
                    // Keep overlay visible if beacon is enabled; otherwise hide
                    if (!alwaysVisibleBeacon) setVisible(false);
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

    // One-shot suppression for Sim's default greeting after heart-invocation
    private boolean suppressDefaultGreetingOnce = false;
    private long lastHeartInvokeMs = 0L;

    public void showMessage(String msg) {
        String m = msg == null ? "" : msg;
        // Normalize apostrophes for comparison (straight/curly)
        String norm = m.replace('\'', '’');
        // If default greeting already shown once, suppress repeats
        if (DEFAULT_GREETING.equals(norm)) {
            if (greetedOnce) {
                return; // no-op to avoid repetitive greeting on toggles
            } else {
                greetedOnce = true;
            }
        }

        this.message = m;
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
        if (chatMode || userInvokedActive) {
            final String t = message == null ? "" : message;
            // If we're suppressing the first assistant turn after invocation, or the content
            // matches the default greeting shortly after invoke, drop tokens.
            long now = System.currentTimeMillis();
            String norm = t.replace('\'', '’').trim();
            boolean isDefaultGreeting = !norm.isEmpty() && (norm.equals(DEFAULT_GREETING) || norm.startsWith(DEFAULT_GREETING));
            boolean recentInvoke = (now - lastHeartInvokeMs) < 5000L; // 5s window
            if (suppressDefaultGreetingOnce || (isDefaultGreeting && recentInvoke)) {
                greetedOnce = true;
                suppressDefaultGreetingOnce = false; // ensure subsequent assistant turns are not suppressed
                return;
            }
            SwingUtilities.invokeLater(() -> {
                try { System.out.println("[SimOverlay] chat onSpeak append len=" + t.length()); } catch (Throwable ignored) {}
                // Route to transcript model (scrollable chat)
                transcript.appendAssistantTokens(t);
                // rely on anim timer for repaint to reduce per-token repaints
            });
        } else {
            // Ignore passive speaks until user explicitly invokes
        }
    }

    @Override
    public void onSpeakStart() {
        if (chatMode || userInvokedActive) {
            if (suppressDefaultGreetingOnce) {
                // Do not create a streaming turn for the greeting we will suppress
                typingInProgress = false;
                return;
            }
            SwingUtilities.invokeLater(() -> {
                // Start a streaming assistant turn in the transcript
                transcript.beginAssistantTurn();
                streamingAssistantIndex = -1; // transcript manages streaming
                typingInProgress = true;
                capChatHistory();
                repaint();
            });
        } else {
            // Ignore passive start until user explicitly invokes
        }
    }

    @Override
    public void onSpeakEnd() {
        if (suppressDefaultGreetingOnce) {
            // Consume and clear suppression without emitting a turn
            suppressDefaultGreetingOnce = false;
            typingInProgress = false;
            return;
        }
        typingInProgress = false;
        // Close current assistant turn in transcript if any
        try { transcript.endAssistantTurn(); } catch (Throwable ignored) {}
    }

    @Override
    public void onQuitRequested() {
        // Gracefully end chat (if any) and dispose the overlay with animation
        SwingUtilities.invokeLater(() -> {
            try { endChatModeInternal(true); } catch (Throwable ignored) {}
            startDisposeSequence();
        });
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
        return new Dimension(420, 240);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        // Resolve accent once per paint (cheap). Falls back to default Aero blue.
        Color accent = resolveAccent();

        // Apply entrance fade and vertical offset
        Composite oldComp = g2.getComposite();
        float a = Math.max(0.85f, Math.min(1f, entranceAlpha));
        g2.setComposite(AlphaComposite.SrcOver.derive(a));
        g2.translate(0, entranceOffsetY);

        // No outer frame/background — keep canvas transparent behind orbs and panel


        // Layout: left area for orbs, right area for text panel
        int padding = 14;
        int leftW = 140; // space for orbs
        int rightX = padding + leftW + 10;
        int rightW = w - rightX - padding;
        int centerX = padding + leftW / 2;
        int centerY = h / 2 + 6; // slightly lower to balance header

        // Idle beacon mode: draw only a dimmed heart and return
        if (isBeaconIdle()) {
            // Build transformed heart path for hit-testing (account entrance offset)
            Shape heartBase = createHeartShape();
            java.awt.geom.AffineTransform at = new java.awt.geom.AffineTransform();
            at.translate(centerX, centerY + entranceOffsetY);
            at.scale(Math.max(0.9, heartScale * 0.98), Math.max(0.9, heartScale * 0.98)); // very subtle pulse
            lastHeartHitShape = at.createTransformedShape(heartBase);

            // Dim heart appearance
            Graphics2D gh = (Graphics2D) g2.create();
            Composite oldC = gh.getComposite();
            gh.setComposite(AlphaComposite.SrcOver.derive(0.35f));
            gh.translate(centerX, centerY);
            gh.scale(heartScale * 0.98, heartScale * 0.98);
            Shape heart = heartBase;
            Rectangle hb = heart.getBounds();
            // Shadow
            Graphics2D gShadow = (Graphics2D) gh.create();
            gShadow.translate(0, 3 + entranceOffsetY);
            gShadow.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f));
            gShadow.setColor(new Color(0,0,0,50));
            gShadow.fill(heart);
            gShadow.dispose();
            // Fill
            float cxh = hb.x + hb.width * 0.45f;
            float cyh = hb.y + hb.height * 0.35f;
            float rh = Math.max(hb.width, hb.height) * 0.75f;
            Color lightIdle = AccentColorUtil.lighten(accent, 0.40f);
            Color darkIdle  = AccentColorUtil.darken(accent, 0.30f);
            RadialGradientPaint heartPaint = new RadialGradientPaint(
                    new Point2D.Float(cxh, cyh), rh,
                    new float[]{0f, 1f},
                    new Color[]{
                        new Color(lightIdle.getRed(), lightIdle.getGreen(), lightIdle.getBlue(), 150),
                        new Color(darkIdle.getRed(),  darkIdle.getGreen(),  darkIdle.getBlue(),  130)
                    }
            );
            gh.setPaint(heartPaint);
            gh.translate(0, entranceOffsetY);
            gh.fill(heart);
            // Outline (very subtle)
            gh.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            gh.setColor(new Color(255,255,255,20));
            gh.draw(heart);
            gh.setComposite(oldC);
            gh.dispose();

            // Restore and return (skip orbs/panel)
            g2.setComposite(oldComp);
            g2.dispose();
            return;
        }

        // Beating heart at center of spinning orbs (reused style, simplified)
        {
            Graphics2D gh = (Graphics2D) g2.create();
            gh.translate(centerX, centerY);
            gh.scale(heartScale, heartScale);
            Shape heart = createHeartShape();
            Rectangle hb = heart.getBounds();
            // Update heart hit shape for active state (account for entrance offset applied to g2 earlier)
            {
                java.awt.geom.AffineTransform at = new java.awt.geom.AffineTransform();
                at.translate(centerX, centerY + 0); // entrance offset already applied to g2
                at.scale(heartScale, heartScale);
                lastHeartHitShape = at.createTransformedShape(heart);
            }
            // Soft shadow
            Graphics2D gShadow = (Graphics2D) gh.create();
            gShadow.translate(0, 3);
            gShadow.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));
            gShadow.setColor(new Color(0,0,0,60));
            gShadow.fill(heart);
            gShadow.dispose();
            // Gradient fill (derived from accent)
            float cxh = hb.x + hb.width * 0.45f;
            float cyh = hb.y + hb.height * 0.35f;
            float rh = Math.max(hb.width, hb.height) * 0.75f;
            Color light = AccentColorUtil.lighten(accent, 0.45f);
            Color dark  = AccentColorUtil.darken(accent, 0.32f);
            RadialGradientPaint heartPaint = new RadialGradientPaint(
                    new Point2D.Float(cxh, cyh), rh,
                    new float[]{0f, 1f},
                    new Color[]{
                        new Color(light.getRed(), light.getGreen(), light.getBlue(), 210),
                        new Color(dark.getRed(),  dark.getGreen(),  dark.getBlue(),  190)
                    }
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
            // Subtle inner ring tinted by emotion color
            Color ec = getEmotionColor(i);
            if (ec != null) {
                Color ring = new Color(ec.getRed(), ec.getGreen(), ec.getBlue(), (int)(120 * p));
                g2.setColor(ring);
                int inset = 3;
                g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawOval(ox - ORB_RADIUS + inset, oy - ORB_RADIUS + inset, d - inset*2, d - inset*2);
            }
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

                // Close button removed; no inline chat exit icon

                // Store for input layout
                lastPanelRect = panelRect;
                // Defer layout to after paint to avoid re-entrancy
                if (chatMode) SwingUtilities.invokeLater(this::layoutInputField);
            }
        }

        // Position chat scroll view within the panel (above input line), visible only in chat mode
        if (lastPanelRect != null) {
            int pad = 10;
            int headerH = 20; // space for inline title/close row
            // Reserve space at bottom for input area + hint when in chat mode
            int inputAreaH = chatMode ? (48 /*input*/ + 16 /*hint/margin*/) : 0;
            int x = lastPanelRect.x + pad + 2;
            int y = lastPanelRect.y + headerH + 8 + entranceOffsetY;
            int wv = lastPanelRect.width - pad*2 - 4;
            int hv = Math.max(40, lastPanelRect.height - headerH - inputAreaH - 16);
            chatView.getScrollPane().setBounds(x, y, wv, hv);
            // Show scroll view only when panel expansion is complete to avoid clipped artifacts
            chatView.getScrollPane().setVisible(chatMode && isPanelOpen());
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
        }

        // Chat mode content (manual painted bubbles)
        // Disabled: replaced by scrollable ChatViewPanel
        if (panelT > 0f && chatMode && false) {
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

    private Color resolveAccent(){
        try {
            int rgb = SettingsStore.get().getMainMenuAccentRGB();
            if (rgb != Integer.MIN_VALUE) {
                return new Color(rgb, false);
            }
        } catch (Throwable ignored) {}
        return AccentColorUtil.defaultAccent();
    }

    // Beacon is shown when overlay is meant to be idle (no panel/orbs) but visible
    private boolean isBeaconIdle() {
        if (!alwaysVisibleBeacon) return false;
        // Idle if no entry/exit animations, no panel expansion, and not in chat
        boolean activeAnimations = animatingIn || entryInProgress || disposeInProgress;
        return !activeAnimations && panelT <= 0f && !chatMode;
    }

    // Start Sim entry sequence from a user double-click on the heart
    private void invokeFromHeart() {
        // Reset entrance animation and stage the full overlay sequence
        animatingIn = true;
        entranceAlpha = 0f;
        entranceT = 0f;
        entranceOffsetY = 12;
        startEntrySequence();
        if (!isVisible()) setVisible(true);
        // Enter chat mode BEFORE first repaint to avoid flashing old panel
        try { startChatMode(); } catch (Throwable ignored) {}
        // Suppress the default greeting this invocation to avoid repetition
        suppressDefaultGreetingOnce = true;
        lastHeartInvokeMs = System.currentTimeMillis();
        userInvokedActive = true;
        repaint();
        // Emit an event to upstream controller to tag invocation source.
        try {
            System.out.println("[SimOverlay] Heart invoked by user (double-click)");
            SimEventBus.get().emitUserInvoked(SimEventBus.InvocationSource.CALL_HEART);
        } catch (Throwable ignored) {}
    }

    // Deactivate Sim from a user double-click on the heart while active
    private void deactivateFromHeart() {
        // If in chat, end it first; then dispose sequence to collapse panel/orbs back to idle beacon
        try { endChatModeInternal(true); } catch (Throwable ignored) {}
        userInvokedActive = false;
        startDisposeSequence();
        repaint();
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

    // Consider panel "open" when expansion animation is effectively done
    private boolean isPanelOpen() {
        return panelT >= 0.98f;
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

    // Emotion palette mapping for orb accents and symbol tints
    private Color getEmotionColor(int idx) {
        switch (idx % ORB_COUNT) {
            case 0: return new Color(255, 191, 0);      // joy – amber
            case 1: return new Color(128, 128, 128);    // neutral – gray
            case 2: return new Color(64, 146, 235);     // low – blue
            case 3: return new Color(235, 87, 87);      // anger – red
            default: return new Color(42, 201, 164);    // calm – teal
        }
    }

    // Draw symbolic vector for each emotion, with improved shapes/fills
    // 0: joy (sunburst), 1: neutral (bar), 2: low (droplet), 3: anger (flame), 4: calm (wave)
    private void drawEmotionSymbol(Graphics2D g2, int cx, int cy, int r, int idx) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Stroke oldStroke = g2.getStroke();
        Color base = getEmotionColor(idx);
        switch (idx % ORB_COUNT) {
            case 0: { // joy – sunburst with gradient core and crisp rays
                int core = Math.max(3, (int)Math.round(r * 0.55));
                Paint old = g2.getPaint();
                RadialGradientPaint gp = new RadialGradientPaint(
                        new Point2D.Float(cx, cy), core,
                        new float[]{0f, 1f},
                        new Color[]{new Color(255, 230, 120, 240), new Color(255, 190, 0, 220)}
                );
                g2.setPaint(gp);
                g2.fill(new Ellipse2D.Double(cx - core/2.0, cy - core/2.0, core, core));
                g2.setPaint(old);
                g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 220));
                g2.setStroke(new BasicStroke(Math.max(1.6f, r/6f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int rays = 10;
                for (int i = 0; i < rays; i++) {
                    double a = (Math.PI * 2 * i) / rays;
                    int x1 = (int)Math.round(cx + (r*0.35) * Math.cos(a));
                    int y1 = (int)Math.round(cy + (r*0.35) * Math.sin(a));
                    int x2 = (int)Math.round(cx + (r*0.95) * Math.cos(a));
                    int y2 = (int)Math.round(cy + (r*0.95) * Math.sin(a));
                    g2.drawLine(x1, y1, x2, y2);
                }
                break;
            }
            case 1: { // neutral – balanced bar with soft ends
                g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 200));
                g2.setStroke(new BasicStroke(Math.max(2.0f, r/3.5f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int w = Math.max(6, (int)Math.round(r * 1.1));
                g2.drawLine(cx - w/2, cy, cx + w/2, cy);
                break;
            }
            case 2: { // low – filled droplet with highlight
                Path2D drop = new Path2D.Double();
                double top = cy - r*0.7;
                double bot = cy + r*0.7;
                drop.moveTo(cx, top);
                drop.curveTo(cx + r*0.45, cy - r*0.2, cx + r*0.45, cy + r*0.3, cx, bot);
                drop.curveTo(cx - r*0.45, cy + r*0.3, cx - r*0.45, cy - r*0.2, cx, top);
                Paint old = g2.getPaint();
                RadialGradientPaint water = new RadialGradientPaint(
                        new Point2D.Float(cx - r*0.15f, cy - r*0.1f), (float)(r*0.9),
                        new float[]{0f, 1f},
                        new Color[]{new Color(170, 210, 255, 220), new Color(base.getRed(), base.getGreen(), base.getBlue(), 220)}
                );
                g2.setPaint(water);
                g2.fill(drop);
                g2.setPaint(old);
                g2.setColor(new Color(0, 0, 0, 120));
                g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(drop);
                // small specular highlight
                g2.setColor(new Color(255,255,255,160));
                g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new Arc2D.Double(cx - r*0.35, cy - r*0.15, r*0.5, r*0.4, 200, 60, Arc2D.OPEN));
                break;
            }
            case 3: { // anger – flame with inner glow
                Path2D flame = new Path2D.Double();
                double topY = cy - r * 0.65;
                double bottomY = cy + r * 0.55;
                double leftX = cx - r * 0.40;
                double rightX = cx + r * 0.40;
                flame.moveTo(cx, topY);
                flame.curveTo(cx + r * 0.28, cy - r * 0.5, rightX, cy - r * 0.05, cx + r * 0.18, cy + r * 0.18);
                flame.curveTo(cx + r * 0.06, cy + r * 0.38, cx + r * 0.06, bottomY, cx, bottomY);
                flame.curveTo(cx - r * 0.06, bottomY, cx - r * 0.06, cy + r * 0.38, cx - r * 0.18, cy + r * 0.18);
                flame.curveTo(leftX,  cy - r * 0.05, cx - r * 0.28, cy - r * 0.5, cx, topY);
                Paint old = g2.getPaint();
                RadialGradientPaint hot = new RadialGradientPaint(
                        new Point2D.Float(cx, (float)(cy - r*0.1)), (float)(r*0.9),
                        new float[]{0f, 0.6f, 1f},
                        new Color[]{new Color(255, 200, 160, 230), new Color(245, 120, 90, 230), new Color(base.getRed(), base.getGreen(), base.getBlue(), 230)}
                );
                g2.setPaint(hot);
                g2.fill(flame);
                g2.setPaint(old);
                g2.setColor(new Color(120, 20, 20, 140));
                g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(flame);
                break;
            }
            default: { // calm – double wave
                g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 210));
                g2.setStroke(new BasicStroke(Math.max(1.6f, r/6f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D wave1 = new Path2D.Double();
                double amp = r * 0.28;
                double len = r * 1.5;
                double startX = cx - len/2.0;
                double step = len / 4.0;
                wave1.moveTo(startX, cy);
                wave1.curveTo(startX + step*0.5, cy - amp, startX + step*1.5, cy + amp, startX + step*2.0, cy);
                wave1.curveTo(startX + step*2.5, cy - amp, startX + step*3.5, cy + amp, startX + step*4.0, cy);
                g2.draw(wave1);
                g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 140));
                Path2D wave2 = new Path2D.Double();
                double offY = Math.max(1.0, r * 0.18);
                wave2.moveTo(startX, cy + offY);
                wave2.curveTo(startX + step*0.5, cy + offY - amp, startX + step*1.5, cy + offY + amp, startX + step*2.0, cy + offY);
                wave2.curveTo(startX + step*2.5, cy + offY - amp, startX + step*3.5, cy + offY + amp, startX + step*4.0, cy + offY);
                g2.draw(wave2);
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

    // Close hotspot removed

    // --- Chat helpers ---
    private enum Role { USER, ASSISTANT }
    private static final class ChatLine {
        final Role role; String text;
        ChatLine(Role r, String t){ this.role = r; this.text = t == null ? "" : t; }
    }

    private boolean initialChatSeedDone = false;
    private void startChatMode() {
        chatMode = true;
        streamingAssistantIndex = -1;
        // Seed with current assistant message only once per session, and do not
        // re-seed the default greeting after it has already been shown once.
        if (!initialChatSeedDone && message != null && !message.isBlank()) {
            String norm = message.replace('\'', '’').trim();
            boolean isDefault = norm.equals(DEFAULT_GREETING) || norm.startsWith(DEFAULT_GREETING);
            if (!(isDefault && greetedOnce)) {
                try {
                    transcript.beginAssistantTurn();
                    transcript.appendAssistantTokens(message);
                } catch (Throwable ignored) {}
                try { transcript.endAssistantTurn(); } catch (Throwable ignored) {}
                initialChatSeedDone = true;
            }
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
        if (removeInput) {
            if (chatInput != null) { try { remove(chatInput); } catch (Throwable ignored) {} chatInput = null; }
            if (inputHint != null) { try { remove(inputHint); } catch (Throwable ignored) {} inputHint = null; }
        }
    }

    private void ensureInputField() {
        if (chatInput != null) { layoutInputField(); return; }
        // Multiline input with placeholder and key bindings
        chatInput = new JTextArea() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setColor(new Color(120,120,120));
                    g2.setFont(getFont().deriveFont(Font.ITALIC));
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString("Ask me anything…", 8, fm.getAscent() + 2);
                    g2.dispose();
                }
            }
        };
        chatInput.setLineWrap(true);
        chatInput.setWrapStyleWord(true);
        chatInput.setOpaque(false);
        chatInput.setBorder(javax.swing.BorderFactory.createEmptyBorder(4,8,4,8));
        chatInput.setForeground(new Color(20,20,20));
        chatInput.setCaretColor(new Color(20,20,20));
        chatInput.setFont(getFont().deriveFont(Font.PLAIN, 14f));
        // Key bindings: Enter=send, Shift+Enter=newline, Esc=hide, Cmd/Ctrl+Enter=send
        javax.swing.InputMap im = chatInput.getInputMap(JComponent.WHEN_FOCUSED);
        javax.swing.ActionMap am = chatInput.getActionMap();
        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), "send");
        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, java.awt.event.KeyEvent.SHIFT_DOWN_MASK), "newline");
        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, java.awt.event.KeyEvent.META_DOWN_MASK), "send");
        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, java.awt.event.KeyEvent.CTRL_DOWN_MASK), "send");
        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "hide");
        am.put("send", new javax.swing.AbstractAction(){ public void actionPerformed(java.awt.event.ActionEvent e){ submitChat(); }});
        am.put("newline", new javax.swing.AbstractAction(){ public void actionPerformed(java.awt.event.ActionEvent e){ chatInput.append("\n"); }});
        am.put("hide", new javax.swing.AbstractAction(){ public void actionPerformed(java.awt.event.ActionEvent e){ deactivateFromHeart(); }});
        add(chatInput);
        // Hint label beneath input
        inputHint = new JLabel("Enter to send • Shift+Enter for newline • Esc to hide");
        inputHint.setForeground(new Color(90,90,90));
        inputHint.setFont(getFont().deriveFont(Font.PLAIN, 10f));
        add(inputHint);
        layoutInputField();
        SwingUtilities.invokeLater(() -> chatInput.requestFocusInWindow());
    }

    private void layoutInputField() {
        // During graceful quit, input may already be removed; avoid NPEs during layout ticks
        if (chatInput == null || lastPanelRect == null) return;
        int pad = 10;
        int x = lastPanelRect.x + pad + 4;
        int h = 48; // taller for 2-line comfort
        int y = lastPanelRect.y + lastPanelRect.height - h - 8 + entranceOffsetY;
        int w = lastPanelRect.width - pad*2 - 8;
        chatInput.setBounds(x, y, w, h);
        // Only reveal input once panel is fully expanded to avoid early clipped paint
        chatInput.setVisible(chatMode && isPanelOpen());
        if (inputHint != null) {
            inputHint.setBounds(x + 4, y + h - 2 + 14, Math.max(80, w - 8), 12);
            inputHint.setVisible(chatMode && isPanelOpen());
        }
    }

    private void submitChat() {
        if (chatInput == null) return;
        String txt = chatInput.getText();
        if (txt == null) txt = "";
        // Trim edges but preserve internal newlines
        txt = txt.strip();
        if (txt.isEmpty()) return;
        chatHistory.add(new ChatLine(Role.USER, txt));
        try { transcript.appendUser(txt); } catch (Throwable ignored) {}
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
