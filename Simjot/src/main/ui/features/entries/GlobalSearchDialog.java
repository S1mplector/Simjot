package main.ui.features.entries;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
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

import main.core.service.NotebookStore;
import main.infrastructure.backup.NotebookInfo;
import main.ui.app.JournalApp;
import main.ui.components.buttons.IconMenuButton;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.fields.ModernTextField;

/**
 * Global search across all notebooks with snippets and filters.
 */
public class GlobalSearchDialog extends JDialog {
    private final JournalApp app;
    private final ModernTextField queryField;
    private final ModernTextField tagField;
    private final ModernTextField fromDateField;
    private final ModernTextField toDateField;
    private final JSpinner moodMin;
    private final JSpinner moodMax;
    private final JCheckBox fuzzyCheck;
    private final JLabel statusLabel;
    private final DefaultListModel<SearchResult> model = new DefaultListModel<>();
    private final JList<SearchResult> list = new JList<>(model);
    private SwingWorker<Void, SearchResult> worker;
    private final Timer debounce;

    public GlobalSearchDialog(Frame owner, JournalApp app) {
        super(owner, "Search", false);
        this.app = app;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setPreferredSize(new Dimension(820, 520));

        FrostedGlassPanel root = new FrostedGlassPanel(new BorderLayout(12, 12), 16);
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel filters = new JPanel(new BorderLayout(10, 10));
        filters.setOpaque(false);
        JPanel topRow = new JPanel(new BorderLayout(8, 8));
        topRow.setOpaque(false);
        queryField = new ModernTextField(30);
        queryField.setPlaceholder("Search across notebooks...");
        topRow.add(queryField, BorderLayout.CENTER);
        IconMenuButton searchBtn = new IconMenuButton("Search", "explorer");
        searchBtn.setToolTipText("Search notebooks");
        searchBtn.addActionListener(e -> runSearch());
        topRow.add(searchBtn, BorderLayout.EAST);

        JPanel secondRow = new JPanel(new GridLayout(2, 1, 6, 6));
        secondRow.setOpaque(false);
        JPanel rowA = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        rowA.setOpaque(false);
        rowA.add(new JLabel("Tags:"));
        tagField = new ModernTextField(18);
        tagField.setPlaceholder("tag1, tag2");
        rowA.add(tagField);
        rowA.add(new JLabel("Date:"));
        fromDateField = new ModernTextField(10);
        fromDateField.setPlaceholder("from yyyy-mm-dd");
        toDateField = new ModernTextField(10);
        toDateField.setPlaceholder("to yyyy-mm-dd");
        rowA.add(fromDateField);
        rowA.add(new JLabel("to"));
        rowA.add(toDateField);

        JPanel rowB = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        rowB.setOpaque(false);
        rowB.add(new JLabel("Mood:"));
        moodMin = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
        moodMax = new JSpinner(new SpinnerNumberModel(100, 0, 100, 1));
        moodMin.setPreferredSize(new Dimension(60, 26));
        moodMax.setPreferredSize(new Dimension(60, 26));
        rowB.add(moodMin);
        rowB.add(new JLabel("to"));
        rowB.add(moodMax);
        fuzzyCheck = new JCheckBox("Fuzzy");
        rowB.add(fuzzyCheck);

        secondRow.add(rowA);
        secondRow.add(rowB);

        filters.add(topRow, BorderLayout.NORTH);
        filters.add(secondRow, BorderLayout.CENTER);
        root.add(filters, BorderLayout.NORTH);

        list.setCellRenderer(new SearchResultRenderer());
        JScrollPane scroll = new JScrollPane(list);
        root.add(scroll, BorderLayout.CENTER);

        statusLabel = new JLabel("Type to search.");
        statusLabel.setForeground(new Color(90, 90, 90));

        debounce = new Timer(250, e -> runSearch());
        debounce.setRepeats(false);
        attachDebounce(queryField);
        attachDebounce(tagField);
        attachDebounce(fromDateField);
        attachDebounce(toDateField);
        moodMin.addChangeListener(e -> debounce.restart());
        moodMax.addChangeListener(e -> debounce.restart());
        fuzzyCheck.addActionListener(e -> debounce.restart());

        list.addListSelectionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx >= 0) list.ensureIndexIsVisible(idx);
        });
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() >= 2) openSelected();
            }
        });

        IconMenuButton openBtn = new IconMenuButton("Open", "load");
        openBtn.setToolTipText("Open selected entry");
        openBtn.addActionListener(e -> openSelected());
        IconMenuButton closeBtn = new IconMenuButton("Close", "close");
        closeBtn.setToolTipText("Close search");
        closeBtn.addActionListener(e -> dispose());
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 18, 0));
        btnRow.setOpaque(false);
        btnRow.add(openBtn);
        btnRow.add(closeBtn);
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setOpaque(false);
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
                for (NotebookInfo nb : notebooks) {
                    if (isCancelled()) break;
                    File[] files = listEntryFiles(nb);
                    if (files == null) continue;
                    for (File f : files) {
                        if (isCancelled()) break;
                        EntryData data = readEntryData(f);
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
        boolean fuzzy = fuzzyCheck.isSelected();
        return new SearchQuery(q, tagText, fromDate, toDate, minMood, maxMood, fuzzy);
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
        return nb.getFolder().listFiles(f -> {
            if (f == null || !f.isFile()) return false;
            String name = f.getName().toLowerCase(Locale.ROOT);
            return name.endsWith(".note") || name.endsWith(".txt")
                    || name.endsWith(".ntk") || name.endsWith(".poem") || name.endsWith(".rtf");
        });
    }

    private static EntryData readEntryData(File f) {
        if (f == null) return null;
        String name = f.getName().toLowerCase(Locale.ROOT);
        try (BufferedReader br = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
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
            this.qCompact = this.qLower.replaceAll("\\s+", "");
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
            data.queryForSnippet = qLower;
            if (q.isEmpty()) return true;
            String text = (data.title == null ? "" : data.title) + " " + (data.text == null ? "" : data.text);
            String lower = text.toLowerCase(Locale.ROOT);
            if (containsAllTokens(qLower, lower)) return true;
            if (!fuzzy) return false;
            String compact = lower.replaceAll("\\s+", "");
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

        SearchResultRenderer() {
            setLayout(new BorderLayout(4, 2));
            titleLine.setFont(titleLine.getFont().deriveFont(Font.PLAIN, 14f));
            meta.setFont(meta.getFont().deriveFont(Font.PLAIN, 11f));
            meta.setForeground(new Color(110, 110, 110));
            add(titleLine, BorderLayout.CENTER);
            add(meta, BorderLayout.SOUTH);
            setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
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
            if (isSelected) {
                setBackground(new Color(225, 235, 250));
                setOpaque(true);
            } else {
                setBackground(Color.WHITE);
                setOpaque(true);
            }
            return this;
        }
        
        private static String escapeHtml(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }

    private static String buildSnippet(String text, String query) {
        if (text == null) return "";
        String flat = text.replaceAll("\\s+", " ").trim();
        if (flat.isEmpty()) return "";
        if (query == null || query.isBlank()) {
            return trimSnippet(flat, 160);
        }
        String lower = flat.toLowerCase(Locale.ROOT);
        String q = query.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf(q);
        if (idx < 0) {
            String[] parts = q.split("\\s+");
            for (String p : parts) {
                if (p.isBlank()) continue;
                idx = lower.indexOf(p);
                if (idx >= 0) break;
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

    private static String trimSnippet(String text, int max) {
        if (text.length() <= max) return text;
        return text.substring(0, max - 3).trim() + "...";
    }

    private static boolean containsAllTokens(String query, String text) {
        String[] parts = query.split("\\s+");
        for (String p : parts) {
            if (p.isBlank()) continue;
            if (!text.contains(p)) return false;
        }
        return true;
    }

    private static boolean fuzzyMatch(String query, String text) {
        if (query.isEmpty()) return true;
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
        Matcher m = Pattern.compile("#([A-Za-z0-9_-]+)").matcher(text);
        while (m.find()) {
            tags.add(m.group(1).toLowerCase(Locale.ROOT));
        }
        return tags;
    }
}
