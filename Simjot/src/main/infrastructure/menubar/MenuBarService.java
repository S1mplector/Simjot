/*
 * SIMJOT - MIT License
 *
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 *
 * See LICENSE.md for full terms.
 */

package main.infrastructure.menubar;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import javax.swing.SwingUtilities;

import main.infrastructure.io.IoLog;

/**
 * Menu bar service using Java's built-in SystemTray API.
 * Works reliably on macOS, Windows, and Linux.
 */
public final class MenuBarService {
    
    public static final int FORMAT_BOLD = 1;
    public static final int FORMAT_ITALIC = 2;
    public static final int FORMAT_UNDERLINE = 4;
    public static final int FORMAT_BULLET = 8;
    public static final int FORMAT_HEADER = 16;
    
    private static final MenuBarService INSTANCE = new MenuBarService();
    
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final CopyOnWriteArrayList<BiConsumer<String, Integer>> entryListeners = new CopyOnWriteArrayList<>();
    
    private SystemTray tray;
    private TrayIcon trayIcon;
    private QuickEntryDialog quickEntryDialog;
    
    private MenuBarService() {}
    
    public static MenuBarService getInstance() {
        return INSTANCE;
    }
    
    public synchronized boolean initialize() {
        if (initialized.get()) return true;
        
        if (!SystemTray.isSupported()) {
            IoLog.warn("menubar", "SystemTray not supported on this platform", null);
            return false;
        }
        
        try {
            tray = SystemTray.getSystemTray();
            
            // Load tray icon
            Image image = loadTrayIcon();
            if (image == null) {
                IoLog.warn("menubar", "Failed to load tray icon", null);
                return false;
            }
            
            // Create popup menu
            PopupMenu popup = createPopupMenu();
            
            // Create tray icon
            trayIcon = new TrayIcon(image, "Simjot Quick Entry", popup);
            trayIcon.setImageAutoSize(true);
            
            // Double-click opens quick entry
            trayIcon.addActionListener(e -> showQuickEntryDialog());
            
            // Add to tray
            tray.add(trayIcon);
            
            initialized.set(true);
            IoLog.info("menubar", "Menu bar service initialized via SystemTray");
            return true;
            
        } catch (AWTException e) {
            IoLog.warn("menubar", "Failed to add tray icon: " + e.getMessage(), e);
            return false;
        } catch (Throwable t) {
            IoLog.warn("menubar", "Menu bar initialization failed: " + t.getMessage(), t);
            return false;
        }
    }
    
    private Image loadTrayIcon() {
        return createVectorSIcon();
    }
    
    private Image createVectorSIcon() {
        // Create a crisp 18x18 vector-style "S" icon for the menu bar
        int size = 18;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2 = img.createGraphics();
        
        // High-quality rendering
        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(java.awt.RenderingHints.KEY_STROKE_CONTROL, java.awt.RenderingHints.VALUE_STROKE_PURE);
        
        // Draw a stylish "S" using paths for crisp vector look
        java.awt.geom.Path2D.Float sPath = new java.awt.geom.Path2D.Float();
        
        // Elegant S curve - top arc curves right, bottom arc curves left
        float cx = size / 2f;
        float cy = size / 2f;
        float w = size * 0.6f;
        float h = size * 0.8f;
        
        // Start from top-right, curve down to middle, then curve to bottom-left
        sPath.moveTo(cx + w * 0.35f, cy - h * 0.35f);
        // Top arc (curving left)
        sPath.curveTo(
            cx + w * 0.35f, cy - h * 0.48f,  // control 1
            cx - w * 0.1f, cy - h * 0.5f,    // control 2
            cx - w * 0.35f, cy - h * 0.32f   // end top-left
        );
        // Middle connection curving to center
        sPath.curveTo(
            cx - w * 0.45f, cy - h * 0.15f,  // control 1
            cx - w * 0.3f, cy + h * 0.05f,   // control 2
            cx, cy                            // center
        );
        // Bottom arc (curving right)
        sPath.curveTo(
            cx + w * 0.3f, cy - h * 0.05f,   // control 1
            cx + w * 0.45f, cy + h * 0.15f,  // control 2
            cx + w * 0.35f, cy + h * 0.32f   // right side
        );
        // Finish at bottom-left
        sPath.curveTo(
            cx + w * 0.1f, cy + h * 0.5f,    // control 1
            cx - w * 0.35f, cy + h * 0.48f,  // control 2
            cx - w * 0.35f, cy + h * 0.35f   // end bottom-left
        );
        
        // Draw the S with a nice stroke
        g2.setColor(java.awt.Color.WHITE); // White for menu bar visibility
        g2.setStroke(new java.awt.BasicStroke(2.2f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        g2.draw(sPath);
        
        g2.dispose();
        return img;
    }
    
    private PopupMenu createPopupMenu() {
        PopupMenu popup = new PopupMenu();
        
        MenuItem quickEntryItem = new MenuItem("Quick Entry...");
        quickEntryItem.addActionListener(e -> showQuickEntryDialog());
        popup.add(quickEntryItem);
        
        popup.addSeparator();
        
        MenuItem openSimjotItem = new MenuItem("Open Simjot");
        openSimjotItem.addActionListener(e -> bringAppToFront());
        popup.add(openSimjotItem);
        
        popup.addSeparator();
        
        MenuItem exitItem = new MenuItem("Hide Tray Icon");
        exitItem.addActionListener(e -> shutdown());
        popup.add(exitItem);
        
        return popup;
    }
    
    private void showQuickEntryDialog() {
        SwingUtilities.invokeLater(() -> {
            if (quickEntryDialog == null) {
                quickEntryDialog = new QuickEntryDialog();
            }
            quickEntryDialog.showDialog();
        });
    }
    
    private void bringAppToFront() {
        // Find and activate the main Simjot window
        SwingUtilities.invokeLater(() -> {
            for (java.awt.Window window : java.awt.Window.getWindows()) {
                if (window instanceof javax.swing.JFrame) {
                    window.setVisible(true);
                    window.toFront();
                    window.requestFocus();
                    break;
                }
            }
        });
    }
    
    public synchronized void shutdown() {
        if (!initialized.get()) return;
        
        if (tray != null && trayIcon != null) {
            tray.remove(trayIcon);
        }
        trayIcon = null;
        tray = null;
        
        initialized.set(false);
        IoLog.info("menubar", "Menu bar service shut down");
    }
    
    public boolean isInitialized() {
        return initialized.get();
    }
    
    public boolean isEnabled() {
        return enabled.get();
    }
    
    public void setEnabled(boolean e) {
        enabled.set(e);
        if (e && !initialized.get()) {
            initialize();
        } else if (!e && initialized.get()) {
            shutdown();
        }
    }
    
    public void showPanel() {
        showQuickEntryDialog();
    }
    
    public void hidePanel() {
        if (quickEntryDialog != null) {
            quickEntryDialog.hideDialog();
        }
    }
    
    public void togglePanel() {
        if (quickEntryDialog != null && quickEntryDialog.isVisible()) {
            hidePanel();
        } else {
            showPanel();
        }
    }
    
    public boolean isPanelVisible() {
        return quickEntryDialog != null && quickEntryDialog.isVisible();
    }
    
    public void setIcon(String symbolName) {
        // Not applicable for SystemTray - icon is fixed
    }
    
    public void setTooltip(String tooltip) {
        if (trayIcon != null && tooltip != null) {
            trayIcon.setToolTip(tooltip);
        }
    }
    
    public void setBadge(int count) {
        // SystemTray doesn't support badges natively
        // Could update tooltip to show count
        if (trayIcon != null) {
            if (count > 0) {
                trayIcon.setToolTip("Simjot Quick Entry (" + count + " pending)");
            } else {
                trayIcon.setToolTip("Simjot Quick Entry");
            }
        }
    }
    
    public String getPanelText() {
        if (quickEntryDialog != null) {
            return quickEntryDialog.getText();
        }
        return "";
    }
    
    public void setPanelText(String text) {
        if (quickEntryDialog != null && text != null) {
            quickEntryDialog.setText(text);
        }
    }
    
    public void clearPanel() {
        if (quickEntryDialog != null) {
            quickEntryDialog.clear();
        }
    }
    
    public void addEntryListener(BiConsumer<String, Integer> listener) {
        if (listener != null) {
            entryListeners.add(listener);
        }
    }
    
    public void removeEntryListener(BiConsumer<String, Integer> listener) {
        entryListeners.remove(listener);
    }
    
    void onEntrySubmitted(String text, int formatFlags) {
        if (text == null || text.isBlank()) return;
        
        IoLog.info("menubar", "Quick entry submitted: " + text.length() + " chars");
        
        for (BiConsumer<String, Integer> listener : entryListeners) {
            try {
                listener.accept(text, formatFlags);
            } catch (Throwable t) {
                IoLog.warn("menubar", "Entry listener error: " + t.getMessage(), t);
            }
        }
    }
}
