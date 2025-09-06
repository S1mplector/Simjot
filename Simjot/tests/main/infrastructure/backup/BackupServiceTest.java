package main.infrastructure.backup;

import main.core.service.SettingsStore;
import main.infrastructure.io.AppDirectories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class BackupServiceTest {

    @TempDir
    File tempRoot;

    @BeforeEach
    void setUp() throws Exception {
        // point app to temp root and ensure dirs
        AppDirectories.setRoot(tempRoot);
        AppDirectories.folder(AppDirectories.Type.SETTINGS);
        resetSettingsSingleton();
    }

    @Test
    void checkAndRunIfDue_createsBackupAndUpdatesLastEpoch() throws Exception {
        // Prepare a source file to be copied
        File srcFile = new File(tempRoot, "notes/file.txt");
        assertTrue(srcFile.getParentFile().mkdirs());
        try (FileWriter fw = new FileWriter(srcFile)) { fw.write("sample"); }

        SettingsStore s = SettingsStore.get();
        s.setBackupFrequency("Daily");
        s.setBackupKeepCount(3);
        s.setLastBackupEpochMillis(0L);
        s.save();

        BackupService.get().checkAndRunIfDue();

        File backupsDir = new File(new File(AppDirectories.folder(AppDirectories.Type.SETTINGS), "backups").getAbsolutePath());
        File[] backups = listBackups(backupsDir);
        assertEquals(1, backups.length, "One backup should be created when due");
        // content copied
        assertTrue(new File(backups[0], "notes/file.txt").exists());
        // last epoch updated
        assertTrue(SettingsStore.get().getLastBackupEpochMillis() > 0L);
    }

    @Test
    void checkAndRunIfDue_skipsWhenNotDue() {
        SettingsStore s = SettingsStore.get();
        s.setBackupFrequency("Daily");
        s.setBackupKeepCount(3);
        s.setLastBackupEpochMillis(System.currentTimeMillis()); // not due
        s.save();

        BackupService.get().checkAndRunIfDue();

        File backupsDir = new File(new File(AppDirectories.folder(AppDirectories.Type.SETTINGS), "backups").getAbsolutePath());
        File[] backups = listBackups(backupsDir);
        assertEquals(0, backups.length, "No backup should be created if not due");
    }

    @Test
    void triggerOnExit_respectsInterval() throws IOException {
        // create a src file
        File srcFile = new File(tempRoot, "a.txt");
        try (FileWriter fw = new FileWriter(srcFile)) { fw.write("x"); }

        SettingsStore s = SettingsStore.get();
        s.setBackupFrequency("Weekly");
        s.setBackupKeepCount(2);
        s.setLastBackupEpochMillis(System.currentTimeMillis() - 8L * 24L * 60L * 60L * 1000L); // 8 days ago
        s.save();

        BackupService.get().triggerOnExit();
        File backupsDir = new File(new File(AppDirectories.folder(AppDirectories.Type.SETTINGS), "backups").getAbsolutePath());
        File[] backups = listBackups(backupsDir);
        assertEquals(1, backups.length, "Should create a backup when overdue on exit");

        // Now not due anymore; should not create an additional one
        long before = SettingsStore.get().getLastBackupEpochMillis();
        BackupService.get().triggerOnExit();
        backups = listBackups(backupsDir);
        assertEquals(1, backups.length, "Should not create an extra backup when not due");
        assertEquals(before, SettingsStore.get().getLastBackupEpochMillis());
    }

    @Test
    void triggerNow_alwaysCreatesBackupRegardlessOfFrequency() throws IOException {
        // clean settings
        SettingsStore s = SettingsStore.get();
        s.setBackupFrequency("Off");
        s.setBackupKeepCount(5);
        s.setLastBackupEpochMillis(0L);
        s.save();

        // ensure a file exists to copy
        File srcFile = new File(tempRoot, "data.bin");
        try (FileWriter fw = new FileWriter(srcFile)) { fw.write("data"); }

        BackupService.get().triggerNow();

        File backupsDir = new File(new File(AppDirectories.folder(AppDirectories.Type.SETTINGS), "backups").getAbsolutePath());
        File[] backups = listBackups(backupsDir);
        assertEquals(1, backups.length, "Manual trigger should create a backup even if frequency is Off");
    }

    private static File[] listBackups(File root) {
        File[] dirs = root.listFiles(f -> f.isDirectory() && f.getName().startsWith("backups"));
        // the BackupManager creates "backups/backup_YYYYMMDD_HHMMSS" under settings/backups
        // here we accept both the root not existing or empty
        File backupsRoot = new File(root.getParentFile(), "backups");
        if (backupsRoot.exists()) {
            File[] children = backupsRoot.listFiles(f -> f.isDirectory() && f.getName().startsWith("backup_"));
            return children == null ? new File[0] : Arrays.stream(children).sorted((a,b)->a.getName().compareTo(b.getName())).toArray(File[]::new);
        }
        return new File[0];
    }

    private static void resetSettingsSingleton() throws Exception {
        java.lang.reflect.Field f = SettingsStore.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, null);
    }
}
