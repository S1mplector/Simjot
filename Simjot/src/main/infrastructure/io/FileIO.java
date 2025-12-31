/*
 * SIMJOT - PROPRIETARY
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 * 
 * This source code is licensed under the Simjot Source-Available Personal Use License.
 * You may view and study this code for personal, non-commercial use.
 * Distribution, commercial use, and derivative works are strictly prohibited.
 * 
 * See LICENSE.md for full terms.
 */

package main.infrastructure.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.HexFormat;

import main.infrastructure.ffi.NativeAccess;

/**
 * File I/O helpers for atomic writes, fsync, checksum, and temp cleanup.
 * Provides utility methods for file operations.
 * Includes atomic write with optional fsync, SHA-256 checksum calculation,
 * disk space checking, temporary file cleanup, and file locking.
 * Utilizes native methods when available for performance.
 * Thread-local buffers are used for efficient I/O operations.
 * Logging is performed for key operations.
 * This class is thread-safe.
 * @see NativeAccess
 * @see IoLog
 */
public final class FileIO {
    private FileIO() {}
    private static final int DIGEST_BUFFER_SIZE = 64 * 1024;
    private static final ThreadLocal<ByteBuffer> DIGEST_BUFFER =
            ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(DIGEST_BUFFER_SIZE));

    public static void atomicWrite(Path target, byte[] data, boolean fsyncFile, boolean fsyncDir) throws IOException {
        if (target == null) throw new IllegalArgumentException("target is null");
        Path dir = target.getParent();
        if (dir == null) throw new IOException("Missing parent for " + target);
        long start = System.currentTimeMillis();
        Files.createDirectories(dir);
        Boolean nativeOk = NativeAccess.atomicWrite(target, data, fsyncFile, fsyncDir);
        if (Boolean.TRUE.equals(nativeOk)) {
            IoLog.info("atomic-write", "Wrote " + target.getFileName() + " (" + data.length + " bytes) in " + (System.currentTimeMillis() - start) + "ms");
            return;
        }
        String base = target.getFileName().toString();
        String prefix = base.length() >= 3 ? base : ("tmp" + base);
        Path tmp = Files.createTempFile(dir, prefix, ".tmp");
        try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buf = ByteBuffer.wrap(data);
            while (buf.hasRemaining()) {
                ch.write(buf);
            }
            if (fsyncFile) {
                try { ch.force(true); } catch (Throwable ignored) {}
            }
        }
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable t) {
            try { Files.deleteIfExists(tmp); } catch (Throwable ignored) {}
            throw t;
        }
        if (fsyncDir) {
            fsyncDirectory(dir);
        }
        IoLog.info("atomic-write", "Wrote " + target.getFileName() + " (" + data.length + " bytes) in " + (System.currentTimeMillis() - start) + "ms");
    }

    public static void atomicWrite(Path target, String data, Charset cs, boolean fsyncFile, boolean fsyncDir) throws IOException {
        atomicWrite(target, data.getBytes(cs), fsyncFile, fsyncDir);
    }

    public static String sha256(Path path) throws IOException {
        String nativeHash = NativeAccess.sha256(path);
        if (nativeHash != null) return nativeHash;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (FileChannel ch = FileChannel.open(path, StandardOpenOption.READ)) {
                ByteBuffer buf = DIGEST_BUFFER.get();
                buf.clear();
                while (ch.read(buf) > 0) {
                    buf.flip();
                    md.update(buf);
                    buf.clear();
                }
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            throw new IOException("Checksum failed for " + path, e);
        }
    }

    public static void ensureSpace(Path dir, long bytesNeeded, String context) throws IOException {
        if (dir == null) return;
        Path p = Files.isDirectory(dir) ? dir : dir.getParent();
        if (p == null) return;
        Boolean nativeOk = NativeAccess.ensureSpace(p, bytesNeeded);
        if (nativeOk != null) {
            if (!nativeOk) {
                long usable = p.toFile().getUsableSpace();
                throw new IOException("Insufficient disk space for " + context + ": need " + bytesNeeded + " bytes, have " + usable);
            }
            return;
        }
        long usable = p.toFile().getUsableSpace();
        if (usable < bytesNeeded) {
            throw new IOException("Insufficient disk space for " + context + ": need " + bytesNeeded + " bytes, have " + usable);
        }
    }

    public static void copyFile(Path src, Path dst, boolean copyAttributes) throws IOException {
        if (src == null || dst == null) throw new IllegalArgumentException("source or destination is null");
        Path parent = dst.getParent();
        if (parent != null) Files.createDirectories(parent);
        Boolean nativeOk = NativeAccess.copyFile(src, dst, copyAttributes);
        if (Boolean.TRUE.equals(nativeOk)) return;
        if (copyAttributes) {
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        } else {
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void cleanupTempFiles(Path root, String suffix, long olderThanMs) {
        if (root == null || suffix == null) return;
        long now = System.currentTimeMillis();
        try {
            Files.walkFileTree(root, new java.nio.file.SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!file.getFileName().toString().endsWith(suffix)) {
                        return FileVisitResult.CONTINUE;
                    }
                    try {
                        long age = now - attrs.lastModifiedTime().toMillis();
                        if (age >= olderThanMs) {
                            Files.deleteIfExists(file);
                            IoLog.info("tmp-clean", "Deleted temp file: " + file);
                        }
                    } catch (IOException ignored) {}
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {}
    }

    public static FileLock tryLock(Path file) throws IOException {
        if (file == null) return null;
        Files.createDirectories(file.getParent());
        FileChannel ch = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock lock = ch.tryLock();
            if (lock == null) {
                try { ch.close(); } catch (Throwable ignored) {}
            }
            return lock;
        } catch (IOException e) {
            ch.close();
            throw e;
        }
    }

    public static void releaseQuietly(FileLock lock) {
        if (lock == null) return;
        try {
            FileChannel ch = lock.channel();
            try { lock.release(); } catch (Throwable ignored) {}
            try { ch.close(); } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    private static void fsyncDirectory(Path dir) {
        try (FileChannel ch = FileChannel.open(dir, StandardOpenOption.READ)) {
            ch.force(true);
        } catch (Throwable ignored) {
            // Best-effort only.
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NATIVE FILE SYSTEM OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if path exists using native implementation when available.
     */
    public static boolean exists(Path path) {
        if (path == null) return false;
        if (NativeAccess.fsExists(path.toString())) return true;
        return Files.exists(path);
    }

    /**
     * Check if path is directory using native implementation when available.
     */
    public static boolean isDirectory(Path path) {
        if (path == null) return false;
        if (NativeAccess.fsIsDir(path.toString())) return true;
        return Files.isDirectory(path);
    }

    /**
     * Get file size using native implementation when available.
     */
    public static long size(Path path) {
        if (path == null) return -1;
        long nativeSize = NativeAccess.fsSize(path.toString());
        if (nativeSize >= 0) return nativeSize;
        try {
            return Files.size(path);
        } catch (IOException e) {
            return -1;
        }
    }

    /**
     * Get file modification time using native implementation when available.
     */
    public static long lastModified(Path path) {
        if (path == null) return -1;
        long nativeMtime = NativeAccess.fsMtime(path.toString());
        if (nativeMtime >= 0) return nativeMtime;
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return -1;
        }
    }

    /**
     * Read all bytes from file using native implementation when available.
     */
    public static byte[] readAllBytes(Path path) throws IOException {
        if (path == null) throw new IllegalArgumentException("path is null");
        byte[] nativeData = NativeAccess.fsReadAll(path.toString());
        if (nativeData != null) return nativeData;
        return Files.readAllBytes(path);
    }

    /**
     * Write all bytes to file using native implementation when available.
     */
    public static void writeAllBytes(Path path, byte[] data) throws IOException {
        if (path == null) throw new IllegalArgumentException("path is null");
        if (data == null) throw new IllegalArgumentException("data is null");
        if (NativeAccess.fsWriteAll(path.toString(), data)) return;
        Files.write(path, data);
    }

    /**
     * Create directories using native implementation when available.
     */
    public static void createDirectories(Path path) throws IOException {
        if (path == null) return;
        if (NativeAccess.fsMkdir(path.toString())) return;
        Files.createDirectories(path);
    }

    /**
     * Delete file or empty directory using native implementation when available.
     */
    public static boolean delete(Path path) {
        if (path == null) return false;
        if (NativeAccess.fsRemove(path.toString())) return true;
        try {
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Robustly delete a file with verification and retries.
     * This method ensures the file is actually deleted.
     * 
     * @param path The file to delete
     * @return true if file was deleted or didn't exist, false if deletion failed
     */
    public static boolean deleteWithVerify(Path path) {
        if (path == null) return true;
        if (!Files.exists(path)) return true;
        
        // Try native first
        if (NativeAccess.fsRemove(path.toString())) {
            if (!Files.exists(path)) {
                IoLog.info("delete", "Deleted: " + path);
                return true;
            }
        }
        
        // Try Java NIO with retries
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                Files.deleteIfExists(path);
                if (!Files.exists(path)) {
                    IoLog.info("delete", "Deleted: " + path);
                    return true;
                }
            } catch (IOException e) {
                // May be locked, wait briefly
                try { Thread.sleep(50); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
        
        // Last resort: try old File API
        try {
            boolean deleted = path.toFile().delete();
            if (deleted && !Files.exists(path)) {
                IoLog.info("delete", "Deleted (legacy): " + path);
                return true;
            }
        } catch (SecurityException ignored) {}
        
        IoLog.info("delete", "WARN: Failed to delete: " + path);
        return false;
    }

    /**
     * Robustly delete a file, throwing if deletion fails.
     * 
     * @param path The file to delete
     * @throws IOException if file exists and could not be deleted
     */
    public static void deleteOrThrow(Path path) throws IOException {
        if (path == null) return;
        if (!Files.exists(path)) return;
        if (!deleteWithVerify(path)) {
            throw new IOException("Failed to delete file: " + path);
        }
    }

    /**
     * Recursively delete a directory and all its contents.
     * 
     * @param dir The directory to delete
     * @return true if directory was fully deleted, false if any deletion failed
     */
    public static boolean deleteRecursively(Path dir) {
        if (dir == null) return true;
        if (!Files.exists(dir)) return true;
        
        final boolean[] success = {true};
        
        try {
            Files.walkFileTree(dir, new java.nio.file.SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!deleteWithVerify(file)) {
                        success[0] = false;
                    }
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) {
                    if (!deleteWithVerify(d)) {
                        success[0] = false;
                    }
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    success[0] = false;
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            IoLog.info("delete", "WARN: Error during recursive delete of " + dir + ": " + e.getMessage());
            return false;
        }
        
        return success[0] && !Files.exists(dir);
    }

    /**
     * Move file to trash/recycle bin if possible, otherwise delete.
     * Note: Java doesn't have built-in trash support, so this just deletes.
     * The method is provided for API symmetry and future enhancement.
     * 
     * @param path The file to move to trash
     * @return true if file was trashed/deleted, false if failed
     */
    public static boolean moveToTrash(Path path) {
        // Java Desktop API has moveToTrash on some platforms
        if (path == null) return true;
        if (!Files.exists(path)) return true;
        
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
                if (desktop.isSupported(java.awt.Desktop.Action.MOVE_TO_TRASH)) {
                    if (desktop.moveToTrash(path.toFile())) {
                        IoLog.info("delete", "Moved to trash: " + path);
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        
        // Fall back to regular delete
        return deleteWithVerify(path);
    }

    /**
     * Rename/move file using native implementation when available.
     */
    public static boolean rename(Path source, Path target) {
        if (source == null || target == null) return false;
        if (NativeAccess.fsRename(source.toString(), target.toString())) return true;
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Get file extension using native implementation when available.
     */
    public static String getExtension(Path path) {
        if (path == null) return null;
        String nativeExt = NativeAccess.fsExtension(path.toString());
        if (nativeExt != null) return nativeExt;
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1) : "";
    }

    /**
     * Get file basename using native implementation when available.
     */
    public static String getBasename(Path path) {
        if (path == null) return null;
        String nativeName = NativeAccess.fsBasename(path.toString());
        if (nativeName != null) return nativeName;
        return path.getFileName().toString();
    }

    /**
     * Join paths using native implementation when available.
     */
    public static String joinPath(String base, String child) {
        if (base == null || child == null) return null;
        String nativeJoined = NativeAccess.fsJoin(base, child);
        if (nativeJoined != null) return nativeJoined;
        return Path.of(base, child).toString();
    }
}
