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
import java.awt.Toolkit;
import java.net.URL;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import javax.swing.SwingUtilities;

import main.infrastructure.io.IoLog;
import main.infrastructure.io.ResourceLoader;

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
        try {
            // Try to load simjot.icns from resources (primary icon)
            URL iconUrl = ResourceLoader.getResource("img/icons/original/simjot.icns");
            if (iconUrl != null) {
                Image img = Toolkit.getDefaultToolkit().getImage(iconUrl);
                if (img != null) {
                    IoLog.info("menubar", "Loaded tray icon from img/icons/original/simjot.icns");
                    return img;
                }
            }
            
            // Try PNG version in background folder
            iconUrl = ResourceLoader.getResource("img/background/simjot.icns");
            if (iconUrl != null) {
                Image img = Toolkit.getDefaultToolkit().getImage(iconUrl);
                if (img != null) {
                    IoLog.info("menubar", "Loaded tray icon from img/background/simjot.icns");
                    return img;
                }
            }
            
            // Try legacy paths
            iconUrl = ResourceLoader.getResource("icons/tray-icon.png");
            if (iconUrl != null) {
                return Toolkit.getDefaultToolkit().getImage(iconUrl);
            }
            
            iconUrl = ResourceLoader.getResource("simjot-icon-16.png");
            if (iconUrl != null) {
                return Toolkit.getDefaultToolkit().getImage(iconUrl);
            }
            
            // Create a simple fallback icon programmatically
            IoLog.info("menubar", "Using fallback tray icon");
            return createFallbackIcon();
        } catch (Throwable t) {
            IoLog.warn("menubar", "Error loading tray icon: " + t.getMessage(), t);
            return createFallbackIcon();
        }
    }
    
    private Image createFallbackIcon() {
        // Create a simple 16x16 icon
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new java.awt.Color(100, 100, 100));
        g2.fillRoundRect(1, 1, 14, 14, 4, 4);
        g2.setColor(java.awt.Color.WHITE);
        g2.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 10));
        g2.drawString("S", 4, 12);
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
                quickEntryDialog = new QuickEntryDialog(this);
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
