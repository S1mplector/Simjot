package main.ui.features.splash;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.Timer;

import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.spinner.ModernSpinner;
import main.ui.theme.aero.AeroTheme;

/**
 * Lightweight Aero-themed splash screen shown during app launch.
 */
public class AeroSplashScreen extends JWindow {
    private final JLabel title = new JLabel("Simjot", SwingConstants.CENTER);
    private final JLabel subtitle = new JLabel("Loading...", SwingConstants.CENTER);
    private final ModernSpinner spinner = new ModernSpinner(28, new Color(0, 120, 215));

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

        // Centered frosted panel to match editor chrome
        JPanel rounded = new FrostedGlassPanel(16);
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

        rounded.add(title);
        rounded.add(Box.createVerticalStrut(6));
        rounded.add(subtitle);
        rounded.add(Box.createVerticalStrut(16));
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
}
