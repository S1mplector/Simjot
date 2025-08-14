package main.ui.components.buttons;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import main.infrastructure.monitoring.AppPerf;
import main.ui.animations.transitions.FadingButton;
import main.ui.components.icons.VectorIconPainter;
import main.ui.components.icons.ImageIconRenderer;
import main.ui.theme.aero.AeroPainters;
import main.ui.theme.aero.AeroTheme;

/** A main-menu button that animates a white vector icon sliding out on hover. */
public class MainMenuButton extends FadingButton {
    private final String iconId;
    private float progress = 0f;    // 0 hidden, 1 fully shown
    private boolean hovering = false;
    private Timer animTimer;

    public MainMenuButton(String text, String iconId){
        super(text);
        this.iconId = iconId.toLowerCase();
        // Left align text
        setHorizontalAlignment(SwingConstants.CENTER);
        // Animation timer using centralized FPS
        animTimer = new Timer(AppPerf.getAnimationDelay(), e->{
            float target = hovering?1f:0f;
            float speed = 0.08f; // approach rate
            if(Math.abs(progress-target)>0.01f){
                progress += (target-progress)*speed;
                repaint();
            } else {
                progress = target;
                animTimer.stop();
            }
        });
        // Hover listeners
        addMouseListener(new MouseAdapter(){
            @Override public void mouseEntered(MouseEvent e){ hovering=true; animTimer.start(); }
            @Override public void mouseExited(MouseEvent e){ hovering=false; animTimer.start(); }
        });
    }

    @Override
    protected void paintComponent(Graphics g){
        int extra = (int) (20 * progress);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Shape bg = new RoundRectangle2D.Float(-extra, 0, getWidth() + 2 * extra, getHeight(), 15, 15);

        // Determine state colors (Aero-style)
        boolean pressed = getModel().isPressed();
        Color top;
        Color bottom;
        if (pressed) {
            top = AeroTheme.BUTTON_PRESS_TOP;
            bottom = AeroTheme.BUTTON_PRESS_BOTTOM;
        } else if (hovering) {
            top = AeroTheme.BUTTON_HOVER_TOP;
            bottom = AeroTheme.BUTTON_HOVER_BOTTOM;
        } else {
            top = AeroTheme.BUTTON_BG_TOP;
            bottom = AeroTheme.BUTTON_BG_BOTTOM;
        }

        // Paint gradient background and subtle glass overlay
        Rectangle r = new Rectangle(-extra, 0, getWidth() + 2 * extra, getHeight());
        AeroPainters.paintVerticalGradient(g2, r, top, bottom, 15);
        AeroPainters.paintGlassOverlay(g2, r, 15);

        // Soft border
        g2.setColor(new Color(180, 180, 180));
        g2.draw(bg);
        g2.dispose();

        // Draw text with fading alpha (but do NOT affect icon color)
        Color fgBase = getForeground();
        Color fgOpaque = new Color(fgBase.getRed(), fgBase.getGreen(), fgBase.getBlue());
        Color fgFaded = new Color(fgOpaque.getRed(), fgOpaque.getGreen(), fgOpaque.getBlue(), (int) (255 * (1 - progress)));

        // Avoid double background paint from FadingButton by making its background transparent during super call
        Color oldBg = getBackground();
        setBackground(new Color(0, 0, 0, 0));
        setForeground(fgFaded);
        super.paintComponent(g); // paints text with modified alpha
        // Restore colors
        setForeground(fgOpaque);
        setBackground(oldBg);

        // Draw sliding icon
        if(progress>0f){
            Graphics2D g2Icon = (Graphics2D) g.create();
            g2Icon.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2Icon.setComposite(AlphaComposite.SrcOver.derive(progress));

            int size = getHeight() - 14; // icon slightly smaller than button height

            // Compute centre position
            int xCenter = (getWidth() - size) / 2;
            // Start from outside right edge when hidden
            int xStart = getWidth() + size;
            int x = xStart + (int)((xCenter - xStart) * progress);

            int y = (getHeight() - size) / 2;
            // Prefer centralized PNG renderer (with caching & shadow); fallback to vector
            String resPath = ImageIconRenderer.mapIdToResource(iconId);
            boolean drawn = false;
            if (resPath != null) {
                ImageIconRenderer.draw(g2Icon, resPath, x, y, size, this, true);
                drawn = true;
            }
            if (!drawn) {
                // Icons use theme primary text color for better contrast
                g2Icon.setColor(AeroTheme.TEXT_PRIMARY);
                drawVector(g2Icon, iconId, x, y, size);
            }
            g2Icon.dispose();
        }
    }

    // PNG path resolution is centralized in ImageIconRenderer.mapIdToResource

    private void drawVector(Graphics2D g2, String id, int x, int y, int s){
        g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch(id){
            case "notebook": {
                // Windows 7-like hardcover notebook with spine, shadow, and gloss
                int r = Math.max(6, s/6);
                int spineW = Math.max(6, s/5);

                // Shadow
                Graphics2D sh = (Graphics2D) g2.create();
                sh.setComposite(AlphaComposite.SrcOver.derive(0.2f));
                sh.setPaint(new RadialGradientPaint(new Point(x + s/2, y + s), s/1.8f,
                        new float[]{0f,1f}, new Color[]{new Color(0,0,0,90), new Color(0,0,0,0)}));
                sh.fillOval(x-2, y+s-6, s+6, 10);
                sh.dispose();

                // Cover gradient
                Paint cover = new LinearGradientPaint(x, y, x, y+s,
                        new float[]{0f, 0.5f, 1f},
                        new Color[]{new Color(250,250,250), new Color(232,232,232), new Color(214,214,214)});
                g2.setPaint(cover);
                g2.fillRoundRect(x, y, s, s, r, r);

                // Spine (slightly darker)
                Paint spine = new LinearGradientPaint(x, y, x, y+s,
                        new float[]{0f,1f}, new Color[]{new Color(225,225,225), new Color(205,205,205)});
                g2.setPaint(spine);
                g2.fillRoundRect(x, y, spineW, s, r, r);

                // Separator line for spine
                g2.setColor(new Color(150,150,150));
                g2.drawLine(x + spineW, y + 2, x + spineW, y + s - 2);

                // Bookmark tab
                int tabW = Math.max(6, s/6), tabH = Math.max(10, s/4);
                int tx = x + s - tabW - 6, ty = y + r - 2;
                Paint tab = new LinearGradientPaint(tx, ty, tx, ty + tabH,
                        new float[]{0f,1f}, new Color[]{new Color(120,170,240), new Color(50,110,200)});
                g2.setPaint(tab);
                g2.fillRoundRect(tx, ty, tabW, tabH, 3, 3);
                g2.setColor(new Color(255,255,255,150));
                g2.drawLine(tx + 2, ty + 2, tx + tabW - 3, ty + 2);

                // Cover gloss
                g2.setPaint(new GradientPaint(0, y, new Color(255,255,255,180), 0, y + s/2f, new Color(255,255,255,0)));
                g2.fillRoundRect(x + 1, y + 1, s - 2, s/2, r - 1, r - 1);

                // Border
                g2.setColor(new Color(140,140,140));
                g2.drawRoundRect(x, y, s, s, r, r);
                break; }
            case "pencil": {
                // Replace simple pencil with a glossy sticky note (used for Idea Sticky)
                int r = Math.max(6, s/8);
                // Shadow
                Graphics2D sh = (Graphics2D) g2.create();
                sh.setComposite(AlphaComposite.SrcOver.derive(0.18f));
                sh.setPaint(new RadialGradientPaint(new Point(x + s/2, y + s), s/1.8f,
                        new float[]{0f,1f}, new Color[]{new Color(0,0,0,90), new Color(0,0,0,0)}));
                sh.fillOval(x-3, y+s-8, s+6, 12);
                sh.dispose();

                // Note body
                Paint note = new LinearGradientPaint(x, y, x, y+s,
                        new float[]{0f, 0.6f, 1f},
                        new Color[]{new Color(255,255,200), new Color(250,245,150), new Color(240,230,120)});
                g2.setPaint(note);
                g2.fillRoundRect(x, y, s, s, r, r);

                // Folded corner
                int fold = Math.max(8, s/5);
                Polygon dog = new Polygon();
                dog.addPoint(x + s - fold, y);
                dog.addPoint(x + s, y);
                dog.addPoint(x + s, y + fold);
                g2.setPaint(new LinearGradientPaint(x, y, x, y+fold,
                        new float[]{0f,1f}, new Color[]{new Color(255,255,255,220), new Color(235,235,200)}));
                g2.fillPolygon(dog);
                g2.setColor(new Color(200,190,120));
                g2.drawPolygon(dog);

                // Glass highlight strip
                g2.setPaint(new GradientPaint(0, y, new Color(255,255,255,170), 0, y + s/2f, new Color(255,255,255,0)));
                g2.fillRoundRect(x+1, y+1, s-2, Math.max(1, s/2), r-2, r-2);

                // Subtle ruled lines
                g2.setColor(new Color(210,200,120));
                g2.setStroke(new BasicStroke(Math.max(1f, s/32f)));
                int gap = Math.max(8, s/5);
                for(int yy = y + gap; yy < y + s - gap/2; yy += gap) g2.drawLine(x + r/2, yy, x + s - r/2, yy);

                // Border
                g2.setColor(new Color(170,160,90));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(x, y, s, s, r, r);
                break; }
            case "image":
                g2.drawRect(x, y, s, s);
                g2.drawLine(x, y+s*3/5, x+s*2/5, y+s*2/5);
                g2.drawLine(x+s*2/5, y+s*2/5, x+s, y+s*4/5);
                break;
            case "lines":
                int gap=s/4;
                for(int i=0;i<3;i++){ int yy=y+i*gap; g2.drawLine(x, yy, x+s, yy); }
                break;
            case "smile": {
                // Glossy yellow happy face with highlight and soft stroke
                int cx = x + s/2, cy = y + s/2;
                // Shadow
                Graphics2D sh = (Graphics2D) g2.create();
                sh.setComposite(AlphaComposite.SrcOver.derive(0.18f));
                sh.setPaint(new RadialGradientPaint(new Point(cx, y + s), s/1.6f,
                        new float[]{0f,1f}, new Color[]{new Color(0,0,0,80), new Color(0,0,0,0)}));
                sh.fillOval(x-4, y+s-8, s+8, 12);
                sh.dispose();

                // Face gradient
                Paint face = new RadialGradientPaint(new Point(cx - s/6, cy - s/6), s/1.4f,
                        new float[]{0f, 0.7f, 1f},
                        new Color[]{new Color(255,235,120), new Color(255,215,60), new Color(235,180,40)});
                g2.setPaint(face);
                g2.fillOval(x, y, s, s);

                // Gloss highlight
                g2.setPaint(new RadialGradientPaint(new Point(cx - s/4, y + s/5), s/2.2f,
                        new float[]{0f,1f}, new Color[]{new Color(255,255,255,200), new Color(255,255,255,0)}));
                g2.fillOval(x + s/10, y + s/10, s/2, s/3);

                // Eyes and smile
                g2.setColor(new Color(80,80,80));
                g2.setStroke(new BasicStroke(Math.max(2f, s/14f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int ex = x + s/3, ey = y + s/3;
                int eW = Math.max(3, s/12);
                g2.fillOval(ex, ey, eW, eW);
                g2.fillOval(x + s*2/3 - eW, ey, eW, eW);
                // Smile arc
                g2.drawArc(x + s/4, y + s/3, s/2, s/2, 200, 140);

                // Border
                g2.setColor(new Color(150,130,60));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawOval(x, y, s, s);
                break; }
            case "wrench":
                // Delegate to shared painter for exact match
                VectorIconPainter.paint(g2, "wrench", x, y, s);
                break;
            case "clock": {
                // Glossy regular analog clock for Pomodoro
                int cx = x + s/2; int cy = y + s/2;

                // Shadow (subtle)
                Graphics2D sh = (Graphics2D) g2.create();
                sh.setComposite(AlphaComposite.SrcOver.derive(0.18f));
                sh.setPaint(new RadialGradientPaint(new Point(cx, y + s), s/1.6f,
                        new float[]{0f,1f}, new Color[]{new Color(0,0,0,90), new Color(0,0,0,0)}));
                sh.fillOval(x-2, y+s-8, s+4, 12);
                sh.dispose();

                // Bezel (metallic)
                Paint bezel = new LinearGradientPaint(x, y, x, y+s,
                        new float[]{0f,0.5f,1f},
                        new Color[]{new Color(242,242,242), new Color(214,214,214), new Color(192,192,192)});
                g2.setPaint(bezel);
                g2.fillOval(x, y, s, s);

                // Face
                int pad = Math.max(3, s/12);
                Paint face = new RadialGradientPaint(new Point(cx - s/6, cy - s/6), s/1.6f,
                        new float[]{0f,1f}, new Color[]{new Color(255,255,255), new Color(235,235,235)});
                g2.setPaint(face);
                int faceSize = s - 2*pad;
                g2.fillOval(x+pad, y+pad, faceSize, faceSize);

                // Ticks (12 marks, majors longer)
                g2.setColor(new Color(120,120,120));
                g2.setStroke(new BasicStroke(Math.max(1f, s/40f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int rOuter = s/2 - pad/2;
                int rInnerMajor = rOuter - Math.max(7, s/8);
                int rInnerMinor = rOuter - Math.max(5, s/12);
                for (int i = 0; i < 12; i++) {
                    double a = Math.toRadians(i * 30);
                    int rIn = (i % 3 == 0) ? rInnerMajor : rInnerMinor;
                    int x1 = cx + (int)(rOuter * Math.cos(a));
                    int y1 = cy + (int)(rOuter * Math.sin(a));
                    int x2 = cx + (int)(rIn * Math.cos(a));
                    int y2 = cy + (int)(rIn * Math.sin(a));
                    g2.drawLine(x1, y1, x2, y2);
                }

                // Hands
                g2.setColor(AeroTheme.TEXT_PRIMARY);
                g2.setStroke(new BasicStroke(Math.max(2f, s/18f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(cx, cy, cx + s/6, cy - s/8); // hour ~2
                g2.setStroke(new BasicStroke(Math.max(1.6f, s/24f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(cx, cy, cx - s/5, cy - s/6); // minute ~10
                g2.setColor(new Color(220,60,60));
                g2.setStroke(new BasicStroke(Math.max(1.2f, s/28f)));
                g2.drawLine(cx, cy, cx, cy - s/4); // seconds up
                g2.fillOval(cx-2, cy-2, 4, 4);

                // Subtle highlight
                g2.setPaint(new RadialGradientPaint(new Point(cx - s/4, y + s/5), s/2.4f,
                        new float[]{0f,1f}, new Color[]{new Color(255,255,255,170), new Color(255,255,255,0)}));
                int hw = Math.max(1, s*3/4);
                int hh = Math.max(1, s/2);
                g2.fillOval(x + s/8, y + s/12, hw, hh);
                break; }
            case "tick":
                // Draw a checkmark/tick symbol
                g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int checkStart = x + s/4;
                int checkMid = x + s*2/5;
                int checkEnd = x + s*3/4;
                int checkBottom = y + s*3/5;
                int checkMiddle = y + s/2;
                int checkTop = y + s/3;
                // Draw the checkmark as two connected lines
                g2.drawLine(checkStart, checkMiddle, checkMid, checkBottom);
                g2.drawLine(checkMid, checkBottom, checkEnd, checkTop);
                break;
            case "breath": {
                // Calming breathing circle with glassy Win7 look
                int cx = x + s/2; int cy = y + s/2;
                // Shadow
                Graphics2D sh = (Graphics2D) g2.create();
                sh.setComposite(AlphaComposite.SrcOver.derive(0.18f));
                sh.setPaint(new RadialGradientPaint(new Point(cx, y + s), s/1.6f,
                        new float[]{0f,1f}, new Color[]{new Color(0,0,0,90), new Color(0,0,0,0)}));
                sh.fillOval(x-3, y+s-8, s+6, 12);
                sh.dispose();

                // Outer ring
                Paint ring = new RadialGradientPaint(new Point(cx - s/6, cy - s/6), s/1.3f,
                        new float[]{0f, 0.7f, 1f},
                        new Color[]{new Color(170,210,255), new Color(110,160,240), new Color(70,120,210)});
                g2.setPaint(ring);
                g2.fillOval(x, y, s, s);

                // Inner hole to make a ring look
                g2.setComposite(AlphaComposite.Clear);
                int hole = Math.max(8, s/2);
                g2.fillOval(cx - hole/2, cy - hole/2, hole, hole);
                g2.setComposite(AlphaComposite.SrcOver);

                // Soft inner glow
                g2.setPaint(new RadialGradientPaint(new Point(cx, cy), s/1.6f,
                        new float[]{0f,1f}, new Color[]{new Color(255,255,255,120), new Color(255,255,255,0)}));
                g2.fillOval(x + s/10, y + s/10, s - s/5, s - s/5);

                // Highlight arc
                g2.setColor(new Color(255,255,255,160));
                g2.setStroke(new BasicStroke(Math.max(2f, s/14f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawArc(x + s/8, y + s/8, s - s/4, s - s/4, 130, 70);

                // Border
                g2.setColor(new Color(80,110,180));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawOval(x, y, s, s);
                break; }
        }
    }
}