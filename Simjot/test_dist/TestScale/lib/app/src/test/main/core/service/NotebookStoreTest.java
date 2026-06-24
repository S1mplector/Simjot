/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import main.infrastructure.backup.NotebookInfo;
import main.infrastructure.io.AppDirectories;

@DisplayName("NotebookStore")
class NotebookStoreTest {

    private File previousRoot;

    @BeforeEach
    void captureRoot() {
        try {
            previousRoot = AppDirectories.getRoot();
        } catch (Throwable ignored) {
            previousRoot = null;
        }
    }

    @AfterEach
    void restoreRoot() {
        AppDirectories.setRoot(previousRoot);
    }

    @Test
    @DisplayName("recovers notebook folders that are missing from notebooks.json")
    void recoversMissingNotebookFolders(@TempDir Path tempDir) throws IOException {
        AppDirectories.setRoot(tempDir.toFile());
        Files.createDirectories(tempDir.resolve("notebooks"));

        Path dailyFolder = Files.createDirectories(tempDir.resolve("notebooks/Daily"));
        Files.writeString(dailyFolder.resolve("20260101_120000.note"), "entry");
        Files.writeString(
                tempDir.resolve("notebooks.json"),
                """
                [
                  {"name":"Daily","type":"JOURNAL","folder":"notebooks/Daily","created":1,"iconId":"notebook","description":"","accentColor":-1}
                ]
                """);

        Path notesFolder = Files.createDirectories(tempDir.resolve("notebooks/Linear Algebra (Resit)"));
        Files.writeString(notesFolder.resolve("20260107_182516.ntk"), "notes");
        Path poemsFolder = Files.createDirectories(tempDir.resolve("notebooks/Poems"));
        Files.writeString(poemsFolder.resolve("20260108_155042.poem"), "poem");

        NotebookStore store = new NotebookStore();
        Map<String, NotebookInfo> byName = store.list().stream()
                .collect(Collectors.toMap(NotebookInfo::getName, Function.identity()));

        assertEquals(3, byName.size());
        assertEquals(NotebookInfo.Type.JOURNAL, byName.get("Daily").getType());
        assertEquals(NotebookInfo.Type.NOTETAKING, byName.get("Linear Algebra (Resit)").getType());
        assertEquals(NotebookInfo.Type.POETRY, byName.get("Poems").getType());

        String persisted = Files.readString(tempDir.resolve("notebooks.json"));
        assertTrue(persisted.contains("\"name\":\"Linear Algebra (Resit)\""));
        assertTrue(persisted.contains("\"folder\":\"notebooks/Linear Algebra (Resit)\""));
        assertTrue(persisted.contains("\"name\":\"Poems\""));
    }

    @Test
    @DisplayName("rebuilds notebooks.json from folders when the registry is missing")
    void rebuildsRegistryFromNotebookFolders(@TempDir Path tempDir) throws IOException {
        AppDirectories.setRoot(tempDir.toFile());
        Path notebooksRoot = Files.createDirectories(tempDir.resolve("notebooks"));
        Path thoughtsFolder = Files.createDirectories(notebooksRoot.resolve("Thoughts"));
        Files.writeString(thoughtsFolder.resolve("20251231_195753.note"), "thought");

        NotebookStore store = new NotebookStore();

        assertEquals(1, store.list().size());
        NotebookInfo recovered = store.list().get(0);
        assertEquals("Thoughts", recovered.getName());
        assertEquals(NotebookInfo.Type.JOURNAL, recovered.getType());
        assertNotNull(recovered.getFolder());

        Path savedRegistry = tempDir.resolve("notebooks.json");
        assertTrue(Files.exists(savedRegistry));
        String persisted = Files.readString(savedRegistry);
        assertTrue(persisted.contains("\"name\":\"Thoughts\""));
    }
}
