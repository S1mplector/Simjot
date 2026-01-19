/*
 * SIMJOT - MIT License
 *
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 *
 * See LICENSE.md for full terms.
 */

package main.infrastructure.menubar;

import java.io.File;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import main.infrastructure.ffi.NativeAccess;
import main.infrastructure.io.AppDirectories;
import main.infrastructure.io.IoLog;

/**
 * Menu bar service for macOS that provides a quick entry panel
 * accessible from the system status bar.
 * 
 * Features:
 * - Frosted glass popup panel
 * - Minimal formatting toolbar (bold, italic, underline, bullets, headers)
 * - Quick journal entry submission
 * - Badge count for notifications
 */
public final class MenuBarService {
    
    // Format flags matching native code
    public static final int FORMAT_BOLD = 1 << 0;
    public static final int FORMAT_ITALIC = 1 << 1;
    public static final int FORMAT_UNDERLINE = 1 << 2;
    public static final int FORMAT_BULLET = 1 << 3;
    public static final int FORMAT_HEADER = 1 << 4;
    
    private static final MenuBarService INSTANCE = new MenuBarService();
    
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final CopyOnWriteArrayList<BiConsumer<String, Integer>> entryListeners = new CopyOnWriteArrayList<>();
    
    // Native FFM
    private final Linker linker = Linker.nativeLinker();
    private SymbolLookup lookup;
    
    // Method handles
    private MethodHandle initHandle;
    private MethodHandle shutdownHandle;
    private MethodHandle isInitializedHandle;
    private MethodHandle showPanelHandle;
    private MethodHandle hidePanelHandle;
    private MethodHandle isPanelVisibleHandle;
    private MethodHandle setIconHandle;
    private MethodHandle setTooltipHandle;
    private MethodHandle setBadgeHandle;
    private MethodHandle getPanelTextHandle;
    private MethodHandle setPanelTextHandle;
    private MethodHandle clearPanelHandle;
    
    private MenuBarService() {}
    
    public static MenuBarService getInstance() {
        return INSTANCE;
    }
    
    /**
     * Initialize the menu bar service.
     * Should be called on app startup if the feature is enabled.
     */
    public synchronized boolean initialize() {
        if (initialized.get()) return true;
        if (!isMacOS()) {
            IoLog.info("menubar", "Menu bar service only available on macOS");
            return false;
        }
        
        lookup = NativeAccess.symbolLookup();
        if (lookup == null) {
            IoLog.warn("menubar", "Native library not available", null);
            return false;
        }
        
        // Initialize method handles
        if (!initializeHandles()) {
            IoLog.warn("menubar", "Failed to initialize native handles", null);
            return false;
        }
        
        // Initialize native menu bar
        if (!nativeInit()) {
            IoLog.warn("menubar", "Failed to initialize native menu bar", null);
            return false;
        }
        
        // Set up the callback bridge
        setupCallbackBridge();
        
        initialized.set(true);
        IoLog.info("menubar", "Menu bar service initialized");
        return true;
    }
    
    /**
     * Shutdown the menu bar service.
     */
    public synchronized void shutdown() {
        if (!initialized.get()) return;
        
        nativeShutdown();
        initialized.set(false);
        IoLog.info("menubar", "Menu bar service shut down");
    }
    
    /**
     * Check if the service is initialized.
     */
    public boolean isInitialized() {
        return initialized.get();
    }
    
    /**
     * Check if the service is enabled in settings.
     */
    public boolean isEnabled() {
        return enabled.get();
    }
    
    /**
     * Enable or disable the service.
     */
    public void setEnabled(boolean e) {
        enabled.set(e);
        if (e && !initialized.get()) {
            initialize();
        } else if (!e && initialized.get()) {
            shutdown();
        }
    }
    
    /**
     * Show the quick entry panel.
     */
    public void showPanel() {
        if (!initialized.get()) return;
        nativeShowPanel();
    }
    
    /**
     * Hide the quick entry panel.
     */
    public void hidePanel() {
        if (!initialized.get()) return;
        nativeHidePanel();
    }
    
    /**
     * Toggle the quick entry panel visibility.
     */
    public void togglePanel() {
        if (!initialized.get()) return;
        if (isPanelVisible()) {
            hidePanel();
        } else {
            showPanel();
        }
    }
    
    /**
     * Check if the panel is visible.
     */
    public boolean isPanelVisible() {
        if (!initialized.get()) return false;
        return nativeIsPanelVisible();
    }
    
    /**
     * Set the status item icon using an SF Symbol name.
     */
    public void setIcon(String symbolName) {
        if (!initialized.get() || symbolName == null) return;
        nativeSetIcon(symbolName);
    }
    
    /**
     * Set the status item tooltip.
     */
    public void setTooltip(String tooltip) {
        if (!initialized.get() || tooltip == null) return;
        nativeSetTooltip(tooltip);
    }
    
    /**
     * Set a badge count on the status item.
     */
    public void setBadge(int count) {
        if (!initialized.get()) return;
        nativeSetBadge(count);
    }
    
    /**
     * Get the current text from the quick entry panel.
     */
    public String getPanelText() {
        if (!initialized.get()) return "";
        return nativeGetPanelText();
    }
    
    /**
     * Set text in the quick entry panel.
     */
    public void setPanelText(String text) {
        if (!initialized.get() || text == null) return;
        nativeSetPanelText(text);
    }
    
    /**
     * Clear the quick entry panel.
     */
    public void clearPanel() {
        if (!initialized.get()) return;
        nativeClearPanel();
    }
    
    /**
     * Add a listener for quick entry submissions.
     * The listener receives the entry text and format flags.
     */
    public void addEntryListener(BiConsumer<String, Integer> listener) {
        if (listener != null) {
            entryListeners.add(listener);
        }
    }
    
    /**
     * Remove an entry listener.
     */
    public void removeEntryListener(BiConsumer<String, Integer> listener) {
        entryListeners.remove(listener);
    }
    
    /**
     * Called when an entry is submitted from the native panel.
     */
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
    
    // ═══════════════════════════════════════════════════════════════════════════
    // NATIVE FFM CALLS
    // ═══════════════════════════════════════════════════════════════════════════
    
    private boolean initializeHandles() {
        if (lookup == null) return false;
        
        try {
            initHandle = lookup.find("simjot_menubar_init")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT)))
                .orElse(null);
            shutdownHandle = lookup.find("simjot_menubar_shutdown")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.ofVoid()))
                .orElse(null);
            isInitializedHandle = lookup.find("simjot_menubar_is_initialized")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT)))
                .orElse(null);
            showPanelHandle = lookup.find("simjot_menubar_show_panel")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.ofVoid()))
                .orElse(null);
            hidePanelHandle = lookup.find("simjot_menubar_hide_panel")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.ofVoid()))
                .orElse(null);
            isPanelVisibleHandle = lookup.find("simjot_menubar_is_panel_visible")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT)))
                .orElse(null);
            setIconHandle = lookup.find("simjot_menubar_set_icon")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)))
                .orElse(null);
            setTooltipHandle = lookup.find("simjot_menubar_set_tooltip")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)))
                .orElse(null);
            setBadgeHandle = lookup.find("simjot_menubar_set_badge")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT)))
                .orElse(null);
            getPanelTextHandle = lookup.find("simjot_menubar_get_panel_text")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT)))
                .orElse(null);
            setPanelTextHandle = lookup.find("simjot_menubar_set_panel_text")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)))
                .orElse(null);
            clearPanelHandle = lookup.find("simjot_menubar_clear_panel")
                .map(s -> linker.downcallHandle(s, FunctionDescriptor.ofVoid()))
                .orElse(null);
            
            return initHandle != null;
        } catch (Throwable t) {
            IoLog.warn("menubar", "Failed to initialize handles: " + t.getMessage(), t);
            return false;
        }
    }
    
    private void setupCallbackBridge() {
        // The callback is handled via polling or by the Java side
        // receiving notifications when entries are saved
        // For now, entries are saved directly to a pending queue file
        // that the main app can pick up
        
        // Start a background thread to check for submitted entries
        Thread callbackThread = new Thread(() -> {
            File pendingFile = new File(AppDirectories.getRoot(), ".quick_entry_pending");
            while (initialized.get()) {
                try {
                    if (pendingFile.exists() && pendingFile.length() > 0) {
                        String content = java.nio.file.Files.readString(pendingFile.toPath());
                        if (!content.isBlank()) {
                            // Parse format flags from first line if present
                            int formatFlags = 0;
                            String text = content;
                            if (content.startsWith("FLAGS:")) {
                                int newline = content.indexOf('\n');
                                if (newline > 0) {
                                    try {
                                        formatFlags = Integer.parseInt(content.substring(6, newline).trim());
                                    } catch (NumberFormatException ignored) {}
                                    text = content.substring(newline + 1);
                                }
                            }
                            
                            // Clear the file
                            java.nio.file.Files.writeString(pendingFile.toPath(), "");
                            
                            // Notify listeners
                            final String entryText = text;
                            final int flags = formatFlags;
                            javax.swing.SwingUtilities.invokeLater(() -> onEntrySubmitted(entryText, flags));
                        }
                    }
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Throwable ignored) {}
            }
        }, "MenuBar-CallbackBridge");
        callbackThread.setDaemon(true);
        callbackThread.start();
    }
    
    private boolean nativeInit() {
        if (initHandle == null) return false;
        try { return ((int) initHandle.invokeExact()) != 0; } catch (Throwable t) { return false; }
    }
    
    private void nativeShutdown() {
        if (shutdownHandle == null) return;
        try { shutdownHandle.invokeExact(); } catch (Throwable ignored) {}
    }
    
    private boolean nativeIsInitialized() {
        if (isInitializedHandle == null) return false;
        try { return ((int) isInitializedHandle.invokeExact()) != 0; } catch (Throwable t) { return false; }
    }
    
    private void nativeShowPanel() {
        if (showPanelHandle == null) return;
        try { showPanelHandle.invokeExact(); } catch (Throwable ignored) {}
    }
    
    private void nativeHidePanel() {
        if (hidePanelHandle == null) return;
        try { hidePanelHandle.invokeExact(); } catch (Throwable ignored) {}
    }
    
    private boolean nativeIsPanelVisible() {
        if (isPanelVisibleHandle == null) return false;
        try { return ((int) isPanelVisibleHandle.invokeExact()) != 0; } catch (Throwable t) { return false; }
    }
    
    private void nativeSetIcon(String symbolName) {
        if (setIconHandle == null || symbolName == null) return;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment cStr = arena.allocateFrom(symbolName);
            setIconHandle.invokeExact(cStr);
        } catch (Throwable ignored) {}
    }
    
    private void nativeSetTooltip(String tooltip) {
        if (setTooltipHandle == null || tooltip == null) return;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment cStr = arena.allocateFrom(tooltip);
            setTooltipHandle.invokeExact(cStr);
        } catch (Throwable ignored) {}
    }
    
    private void nativeSetBadge(int count) {
        if (setBadgeHandle == null) return;
        try { setBadgeHandle.invokeExact(count); } catch (Throwable ignored) {}
    }
    
    private String nativeGetPanelText() {
        if (getPanelTextHandle == null) return "";
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buffer = arena.allocate(8192);
            int len = (int) getPanelTextHandle.invokeExact(buffer, 8192);
            if (len <= 0) return "";
            return buffer.getString(0);
        } catch (Throwable t) { return ""; }
    }
    
    private void nativeSetPanelText(String text) {
        if (setPanelTextHandle == null || text == null) return;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment cStr = arena.allocateFrom(text);
            setPanelTextHandle.invokeExact(cStr);
        } catch (Throwable ignored) {}
    }
    
    private void nativeClearPanel() {
        if (clearPanelHandle == null) return;
        try { clearPanelHandle.invokeExact(); } catch (Throwable ignored) {}
    }
    
    private static boolean isMacOS() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("mac");
    }
}
