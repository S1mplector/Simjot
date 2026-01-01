/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.editor;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.JTextPane;

import main.infrastructure.ffi.NativeAccess;
import main.infrastructure.ffi.NativeLibrary;

/**
 * Native-accelerated hotkey handler for text formatting.
 * 
 * Installs on a JTextPane to handle formatting hotkeys:
 * - Cmd/Ctrl + B: Bold
 * - Cmd/Ctrl + I: Italic
 * - Cmd/Ctrl + U: Underline
 * - Cmd/Ctrl + Shift + S: Strikethrough
 * 
 * Uses native C++ hotkey detection for fast, OS-aware modifier handling.
 * Falls back to Java-only detection if native library unavailable.
 * 
 * @author S1mplector
 */
public final class FormattingHotkeyHandler implements KeyListener {
    
    private final JTextPane textPane;
    private final Runnable onBold;
    private final Runnable onItalic;
    private final Runnable onUnderline;
    private final Runnable onStrikethrough;
    
    /**
     * Create a handler with custom callbacks for each action.
     */
    public FormattingHotkeyHandler(JTextPane textPane,
                                    Runnable onBold,
                                    Runnable onItalic,
                                    Runnable onUnderline,
                                    Runnable onStrikethrough) {
        this.textPane = textPane;
        this.onBold = onBold;
        this.onItalic = onItalic;
        this.onUnderline = onUnderline;
        this.onStrikethrough = onStrikethrough;
    }
    
    /**
     * Create a handler using RichTextStyler for formatting.
     */
    public FormattingHotkeyHandler(JTextPane textPane) {
        this(textPane,
             () -> RichTextStyler.toggleSelectionBold(textPane),
             () -> RichTextStyler.toggleSelectionItalic(textPane),
             () -> RichTextStyler.toggleSelectionUnderline(textPane),
             () -> RichTextStyler.toggleSelectionStrike(textPane));
    }
    
    /**
     * Install the hotkey handler on the text pane.
     * Returns the handler instance for removal if needed.
     */
    public static FormattingHotkeyHandler install(JTextPane textPane) {
        FormattingHotkeyHandler handler = new FormattingHotkeyHandler(textPane);
        textPane.addKeyListener(handler);
        return handler;
    }
    
    /**
     * Install with custom callbacks.
     */
    public static FormattingHotkeyHandler install(JTextPane textPane,
                                                   Runnable onBold,
                                                   Runnable onItalic,
                                                   Runnable onUnderline,
                                                   Runnable onStrikethrough) {
        FormattingHotkeyHandler handler = new FormattingHotkeyHandler(
            textPane, onBold, onItalic, onUnderline, onStrikethrough);
        textPane.addKeyListener(handler);
        return handler;
    }
    
    /**
     * Uninstall this handler from its text pane.
     */
    public void uninstall() {
        textPane.removeKeyListener(this);
    }
    
    @Override
    public void keyPressed(KeyEvent e) {
        int action = NativeAccess.hotkeyCheckEvent(e);
        
        if (action != NativeLibrary.ACTION_NONE) {
            e.consume();
            switch (action) {
                case NativeLibrary.ACTION_BOLD -> {
                    if (onBold != null) onBold.run();
                }
                case NativeLibrary.ACTION_ITALIC -> {
                    if (onItalic != null) onItalic.run();
                }
                case NativeLibrary.ACTION_UNDERLINE -> {
                    if (onUnderline != null) onUnderline.run();
                }
                case NativeLibrary.ACTION_STRIKETHROUGH -> {
                    if (onStrikethrough != null) onStrikethrough.run();
                }
            }
        }
    }
    
    @Override
    public void keyReleased(KeyEvent e) {
        // Not needed
    }
    
    @Override
    public void keyTyped(KeyEvent e) {
        // Not needed
    }
    
    /**
     * Get the display string for a formatting action (e.g., "⌘B" or "Ctrl+B").
     */
    public static String getDisplayString(int action) {
        return NativeAccess.hotkeyGetDisplayString(action);
    }
    
    /**
     * Check if native hotkey support is available.
     */
    public static boolean hasNativeSupport() {
        return NativeAccess.hasHotkeySupport();
    }
}
