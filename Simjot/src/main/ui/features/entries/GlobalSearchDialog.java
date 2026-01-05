/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.entries;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.text.StyledDocument;
import javax.swing.text.rtf.RTFEditorKit;

import main.core.security.EncryptionManager;
import main.core.security.crypto.CryptoException;
import main.core.service.NotebookStore;
import main.infrastructure.backup.NotebookInfo;
import main.infrastructure.ffi.NativeAccess;
import main.infrastructure.io.FileIO;
import main.ui.app.JournalApp;
import main.ui.components.buttons.IconMenuButton;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.spinner.ModernSpinnerUI;
import main.ui.theme.Theme;
import main.ui.theme.aero.AeroPainters;
import main.ui.theme.aero.AeroTheme;

/**
 * Global search across all notebooks with snippets and filters.
 */
public class GlobalSearchDialog extends JDialog {
    private final JournalApp app;
    private final AeroSearchField queryField;
    private final AeroSearchField tagField;
    private final AeroSearchField fromDateField;
    private final AeroSearchField toDateField;
    private final JSpinner moodMin;
    private final JSpinner moodMax;
    private final JLabel statusLabel;
    private final DefaultListModel<SearchResult> model = new DefaultListModel<>();
    private final JList<SearchResult> list = new JList<>(model);
    private SwingWorker<Void, SearchResult> worker;
    private final Timer debounce;

    public GlobalSearchDialog(Frame owner, JournalApp app) {
        super(owner, "Search", false);
        this.app = app;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setPreferredSize(new Dimension(840, 520));

        FrostedGlassPanel root = new FrostedGlassPanel(new BorderLayout(12, 12), 18);
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        FrostedGlassPanel filters = new FrostedGlassPanel(new BorderLayout(10, 10), 14);
        filters.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        FrostedGlassPanel topRow = new FrostedGlassPanel(new BorderLayout(10, 0), 12);
        topRow.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        queryField = new AeroSearchField(30);
        queryField.setPlaceholder("Search across notebooks...");
        queryField.setPreferredSize(new Dimension(520, 32));
        topRow.add(queryField, BorderLayout.CENTER);
        IconMenuButton searchBtn = new IconMenuButton("Search", "search");
        searchBtn.setToolTipText("Search notebooks");
        searchBtn.addActionListener(e -> runSearch());
        topRow.add(searchBtn, BorderLayout.EAST);

        JPanel secondRow = new JPanel(new GridLayout(2, 1, 6, 6));
        secondRow.setOpaque(false);
        JPanel rowA = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        rowA.setOpaque(false);
        rowA.add(makeLabel("Tags:"));
        tagField = new AeroSearchField(18);
        tagField.setPlaceholder("tag1, tag2");
        rowA.add(tagField);
        rowA.add(makeLabel("Date:"));
        fromDateField = new AeroSearchField(10);
        fromDateField.setPlaceholder("from yyyy-mm-dd");
        toDateField = new AeroSearchField(10);
        toDateField.setPlaceholder("to yyyy-mm-dd");
        rowA.add(fromDateField);
        rowA.add(makeLabel("to"));
        rowA.add(toDateField);

        JPanel rowB = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        rowB.setOpaque(false);
        rowB.add(makeLabel("Mood:"));
        moodMin = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
        moodMax = new JSpinner(new SpinnerNumberModel(100, 0, 100, 1));
        moodMin.setUI(new ModernSpinnerUI());
        moodMax.setUI(new ModernSpinnerUI());
        moodMin.setPreferredSize(new Dimension(64, 26));
        moodMax.setPreferredSize(new Dimension(64, 26));
        rowB.add(moodMin);
        rowB.add(makeLabel("to"));
        rowB.add(moodMax);

        secondRow.add(rowA);
        secondRow.add(rowB);

        filters.add(topRow, BorderLayout.NORTH);
        filters.add(secondRow, BorderLayout.CENTER);
        root.add(filters, BorderLayout.NORTH);

        list.setCellRenderer(new SearchResultRenderer());
        list.setOpaque(false);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        FrostedGlassPanel resultsCard = new FrostedGlassPanel(new BorderLayout(), 16);
        resultsCard.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        resultsCard.add(scroll, BorderLayout.CENTER);
        root.add(resultsCard, BorderLayout.CENTER);

        statusLabel = new JLabel("Type to search.");
        statusLabel.setForeground(new Color(80, 90, 105));
        statusLabel.setFont(AeroTheme.defaultFont());

        debounce = new Timer(250, e -> runSearch());
        debounce.setRepeats(false);
        attachDebounce(queryField);
        attachDebounce(tagField);
        attachDebounce(fromDateField);
        attachDebounce(toDateField);
        moodMin.addChangeListener(e -> debounce.restart());
        moodMax.addChangeListener(e -> debounce.restart());

        list.addListSelectionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx >= 0) list.ensureIndexIsVisible(idx);
        });
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() >= 2) openSelected();
            }
        });

        IconMenuButton openBtn = new IconMenuButton("Open", "analyze");
        openBtn.setToolTipText("Open selected entry");
        openBtn.addActionListener(e -> openSelected());
        IconMenuButton closeBtn = new IconMenuButton("Cancel", "exit");
        closeBtn.setToolTipText("Cancel search");
        closeBtn.addActionListener(e -> dispose());
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 18, 0));
        btnRow.setOpaque(false);
        btnRow.add(openBtn);
        btnRow.add(closeBtn);
        FrostedGlassPanel bottomBar = new FrostedGlassPanel(new BorderLayout(8, 0), 12);
        bottomBar.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        bottomBar.add(statusLabel, BorderLayout.WEST);
        bottomBar.add(btnRow, BorderLayout.EAST);
        root.add(bottomBar, BorderLayout.SOUTH);

        setContentPane(root);
        pack();
        setLocationRelativeTo(owner);
    }

    private void attachDebounce(JTextField field) {
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { debounce.restart(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { debounce.restart(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { debounce.restart(); }
        });
    }

    private void runSearch() {
        if (worker != null && !worker.isDone()) {
            worker.cancel(true);
        }
        model.clear();

        SearchQuery query = buildQuery();
        if (query.isEmpty()) {
            statusLabel.setText("Type to search.");
            return;
        }

        statusLabel.setText("Searching...");
        worker = new SwingWorker<>() {
            @Override protected Void doInBackground() {
                List<NotebookInfo> notebooks = new NotebookStore().list();
                
                // Try native batch search first (much faster for text search)
                if (!query.q.isEmpty() && NativeAccess.searchBatchReady()) {
                    List<NativeAccess.BatchSearchResult> nativeResults = runNativeBatchSearch(query, notebooks);
                    if (nativeResults != null && !nativeResults.isEmpty()) {
                        for (NativeAccess.BatchSearchResult nr : nativeResults) {
                            if (isCancelled()) break;
                            SearchResult res = convertNativeResult(nr, notebooks, query);
                            if (res != null) publish(res);
                        }
                        // If we have results from native, we're done unless there are encrypted files
                        // Fall through to Java search for encrypted files only
                    }
                }
                
                // Fall back to Java search for encrypted files or if native unavailable
                AtomicBoolean skipEncrypted = new AtomicBoolean(false);
                Set<String> nativeFoundPaths = new HashSet<>();
                for (int i = 0; i < model.size(); i++) {
                    nativeFoundPaths.add(model.get(i).file.getAbsolutePath());
                }
                
                for (NotebookInfo nb : notebooks) {
                    if (isCancelled()) break;
                    File[] files = listEntryFiles(nb);
                    if (files == null) continue;
                    for (File f : files) {
                        if (isCancelled()) break;
                        // Skip files already found by native search
                        if (nativeFoundPaths.contains(f.getAbsolutePath())) continue;
                        // Only process encrypted files in Java fallback if native was used
                        if (!nativeFoundPaths.isEmpty() && !EncryptionManager.isEncrypted(f)) continue;
                        
                        EntryData data = readEntryData(f, GlobalSearchDialog.this, skipEncrypted);
                        if (data == null) continue;
                        if (!query.matches(data)) continue;
                        SearchResult res = new SearchResult(nb, f, data);
                        publish(res);
                    }
                }
                return null;
            }

            @Override protected void process(List<SearchResult> chunks) {
                for (SearchResult r : chunks) {
                    model.addElement(r);
                }
                statusLabel.setText(model.size() + " result(s)");
            }

            @Override protected void done() {
                if (model.isEmpty()) {
                    statusLabel.setText("No results.");
                }
            }
        };
        worker.execute();
    }

    private List<NativeAccess.BatchSearchResult> runNativeBatchSearch(SearchQuery query, List<NotebookInfo> notebooks) {
        List<String> dirs = new ArrayList<>();
        for (NotebookInfo nb : notebooks) {
            if (nb.getFolder() != null && nb.getFolder().exists()) {
                dirs.add(nb.getFolder().getAbsolutePath());
            }
        }
        if (dirs.isEmpty()) return null;
        
        return NativeAccess.searchBatch(query.q, dirs, ".note,.txt,.ntk,.poem,.rtf", 500);
    }

    private SearchResult convertNativeResult(NativeAccess.BatchSearchResult nr, List<NotebookInfo> notebooks, SearchQuery query) {
        if (nr == null || nr.filePath == null) return null;
        
        File file = new File(nr.filePath);
        if (!file.exists()) return null;
        
        // Find the matching notebook
        NotebookInfo matchedNb = null;
        String filePath = file.getAbsolutePath();
        for (NotebookInfo nb : notebooks) {
            if (nb.getFolder() != null && filePath.startsWith(nb.getFolder().getAbsolutePath())) {
                matchedNb = nb;
                break;
            }
        }
        if (matchedNb == null) return null;
        
        // Convert to EntryData for additional filtering
        EntryData data = new EntryData();
        data.title = nr.title;
        data.text = nr.snippet; // Use snippet for matching; full text not available
        data.savedAt = nr.savedAt > 0 ? nr.savedAt : file.lastModified();
        data.mood = nr.mood;
        data.tags = new HashSet<>(nr.tags);
        data.queryForSnippet = query.q;
        
        // Apply additional filters (date, mood, tags)
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

    private void openSelected() {
        SearchResult selected = list.getSelectedValue();
        if (selected == null) return;
        if (app != null) {
            app.openExistingEntryEditor(selected.notebook, selected.file);
            dispose();
        }
    }

    private SearchQuery buildQuery() {
        String q = queryField.getText() == null ? "" : queryField.getText().trim();
        String tagText = tagField.getText() == null ? "" : tagField.getText().trim();
        LocalDate fromDate = parseDate(fromDateField.getText());
        LocalDate toDate = parseDate(toDateField.getText());
        int minMood = (Integer) moodMin.getValue();
        int maxMood = (Integer) moodMax.getValue();
        return new SearchQuery(q, tagText, fromDate, toDate, minMood, maxMood, false);
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(raw.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            return null;
        }
    }

    private static File[] listEntryFiles(NotebookInfo nb) {
        if (nb == null || nb.getFolder() == null || !nb.getFolder().exists()) return null;
        File folder = nb.getFolder();
        String nativeResult = NativeAccess.fsListFiltered(
                folder.getAbsolutePath(),
                ".note,.txt,.ntk,.poem,.rtf",
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
            return name.endsWith(".note") || name.endsWith(".txt")
                    || name.endsWith(".ntk") || name.endsWith(".poem") || name.endsWith(".rtf");
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

    private static class SearchResult {
        final NotebookInfo notebook;
        final File file;
        final String title;
        final String snippet;
        final long savedAt;
        final int mood;
        final Set<String> tags;

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

    private static class EntryData {
        String title;
        String text;
        long savedAt;
        int mood = -1;
        Set<String> tags = new HashSet<>();
        String queryForSnippet;
    }

    private static class SearchQuery {
        final String q;
        final String qLower;
        final String[] tokens;
        final String qCompact;
        final Set<String> tags;
        final LocalDate from;
        final LocalDate to;
        final int moodMin;
        final int moodMax;
        final boolean fuzzy;

        SearchQuery(String q, String tagText, LocalDate from, LocalDate to, int moodMin, int moodMax, boolean fuzzy) {
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

        boolean isEmpty() {
            return q.isEmpty() && tags.isEmpty() && from == null && to == null;
        }

        boolean matches(EntryData data) {
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
            String text = (data.title == null ? "" : data.title) + " " + (data.text == null ? "" : data.text);
            if (containsAllTokens(tokens, text)) return true;
            if (!fuzzy) return false;
            String compact = stripSpaces(collapseSpaces(text.toLowerCase(Locale.ROOT)));
            return fuzzyMatch(qCompact, compact);
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

    private static class SearchResultRenderer extends JPanel implements ListCellRenderer<SearchResult> {
        private final JLabel titleLine = new JLabel();
        private final JLabel meta = new JLabel();
        private boolean selected;

        SearchResultRenderer() {
            setLayout(new BorderLayout(4, 2));
            setOpaque(false);
            titleLine.setFont(AeroTheme.defaultBoldFont(14f));
            meta.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 11f));
            meta.setForeground(new Color(95, 105, 120));
            add(titleLine, BorderLayout.CENTER);
            add(meta, BorderLayout.SOUTH);
            setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends SearchResult> list, SearchResult value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            if (value != null) {
                // Format: "Entry Title" from 📓 Notebook Name
                String entryTitle = value.title != null && !value.title.isEmpty() ? value.title : "Untitled";
                String nbName = value.notebook.getName();
                titleLine.setText("<html><b>" + escapeHtml(entryTitle) + "</b> <span style='color:#666;'>from</span> <span style='color:#4a7c9b;'>📓 " + escapeHtml(nbName) + "</span></html>");
                
                String date = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                        .format(Instant.ofEpochMilli(value.savedAt).atZone(ZoneId.systemDefault()).toLocalDate());
                String mood = value.mood >= 0 ? "Mood " + value.mood : "";
                String tagText = (value.tags != null && !value.tags.isEmpty())
                        ? "Tags: " + String.join(", ", value.tags)
                        : "";
                StringBuilder sb = new StringBuilder(date);
                if (!mood.isEmpty()) sb.append("  •  ").append(mood);
                if (!tagText.isEmpty()) sb.append("  •  ").append(tagText);
                meta.setText(sb.toString());
            }
            this.selected = isSelected;
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int arc = 12;
            java.awt.Rectangle r = new java.awt.Rectangle(0, 0, w, h);
            Color top = selected ? new Color(235, 245, 255) : new Color(252, 253, 255);
            Color bottom = selected ? new Color(217, 232, 248) : new Color(232, 239, 246);
            AeroPainters.paintVerticalGradient(g2, r, top, bottom, arc);
            AeroPainters.paintGlassOverlay(g2, r, arc);
            if (selected) {
                AeroPainters.paintOuterGlow(g2, r, arc, new Color(120, 170, 255), 4, 90);
            }
            g2.setColor(selected ? new Color(120, 170, 255, 140) : new Color(185, 195, 210));
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
        
        private static String escapeHtml(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
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

    private static boolean containsAllTokens(String[] tokens, String text) {
        if (tokens == null || tokens.length == 0) return true;
        for (String p : tokens) {
            if (p == null || p.isBlank()) continue;
            if (!NativeAccess.searchContainsCi(text, p)) return false;
        }
        return true;
    }

    private static boolean fuzzyMatch(String query, String text) {
        if (query.isEmpty()) return true;
        if (NativeAccess.searchFuzzyMatch(text, query, Math.max(1, query.length() / 4))) return true;
        int qi = 0;
        for (int i = 0; i < text.length() && qi < query.length(); i++) {
            if (text.charAt(i) == query.charAt(qi)) {
                qi++;
            }
        }
        double ratio = (double) qi / (double) query.length();
        return ratio >= 0.7;
    }

    private static Set<String> extractTags(String text) {
        Set<String> tags = new HashSet<>();
        if (text == null || text.isBlank()) return tags;
        List<String> nativeTags = NativeAccess.textExtractTags(text);
        if (nativeTags != null) {
            for (String tag : nativeTags) {
                if (tag != null && !tag.isEmpty()) tags.add(tag);
            }
            return tags;
        }
        Matcher m = Pattern.compile("#([A-Za-z0-9_-]+)").matcher(text);
        while (m.find()) {
            tags.add(m.group(1).toLowerCase(Locale.ROOT));
        }
        return tags;
    }

    private static JLabel makeLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(AeroTheme.defaultBoldFont(12f));
        label.setForeground(new Color(55, 60, 70));
        return label;
    }

    private static final class AeroSearchField extends JTextField {
        private String placeholder;

        AeroSearchField(int columns) {
            super(columns);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            setFont(AeroTheme.defaultFont());
            setForeground(AeroTheme.TEXT_PRIMARY);
            setCaretColor(AeroTheme.AERO_BLUE_DARK);
        }

        void setPlaceholder(String placeholder) {
            this.placeholder = placeholder;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            if (Theme.isPlainWhite()) {
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, w, h, 10, 10);
            } else {
                GradientPaint gp = new GradientPaint(0, 0, new Color(252, 252, 252), 0, h, new Color(233, 236, 242));
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, w, h, 10, 10);
                g2.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 170), 0, h / 2f, new Color(255, 255, 255, 0)));
                g2.fillRoundRect(1, 1, w - 2, h / 2, 9, 9);
            }
            super.paintComponent(g2);

            if ((getText() == null || getText().isEmpty()) && !isFocusOwner() && placeholder != null) {
                g2.setFont(getFont());
                g2.setColor(new Color(130, 130, 130));
                FontMetrics fm = g2.getFontMetrics();
                int textY = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(placeholder, 12, textY);
            }
            g2.dispose();
        }

        @Override
        protected void paintBorder(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(new Color(190, 190, 190));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);
            if (!Theme.isPlainWhite() && isFocusOwner()) {
                g2.setColor(new Color(AeroTheme.AERO_BLUE.getRed(), AeroTheme.AERO_BLUE.getGreen(), AeroTheme.AERO_BLUE.getBlue(), 120));
                g2.drawRoundRect(1, 1, w - 3, h - 3, 8, 8);
            }
            g2.dispose();
        }
    }
}
