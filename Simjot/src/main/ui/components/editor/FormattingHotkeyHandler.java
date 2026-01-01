/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.editor;

import java.awt.Toolkit;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;

import main.infrastructure.ffi.NativeAccess;

/**
 * Platform-aware hotkey handler for text formatting.
 * 
 * Installs on a JTextPane to handle formatting hotkeys:
 * - Cmd/Ctrl + B: Bold
 * - Cmd/Ctrl + I: Italic
 * - Cmd/Ctrl + U: Underline
 * - Cmd/Ctrl + Shift + S: Strikethrough
 * 
 * Uses InputMap/ActionMap bindings for reliable key handling that takes
 * precedence over JTextPane's built-in editor kit bindings.
 * 
 * @author S1mplector
 */
public final class FormattingHotkeyHandler {
    
    private static final String ACTION_KEY_BOLD = "simjot-bold";
    private static final String ACTION_KEY_ITALIC = "simjot-italic";
    private static final String ACTION_KEY_UNDERLINE = "simjot-underline";
    private static final String ACTION_KEY_STRIKETHROUGH = "simjot-strikethrough";
    
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
        handler.installBindings();
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
        handler.installBindings();
        return handler;
    }
    
    /**
     * Install InputMap/ActionMap bindings for formatting hotkeys.
     */
    private void installBindings() {
        // Get platform-specific modifier (Cmd on macOS, Ctrl elsewhere)
        int modMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        
        // Use WHEN_FOCUSED for direct focus and WHEN_ANCESTOR for scroll pane scenarios
        InputMap imFocused = textPane.getInputMap(JComponent.WHEN_FOCUSED);
        InputMap imAncestor = textPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap am = textPane.getActionMap();
        
        // Bold: Cmd/Ctrl + B
        KeyStroke boldKey = KeyStroke.getKeyStroke(KeyEvent.VK_B, modMask);
        System.out.println("[FormattingHotkeyHandler] Installing bold binding: " + boldKey + " (modMask=" + modMask + ")");
        imFocused.put(boldKey, ACTION_KEY_BOLD);
        imAncestor.put(boldKey, ACTION_KEY_BOLD);
        am.put(ACTION_KEY_BOLD, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                System.out.println("[FormattingHotkeyHandler] BOLD action triggered!");
                if (onBold != null) onBold.run();
            }
        });
        
        // Italic: Cmd/Ctrl + I
        KeyStroke italicKey = KeyStroke.getKeyStroke(KeyEvent.VK_I, modMask);
        imFocused.put(italicKey, ACTION_KEY_ITALIC);
        imAncestor.put(italicKey, ACTION_KEY_ITALIC);
        am.put(ACTION_KEY_ITALIC, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (onItalic != null) onItalic.run();
            }
        });
        
        // Underline: Cmd/Ctrl + U
        KeyStroke underlineKey = KeyStroke.getKeyStroke(KeyEvent.VK_U, modMask);
        imFocused.put(underlineKey, ACTION_KEY_UNDERLINE);
        imAncestor.put(underlineKey, ACTION_KEY_UNDERLINE);
        am.put(ACTION_KEY_UNDERLINE, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (onUnderline != null) onUnderline.run();
            }
        });
        
        // Strikethrough: Cmd/Ctrl + Shift + S
        KeyStroke strikeKey = KeyStroke.getKeyStroke(KeyEvent.VK_S, modMask | KeyEvent.SHIFT_DOWN_MASK);
        imFocused.put(strikeKey, ACTION_KEY_STRIKETHROUGH);
        imAncestor.put(strikeKey, ACTION_KEY_STRIKETHROUGH);
        am.put(ACTION_KEY_STRIKETHROUGH, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (onStrikethrough != null) onStrikethrough.run();
            }
        });
    }
    
    /**
     * Uninstall this handler from its text pane.
     */
    public void uninstall() {
        InputMap im = textPane.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = textPane.getActionMap();
        
        int modMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        
        im.remove(KeyStroke.getKeyStroke(KeyEvent.VK_B, modMask));
        im.remove(KeyStroke.getKeyStroke(KeyEvent.VK_I, modMask));
        im.remove(KeyStroke.getKeyStroke(KeyEvent.VK_U, modMask));
        im.remove(KeyStroke.getKeyStroke(KeyEvent.VK_S, modMask | KeyEvent.SHIFT_DOWN_MASK));
        
        am.remove(ACTION_KEY_BOLD);
        am.remove(ACTION_KEY_ITALIC);
        am.remove(ACTION_KEY_UNDERLINE);
        am.remove(ACTION_KEY_STRIKETHROUGH);
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
