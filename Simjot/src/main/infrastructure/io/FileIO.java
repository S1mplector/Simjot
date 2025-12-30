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

import main.infrastructure.ffi.NativeAccess;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * File I/O helpers for atomic writes, fsync, checksum, and temp cleanup.
 */
public final class FileIO {
    private FileIO() {}

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
        Path tmp = dir.resolve(target.getFileName().toString() + ".tmp" + System.nanoTime());
        try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ch.write(ByteBuffer.wrap(data));
            if (fsyncFile) {
                try { ch.force(true); } catch (Throwable ignored) {}
            }
        }
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
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
                ByteBuffer buf = ByteBuffer.allocateDirect(8192);
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

    public static void cleanupTempFiles(Path root, String suffix, long olderThanMs) {
        if (root == null || suffix == null) return;
        long now = System.currentTimeMillis();
        try {
            Files.walk(root)
                    .filter(p -> p.getFileName().toString().endsWith(suffix))
                    .forEach(p -> {
                        try {
                            long age = now - Files.getLastModifiedTime(p).toMillis();
                            if (age >= olderThanMs) {
                                Files.deleteIfExists(p);
                                IoLog.info("tmp-clean", "Deleted temp file: " + p);
                            }
                        } catch (IOException ignored) {}
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
}
