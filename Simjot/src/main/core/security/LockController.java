/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.core.security;

import main.core.service.SettingsStore;
import main.ui.app.JournalApp;
import main.ui.dialog.security.ElegantLockScreen;

import java.awt.*;
import java.awt.event.AWTEventListener;
import java.util.HashSet;
import java.util.Set;
import javax.swing.SwingUtilities;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;

public final class LockController {
    private static LockController INSTANCE;
    public static synchronized LockController get(){ if (INSTANCE == null) INSTANCE = new LockController(); return INSTANCE; }

    private JournalApp app;
    private volatile long lastActivityMs = System.currentTimeMillis();
    private javax.swing.Timer idleTimer;
    private boolean locked = false;
    private JComponent cover;

    // per-session unlocked notebooks
    private final Set<String> unlockedNotebooks = new HashSet<>();

    private final AWTEventListener activityListener = e -> {
        // Any input resets timer if not locked
        if (!locked) lastActivityMs = System.currentTimeMillis();
    };

    private LockController() {}

    public void init(JournalApp app){
        if (this.app != null) return; // init once
        this.app = app;
        ensureCover();
        // Keep cover in sync with frame bounds
        app.addComponentListener(new java.awt.event.ComponentAdapter(){
            @Override public void componentResized(java.awt.event.ComponentEvent e){ layoutCover(); }
            @Override public void componentMoved(java.awt.event.ComponentEvent e){ layoutCover(); }
            @Override public void componentShown(java.awt.event.ComponentEvent e){ layoutCover(); }
        });
        // Listen to user input globally
        long mask = AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_WHEEL_EVENT_MASK;
        try { Toolkit.getDefaultToolkit().addAWTEventListener(activityListener, mask); } catch (Throwable ignored) {}
        // Start idle timer
        idleTimer = new javax.swing.Timer(1000, e -> checkIdle());
        idleTimer.setRepeats(true);
        try { idleTimer.start(); } catch (Throwable ignored) {}
    }

    private void ensureCover(){
        if (cover != null) return;
        cover = new JComponent(){
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0,0,0,160));
                g2.fillRect(0,0,getWidth(),getHeight());
                g2.setColor(new Color(255,255,255,220));
                g2.setFont(g2.getFont().deriveFont(Font.BOLD, 20f));
                String txt = "Locked";
                int tw = g2.getFontMetrics().stringWidth(txt);
                g2.drawString(txt, (getWidth()-tw)/2, Math.max(40, getHeight()/3));
                g2.dispose();
            }
        };
        cover.setVisible(false);
        try {
            JLayeredPane lp = app.getLayeredPane();
            lp.add(cover, JLayeredPane.MODAL_LAYER);
            layoutCover();
        } catch (Throwable ignored) {}
    }

    private void layoutCover(){
        if (app == null || cover == null) return;
        try {
            Dimension d = app.getSize();
            cover.setBounds(0,0, d.width, d.height);
            cover.revalidate(); cover.repaint();
        } catch (Throwable ignored) {}
    }

    private void checkIdle(){
        SettingsStore s = SettingsStore.get();
        if (!s.isLockEnabled()) return;
        int sec = s.getLockTimeoutSec();
        if (sec <= 0) return;
        if (locked) return;
        long now = System.currentTimeMillis();
        if (now - lastActivityMs >= sec * 1000L) {
            lockNow();
        }
    }

    public boolean isLocked(){ return locked; }

    public void lockNow(){
        // called from timer thread (EDT) or elsewhere; ensure on EDT for dialog
        SwingUtilities.invokeLater(() -> {
            if (locked) return;
            locked = true;
            unlockedNotebooks.clear();
            if (cover != null) cover.setVisible(true);
            ElegantLockScreen dlg = new ElegantLockScreen(app, false);
            boolean ok = dlg.blockUntilUnlocked();
            if (ok) {
                locked = false;
                if (cover != null) cover.setVisible(false);
                lastActivityMs = System.currentTimeMillis();
            }
        });
    }

    public void lockNowBlocking(){
        if (locked) return;
        locked = true;
        unlockedNotebooks.clear();
        if (cover != null) cover.setVisible(true);
        ElegantLockScreen dlg = new ElegantLockScreen(app, false);
        boolean ok = dlg.blockUntilUnlocked();
        if (ok) {
            locked = false;
            if (cover != null) cover.setVisible(false);
            lastActivityMs = System.currentTimeMillis();
        }
    }

    public boolean promptUnlockNotebook(String name){
        if (name == null) return true;
        SettingsStore s = SettingsStore.get();
        if (!s.isLockEnabled()) return true;
        if (!s.isNotebookLocked(name)) return true;
        if (unlockedNotebooks.contains(name)) return true;
        ElegantLockScreen dlg = new ElegantLockScreen(app, false);
        boolean ok = dlg.blockUntilUnlocked();
        if (ok) {
            unlockedNotebooks.add(name);
            lastActivityMs = System.currentTimeMillis();
            return true;
        }
        return false;
    }
}
