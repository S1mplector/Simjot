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
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import main.ui.app.JournalApp;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.containers.RoundedPanel;
import main.ui.components.fields.ModernTextField;
import main.ui.components.spinner.ModernSpinnerUI;
import main.core.service.SettingsStore;
import main.ui.features.entries.BackgroundPainter;
import main.ui.theme.aero.AeroTheme;

/**
 * Global search across all notebooks with snippets and filters.
 */
public class GlobalSearchDialog extends JDialog {
    private final JournalApp app;
    private final BackgroundPainter backgroundPainter = new BackgroundPainter();
    private final ModernTextField queryField;
    private final ModernTextField tagField;
    private final ModernTextField fromDateField;
    private final ModernTextField toDateField;
    private final JSpinner moodMin;
    private final JSpinner moodMax;
    private final JLabel statusLabel;
    private final DefaultListModel<GlobalSearchEngine.SearchResult> model = new DefaultListModel<>();
    private final JList<GlobalSearchEngine.SearchResult> list = new JList<>(model);
    private SwingWorker<Void, GlobalSearchEngine.SearchResult> worker;
    private final Timer debounce;

    public GlobalSearchDialog(Frame owner, JournalApp app) {
        super(owner, "Search", false);
        this.app = app;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setPreferredSize(new Dimension(880, 560));

        JPanel backgroundPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                String bgPath = SettingsStore.get().getEntryBackgroundImage();
                float opacity = SettingsStore.get().getEntryBackgroundOpacity();
                backgroundPainter.paint(g, this, bgPath, opacity, true);
            }
        };
        backgroundPanel.setOpaque(false);

        FrostedGlassPanel root = new FrostedGlassPanel(new BorderLayout(12, 12), 16) {
            @Override
            protected float getOpacityScale() {
                return SettingsStore.get().getEditorGlassOpacity();
            }
        };
        root.setBorder(new EmptyBorder(16, 16, 16, 16));
        backgroundPanel.add(root, BorderLayout.CENTER);

        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        JLabel title = new JLabel("Search");
        title.setFont(AeroTheme.defaultBoldFont(18f));
        title.setForeground(new Color(30, 36, 46));
        statusLabel = new JLabel("Type to search.");
        statusLabel.setForeground(new Color(120, 130, 145));
        statusLabel.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 12f));
        header.add(title, BorderLayout.WEST);
        header.add(statusLabel, BorderLayout.EAST);

        RoundedPanel searchBar = new RoundedPanel(12);
        searchBar.setFlat(true);
        searchBar.setBackground(Color.WHITE);
        searchBar.setLayout(new BorderLayout(10, 0));
        searchBar.setBorder(new EmptyBorder(6, 10, 6, 10));

        queryField = new ModernTextField(30);
        queryField.setPlaceholder("Search across notebooks");
        queryField.setFont(AeroTheme.defaultFont().deriveFont(15f));
        queryField.setPreferredSize(new Dimension(520, 32));

        RoundedButton searchBtn = new RoundedButton("Search").withIcon("search");
        searchBtn.setPreferredSize(new Dimension(110, 32));
        searchBtn.setToolTipText("Run search now");
        searchBtn.addActionListener(e -> runSearch());

        RoundedButton clearBtn = new RoundedButton("Clear").withIcon("close");
        clearBtn.setPreferredSize(new Dimension(96, 32));
        clearBtn.setToolTipText("Clear all filters");
        clearBtn.addActionListener(e -> clearFilters());

        JPanel searchActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        searchActions.setOpaque(false);
        searchActions.add(clearBtn);
        searchActions.add(searchBtn);

        searchBar.add(queryField, BorderLayout.CENTER);
        searchBar.add(searchActions, BorderLayout.EAST);

        RoundedPanel filterCard = new RoundedPanel(12);
        filterCard.setFlat(true);
        filterCard.setBackground(new Color(250, 250, 252));
        filterCard.setBorder(new EmptyBorder(6, 10, 6, 10));
        filterCard.setLayout(new GridLayout(2, 1, 0, 6));

        JPanel rowA = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        rowA.setOpaque(false);
        rowA.add(makeLabel("Tags"));
        tagField = new ModernTextField(18);
        tagField.setPlaceholder("tag1, tag2");
        rowA.add(tagField);
        rowA.add(makeLabel("From"));
        fromDateField = new ModernTextField(10);
        fromDateField.setPlaceholder("YYYY-MM-DD");
        rowA.add(fromDateField);
        rowA.add(makeLabel("To"));
        toDateField = new ModernTextField(10);
        toDateField.setPlaceholder("YYYY-MM-DD");
        rowA.add(toDateField);

        JPanel rowB = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        rowB.setOpaque(false);
        rowB.add(makeLabel("Mood"));
        moodMin = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
        moodMax = new JSpinner(new SpinnerNumberModel(100, 0, 100, 1));
        moodMin.setUI(new ModernSpinnerUI());
        moodMax.setUI(new ModernSpinnerUI());
        moodMin.setPreferredSize(new Dimension(64, 26));
        moodMax.setPreferredSize(new Dimension(64, 26));
        rowB.add(moodMin);
        rowB.add(makeLabel("to"));
        rowB.add(moodMax);

        filterCard.add(rowA);
        filterCard.add(rowB);

        JPanel topStack = new JPanel();
        topStack.setOpaque(false);
        topStack.setLayout(new BoxLayout(topStack, BoxLayout.Y_AXIS));
        topStack.add(header);
        topStack.add(javax.swing.Box.createVerticalStrut(10));
        topStack.add(searchBar);
        topStack.add(javax.swing.Box.createVerticalStrut(8));
        topStack.add(filterCard);
        root.add(topStack, BorderLayout.NORTH);

        list.setCellRenderer(new SearchResultRenderer());
        list.setOpaque(false);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        RoundedPanel resultsCard = new RoundedPanel(16);
        resultsCard.setFlat(true);
        resultsCard.setBackground(Color.WHITE);
        resultsCard.setBorder(new EmptyBorder(10, 10, 10, 10));
        resultsCard.setLayout(new BorderLayout());
        resultsCard.add(scroll, BorderLayout.CENTER);
        root.add(resultsCard, BorderLayout.CENTER);

        RoundedButton openBtn = new RoundedButton("Open").withIcon("analyze");
        openBtn.setToolTipText("Open selected entry");
        openBtn.setPreferredSize(new Dimension(110, 32));
        openBtn.setEnabled(false);
        openBtn.addActionListener(e -> openSelected());
        RoundedButton closeBtn = new RoundedButton("Close").withIcon("exit");
        closeBtn.setToolTipText("Close search");
        closeBtn.setPreferredSize(new Dimension(110, 32));
        closeBtn.addActionListener(e -> dispose());
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnRow.setOpaque(false);
        btnRow.add(openBtn);
        btnRow.add(closeBtn);

        JLabel hint = new JLabel("Enter to open • Esc to close");
        hint.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 11f));
        hint.setForeground(new Color(130, 140, 155));
        JPanel bottomBar = new JPanel(new BorderLayout(8, 0));
        bottomBar.setOpaque(false);
        bottomBar.setBorder(new EmptyBorder(2, 2, 2, 2));
        bottomBar.add(hint, BorderLayout.WEST);
        bottomBar.add(btnRow, BorderLayout.EAST);
        root.add(bottomBar, BorderLayout.SOUTH);

        debounce = new Timer(200, e -> runSearch());
        debounce.setRepeats(false);
        attachDebounce(queryField);
        attachDebounce(tagField);
        attachDebounce(fromDateField);
        attachDebounce(toDateField);
        moodMin.addChangeListener(e -> debounce.restart());
        moodMax.addChangeListener(e -> debounce.restart());

        queryField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    runSearch();
                }
            }
        });

        list.addListSelectionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx >= 0) list.ensureIndexIsVisible(idx);
            openBtn.setEnabled(idx >= 0);
        });
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() >= 2) openSelected();
            }
        });

        list.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "open");
        list.getActionMap().put("open", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { openSelected(); }
        });

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeOrClear");
        getRootPane().getActionMap().put("closeOrClear", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                boolean hasFilters =
                    !queryField.getText().isBlank()
                    || !tagField.getText().isBlank()
                    || !fromDateField.getText().isBlank()
                    || !toDateField.getText().isBlank()
                    || ((Integer) moodMin.getValue()) > 0
                    || ((Integer) moodMax.getValue()) < 100;
                if (hasFilters) {
                    clearFilters();
                } else {
                    dispose();
                }
            }
        });

        setContentPane(backgroundPanel);
        pack();
        setLocationRelativeTo(owner);
        javax.swing.SwingUtilities.invokeLater(() -> queryField.requestFocusInWindow());
    }

    private void attachDebounce(JTextField field) {
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { debounce.restart(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { debounce.restart(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { debounce.restart(); }
        });
    }

    private void clearFilters() {
        if (worker != null && !worker.isDone()) {
            worker.cancel(true);
        }
        queryField.setText("");
        tagField.setText("");
        fromDateField.setText("");
        toDateField.setText("");
        moodMin.setValue(0);
        moodMax.setValue(100);
        model.clear();
        list.clearSelection();
        statusLabel.setText("Type to search.");
    }

    private void runSearch() {
        if (worker != null && !worker.isDone()) {
            worker.cancel(true);
        }
        model.clear();
        list.clearSelection();

        GlobalSearchEngine.SearchQuery query = buildQuery();
        if (query == null) return;
        if (query.isEmpty()) {
            statusLabel.setText("Type to search.");
            return;
        }

        statusLabel.setText("Searching...");
        worker = new SwingWorker<>() {
            @Override protected Void doInBackground() {
                GlobalSearchEngine.search(query, GlobalSearchDialog.this, result -> publish(result), this::isCancelled);
                return null;
            }

            @Override protected void process(List<GlobalSearchEngine.SearchResult> chunks) {
                for (GlobalSearchEngine.SearchResult r : chunks) {
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
        GlobalSearchEngine.SearchResult selected = list.getSelectedValue();
        if (selected == null) return;
        if (app != null) {
            app.openExistingEntryEditor(selected.notebook, selected.file);
            dispose();
        }
    }

    private GlobalSearchEngine.SearchQuery buildQuery() {
        String q = queryField.getText() == null ? "" : queryField.getText().trim();
        String tagText = tagField.getText() == null ? "" : tagField.getText().trim();
        String fromRaw = fromDateField.getText();
        String toRaw = toDateField.getText();
        LocalDate fromDate = parseDate(fromRaw);
        LocalDate toDate = parseDate(toRaw);
        if (fromRaw != null && !fromRaw.trim().isEmpty() && fromDate == null) {
            statusLabel.setText("Invalid from date (use YYYY-MM-DD).");
            return null;
        }
        if (toRaw != null && !toRaw.trim().isEmpty() && toDate == null) {
            statusLabel.setText("Invalid to date (use YYYY-MM-DD).");
            return null;
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            LocalDate tmp = fromDate;
            fromDate = toDate;
            toDate = tmp;
            fromDateField.setText(fromDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
            toDateField.setText(toDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
        }
        int minMood = (Integer) moodMin.getValue();
        int maxMood = (Integer) moodMax.getValue();
        if (minMood > maxMood) {
            int tmp = minMood;
            minMood = maxMood;
            maxMood = tmp;
            moodMin.setValue(minMood);
            moodMax.setValue(maxMood);
        }
        return new GlobalSearchEngine.SearchQuery(q, tagText, fromDate, toDate, minMood, maxMood, false);
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(raw.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            return null;
        }
    }

    private static class SearchResultRenderer extends JPanel implements ListCellRenderer<GlobalSearchEngine.SearchResult> {
        private final JLabel titleLine = new JLabel();
        private final JLabel snippetLine = new JLabel();
        private final JLabel meta = new JLabel();
        private boolean selected;

        SearchResultRenderer() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);
            titleLine.setFont(AeroTheme.defaultBoldFont(14f));
            titleLine.setForeground(new Color(30, 36, 46));
            snippetLine.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 12f));
            snippetLine.setForeground(new Color(105, 115, 130));
            meta.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 11f));
            meta.setForeground(new Color(135, 145, 160));
            add(titleLine);
            add(snippetLine);
            add(meta);
            setBorder(new EmptyBorder(10, 12, 10, 12));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends GlobalSearchEngine.SearchResult> list, GlobalSearchEngine.SearchResult value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            if (value != null) {
                String entryTitle = value.title != null && !value.title.isEmpty() ? value.title : "Untitled";
                String nbName = value.notebook.getName();
                titleLine.setText(entryTitle);

                String snippet = value.snippet == null ? "" : value.snippet.trim();
                if (snippet.isEmpty()) {
                    snippetLine.setText("");
                    snippetLine.setVisible(false);
                } else {
                    snippetLine.setVisible(true);
                    int wrap = list.getWidth() > 0 ? Math.max(320, list.getWidth() - 80) : 520;
                    snippetLine.setText("<html><div style='width:" + wrap + "px;'>" + escapeHtml(snippet) + "</div></html>");
                }

                String date = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                        .format(Instant.ofEpochMilli(value.savedAt).atZone(ZoneId.systemDefault()).toLocalDate());
                StringBuilder sb = new StringBuilder("📓 ").append(nbName).append("  •  ").append(date);
                if (value.mood >= 0) sb.append("  •  Mood ").append(value.mood);
                if (value.tags != null && !value.tags.isEmpty()) {
                    sb.append("  •  #").append(String.join(" #", value.tags));
                }
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
            Color fill = selected ? new Color(230, 243, 255) : new Color(255, 255, 255);
            Color border = selected ? new Color(120, 170, 255) : new Color(220, 228, 236);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.setColor(border);
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }

        private static String escapeHtml(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }

    private static JLabel makeLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(AeroTheme.defaultBoldFont(12f));
        label.setForeground(new Color(55, 60, 70));
        return label;
    }

}
