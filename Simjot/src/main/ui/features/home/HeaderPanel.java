/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.home;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.font.*;
import java.awt.image.BufferedImage;
import java.util.Random;
import javax.swing.*;

import main.infrastructure.ffi.NativeAccess;
import main.infrastructure.io.QuoteLibrary;
import main.infrastructure.monitoring.AppPerf;
import main.core.service.SettingsStore;
import main.ui.theme.Theme;
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
    private BufferedImage cachedHeart;
    private int cachedW;
    private int cachedH;
    private Color cachedAccent;
    private static final float PANEL_HEART_SCALE = 1.12f;
    
    public HeaderPanel() {
        this(Theme.getWidgetAccent());
    }

    public HeaderPanel(Color accent) {
        setPreferredSize(new Dimension(800, 120));
        setOpaque(false);
        setLayout(new BorderLayout());
        this.accent = (accent != null ? accent : AeroTheme.AERO_BLUE);
        // Load curated quotes from resources (native-accelerated I/O when available).
        quotePool = new java.util.ArrayList<>(QuoteLibrary.loadQuotes());
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
                    // Advance phase and compute eased beat using native math
                    phase += 0.05; // speed
                    double eased = NativeAccess.easeCosine((float) (phase % (2 * Math.PI) / (2 * Math.PI)));

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

                    // Decay spring using native math
                    spring = NativeAccess.springDecay(spring, 0.90f, 0.001f);

                    // Calculate heartbeat scale using native math
                    float baseAmp = 0.06f;
                    heartScale = NativeAccess.heartbeatScale((float) phase, baseAmp, spring);

                    // ECG drawing progression (left→right draw then fade)
                    if(ecgOpacity > 0f){
                        if(ecgDraw < 1f){
                            ecgDraw += 0.06f; // draw speed
                            if(ecgDraw > 1f) ecgDraw = 1f;
                        } else {
                            ecgOpacity -= 0.02f; // fade after fully drawn
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
        if (width > 0 && height > 0) {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

            // Precompute text shapes for panel sizing and drawing.
            Font titleFont = new Font("Segoe UI", Font.BOLD, 36);
            String text = "Simjot";
            FontRenderContext frc = g2.getFontRenderContext();
            GlyphVector gv = titleFont.createGlyphVector(frc, text);
            Rectangle2D titleVisual = gv.getVisualBounds();
            int x = (int) Math.round((width - titleVisual.getWidth()) / 2.0);
            int y = height / 2;
            Shape textShape = gv.getOutline(x, y);
            Rectangle2D titleBounds = textShape.getBounds2D();

            Font quoteFont = new Font("Segoe UI", Font.ITALIC, 18);
            GlyphVector qgv = quoteFont.createGlyphVector(frc, quote);
            Rectangle2D quoteVisual = qgv.getVisualBounds();
            int quoteX = (int) Math.round((width - quoteVisual.getWidth()) / 2.0);
            int quoteY = y + 30;
            Shape quoteShape = qgv.getOutline(quoteX, quoteY);
            Rectangle2D quoteBounds = quoteShape.getBounds2D();

            // Inline next button hit area (used for hover/click).
            int btnSize = 22;
            int arrowCX = (int) Math.round(quoteBounds.getX() + quoteBounds.getWidth() + 16);
            int arrowCY = (int) Math.round(quoteBounds.getY() + quoteBounds.getHeight() / 2.0);
            nextHit.setBounds(arrowCX - btnSize / 2, arrowCY - btnSize / 2, btnSize, btnSize);

            // Frosted glass panel behind heart + title + quote for contrast.
            Shape heartBase = createHeartShape();
            Rectangle hb = heartBase.getBounds();
            double heartX = (width / 2.0) + hb.x * PANEL_HEART_SCALE;
            double heartY = (height / 2.0 - 10) + hb.y * PANEL_HEART_SCALE;
            Rectangle2D heartBounds = new Rectangle2D.Double(
                heartX, heartY, hb.width * PANEL_HEART_SCALE, hb.height * PANEL_HEART_SCALE
            );
            Rectangle panelRect = computePanelRect(width, height, titleBounds, quoteBounds, heartBounds, nextHit);
            paintFrostedPanel(g2, panelRect, textAlpha * 0.6f);

            if (cachedHeart == null || cachedW != width || cachedH != height || cachedAccent == null || !cachedAccent.equals(accent)) {
                cachedW = width;
                cachedH = height;
                cachedAccent = accent;
                BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D cg = img.createGraphics();
                cg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                AffineTransform at = cg.getTransform();
                cg.translate(width / 2, height / 2 - 10);
                Shape heart0 = createHeartShape();
                Rectangle b0 = heart0.getBounds();
                Graphics2D gS = (Graphics2D) cg.create();
                gS.translate(0, 4);
                Color sc = new Color(0, 0, 0, 40);
                for (int i = 0; i < 3; i++) {
                    gS.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f - i*0.06f));
                    gS.translate(0, 1);
                    gS.setColor(sc);
                    gS.fill(heart0);
                }
                gS.dispose();
                float cx0 = b0.x + b0.width * 0.45f;
                float cy0 = b0.y + b0.height * 0.35f;
                float r0 = Math.max(b0.width, b0.height) * 0.75f;
                Color light0 = AccentColorUtil.lighten(accent, 0.45f);
                Color dark0  = AccentColorUtil.darken(accent, 0.30f);
                RadialGradientPaint hp = new RadialGradientPaint(
                    new Point2D.Float(cx0, cy0), r0,
                    new float[]{0f, 1f},
                    new Color[]{
                        new Color(light0.getRed(), light0.getGreen(), light0.getBlue(), 210),
                        new Color(dark0.getRed(),  dark0.getGreen(),  dark0.getBlue(),  190)
                    }
                );
                cg.setPaint(hp);
                cg.fill(heart0);
                cg.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                cg.setColor(new Color(255, 255, 255, 28));
                cg.draw(heart0);
                Shape oc = cg.getClip();
                cg.setClip(heart0);
                LinearGradientPaint hl = new LinearGradientPaint(
                    new Point2D.Float(b0.x, b0.y),
                    new Point2D.Float(b0.x, b0.y + b0.height * 0.35f),
                    new float[]{0f, 1f},
                    new Color[]{new Color(255,255,255,80), new Color(255,255,255,0)}
                );
                cg.setPaint(hl);
                cg.fill(new Rectangle2D.Float(b0.x, b0.y, b0.width, (float)(b0.height * 0.35)));
                cg.setClip(oc);
                cg.setTransform(at);
                cg.dispose();
                cachedHeart = img;
            }
            AffineTransform old = g2.getTransform();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, textAlpha))));
            g2.translate(width / 2, height / 2 - 10);
            g2.scale(heartScale, heartScale);
            g2.drawImage(cachedHeart, -width/2, -(height/2 - 10), null);
            Shape heart = createHeartShape();
            Rectangle bounds = heart.getBounds();
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
            if (ecgOpacity > 0f) {
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

        }
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

    private static Rectangle computePanelRect(int width,
                                              int height,
                                              Rectangle2D titleBounds,
                                              Rectangle2D quoteBounds,
                                              Rectangle2D heartBounds,
                                              Rectangle nextBounds) {
        double minX = Math.min(Math.min(titleBounds.getX(), quoteBounds.getX()), heartBounds.getX());
        double maxX = Math.max(Math.max(titleBounds.getX() + titleBounds.getWidth(), quoteBounds.getX() + quoteBounds.getWidth()),
                heartBounds.getX() + heartBounds.getWidth());
        double minY = Math.min(Math.min(titleBounds.getY(), quoteBounds.getY()), heartBounds.getY());
        double maxY = Math.max(Math.max(titleBounds.getY() + titleBounds.getHeight(), quoteBounds.getY() + quoteBounds.getHeight()),
                heartBounds.getY() + heartBounds.getHeight());

        if (nextBounds != null) {
            minX = Math.min(minX, nextBounds.getX());
            maxX = Math.max(maxX, nextBounds.getX() + nextBounds.getWidth());
            minY = Math.min(minY, nextBounds.getY());
            maxY = Math.max(maxY, nextBounds.getY() + nextBounds.getHeight());
        }

        int padX = 26;
        int padY = 18;
        Rectangle r = new Rectangle(
            (int) Math.floor(minX) - padX,
            (int) Math.floor(minY) - padY,
            (int) Math.ceil(maxX - minX) + padX * 2,
            (int) Math.ceil(maxY - minY) + padY * 2
        );

        int minXClamp = 12;
        int minYClamp = 6;
        int maxXClamp = width - 12;
        int maxYClamp = height - 6;
        int x = Math.max(minXClamp, r.x);
        int y = Math.max(minYClamp, r.y);
        int right = Math.min(maxXClamp, r.x + r.width);
        int bottom = Math.min(maxYClamp, r.y + r.height);
        int w = Math.max(0, right - x);
        int h = Math.max(0, bottom - y);
        return new Rectangle(x, y, w, h);
    }

    private static void paintFrostedPanel(Graphics2D g2, Rectangle r, float alpha) {
        if (r == null || r.width <= 0 || r.height <= 0) return;
        float clamped = Math.max(0f, Math.min(1f, alpha));
        if (clamped <= 0f) return;
        Object aa = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int arc = Math.min(22, Math.min(r.width, r.height));
        int innerArc = Math.max(arc - 2, 2);
        RoundRectangle2D shape = new RoundRectangle2D.Float(r.x, r.y, r.width, r.height, arc, arc);

        GradientPaint base = new GradientPaint(
            r.x, r.y, scaleAlpha(new Color(255, 255, 255, 190), clamped),
            r.x, r.y + r.height, scaleAlpha(new Color(235, 235, 235, 120), clamped)
        );
        g2.setPaint(base);
        g2.fill(shape);

        GradientPaint sheen = new GradientPaint(
            r.x, r.y, scaleAlpha(new Color(255, 255, 255, 110), clamped),
            r.x, r.y + r.height * 0.55f, scaleAlpha(new Color(255, 255, 255, 20), clamped)
        );
        g2.setPaint(sheen);
        g2.fill(shape);

        GradientPaint shadow = new GradientPaint(
            r.x, (float) (r.y + r.height * 0.45f), scaleAlpha(new Color(0, 0, 0, 12), clamped),
            r.x, r.y + r.height, scaleAlpha(new Color(0, 0, 0, 35), clamped)
        );
        g2.setPaint(shadow);
        g2.fill(shape);

        g2.setStroke(new BasicStroke(1f));
        g2.setColor(scaleAlpha(new Color(255, 255, 255, 90), clamped));
        g2.draw(new RoundRectangle2D.Float(r.x + 1.5f, r.y + 1.5f, r.width - 3f, r.height - 3f, innerArc, innerArc));
        g2.setColor(scaleAlpha(new Color(0, 0, 0, 30), clamped));
        g2.draw(new RoundRectangle2D.Float(r.x + 0.5f, r.y + 0.5f, r.width - 1f, r.height - 1f, arc, arc));

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
    }

    private static Color scaleAlpha(Color color, float scale) {
        int alpha = Math.round(color.getAlpha() * scale);
        alpha = Math.max(0, Math.min(255, alpha));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }
}
