/*
 * SIMJOT - No Derivatives License
 *
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 *
 * See LICENSE for full terms.
 */

package main.infrastructure.ffi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SetupStatusTest {

    @Test
    void reportsCompletedSetup(@TempDir Path tempDir) {
        Path root = tempDir.resolve("Simjot");

        assertEquals(0, NativeAccess.setupInit(root.toString()));

        int[] status = NativeAccess.setupStatus(root.toString());
        assertEquals(8, status.length);
        assertEquals(1, status[0], "root should exist");
        assertEquals(1, status[1], "root should be writable");
        assertEquals(1, status[2], "notebooks should be ready");
        assertEquals(1, status[3], "mood should be ready");
        assertEquals(1, status[4], "settings should be ready");
        assertEquals(1, status[5], "wallpapers should be ready");
        assertEquals(1, status[7], "setup should be complete");
        assertTrue(NativeAccess.isSetupComplete(root.toString()));
    }
}
