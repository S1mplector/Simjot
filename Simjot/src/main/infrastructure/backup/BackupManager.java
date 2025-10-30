package main.infrastructure.backup;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

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

    public static void performBackup(File srcRoot,
                                     File backupRoot,
                                     int keepCount,
                                     int pruneDays,
                                     boolean includeMood,
                                     boolean includeSettings,
                                     boolean includeWallpapers,
                                     boolean verify) throws IOException {
        if (srcRoot == null || backupRoot == null) return;
        if (!srcRoot.exists() || !srcRoot.isDirectory()) return;
        if (!backupRoot.exists()) backupRoot.mkdirs();

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File target = new File(backupRoot, "backup_" + stamp);
        Path targetPath = target.toPath();

        // Ensure base
        Files.createDirectories(targetPath);

        // Always include notebooks folder if present
        copyIfExists(new File(srcRoot, "notebooks"), new File(target, "notebooks"));

        // Include legacy content if present (best-effort for older installs)
        copyIfExists(new File(srcRoot, "entries"), new File(target, "entries"));
        copyIfExists(new File(srcRoot, "poems"), new File(target, "poems"));
        copyIfExists(new File(srcRoot, "drawings"), new File(target, "drawings"));
        copyIfExists(new File(srcRoot, "tasks"), new File(target, "tasks"));

        // Include metadata file if present
        copyIfExists(new File(srcRoot, "notebooks.json"), new File(target, "notebooks.json"));

        if (includeMood) copyIfExists(new File(srcRoot, "mood"), new File(target, "mood"));
        if (includeSettings) copyIfExists(new File(srcRoot, "settings"), new File(target, "settings"));
        if (includeWallpapers) copyIfExists(new File(srcRoot, "wallpapers"), new File(target, "wallpapers"));

        if (verify) {
            // Simple verification: ensure target exists and has at least the same total size for included parts
            long srcSize = 0L;
            srcSize += sizeIfExists(new File(srcRoot, "notebooks"));
            srcSize += sizeIfExists(new File(srcRoot, "entries"));
            srcSize += sizeIfExists(new File(srcRoot, "poems"));
            srcSize += sizeIfExists(new File(srcRoot, "drawings"));
            srcSize += sizeIfExists(new File(srcRoot, "tasks"));
            srcSize += sizeIfExists(new File(srcRoot, "notebooks.json"));
            if (includeMood) srcSize += sizeIfExists(new File(srcRoot, "mood"));
            if (includeSettings) srcSize += sizeIfExists(new File(srcRoot, "settings"));
            if (includeWallpapers) srcSize += sizeIfExists(new File(srcRoot, "wallpapers"));

            long dstSize = 0L;
            dstSize += sizeIfExists(new File(target, "notebooks"));
            dstSize += sizeIfExists(new File(target, "entries"));
            dstSize += sizeIfExists(new File(target, "poems"));
            dstSize += sizeIfExists(new File(target, "drawings"));
            dstSize += sizeIfExists(new File(target, "tasks"));
            dstSize += sizeIfExists(new File(target, "notebooks.json"));
            if (includeMood) dstSize += sizeIfExists(new File(target, "mood"));
            if (includeSettings) dstSize += sizeIfExists(new File(target, "settings"));
            if (includeWallpapers) dstSize += sizeIfExists(new File(target, "wallpapers"));

            if (dstSize < srcSize) {
                throw new IOException("Backup verification failed: destination size smaller than source");
            }
        }

        pruneOldBackups(backupRoot, keepCount, pruneDays);
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

    private static void copyIfExists(File src, File dst) throws IOException {
        if (src == null) return;
        if (!src.exists()) return;
        if (src.isDirectory()) copyDirectory(src.toPath(), dst.toPath());
        else {
            Files.createDirectories(Objects.requireNonNull(dst.getParentFile()).toPath());
            Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private static long sizeIfExists(File f) {
        if (f == null || !f.exists()) return 0L;
        if (f.isFile()) return f.length();
        File[] kids = f.listFiles();
        long sum = 0L;
        if (kids != null) for (File k : kids) sum += sizeIfExists(k);
        return sum;
    }

    private static void pruneOldBackups(File backupRoot, int keepCount, int pruneDays) {
        File[] dirs = backupRoot.listFiles(f -> f.isDirectory() && f.getName().startsWith("backup_"));
        if (dirs == null || dirs.length == 0) return;
        Arrays.sort(dirs, Comparator.comparing(File::getName).reversed());
        // First enforce keepCount by deleting beyond index keepCount-1
        for (int i = keepCount; i < dirs.length; i++) deleteRecursive(dirs[i]);
        // Then prune by age if requested (but never delete the most recent keepCount backups)
        if (pruneDays > 0) {
            long now = System.currentTimeMillis();
            long maxAgeMs = pruneDays * 24L * 3600L * 1000L;
            for (int i = keepCount; i < dirs.length; i++) {
                File d = dirs[i];
                long age = now - d.lastModified();
                if (age > maxAgeMs) deleteRecursive(d);
            }
        }
    }

    private static void deleteRecursive(File f) {
        File[] children = f.listFiles();
        if (children != null) for (File c : children) deleteRecursive(c);
        // best-effort
        try { Files.deleteIfExists(f.toPath()); } catch (IOException ignored) {}
    }

    public static void restoreFromBackup(File backupDir, File dstRoot) throws IOException {
        if (backupDir == null || dstRoot == null) return;
        if (!backupDir.exists() || !backupDir.isDirectory()) return;
        if (!dstRoot.exists()) dstRoot.mkdirs();
        // Copy everything under backupDir into dstRoot
        Files.walk(backupDir.toPath()).forEach(path -> {
            try {
                Path rel = backupDir.toPath().relativize(path);
                Path dest = dstRoot.toPath().resolve(rel);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(Objects.requireNonNull(dest.getParent()));
                    Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
            } catch (IOException ignored) {}
        });
    }
}
