/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.entries;

import java.awt.Component;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.text.StyledDocument;
import javax.swing.text.rtf.RTFEditorKit;

import main.core.security.EncryptionManager;
import main.core.security.crypto.CryptoException;
import main.core.service.NotebookStore;
import main.infrastructure.backup.NotebookInfo;
import main.infrastructure.ffi.MacNativeBridge;
import main.infrastructure.ffi.NativeAccess;
import main.infrastructure.io.AppDirectories;
import main.infrastructure.io.FileIO;
import main.infrastructure.io.MacSecurityBookmarkStore;

public final class GlobalSearchEngine {
    private static final Pattern TAG_PATTERN = Pattern.compile("#([A-Za-z0-9_-]+)");

    private GlobalSearchEngine() {}

    public static void search(SearchQuery query, Component parent, Consumer<SearchResult> consumer) {
        search(query, parent, consumer, () -> false);
    }

    public static void search(SearchQuery query, Component parent, Consumer<SearchResult> consumer, BooleanSupplier cancelled) {
        if (query == null || consumer == null) return;
        List<NotebookInfo> notebooks = new NotebookStore().list();
        AtomicBoolean skipEncrypted = new AtomicBoolean(false);

        Set<String> nativeFoundPaths = new HashSet<>();
        boolean nativeBatchProvidedUnencrypted = false;
        if (!query.q.isEmpty() && NativeAccess.searchBatchReady()) {
            List<NativeAccess.BatchSearchResult> nativeResults = runNativeBatchSearch(query, notebooks);
            if (nativeResults != null && !nativeResults.isEmpty()) {
                nativeBatchProvidedUnencrypted = true;
                for (NativeAccess.BatchSearchResult nr : nativeResults) {
                    if (cancelled != null && cancelled.getAsBoolean()) return;
                    SearchResult res = convertNativeResult(nr, notebooks, query);
                    if (res != null) {
                        nativeFoundPaths.add(res.file.getAbsolutePath());
                        consumer.accept(res);
                    }
                }
            }
        }

        Set<String> spotlightCandidates = new HashSet<>();
        if (!query.q.isEmpty()) {
            List<String> roots = new ArrayList<>();
            for (NotebookInfo nb : notebooks) {
                if (nb.getFolder() != null && nb.getFolder().exists()) {
                    roots.add(nb.getFolder().getAbsolutePath());
                }
            }
            List<String> spotlight = MacNativeBridge.spotlightSearchPaths(
                    roots,
                    query.q,
                    ".note,.txt,.ntk,.poem,.rtf",
                    500,
                    1200
            );
            if (spotlight != null && !spotlight.isEmpty()) {
                spotlightCandidates.addAll(spotlight);
                for (String path : spotlight) {
                    if (cancelled != null && cancelled.getAsBoolean()) return;
                    if (path == null || path.isBlank()) continue;
                    if (nativeFoundPaths.contains(path)) continue;

                    File f = new File(path);
                    if (!f.exists() || !f.isFile()) continue;

                    NotebookInfo nb = findNotebookForFile(notebooks, f);
                    if (nb == null) continue;

                    EntryData data = readEntryData(f, parent, skipEncrypted);
                    if (data == null) continue;
                    if (!query.matches(data)) continue;

                    nativeFoundPaths.add(path);
                    consumer.accept(new SearchResult(nb, f, data));
                    if (nativeFoundPaths.size() >= 500) {
                        return;
                    }
                }
            }
        }

        boolean spotlightNarrowScan = spotlightCandidates.size() >= 200;
        for (NotebookInfo nb : notebooks) {
            if (cancelled != null && cancelled.getAsBoolean()) return;
            File[] files = listEntryFiles(nb);
            if (files == null) continue;
            for (File f : files) {
                if (cancelled != null && cancelled.getAsBoolean()) return;
                if (nativeFoundPaths.contains(f.getAbsolutePath())) continue;
                if (spotlightNarrowScan
                        && !EncryptionManager.isEncrypted(f)
                        && !spotlightCandidates.contains(f.getAbsolutePath())) {
                    continue;
                }
                if (nativeBatchProvidedUnencrypted && !EncryptionManager.isEncrypted(f)) continue;

                EntryData data = readEntryData(f, parent, skipEncrypted);
                if (data == null) continue;
                if (!query.matches(data)) continue;
                consumer.accept(new SearchResult(nb, f, data));
            }
        }
    }

    private static NotebookInfo findNotebookForFile(List<NotebookInfo> notebooks, File file) {
        if (file == null || notebooks == null || notebooks.isEmpty()) return null;
        String path = file.getAbsolutePath();
        for (NotebookInfo nb : notebooks) {
            if (nb == null || nb.getFolder() == null) continue;
            String root = nb.getFolder().getAbsolutePath();
            if (path.startsWith(root)) return nb;
        }
        return null;
    }

    private static List<NativeAccess.BatchSearchResult> runNativeBatchSearch(SearchQuery query, List<NotebookInfo> notebooks) {
        List<String> dirs = new ArrayList<>();
        for (NotebookInfo nb : notebooks) {
            if (nb.getFolder() != null && nb.getFolder().exists()) {
                dirs.add(nb.getFolder().getAbsolutePath());
            }
        }
        if (dirs.isEmpty()) return null;

        return NativeAccess.searchBatch(query.q, dirs, ".note,.txt,.ntk,.poem,.rtf", 500);
    }

    private static SearchResult convertNativeResult(NativeAccess.BatchSearchResult nr, List<NotebookInfo> notebooks, SearchQuery query) {
        if (nr == null || nr.filePath == null) return null;

        File file = new File(nr.filePath);
        if (!file.exists()) return null;

        NotebookInfo matchedNb = null;
        String filePath = file.getAbsolutePath();
        for (NotebookInfo nb : notebooks) {
            if (nb.getFolder() != null && filePath.startsWith(nb.getFolder().getAbsolutePath())) {
                matchedNb = nb;
                break;
            }
        }
        if (matchedNb == null) return null;

        EntryData data = new EntryData();
        data.title = nr.title;
        data.text = nr.snippet;
        data.savedAt = nr.savedAt > 0 ? nr.savedAt : file.lastModified();
        data.mood = nr.mood;
        data.tags = new HashSet<>(nr.tags);
        data.queryForSnippet = query.q;

        LocalDate date = Instant.ofEpochMilli(data.savedAt).atZone(ZoneId.systemDefault()).toLocalDate();
        if (query.from != null && date.isBefore(query.from)) return null;
        if (query.to != null && date.isAfter(query.to)) return null;
        if (!query.tags.isEmpty() && !data.tags.containsAll(query.tags)) return null;
        if (query.moodMin > 0 || query.moodMax < 100) {
            if (data.mood < 0) return null;
            if (data.mood < query.moodMin || data.mood > query.moodMax) return null;
        }

        return new SearchResult(matchedNb, file, data, nr.snippet);
    }

    private static File[] listEntryFiles(NotebookInfo nb) {
        if (nb == null || nb.getFolder() == null || !nb.getFolder().exists()) return null;
        File folder = nb.getFolder();
        try { AppDirectories.restoreMacScopedAccess(AppDirectories.getRoot()); } catch (Throwable ignored) {}
        try { MacSecurityBookmarkStore.ensureAccess(folder); } catch (Throwable ignored) {}
        String nativeResult = NativeAccess.fsListFiltered(
                folder.getAbsolutePath(),
                ".entry,.note,.txt,.md,.ntk,.poem,.rtf,.jrnl",
                false);
        if (nativeResult != null && !nativeResult.isEmpty()) {
            List<File> files = new ArrayList<>();
            for (String line : nativeResult.split("\n")) {
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\|", 4);
                if (parts.length >= 4 && "f".equals(parts[0])) {
                    files.add(new File(folder, parts[3]));
                }
            }
            return files.toArray(new File[0]);
        }
        return folder.listFiles(f -> {
            if (f == null || !f.isFile()) return false;
            String name = f.getName().toLowerCase(Locale.ROOT);
            return name.endsWith(".entry") || name.endsWith(".note") || name.endsWith(".txt")
                    || name.endsWith(".md") || name.endsWith(".ntk") || name.endsWith(".poem")
                    || name.endsWith(".rtf") || name.endsWith(".jrnl");
        });
    }

    private static EntryData readEntryData(File f, Component parent, AtomicBoolean skipEncrypted) {
        if (f == null) return null;
        String name = f.getName().toLowerCase(Locale.ROOT);
        try {
            byte[] raw;
            if (EncryptionManager.isEncrypted(f)) {
                if (skipEncrypted != null && skipEncrypted.get()) return null;
                try {
                    raw = EncryptionManager.readFileMaybeDecrypt(f, parent, true);
                } catch (CryptoException ex) {
                    if (skipEncrypted != null) skipEncrypted.set(true);
                    return null;
                }
            } else {
                raw = FileIO.readAllBytes(f.toPath());
            }
            if (raw == null) return null;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(raw), StandardCharsets.UTF_8))) {
                String first = br.readLine();
                if (first == null) return null;
                EntryData data = new EntryData();
                data.savedAt = f.lastModified();
                if (name.endsWith(".poem")) {
                    data.title = first.trim();
                    br.readLine();
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line).append('\n');
                    }
                    data.text = sb.toString();
                    data.tags = extractTags(data.text);
                    data.mood = -1;
                    return data;
                }

                EntryFileFormat.EntryMeta meta = EntryFileFormat.parseHeader(first);
                String title = "";
                String firstContentLine;
                if (meta != null) {
                    title = meta.title == null ? "" : meta.title;
                    data.mood = meta.mood;
                    if (meta.savedAt > 0) data.savedAt = meta.savedAt;
                    firstContentLine = br.readLine();
                    if (firstContentLine != null && firstContentLine.isBlank()) {
                        firstContentLine = br.readLine();
                    }
                } else {
                    title = first.trim();
                    br.readLine();
                    firstContentLine = br.readLine();
                }
                data.title = title;

                StringBuilder sb = new StringBuilder();
                if (firstContentLine != null) sb.append(firstContentLine).append('\n');
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                String body = stripImageManifest(sb.toString());
                data.text = rtfToPlain(body);
                data.tags = extractTags(data.text);
                return data;
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String stripImageManifest(String body) {
        if (body == null) return "";
        String trimmed = body.stripLeading();
        if (trimmed.startsWith("IMGMAP:")) {
            int nl = trimmed.indexOf('\n');
            if (nl >= 0) return trimmed.substring(nl + 1).stripLeading();
            return "";
        }
        return body;
    }

    private static String rtfToPlain(String text) {
        if (text == null) return "";
        String trimmed = text.stripLeading();
        if (!trimmed.startsWith("{\\rtf")) return text;
        try {
            RTFEditorKit kit = new RTFEditorKit();
            StyledDocument doc = (StyledDocument) kit.createDefaultDocument();
            kit.read(new ByteArrayInputStream(trimmed.getBytes(StandardCharsets.UTF_8)), doc, 0);
            return doc.getText(0, doc.getLength());
        } catch (Exception e) {
            return text;
        }
    }

    public static final class SearchResult {
        public final NotebookInfo notebook;
        public final File file;
        public final String title;
        public final String snippet;
        public final long savedAt;
        public final int mood;
        public final Set<String> tags;

        SearchResult(NotebookInfo nb, File file, EntryData data) {
            this.notebook = nb;
            this.file = file;
            this.title = data.title == null || data.title.isBlank() ? file.getName() : data.title;
            this.snippet = buildSnippet(data.text, data.queryForSnippet);
            this.savedAt = data.savedAt;
            this.mood = data.mood;
            this.tags = data.tags;
        }

        SearchResult(NotebookInfo nb, File file, EntryData data, String nativeSnippet) {
            this.notebook = nb;
            this.file = file;
            this.title = data.title == null || data.title.isBlank() ? file.getName() : data.title;
            this.snippet = nativeSnippet != null && !nativeSnippet.isEmpty() ? nativeSnippet : buildSnippet(data.text, data.queryForSnippet);
            this.savedAt = data.savedAt;
            this.mood = data.mood;
            this.tags = data.tags;
        }
    }

    public static final class EntryData {
        String title;
        String text;
        long savedAt;
        int mood = -1;
        Set<String> tags = new HashSet<>();
        String queryForSnippet;
    }

    public static final class SearchQuery {
        public final String q;
        public final String qLower;
        public final String[] tokens;
        public final String qCompact;
        public final Set<String> tags;
        public final LocalDate from;
        public final LocalDate to;
        public final int moodMin;
        public final int moodMax;
        public final boolean fuzzy;

        public SearchQuery(String q, String tagText, LocalDate from, LocalDate to, int moodMin, int moodMax, boolean fuzzy) {
            this.q = q == null ? "" : q.trim();
            this.qLower = this.q.toLowerCase(Locale.ROOT);
            this.tokens = this.qLower.isEmpty() ? new String[0] : this.qLower.split("\\s+");
            this.qCompact = stripSpaces(collapseSpaces(this.qLower));
            this.tags = parseTagsFilter(tagText);
            this.from = from;
            this.to = to;
            this.moodMin = moodMin;
            this.moodMax = moodMax;
            this.fuzzy = fuzzy;
        }

        public boolean isEmpty() {
            boolean moodFiltered = moodMin > 0 || moodMax < 100;
            return q.isEmpty() && tags.isEmpty() && from == null && to == null && !moodFiltered;
        }

        public boolean matches(EntryData data) {
            if (data == null) return false;
            LocalDate date = Instant.ofEpochMilli(data.savedAt).atZone(ZoneId.systemDefault()).toLocalDate();
            if (from != null && date.isBefore(from)) return false;
            if (to != null && date.isAfter(to)) return false;
            if (!tags.isEmpty()) {
                if (data.tags == null || data.tags.isEmpty()) {
                    data.tags = extractTags(data.text);
                }
                if (!data.tags.containsAll(tags)) return false;
            }
            if (moodMin > 0 || moodMax < 100) {
                if (data.mood < 0) return false;
                if (data.mood < moodMin || data.mood > moodMax) return false;
            }
            data.queryForSnippet = q;
            if (q.isEmpty()) return true;

            String combinedText = NativeAccess.textNormalize(
                (data.title == null ? "" : data.title) + " " + (data.text == null ? "" : data.text)
            );

            if (tokens.length > 0 && NativeAccess.stringContainsCi(combinedText, q)) {
                return true;
            }

            if (fuzzy) {
                if (NativeAccess.textFuzzyMatch(combinedText, q)) {
                    return true;
                }

                int fuzzyScore = NativeAccess.textFuzzyScore(combinedText, q);
                if (fuzzyScore >= q.length() * 0.7) {
                    return true;
                }
            }

            return false;
        }

        private static Set<String> parseTagsFilter(String raw) {
            Set<String> out = new HashSet<>();
            if (raw == null || raw.trim().isEmpty()) return out;
            String[] parts = raw.split("[,\\s]+");
            for (String p : parts) {
                String t = p.trim().toLowerCase(Locale.ROOT);
                if (!t.isEmpty()) out.add(t);
            }
            return out;
        }
    }

    private static String buildSnippet(String text, String query) {
        if (text == null) return "";
        String flat = collapseSpaces(text);
        if (flat.isEmpty()) return "";
        if (query == null || query.isBlank()) {
            return trimSnippet(flat, 160);
        }
        String q = query.trim();
        int idx = findIndexCi(flat, q);
        if (idx < 0) {
            String[] parts = q.split("\\s+");
            for (String p : parts) {
                if (p.isBlank()) continue;
                idx = findIndexCi(flat, p);
                if (idx >= 0) {
                    q = p;
                    break;
                }
            }
        }
        if (idx < 0) return trimSnippet(flat, 160);
        int start = Math.max(0, idx - 40);
        int end = Math.min(flat.length(), idx + q.length() + 60);
        String snippet = flat.substring(start, end).trim();
        if (start > 0) snippet = "..." + snippet;
        if (end < flat.length()) snippet = snippet + "...";
        return snippet;
    }

    private static String collapseSpaces(String text) {
        if (text == null || text.isEmpty()) return "";
        String nativeCollapsed = NativeAccess.patternCollapseSpaces(text);
        if (nativeCollapsed != null) return nativeCollapsed;
        return text.replaceAll("\\s+", " ").trim();
    }

    private static String stripSpaces(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c)) sb.append(c);
        }
        return sb.toString();
    }

    private static int findIndexCi(String text, String needle) {
        if (text == null || needle == null || needle.isBlank()) return -1;
        long idx = NativeAccess.searchFindCi(text, needle);
        if (idx >= 0 && idx <= Integer.MAX_VALUE) return (int) idx;
        String lower = text.toLowerCase(Locale.ROOT);
        String qLower = needle.toLowerCase(Locale.ROOT);
        return lower.indexOf(qLower);
    }

    private static String trimSnippet(String text, int max) {
        if (text.length() <= max) return text;
        return text.substring(0, max - 3).trim() + "...";
    }

    @SuppressWarnings("unused")
    private static boolean containsAllTokens(String[] tokens, String text) {
        if (tokens == null || tokens.length == 0) return true;
        for (String p : tokens) {
            if (p == null || p.isBlank()) continue;
            if (!NativeAccess.searchContainsCi(text, p)) return false;
        }
        return true;
    }

    @SuppressWarnings("unused")
    private static boolean fuzzyMatch(String query, String text) {
        if (query.isEmpty()) return true;

        int maxDistance = Math.max(1, query.length() / 4);
        if (NativeAccess.searchFuzzyMatch(text, query, maxDistance)) return true;

        int similarity = NativeAccess.textSimilarity(text, query);
        if (similarity >= 70) return true;

        Integer distance = NativeAccess.textLevenshtein(text, query);
        if (distance != null) {
            double ratio = (double) (query.length() - distance) / (double) query.length();
            return ratio >= 0.7;
        }

        return false;
    }

    private static Set<String> extractTags(String text) {
        Set<String> tags = new HashSet<>();
        if (text == null || text.isBlank()) return tags;

        List<String> nativeTags = NativeAccess.textExtractTags(text);
        if (nativeTags != null && !nativeTags.isEmpty()) {
            for (String tag : nativeTags) {
                if (tag != null && !tag.isEmpty()) {
                    tags.add(tag.toLowerCase(Locale.ROOT));
                }
            }
            return tags;
        }

        Matcher m = TAG_PATTERN.matcher(text);
        while (m.find()) {
            tags.add(m.group(1).toLowerCase(Locale.ROOT));
        }
        return tags;
    }
}
