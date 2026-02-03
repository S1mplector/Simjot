/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoglu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.dialog.security;

import main.core.security.LockUtil;
import main.core.service.SettingsStore;
import main.infrastructure.io.ResourceLoader;
import main.ui.features.home.BackgroundPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Elegant full-screen lock screen with beautiful animations, clock display,
 * and refined visual design inspired by modern OS lock screens.
 */
public class ElegantLockScreen extends JDialog {
    
    // Color palette
    private static final Color ACCENT = new Color(99, 102, 241);
    private static final Color ACCENT_LIGHT = new Color(129, 140, 248);
    private static final Color TEXT_WHITE = new Color(255, 255, 255);
    private static final Color TEXT_WHITE_MUTED = new Color(255, 255, 255, 180);
    private static final Color GLASS_BG = new Color(255, 255, 255, 35);
    private static final Color GLASS_BORDER = new Color(255, 255, 255, 60);
    private static final Color FIELD_BG = new Color(0, 0, 0, 40);
    private static final Color FIELD_BG_FOCUS = new Color(0, 0, 0, 60);
    private static final Color ERROR_COLOR = new Color(248, 113, 113);
    
    private final JPasswordField passwordField;
    private final JLabel clockLabel;
    private final JLabel dateLabel;
    private final JLabel errorLabel;
    private final JPanel glassCard;
    private boolean unlocked = false;
    private final boolean fullScreen;
    private float fadeProgress = 0f;
    private Timer fadeTimer;
    private Timer clockTimer;
    private Timer errorFadeTimer;
    private float errorAlpha = 0f;

    public ElegantLockScreen(Frame owner) {
        this(owner, true);
    }

    public ElegantLockScreen(Frame owner, boolean fullScreen) {
        super(owner, "Unlock", true);
        this.fullScreen = fullScreen;
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        
        passwordField = new JPasswordField(20);
        clockLabel = new JLabel();
        dateLabel = new JLabel();
        errorLabel = new JLabel(" ");
        glassCard = createGlassCard();
        
        buildUI();
        setupBounds(owner);
        setupAnimations();
        
        try { setAlwaysOnTop(true); } catch (Throwable ignored) {}
    }

    private void setupBounds(Frame owner) {
        if (fullScreen) {
            Rectangle bounds;
            try {
                if (owner != null && owner.getGraphicsConfiguration() != null) {
                    bounds = owner.getGraphicsConfiguration().getBounds();
                } else {
                    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
                    GraphicsDevice gd = ge.getDefaultScreenDevice();
                    bounds = gd.getDefaultConfiguration().getBounds();
                }
            } catch (Throwable t) {
                Dimension s = Toolkit.getDefaultToolkit().getScreenSize();
                bounds = new Rectangle(0, 0, s.width, s.height);
            }
            setBounds(bounds);
            setLocation(bounds.x, bounds.y);
        } else {
            try {
                Point p = owner.getLocationOnScreen();
                Dimension d = owner.getSize();
                setBounds(p.x, p.y, d.width, d.height);
            } catch (Throwable t) {
                Dimension s = Toolkit.getDefaultToolkit().getScreenSize();
                setBounds(0, 0, s.width, s.height);
            }
        }
    }

    private void setupAnimations() {
        // Fade-in animation
        fadeTimer = new Timer(20, e -> {
            fadeProgress = Math.min(1f, fadeProgress + 0.06f);
            repaint();
            if (fadeProgress >= 1f) {
                fadeTimer.stop();
            }
        });
        
        // Clock update timer
        clockTimer = new Timer(1000, e -> updateClock());
        
        // Error fade timer
        errorFadeTimer = new Timer(50, e -> {
            errorAlpha = Math.max(0f, errorAlpha - 0.05f);
            if (errorAlpha <= 0f) {
                errorFadeTimer.stop();
                errorLabel.setText(" ");
            }
            errorLabel.repaint();
        });
    }

    @Override
    public void setVisible(boolean visible) {
        if (visible) {
            fadeProgress = 0f;
            updateClock();
            fadeTimer.start();
            clockTimer.start();
            SwingUtilities.invokeLater(() -> passwordField.requestFocusInWindow());
        } else {
            clockTimer.stop();
        }
        super.setVisible(visible);
    }

    private void buildUI() {
        JComponent content = createBackground();
        content.setLayout(new GridBagLayout());

        // Main container with vertical layout
        JPanel mainContainer = new JPanel();
        mainContainer.setOpaque(false);
        mainContainer.setLayout(new BoxLayout(mainContainer, BoxLayout.Y_AXIS));

        // Time display
        JPanel timePanel = createTimePanel();
        timePanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainContainer.add(timePanel);
        mainContainer.add(Box.createVerticalStrut(40));

        // Glass card with unlock UI
        glassCard.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainContainer.add(glassCard);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.anchor = GridBagConstraints.CENTER;
        content.add(mainContainer, gbc);

        setContentPane(content);
    }

    private JPanel createTimePanel() {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                // Transparent - just container
            }
        };
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // Clock
        clockLabel.setFont(new Font("SF Pro Display", Font.PLAIN, 72));
        clockLabel.setForeground(TEXT_WHITE);
        clockLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(clockLabel);

        panel.add(Box.createVerticalStrut(8));

        // Date
        dateLabel.setFont(new Font("SF Pro Text", Font.PLAIN, 18));
        dateLabel.setForeground(TEXT_WHITE_MUTED);
        dateLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(dateLabel);

        updateClock();
        return panel;
    }

    private void updateClock() {
        LocalTime now = LocalTime.now();
        clockLabel.setText(now.format(DateTimeFormatter.ofPattern("HH:mm")));
        
        java.time.LocalDate today = java.time.LocalDate.now();
        String dayName = today.getDayOfWeek().toString();
        dayName = dayName.charAt(0) + dayName.substring(1).toLowerCase();
        String monthName = today.getMonth().toString();
        monthName = monthName.charAt(0) + monthName.substring(1).toLowerCase();
        dateLabel.setText(dayName + ", " + monthName + " " + today.getDayOfMonth());
    }

    private JPanel createGlassCard() {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int w = getWidth(), h = getHeight();
                float alpha = fadeProgress;
                
                // Glass background with blur simulation
                g2.setColor(new Color(GLASS_BG.getRed(), GLASS_BG.getGreen(), GLASS_BG.getBlue(), 
                    (int)(GLASS_BG.getAlpha() * alpha)));
                g2.fill(new RoundRectangle2D.Float(0, 0, w, h, 32, 32));
                
                // Top highlight
                GradientPaint highlight = new GradientPaint(
                    0, 0, new Color(255, 255, 255, (int)(40 * alpha)),
                    0, h * 0.4f, new Color(255, 255, 255, 0)
                );
                g2.setPaint(highlight);
                g2.fill(new RoundRectangle2D.Float(0, 0, w, h, 32, 32));
                
                // Border
                g2.setColor(new Color(GLASS_BORDER.getRed(), GLASS_BORDER.getGreen(), GLASS_BORDER.getBlue(),
                    (int)(GLASS_BORDER.getAlpha() * alpha)));
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 32, 32));
                
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(36, 44, 36, 44));

        // Lock icon
        JPanel lockIcon = new AnimatedLockIcon();
        lockIcon.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(lockIcon);
        card.add(Box.createVerticalStrut(20));

        // Welcome text
        JLabel title = new JLabel("Welcome Back");
        title.setFont(new Font("SF Pro Display", Font.BOLD, 24));
        title.setForeground(TEXT_WHITE);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(title);
        card.add(Box.createVerticalStrut(8));

        JLabel subtitle = new JLabel("Enter your password to unlock");
        subtitle.setFont(new Font("SF Pro Text", Font.PLAIN, 14));
        subtitle.setForeground(TEXT_WHITE_MUTED);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(subtitle);
        card.add(Box.createVerticalStrut(28));

        // Password field
        JPanel fieldWrapper = createPasswordField();
        fieldWrapper.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(fieldWrapper);
        card.add(Box.createVerticalStrut(8));

        // Error label
        errorLabel.setFont(new Font("SF Pro Text", Font.PLAIN, 13));
        errorLabel.setForeground(ERROR_COLOR);
        errorLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(errorLabel);
        card.add(Box.createVerticalStrut(20));

        // Unlock button
        JButton unlockBtn = createUnlockButton();
        unlockBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(unlockBtn);

        card.setPreferredSize(new Dimension(380, 380));
        card.setMaximumSize(new Dimension(380, 380));
        return card;
    }

    private JPanel createPasswordField() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 0)) {
            private boolean focused = false;
            private float focusAnim = 0f;
            private Timer animTimer;
            {
                animTimer = new Timer(16, e -> {
                    float target = focused ? 1f : 0f;
                    focusAnim += (target - focusAnim) * 0.2f;
                    if (Math.abs(focusAnim - target) < 0.01f) {
                        focusAnim = target;
                        ((Timer)e.getSource()).stop();
                    }
                    repaint();
                });
                passwordField.addFocusListener(new FocusAdapter() {
                    @Override public void focusGained(FocusEvent e) { focused = true; animTimer.start(); }
                    @Override public void focusLost(FocusEvent e) { focused = false; animTimer.start(); }
                });
            }
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int w = getWidth(), h = getHeight();
                
                // Glow when focused
                if (focusAnim > 0) {
                    for (int i = 0; i < 6; i++) {
                        int glowAlpha = (int)((6 - i) * 6 * focusAnim);
                        g2.setColor(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), glowAlpha));
                        g2.fill(new RoundRectangle2D.Float(-i, -i, w + i * 2, h + i * 2, 14 + i, 14 + i));
                    }
                }
                
                // Background
                Color bg = focused ? FIELD_BG_FOCUS : FIELD_BG;
                g2.setColor(bg);
                g2.fill(new RoundRectangle2D.Float(0, 0, w, h, 12, 12));
                
                // Border
                Color borderColor = new Color(
                    (int)(255 * (0.3f + focusAnim * 0.3f)),
                    (int)(255 * (0.3f + focusAnim * 0.3f)),
                    (int)(255 * (0.3f + focusAnim * 0.4f)),
                    (int)(80 + 80 * focusAnim)
                );
                g2.setColor(borderColor);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 12, 12));
                
                g2.dispose();
            }
        };
        wrapper.setOpaque(false);

        passwordField.setBorder(new EmptyBorder(14, 18, 14, 14));
        passwordField.setOpaque(false);
        passwordField.setFont(new Font("SF Pro Text", Font.PLAIN, 15));
        passwordField.setForeground(TEXT_WHITE);
        passwordField.setCaretColor(TEXT_WHITE);
        passwordField.setEchoChar('●');
        passwordField.addActionListener(e -> tryUnlock());
        wrapper.add(passwordField, BorderLayout.CENTER);

        // Eye toggle
        JLabel toggle = createEyeToggle();
        wrapper.add(toggle, BorderLayout.EAST);

        wrapper.setPreferredSize(new Dimension(280, 52));
        wrapper.setMaximumSize(new Dimension(280, 52));
        return wrapper;
    }

    private JLabel createEyeToggle() {
        return new JLabel() {
            private boolean hovered = false;
            private boolean visible = false;
            {
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
                    @Override public void mouseExited(MouseEvent e) { hovered = false; repaint(); }
                    @Override public void mouseClicked(MouseEvent e) {
                        visible = !visible;
                        passwordField.setEchoChar(visible ? (char) 0 : '●');
                        repaint();
                    }
                });
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int w = getWidth(), h = getHeight();
                int cx = w / 2, cy = h / 2;
                
                g2.setColor(hovered ? TEXT_WHITE : TEXT_WHITE_MUTED);
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                
                // Eye shape
                g2.draw(new Arc2D.Float(cx - 9, cy - 5, 18, 10, 0, 180, Arc2D.OPEN));
                g2.draw(new Arc2D.Float(cx - 9, cy - 5, 18, 10, 180, 180, Arc2D.OPEN));
                g2.fillOval(cx - 3, cy - 3, 6, 6);
                
                if (!visible) {
                    g2.drawLine(cx - 10, cy + 6, cx + 10, cy - 6);
                }
                
                g2.dispose();
            }
            @Override
            public Dimension getPreferredSize() { return new Dimension(48, 52); }
        };
    }

    private JButton createUnlockButton() {
        JButton btn = new JButton("Unlock") {
            private float hoverAnim = 0f;
            private Timer animTimer = new Timer(16, null);
            {
                animTimer.addActionListener(e -> {
                    float target = getModel().isRollover() ? 1f : 0f;
                    hoverAnim += (target - hoverAnim) * 0.2f;
                    if (Math.abs(hoverAnim - target) < 0.01f) { hoverAnim = target; animTimer.stop(); }
                    repaint();
                });
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { animTimer.start(); }
                    @Override public void mouseExited(MouseEvent e) { animTimer.start(); }
                });
            }
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int w = getWidth(), h = getHeight();
                
                // Glow
                for (int i = 0; i < 8; i++) {
                    int alpha = (int)((8 - i) * 5 * (0.6f + hoverAnim * 0.4f));
                    g2.setColor(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), alpha));
                    g2.fill(new RoundRectangle2D.Float(i, i + 2, w - i * 2, h - i * 2, 14 - i, 14 - i));
                }
                
                // Background
                Color top = blendColors(ACCENT, ACCENT_LIGHT, hoverAnim * 0.4f);
                Color bottom = getModel().isPressed() ? ACCENT.darker() : ACCENT;
                g2.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
                g2.fill(new RoundRectangle2D.Float(0, 0, w, h, 12, 12));
                
                // Inner shine
                g2.setColor(new Color(255, 255, 255, (int)(35 + hoverAnim * 15)));
                g2.fill(new RoundRectangle2D.Float(1, 1, w - 2, h / 2f - 1, 11, 11));
                
                // Text
                g2.setColor(Color.WHITE);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(), (w - fm.stringWidth(getText())) / 2, (h + fm.getAscent() - fm.getDescent()) / 2);
                
                g2.dispose();
            }
        };
        btn.setFont(new Font("SF Pro Text", Font.BOLD, 15));
        btn.setPreferredSize(new Dimension(280, 50));
        btn.setMaximumSize(new Dimension(280, 50));
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> tryUnlock());
        getRootPane().setDefaultButton(btn);
        return btn;
    }

    private JComponent createBackground() {
        String bgPath = SettingsStore.get().getBackgroundImage();
        JPanel content;
        
        if (bgPath != null && !bgPath.isEmpty()) {
            if (bgPath.startsWith("gen:")) {
                Image img = main.ui.features.gallery.GeneratedWallpapers.render(bgPath, 2560, 1440);
                content = new DimmedBackgroundPanel(img);
            } else if (bgPath.startsWith("res:")) {
                String resPath = bgPath.substring(4);
                Image img = ResourceLoader.createImage("Simjot/" + resPath);
                if (img != null) {
                    content = new DimmedBackgroundPanel(img);
                } else {
                    content = createDefaultBackground();
                }
            } else {
                BackgroundPanel bg = new BackgroundPanel(bgPath);
                bg.setOpacityOverride(1.0f);
                content = new DimmedBackgroundPanel(bg);
            }
        } else {
            content = createDefaultBackground();
        }
        return content;
    }

    private JPanel createDefaultBackground() {
        return new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                int w = getWidth(), h = getHeight();
                
                // Gradient background
                GradientPaint gp = new GradientPaint(
                    0, 0, new Color(30, 41, 59),
                    w, h, new Color(15, 23, 42)
                );
                g2.setPaint(gp);
                g2.fillRect(0, 0, w, h);
                g2.dispose();
            }
        };
    }

    private void tryUnlock() {
        SettingsStore s = SettingsStore.get();
        String salt = s.getLockPasswordSalt();
        String hash = s.getLockPasswordHash();
        
        if (hash == null || hash.isBlank()) {
            unlocked = true;
            setVisible(false);
            dispose();
            return;
        }
        
        char[] pw = passwordField.getPassword();
        boolean ok = LockUtil.verify(new String(pw), salt, hash);
        
        if (ok) {
            unlocked = true;
            setVisible(false);
            dispose();
            try {
                Window w = getOwner();
                if (w instanceof JFrame jf) {
                    jf.setExtendedState(jf.getExtendedState() | JFrame.MAXIMIZED_BOTH);
                    jf.toFront();
                    jf.requestFocus();
                }
            } catch (Throwable ignored) {}
        } else {
            showError("Incorrect password");
            shakeCard();
            passwordField.setText("");
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorAlpha = 1f;
        errorFadeTimer.stop();
        // Start fade after delay
        Timer delayTimer = new Timer(2000, e -> {
            errorFadeTimer.start();
            ((Timer)e.getSource()).stop();
        });
        delayTimer.setRepeats(false);
        delayTimer.start();
    }

    private void shakeCard() {
        Point loc = glassCard.getLocation();
        Timer shake = new Timer(25, null);
        int[] frame = {0};
        shake.addActionListener(e -> {
            if (frame[0] >= 8) {
                shake.stop();
                glassCard.setLocation(loc);
                return;
            }
            int offset = (frame[0] % 2 == 0) ? 12 : -12;
            offset = (int)(offset * (1 - frame[0] / 8f));
            glassCard.setLocation(loc.x + offset, loc.y);
            frame[0]++;
        });
        shake.start();
    }

    public boolean blockUntilUnlocked() {
        setVisible(true);
        return unlocked;
    }

    private static Color blendColors(Color c1, Color c2, float ratio) {
        float ir = 1f - ratio;
        return new Color(
            (int)(c1.getRed() * ir + c2.getRed() * ratio),
            (int)(c1.getGreen() * ir + c2.getGreen() * ratio),
            (int)(c1.getBlue() * ir + c2.getBlue() * ratio)
        );
    }

    /**
     * Background panel with dimming overlay.
     */
    private class DimmedBackgroundPanel extends JPanel {
        private final Image bgImage;
        private final JPanel sourcePanel;

        DimmedBackgroundPanel(Image img) {
            this.bgImage = img;
            this.sourcePanel = null;
            setOpaque(false);
        }

        DimmedBackgroundPanel(JPanel source) {
            this.bgImage = null;
            this.sourcePanel = source;
            setLayout(new BorderLayout());
            add(source, BorderLayout.CENTER);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth(), h = getHeight();

            if (bgImage != null) {
                // Scale and center image
                int iw = bgImage.getWidth(null);
                int ih = bgImage.getHeight(null);
                float scale = Math.max((float) w / iw, (float) h / ih);
                int sw = (int)(iw * scale);
                int sh = (int)(ih * scale);
                int x = (w - sw) / 2;
                int y = (h - sh) / 2;
                g2.drawImage(bgImage, x, y, sw, sh, null);
            }

            // Dim overlay
            g2.setColor(new Color(0, 0, 0, 100));
            g2.fillRect(0, 0, w, h);
            
            g2.dispose();
        }
    }

    /**
     * Animated lock icon with subtle pulse effect.
     */
    private class AnimatedLockIcon extends JPanel {
        private float pulsePhase = 0f;
        private Timer pulseTimer;

        AnimatedLockIcon() {
            setOpaque(false);
            setPreferredSize(new Dimension(80, 80));
            setMaximumSize(new Dimension(80, 80));
            
            // Slower, smoother pulse
            pulseTimer = new Timer(60, e -> {
                pulsePhase += 0.06f;
                if (pulsePhase > Math.PI * 2) pulsePhase -= Math.PI * 2;
                repaint();
            });
            pulseTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            int cx = w / 2, cy = h / 2;

            // Subtle pulse - reduced intensity
            float pulse = 1f + (float)Math.sin(pulsePhase) * 0.02f;
            float glowIntensity = 0.3f + (float)Math.sin(pulsePhase) * 0.1f;
            float alpha = fadeProgress;

            // Outer glow - reduced layers and intensity
            for (int i = 0; i < 8; i++) {
                int glowAlpha = (int)((8 - i) * 3 * glowIntensity * alpha);
                g2.setColor(new Color(255, 255, 255, Math.min(glowAlpha, 255)));
                float size = (28 + i * 2f) * pulse;
                g2.fill(new Ellipse2D.Float(cx - size / 2, cy - size / 2, size, size));
            }

            // Background circle
            g2.setColor(new Color(255, 255, 255, (int)(15 * alpha)));
            float bgSize = 48 * pulse;
            g2.fill(new Ellipse2D.Float(cx - bgSize / 2, cy - bgSize / 2, bgSize, bgSize));

            // Lock body
            g2.setColor(new Color(255, 255, 255, (int)(240 * alpha)));
            int bodyW = 22, bodyH = 17;
            int bodyX = cx - bodyW / 2, bodyY = cy;
            g2.fill(new RoundRectangle2D.Float(bodyX, bodyY, bodyW, bodyH, 4, 4));

            // Lock shackle
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int shackleW = 14, shackleH = 12;
            g2.drawArc(cx - shackleW / 2, bodyY - shackleH, shackleW, shackleH * 2, 0, 180);

            // Keyhole
            g2.setColor(new Color(30, 41, 59, (int)(255 * alpha)));
            g2.fillOval(cx - 2, bodyY + 4, 4, 4);
            g2.fillRect(cx - 1, bodyY + 7, 3, 5);

            g2.dispose();
        }
    }
}
