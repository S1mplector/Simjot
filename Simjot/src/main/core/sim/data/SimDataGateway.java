/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.core.sim.data;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import main.core.service.NotebookStore;
import main.infrastructure.backup.NotebookInfo;
import main.infrastructure.io.MoodFile;

/**
 * Provides Sim read-only access to notebooks, entries and mood data.
 */
public final class SimDataGateway {
    private static SimDataGateway INSTANCE;

    private final NotebookStore notebookStore;

    private SimDataGateway() {
        this.notebookStore = new NotebookStore();
    }

    public static SimDataGateway get(){
        if (INSTANCE == null) INSTANCE = new SimDataGateway();
        return INSTANCE;
    }

    public List<NotebookInfo> listNotebooks(){
        try { return notebookStore.list(); } catch (Throwable t) { return Collections.emptyList(); }
    }

    public File getMoodLogFile(){
        return MoodFile.getPrimaryFile();
    }

    // ---- Entries API ----

    /**
     * Returns all .txt entry files in the given notebook folder (non-recursive), sorted by lastModified ascending.
     */
    public List<File> listEntries(NotebookInfo notebook) {
        if (notebook == null) return Collections.emptyList();
        File folder = notebook.getFolder();
        if (folder == null || !folder.isDirectory()) return Collections.emptyList();
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".txt"));
        if (files == null || files.length == 0) return Collections.emptyList();
        List<File> list = new ArrayList<>(Arrays.asList(files));
        list.sort(Comparator.comparingLong(File::lastModified));
        return list;
    }

    /**
     * Returns most-recent-first entry files up to a limit.
     */
    public List<File> listEntriesByModifiedDesc(NotebookInfo notebook, int limit) {
        List<File> all = listEntries(notebook);
        if (all.isEmpty()) return all;
        all.sort(Comparator.comparingLong(File::lastModified).reversed());
        if (limit > 0 && all.size() > limit) {
            return new ArrayList<>(all.subList(0, limit));
        }
        return all;
    }

    /**
     * Reads the content of an entry file. If maxChars > 0, returns only the last maxChars characters.
     */
    public String readEntry(File entryFile, int maxChars) {
        if (entryFile == null || !entryFile.isFile()) return "";
        try {
            String content = Files.readString(entryFile.toPath(), StandardCharsets.UTF_8);
            if (content.startsWith("SJMETA:")) {
                int nl = content.indexOf('\n');
                if (nl >= 0) {
                    content = content.substring(nl + 1).stripLeading();
                }
            }
            if (content.startsWith("IMGMAP:")) {
                int nl = content.indexOf('\n');
                if (nl >= 0) content = content.substring(nl + 1).stripLeading();
            }
            if (maxChars > 0 && content.length() > maxChars) {
                return content.substring(content.length() - maxChars);
            }
            return content;
        } catch (IOException ex) {
            return "";
        }
    }

    // ---- Mood API ----

    public static final class MoodSample {
        public final String timestamp; // expected format yyyyMMdd_HHmmss
        public final double value;     // mood value

        public MoodSample(String timestamp, double value) {
            this.timestamp = timestamp;
            this.value = value;
        }
    }

    /**
     * Reads mood samples from mood_log.moods (binary). Returns most-recent-first when limit > 0.
     */
    public List<MoodSample> readMoodSamples(int limit) {
        List<MoodSample> out = new ArrayList<>();
        List<MoodFile.MoodRecord> records = MoodFile.readAllRecords();
        for (MoodFile.MoodRecord rec : records) {
            if (rec == null || rec.timestamp == null) continue;
            out.add(new MoodSample(rec.legacyTimestampKey(), rec.composite));
        }

        // Sort by timestamp descending if it matches the entry filename style; otherwise by insertion order
        out.sort((a, b) -> Objects.compare(b.timestamp, a.timestamp, String::compareTo));
        if (limit > 0 && out.size() > limit) {
            return new ArrayList<>(out.subList(0, limit));
        }
        return out;
    }

    /**
     * Computes the average of the latest N mood samples. Returns NaN if not enough data.
     */
    public double computeRecentMoodAverage(int window) {
        if (window <= 0) return Double.NaN;
        List<MoodSample> samples = readMoodSamples(window);
        if (samples.isEmpty()) return Double.NaN;
        double sum = 0d;
        for (MoodSample s : samples) sum += s.value;
        return sum / samples.size();
    }
}
