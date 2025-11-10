package main.ui.features.home;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.font.*;
import java.util.Random;
import javax.swing.*;

import main.infrastructure.monitoring.AppPerf;
import main.core.service.SettingsStore;
import main.ui.theme.aero.AeroTheme;
import main.ui.util.AccentColorUtil;

public class HeaderPanel extends JPanel {
    private float textAlpha = 0f;
    private float heartScale = 1f;
    private float ecgDraw = 0f;    // 0 = not drawn, 1 = fully drawn
    private float ecgOpacity = 0f; // current alpha of ECG line
    private boolean beatPeak = false; // tracks heart peak to trigger ECG
    private Timer fadeTimer, pulseTimer;
    // Quote rotation
    private Timer rotateTimer, quoteFadeTimer;
    // Inline Next (chevron) button hit area & hover state
    private Rectangle nextHit = new Rectangle();
    private boolean nextHover = false;
    // Animation state for eased pulse
    private double phase = 0;       // continuous time phase
    private double lastBeatValue = 0; // for peak detection on eased curve
    private float spring = 0f;      // small overshoot that decays after peak
    private String quote;
    private java.util.List<String> quotePool;
    private int quoteIndex = 0;
    private final Color accent;
    
    public HeaderPanel() {
        this(AeroTheme.AERO_BLUE);
    }

    public HeaderPanel(Color accent) {
        setPreferredSize(new Dimension(800, 120));
        setOpaque(false);
        setLayout(new BorderLayout());
        this.accent = (accent != null ? accent : AeroTheme.AERO_BLUE);
        // Random calming quotes (built-in).
        String[] quotes = {
            "Take a deep breath. You are enough.",
            "Every day is a fresh start.",
            "Peace begins with a smile.",
            "Keep calm and carry on.",
            "What's something you did that you're proud of today?",
            "Jot down your thoughts, calm them down.",
            "Inhale calm, exhale tension.",
            "One thing at a time.",
            "Let today be gentle.",
            "Slow is smooth, smooth is fast.",
            "You are safe here.",
            "Soft shoulders, unclenched jaw.",
            "Be kind to your mind.",
            "A small step is still a step.",
            "You have done enough for now.",
            "Rest is productive.",
            "Return to your breath.",
            "Let go of what you can’t control.",
            "Notice one good thing.",
            "May your thoughts be light today.",
            "It's okay to pause.",
            "You can begin again.",
            "Drink some water.",
            "Step outside for a moment.",
            "Loosen your shoulders.",
            "A gentle day is still a good day.",
            "Tiny progress is progress.",
            "Breathe in for 4, out for 6.",
            "Give yourself some grace.",
            "Let the hurry fall away.",
            "Your pace is perfect for you.",
            "Quiet moments count, too."
        };
        // Merge with custom quotes from settings
        quotePool = new java.util.ArrayList<>();
        java.util.Collections.addAll(quotePool, quotes);
        try {
            String[] custom = SettingsStore.get().getHeaderCustomQuotes();
            if (custom != null) {
                for (String q : custom) if (q != null && !q.trim().isEmpty()) quotePool.add(q.trim());
            }
        } catch (Throwable ignored) {}
        if (quotePool.isEmpty()) quotePool.add("Take a deep breath. You are enough.");
        quoteIndex = new Random().nextInt(quotePool.size());
        quote = quotePool.get(quoteIndex);

        // Mouse interactivity for inline chevron button
        MouseAdapter mx = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                boolean h = nextHit != null && nextHit.contains(e.getPoint());
                if (h != nextHover) {
                    nextHover = h;
                    setCursor(h ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
                    repaint();
                }
            }
            @Override public void mouseExited(MouseEvent e) {
                if (nextHover) { nextHover = false; setCursor(Cursor.getDefaultCursor()); repaint(); }
            }
            @Override public void mouseClicked(MouseEvent e) {
                if (nextHit != null && nextHit.contains(e.getPoint())) advanceQuote();
            }
        };
        addMouseMotionListener(mx);
        addMouseListener(mx);
    }
    
    public void startAnimation() {
        fadeTimer = new Timer(50, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                textAlpha += 0.05f;
                if (textAlpha >= 1f) {
                    textAlpha = 1f;
                    fadeTimer.stop();
                }
                repaint();
            }
        });
        fadeTimer.start();
        
        // Check if main menu animations are disabled before starting pulse animation
        if (!SettingsStore.get().isMainMenuAnimationsDisabled()) {
            pulseTimer = new Timer(AppPerf.getAnimationDelay(), new ActionListener() { // centralized FPS
                public void actionPerformed(ActionEvent e) {
                    // Advance phase and compute eased beat between 0..1
                    phase += 0.05; // speed
                    double eased = (1 - Math.cos(phase)) * 0.5; // cosine ease-in-out

                    // Small overshoot spring right after peak
                    boolean justPeaked = (eased > 0.98 && lastBeatValue <= 0.98);
                    if (justPeaked) {
                        beatPeak = true;
                        spring = 0.08f;     // overshoot amount
                        ecgDraw = 0f;       // restart ECG drawing
                        ecgOpacity = 1f;    // full opacity at start
                    }
                    if (eased < 0.5) {
                        beatPeak = false; // allow next peak
                    }
                    lastBeatValue = eased;

                    // Decay spring
                    if (spring > 0f) {
                        spring *= 0.90f; // damping
                        if (spring < 0.001f) spring = 0f;
                    }

                    // Base amplitude subtle for Aero
                    float baseAmp = 0.06f;
                    heartScale = 1f + baseAmp * (float)(eased * 2 - 1) + spring; // around 1.0 with small overshoot

                    // ECG drawing
                    if(ecgOpacity > 0f){
                        if(ecgDraw < 1f){
                            ecgDraw += 0.06f; // speed of drawing left→right
                            if(ecgDraw > 1f) ecgDraw = 1f;
                        } else {
                            ecgOpacity -= 0.02f; // fade out after fully drawn
                            if(ecgOpacity < 0f) ecgOpacity = 0f;
                        }
                    }
                    repaint();
                }
            });
            pulseTimer.start();
        }

        // Periodic quote rotation with soft fade
        int periodSec = 12;
        try { periodSec = SettingsStore.get().getHeaderQuoteRotationSeconds(); } catch (Throwable ignored) {}
        rotateTimer = new Timer(Math.max(5, periodSec) * 1000, e -> advanceQuote());
        rotateTimer.start();
    }

    private void advanceQuote(){
        if (quotePool == null || quotePool.isEmpty()) return;
        quoteIndex = (quoteIndex + 1) % quotePool.size();
        String next = quotePool.get(quoteIndex);
        startQuoteFadeTo(next);
    }

    private void startQuoteFadeTo(String next){
        if (fadeTimer != null && fadeTimer.isRunning()) fadeTimer.stop();
        if (quoteFadeTimer != null) quoteFadeTimer.stop();
        final String target = next;
        final int[] phaseRef = {0}; // 0 = fade out, 1 = fade in
        quoteFadeTimer = new Timer(40, ev -> {
            if (phaseRef[0] == 0){
                textAlpha -= 0.10f;
                if (textAlpha <= 0f){
                    textAlpha = 0f;
                    phaseRef[0] = 1;
                    quote = target;
                }
            } else {
                textAlpha += 0.10f;
                if (textAlpha >= 1f){
                    textAlpha = 1f;
                    quoteFadeTimer.stop();
                }
            }
            repaint();
        });
        quoteFadeTimer.start();
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        int width = getWidth();
        int height = getHeight();
        
        // Draw heart shape behind text (Aero gradient, shadow, outline, highlight, glow)
        AffineTransform old = g2.getTransform();
        g2.translate(width / 2, height / 2 - 10);
        g2.scale(heartScale, heartScale);
        Shape heart = createHeartShape();
        Rectangle bounds = heart.getBounds();

        // Soft glass shadow (faux blur via multiple translucent draws)
        Graphics2D gShadow = (Graphics2D) g2.create();
        gShadow.translate(0, 4);
        Color shadowColor = new Color(0, 0, 0, (int)(40 * textAlpha));
        for (int i = 0; i < 3; i++) {
            gShadow.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f - i*0.06f));
            gShadow.translate(0, 1);
            gShadow.setColor(shadowColor);
            gShadow.fill(heart);
        }
        gShadow.dispose();

        // Gradient fill (derived from accent)
        float cx = bounds.x + bounds.width * 0.45f;
        float cy = bounds.y + bounds.height * 0.35f;
        float radius = Math.max(bounds.width, bounds.height) * 0.75f;
        Color lightBase = AccentColorUtil.lighten(accent, 0.45f);
        Color darkBase  = AccentColorUtil.darken(accent, 0.30f);
        RadialGradientPaint heartPaint = new RadialGradientPaint(
            new Point2D.Float(cx, cy), radius,
            new float[]{0f, 1f},
            new Color[]{
                new Color(lightBase.getRed(), lightBase.getGreen(), lightBase.getBlue(), (int)(210 * textAlpha)),
                new Color(darkBase.getRed(),  darkBase.getGreen(),  darkBase.getBlue(),  (int)(190 * textAlpha))
            }
        );
        g2.setPaint(heartPaint);
        g2.fill(heart);

        // Soft outline
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(255, 255, 255, (int)(28 * textAlpha)));
        g2.draw(heart);

        // Inner top highlight (specular)
        Shape oldClip = g2.getClip();
        g2.setClip(heart);
        LinearGradientPaint highlight = new LinearGradientPaint(
            new Point2D.Float(bounds.x, bounds.y),
            new Point2D.Float(bounds.x, bounds.y + bounds.height * 0.35f),
            new float[]{0f, 1f},
            new Color[]{new Color(255,255,255,(int)(80*textAlpha)), new Color(255,255,255,0)}
        );
        g2.setPaint(highlight);
        g2.fill(new Rectangle2D.Float(bounds.x, bounds.y, bounds.width, (float)(bounds.height * 0.35)));
        g2.setClip(oldClip);

        // Beat-synced outer glow
        if (spring > 0f) {
            Graphics2D gGlow = (Graphics2D) g2.create();
            float glowAlpha = Math.min(0.35f, spring * 2.5f);
            gGlow.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, glowAlpha));
            float glowR = Math.max(bounds.width, bounds.height) * 0.95f;
            RadialGradientPaint glowPaint = new RadialGradientPaint(
                new Point2D.Float(bounds.x + bounds.width/2f, bounds.y + bounds.height/2f), glowR,
                new float[]{0f, 1f},
                new Color[]{
                    new Color(AccentColorUtil.lighten(accent, 0.40f).getRed(), AccentColorUtil.lighten(accent, 0.40f).getGreen(), AccentColorUtil.lighten(accent, 0.40f).getBlue(), 140),
                    new Color(AccentColorUtil.lighten(accent, 0.40f).getRed(), AccentColorUtil.lighten(accent, 0.40f).getGreen(), AccentColorUtil.lighten(accent, 0.40f).getBlue(), 0)
                }
            );
            gGlow.setPaint(glowPaint);
            gGlow.fill(new Ellipse2D.Float(bounds.x - glowR*0.15f, bounds.y - glowR*0.15f, bounds.width + glowR*0.3f, bounds.height + glowR*0.3f));
            gGlow.dispose();
        }
        g2.setTransform(old);
        
        // Draw ECG pulse line under heart (solid, with slight beat bump)
        if(ecgOpacity > 0f){
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ecgOpacity));
            float bump = 12f + spring * 160f; // amplitude bump at beat
            g2.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(255,255,255, 220));
            int ecgWidth = 150;
            int startX = width/2 - ecgWidth/2;
            int yBase = height/2 - 8;
            Path2D path = new Path2D.Double();
            path.moveTo(startX, yBase);
            path.lineTo(startX+20, yBase);
            path.lineTo(startX+35, yBase-bump);
            path.lineTo(startX+50, yBase+0.75*bump);
            path.lineTo(startX+70, yBase);
            path.lineTo(startX+ecgWidth, yBase);
            // Clip to progressive width
            Shape oldClip2 = g2.getClip();
            g2.setClip(startX, yBase-25, (int)(ecgWidth*ecgDraw), 50);
            g2.draw(path);
            // subtle crisp overlay
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(new Color(255,255,255, 160));
            g2.draw(path);
            g2.setClip(oldClip2);
            // reset composite for subsequent drawings
            g2.setComposite(AlphaComposite.SrcOver);
        }
        
        // Draw title text using vector glyphs with aero gradient & soft shadow
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        Font titleFont = new Font("Segoe UI", Font.BOLD, 36);
        String text = "Simjot";
        FontRenderContext frc = g2.getFontRenderContext();
        GlyphVector gv = titleFont.createGlyphVector(frc, text);
        Rectangle2D vb = gv.getVisualBounds();
        int x = (int) Math.round((width - vb.getWidth()) / 2.0);
        int y = height / 2;
        Shape textShape = gv.getOutline(x, y);

        // Apply overall alpha for fade-in
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, textAlpha));

        // Soft shadow
        Graphics2D gs = (Graphics2D) g2.create();
        gs.translate(1.5, 2.0);
        gs.setColor(new Color(0, 0, 0, 90));
        gs.fill(textShape);
        gs.dispose();

        // Gradient fill inside glyphs
        Rectangle2D tb = textShape.getBounds2D();
        LinearGradientPaint textPaint = new LinearGradientPaint(
            new Point2D.Double(tb.getX(), tb.getY()),
            new Point2D.Double(tb.getX(), tb.getY() + tb.getHeight()),
            new float[]{0f, 1f},
            new Color[]{new Color(255,255,255), new Color(245,245,245)}
        );
        Graphics2D gf = (Graphics2D) g2.create();
        gf.setPaint(textPaint);
        gf.fill(textShape);
        gf.dispose();

        // Thin highlight stroke for glassy edge
        Graphics2D gh = (Graphics2D) g2.create();
        gh.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        gh.setColor(new Color(255,255,255, 90));
        gh.draw(textShape);
        gh.dispose();
        
        // Draw the encouragement quote in italic with theme color, soft shadow and subtle highlight
        Font quoteFont = new Font("Segoe UI", Font.ITALIC, 18);
        FontRenderContext qfrc = g2.getFontRenderContext();
        GlyphVector qgv = quoteFont.createGlyphVector(qfrc, quote);
        Rectangle2D qvb = qgv.getVisualBounds();
        int quoteX = (int) Math.round((width - qvb.getWidth()) / 2.0);
        int quoteY = y + 30; // spacing below title
        Shape quoteShape = qgv.getOutline(quoteX, quoteY);

        // Apply global fade-in alpha
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, textAlpha));

        // Soft shadow for legibility
        Graphics2D qShadow = (Graphics2D) g2.create();
        qShadow.translate(1.0, 1.0);
        qShadow.setColor(new Color(0, 0, 0, 70));
        qShadow.fill(quoteShape);
        qShadow.dispose();

        // Primary fill in white (as requested)
        Graphics2D qFill = (Graphics2D) g2.create();
        qFill.setColor(Color.WHITE);
        qFill.fill(quoteShape);
        qFill.dispose();

        // Subtle top highlight inside glyphs for an aero touch
        Rectangle2D qb = quoteShape.getBounds2D();
        Shape oldClip3 = g2.getClip();
        g2.setClip(quoteShape);
        LinearGradientPaint qHighlight = new LinearGradientPaint(
            new Point2D.Double(qb.getX(), qb.getY()),
            new Point2D.Double(qb.getX(), qb.getY() + qb.getHeight() * 0.5),
            new float[]{0f, 1f},
            new Color[]{new Color(255,255,255,60), new Color(255,255,255,0)}
        );
        g2.setPaint(qHighlight);
        g2.fill(new Rectangle2D.Double(qb.getX(), qb.getY(), qb.getWidth(), qb.getHeight() * 0.5));
        g2.setClip(oldClip3);
        
        // ---- Inline vector 'Next' arrow button right of the quote ----
        int btnSize = 22;
        int arrowCX = (int) Math.round(qb.getX() + qb.getWidth() + 16);
        int arrowCY = (int) Math.round(qb.getY() + qb.getHeight() / 2.0);
        nextHit.setBounds(arrowCX - btnSize/2, arrowCY - btnSize/2, btnSize, btnSize);

        Graphics2D nb = (Graphics2D) g2.create();
        nb.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        nb.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, textAlpha))));
        // Shadow
        nb.setColor(new Color(0,0,0, nextHover ? 120 : 90));
        nb.fillOval(nextHit.x + 1, nextHit.y + 2, nextHit.width, nextHit.height);
        // Background circle
        Color bg = new Color(255,255,255, nextHover ? 110 : 70);
        nb.setColor(bg);
        nb.fillOval(nextHit.x, nextHit.y, nextHit.width, nextHit.height);
        // Border
        nb.setStroke(new BasicStroke(1.2f));
        nb.setColor(new Color(255,255,255, 160));
        nb.drawOval(nextHit.x, nextHit.y, nextHit.width, nextHit.height);
        // Chevron '>'
        Path2D chevron = new Path2D.Double();
        double ax = arrowCX - 3.5, ay = arrowCY - 5.0;
        chevron.moveTo(ax, ay);
        chevron.lineTo(arrowCX + 4.5, arrowCY);
        chevron.lineTo(ax, arrowCY + 5.0);
        nb.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        nb.setColor(Color.WHITE);
        nb.draw(chevron);
        nb.dispose();

        g2.dispose();
    }
    
    private Shape createHeartShape() {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(0, -20);
        path.curveTo(-25, -50, -60, -10, 0, 30);
        path.curveTo(60, -10, 25, -50, 0, -20);
        path.closePath();
        return path;
    }
}
