/*
 * SIMJOT - MIT License
 *
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 *
 * See LICENSE.md for full terms.
 */

package main.infrastructure.ffi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests for the native hotkey manager functionality.
 * 
 * These tests verify that the native hotkey detection works correctly
 * for text formatting shortcuts (Bold, Italic, Underline, Strikethrough).
 * 
 * @author S1mplector
 */
public class HotkeyManagerTest {
    
    private static boolean nativeAvailable;
    private static int primaryMod;
    
    @BeforeAll
    static void setup() {
        nativeAvailable = NativeAccess.hasHotkeySupport();
        if (nativeAvailable) {
            primaryMod = NativeAccess.hotkeyGetPrimaryModifier();
        }
    }
    
    @Test
    void testNativeLibraryLoaded() {
        // This test verifies the native library is accessible
        NativeLibrary lib = null;
        try {
            lib = NativeLibrary.loadDefault();
            assertNotNull(lib, "Native library should load");
        } catch (Exception e) {
            fail("Native library failed to load: " + e.getMessage());
        } finally {
            if (lib != null) lib.close();
        }
    }
    
    @Test
    void testHotkeySupport() {
        Assumptions.assumeTrue(nativeAvailable, "Native hotkey support not available");
        assertTrue(NativeAccess.hasHotkeySupport(), "Hotkey support should be available");
    }
    
    @Test
    void testPlatformDetection() {
        Assumptions.assumeTrue(nativeAvailable, "Native hotkey support not available");
        
        int platform = NativeAccess.hotkeyGetPlatform();
        assertTrue(platform >= NativeLibrary.PLATFORM_UNKNOWN && platform <= NativeLibrary.PLATFORM_LINUX,
            "Platform should be a valid constant");
        
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("mac")) {
            assertEquals(NativeLibrary.PLATFORM_MACOS, platform, "Should detect macOS");
        } else if (osName.contains("win")) {
            assertEquals(NativeLibrary.PLATFORM_WINDOWS, platform, "Should detect Windows");
        } else if (osName.contains("linux")) {
            assertEquals(NativeLibrary.PLATFORM_LINUX, platform, "Should detect Linux");
        }
    }
    
    @Test
    void testPrimaryModifier() {
        Assumptions.assumeTrue(nativeAvailable, "Native hotkey support not available");
        
        int mod = NativeAccess.hotkeyGetPrimaryModifier();
        String osName = System.getProperty("os.name", "").toLowerCase();
        
        if (osName.contains("mac")) {
            assertEquals(NativeLibrary.MOD_META, mod, "macOS should use Meta (Cmd) as primary modifier");
        } else {
            assertEquals(NativeLibrary.MOD_CTRL, mod, "Non-macOS should use Ctrl as primary modifier");
        }
    }
    
    @Test
    void testBoldHotkey() {
        Assumptions.assumeTrue(nativeAvailable, "Native hotkey support not available");
        
        // Test uppercase B
        int action = NativeAccess.hotkeyCheck('B', primaryMod);
        assertEquals(NativeLibrary.ACTION_BOLD, action, "Cmd/Ctrl+B should trigger Bold");
        
        // Test lowercase b
        action = NativeAccess.hotkeyCheck('b', primaryMod);
        assertEquals(NativeLibrary.ACTION_BOLD, action, "Cmd/Ctrl+b should also trigger Bold");
    }
    
    @Test
    void testItalicHotkey() {
        Assumptions.assumeTrue(nativeAvailable, "Native hotkey support not available");
        
        int action = NativeAccess.hotkeyCheck('I', primaryMod);
        assertEquals(NativeLibrary.ACTION_ITALIC, action, "Cmd/Ctrl+I should trigger Italic");
    }
    
    @Test
    void testUnderlineHotkey() {
        Assumptions.assumeTrue(nativeAvailable, "Native hotkey support not available");
        
        int action = NativeAccess.hotkeyCheck('U', primaryMod);
        assertEquals(NativeLibrary.ACTION_UNDERLINE, action, "Cmd/Ctrl+U should trigger Underline");
    }
    
    @Test
    void testStrikethroughHotkey() {
        Assumptions.assumeTrue(nativeAvailable, "Native hotkey support not available");
        
        // Strikethrough requires Shift
        int action = NativeAccess.hotkeyCheck('S', primaryMod | NativeLibrary.MOD_SHIFT);
        assertEquals(NativeLibrary.ACTION_STRIKETHROUGH, action, 
            "Cmd/Ctrl+Shift+S should trigger Strikethrough");
        
        // Without Shift, should not match
        action = NativeAccess.hotkeyCheck('S', primaryMod);
        assertEquals(NativeLibrary.ACTION_NONE, action, 
            "Cmd/Ctrl+S without Shift should not trigger Strikethrough");
    }
    
    @Test
    void testUnboundKeys() {
        Assumptions.assumeTrue(nativeAvailable, "Native hotkey support not available");
        
        // X is not bound to any action
        int action = NativeAccess.hotkeyCheck('X', primaryMod);
        assertEquals(NativeLibrary.ACTION_NONE, action, "Unbound key should return ACTION_NONE");
        
        // Z is not bound
        action = NativeAccess.hotkeyCheck('Z', primaryMod);
        assertEquals(NativeLibrary.ACTION_NONE, action, "Z should return ACTION_NONE");
    }
    
    @Test
    void testWrongModifier() {
        Assumptions.assumeTrue(nativeAvailable, "Native hotkey support not available");
        
        // Alt alone should not trigger Bold
        int action = NativeAccess.hotkeyCheck('B', NativeLibrary.MOD_ALT);
        assertEquals(NativeLibrary.ACTION_NONE, action, "Alt+B should not trigger Bold");
        
        // No modifier should not trigger
        action = NativeAccess.hotkeyCheck('B', NativeLibrary.MOD_NONE);
        assertEquals(NativeLibrary.ACTION_NONE, action, "B alone should not trigger Bold");
    }
    
    @Test
    void testDisplayString() {
        Assumptions.assumeTrue(nativeAvailable, "Native hotkey support not available");
        
        String boldDisplay = NativeAccess.hotkeyGetDisplayString(NativeLibrary.ACTION_BOLD);
        assertNotNull(boldDisplay, "Display string should not be null");
        assertFalse(boldDisplay.isEmpty(), "Display string should not be empty");
        
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("mac")) {
            assertEquals("⌘B", boldDisplay, "macOS Bold display should be ⌘B");
        } else {
            assertEquals("Ctrl+B", boldDisplay, "Non-macOS Bold display should be Ctrl+B");
        }
    }
    
    @Test
    void testModifierConversion() {
        // Test that AWT modifier conversion works correctly
        int awtMeta = java.awt.event.InputEvent.META_DOWN_MASK;
        int awtCtrl = java.awt.event.InputEvent.CTRL_DOWN_MASK;
        int awtShift = java.awt.event.InputEvent.SHIFT_DOWN_MASK;
        int awtAlt = java.awt.event.InputEvent.ALT_DOWN_MASK;
        
        assertEquals(NativeLibrary.MOD_META, NativeAccess.convertAwtModifiers(awtMeta));
        assertEquals(NativeLibrary.MOD_CTRL, NativeAccess.convertAwtModifiers(awtCtrl));
        assertEquals(NativeLibrary.MOD_SHIFT, NativeAccess.convertAwtModifiers(awtShift));
        assertEquals(NativeLibrary.MOD_ALT, NativeAccess.convertAwtModifiers(awtAlt));
        
        // Combined modifiers
        int combined = awtCtrl | awtShift;
        int expected = NativeLibrary.MOD_CTRL | NativeLibrary.MOD_SHIFT;
        assertEquals(expected, NativeAccess.convertAwtModifiers(combined));
    }
}
