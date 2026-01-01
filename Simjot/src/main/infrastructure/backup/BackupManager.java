/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.infrastructure.backup;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import main.core.security.crypto.ContentType;
import main.core.security.crypto.CryptoConfig;
import main.core.security.crypto.CryptoException;
import main.core.security.crypto.SimjotCrypto;
import main.infrastructure.io.FileIO;

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
        try {
            performBackup(srcRoot, backupRoot, keepCount, pruneDays,
                    includeMood, includeSettings, includeWallpapers,
                    verify, false, null);
        } catch (CryptoException ex) {
            throw new IOException("Backup encryption failed", ex);
        }
    }

    public static void performBackup(File srcRoot,
                                     File backupRoot,
                                     int keepCount,
                                     int pruneDays,
                                     boolean includeMood,
                                     boolean includeSettings,
                                     boolean includeWallpapers,
                                     boolean verify,
                                     boolean encrypt,
                                     String password) throws IOException, CryptoException {
        if (srcRoot == null || backupRoot == null) return;
        if (!srcRoot.exists() || !srcRoot.isDirectory()) return;
        if (!backupRoot.exists()) backupRoot.mkdirs();

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String name = "backup_" + stamp;

        if (encrypt) {
            if (password == null || password.isBlank()) {
                throw new CryptoException("Encryption password required", CryptoException.ErrorCode.INVALID_PASSWORD);
            }
            File targetFile = new File(backupRoot, name + CryptoConfig.ENCRYPTED_BACKUP_EXTENSION);
            createEncryptedBackup(srcRoot, targetFile, password, includeMood, includeSettings, includeWallpapers, name);
            if (verify && !looksEncryptedBackup(targetFile)) {
                throw new IOException("Backup verification failed: encrypted archive invalid");
            }
        } else {
            File target = new File(backupRoot, name);
            Path targetPath = target.toPath();
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
                long srcSize = estimateBackupSize(srcRoot, includeMood, includeSettings, includeWallpapers);
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
                    FileIO.copyFile(path, dest, true);
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
            FileIO.copyFile(src.toPath(), dst.toPath(), true);
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

    public static long estimateBackupSize(File srcRoot,
                                          boolean includeMood,
                                          boolean includeSettings,
                                          boolean includeWallpapers) {
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
        return srcSize;
    }

    private static void pruneOldBackups(File backupRoot, int keepCount, int pruneDays) {
        File[] items = backupRoot.listFiles(f -> {
            if (f == null) return false;
            if (f.isDirectory()) return f.getName().startsWith("backup_");
            if (f.isFile()) return looksEncryptedBackup(f);
            return false;
        });
        if (items == null || items.length == 0) return;
        Arrays.sort(items, Comparator.comparing(BackupManager::backupSortKey).reversed());
        // First enforce keepCount by deleting beyond index keepCount-1
        for (int i = keepCount; i < items.length; i++) deleteRecursive(items[i]);
        // Then prune by age if requested (but never delete the most recent keepCount backups)
        if (pruneDays > 0) {
            long now = System.currentTimeMillis();
            long maxAgeMs = pruneDays * 24L * 3600L * 1000L;
            for (int i = keepCount; i < items.length; i++) {
                File item = items[i];
                long age = now - item.lastModified();
                if (age > maxAgeMs) deleteRecursive(item);
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
        try {
            restoreFromBackup(backupDir, dstRoot, null);
        } catch (CryptoException ex) {
            throw new IOException("Restore failed", ex);
        }
    }

    public static void restoreFromBackup(File backupDir, File dstRoot, String password) throws IOException, CryptoException {
        if (backupDir == null || dstRoot == null) return;
        if (!backupDir.exists()) return;
        if (!dstRoot.exists()) dstRoot.mkdirs();
        if (backupDir.isDirectory()) {
            // Copy everything under backupDir into dstRoot
            Files.walk(backupDir.toPath()).forEach(path -> {
                try {
                    Path rel = backupDir.toPath().relativize(path);
                    Path dest = dstRoot.toPath().resolve(rel);
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.createDirectories(Objects.requireNonNull(dest.getParent()));
                        FileIO.copyFile(path, dest, true);
                    }
                } catch (IOException ignored) {}
            });
            return;
        }

        if (!looksEncryptedBackup(backupDir)) {
            throw new IOException("Unsupported backup format");
        }
        if (password == null || password.isBlank()) {
            throw new CryptoException("Encryption password required", CryptoException.ErrorCode.INVALID_PASSWORD);
        }
        restoreFromEncryptedArchive(backupDir, dstRoot, password);
    }

    private static void createEncryptedBackup(File srcRoot,
                                              File targetFile,
                                              String password,
                                              boolean includeMood,
                                              boolean includeSettings,
                                              boolean includeWallpapers,
                                              String identifier) throws IOException, CryptoException {
        if (targetFile.exists()) Files.deleteIfExists(targetFile.toPath());
        CryptoConfig config = CryptoConfig.forBackups()
                .withCompression(false)
                .withIdentifier(identifier);
        SimjotCrypto crypto = new SimjotCrypto();
        // Important: BufferedOutputStream must be in try-with-resources for proper close ordering
        // Close order: zos -> buffered -> encStream -> fos (cipher finalization needs proper flush)
        try (FileOutputStream fos = new FileOutputStream(targetFile);
             OutputStream encStream = crypto.createEncryptingStream(
                     fos, password, ContentType.BACKUP, config);
             BufferedOutputStream buffered = new BufferedOutputStream(encStream);
             ZipOutputStream zos = new ZipOutputStream(buffered)) {
            writeBackupZip(zos, srcRoot, includeMood, includeSettings, includeWallpapers);
            zos.finish(); // Explicitly finish zip before streams close
            buffered.flush(); // Ensure all data is flushed to cipher stream
        } catch (IOException | CryptoException ex) {
            try { Files.deleteIfExists(targetFile.toPath()); } catch (IOException ignored) {}
            throw ex;
        }
    }

    private static void writeBackupZip(ZipOutputStream zos,
                                       File srcRoot,
                                       boolean includeMood,
                                       boolean includeSettings,
                                       boolean includeWallpapers) throws IOException {
        addDirectoryToZip(srcRoot, "notebooks", zos);
        addDirectoryToZip(srcRoot, "entries", zos);
        addDirectoryToZip(srcRoot, "poems", zos);
        addDirectoryToZip(srcRoot, "drawings", zos);
        addDirectoryToZip(srcRoot, "tasks", zos);
        addFileToZip(srcRoot, "notebooks.json", zos);

        if (includeMood) addDirectoryToZip(srcRoot, "mood", zos);
        if (includeSettings) addDirectoryToZip(srcRoot, "settings", zos);
        if (includeWallpapers) addDirectoryToZip(srcRoot, "wallpapers", zos);
    }

    private static void addFileToZip(File srcRoot, String name, ZipOutputStream zos) throws IOException {
        File file = new File(srcRoot, name);
        if (!file.exists() || !file.isFile()) return;
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(file.lastModified());
        zos.putNextEntry(entry);
        Files.copy(file.toPath(), zos);
        zos.closeEntry();
    }

    private static void addDirectoryToZip(File srcRoot, String name, ZipOutputStream zos) throws IOException {
        File dir = new File(srcRoot, name);
        if (!dir.exists() || !dir.isDirectory()) return;
        Path base = dir.toPath();
        Files.walkFileTree(base, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dirPath, BasicFileAttributes attrs) throws IOException {
                try {
                    String rel = base.relativize(dirPath).toString().replace(File.separatorChar, '/');
                    String entryName = name + "/";
                    if (!rel.isEmpty()) entryName += rel + "/";
                    ZipEntry entry = new ZipEntry(entryName);
                    entry.setTime(attrs.lastModifiedTime().toMillis());
                    zos.putNextEntry(entry);
                    zos.closeEntry();
                } catch (IOException ignored) {}
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                try {
                    String rel = base.relativize(file).toString().replace(File.separatorChar, '/');
                    String entryName = name + "/" + rel;
                    ZipEntry entry = new ZipEntry(entryName);
                    entry.setTime(attrs.lastModifiedTime().toMillis());
                    zos.putNextEntry(entry);
                    Files.copy(file, zos);
                    zos.closeEntry();
                } catch (IOException ignored) {}
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void restoreFromEncryptedArchive(File backupFile, File dstRoot, String password)
            throws IOException, CryptoException {
        SimjotCrypto crypto = new SimjotCrypto();
        Path rootPath = dstRoot.toPath().toAbsolutePath().normalize();
        try (InputStream fis = new BufferedInputStream(new FileInputStream(backupFile));
             InputStream decStream = crypto.createDecryptingStream(fis, password);
             ZipInputStream zis = new ZipInputStream(new BufferedInputStream(decStream))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name == null || name.isBlank()) {
                    zis.closeEntry();
                    continue;
                }
                Path target = rootPath.resolve(name).normalize();
                if (!target.startsWith(rootPath)) {
                    throw new IOException("Invalid backup entry: " + name);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(Objects.requireNonNull(target.getParent()));
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                }
                if (entry.getTime() > 0) {
                    try { Files.setLastModifiedTime(target, java.nio.file.attribute.FileTime.fromMillis(entry.getTime())); } catch (IOException ignored) {}
                }
                zis.closeEntry();
            }
        }
    }

    private static boolean looksEncryptedBackup(File file) {
        if (file == null || !file.isFile()) return false;
        String name = file.getName().toLowerCase();
        if (!name.endsWith(CryptoConfig.ENCRYPTED_BACKUP_EXTENSION)) return false;
        try (InputStream in = new FileInputStream(file)) {
            byte[] magic = new byte[CryptoConfig.MAGIC_BYTES.length];
            if (in.read(magic) != magic.length) return false;
            return Arrays.equals(magic, CryptoConfig.MAGIC_BYTES);
        } catch (IOException ignored) {
            return false;
        }
    }

    private static String backupSortKey(File file) {
        if (file == null) return "";
        String name = file.getName();
        if (name.endsWith(CryptoConfig.ENCRYPTED_BACKUP_EXTENSION)) {
            return name.substring(0, name.length() - CryptoConfig.ENCRYPTED_BACKUP_EXTENSION.length());
        }
        return name;
    }
}
