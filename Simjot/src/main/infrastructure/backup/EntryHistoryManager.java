/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * EntryHistoryManager is responsible for managing the history of file entries,
 * including creating snapshots and maintaining a manifest of changes.
 * It provides functionality to record, retrieve, and restore snapshots of file entries.
 * Furthermore, it ensures that only a specified number of recent snapshots are retained,
 * pruning older ones as necessary.
 * It is designed to facilitate version control and data recovery for file entries.
 * It is thread-safe and handles I/O exceptions gracefully, logging warnings when operations fail.
 * 
 * @author S1mplector
 */

package main.infrastructure.backup;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import main.infrastructure.io.FileIO;
import main.infrastructure.io.IoLog;

/**
 * Rolling, per-entry history snapshots with a simple manifest.
 */
public final class EntryHistoryManager {
    private EntryHistoryManager() {}

    private static final String HISTORY_DIR = ".history";
    private static final String MANIFEST_NAME = "manifest.tsv";

    public static final class Snapshot {
        public final String entryName;
        public final String timestamp;
        public final String checksum;
        public final long size;
        public final File file;

        Snapshot(String entryName, String timestamp, String checksum, long size, File file) {
            this.entryName = entryName;
            this.timestamp = timestamp;
            this.checksum = checksum;
            this.size = size;
            this.file = file;
        }
    }

    public static void recordSnapshot(File entryFile, int keep) {
        if (entryFile == null || !entryFile.exists()) return;
        try {
            File historyRoot = new File(entryFile.getParentFile(), HISTORY_DIR);
            if (!historyRoot.exists()) historyRoot.mkdirs();
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date());
            String backupName = entryFile.getName() + "." + ts + ".bak";
            File backupFile = new File(historyRoot, backupName);
            FileIO.ensureSpace(backupFile.toPath(), entryFile.length() + 4096L, "entry history snapshot");
            FileIO.copyFile(entryFile.toPath(), backupFile.toPath(), false);
            String checksum = FileIO.sha256(entryFile.toPath());
            long size = entryFile.length();

            List<Snapshot> all = readManifest(historyRoot);
            all.add(new Snapshot(entryFile.getName(), ts, checksum, size, backupFile));
            writeManifest(historyRoot, all);
            pruneOldSnapshots(historyRoot, entryFile.getName(), keep);
        } catch (IOException e) {
            IoLog.warn("entry-history", "Failed to record snapshot for " + entryFile, e);
        }
    }

    public static Snapshot getLatestSnapshot(File entryFile) {
        if (entryFile == null) return null;
        File historyRoot = new File(entryFile.getParentFile(), HISTORY_DIR);
        List<Snapshot> snaps = listSnapshots(entryFile, historyRoot);
        return snaps.isEmpty() ? null : snaps.get(snaps.size() - 1);
    }

    public static List<Snapshot> listSnapshots(File entryFile) {
        if (entryFile == null) return List.of();
        File historyRoot = new File(entryFile.getParentFile(), HISTORY_DIR);
        return listSnapshots(entryFile, historyRoot);
    }

    public static boolean restoreSnapshot(File entryFile, Snapshot snap) {
        if (entryFile == null || snap == null || snap.file == null || !snap.file.exists()) return false;
        try {
            FileIO.copyFile(snap.file.toPath(), entryFile.toPath(), false);
            return true;
        } catch (IOException e) {
            IoLog.warn("entry-history-restore", "Failed to restore snapshot " + snap.file, e);
            return false;
        }
    }

    public static String getLastChecksum(File entryFile) {
        Snapshot latest = getLatestSnapshot(entryFile);
        return latest == null ? null : latest.checksum;
    }

    private static List<Snapshot> listSnapshots(File entryFile, File historyRoot) {
        List<Snapshot> snaps = new ArrayList<>();
        if (entryFile == null || !historyRoot.exists()) return snaps;
        for (Snapshot s : readManifest(historyRoot)) {
            if (entryFile.getName().equals(s.entryName) && s.file.exists()) {
                snaps.add(s);
            }
        }
        snaps.sort(Comparator.comparing(s -> s.timestamp));
        return snaps;
    }

    private static void pruneOldSnapshots(File historyRoot, String entryName, int keep) {
        if (keep <= 0) return;
        List<Snapshot> snaps = listSnapshots(new File(historyRoot.getParentFile(), entryName), historyRoot);
        if (snaps.size() <= keep) return;
        int toRemove = snaps.size() - keep;
        for (int i = 0; i < toRemove; i++) {
            Snapshot s = snaps.get(i);
            try { Files.deleteIfExists(s.file.toPath()); } catch (IOException ignored) {}
        }
        // Rewrite manifest without deleted entries
        List<Snapshot> all = readManifest(historyRoot);
        List<Snapshot> remaining = new ArrayList<>();
        for (Snapshot s : all) {
            if (!entryName.equals(s.entryName)) {
                remaining.add(s);
                continue;
            }
            if (s.file.exists()) remaining.add(s);
        }
        writeManifest(historyRoot, remaining);
    }

    private static List<Snapshot> readManifest(File historyRoot) {
        List<Snapshot> out = new ArrayList<>();
        File manifest = new File(historyRoot, MANIFEST_NAME);
        if (!manifest.exists()) return out;
        try {
            List<String> lines = Files.readAllLines(manifest.toPath(), StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line == null || line.isBlank()) continue;
                String[] parts = line.split("\\|", -1);
                if (parts.length < 4) continue;
                String entry = parts[0];
                String ts = parts[1];
                String checksum = parts[2];
                long size = 0L;
                try { size = Long.parseLong(parts[3]); } catch (NumberFormatException ignored) {}
                File snapFile = new File(historyRoot, entry + "." + ts + ".bak");
                out.add(new Snapshot(entry, ts, checksum, size, snapFile));
            }
        } catch (IOException ignored) {}
        return out;
    }

    private static void writeManifest(File historyRoot, List<Snapshot> snaps) {
        try {
            StringBuilder sb = new StringBuilder();
            for (Snapshot s : snaps) {
                sb.append(s.entryName).append('|')
                        .append(s.timestamp).append('|')
                        .append(s.checksum == null ? "" : s.checksum).append('|')
                        .append(s.size).append('\n');
            }
            Path manifest = new File(historyRoot, MANIFEST_NAME).toPath();
            FileIO.atomicWrite(manifest, sb.toString(), StandardCharsets.UTF_8, true, true);
        } catch (IOException e) {
            IoLog.warn("entry-history-manifest", "Failed to write manifest", e);
        }
    }
}
