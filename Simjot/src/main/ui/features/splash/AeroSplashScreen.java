package main.ui.features.splash;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import javax.swing.*;
import main.ui.theme.aero.AeroTheme;

/**
 * Lightweight Aero-themed splash screen shown during app launch.
 */
public class AeroSplashScreen extends JWindow {
    private final JLabel title = new JLabel("Simjot", SwingConstants.CENTER);
    private final JLabel subtitle = new JLabel("Loading...", SwingConstants.CENTER);
    private final Spinner spinner = new Spinner();

    public AeroSplashScreen() {
        setAlwaysOnTop(true);
        // Transparent window; only the rounded inner panel will be visible
        setBackground(new Color(0, 0, 0, 0));
        setSize(520, 260);
        setLocationRelativeTo(null);

        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        content.setBackground(new Color(0, 0, 0, 0));
        content.setDoubleBuffered(true);

        // Centered rounded panel with simple white fill and light gray border
        JPanel rounded = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                int arc = 16;
                g2.setColor(Color.WHITE);
                g2.fill(new RoundRectangle2D.Float(0, 0, w-1, h-1, arc, arc));
                g2.setColor(new Color(200, 200, 200));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w-1, h-1, arc, arc));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        rounded.setOpaque(false);
        rounded.setLayout(new BoxLayout(rounded, BoxLayout.Y_AXIS));
        rounded.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        rounded.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Texts
        title.setForeground(AeroTheme.TEXT_PRIMARY);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 28f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitle.setForeground(new Color(0, 0, 0, 180));
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 15f));
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Spinner
        spinner.setAlignmentX(Component.CENTER_ALIGNMENT);
        spinner.setMaximumSize(new Dimension(40, 40));

        rounded.add(title);
        rounded.add(Box.createVerticalStrut(6));
        rounded.add(subtitle);
        rounded.add(Box.createVerticalStrut(12));
        rounded.add(spinner);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1; gbc.weighty = 1;
        gbc.fill = GridBagConstraints.NONE;
        content.add(rounded, gbc);

        setContentPane(content);
    }

    public void setStatus(String text) {
        if (text != null && !text.equals(subtitle.getText())) {
            subtitle.setText(text);
        }
    }

    // No window shape manipulation needed with the simplified visuals

    public void fadeOutAndDispose() { fadeOutAndDispose(null); }

    public void fadeOutAndDispose(Runnable onComplete) {
        final float[] alpha = {1f};
        Timer t = new Timer(15, null);
        t.addActionListener(e -> {
            alpha[0] -= 0.06f;
            if (alpha[0] <= 0f) {
                t.stop();
                setVisible(false);
                dispose();
                if (onComplete != null) onComplete.run();
            } else {
                float a = Math.max(0f, alpha[0]);
                try { setOpacity(a); } catch (Throwable ignore) {}
            }
        });
        t.start();
    }

    // Lightweight animated spinner with Aero styling
    private static final class Spinner extends JComponent {
        private final Timer timer;
        private int step = 0; // 0..11

        Spinner() {
            setOpaque(false);
            setPreferredSize(new Dimension(28, 28));
            setMinimumSize(new Dimension(28, 28));
            timer = new Timer(80, e -> { // ~12 steps per rotation, ~1 rps
                step = (step + 1) % 12;
                repaint();
            });
            timer.start();
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int s = Math.min(w, h);
            int radius = (s / 2) - 2;
            int cx = w / 2;
            int cy = h / 2;

            // Windows 7 style: 12 dots with fading alpha tail
            for (int i = 0; i < 12; i++) {
                int idx = (step + i) % 12; // head at current step
                float t = 1f - (i / 12f);
                int alpha = (int) (60 + t * 160); // 60..220
                Color c = new Color(AeroTheme.TEXT_PRIMARY.getRed(), AeroTheme.TEXT_PRIMARY.getGreen(), AeroTheme.TEXT_PRIMARY.getBlue(), alpha);
                double ang = Math.toRadians(idx * 30.0);
                int dotR = Math.max(2, s / 10);
                int dx = cx + (int) (Math.cos(ang) * radius) - dotR/2;
                int dy = cy + (int) (Math.sin(ang) * radius) - dotR/2;
                g2.setColor(c);
                g2.fillOval(dx, dy, dotR, dotR);
            }
            g2.dispose();
        }

        @Override public void addNotify() { super.addNotify(); if (!timer.isRunning()) timer.start(); }
        @Override public void removeNotify() { if (timer.isRunning()) timer.stop(); super.removeNotify(); }
    }
}
