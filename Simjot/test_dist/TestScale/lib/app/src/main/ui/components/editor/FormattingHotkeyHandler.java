/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.components.editor;

import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.InputEvent;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.text.Keymap;

import main.infrastructure.ffi.NativeAccess;
import main.infrastructure.ffi.NativeLibrary;

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
             () -> RichTextStyler.toggleBold(textPane),
             () -> RichTextStyler.toggleItalic(textPane),
             () -> RichTextStyler.toggleUnderline(textPane),
             () -> RichTextStyler.toggleStrike(textPane));
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
        // Use WHEN_FOCUSED for direct focus and WHEN_ANCESTOR for scroll pane scenarios
        InputMap imFocused = textPane.getInputMap(JComponent.WHEN_FOCUSED);
        InputMap imAncestor = textPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap am = textPane.getActionMap();
        Keymap keymap = textPane.getKeymap();
        int[] modMasks = resolveModifierMasks();
        
        // Bold: Cmd/Ctrl + B
        AbstractAction boldAction = new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (onBold != null) onBold.run();
            }
        };
        am.put(ACTION_KEY_BOLD, boldAction);
        bindKeyStrokes(imFocused, imAncestor, keymap, KeyEvent.VK_B, modMasks, ACTION_KEY_BOLD, boldAction);
        
        // Italic: Cmd/Ctrl + I
        AbstractAction italicAction = new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (onItalic != null) onItalic.run();
            }
        };
        am.put(ACTION_KEY_ITALIC, italicAction);
        bindKeyStrokes(imFocused, imAncestor, keymap, KeyEvent.VK_I, modMasks, ACTION_KEY_ITALIC, italicAction);
        
        // Underline: Cmd/Ctrl + U
        AbstractAction underlineAction = new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (onUnderline != null) onUnderline.run();
            }
        };
        am.put(ACTION_KEY_UNDERLINE, underlineAction);
        bindKeyStrokes(imFocused, imAncestor, keymap, KeyEvent.VK_U, modMasks, ACTION_KEY_UNDERLINE, underlineAction);
        
        // Strikethrough: Cmd/Ctrl + Shift + S
        AbstractAction strikeAction = new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (onStrikethrough != null) onStrikethrough.run();
            }
        };
        am.put(ACTION_KEY_STRIKETHROUGH, strikeAction);
        bindKeyStrokes(imFocused, imAncestor, keymap, KeyEvent.VK_S, modMasks,
            ACTION_KEY_STRIKETHROUGH, strikeAction, InputEvent.SHIFT_DOWN_MASK);
    }
    
    /**
     * Uninstall this handler from its text pane.
     */
    public void uninstall() {
        InputMap im = textPane.getInputMap(JComponent.WHEN_FOCUSED);
        InputMap imAncestor = textPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap am = textPane.getActionMap();
        Keymap keymap = textPane.getKeymap();
        
        int[] modMasks = resolveModifierMasks();
        unbindKeyStrokes(im, imAncestor, keymap, KeyEvent.VK_B, modMasks, 0);
        unbindKeyStrokes(im, imAncestor, keymap, KeyEvent.VK_I, modMasks, 0);
        unbindKeyStrokes(im, imAncestor, keymap, KeyEvent.VK_U, modMasks, 0);
        unbindKeyStrokes(im, imAncestor, keymap, KeyEvent.VK_S, modMasks, InputEvent.SHIFT_DOWN_MASK);
        
        am.remove(ACTION_KEY_BOLD);
        am.remove(ACTION_KEY_ITALIC);
        am.remove(ACTION_KEY_UNDERLINE);
        am.remove(ACTION_KEY_STRIKETHROUGH);
    }

    private static int[] resolveModifierMasks() {
        int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        int nativePrimary = NativeAccess.hotkeyGetPrimaryModifier();
        int nativeMask = nativePrimary == NativeLibrary.MOD_META
            ? InputEvent.META_DOWN_MASK
            : InputEvent.CTRL_DOWN_MASK;
        int[] candidates = new int[] {
            menuMask,
            nativeMask,
            InputEvent.META_DOWN_MASK,
            InputEvent.CTRL_DOWN_MASK
        };
        int[] unique = new int[candidates.length];
        int count = 0;
        for (int mask : candidates) {
            if (mask == 0) continue;
            boolean seen = false;
            for (int i = 0; i < count; i++) {
                if (unique[i] == mask) {
                    seen = true;
                    break;
                }
            }
            if (!seen) unique[count++] = mask;
        }
        int[] out = new int[count];
        System.arraycopy(unique, 0, out, 0, count);
        return out;
    }

    private static void bindKeyStrokes(InputMap focused,
                                       InputMap ancestor,
                                       Keymap keymap,
                                       int keyCode,
                                       int[] modMasks,
                                       String actionKey,
                                       javax.swing.Action action,
                                       int... extraModifiers) {
        int extra = (extraModifiers != null && extraModifiers.length > 0) ? extraModifiers[0] : 0;
        for (int mask : modMasks) {
            int mods = mask | extra;
            addKeyBinding(focused, ancestor, keymap, KeyStroke.getKeyStroke(keyCode, mods), actionKey, action);
            if (keyCode >= KeyEvent.VK_A && keyCode <= KeyEvent.VK_Z) {
                char upper = (char) ('A' + (keyCode - KeyEvent.VK_A));
                char lower = (char) ('a' + (keyCode - KeyEvent.VK_A));
                addKeyBinding(focused, ancestor, keymap, KeyStroke.getKeyStroke(upper, mods), actionKey, action);
                addKeyBinding(focused, ancestor, keymap, KeyStroke.getKeyStroke(lower, mods), actionKey, action);
            }
        }
    }

    private static void unbindKeyStrokes(InputMap focused,
                                         InputMap ancestor,
                                         Keymap keymap,
                                         int keyCode,
                                         int[] modMasks,
                                         int extra) {
        for (int mask : modMasks) {
            int mods = mask | extra;
            removeKeyBinding(focused, ancestor, keymap, KeyStroke.getKeyStroke(keyCode, mods));
            if (keyCode >= KeyEvent.VK_A && keyCode <= KeyEvent.VK_Z) {
                char upper = (char) ('A' + (keyCode - KeyEvent.VK_A));
                char lower = (char) ('a' + (keyCode - KeyEvent.VK_A));
                removeKeyBinding(focused, ancestor, keymap, KeyStroke.getKeyStroke(upper, mods));
                removeKeyBinding(focused, ancestor, keymap, KeyStroke.getKeyStroke(lower, mods));
            }
        }
    }

    private static void addKeyBinding(InputMap focused,
                                      InputMap ancestor,
                                      Keymap keymap,
                                      KeyStroke keyStroke,
                                      String actionKey,
                                      javax.swing.Action action) {
        if (keyStroke == null) return;
        focused.put(keyStroke, actionKey);
        ancestor.put(keyStroke, actionKey);
        if (keymap != null) {
            keymap.addActionForKeyStroke(keyStroke, action);
        }
    }

    private static void removeKeyBinding(InputMap focused,
                                         InputMap ancestor,
                                         Keymap keymap,
                                         KeyStroke keyStroke) {
        if (keyStroke == null) return;
        focused.remove(keyStroke);
        ancestor.remove(keyStroke);
        if (keymap != null) {
            keymap.removeKeyStrokeBinding(keyStroke);
        }
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
