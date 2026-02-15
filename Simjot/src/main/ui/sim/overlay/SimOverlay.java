/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.sim.overlay;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javax.swing.*;
import main.core.sim.api.SimEventBus;
import main.core.service.SettingsStore;
import main.ui.app.JournalApp;
import main.ui.theme.aero.AeroPainters;
import main.ui.util.AccentColorUtil;

/**
 * Minimal overlay for Sim. Added to the JFrame layered pane.
 */
public class SimOverlay extends JComponent implements SimEventBus.Listener {
    private static final String DEFAULT_GREETING = "Hi, I’m Sim.";
    private static final int PANEL_ARC = 16;
    private static final int PANEL_HEADER_H = 34;
    private static final DateTimeFormatter SESSION_TIME_FMT =
            DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault());

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
    private float orbEntryPhase = 0f;  // reveal progression
    private float orbExitPhase = 0f;   // hide progression
    private double[] orbT = new double[ORB_COUNT]; // 0..1 progress per orb
    private float panelT = 0f; // 0..1 expansion of text panel
    private float panelOpacity = 0f;
    private long lastAnimNanos = 0L;

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
    private static final int ORB_RADIUS = 17; // circle radius
    private static final int ORB_RING_RADIUS = 44; // distance from center
    private static final int HEART_CLEARANCE = 10; // extra spacing between heart and orbs
    private static final int MAGI_BRAIN_COUNT = 3;
    private static final int MAGI_RING_RADIUS = ORB_RING_RADIUS + HEART_CLEARANCE + ORB_RADIUS + 24;
    private static final int MAGI_BRAIN_RADIUS = 11;
    private static final int ORBIT_LAYOUT_PADDING = 28;
    private static final int ORBIT_LAYOUT_WIDTH = 172;
    private static final float ORB_PHASE_STAGGER = 0.58f; // overlap between adjacent orb transitions
    private static final float ORB_ENTRY_PHASE_SPEED = 0.066f;
    private static final float ORB_EXIT_PHASE_SPEED = 0.068f;
    private final float[] orbHighlight = new float[ORB_COUNT];       // current 0..1
    private final float[] orbHighlightTarget = new float[ORB_COUNT]; // target 0..1


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
    // Special "thinking" motion when Sim is preparing guidance
    private boolean guidanceThinking = false;
    private double thinkingPhase = 0.0;
    private float thinkingPulse = 0f;
    private float thinkingOrbScale = 1f;
    private double magiSpinAngle = 0.0;
    private boolean magiDeliberating = false;
    private String guidanceConsensus = "";
    private String[] guidanceOutcomeEmotions = new String[0];
    private ConsensusType guidanceConsensusType = ConsensusType.NONE;
    private final MagiDecision[] magiConsensusDecisions = new MagiDecision[MAGI_BRAIN_COUNT];
    private final String[] magiConsensusCodes = new String[MAGI_BRAIN_COUNT];
    private OutcomeLabel outcomeLabel;

    private enum ConsensusType {
        NONE,
        UNANIMOUS,
        MAJORITY,
        CONDITIONAL,
        DEADLOCK,
        INFORMATIONAL
    }

    private enum MagiDecision {
        NEUTRAL,
        AGREE,
        DISAGREE,
        CONDITIONAL,
        DEADLOCK,
        INFORMATIONAL
    }

    // New: scrollable chat components
    private final ChatTranscriptModel transcript = new ChatTranscriptModel();
    private final ChatViewPanel chatView = new ChatViewPanel(transcript);
    // Only show message/panel when user has explicitly invoked or we're in chat
    private boolean userInvokedActive = false;
    // Panel actions
    private JButton chatsButton;
    private JButton hideButton;
    // In-memory archived chat sessions
    private final java.util.List<ChatSession> archivedSessions = new java.util.ArrayList<>();
    private final JournalApp app;

    public SimOverlay() {
        this(null);
    }

    public SimOverlay(JournalApp app) {
        this.app = app;
        setOpaque(false);
        setVisible(true);
        setLayout(null); // we'll position children manually
        clearGuidanceOutcomeVisuals();
        // Listen for speak events
        try { SimEventBus.get().addListener(this); } catch (Throwable ignored) {}

        // Integrate scrollable chat view but keep it hidden until chatMode
        chatView.setOpaque(false);
        chatView.getScrollPane().setVisible(false);
        add(chatView.getScrollPane());
        ensureActionButtons();
        ensureOutcomeLabel();

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
            long now = System.nanoTime();
            double dt = 0.016; // ~60 FPS fallback
            if (lastAnimNanos != 0L) {
                dt = (now - lastAnimNanos) / 1_000_000_000.0;
                if (dt <= 0 || dt > 0.10) dt = 0.016;
            }
            lastAnimNanos = now;
            double tickScale = dt * 60.0;
            boolean needsRepaint = false;
            // entrance animation: ease-out fade and slide up
            if (animatingIn && entranceAlpha < 1f) {
                entranceT = Math.min(1f, entranceT + (float)(0.08f * tickScale));
                entranceAlpha = easeOutCubic(entranceT);
                entranceOffsetY = (int)Math.round((1.0 - entranceAlpha) * 12);
                if (entranceT >= 1f) { animatingIn = false; }
                needsRepaint = true;
            }

            // Entry staged sequence: reveal orbs one-by-one.
            if (entryInProgress) {
                float prevPhase = orbEntryPhase;
                orbEntryPhase = Math.min(orbPhaseEnd(), orbEntryPhase + (float) (ORB_ENTRY_PHASE_SPEED * tickScale));
                applyOrbPhase(orbEntryPhase);
                if (Math.abs(orbEntryPhase - prevPhase) > 0.0001f) {
                    needsRepaint = true;
                }
                if (orbEntryPhase >= orbPhaseEnd() - 0.0001f) {
                    // Floating buttons are shown after the orb reveal completes.
                    entryInProgress = false;
                }
            }

            // Dispose sequence: hide orbs in reverse with staggered easing.
            if (disposeInProgress) {
                float prevPhase = orbExitPhase;
                orbExitPhase = Math.max(0f, orbExitPhase - (float) (ORB_EXIT_PHASE_SPEED * tickScale));
                applyOrbPhase(orbExitPhase);
                if (Math.abs(orbExitPhase - prevPhase) > 0.0001f) {
                    needsRepaint = true;
                }
                if (orbExitPhase <= 0.0001f) {
                    disposeInProgress = false;
                    // Keep overlay visible if beacon is enabled; otherwise hide
                    if (!alwaysVisibleBeacon) setVisible(false);
                }
            }
            if (guidanceThinking) {
                thinkingPhase += 0.122 * tickScale;
                float wave = (float) ((Math.sin(thinkingPhase) + 1.0) * 0.5);
                thinkingPulse = 0.38f + 0.62f * wave;
                // Keep all orbs gently lit while thinking for a "processing" feel.
                for (int i = 0; i < ORB_COUNT; i++) {
                    float localWave = (float) ((Math.sin(thinkingPhase + i * 1.1) + 1.0) * 0.5);
                    float target = 0.18f + 0.28f * localWave;
                    orbHighlightTarget[i] = Math.max(orbHighlightTarget[i], target);
                }
                needsRepaint = true;
            } else {
                thinkingPulse = Math.max(0f, thinkingPulse - (float) (0.055f * tickScale));
            }
            float orbTargetScale = guidanceThinking ? 0.84f : 1f;
            float orbScaleFollow = Math.min(1f, (float) (0.075f * tickScale));
            thinkingOrbScale += (orbTargetScale - thinkingOrbScale) * orbScaleFollow;

            // Heart pulse animation (cosine ease-in-out + small spring at peaks)
            double heartStep = 0.05 * tickScale;
            if (guidanceThinking) {
                heartStep *= (1.30 + 0.42 * thinkingPulse);
            }
            heartPhase += heartStep;
            double eased = (1 - Math.cos(heartPhase)) * 0.5; // 0..1
            boolean justPeaked = (eased > 0.98 && lastBeatValue <= 0.98);
            if (justPeaked) {
                heartSpring = 0.08f; // overshoot amount
            }
            lastBeatValue = eased;
            if (heartSpring > 0f) {
                heartSpring *= Math.pow(0.90f, tickScale); // damping
                if (heartSpring < 0.001f) heartSpring = 0f;
            }
            float baseAmp = guidanceThinking ? (0.076f + 0.020f * thinkingPulse) : 0.06f;
            heartScale = 1f + baseAmp * (float)(eased * 2 - 1) + heartSpring;

            // continuous spin for orbs
            double speed = Math.PI / 138; // smoother idle cadence
            if (guidanceThinking) {
                double beatPhase = heartPhase / (Math.PI * 2.0);
                beatPhase -= Math.floor(beatPhase);
                // Parabolic fast-slow-fast profile through the beat cycle.
                double parabola = 4.0 * (beatPhase - 0.5) * (beatPhase - 0.5); // 0 at mid, 1 at edges
                double fastSlowFast = 0.70 + 1.95 * parabola;
                speed *= fastSlowFast * (1.05 + 0.55 * thinkingPulse);
            }
            spinAngle += speed * tickScale;
            if (spinAngle > Math.PI * 2) spinAngle -= Math.PI * 2;
            double magiSpeed = speed * 0.72;
            if (guidanceThinking) {
                magiSpeed *= 0.95 + 0.35 * thinkingPulse;
            }
            magiSpinAngle -= magiSpeed * tickScale;
            if (magiSpinAngle < -Math.PI * 2) magiSpinAngle += Math.PI * 2;
            if (magiSpinAngle > Math.PI * 2) magiSpinAngle -= Math.PI * 2;

            // Emotion orb highlight easing (driven by onEmotionTagged events)
            for (int i = 0; i < ORB_COUNT; i++) {
                float target = orbHighlightTarget[i];
                if (target > 0f) {
                    target = Math.max(0f, target - (float)(0.0022f * tickScale)); // ~7-8s fade
                    orbHighlightTarget[i] = target;
                }
                float current = orbHighlight[i];
                float next;
                if (current < target) {
                    next = Math.min(target, current + (float)(0.085f * tickScale));
                } else {
                    next = Math.max(target, current - (float)(0.04f * tickScale));
                }
                if (Math.abs(next - current) > 0.0005f) {
                    orbHighlight[i] = next;
                    needsRepaint = true;
                }
            }

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
    public void onCardSwitched(String cardId) {
        SwingUtilities.invokeLater(() -> {
            refreshActionButtonsState();
            repaint();
        });
    }

    @Override
    public void onSpeak(String message) {
        if (chatMode) {
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
        if (chatMode) {
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
        if (!chatMode) return;
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
    public void onEmotionTagged(String entryId, String emotion, double intensity) {
        final String label = emotion == null ? "" : emotion.trim();
        if (label.isEmpty()) return;
        final float v = (float) Math.max(0.20, Math.min(1.0, intensity / 100.0));
        final int idx = emotionToOrbIndex(label);
        if (idx < 0 || idx >= ORB_COUNT) return;
        SwingUtilities.invokeLater(() -> {
            orbHighlightTarget[idx] = Math.max(orbHighlightTarget[idx], v);
            repaint();
        });
    }

    @Override
    public void onGuidanceRequested(String text) {
        SwingUtilities.invokeLater(() -> {
            if (!userInvokedActive || chatMode) return;
            magiDeliberating = true;
            clearGuidanceOutcomeVisuals();
            setGuidanceThinking(true);
            message = "Sim is thinking…";
            refreshActionButtonsState();
            repaint();
        });
    }

    @Override
    public void onGuidanceOutcome(String consensus, String[] emotions) {
        onGuidanceOutcome(consensus, emotions, null);
    }

    @Override
    public void onGuidanceOutcome(String consensus, String[] emotions, String[] brainStatuses) {
        SwingUtilities.invokeLater(() -> {
            guidanceConsensus = consensus == null ? "" : consensus.trim();
            applyGuidanceConsensusVisuals(guidanceConsensus, brainStatuses);
            guidanceOutcomeEmotions = sanitizeEmotionLabels(emotions);
            emphasizeOutcomeEmotions(guidanceOutcomeEmotions);
            updateOutcomeLabelText();
            repaint();
        });
    }

    @Override
    public void onGuidanceProduced(String text) {
        SwingUtilities.invokeLater(() -> {
            magiDeliberating = false;
            setGuidanceThinking(false);
            if (!userInvokedActive || chatMode) return;
            String out = text == null ? "" : text.strip();
            if (guidanceConsensusType == ConsensusType.NONE && !out.isEmpty()) {
                guidanceConsensus = "INFORMATIONAL (情報)";
                applyGuidanceConsensusVisuals(guidanceConsensus, null);
            }
            message = out.isEmpty() ? "I could not generate guidance this time." : "Guidance added to your entry.";
            updateOutcomeLabelText();
            refreshActionButtonsState();
            repaint();
        });
    }

    @Override
    public void onQuitRequested() {
        // Gracefully end chat (if any) and dispose the overlay with animation
        SwingUtilities.invokeLater(() -> {
            magiDeliberating = false;
            clearGuidanceOutcomeVisuals();
            setGuidanceThinking(false);
            try { archiveCurrentSessionIfNotEmpty(); } catch (Throwable ignored) {}
            try { endChatModeInternal(true); } catch (Throwable ignored) {}
            userInvokedActive = false;
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
        // Wider to fit orbit + floating controls with extra breathing room.
        return new Dimension(462, 252);
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

        // No outer frame/background — keep canvas transparent.
        // Layout keeps orbs on the left and floating controls on the right.
        int padding = ORBIT_LAYOUT_PADDING;
        int leftW = ORBIT_LAYOUT_WIDTH; // space for orbs
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

        // Soft ambient glow to blend the orbit with background and reduce harsh edges.
        {
            Graphics2D ga = (Graphics2D) g2.create();
            float visibility = orbVisibilityProgress();
            float auraPulse = guidanceThinking ? (0.64f + 0.36f * thinkingPulse) : 0.78f;
            float auraAlpha = clamp01((0.36f + 0.58f * visibility) * auraPulse);
            int auraRadius = MAGI_RING_RADIUS + 36;
            RadialGradientPaint aura = new RadialGradientPaint(
                    new Point2D.Float(centerX, centerY), auraRadius,
                    new float[]{0f, 0.58f, 1f},
                    new Color[]{
                            new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), (int) (70 * auraAlpha)),
                            new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), (int) (32 * auraAlpha)),
                            new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 0)
                    }
            );
            Paint oldPaint = ga.getPaint();
            ga.setPaint(aura);
            ga.fillOval(centerX - auraRadius, centerY - auraRadius, auraRadius * 2, auraRadius * 2);
            ga.setPaint(oldPaint);
            ga.dispose();
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
            int idleBob = (int) Math.round((1.6 * Math.sin(spinAngle * 1.35 + i * 0.95)) * (0.42 + 0.58 * eased));
            oy += idleBob;
            if (guidanceThinking) {
                double nx = Math.cos(theta); // -1..1
                double arc = 1.0 - (nx * nx); // parabola shape across arc
                int drift = (int) Math.round((2.7 * Math.sin(thinkingPhase + i * 1.25) + 3.8 * (arc - 0.5)) * eased);
                oy += drift;
            }
            float glow = clamp01(orbHighlight[i]);
            int rrBase = ORB_RADIUS + Math.round(3f * glow);
            float orbScale = thinkingOrbScale;
            if (guidanceThinking) {
                float breathe = (float) ((Math.sin(thinkingPhase * 1.25 + i * 0.95) + 1.0) * 0.5);
                orbScale *= 0.96f + 0.09f * breathe;
            }
            int rr = Math.max(9, Math.round(rrBase * orbScale));
            int d = rr * 2;
            Color ec = getEmotionColor(i);

            // Ambient halo (always on, stronger for highlighted emotions).
            {
                Paint oldPaint = g2.getPaint();
                int haloR = rr + 8;
                RadialGradientPaint halo = new RadialGradientPaint(
                        new Point2D.Float(ox, oy), haloR,
                        new float[]{0f, 0.72f, 1f},
                        new Color[]{
                                new Color(ec.getRed(), ec.getGreen(), ec.getBlue(), (int) ((18 + 40 * glow) * p)),
                                new Color(ec.getRed(), ec.getGreen(), ec.getBlue(), (int) ((8 + 18 * glow) * p)),
                                new Color(ec.getRed(), ec.getGreen(), ec.getBlue(), 0)
                        }
                );
                g2.setPaint(halo);
                g2.fillOval(ox - haloR, oy - haloR, haloR * 2, haloR * 2);
                g2.setPaint(oldPaint);
            }

            // Soft shadow
            g2.setColor(new Color(0, 0, 0, (int)(38 * p)));
            g2.fillOval(ox - rr, oy - rr + 2, d, d);

            // Guidance emotion highlight halo
            if (glow > 0.01f) {
                Paint oldPaint = g2.getPaint();
                RadialGradientPaint halo = new RadialGradientPaint(
                        new Point2D.Float(ox, oy), rr + 11f,
                        new float[]{0f, 0.55f, 1f},
                        new Color[]{
                                new Color(ec.getRed(), ec.getGreen(), ec.getBlue(), (int)(178 * glow * p)),
                                new Color(ec.getRed(), ec.getGreen(), ec.getBlue(), (int)(76 * glow * p)),
                                new Color(ec.getRed(), ec.getGreen(), ec.getBlue(), 0)
                        }
                );
                g2.setPaint(halo);
                g2.fillOval(ox - rr - 11, oy - rr - 11, d + 22, d + 22);
                g2.setPaint(oldPaint);
            }

            // Clean color orb with subtle depth
            Color top = AccentColorUtil.lighten(ec, Math.min(0.64f, 0.36f + 0.22f * glow));
            Color bottom = AccentColorUtil.darken(ec, Math.max(0.16f, 0.30f - 0.08f * glow));
            Paint oldPaint = g2.getPaint();
            RadialGradientPaint body = new RadialGradientPaint(
                    new Point2D.Float(ox - rr * 0.22f, oy - rr * 0.36f), rr * 1.08f,
                    new float[]{0f, 0.68f, 1f},
                    new Color[]{
                            new Color(top.getRed(), top.getGreen(), top.getBlue(), 246),
                            new Color(ec.getRed(), ec.getGreen(), ec.getBlue(), 226),
                            new Color(bottom.getRed(), bottom.getGreen(), bottom.getBlue(), 226)
                    }
            );
            g2.setPaint(body);
            g2.fillOval(ox - rr, oy - rr, d, d);
            g2.setPaint(oldPaint);

            // Glossy hot spots
            g2.setColor(new Color(255, 255, 255, (int)(108 * p)));
            g2.fillOval(ox - rr / 3, oy - rr / 2, Math.max(4, rr / 2), Math.max(3, rr / 3));
            g2.setColor(new Color(255, 255, 255, (int)(58 * p)));
            g2.fillOval(ox - rr / 6, oy - rr / 7, Math.max(3, rr / 3), Math.max(2, rr / 4));

            // Edge ring
            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(new Color(255, 255, 255, (int)(132 * p)));
            g2.drawOval(ox - rr, oy - rr, d, d);
            g2.setColor(new Color(0, 0, 0, (int)(82 * p)));
            g2.drawOval(ox - rr, oy - rr, d, d);
        }

        paintMagiBrainLayer(g2, centerX, centerY, accent);

        // Overlay menu is now floating buttons only (no text panel/chat panel).
        lastPanelRect = null;
        chatView.getScrollPane().setVisible(false);
        layoutActionButtons();
        layoutOutcomeLabel();

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

    private void setGuidanceThinking(boolean thinking) {
        guidanceThinking = thinking;
        if (thinking) {
            thinkingPhase = 0.0;
            thinkingPulse = Math.max(thinkingPulse, 0.35f);
        } else {
            magiDeliberating = false;
        }
    }

    // Beacon is shown when overlay is meant to be idle (no panel/orbs) but visible
    private boolean isBeaconIdle() {
        if (!alwaysVisibleBeacon) return false;
        // Idle only when Sim is not actively user-invoked.
        boolean activeAnimations = animatingIn || entryInProgress || disposeInProgress;
        return !activeAnimations && !userInvokedActive && !chatMode;
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
        // Open in compact menu mode first (floating actions).
        chatMode = false;
        magiDeliberating = false;
        clearGuidanceOutcomeVisuals();
        setGuidanceThinking(false);
        try { transcript.clear(); } catch (Throwable ignored) {}
        typingInProgress = false;
        streamingAssistantIndex = -1;
        initialChatSeedDone = true;
        message = "Choose an action";
        setActionButtonsVisible(false);
        refreshActionButtonsState();
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
        setGuidanceThinking(false);
        try { archiveCurrentSessionIfNotEmpty(); } catch (Throwable ignored) {}
        try { SimEventBus.get().emitChatEnded(); } catch (Throwable ignored) {}
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

    private static float easeInOutCubic(float t) {
        t = clamp01(t);
        if (t < 0.5f) return 4f * t * t * t;
        float u = -2f * t + 2f;
        return 1f - (u * u * u) / 2f;
    }

    private static float inverseEaseInOutCubic(float y) {
        y = clamp01(y);
        if (y < 0.5f) {
            return (float) Math.cbrt(y / 4f);
        }
        return 1f - (float) (Math.cbrt(2f * (1f - y)) / 2f);
    }

    private float orbPhaseEnd() {
        return 1f + (ORB_COUNT - 1) * ORB_PHASE_STAGGER;
    }

    private void applyOrbPhase(float phase) {
        for (int i = 0; i < ORB_COUNT; i++) {
            float raw = clamp01(phase - i * ORB_PHASE_STAGGER);
            orbT[i] = easeInOutCubic(raw);
        }
    }

    private float estimateCurrentOrbPhase() {
        float phase = 0f;
        for (int i = 0; i < ORB_COUNT; i++) {
            float raw = inverseEaseInOutCubic((float) orbT[i]);
            phase = Math.max(phase, raw + i * ORB_PHASE_STAGGER);
        }
        return clamp01(phase / Math.max(0.0001f, orbPhaseEnd())) * orbPhaseEnd();
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

    private float orbVisibilityProgress() {
        float max = 0f;
        for (int i = 0; i < ORB_COUNT; i++) {
            max = Math.max(max, (float) orbT[i]);
        }
        return clamp01(max);
    }

    // Emotion palette mapping for orb accents and symbol tints
    private Color getEmotionColor(int idx) {
        switch (idx % ORB_COUNT) {
            case 0: return new Color(255, 194, 64);     // joy
            case 1: return new Color(156, 162, 176);    // neutral
            case 2: return new Color(101, 167, 255);    // low/sad
            case 3: return new Color(244, 111, 111);    // anger/stress
            default: return new Color(87, 210, 180);    // calm
        }
    }

    private void clearGuidanceOutcomeVisuals() {
        guidanceConsensus = "";
        guidanceOutcomeEmotions = new String[0];
        guidanceConsensusType = ConsensusType.NONE;
        for (int i = 0; i < MAGI_BRAIN_COUNT; i++) {
            magiConsensusDecisions[i] = MagiDecision.NEUTRAL;
            magiConsensusCodes[i] = "";
        }
        updateOutcomeLabelText();
    }

    private void applyGuidanceConsensusVisuals(String rawConsensus, String[] brainStatuses) {
        guidanceConsensusType = parseConsensusType(rawConsensus);
        boolean appliedExplicitVotes = applyBrainStatusesToMagiOrbs(brainStatuses);
        if (appliedExplicitVotes) {
            ConsensusType inferred = inferConsensusTypeFromMagiVotes();
            if (inferred != ConsensusType.NONE) guidanceConsensusType = inferred;
            return;
        }
        MagiDecision[] pattern = fallbackConsensusPattern(guidanceConsensusType);
        for (int i = 0; i < MAGI_BRAIN_COUNT; i++) {
            MagiDecision d = pattern[i % pattern.length];
            magiConsensusDecisions[i] = d;
            magiConsensusCodes[i] = decisionCode(d);
        }
    }

    private MagiDecision[] fallbackConsensusPattern(ConsensusType type) {
        return switch (type) {
            case UNANIMOUS -> new MagiDecision[]{MagiDecision.AGREE, MagiDecision.AGREE, MagiDecision.AGREE};
            case MAJORITY -> new MagiDecision[]{MagiDecision.AGREE, MagiDecision.AGREE, MagiDecision.DISAGREE};
            case CONDITIONAL -> new MagiDecision[]{MagiDecision.CONDITIONAL, MagiDecision.CONDITIONAL, MagiDecision.CONDITIONAL};
            case DEADLOCK -> new MagiDecision[]{MagiDecision.AGREE, MagiDecision.DISAGREE, MagiDecision.CONDITIONAL};
            case INFORMATIONAL -> new MagiDecision[]{MagiDecision.INFORMATIONAL, MagiDecision.INFORMATIONAL, MagiDecision.INFORMATIONAL};
            default -> new MagiDecision[]{MagiDecision.NEUTRAL, MagiDecision.NEUTRAL, MagiDecision.NEUTRAL};
        };
    }

    private boolean applyBrainStatusesToMagiOrbs(String[] brainStatuses) {
        if (brainStatuses == null || brainStatuses.length == 0) return false;
        boolean any = false;
        for (int i = 0; i < MAGI_BRAIN_COUNT; i++) {
            String raw = i < brainStatuses.length ? brainStatuses[i] : null;
            MagiDecision d = decisionFromBrainStatus(raw);
            magiConsensusDecisions[i] = d;
            magiConsensusCodes[i] = decisionCode(d);
            if (d != MagiDecision.NEUTRAL) any = true;
        }
        return any;
    }

    private MagiDecision decisionFromBrainStatus(String status) {
        String s = status == null ? "" : status.trim().toLowerCase(java.util.Locale.ROOT);
        if (s.isEmpty()) return MagiDecision.NEUTRAL;
        if (s.contains("yes") || s.contains("approve") || s.contains("accept")) return MagiDecision.AGREE;
        if (s.contains("no") || s.contains("reject")) return MagiDecision.DISAGREE;
        if (s.contains("conditional") || s.contains("condition")) return MagiDecision.CONDITIONAL;
        if (s.contains("deadlock") || s.contains("stalemate") || s.contains("tie")) return MagiDecision.DEADLOCK;
        if (s.contains("inform") || s.contains("info")) return MagiDecision.INFORMATIONAL;
        return MagiDecision.NEUTRAL;
    }

    private ConsensusType inferConsensusTypeFromMagiVotes() {
        int agree = 0;
        int disagree = 0;
        int conditional = 0;
        int informational = 0;
        int deadlock = 0;
        for (int i = 0; i < MAGI_BRAIN_COUNT; i++) {
            MagiDecision d = magiConsensusDecisions[i];
            if (d == null) continue;
            switch (d) {
                case AGREE -> agree++;
                case DISAGREE -> disagree++;
                case CONDITIONAL -> conditional++;
                case INFORMATIONAL -> informational++;
                case DEADLOCK -> deadlock++;
                default -> {}
            }
        }
        if (agree == MAGI_BRAIN_COUNT || disagree == MAGI_BRAIN_COUNT) return ConsensusType.UNANIMOUS;
        if (conditional == MAGI_BRAIN_COUNT) return ConsensusType.CONDITIONAL;
        if (informational == MAGI_BRAIN_COUNT) return ConsensusType.INFORMATIONAL;
        if (agree >= 2 || disagree >= 2) return ConsensusType.MAJORITY;
        if (conditional >= 2 || (conditional >= 1 && (agree >= 1 || disagree >= 1))) return ConsensusType.CONDITIONAL;
        if (deadlock >= 2 || (deadlock >= 1 && agree >= 1 && disagree >= 1)) return ConsensusType.DEADLOCK;
        if (informational >= 2) return ConsensusType.INFORMATIONAL;
        return ConsensusType.NONE;
    }

    private ConsensusType parseConsensusType(String rawConsensus) {
        String raw = rawConsensus == null ? "" : rawConsensus.trim();
        if (raw.isEmpty()) return ConsensusType.NONE;
        String c = raw.toLowerCase(java.util.Locale.ROOT);
        if (c.contains("accepted") || c.contains("accept") || c.contains("approve") || c.contains("agreed")) {
            return ConsensusType.UNANIMOUS;
        }
        if (c.contains("unanim") || raw.contains("合意")) return ConsensusType.UNANIMOUS;
        if (c.contains("2-1") || c.contains("2/1") || c.contains("split")) return ConsensusType.MAJORITY;
        if (c.contains("majorit")) return ConsensusType.MAJORITY;
        if (c.contains("depends") || c.contains("unless")) return ConsensusType.CONDITIONAL;
        if (c.contains("conditional") || raw.contains("状態")) return ConsensusType.CONDITIONAL;
        if (c.contains("stalemate") || c.contains("tie") || c.contains("hung")) return ConsensusType.DEADLOCK;
        if (c.contains("deadlock")) return ConsensusType.DEADLOCK;
        if (c.contains("inform") || c.contains("info") || raw.contains("情報")) return ConsensusType.INFORMATIONAL;
        return ConsensusType.INFORMATIONAL;
    }

    private String decisionCode(MagiDecision d) {
        if (d == null) return "";
        return switch (d) {
            case AGREE -> "AG";
            case DISAGREE -> "NO";
            case CONDITIONAL -> "IF";
            case DEADLOCK -> "DL";
            case INFORMATIONAL -> "IN";
            default -> "";
        };
    }

    private String consensusTitle(ConsensusType type) {
        if (type == null) return "";
        return switch (type) {
            case UNANIMOUS -> "UNANIMOUS (合意)";
            case MAJORITY -> "MAJORITY";
            case CONDITIONAL -> "CONDITIONAL (状態)";
            case DEADLOCK -> "DEADLOCK";
            case INFORMATIONAL -> "INFORMATIONAL (情報)";
            default -> "";
        };
    }

    private Color getMagiBrainColor(MagiDecision decision) {
        if (decision == null) decision = MagiDecision.NEUTRAL;
        return switch (decision) {
            case AGREE -> new Color(112, 214, 150);
            case DISAGREE -> new Color(241, 112, 112);
            case CONDITIONAL -> new Color(247, 198, 104);
            case DEADLOCK -> new Color(176, 144, 229);
            case INFORMATIONAL -> new Color(128, 190, 255);
            default -> new Color(250, 252, 255);
        };
    }

    private void paintMagiBrainLayer(Graphics2D g2, int centerX, int centerY, Color accent) {
        float visibility = orbVisibilityProgress();
        if (visibility <= 0.02f) return;
        boolean hasConsensus = guidanceConsensusType != ConsensusType.NONE;
        float idleWave = (float) ((Math.sin(magiSpinAngle * 1.25) + 1.0) * 0.5);
        float pulse = magiDeliberating || guidanceThinking
                ? clamp01(0.45f + 0.55f * thinkingPulse)
                : clamp01(0.25f + 0.35f * idleWave);
        float alpha = clamp01(visibility * (magiDeliberating || guidanceThinking
                ? (0.55f + 0.45f * pulse)
                : (0.44f + 0.26f * pulse)));
        if (hasConsensus && !magiDeliberating && !guidanceThinking) {
            alpha = Math.max(alpha, 0.9f);
        }

        int ringRadius = MAGI_RING_RADIUS + (magiDeliberating || guidanceThinking
                ? (int) Math.round(2.8 * Math.sin(thinkingPhase * 0.8))
                : 0);
        float ringAlpha = alpha * (0.62f + 0.26f * pulse);
        Graphics2D go = (Graphics2D) g2.create();
        go.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Ambient ring aura
        int auraRadius = ringRadius + 16;
        RadialGradientPaint ringAura = new RadialGradientPaint(
                new Point2D.Float(centerX, centerY), auraRadius,
                new float[]{0f, 0.72f, 1f},
                new Color[]{
                        new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), (int) (24 * alpha)),
                        new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), (int) (48 * alpha)),
                        new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 0)
                }
        );
        Paint oldPaint = go.getPaint();
        go.setPaint(ringAura);
        go.fillOval(centerX - auraRadius, centerY - auraRadius, auraRadius * 2, auraRadius * 2);
        go.setPaint(oldPaint);

        // Outer circular layer
        go.setStroke(new BasicStroke(1.45f));
        go.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), (int) (146 * ringAlpha)));
        go.drawOval(centerX - ringRadius, centerY - ringRadius, ringRadius * 2, ringRadius * 2);
        go.setColor(new Color(255, 255, 255, (int) (88 * ringAlpha)));
        go.drawOval(centerX - ringRadius + 1, centerY - ringRadius + 1, ringRadius * 2 - 2, ringRadius * 2 - 2);
        int sweep = (int) Math.round(Math.toDegrees(-magiSpinAngle * 1.2));
        go.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        go.setColor(new Color(255, 255, 255, (int) (146 * ringAlpha)));
        go.drawArc(centerX - ringRadius, centerY - ringRadius, ringRadius * 2, ringRadius * 2, sweep, 64);
        go.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        go.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), (int) (150 * ringAlpha)));
        go.drawArc(centerX - ringRadius + 1, centerY - ringRadius + 1, ringRadius * 2 - 2, ringRadius * 2 - 2, sweep + 182, 50);

        int[] xs = new int[MAGI_BRAIN_COUNT];
        int[] ys = new int[MAGI_BRAIN_COUNT];
        for (int i = 0; i < MAGI_BRAIN_COUNT; i++) {
            double theta = magiSpinAngle + (Math.PI * 2.0 * i / MAGI_BRAIN_COUNT) - (Math.PI / 2.0);
            xs[i] = (int) Math.round(centerX + ringRadius * Math.cos(theta));
            ys[i] = (int) Math.round(centerY + ringRadius * Math.sin(theta));
        }

        // Triangle links between MAGI brains
        Path2D triangle = new Path2D.Double();
        triangle.moveTo(xs[0], ys[0]);
        triangle.lineTo(xs[1], ys[1]);
        triangle.lineTo(xs[2], ys[2]);
        triangle.closePath();
        go.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        go.setColor(new Color(182, 214, 255, (int) (174 * alpha)));
        go.draw(triangle);
        go.setColor(new Color(255, 255, 255, (int) (65 * alpha)));
        go.setStroke(new BasicStroke(1.0f));
        go.draw(triangle);

        String consensusLabel = consensusTitle(guidanceConsensusType);
        if (!consensusLabel.isBlank()) {
            Font old = go.getFont();
            go.setFont(old.deriveFont(Font.BOLD, 9.4f));
            FontMetrics fm = go.getFontMetrics();
            int tw = fm.stringWidth(consensusLabel);
            int tx = centerX - tw / 2;
            int ty = centerY - ringRadius + Math.max(14, MAGI_BRAIN_RADIUS + 4);
            go.setColor(new Color(16, 24, 32, (int) (185 * alpha)));
            go.drawString(consensusLabel, tx, ty);
            go.setFont(old);
        }

        for (int i = 0; i < MAGI_BRAIN_COUNT; i++) {
            MagiDecision decision = magiConsensusDecisions[i] == null ? MagiDecision.NEUTRAL : magiConsensusDecisions[i];
            Color c = getMagiBrainColor(decision);
            double phaseRef = (magiDeliberating || guidanceThinking) ? thinkingPhase : (magiSpinAngle * 1.6);
            float localWave = (float) ((Math.sin(phaseRef * 1.38 + i * 2.1) + 1.0) * 0.5);
            int rr = Math.max(6, Math.round(MAGI_BRAIN_RADIUS * (0.92f + 0.22f * localWave)));
            int d = rr * 2;
            int x = xs[i];
            int y = ys[i];

            go.setColor(new Color(0, 0, 0, (int) (50 * alpha)));
            go.fillOval(x - rr, y - rr + 2, d, d);

            float topLift = hasConsensus ? 0.18f : 0.42f;
            float bottomDrop = hasConsensus ? 0.36f : 0.24f;
            Color top = AccentColorUtil.lighten(c, topLift);
            Color bottom = AccentColorUtil.darken(c, bottomDrop);
            Paint old = go.getPaint();
            RadialGradientPaint body = new RadialGradientPaint(
                    new Point2D.Float(x - rr * 0.18f, y - rr * 0.28f), rr * 1.03f,
                    new float[]{0f, 0.68f, 1f},
                    new Color[]{
                            new Color(top.getRed(), top.getGreen(), top.getBlue(), (int) ((hasConsensus ? 248 : 235) * alpha)),
                            new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) ((hasConsensus ? 242 : 220) * alpha)),
                            new Color(bottom.getRed(), bottom.getGreen(), bottom.getBlue(), (int) ((hasConsensus ? 238 : 220) * alpha))
                    }
            );
            go.setPaint(body);
            go.fillOval(x - rr, y - rr, d, d);
            go.setPaint(old);

            go.setColor(new Color(255, 255, 255, (int) (130 * alpha)));
            go.setStroke(new BasicStroke(1.25f));
            go.drawOval(x - rr, y - rr, d, d);
            go.setColor(new Color(0, 0, 0, (int) (80 * alpha)));
            go.drawOval(x - rr, y - rr, d, d);

            String code = magiConsensusCodes[i];
            if (code != null && !code.isBlank()) {
                Font oldFont = go.getFont();
                go.setFont(oldFont.deriveFont(Font.BOLD, 8.5f));
                FontMetrics fm = go.getFontMetrics();
                int tw = fm.stringWidth(code);
                int tx = x - tw / 2;
                int ty = y + (fm.getAscent() - fm.getDescent()) / 2;
                go.setColor(new Color(28, 34, 42, (int) (225 * alpha)));
                go.drawString(code, tx, ty);
                go.setFont(oldFont);
            }
        }

        go.dispose();
    }

    private int emotionToOrbIndex(String emotionLabel) {
        String e = emotionLabel == null ? "" : emotionLabel.trim().toLowerCase(java.util.Locale.ROOT);
        if (e.isEmpty()) return 1;
        if (e.contains("joy") || e.contains("happy") || e.contains("grat") || e.contains("excit")
                || e.contains("hope") || e.contains("love") || e.contains("relief")) return 0;
        if (e.contains("calm") || e.contains("peace") || e.contains("ground") || e.contains("content")
                || e.contains("stable")) return 4;
        if (e.contains("anger") || e.contains("angry") || e.contains("mad") || e.contains("frustrat")
                || e.contains("irritat") || e.contains("annoy") || e.contains("rage")
                || e.contains("stress") || e.contains("overwhelm")) return 3;
        if (e.contains("sad") || e.contains("down") || e.contains("low") || e.contains("lonely")
                || e.contains("grief") || e.contains("anx") || e.contains("worr") || e.contains("fear")
                || e.contains("tired") || e.contains("exhaust")) return 2;
        return 1;
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

    private void paintFrostedPanel(Graphics2D g2, Rectangle panelRect, float alpha) {
        if (panelRect == null || panelRect.width <= 0 || panelRect.height <= 0) return;
        float a = clamp01(alpha);
        Graphics2D gp = (Graphics2D) g2.create();
        gp.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gp.setComposite(AlphaComposite.SrcOver.derive(Math.max(0.01f, a)));

        int x = panelRect.x;
        int y = panelRect.y;
        int w = panelRect.width;
        int h = panelRect.height;

        // Outer shadow
        gp.setColor(new Color(0, 0, 0, 48));
        gp.fillRoundRect(x + 1, y + 3, w, h, PANEL_ARC, PANEL_ARC);

        Shape panelShape = new RoundRectangle2D.Float(x, y, w, h, PANEL_ARC, PANEL_ARC);
        Shape oldClip = gp.getClip();
        gp.setClip(panelShape);

        GradientPaint base = new GradientPaint(
                x, y, new Color(255, 255, 255, 168),
                x, y + h, new Color(224, 229, 236, 124)
        );
        gp.setPaint(base);
        gp.fill(panelShape);

        GradientPaint frost = new GradientPaint(
                x, y, new Color(255, 255, 255, 118),
                x, y + Math.max(1, (int) (h * 0.58f)), new Color(255, 255, 255, 24)
        );
        gp.setPaint(frost);
        gp.fill(panelShape);

        GradientPaint depth = new GradientPaint(
                x, y + (int) (h * 0.42f), new Color(0, 0, 0, 8),
                x, y + h, new Color(0, 0, 0, 44)
        );
        gp.setPaint(depth);
        gp.fill(panelShape);

        int separatorY = y + PANEL_HEADER_H;
        gp.setColor(new Color(255, 255, 255, 92));
        gp.drawLine(x + 10, separatorY, x + w - 10, separatorY);
        gp.setColor(new Color(0, 0, 0, 30));
        gp.drawLine(x + 10, separatorY + 1, x + w - 10, separatorY + 1);

        gp.setClip(oldClip);
        gp.setColor(new Color(255, 255, 255, 96));
        gp.drawRoundRect(x, y, w - 1, h - 1, PANEL_ARC, PANEL_ARC);
        gp.setColor(new Color(0, 0, 0, 55));
        gp.drawRoundRect(x, y, w - 1, h - 1, PANEL_ARC, PANEL_ARC);
        AeroPainters.paintInnerShadow(gp, panelRect, PANEL_ARC, new Color(0, 0, 0), 6, 44);
        gp.dispose();
    }

    private void layoutOverlayChildren() {
        layoutActionButtons();
        layoutOutcomeLabel();
        layoutInputField();
    }

    private void layoutActionButtons() {
        if (chatsButton == null || hideButton == null) return;
        refreshActionButtonsState();
        int padding = ORBIT_LAYOUT_PADDING;
        int leftW = ORBIT_LAYOUT_WIDTH;
        int rightX = padding + leftW + 10;
        int availableW = Math.max(120, getWidth() - rightX - padding);
        int btnY = (getHeight() / 2) - 14 + entranceOffsetY;
        int guidanceW = 98;
        int hideW = 66;
        int gap = 10;

        int total = guidanceW + gap + hideW;
        int startX = rightX + Math.max(0, (availableW - total) / 2);
        chatsButton.setBounds(startX, btnY, guidanceW, 28);
        hideButton.setBounds(startX + guidanceW + gap, btnY, hideW, 28);

        boolean visible = userInvokedActive && !disposeInProgress && !entryInProgress;
        setActionButtonsVisible(visible);
    }

    private void setActionButtonsVisible(boolean visible) {
        if (chatsButton != null) chatsButton.setVisible(visible);
        if (hideButton != null) hideButton.setVisible(visible);
    }

    private void ensureOutcomeLabel() {
        if (outcomeLabel != null) return;
        outcomeLabel = new OutcomeLabel();
        outcomeLabel.setVisible(false);
        add(outcomeLabel);
    }

    private void layoutOutcomeLabel() {
        if (outcomeLabel != null) outcomeLabel.setVisible(false);
    }

    private void updateOutcomeLabelText() {
        if (outcomeLabel == null) return;
        outcomeLabel.setText("");
        outcomeLabel.setVisible(false);
    }

    private void ensureActionButtons() {
        if (chatsButton != null) return;
        chatsButton = createActionButton("Guidance", this::onSecondaryAction);
        hideButton = createActionButton("Hide", this::deactivateFromHeart);
        add(chatsButton);
        add(hideButton);
        refreshActionButtonsState();
        setActionButtonsVisible(false);
        ensureOutcomeLabel();
    }

    private void onSecondaryAction() {
        requestGuidanceFromMenu();
    }

    private void requestGuidanceFromMenu() {
        boolean available = isGuidanceAvailableInContext();
        if (!available) {
            magiDeliberating = false;
            clearGuidanceOutcomeVisuals();
            setGuidanceThinking(false);
            message = "Open a journal entry to use Guidance.";
            repaint();
            return;
        }
        boolean requested = false;
        try {
            requested = app != null && app.requestSimGuidanceForCurrentCard();
        } catch (Throwable ignored) {}
        if (requested) {
            magiDeliberating = true;
            clearGuidanceOutcomeVisuals();
            setGuidanceThinking(true);
            message = "Sim is thinking…";
        } else {
            magiDeliberating = false;
            message = "Guidance is already running for this entry.";
        }
        refreshActionButtonsState();
        repaint();
    }

    private boolean isGuidanceAvailableInContext() {
        try {
            return app != null && app.isSimGuidanceAvailableForCurrentCard();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void refreshActionButtonsState() {
        if (chatsButton == null) return;
        chatsButton.setText("Guidance");
        boolean guidanceEnabled = isGuidanceAvailableInContext() && !guidanceThinking;
        chatsButton.setEnabled(guidanceEnabled);
        chatsButton.setToolTipText(guidanceThinking
                ? "Sim is currently generating guidance"
                : guidanceEnabled
                ? "Generate guidance from the current journal entry"
                : "Available only in a journal entry editor");
        if (hideButton != null) {
            hideButton.setEnabled(true);
            hideButton.setToolTipText("Hide Sim overlay");
        }
    }

    private JButton createActionButton(String text, Runnable action) {
        JButton b = new GlassActionButton(text);
        b.addActionListener(e -> {
            try { action.run(); } catch (Throwable ignored) {}
        });
        b.setVisible(false);
        return b;
    }

    private String[] sanitizeEmotionLabels(String[] labels) {
        if (labels == null || labels.length == 0) return new String[0];
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (String l : labels) {
            if (l == null) continue;
            String e = l.trim().toLowerCase(java.util.Locale.ROOT);
            if (e.isEmpty()) continue;
            if (e.contains("joy") || e.contains("happy") || e.contains("grat") || e.contains("hope")) e = "joy";
            else if (e.contains("calm") || e.contains("peace") || e.contains("ground") || e.contains("content")) e = "calm";
            else if (e.contains("anger") || e.contains("mad") || e.contains("frustr") || e.contains("stress") || e.contains("overwhelm")) e = "anger";
            else if (e.contains("sad") || e.contains("anx") || e.contains("fear") || e.contains("worr") || e.contains("lonely") || e.contains("grief")) e = "sad";
            else if (e.contains("neutral") || e.contains("fine") || e.contains("ok")) e = "neutral";
            out.add(e);
            if (out.size() >= 3) break;
        }
        return out.toArray(new String[0]);
    }

    private void emphasizeOutcomeEmotions(String[] emotions) {
        if (emotions == null) return;
        int rank = 0;
        for (String e : emotions) {
            int idx = emotionToOrbIndex(e);
            if (idx < 0 || idx >= ORB_COUNT) continue;
            float boost = Math.max(0.60f, 0.96f - rank * 0.12f);
            orbHighlightTarget[idx] = Math.max(orbHighlightTarget[idx], boost);
            rank++;
            if (rank >= 3) break;
        }
    }

    private final class GlassActionButton extends JButton {
        private GlassActionButton(String text) {
            super(text);
            setOpaque(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(getFont().deriveFont(Font.BOLD, 11.5f));
            setForeground(new Color(26, 30, 38));
            setMargin(new Insets(0, 10, 0, 10));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int arc = 14;
            boolean enabled = isEnabled();
            boolean hover = getModel().isRollover();
            boolean pressed = getModel().isPressed();
            Color accent = resolveAccent();

            // Soft depth shadow.
            g2.setColor(new Color(0, 0, 0, enabled ? 34 : 20));
            g2.fillRoundRect(0, 1, w - 1, h - 1, arc, arc);

            Color baseTop = !enabled ? new Color(242, 244, 247, 92)
                    : pressed ? new Color(212, 228, 246, 194)
                    : hover ? new Color(236, 246, 255, 186)
                    : new Color(249, 251, 253, 144);
            Color baseBottom = !enabled ? new Color(224, 228, 233, 84)
                    : pressed ? new Color(189, 210, 236, 182)
                    : hover ? new Color(204, 224, 245, 164)
                    : new Color(222, 227, 234, 126);
            GradientPaint gp = new GradientPaint(0, 0, baseTop, 0, h, baseBottom);
            g2.setPaint(gp);
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);

            GradientPaint sheen = new GradientPaint(
                    0, 0, new Color(255, 255, 255, enabled ? 84 : 44),
                    0, h / 2, new Color(255, 255, 255, 0)
            );
            g2.setPaint(sheen);
            g2.fillRoundRect(1, 1, w - 2, Math.max(2, h / 2), arc - 1, arc - 1);

            Color border = !enabled ? new Color(255, 255, 255, 52)
                    : hover || pressed
                    ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), pressed ? 165 : 145)
                    : new Color(255, 255, 255, 88);
            g2.setColor(border);
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.setColor(new Color(0, 0, 0, 36));
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

            FontMetrics fm = g2.getFontMetrics(getFont());
            int tx = (w - fm.stringWidth(getText())) / 2;
            int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
            Color textColor = !enabled ? new Color(96, 100, 108)
                    : hover ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue())
                    : pressed ? new Color(20, 24, 30)
                    : new Color(34, 38, 44);
            g2.setColor(textColor);
            g2.drawString(getText(), tx, ty);
            g2.dispose();
        }
    }

    private final class OutcomeLabel extends JComponent {
        private String text = "";

        private OutcomeLabel() {
            setOpaque(false);
            Font base = UIManager.getFont("Label.font");
            if (base == null) base = new Font("Dialog", Font.PLAIN, 12);
            setFont(base.deriveFont(Font.PLAIN, 11f));
            setForeground(new Color(38, 44, 52));
            setToolTipText("MAGI guidance consensus and prominent emotions");
        }

        public void setText(String text) {
            this.text = text == null ? "" : text;
            repaint();
        }

        public String getText() {
            return text;
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (text == null || text.isBlank()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int arc = 12;
            GradientPaint gp = new GradientPaint(
                    0, 0, new Color(252, 254, 255, 164),
                    0, h, new Color(228, 234, 241, 132)
            );
            g2.setPaint(gp);
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.setColor(new Color(255, 255, 255, 105));
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.setColor(new Color(0, 0, 0, 48));
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

            FontMetrics fm = g2.getFontMetrics(getFont());
            int tx = Math.max(8, (w - fm.stringWidth(text)) / 2);
            int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.setColor(new Color(26, 32, 40));
            g2.drawString(text, tx, ty);
            g2.dispose();
        }
    }

    private void startEntrySequence() {
        entryInProgress = true;
        disposeInProgress = false;
        panelAppearing = false;
        magiDeliberating = false;
        clearGuidanceOutcomeVisuals();
        setGuidanceThinking(false);
        thinkingPulse = 0f;
        thinkingOrbScale = 1f;
        orbEntryPhase = 0f;
        orbExitPhase = orbPhaseEnd();
        panelT = 0f;
        panelOpacity = 0f;
        for (int i = 0; i < ORB_COUNT; i++) {
            orbT[i] = 0.0;
            orbHighlight[i] = 0f;
            orbHighlightTarget[i] = 0f;
        }
        setActionButtonsVisible(false);
    }

    private void startDisposeSequence() {
        disposeInProgress = true;
        entryInProgress = false;
        panelAppearing = false;
        magiDeliberating = false;
        clearGuidanceOutcomeVisuals();
        setGuidanceThinking(false);
        thinkingPulse = 0f;
        thinkingOrbScale = 1f;
        orbExitPhase = estimateCurrentOrbPhase();
        setActionButtonsVisible(false);
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
        ensureActionButtons();
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
        layoutOverlayChildren();
        chatView.scrollToBottom();
        revalidate(); repaint();
        // Entering chat should be user-driven only; do not auto-trigger follow-ups here to avoid interference
    }

    private void endChatMode() {
        // Emit end event and dispose panel with animation
        try { SimEventBus.get().emitChatEnded(); } catch (Throwable ignored) {}
        try { archiveCurrentSessionIfNotEmpty(); } catch (Throwable ignored) {}
        endChatModeInternal(true);
        userInvokedActive = false;
        startDisposeSequence();
    }

    private void endChatModeInternal(boolean removeInput) {
        chatMode = false;
        streamingAssistantIndex = -1;
        setActionButtonsVisible(false);
        if (removeInput) {
            if (chatInput != null) { try { remove(chatInput); } catch (Throwable ignored) {} chatInput = null; }
            if (inputHint != null) { try { remove(inputHint); } catch (Throwable ignored) {} inputHint = null; }
        }
    }

    private static final class ChatSession {
        final String title;
        final long savedAt;
        final java.util.List<ChatTranscriptModel.Entry> entries;
        ChatSession(String title, long savedAt, java.util.List<ChatTranscriptModel.Entry> entries) {
            this.title = title == null ? "Chat" : title;
            this.savedAt = savedAt;
            this.entries = entries == null ? java.util.List.of() : entries;
        }
    }

    private void startNewChatSession() {
        setGuidanceThinking(false);
        archiveCurrentSessionIfNotEmpty();
        try { SimEventBus.get().emitChatEnded(); } catch (Throwable ignored) {}
        try { transcript.clear(); } catch (Throwable ignored) {}
        chatHistory.clear();
        typingInProgress = false;
        streamingAssistantIndex = -1;
        initialChatSeedDone = true; // do not re-seed greeting on manual new chat
        if (chatInput != null) {
            chatInput.setText("");
            SwingUtilities.invokeLater(() -> chatInput.requestFocusInWindow());
        }
        chatView.scrollToBottom();
        repaint();
    }

    private void showArchivedChatsMenu() {
        if (chatsButton == null) return;
        JPopupMenu menu = new JPopupMenu();
        if (archivedSessions.isEmpty()) {
            JMenuItem empty = new JMenuItem("No previous chats yet");
            empty.setEnabled(false);
            menu.add(empty);
        } else {
            for (int i = archivedSessions.size() - 1; i >= 0; i--) {
                ChatSession s = archivedSessions.get(i);
                String label = SESSION_TIME_FMT.format(java.time.Instant.ofEpochMilli(s.savedAt)) + "  " + s.title;
                JMenuItem item = new JMenuItem(label);
                final ChatSession selected = s;
                item.addActionListener(e -> restoreArchivedSession(selected));
                menu.add(item);
            }
        }
        menu.show(chatsButton, 0, chatsButton.getHeight() + 4);
    }

    private void restoreArchivedSession(ChatSession session) {
        if (session == null || session.entries == null) return;
        archiveCurrentSessionIfNotEmpty();
        try { transcript.setEntries(session.entries); } catch (Throwable ignored) {}
        typingInProgress = false;
        streamingAssistantIndex = -1;
        initialChatSeedDone = true;
        chatView.scrollToBottom();
        if (chatInput != null) SwingUtilities.invokeLater(() -> chatInput.requestFocusInWindow());
        repaint();
    }

    private void archiveCurrentSessionIfNotEmpty() {
        java.util.List<ChatTranscriptModel.Entry> snap;
        try {
            snap = transcript.snapshot();
        } catch (Throwable ignored) {
            return;
        }
        if (snap == null || snap.isEmpty()) return;
        java.util.List<ChatTranscriptModel.Entry> copy = new java.util.ArrayList<>();
        for (ChatTranscriptModel.Entry e : snap) {
            if (e == null) continue;
            copy.add(new ChatTranscriptModel.Entry(e.role, e.text, e.ts));
        }
        if (copy.isEmpty()) return;
        if (!archivedSessions.isEmpty()) {
            ChatSession last = archivedSessions.get(archivedSessions.size() - 1);
            if (sameTranscript(last.entries, copy)) return;
        }
        String title = makeSessionTitle(copy);
        archivedSessions.add(new ChatSession(title, System.currentTimeMillis(), copy));
        int maxSessions = 30;
        if (archivedSessions.size() > maxSessions) {
            archivedSessions.remove(0);
        }
    }

    private boolean sameTranscript(java.util.List<ChatTranscriptModel.Entry> a, java.util.List<ChatTranscriptModel.Entry> b) {
        if (a == b) return true;
        if (a == null || b == null || a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            ChatTranscriptModel.Entry ea = a.get(i);
            ChatTranscriptModel.Entry eb = b.get(i);
            if (ea == eb) continue;
            if (ea == null || eb == null) return false;
            if (ea.role != eb.role) return false;
            if (!java.util.Objects.equals(ea.text, eb.text)) return false;
        }
        return true;
    }

    private String makeSessionTitle(java.util.List<ChatTranscriptModel.Entry> entries) {
        if (entries == null || entries.isEmpty()) return "Chat";
        for (ChatTranscriptModel.Entry e : entries) {
            if (e == null || e.text == null) continue;
            String t = e.text.strip();
            if (t.isEmpty()) continue;
            int max = 28;
            return t.length() <= max ? t : (t.substring(0, max - 1) + "…");
        }
        return "Chat";
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
        if (chatInput == null) return;
        if (lastPanelRect == null) {
            chatInput.setVisible(false);
            if (inputHint != null) inputHint.setVisible(false);
            return;
        }
        int pad = 10;
        int x = lastPanelRect.x + pad + 4;
        int h = 48; // taller for 2-line comfort
        int y = lastPanelRect.y + lastPanelRect.height - h - 8 + entranceOffsetY;
        int w = Math.max(80, lastPanelRect.width - pad*2 - 8);
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
