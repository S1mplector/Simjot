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
