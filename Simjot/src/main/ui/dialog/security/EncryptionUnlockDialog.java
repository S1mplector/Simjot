/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.dialog.security;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import main.ui.components.containers.FrostedGlassPanel;

/**
 * Modern encryption password unlock dialog with improved UX.
 */
public class EncryptionUnlockDialog extends JDialog {
    
    private static final Color ACCENT = new Color(88, 86, 214);
    private static final Color ACCENT_HOVER = new Color(108, 106, 234);
    private static final Color TEXT_PRIMARY = new Color(30, 30, 30);
    private static final Color TEXT_SECONDARY = new Color(100, 100, 100);
    private static final Color FIELD_BG = new Color(255, 255, 255, 230);
    private static final Color FIELD_BORDER = new Color(200, 200, 200);
    private static final Color FIELD_BORDER_FOCUS = ACCENT;
    
    public static final class Result {
        public final char[] password;
        public final boolean remember;

        private Result(char[] password, boolean remember) {
            this.password = password;
            this.remember = remember;
        }
    }

    private final JPasswordField passwordField = new JPasswordField(20);
    private final JCheckBox rememberCheck = new JCheckBox("Remember for this session");
    private boolean confirmed = false;
    private char[] password = null;
    private boolean passwordVisible = false;

    public EncryptionUnlockDialog(Frame owner) {
        super(owner, "Unlock Encryption", true);
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        buildUI();
        pack();
        setSize(400, 320);
        setLocationRelativeTo(owner);
    }

    public static Result prompt(java.awt.Component parent) {
        Window w = parent instanceof Window ? (Window) parent : SwingUtilities.getWindowAncestor(parent);
        Frame owner = (w instanceof Frame) ? (Frame) w : null;
        EncryptionUnlockDialog d = new EncryptionUnlockDialog(owner);
        d.setVisible(true);
        if (!d.confirmed || d.password == null) return null;
        return new Result(d.password, d.rememberCheck.isSelected());
    }

    private void buildUI() {
        FrostedGlassPanel root = new FrostedGlassPanel(new BorderLayout(), 20);
        root.setBorder(BorderFactory.createEmptyBorder(28, 32, 24, 32));

        // Header with lock icon
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(0, 0, 20, 0));

        // Lock icon
        JPanel iconPanel = new LockIcon();
        iconPanel.setAlignmentX(JPanel.CENTER_ALIGNMENT);
        header.add(iconPanel);
        header.add(Box.createVerticalStrut(16));

        // Title
        JLabel title = new JLabel("Unlock Encrypted Content");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setForeground(TEXT_PRIMARY);
        title.setAlignmentX(JLabel.CENTER_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(6));

        // Subtitle
        JLabel subtitle = new JLabel("Enter your encryption password to continue");
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 12f));
        subtitle.setForeground(TEXT_SECONDARY);
        subtitle.setAlignmentX(JLabel.CENTER_ALIGNMENT);
        header.add(subtitle);

        root.add(header, BorderLayout.NORTH);

        // Center content
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(0, 0, 0, 0);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;
        gc.gridx = 0;
        gc.gridy = 0;

        // Password field with show/hide toggle
        JPanel fieldWrapper = createPasswordFieldWrapper();
        center.add(fieldWrapper, gc);

        gc.gridy = 1;
        gc.insets = new Insets(12, 0, 0, 0);
        rememberCheck.setOpaque(false);
        rememberCheck.setFont(rememberCheck.getFont().deriveFont(12f));
        rememberCheck.setForeground(TEXT_SECONDARY);
        rememberCheck.setSelected(true);
        rememberCheck.setFocusPainted(false);
        center.add(rememberCheck, gc);

        root.add(center, BorderLayout.CENTER);

        // Buttons
        JPanel btns = new JPanel(new GridBagLayout());
        btns.setOpaque(false);
        btns.setBorder(new EmptyBorder(24, 0, 0, 0));
        GridBagConstraints bc = new GridBagConstraints();
        bc.fill = GridBagConstraints.HORIZONTAL;
        bc.weightx = 0.5;
        bc.insets = new Insets(0, 0, 0, 6);
        bc.gridx = 0;

        JButton cancel = createButton("Cancel", false);
        cancel.addActionListener(e -> {
            setVisible(false);
            dispose();
        });
        btns.add(cancel, bc);

        bc.gridx = 1;
        bc.insets = new Insets(0, 6, 0, 0);
        JButton ok = createButton("Unlock", true);
        ok.addActionListener(e -> confirm());
        btns.add(ok, bc);

        root.add(btns, BorderLayout.SOUTH);

        setContentPane(root);
        getRootPane().setDefaultButton(ok);
        
        // Focus password field on open
        SwingUtilities.invokeLater(() -> passwordField.requestFocusInWindow());
    }

    private JPanel createPasswordFieldWrapper() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 0)) {
            private boolean focused = false;
            {
                passwordField.addFocusListener(new java.awt.event.FocusAdapter() {
                    @Override public void focusGained(java.awt.event.FocusEvent e) { focused = true; repaint(); }
                    @Override public void focusLost(java.awt.event.FocusEvent e) { focused = false; repaint(); }
                });
            }
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(FIELD_BG);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
                g2.setColor(focused ? FIELD_BORDER_FOCUS : FIELD_BORDER);
                g2.setStroke(new BasicStroke(focused ? 2f : 1f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 12, 12));
                g2.dispose();
            }
        };
        wrapper.setOpaque(false);
        wrapper.setBorder(new EmptyBorder(0, 0, 0, 0));

        passwordField.setBorder(new EmptyBorder(12, 14, 12, 8));
        passwordField.setOpaque(false);
        passwordField.setFont(passwordField.getFont().deriveFont(14f));
        passwordField.setEchoChar('•');
        wrapper.add(passwordField, BorderLayout.CENTER);

        // Show/hide toggle
        JLabel toggle = new JLabel("Show") {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
            }
        };
        toggle.setFont(toggle.getFont().deriveFont(Font.PLAIN, 11f));
        toggle.setForeground(ACCENT);
        toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        toggle.setBorder(new EmptyBorder(0, 8, 0, 14));
        toggle.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                passwordVisible = !passwordVisible;
                passwordField.setEchoChar(passwordVisible ? (char) 0 : '•');
                toggle.setText(passwordVisible ? "Hide" : "Show");
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                toggle.setForeground(ACCENT_HOVER);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                toggle.setForeground(ACCENT);
            }
        });
        wrapper.add(toggle, BorderLayout.EAST);

        wrapper.setPreferredSize(new Dimension(300, 48));
        return wrapper;
    }

    private JButton createButton(String text, boolean primary) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                Color bg = primary ? ACCENT : new Color(240, 240, 240);
                Color fg = primary ? Color.WHITE : TEXT_PRIMARY;
                
                if (getModel().isPressed()) {
                    bg = primary ? ACCENT.darker() : new Color(220, 220, 220);
                } else if (getModel().isRollover()) {
                    bg = primary ? ACCENT_HOVER : new Color(230, 230, 230);
                }
                
                g2.setColor(bg);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
                
                g2.setColor(fg);
                g2.setFont(getFont());
                java.awt.FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), x, y);
                g2.dispose();
            }
        };
        btn.setFont(btn.getFont().deriveFont(Font.BOLD, 13f));
        btn.setPreferredSize(new Dimension(140, 42));
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private void confirm() {
        this.password = passwordField.getPassword();
        this.confirmed = true;
        setVisible(false);
        dispose();
    }

    /**
     * Custom lock icon component.
     */
    private static class LockIcon extends JPanel {
        LockIcon() {
            setOpaque(false);
            setPreferredSize(new Dimension(56, 56));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int cx = w / 2;
            int cy = h / 2;

            // Background circle
            g2.setColor(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 30));
            g2.fillOval(0, 0, w, h);

            // Lock body
            g2.setColor(ACCENT);
            int bodyW = 20;
            int bodyH = 16;
            int bodyX = cx - bodyW / 2;
            int bodyY = cy - 2;
            g2.fill(new RoundRectangle2D.Float(bodyX, bodyY, bodyW, bodyH, 4, 4));

            // Lock shackle
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int shackleW = 12;
            int shackleH = 10;
            g2.drawArc(cx - shackleW / 2, bodyY - shackleH, shackleW, shackleH * 2, 0, 180);

            // Keyhole
            g2.setColor(new Color(255, 255, 255, 200));
            int khSize = 4;
            g2.fillOval(cx - khSize / 2, bodyY + 4, khSize, khSize);
            g2.fillRect(cx - 1, bodyY + 7, 2, 5);

            g2.dispose();
        }
    }
}
