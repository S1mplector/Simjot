package main.ui.features.poetry;

import main.core.poetry.PoetryUtils;
import main.ui.components.scrollbar.ModernScrollBarUI;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.awt.event.MouseEvent;

/**
 * Non-intrusive sidebar showing per-line syllable counts and end-rhyme labels.
 * Keep it lightweight and decoupled from the editor.
 */
public class StatsSidebarPanel extends JPanel {
    private final DefaultListModel<String> model = new DefaultListModel<>();
    // Keep lightweight metadata aligned with list model indices
    private final java.util.List<Integer> syllablesByIndex = new ArrayList<>();
    private final java.util.List<Boolean> stanzaBreakByIndex = new ArrayList<>();
    private final java.util.List<String> tooltipsByIndex = new ArrayList<>();
    private final java.util.List<Integer> modelIndexByTextLine = new ArrayList<>(); // maps text line -> model index

    private final JList<String> list = new JList<>(model) {
        @Override
        public String getToolTipText(MouseEvent event) {
            int i = locationToIndex(event.getPoint());
            if (i >= 0 && i < tooltipsByIndex.size()) return tooltipsByIndex.get(i);
            return null;
        }
    };

    // Controls
    private final JSpinner targetSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 24, 1)); // 0 = off
    private final JCheckBox perStanzaToggle = new JCheckBox("Per-stanza rhymes", true);
    private final JButton copyBtn = new JButton("Copy");
    private final JLabel summaryLabel = new JLabel(" ");

    // Cached state
    private int targetSyllables = 0; // 0 disables coloring
    private boolean perStanza = true;

    public StatsSidebarPanel(){
        super(new BorderLayout());
        setOpaque(false);
        list.setOpaque(false);
        list.setForeground(new Color(70,70,70));
        list.setFont(new Font("SansSerif", Font.PLAIN, 12));
        // Enable tooltips for list items
        ToolTipManager.sharedInstance().registerComponent(list);

        // Compact header with minimal controls
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        header.setOpaque(false);
        JLabel meterLbl = new JLabel("Meter:");
        meterLbl.setForeground(new Color(70,70,70));
        targetSpinner.setToolTipText("Target syllables per line (0 = off)");
        ((JSpinner.DefaultEditor)targetSpinner.getEditor()).getTextField().setColumns(2);
        targetSpinner.addChangeListener(e -> {
            Object v = targetSpinner.getValue();
            targetSyllables = (v instanceof Integer) ? (Integer) v : 0;
            list.repaint();
        });
        perStanzaToggle.setOpaque(false);
        perStanzaToggle.setForeground(new Color(70,70,70));
        perStanzaToggle.addActionListener(e -> {
            perStanza = perStanzaToggle.isSelected();
            // Recompute labels with same last text if caller refreshes soon; safe to no-op otherwise
        });
        header.add(meterLbl);
        header.add(targetSpinner);
        // Preset buttons 8/10/12 for quick meter selection
        JButton p8 = new JButton("8");
        JButton p10 = new JButton("10");
        JButton p12 = new JButton("12");
        for (JButton b : new JButton[]{p8, p10, p12}) {
            b.setFocusable(false);
            b.setMargin(new Insets(2,6,2,6));
            b.setFont(new Font("SansSerif", Font.PLAIN, 11));
            header.add(b);
        }
        p8.addActionListener(e -> targetSpinner.setValue(8));
        p10.addActionListener(e -> targetSpinner.setValue(10));
        p12.addActionListener(e -> targetSpinner.setValue(12));
        header.add(Box.createHorizontalStrut(6));
        header.add(perStanzaToggle);
        header.add(Box.createHorizontalStrut(6));
        copyBtn.setFocusable(false);
        copyBtn.setMargin(new Insets(2,6,2,6));
        copyBtn.setToolTipText("Copy metrics to clipboard");
        copyBtn.addActionListener(e -> copyMetricsToClipboard());
        header.add(copyBtn);

        // Custom cell renderer for subtle color cues
        list.setCellRenderer(new DefaultListCellRenderer(){
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel c = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                c.setOpaque(false);
                c.setFont(new Font("SansSerif", Font.PLAIN, 12));
                c.setForeground(new Color(70,70,70));
                if (index >= 0 && index < stanzaBreakByIndex.size() && Boolean.TRUE.equals(stanzaBreakByIndex.get(index))) {
                    c.setForeground(new Color(120,120,120));
                    c.setFont(c.getFont().deriveFont(Font.ITALIC));
                } else if (targetSyllables > 0 && index >= 0 && index < syllablesByIndex.size()) {
                    int syl = Math.max(0, syllablesByIndex.get(index));
                    int diff = syl - targetSyllables;
                    // within +/-1: greenish, off by 2-3: orange, else red
                    Color col = switch (Math.abs(diff)) {
                        case 0, 1 -> new Color(20, 120, 20);
                        case 2, 3 -> new Color(180, 110, 0);
                        default -> new Color(160, 30, 30);
                    };
                    c.setForeground(col);
                }
                return c;
            }
        });

        JScrollPane sp = new JScrollPane(list){
            { setBorder(BorderFactory.createEmptyBorder()); setOpaque(false); getViewport().setOpaque(false);} };
        // Apply modern scrollbar UI
        JScrollBar vbar = sp.getVerticalScrollBar();
        if (vbar != null) {
            vbar.setUI(new ModernScrollBarUI());
            vbar.setUnitIncrement(16);
            vbar.setPreferredSize(new Dimension(12, Integer.MAX_VALUE));
        }
        JScrollBar hbar = sp.getHorizontalScrollBar();
        if (hbar != null) {
            hbar.setUI(new ModernScrollBarUI());
            hbar.setUnitIncrement(16);
            hbar.setPreferredSize(new Dimension(Integer.MAX_VALUE, 12));
        }

        // Footer summary
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        summaryLabel.setForeground(new Color(90,90,90));
        summaryLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
        summaryLabel.setBorder(BorderFactory.createEmptyBorder(4, 2, 2, 2));
        footer.add(summaryLabel, BorderLayout.CENTER);

        add(header, BorderLayout.NORTH);
        add(sp, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);

        setPreferredSize(new Dimension(200, 0));
        setBorder(BorderFactory.createEmptyBorder(6,6,6,6));
    }

    public void updateFromText(String text){
        java.util.List<String> lines = PoetryUtils.splitLines(text);
        model.clear();
        syllablesByIndex.clear();
        stanzaBreakByIndex.clear();
        tooltipsByIndex.clear();
        modelIndexByTextLine.clear();

        // Build rhyme groups (global or per stanza)
        Map<String, Character> keyToLabel = new LinkedHashMap<>();
        int stanzaNo = 1;
        int totalSyl = 0, minSyl = Integer.MAX_VALUE, maxSyl = 0, countedLines = 0;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            boolean isBlank = (line == null || line.trim().isEmpty());
            if (isBlank) {
                // Add a subtle stanza break marker
                String sep = String.format(Locale.ROOT, "— stanza %d —", stanzaNo);
                model.addElement(sep);
                syllablesByIndex.add(0);
                stanzaBreakByIndex.add(true);
                tooltipsByIndex.add(" ");
                // Map this blank text line to the separator model row for caret sync
                modelIndexByTextLine.add(model.size()-1);
                stanzaNo++;
                if (perStanza) keyToLabel.clear();
                continue;
            }

            int syl = PoetryUtils.countSyllablesInLine(line);
            String end = PoetryUtils.endWord(line);
            String key = end != null ? PoetryUtils.rhymeKey(end) : null;
            Character label = null;
            if (key != null && !key.isBlank()){
                label = keyToLabel.get(key);
                if (label == null) {
                    int idx = keyToLabel.size();
                    label = (char) ('A' + Math.min(idx, 25));
                    keyToLabel.put(key, label);
                }
            }
            String lbl = String.format(Locale.ROOT, "%2d: %2d syl%s%s",
                    (i+1), syl,
                    label!=null?" • ":"",
                    label!=null?label.toString():"");
            model.addElement(lbl);
            syllablesByIndex.add(syl);
            stanzaBreakByIndex.add(false);
            String tip;
            if (end != null && key != null) tip = String.format(Locale.ROOT, "End: %s  |  Rhyme key: %s", end, key);
            else if (end != null) tip = String.format(Locale.ROOT, "End: %s", end);
            else tip = " ";
            tooltipsByIndex.add(tip);
            modelIndexByTextLine.add(model.size()-1);

            totalSyl += syl; countedLines++;
            minSyl = Math.min(minSyl, syl);
            maxSyl = Math.max(maxSyl, syl);
        }

        // Summary footer
        if (countedLines > 0) {
            double avg = totalSyl / (double) countedLines;
            summaryLabel.setText(String.format(Locale.ROOT, "Avg: %.1f  •  Min: %d  •  Max: %d", avg, minSyl, maxSyl));
        } else {
            summaryLabel.setText(" ");
        }
    }

    // --- Optional external controls ---
    public void setTargetSyllables(int target) {
        this.targetSyllables = Math.max(0, target);
        targetSpinner.setValue(this.targetSyllables);
        list.repaint();
    }

    public int getTargetSyllables() { return targetSyllables; }

    public void setPerStanza(boolean perStanza) {
        this.perStanza = perStanza;
        perStanzaToggle.setSelected(perStanza);
    }

    public boolean isPerStanza() { return perStanza; }

    // --- Caret sync: highlight current line and auto-scroll ---
    private int highlightedModelIndex = -1;
    public void setHighlightedLine(int textLineIndex) {
        if (textLineIndex < 0 || textLineIndex >= modelIndexByTextLine.size()) {
            list.clearSelection();
            highlightedModelIndex = -1;
            return;
        }
        highlightedModelIndex = modelIndexByTextLine.get(textLineIndex);
        if (highlightedModelIndex >= 0 && highlightedModelIndex < model.size()) {
            list.setSelectedIndex(highlightedModelIndex);
            list.ensureIndexIsVisible(highlightedModelIndex);
        }
    }

    // --- Export: copy metrics to clipboard ---
    private void copyMetricsToClipboard() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < model.size(); i++) {
            String row = model.get(i);
            boolean sep = (i < stanzaBreakByIndex.size() && stanzaBreakByIndex.get(i));
            if (sep) {
                sb.append(row).append('\n');
            } else {
                sb.append(row).append('\n');
            }
        }
        if (summaryLabel.getText() != null && !summaryLabel.getText().isBlank()) {
            sb.append('\n').append(summaryLabel.getText()).append('\n');
        }
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(sb.toString()), null);
        } catch (Throwable ignored) { /* no-op */ }
    }
}
