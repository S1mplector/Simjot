package main.core.service;

import main.infrastructure.backup.NotebookInfo;
import main.infrastructure.io.AppDirectories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class NotebookStoreTest {

    @TempDir
    File tempRoot;

    @BeforeEach
    void setUp() {
        AppDirectories.setRoot(tempRoot);
        // Ensure settings dir exists as some operations may save alongside
        AppDirectories.folder(AppDirectories.Type.SETTINGS);
    }

    @Test
    void createPersistAndReload() {
        NotebookStore store = new NotebookStore();
        assertTrue(store.list().isEmpty(), "Store should start empty in fresh temp root");

        NotebookInfo nb1 = store.create("MyJournal", NotebookInfo.Type.JOURNAL, "iconA");
        NotebookInfo nb2 = store.create("Poems", NotebookInfo.Type.POETRY, "iconB");

        List<NotebookInfo> first = store.list();
        assertEquals(2, first.size());

        // Folders created on disk
        assertTrue(nb1.getFolder().exists() && nb1.getFolder().isDirectory());
        assertTrue(nb2.getFolder().exists() && nb2.getFolder().isDirectory());

        // Reload from disk
        NotebookStore reloaded = new NotebookStore();
        List<String> names = reloaded.list().stream().map(NotebookInfo::getName).collect(Collectors.toList());
        assertEquals(Set.of("MyJournal", "Poems"), Set.copyOf(names));
    }

    @Test
    void rejectsDuplicateNamesCaseInsensitive() {
        NotebookStore store = new NotebookStore();
        store.create("MyNotebook", NotebookInfo.Type.JOURNAL, "iconA");
        assertThrows(IllegalArgumentException.class, () -> store.create("mynotebook", NotebookInfo.Type.POETRY, "iconB"));
    }

    @Test
    void savesAsJsonAndReloadsFromJson() throws Exception {
        NotebookStore store = new NotebookStore();
        store.create("JsonOne", NotebookInfo.Type.JOURNAL, "iconA");
        store.create("JsonTwo", NotebookInfo.Type.POETRY, "iconB");

        File jsonFile = new File(tempRoot, "notebooks.json");
        assertTrue(jsonFile.exists(), "Store should persist to notebooks.json");
        String content = Files.readString(jsonFile.toPath());
        assertTrue(content.trim().startsWith("["), "Store should write JSON array");
        assertFalse(new File(tempRoot, "notebooks.json.tmp").exists(), "Temp file should be moved atomically");

        NotebookStore reloaded = new NotebookStore();
        List<String> names = reloaded.list().stream().map(NotebookInfo::getName).collect(Collectors.toList());
        assertEquals(Set.of("JsonOne", "JsonTwo"), Set.copyOf(names));
    }

    @Test
    void renameUpdatesFolderAndStore() {
        NotebookStore store = new NotebookStore();
        NotebookInfo nb = store.create("OldName", NotebookInfo.Type.JOURNAL, "i");
        File oldFolder = nb.getFolder();

        boolean ok = store.rename(nb, "NewName");
        assertTrue(ok);

        // After rename, list should contain NewName
        List<String> names = store.list().stream().map(NotebookInfo::getName).collect(Collectors.toList());
        assertTrue(names.contains("NewName"));
        assertFalse(names.contains("OldName"));

        // Old folder should not exist; new one should
        assertFalse(oldFolder.exists());
        File newFolder = new File(oldFolder.getParentFile(), "NewName");
        assertTrue(newFolder.exists());
    }

    @Test
    void deleteRemovesFolderAndCleansMoodData() throws IOException {
        NotebookStore store = new NotebookStore();
        NotebookInfo nb = store.create("J1", NotebookInfo.Type.JOURNAL, "i");

        // Create some fake journal entries with timestamps in filenames
        File f1 = new File(nb.getFolder(), "20250101_120000.txt");
        File f2 = new File(nb.getFolder(), "20250102_090000.txt");
        try (FileWriter fw = new FileWriter(f1)) { fw.write("entry1"); }
        try (FileWriter fw = new FileWriter(f2)) { fw.write("entry2"); }

        // Create mood log with matching and non-matching timestamps
        File moodFile = new File(AppDirectories.folder(AppDirectories.Type.MOOD_DATA), "mood_log.txt");
        try (PrintWriter pw = new PrintWriter(new FileWriter(moodFile))) {
            pw.println("20250101_120000, 0.7"); // should be removed
            pw.println("20250102_090000, -0.2"); // should be removed
            pw.println("20250103_080000, 0.1"); // should stay
        }

        // Now delete the notebook, which should clean up matching mood entries and remove the folder
        store.delete(nb);

        assertFalse(nb.getFolder().exists(), "Notebook folder should be deleted");

        // Mood file should exist and no longer contain removed timestamps
        assertTrue(moodFile.exists(), "Mood log should still exist after cleanup");
        String content = java.nio.file.Files.readString(moodFile.toPath());
        assertFalse(content.contains("20250101_120000"));
        assertFalse(content.contains("20250102_090000"));
        assertTrue(content.contains("20250103_080000"));

        // Store should no longer list the notebook
        assertTrue(store.list().isEmpty());

        // Reload to ensure persistence reflects deletion
        NotebookStore reloaded = new NotebookStore();
        assertTrue(reloaded.list().isEmpty());
    }
}
