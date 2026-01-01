/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.dialog.security;

import main.core.security.LockUtil;
import main.core.service.SettingsStore;
import main.infrastructure.io.ResourceLoader;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.input.AeroPasswordField;
import main.ui.features.home.AnalogClockPanel;
import main.ui.features.home.BackgroundPanel;
import main.ui.features.home.TodayCalendarPanel;
import main.ui.theme.aero.AeroTheme;

import javax.swing.*;
import java.awt.*;

/**
 * Full-screen startup lock dialog that shows the user's background, a welcome message,
 * and the main menu clock + calendar, with an Aero-styled password field.
 */
public class LockScreenDialog extends JDialog {
    private final AeroPasswordField passwordField = new AeroPasswordField(18);
    private boolean unlocked = false;
    private final boolean fullScreen;

    public LockScreenDialog(Frame owner) { this(owner, true); }

    public LockScreenDialog(Frame owner, boolean fullScreen) {
        super(owner, "Unlock", true);
        this.fullScreen = fullScreen;
        setUndecorated(true);
        setBackground(new Color(0,0,0,0));
        buildUI();
        // Size based on mode
        if (this.fullScreen) {
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
                bounds = new Rectangle(0,0,s.width,s.height);
            }
            setBounds(bounds);
            setLocation(bounds.x, bounds.y);
        } else {
            // Window-scoped: match owner window bounds on screen
            try {
                Point p = owner.getLocationOnScreen();
                Dimension d = owner.getSize();
                setBounds(p.x, p.y, d.width, d.height);
            } catch (Throwable t) {
                // Fallback to full screen
                Dimension s = Toolkit.getDefaultToolkit().getScreenSize();
                setBounds(0,0,s.width,s.height);
            }
        }
        try { setAlwaysOnTop(true); } catch (Throwable ignored) {}
        try { toFront(); requestFocus(); } catch (Throwable ignored) {}
    }

    private void buildUI(){
        // Background container with centered column
        JComponent content = createBackground();
        content.setLayout(new GridBagLayout());

        FrostedGlassPanel column = new FrostedGlassPanel(18);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setBorder(BorderFactory.createEmptyBorder(18, 24, 18, 24));

        // Welcome header (bigger)
        JLabel title = new JLabel("Welcome back");
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setForeground(AeroTheme.TEXT_PRIMARY);
        title.setFont(AeroTheme.defaultFont().deriveFont(Font.BOLD, 34f));
        JLabel sub = new JLabel("Enter your password to continue");
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);
        sub.setForeground(new Color(90, 90, 90));
        sub.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 14f));

        column.add(title);
        column.add(Box.createVerticalStrut(6));
        column.add(sub);

        // Clock + calendar row (match MainMenuPanel sizing)
        int clockSize = 200; // preferred size in main menu
        int clockMax  = 220; // maximum size in main menu
        int calSize   = 150; // preferred size in main menu
        int calMax    = 170; // maximum size in main menu

        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        AnalogClockPanel clock = new AnalogClockPanel();
        clock.setPreferredSize(new Dimension(clockSize, clockSize));
        clock.setMaximumSize(new Dimension(clockMax, clockMax));
        TodayCalendarPanel cal = new TodayCalendarPanel();
        cal.setPreferredSize(new Dimension(calSize, calSize));
        cal.setMaximumSize(new Dimension(calMax, calMax));
        row.add(clock);
        row.add(Box.createRigidArea(new Dimension(18, 0)));
        row.add(cal);

        column.add(Box.createVerticalStrut(16));
        row.setAlignmentX(Component.CENTER_ALIGNMENT);
        column.add(row);

        // Password input and buttons
        JPanel login = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        login.setOpaque(false);
        passwordField.setColumns(16);
        passwordField.addActionListener(e -> tryUnlock());
        RoundedButton unlockBtn = new RoundedButton("Unlock");
        unlockBtn.addActionListener(e -> tryUnlock());
        login.add(passwordField);
        login.add(unlockBtn);
        getRootPane().setDefaultButton(unlockBtn);

        column.add(Box.createVerticalStrut(16));
        login.setAlignmentX(Component.CENTER_ALIGNMENT);
        column.add(login);

        // Add the centered column into the background using GridBag
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.weightx = 1; gbc.weighty = 1;
        gbc.anchor = GridBagConstraints.CENTER;
        content.add(column, gbc);
        setContentPane(content);
    }

    private JComponent createBackground(){
        String bgPath = SettingsStore.get().getBackgroundImage();
        JPanel content;
        if (bgPath != null && !bgPath.isEmpty()) {
            if (bgPath.startsWith("gen:")) {
                String id = bgPath;
                Image img = main.ui.features.gallery.GeneratedWallpapers.render(id, 2560, 1440);
                BackgroundPanel bg = new BackgroundPanel(img);
                bg.setOpacityOverride(1.0f);
                content = bg;
            } else if (bgPath.startsWith("res:")) {
                String resPath = bgPath.substring(4);
                Image img = ResourceLoader.createImage("Simjot/" + resPath);
                if (img != null) {
                    BackgroundPanel bg = new BackgroundPanel(img);
                    bg.setOpacityOverride(1.0f);
                    content = bg;
                } else {
                    content = new JPanel();
                    content.setBackground(Color.WHITE);
                }
            } else {
                BackgroundPanel bg = new BackgroundPanel(bgPath);
                bg.setOpacityOverride(1.0f);
                content = bg;
            }
        } else {
            content = new JPanel();
            content.setBackground(Color.WHITE);
        }
        return content;
    }

    private void tryUnlock(){
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
                java.awt.Window w = getOwner();
                if (w instanceof javax.swing.JFrame jf) {
                    jf.setExtendedState(jf.getExtendedState() | javax.swing.JFrame.MAXIMIZED_BOTH);
                    jf.toFront();
                    jf.requestFocus();
                }
            } catch (Throwable ignored) {}
        } else {
            JOptionPane.showMessageDialog(this, "Incorrect password.", "Unlock Failed", JOptionPane.ERROR_MESSAGE);
            passwordField.setText("");
        }
    }

    public boolean blockUntilUnlocked(){
        setVisible(true);
        return unlocked;
    }
}
