package main.ui.splash;

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
    private final JProgressBar bar = new JProgressBar();

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

        // Progress bar
        bar.setIndeterminate(true);
        bar.setBorderPainted(false);
        bar.setAlignmentX(Component.CENTER_ALIGNMENT);
        bar.setMaximumSize(new Dimension(Short.MAX_VALUE, 16));

        rounded.add(title);
        rounded.add(Box.createVerticalStrut(6));
        rounded.add(subtitle);
        rounded.add(Box.createVerticalStrut(14));
        rounded.add(bar);

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

    public void fadeOutAndDispose(Runnable onDone) {
        // Quick fade-out using a Swing timer adjusting window opacity
        final float[] alpha = {1f};
        Timer t = new Timer(15, e -> {
            alpha[0] -= 0.08f;
            float a = Math.max(0f, alpha[0]);
            try { setOpacity(a); } catch (Throwable ignore) {}
            if (a <= 0f) {
                ((Timer) e.getSource()).stop();
                dispose();
                if (onDone != null) onDone.run();
            }
        });
        t.start();
    }
}
