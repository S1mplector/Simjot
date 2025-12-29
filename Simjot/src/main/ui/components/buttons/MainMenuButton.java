package main.ui.components.buttons;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JButton;
import javax.swing.SwingConstants;
import main.ui.components.icons.ImageIconRenderer;
import main.ui.components.icons.VectorIconPainter;
import main.ui.theme.aero.AeroTheme;

/**
 * Main menu button with a calm, non-animated style.
 * Rounded card with subtle hover/press shading and centered icon + text.
 */
public class MainMenuButton extends JButton {
    private final String iconId;
    private boolean hovering = false;

    public MainMenuButton(String text, String iconId){
        super(text);
        this.iconId = iconId.toLowerCase();
        // Left align text
        setHorizontalAlignment(SwingConstants.CENTER);
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setFont(getFont().deriveFont(Font.BOLD, 18f));
        setForeground(AeroTheme.TEXT_PRIMARY);
        // Size calculated to fit icon + text without clipping
        int baseHeight = 60;
        int textWidth = getFontMetrics(getFont()).stringWidth(text) + 1;
        int minWidth = Math.max(260, 16 * 3 + textWidth + baseHeight); // padding + icon + text
        Dimension pref = new Dimension(minWidth, baseHeight);
        setPreferredSize(pref);
        setMinimumSize(pref);
        setMaximumSize(new Dimension(minWidth, baseHeight + 6)); // allow slight vertical growth for layout
        // Hover listeners for simple state toggle (no animation)
        addMouseListener(new MouseAdapter(){
            @Override public void mouseEntered(MouseEvent e){ hovering = true; repaint(); }
            @Override public void mouseExited(MouseEvent e){ hovering = false; repaint(); }
        });
    }

    @Override
    protected void paintComponent(Graphics g){
        boolean pressed = getModel().isArmed() && getModel().isPressed();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        float radius = 14f;

        Color base = new Color(245, 246, 250);
        Color hover = new Color(235, 239, 245);
        Color press = new Color(225, 231, 240);
        Color border = new Color(190, 195, 205);

        Color bg = pressed ? press : (hovering ? hover : base);
        g2.setColor(bg);
        g2.fill(new RoundRectangle2D.Float(0, 0, w, h, radius, radius));
        g2.setColor(border);
        g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, radius, radius));

        // Draw icon + text
        int padding = 18;
        int iconSize = Math.max(24, Math.min(h - padding * 2, 30));
        int iconX = padding;
        int iconY = (h - iconSize) / 2;

        Graphics2D gIcon = (Graphics2D) g2.create();
        gIcon.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        String resPath = ImageIconRenderer.mapIdToResource(iconId);
        boolean drawn = false;
        if (resPath != null) {
            ImageIconRenderer.draw(gIcon, resPath, iconX, iconY, iconSize, this, true);
            drawn = true;
        }
        if (!drawn) {
            gIcon.setColor(AeroTheme.TEXT_PRIMARY);
            drawVector(gIcon, iconId, iconX, iconY, iconSize);
        }
        gIcon.dispose();

        g2.setColor(getForeground());
        FontMetrics fm = g2.getFontMetrics();
        String text = getText();
        int textX = iconX + iconSize + padding;
        int textY = (h + fm.getAscent() - fm.getDescent()) / 2;
        g2.drawString(text, textX, textY);
        g2.dispose();
    }

    // Icon path resolution is centralized in ImageIconRenderer.mapIdToResource

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
            case "calendar": {
                int r = Math.max(6, s/8);
                // Shadow
                Graphics2D sh = (Graphics2D) g2.create();
                sh.setComposite(AlphaComposite.SrcOver.derive(0.18f));
                sh.setPaint(new RadialGradientPaint(new Point(x + s/2, y + s), s/1.6f,
                        new float[]{0f,1f}, new Color[]{new Color(0,0,0,80), new Color(0,0,0,0)}));
                sh.fillOval(x-3, y+s-8, s+6, 12);
                sh.dispose();

                // Body
                Paint body = new LinearGradientPaint(x, y, x, y+s,
                        new float[]{0f, 0.6f, 1f},
                        new Color[]{new Color(248,248,248), new Color(234,238,244), new Color(220,224,230)});
                g2.setPaint(body);
                g2.fillRoundRect(x, y + r/2, s, s, r, r);

                // Header bar
                Paint head = new LinearGradientPaint(x, y, x, y + r + r/2,
                        new float[]{0f,1f}, new Color[]{new Color(120,160,230), new Color(70,120,210)});
                g2.setPaint(head);
                g2.fillRoundRect(x, y, s, r + r/2, r, r);

                // Binding rings
                g2.setStroke(new BasicStroke(Math.max(2f, s/18f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(255,255,255,200));
                int ringY = y + r/3;
                g2.drawLine(x + s/4, ringY, x + s/4, ringY + r/2);
                g2.drawLine(x + s*3/4, ringY, x + s*3/4, ringY + r/2);

                // Grid dots
                g2.setColor(new Color(80, 100, 140));
                g2.setStroke(new BasicStroke(Math.max(1f, s/28f)));
                int cols = 3, rows = 3;
                int padX = s/6;
                int padY = r + r/2;
                int cellW = (s - padX*2) / (cols - 1 == 0 ? 1 : cols - 1);
                int cellH = (s - padY - padX) / (rows - 1 == 0 ? 1 : rows - 1);
                for (int row = 0; row < rows; row++) {
                    for (int col = 0; col < cols; col++) {
                        int cx = x + padX + col * cellW;
                        int cy = y + padY + row * cellH;
                        g2.fillOval(cx - 2, cy - 2, 4, 4);
                    }
                }

                // Border
                g2.setColor(new Color(140, 150, 170));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(x, y + r/2, s, s, r, r);
                break; }
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
