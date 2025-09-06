package main.core.service;

import main.core.service.SettingsStore;
import main.infrastructure.io.AppDirectories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class SettingsStoreTest {

    @TempDir
    File tempRoot;

    @BeforeEach
    void setUp() throws Exception {
        // Point the app to a temp root so tests do not touch user data
        AppDirectories.setRoot(tempRoot);
        // Ensure a fresh singleton for each test
        resetSingleton();
    }

    @Test
    void testSaveAndReloadPreferences() throws Exception {
        SettingsStore s = SettingsStore.get();
        s.setTheme("Dark");
        s.setJournalFontSize(18);
        s.setAutosaveDelayMs(2500);
        s.setWidgetPanelVisible(false);
        s.save();

        // Recreate the singleton to reload from disk
        resetSingleton();
        SettingsStore reloaded = SettingsStore.get();

        assertEquals("Dark", reloaded.getTheme());
        assertEquals(18, reloaded.getJournalFontSize());
        assertEquals(2500, reloaded.getAutosaveDelayMs());
        assertFalse(reloaded.isWidgetPanelVisible());
    }

    @Test
    void testOpacityClampingIsPersisted() throws Exception {
        SettingsStore s = SettingsStore.get();
        s.setBackgroundOpacity(2.5f); // should clamp to 1.0
        s.setEntryBackgroundOpacity(-5f); // should clamp to 0.0
        s.setPoemBackgroundOpacity(0.25f); // within range
        s.save();

        resetSingleton();
        SettingsStore reloaded = SettingsStore.get();

        assertEquals(1.0f, reloaded.getBackgroundOpacity(), 0.0001f);
        assertEquals(0.0f, reloaded.getEntryBackgroundOpacity(), 0.0001f);
        assertEquals(0.25f, reloaded.getPoemBackgroundOpacity(), 0.0001f);
    }

    private static void resetSingleton() throws Exception {
        Field f = SettingsStore.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, null);
    }
}
