package main.ui.buttons;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import main.transitions.FadingButton;
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
        // Animation timer ~60 FPS
        animTimer = new Timer(16, e->{
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
            // Icons should appear solid black per request
            g2Icon.setColor(Color.BLACK);
            drawVector(g2Icon, iconId, x, y, size);
            g2Icon.dispose();
        }
    }

    private void drawVector(Graphics2D g2, String id, int x, int y, int s){
        g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch(id){
            case "notebook":
                g2.drawRoundRect(x, y, s, s, 8,8);
                g2.drawLine(x+s/2, y, x+s/2, y+s);
                break;
            case "pencil":
                g2.drawLine(x, y+s, x+s, y);
                g2.drawLine(x+3, y+s-3, x+s-3, y+3);
                break;
            case "image":
                g2.drawRect(x, y, s, s);
                g2.drawLine(x, y+s*3/5, x+s*2/5, y+s*2/5);
                g2.drawLine(x+s*2/5, y+s*2/5, x+s, y+s*4/5);
                break;
            case "lines":
                int gap=s/4;
                for(int i=0;i<3;i++){ int yy=y+i*gap; g2.drawLine(x, yy, x+s, yy); }
                break;
            case "smile":
                g2.drawOval(x, y, s, s);
                g2.fillOval(x+s/3, y+s/3, s/10, s/10);
                g2.fillOval(x+s*2/3 - s/10, y+s/3, s/10, s/10);
                g2.drawArc(x+s/4, y+s/3, s/2, s/2, 200, 140);
                break;
            case "wrench":
                g2.drawLine(x, y+s/2, x+s, y+s/2);
                g2.drawOval(x+s-6, y+s/2-6, 12,12);
                g2.drawOval(x-6, y+s/2-6, 12,12);
                break;
            case "clock":
                // Simple clock face with hour and minute hands
                g2.drawOval(x, y, s, s);
                int cx = x + s/2;
                int cy = y + s/2;
                // hour hand (at ~2)
                g2.drawLine(cx, cy, cx + s/6, cy - s/8);
                // minute hand (at ~10)
                g2.drawLine(cx, cy, cx - s/5, cy - s/6);
                // center dot
                g2.fillOval(cx-2, cy-2, 4, 4);
                break;
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
            case "breath":
                // Breathing icon simplified to a single circle
                g2.drawOval(x, y, s, s);
                break;
        }
    }
}