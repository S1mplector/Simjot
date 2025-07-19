package main.ui.animations;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * A modern, smooth, indeterminate spinner for loading/progress indication.
 * This spinner features a circular design with a rotating arc,
 * providing a visually appealing and smooth animation.
 * * Usage:
 * * <pre>
 * * ModernSpinner spinner = new ModernSpinner();
 * * * // Add to a panel or frame
 * * * panel.add(spinner);
 * * * spinner.start(); // Start the animation
 * * * // To stop the animation, call spinner.stop();
 * * * </pre>
 * * This class extends JComponent and uses a Timer to update the spinner's angle,
 * * creating a smooth rotation effect. The spinner's size and color can be customized
 * * through the constructor.
 * * The default size is 36 pixels and the default color is a shade of blue.
 */
public class ModernSpinner extends JComponent {
    private float angle = 0f;
    private final Timer timer;
    private final int size;
    private final Color color;

    public ModernSpinner() {
        this(36, new Color(0, 120, 215));
    }
    public ModernSpinner(int size, Color color) {
        this.size = size;
        this.color = color;
        setPreferredSize(new Dimension(size, size));
        setOpaque(false);
        timer = new Timer(16, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                angle += 0.08f;
                if (angle > Math.PI * 2) angle -= Math.PI * 2;
                repaint();
            }
        });
        timer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        int r = size / 2 - 4;
        int cx = w / 2, cy = h / 2;
        float thickness = Math.max(3f, size * 0.13f);
        g2.setStroke(new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 60));
        g2.drawArc(cx - r, cy - r, r * 2, r * 2, 0, 360);
        g2.setColor(color);
        g2.drawArc(cx - r, cy - r, r * 2, r * 2, (int) Math.toDegrees(angle), 90);
        g2.dispose();
    }

    public void stop() { timer.stop(); }
    public void start() { timer.start(); }
}
