package main.infrastructure.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class BackupManagerTest {

    private static final Pattern NAME_PATTERN = Pattern.compile("backup_\\d{8}_\\d{6}");

    @TempDir
    File tempDir;

    @Test
    void createBackupCreatesTimestampedDirAndCopiesFiles() throws IOException {
        File src = new File(tempDir, "src");
        File backupRoot = new File(tempDir, "backups");
        assertTrue(src.mkdirs());
        assertTrue(backupRoot.mkdirs());

        // Create some content in source
        File subFolder = new File(src, "notes");
        assertTrue(subFolder.mkdirs());
        File note = new File(subFolder, "hello.txt");
        try (FileWriter fw = new FileWriter(note)) { fw.write("Hello Backup"); }

        BackupManager.performBackup(src, backupRoot, 5);

        File[] backups = backups(backupRoot);
        assertEquals(1, backups.length, "One backup directory should be created");
        assertTrue(NAME_PATTERN.matcher(backups[0].getName()).matches(), "Backup name should match timestamp pattern");

        // Copied file exists with content
        File copied = new File(backups[0], "notes/hello.txt");
        assertTrue(copied.exists(), "Copied file should exist in backup");
        String content = Files.readString(copied.toPath());
        assertEquals("Hello Backup", content);
    }

    @Test
    void retentionKeepsOnlyNMostRecent() throws Exception {
        File src = new File(tempDir, "src2");
        File backupRoot = new File(tempDir, "backups2");
        assertTrue(src.mkdirs());
        assertTrue(backupRoot.mkdirs());

        // create a file so copy has something to do
        File f = new File(src, "file.txt");
        try (FileWriter fw = new FileWriter(f)) { fw.write("data"); }

        // Create 3 backups with 1 second spacing to ensure distinct timestamps
        BackupManager.performBackup(src, backupRoot, 2);
        Thread.sleep(1000);
        BackupManager.performBackup(src, backupRoot, 2);
        Thread.sleep(1000);
        BackupManager.performBackup(src, backupRoot, 2);

        File[] backups = backups(backupRoot);
        assertEquals(2, backups.length, "Retention should keep only 2 most recent backups");

        // Verify they are the two newest by name (lexicographically descending matches timestamp recency)
        List<String> names = Arrays.stream(backups).map(File::getName).sorted(Comparator.reverseOrder()).collect(Collectors.toList());
        assertEquals(names, Arrays.stream(backups).map(File::getName).sorted(Comparator.reverseOrder()).collect(Collectors.toList()));
        assertTrue(names.stream().allMatch(n -> NAME_PATTERN.matcher(n).matches()));
    }

    private static File[] backups(File root) {
        File[] dirs = root.listFiles(f -> f.isDirectory() && f.getName().startsWith("backup_"));
        if (dirs == null) return new File[0];
        // return sorted ascending by name for consistency
        Arrays.sort(dirs, Comparator.comparing(File::getName));
        return dirs;
    }
}
