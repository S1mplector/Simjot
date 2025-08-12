package main.util;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Reusable backup utilities. Not automatically scheduled; call from app logic.
 */
public final class BackupManager {
    private BackupManager() {}

    public enum Frequency { OFF, DAILY, WEEKLY, MONTHLY; }

    public static Frequency parseFrequency(String v) {
        if (v == null) return Frequency.OFF;
        switch (v.toLowerCase()) {
            case "daily": return Frequency.DAILY;
            case "weekly": return Frequency.WEEKLY;
            case "monthly": return Frequency.MONTHLY;
            default: return Frequency.OFF;
        }
    }

    /**
     * Creates a timestamped backup of srcDir under backupRoot, then prunes to keepCount.
     */
    public static void performBackup(File srcDir, File backupRoot, int keepCount) throws IOException {
        if (srcDir == null || backupRoot == null) return;
        if (!srcDir.exists() || !srcDir.isDirectory()) return;
        if (!backupRoot.exists()) backupRoot.mkdirs();

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File target = new File(backupRoot, "backup_" + stamp);
        copyDirectory(srcDir.toPath(), target.toPath());
        pruneOldBackups(backupRoot, keepCount);
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(path -> {
            try {
                Path rel = source.relativize(path);
                Path dest = target.resolve(rel);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
            } catch (IOException ignored) {}
        });
    }

    private static void pruneOldBackups(File backupRoot, int keepCount) {
        File[] dirs = backupRoot.listFiles(f -> f.isDirectory() && f.getName().startsWith("backup_"));
        if (dirs == null || dirs.length <= keepCount) return;
        Arrays.sort(dirs, Comparator.comparing(File::getName).reversed());
        for (int i = keepCount; i < dirs.length; i++) deleteRecursive(dirs[i]);
    }

    private static void deleteRecursive(File f) {
        File[] children = f.listFiles();
        if (children != null) for (File c : children) deleteRecursive(c);
        // best-effort
        try { Files.deleteIfExists(f.toPath()); } catch (IOException ignored) {}
    }
}
