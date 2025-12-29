package main.ui.features.splash;

import java.awt.Color;
import java.awt.Component;
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

import main.ui.components.spinner.ModernSpinner;
import main.ui.theme.aero.AeroTheme;

/**
 * Special splash screen shown when saving settings/preferences.
 * Displays a custom message and ensures robust preference saving.
 */
public class SettingsSaveSplash extends JWindow {
    private final JLabel headerLabel = new JLabel("Please wait while Simjot saves your preferences.", SwingConstants.CENTER);
    private final JLabel statusLabel = new JLabel("Initializing...", SwingConstants.CENTER);
    private final ModernSpinner spinner = new ModernSpinner(28, new Color(0, 120, 215));

    public SettingsSaveSplash() {
        setAlwaysOnTop(true);
        setBackground(new Color(0, 0, 0, 0));
        setSize(480, 200);
        setLocationRelativeTo(null);

        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        content.setBackground(new Color(0, 0, 0, 0));
        content.setDoubleBuffered(true);

        // Rounded panel with white fill
        JPanel rounded = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                int arc = 16;
                g2.setColor(Color.WHITE);
                g2.fill(new RoundRectangle2D.Float(0, 0, w-1, h-1, arc, arc));
                g2.setColor(new Color(180, 190, 200));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w-1, h-1, arc, arc));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        rounded.setOpaque(false);
        rounded.setLayout(new BoxLayout(rounded, BoxLayout.Y_AXIS));
        rounded.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        rounded.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Header text
        headerLabel.setForeground(AeroTheme.TEXT_PRIMARY);
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 15f));
        headerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Status text
        statusLabel.setForeground(new Color(80, 80, 80));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 13f));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Spinner
        spinner.setAlignmentX(Component.CENTER_ALIGNMENT);

        rounded.add(headerLabel);
        rounded.add(Box.createVerticalStrut(12));
        rounded.add(statusLabel);
        rounded.add(Box.createVerticalStrut(16));
        rounded.add(spinner);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1; gbc.weighty = 1;
        gbc.fill = GridBagConstraints.NONE;
        content.add(rounded, gbc);

        setContentPane(content);
    }

    public void setStatus(String text) {
        if (text != null && !text.equals(statusLabel.getText())) {
            statusLabel.setText(text);
        }
    }

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
