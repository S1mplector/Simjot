/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoglu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.dialog.security;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * Elegant, modern unlock dialog for encrypted note content.
 * Features smooth animations, refined typography, and a polished glass aesthetic.
 */
public class ElegantUnlockDialog extends JDialog {
    
    // Refined color palette
    private static final Color ACCENT = new Color(99, 102, 241);       // Indigo
    private static final Color ACCENT_LIGHT = new Color(129, 140, 248);
    private static final Color ACCENT_GLOW = new Color(99, 102, 241, 40);
    private static final Color TEXT_PRIMARY = new Color(17, 24, 39);
    private static final Color TEXT_SECONDARY = new Color(107, 114, 128);
    private static final Color TEXT_MUTED = new Color(156, 163, 175);
    private static final Color SURFACE = new Color(255, 255, 255, 245);
    private static final Color SURFACE_ELEVATED = new Color(249, 250, 251);
    private static final Color BORDER_SUBTLE = new Color(229, 231, 235);
    private static final Color BORDER_FOCUS = new Color(99, 102, 241, 180);
    private static final Color ERROR_COLOR = new Color(239, 68, 68);
    private static final Color ERROR_BG = new Color(254, 242, 242);
    
    public static final class Result {
        public final char[] password;
        public final boolean remember;
        private Result(char[] password, boolean remember) {
            this.password = password;
            this.remember = remember;
        }
    }

    private final JPasswordField passwordField;
    private final JCheckBox rememberCheck;
    private final JLabel errorLabel;
    private final AnimatedLockIcon lockIcon;
    private final JButton unlockButton;
    private boolean confirmed = false;
    private char[] password = null;
    private boolean passwordVisible = false;
    private float fadeProgress = 0f;
    private Timer fadeTimer;

    public ElegantUnlockDialog(Frame owner) {
        super(owner, "Unlock", true);
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        
        passwordField = new JPasswordField(24);
        rememberCheck = new JCheckBox("Remember for this session");
        errorLabel = new JLabel(" ");
        lockIcon = new AnimatedLockIcon();
        unlockButton = createPrimaryButton("Unlock");
        
        buildUI();
        setSize(420, 400);
        setLocationRelativeTo(owner);
        
        // Fade-in animation
        fadeTimer = new Timer(16, e -> {
            fadeProgress = Math.min(1f, fadeProgress + 0.08f);
            repaint();
            if (fadeProgress >= 1f) {
                fadeTimer.stop();
                lockIcon.startPulse();
            }
        });
    }

    public static Result prompt(Component parent) {
        Window w = parent instanceof Window ? (Window) parent : SwingUtilities.getWindowAncestor(parent);
        Frame owner = (w instanceof Frame) ? (Frame) w : null;
        ElegantUnlockDialog d = new ElegantUnlockDialog(owner);
        d.setVisible(true);
        if (!d.confirmed || d.password == null) return null;
        return new Result(d.password, d.rememberCheck.isSelected());
    }
    
    @Override
    public void setVisible(boolean visible) {
        if (visible) {
            fadeProgress = 0f;
            fadeTimer.start();
        }
        super.setVisible(visible);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int w = getWidth(), h = getHeight();
                float alpha = fadeProgress;
                
                // Outer shadow
                for (int i = 0; i < 20; i++) {
                    float shadowAlpha = (20 - i) * 0.008f * alpha;
                    g2.setColor(new Color(0, 0, 0, (int)(shadowAlpha * 255)));
                    g2.fill(new RoundRectangle2D.Float(i, i + 2, w - i * 2, h - i * 2, 28 - i, 28 - i));
                }
                
                // Main card background
                g2.setColor(new Color(255, 255, 255, (int)(250 * alpha)));
                g2.fill(new RoundRectangle2D.Float(0, 0, w, h, 24, 24));
                
                // Subtle top gradient sheen
                GradientPaint sheen = new GradientPaint(
                    0, 0, new Color(255, 255, 255, (int)(80 * alpha)),
                    0, h * 0.3f, new Color(255, 255, 255, 0)
                );
                g2.setPaint(sheen);
                g2.fill(new RoundRectangle2D.Float(0, 0, w, h, 24, 24));
                
                // Border
                g2.setColor(new Color(229, 231, 235, (int)(180 * alpha)));
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 24, 24));
                
                g2.dispose();
            }
        };
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(40, 40, 36, 40));

        // Header
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(0, 0, 28, 0));

        lockIcon.setAlignmentX(Component.CENTER_ALIGNMENT);
        header.add(lockIcon);
        header.add(Box.createVerticalStrut(20));

        JLabel title = new JLabel("Unlock Content");
        title.setFont(new Font("SF Pro Display", Font.BOLD, 22));
        title.setForeground(TEXT_PRIMARY);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(8));

        JLabel subtitle = new JLabel("Enter your password to decrypt");
        subtitle.setFont(new Font("SF Pro Text", Font.PLAIN, 14));
        subtitle.setForeground(TEXT_SECONDARY);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        header.add(subtitle);

        root.add(header, BorderLayout.NORTH);

        // Center
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);

        JPanel fieldWrapper = createPasswordField();
        fieldWrapper.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(fieldWrapper);
        
        center.add(Box.createVerticalStrut(8));
        
        errorLabel.setFont(new Font("SF Pro Text", Font.PLAIN, 12));
        errorLabel.setForeground(ERROR_COLOR);
        errorLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(errorLabel);

        center.add(Box.createVerticalStrut(16));

        rememberCheck.setOpaque(false);
        rememberCheck.setFont(new Font("SF Pro Text", Font.PLAIN, 13));
        rememberCheck.setForeground(TEXT_SECONDARY);
        rememberCheck.setSelected(true);
        rememberCheck.setFocusPainted(false);
        rememberCheck.setAlignmentX(Component.CENTER_ALIGNMENT);
        rememberCheck.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        center.add(rememberCheck);

        root.add(center, BorderLayout.CENTER);

        // Buttons
        JPanel buttons = new JPanel(new GridLayout(1, 2, 12, 0));
        buttons.setOpaque(false);
        buttons.setBorder(new EmptyBorder(28, 0, 0, 0));

        JButton cancel = createSecondaryButton("Cancel");
        cancel.addActionListener(e -> {
            confirmed = false;
            setVisible(false);
            dispose();
        });
        buttons.add(cancel);

        unlockButton.addActionListener(e -> confirm());
        buttons.add(unlockButton);

        root.add(buttons, BorderLayout.SOUTH);

        setContentPane(root);
        getRootPane().setDefaultButton(unlockButton);
        
        // Escape to close
        getRootPane().registerKeyboardAction(
            e -> { confirmed = false; setVisible(false); dispose(); },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        
        SwingUtilities.invokeLater(() -> passwordField.requestFocusInWindow());
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
                        animTimer.stop();
                    }
                    repaint();
                });
                passwordField.addFocusListener(new FocusAdapter() {
                    @Override public void focusGained(FocusEvent e) { 
                        focused = true; 
                        animTimer.start();
                    }
                    @Override public void focusLost(FocusEvent e) { 
                        focused = false; 
                        animTimer.start();
                    }
                });
            }
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int w = getWidth(), h = getHeight();
                
                // Glow effect when focused
                if (focusAnim > 0) {
                    for (int i = 0; i < 8; i++) {
                        int glowAlpha = (int)((8 - i) * 4 * focusAnim);
                        g2.setColor(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), glowAlpha));
                        g2.fill(new RoundRectangle2D.Float(-i, -i, w + i * 2, h + i * 2, 14 + i, 14 + i));
                    }
                }
                
                // Background
                g2.setColor(SURFACE_ELEVATED);
                g2.fill(new RoundRectangle2D.Float(0, 0, w, h, 12, 12));
                
                // Border
                Color borderColor = new Color(
                    (int)(BORDER_SUBTLE.getRed() + (BORDER_FOCUS.getRed() - BORDER_SUBTLE.getRed()) * focusAnim),
                    (int)(BORDER_SUBTLE.getGreen() + (BORDER_FOCUS.getGreen() - BORDER_SUBTLE.getGreen()) * focusAnim),
                    (int)(BORDER_SUBTLE.getBlue() + (BORDER_FOCUS.getBlue() - BORDER_SUBTLE.getBlue()) * focusAnim),
                    (int)(180 + 75 * focusAnim)
                );
                g2.setColor(borderColor);
                g2.setStroke(new BasicStroke(focused ? 2f : 1f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 12, 12));
                
                g2.dispose();
            }
        };
        wrapper.setOpaque(false);

        passwordField.setBorder(new EmptyBorder(14, 16, 14, 12));
        passwordField.setOpaque(false);
        passwordField.setFont(new Font("SF Pro Text", Font.PLAIN, 15));
        passwordField.setForeground(TEXT_PRIMARY);
        passwordField.setCaretColor(ACCENT);
        passwordField.setEchoChar('●');
        wrapper.add(passwordField, BorderLayout.CENTER);

        // Toggle visibility
        JLabel toggle = new JLabel() {
            private boolean hovered = false;
            {
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
                    @Override public void mouseExited(MouseEvent e) { hovered = false; repaint(); }
                    @Override public void mouseClicked(MouseEvent e) {
                        passwordVisible = !passwordVisible;
                        passwordField.setEchoChar(passwordVisible ? (char) 0 : '●');
                        repaint();
                    }
                });
            }
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int w = getWidth(), h = getHeight();
                int cx = w / 2, cy = h / 2;
                
                g2.setColor(hovered ? ACCENT : TEXT_MUTED);
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                
                // Eye icon
                g2.draw(new Arc2D.Float(cx - 8, cy - 4, 16, 8, 0, 180, Arc2D.OPEN));
                g2.draw(new Arc2D.Float(cx - 8, cy - 4, 16, 8, 180, 180, Arc2D.OPEN));
                g2.fillOval(cx - 3, cy - 3, 6, 6);
                
                if (!passwordVisible) {
                    g2.drawLine(cx - 10, cy + 6, cx + 10, cy - 6);
                }
                
                g2.dispose();
            }
            @Override
            public Dimension getPreferredSize() { return new Dimension(44, 44); }
        };
        toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        wrapper.add(toggle, BorderLayout.EAST);

        wrapper.setPreferredSize(new Dimension(320, 52));
        wrapper.setMaximumSize(new Dimension(320, 52));
        return wrapper;
    }

    private JButton createPrimaryButton(String text) {
        JButton btn = new JButton(text) {
            private float hoverAnim = 0f;
            private Timer animTimer;
            {
                animTimer = new Timer(16, e -> {
                    float target = getModel().isRollover() ? 1f : 0f;
                    hoverAnim += (target - hoverAnim) * 0.25f;
                    if (Math.abs(hoverAnim - target) < 0.01f) {
                        hoverAnim = target;
                        animTimer.stop();
                    }
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
                
                // Shadow
                for (int i = 0; i < 6; i++) {
                    int alpha = (int)((6 - i) * 8 * (1 + hoverAnim * 0.5f));
                    g2.setColor(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), alpha));
                    g2.fill(new RoundRectangle2D.Float(i, i + 2, w - i * 2, h - i * 2, 12 - i, 12 - i));
                }
                
                // Background gradient
                Color top = blendColors(ACCENT, ACCENT_LIGHT, hoverAnim * 0.3f);
                Color bottom = getModel().isPressed() ? ACCENT.darker() : ACCENT;
                GradientPaint gp = new GradientPaint(0, 0, top, 0, h, bottom);
                g2.setPaint(gp);
                g2.fill(new RoundRectangle2D.Float(0, 0, w, h, 12, 12));
                
                // Inner highlight
                g2.setColor(new Color(255, 255, 255, 40));
                g2.fill(new RoundRectangle2D.Float(1, 1, w - 2, h / 2 - 1, 11, 11));
                
                // Text
                g2.setColor(Color.WHITE);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int tx = (w - fm.stringWidth(getText())) / 2;
                int ty = (h + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), tx, ty);
                
                g2.dispose();
            }
        };
        btn.setFont(new Font("SF Pro Text", Font.BOLD, 14));
        btn.setPreferredSize(new Dimension(140, 48));
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JButton createSecondaryButton(String text) {
        JButton btn = new JButton(text) {
            private float hoverAnim = 0f;
            private Timer animTimer = new Timer(16, null);
            {
                animTimer.addActionListener(e -> {
                    float target = getModel().isRollover() ? 1f : 0f;
                    hoverAnim += (target - hoverAnim) * 0.25f;
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
                
                Color bg = blendColors(SURFACE_ELEVATED, new Color(243, 244, 246), hoverAnim);
                if (getModel().isPressed()) bg = new Color(229, 231, 235);
                g2.setColor(bg);
                g2.fill(new RoundRectangle2D.Float(0, 0, w, h, 12, 12));
                
                g2.setColor(BORDER_SUBTLE);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 12, 12));
                
                g2.setColor(TEXT_PRIMARY);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(), (w - fm.stringWidth(getText())) / 2, (h + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        btn.setFont(new Font("SF Pro Text", Font.PLAIN, 14));
        btn.setPreferredSize(new Dimension(140, 48));
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private void confirm() {
        char[] pw = passwordField.getPassword();
        if (pw.length == 0) {
            showError("Please enter your password");
            shakeDialog();
            return;
        }
        this.password = pw;
        this.confirmed = true;
        setVisible(false);
        dispose();
    }
    
    public void showError(String message) {
        errorLabel.setText(message);
        lockIcon.showError();
    }
    
    private void shakeDialog() {
        Point loc = getLocation();
        Timer shake = new Timer(20, null);
        int[] frame = {0};
        shake.addActionListener(e -> {
            if (frame[0] >= 10) {
                shake.stop();
                setLocation(loc);
                return;
            }
            int offset = (frame[0] % 2 == 0) ? 8 : -8;
            offset = (int)(offset * (1 - frame[0] / 10f));
            setLocation(loc.x + offset, loc.y);
            frame[0]++;
        });
        shake.start();
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
     * Animated lock icon with pulse and error shake effects.
     */
    private class AnimatedLockIcon extends JPanel {
        private float pulsePhase = 0f;
        private float errorFlash = 0f;
        private Timer pulseTimer;
        private Timer errorTimer;
        
        AnimatedLockIcon() {
            setOpaque(false);
            setPreferredSize(new Dimension(72, 72));
            
            pulseTimer = new Timer(50, e -> {
                pulsePhase += 0.1f;
                if (pulsePhase > Math.PI * 2) pulsePhase -= Math.PI * 2;
                repaint();
            });
            
            errorTimer = new Timer(16, e -> {
                errorFlash = Math.max(0, errorFlash - 0.08f);
                if (errorFlash <= 0) errorTimer.stop();
                repaint();
            });
        }
        
        void startPulse() { pulseTimer.start(); }
        void stopPulse() { pulseTimer.stop(); }
        
        void showError() {
            errorFlash = 1f;
            errorTimer.start();
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            int w = getWidth(), h = getHeight();
            int cx = w / 2, cy = h / 2;
            
            float pulse = 1f + (float)Math.sin(pulsePhase) * 0.03f;
            float glowIntensity = 0.5f + (float)Math.sin(pulsePhase) * 0.2f;
            
            // Error state blend
            Color iconColor = blendColors(ACCENT, ERROR_COLOR, errorFlash);
            Color glowColor = blendColors(ACCENT_GLOW, new Color(239, 68, 68, 40), errorFlash);
            
            // Outer glow
            for (int i = 0; i < 15; i++) {
                int alpha = (int)((15 - i) * 2.5f * glowIntensity * fadeProgress);
                g2.setColor(new Color(iconColor.getRed(), iconColor.getGreen(), iconColor.getBlue(), alpha));
                float size = (36 + i * 2) * pulse;
                g2.fill(new Ellipse2D.Float(cx - size / 2, cy - size / 2, size, size));
            }
            
            // Background circle
            g2.setColor(new Color(iconColor.getRed(), iconColor.getGreen(), iconColor.getBlue(), (int)(25 * fadeProgress)));
            float bgSize = 64 * pulse;
            g2.fill(new Ellipse2D.Float(cx - bgSize / 2, cy - bgSize / 2, bgSize, bgSize));
            
            // Lock body
            g2.setColor(new Color(iconColor.getRed(), iconColor.getGreen(), iconColor.getBlue(), (int)(255 * fadeProgress)));
            int bodyW = (int)(26 * pulse), bodyH = (int)(20 * pulse);
            int bodyX = cx - bodyW / 2, bodyY = cy;
            g2.fill(new RoundRectangle2D.Float(bodyX, bodyY, bodyW, bodyH, 5, 5));
            
            // Lock shackle
            g2.setStroke(new BasicStroke(3.5f * pulse, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int shackleW = (int)(16 * pulse), shackleH = (int)(14 * pulse);
            g2.drawArc(cx - shackleW / 2, bodyY - shackleH, shackleW, shackleH * 2, 0, 180);
            
            // Keyhole
            g2.setColor(new Color(255, 255, 255, (int)(220 * fadeProgress)));
            int khSize = (int)(5 * pulse);
            g2.fillOval(cx - khSize / 2, bodyY + (int)(5 * pulse), khSize, khSize);
            g2.fillRect(cx - (int)(1.5f * pulse), bodyY + (int)(9 * pulse), (int)(3 * pulse), (int)(6 * pulse));
            
            g2.dispose();
        }
    }
}
