package main.ui.features.splash;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.Timer;

import main.ui.components.indicators.Win7ProgressBar;
import main.ui.theme.aero.AeroTheme;

/**
 * Lightweight Aero-themed splash screen shown during app launch.
 */
public class AeroSplashScreen extends JWindow {
    private final JLabel title = new JLabel("Simjot", SwingConstants.CENTER);
    private final JLabel subtitle = new JLabel("Loading...", SwingConstants.CENTER);
    private final Win7ProgressBar progressBar = Win7ProgressBar.createCompact();

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
        progressBar.setAlignmentX(Component.CENTER_ALIGNMENT);
        progressBar.setMaximumSize(new Dimension(280, 18));

        rounded.add(title);
        rounded.add(Box.createVerticalStrut(6));
        rounded.add(subtitle);
        rounded.add(Box.createVerticalStrut(16));
        rounded.add(progressBar);

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
}
