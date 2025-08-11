package main.ui.widgets;

import java.awt.*;
import javax.swing.*;
import java.awt.geom.*;
import main.ui.theme.aero.AeroTheme;

/**
 * A calming breathing-circle animation that expands and contracts in a smooth
 * sinusoid. Intended to sit transparently on top of a screen (e.g., the main
 * menu) without intercepting mouse events.
 */

public class BreathingWidget extends JComponent implements Widget {
    private static final long serialVersionUID = 1L;

    /** ~60 FPS update timer */
    private final Timer timer;
    /** Phase from 0..1 used to drive the easing curve */
    private float phase;
    
    // Breathing configuration
    private int inhaleTime = 4;
    private int hold1Time = 4;
    private int exhaleTime = 4;
    private int hold2Time = 4;
    private int opacity = 90;
    private int sizePercent = 40;
    private Color circleColor = Color.WHITE;

    public BreathingWidget() {
        setOpaque(false);
        // Allow clicks to fall through to components underneath
        setFocusable(false);
        
        // Set bounds to fill parent
        setAlignmentX(0.5f);
        setAlignmentY(0.5f);

        timer = new Timer(16, e -> {
            updatePhase();
            repaint();
        });
        setVisible(false);
    }

    // ------------------------------------------------------------------------
    // Widget API
    // ------------------------------------------------------------------------
    @Override public void start() {
        if (!timer.isRunning()) {
            timer.start();
        }
        setVisible(true);
    }

    @Override public void stop() {
        if (timer.isRunning()) {
            timer.stop();
        }
        setVisible(false);
    }

    @Override
    public void setEnabled(boolean enabled) {
        // Let Swing track enabled state for property changes/accessibility
        super.setEnabled(enabled);
        if (enabled) {
            start();
        } else {
            stop();
        }
    }

    @Override
    public boolean isEnabled() {
        return timer != null && timer.isRunning();
    }
    
    // ------------------------------------------------------------------------
    // Configuration
    // ------------------------------------------------------------------------
    public void configure(int inhale, int hold1, int exhale, int hold2, 
                         int opacity, int size, String color) {
        this.inhaleTime = inhale;
        this.hold1Time = hold1;
        this.exhaleTime = exhale;
        this.hold2Time = hold2;
        this.opacity = opacity;
        this.sizePercent = size;
        
        // Parse color
        switch(color) {
            case "Blue": circleColor = new Color(100, 149, 237); break;
            case "Green": circleColor = new Color(144, 238, 144); break;
            case "Purple": circleColor = new Color(186, 85, 211); break;
            case "Orange": circleColor = new Color(255, 165, 0); break;
            default: circleColor = Color.WHITE;
        }
        
        phase = 0f; // Reset animation
    }
    
    private void updatePhase() {
        // Calculate total cycle time in seconds
        int totalCycle = inhaleTime + hold1Time + exhaleTime + hold2Time;
        if (totalCycle == 0) totalCycle = 1;
        
        // Increment phase based on cycle time
        float increment = 1f / (totalCycle * 60f); // 60 FPS
        phase = (phase + increment) % 1f;
    }

    // ------------------------------------------------------------------------
    // Painting
    // ------------------------------------------------------------------------
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        if (!isVisible()) return;
        
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Calculate which phase of breathing we're in
        float breathingScale = calculateBreathingScale();
        
        int base = Math.min(getWidth(), getHeight());
        int radius = (int) (breathingScale * base * (sizePercent / 100f));

        int x = (getWidth() - radius) / 2;
        int y = (getHeight() - radius) / 2;

        // Apply configured opacity
        int alpha = (int) (opacity * 255 / 100);

        // ---- Soft drop shadow (Aero-style) ----
        int shadow = Math.max(6, radius/18);
        for(int i=shadow; i>=1; i--){
            float a = 0.06f * (i/(float)shadow);
            g2.setComposite(AlphaComposite.SrcOver.derive(a));
            g2.setColor(Color.BLACK);
            g2.fillOval(x - i, y - i + 4, radius + i*2, radius + i*2);
        }
        g2.setComposite(AlphaComposite.SrcOver);

        // ---- Glossy sphere body with radial gradient ----
        float cx = x + radius/2f;
        float cy = y + radius/2f - radius*0.15f; // lift highlight a bit
        Color cInner = new Color(
            Math.min(255, (int)(circleColor.getRed()*1.06)),
            Math.min(255, (int)(circleColor.getGreen()*1.06)),
            Math.min(255, (int)(circleColor.getBlue()*1.06)),
            alpha
        );
        Color cOuter = new Color(
            Math.max(0, (int)(circleColor.getRed()*0.65)),
            Math.max(0, (int)(circleColor.getGreen()*0.65)),
            Math.max(0, (int)(circleColor.getBlue()*0.65)),
            Math.max(40, (int)(alpha*0.9))
        );
        float rGrad = radius*0.6f;
        RadialGradientPaint rgp = new RadialGradientPaint(
            new Point2D.Float(cx, cy), rGrad,
            new float[]{0f, 1f},
            new Color[]{cInner, cOuter}
        );
        g2.setPaint(rgp);
        g2.fillOval(x, y, radius, radius);

        // ---- Subtle top glass highlight ----
        Shape oldClip = g2.getClip();
        Area topHalf = new Area(new Ellipse2D.Float(x, y, radius, radius));
        topHalf.intersect(new Area(new Rectangle(x, y, radius, (int)(radius*0.55))));
        g2.setClip(topHalf);
        GradientPaint gp = new GradientPaint(x, y, new Color(255,255,255,100), x, y+(int)(radius*0.6), new Color(255,255,255,0));
        g2.setPaint(gp);
        g2.fillOval(x, y, radius, radius);
        g2.setClip(oldClip);

        // ---- Rim and breathing glow ----
        float pulse = 0.5f + 0.5f * (float)Math.sin(System.currentTimeMillis()/400.0);
        int rimAlpha = Math.min(255, (int)(120 * pulse));
        g2.setStroke(new BasicStroke(Math.max(2f, radius/40f)));
        g2.setColor(new Color(255,255,255, rimAlpha));
        g2.drawOval(x+1, y+1, radius-2, radius-2);

        // Outer aura glow
        for(int i=1;i<=4;i++){
            int grow = i*3;
            int aAura = Math.max(10, (int)(alpha * (0.10f/(i))));
            g2.setColor(new Color(AeroTheme.AERO_BLUE.getRed(), AeroTheme.AERO_BLUE.getGreen(), AeroTheme.AERO_BLUE.getBlue(), aAura));
            g2.drawOval(x - grow, y - grow, radius + grow*2, radius + grow*2);
        }

        g2.dispose();
    }
    
    private float calculateBreathingScale() {
        int totalCycle = inhaleTime + hold1Time + exhaleTime + hold2Time;
        if (totalCycle == 0) return 0.5f;
        
        float currentTime = phase * totalCycle;
        
        // Determine which phase we're in
        if (currentTime < inhaleTime) {
            // Inhaling: scale from 0.2 to 1.0
            float progress = currentTime / inhaleTime;
            return 0.2f + 0.8f * easeInOut(progress);
        } else if (currentTime < inhaleTime + hold1Time) {
            // Holding full: stay at 1.0
            return 1.0f;
        } else if (currentTime < inhaleTime + hold1Time + exhaleTime) {
            // Exhaling: scale from 1.0 to 0.2
            float progress = (currentTime - inhaleTime - hold1Time) / exhaleTime;
            return 1.0f - 0.8f * easeInOut(progress);
        } else {
            // Holding empty: stay at 0.2
            return 0.2f;
        }
    }
    
    private float easeInOut(float t) {
        return (float) (0.5 - 0.5 * Math.cos(t * Math.PI));
    }

    @Override public Dimension getPreferredSize() {
        // Try to match parent size if available
        Container parent = getParent();
        if (parent != null) {
            return parent.getSize();
        }
        return new Dimension(800, 600);
    }
    
    @Override public Dimension getMinimumSize() {
        return getPreferredSize();
    }
    
    @Override public Dimension getMaximumSize() {
        return getPreferredSize();
    }
}