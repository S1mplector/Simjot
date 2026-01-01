/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.poetry;

import main.core.poetry.MeterScanner;
import main.core.poetry.MeterAnalysis;
import main.ui.components.scrollbar.ModernScrollBarUI;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.awt.event.MouseEvent;
import java.util.function.IntConsumer;

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
    private final java.util.List<Integer> textLineByModelIndex = new ArrayList<>(); // reverse: model index -> text line
    private final java.util.List<Integer> contentLineByModelIndex = new ArrayList<>(); // reverse: model index -> content line (skip stanza separators)
    private final java.util.List<int[]> stressByTextLine = new ArrayList<>();
    private final MeterScanner meterScanner = new MeterScanner();

    // Async/debounce state
    private javax.swing.Timer debounceTimer;
    private volatile SwingWorker<MeterAnalysis, Void> analysisWorker;
    private String pendingText = "";
    private String lastText = ""; // cache last non-null text for recomputation
    private String lastDetectedForm = "";

    // Optional callback to notify editor to move caret to a given text line index
    private IntConsumer onRowClick;
    private Runnable onAnalysisFinished;

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
    private int[] targetPattern = new int[0];
    private String targetPatternLabel = null;
    private Integer patternExpectedLines = null;
    private boolean suppressSpinnerEvents = false;

    public StatsSidebarPanel(){
        super(new BorderLayout());
        setOpaque(false);
        list.setOpaque(false);
        list.setForeground(new Color(70,70,70));
        list.setFont(new Font("SansSerif", Font.PLAIN, 12));
        // Enable tooltips for list items
        ToolTipManager.sharedInstance().registerComponent(list);
        // Click to sync caret back to editor (single-click)
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (onRowClick == null) return;
                int i = list.locationToIndex(e.getPoint());
                if (i < 0 || i >= model.getSize()) return;
                // Ignore stanza separator rows
                if (i < stanzaBreakByIndex.size() && Boolean.TRUE.equals(stanzaBreakByIndex.get(i))) return;
                int textLine = (i < textLineByModelIndex.size() ? textLineByModelIndex.get(i) : -1);
                if (textLine >= 0) {
                    onRowClick.accept(textLine);
                }
            }
        });

        // Debounce timer for background analysis
        debounceTimer = new javax.swing.Timer(160, e -> {
            String text = pendingText; // snapshot
            runAnalysisAsync(text);
        });
        debounceTimer.setRepeats(false);

        // Compact header with richer controls
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        header.setOpaque(false);
        JLabel meterLbl = new JLabel("Meter:");
        meterLbl.setForeground(new Color(70,70,70));
        targetSpinner.setToolTipText("Target syllables per line (0 = off)");
        ((JSpinner.DefaultEditor)targetSpinner.getEditor()).getTextField().setColumns(2);
        targetSpinner.addChangeListener(e -> {
            if (suppressSpinnerEvents) return;
            Object v = targetSpinner.getValue();
            targetSyllables = (v instanceof Integer) ? (Integer) v : 0;
            // Manual meter changes override any active form pattern
            targetPattern = new int[0];
            targetPatternLabel = null;
            patternExpectedLines = null;
            list.repaint();
            if (lastText != null) runAnalysisAsync(lastText);
        });
        perStanzaToggle.setOpaque(false);
        perStanzaToggle.setForeground(new Color(70,70,70));
        perStanzaToggle.addActionListener(e -> {
            perStanza = perStanzaToggle.isSelected();
            // Recompute immediately using last known text
            if (lastText != null) runAnalysisAsync(lastText);
        });
        header.add(meterLbl);
        header.add(targetSpinner);

        // Preset buttons: 0(off), 6,7,8,9,10,11,12,14
        JPanel presetsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        presetsPanel.setOpaque(false);
        int[] presets = new int[]{0,6,7,8,9,10,11,12,14};
        for (int p : presets) {
            JButton b = new JButton(String.valueOf(p));
            b.setFocusable(false);
            b.setMargin(new Insets(2,6,2,6));
            b.setToolTipText(p == 0 ? "Turn off target meter" : ("Set target to " + p));
            b.addActionListener(ev -> {
                targetSpinner.setValue(p);
                if (lastText != null) runAnalysisAsync(lastText);
            });
            presetsPanel.add(b);
        }
        header.add(new JLabel("  "));
        header.add(presetsPanel);

        // Meter preset combo (single-target conveniences)
        header.add(new JLabel("  Preset:"));
        JComboBox<String> presetCombo = new JComboBox<>(new String[]{
                "Off (0)",
                "Light (6)",
                "Heptasyllabic (7)",
                "Octosyllabic (8)",
                "Nona (9)",
                "Pentameter (10)",
                "Hendecasyllabic (11)",
                "Alexandrine (12)",
                "Fourteen (14)"
        });
        presetCombo.setFocusable(false);
        presetCombo.addActionListener(ev -> {
            String sel = Objects.toString(presetCombo.getSelectedItem(), "");
            int val = 0;
            if (sel.contains("(6)")) val = 6;
            else if (sel.contains("(7)")) val = 7;
            else if (sel.contains("(8)")) val = 8;
            else if (sel.contains("(9)")) val = 9;
            else if (sel.contains("(10)")) val = 10;
            else if (sel.contains("(11)")) val = 11;
            else if (sel.contains("(12)")) val = 12;
            else if (sel.contains("(14)")) val = 14;
            targetSpinner.setValue(val);
            if (lastText != null) runAnalysisAsync(lastText);
        });
        header.add(presetCombo);
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
                } else if (targetPattern != null && targetPattern.length > 0
                        && index >= 0 && index < contentLineByModelIndex.size()
                        && index < syllablesByIndex.size()) {
                    int ord = contentLineByModelIndex.get(index);
                    int expected = (ord >= 0 && targetPattern.length > 0) ? targetPattern[ord % targetPattern.length] : 0;
                    if (expected > 0) {
                        int syl = Math.max(0, syllablesByIndex.get(index));
                        int diff = syl - expected;
                        Color col = switch (Math.abs(diff)) {
                            case 0 -> new Color(20, 120, 20);
                            case 1 -> new Color(30, 150, 30);
                            case 2, 3 -> new Color(180, 110, 0);
                            default -> new Color(160, 30, 30);
                        };
                        c.setForeground(col);
                    }
                } else if (index >= 0 && index < textLineByModelIndex.size()) {
                    int textLine = textLineByModelIndex.get(index);
                    if (textLine >= 0 && textLine < stressByTextLine.size()) {
                        int[] stress = stressByTextLine.get(textLine);
                        if (stress != null && stress.length > 0) {
                            int pulses = 0;
                            for (int b : stress) if (b == 1) pulses++;
                            if (pulses > 0) {
                                c.setForeground(new Color(60, 100, 160));
                            }
                        }
                    }
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

        // Trigger initial analysis when the panel becomes visible, if we have text cached
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (isShowing() && model.isEmpty() && lastText != null && !lastText.isEmpty()) {
                    runAnalysisAsync(lastText);
                }
            }
        });
    }

    public void updateFromText(String text){
        // Debounced, non-blocking update
        lastText = (text == null ? "" : text);
        pendingText = lastText;
        if (debounceTimer.isRunning()) debounceTimer.stop();
        debounceTimer.start();
    }

    private void runAnalysisAsync(String text) {
        // Cancel previous worker if running
        SwingWorker<MeterAnalysis, Void> prev = analysisWorker;
        if (prev != null && !prev.isDone()) prev.cancel(true);

        analysisWorker = new SwingWorker<>() {
            @Override
            protected MeterAnalysis doInBackground() {
                try {
                    return meterScanner.analyze(text, perStanza);
                } catch (Throwable t) {
                    return new MeterAnalysis(
                            java.util.Collections.<String>emptyList(),
                            java.util.Collections.<Integer>emptyList(),
                            java.util.Collections.<Boolean>emptyList(),
                            java.util.Collections.<String>emptyList(),
                            java.util.Collections.<Integer>emptyList(),
                            java.util.Collections.<Integer>emptyList(),
                            java.util.Collections.<String>emptyList(),
                            "",
                            java.util.Collections.<int[]>emptyList(),
                            0,0,0,0.0);
                }
            }
            @Override
            protected void done() {
                if (isCancelled()) return;
                MeterAnalysis a = null;
                try { a = get(); } catch (Throwable ignored) {}
                if (a == null) return;
                applyAnalysis(a);
            }
        };
        analysisWorker.execute();
    }

    private void applyAnalysis(MeterAnalysis a) {
        model.clear();
        syllablesByIndex.clear();
        stanzaBreakByIndex.clear();
        tooltipsByIndex.clear();
        modelIndexByTextLine.clear();
        textLineByModelIndex.clear();
        contentLineByModelIndex.clear();
        lastDetectedForm = a.detectedForm == null ? "" : a.detectedForm;
        stressByTextLine.clear();
        stressByTextLine.addAll(a.stressByTextLine);

        // Fill model and side arrays from analysis
        for (int i = 0; i < a.displayRows.size(); i++) {
            model.addElement(a.displayRows.get(i));
        }
        syllablesByIndex.addAll(a.syllablesByRow);
        stanzaBreakByIndex.addAll(a.stanzaBreakByRow);
        tooltipsByIndex.addAll(a.tooltipsByRow);
        modelIndexByTextLine.addAll(a.modelIndexByTextLine);
        // Build reverse mapping: model index -> text line index
        for (int i = 0; i < model.size(); i++) textLineByModelIndex.add(-1);
        for (int textLine = 0; textLine < modelIndexByTextLine.size(); textLine++) {
            int modelIdx = modelIndexByTextLine.get(textLine);
            if (modelIdx >= 0 && modelIdx < textLineByModelIndex.size()) {
                textLineByModelIndex.set(modelIdx, textLine);
            }
        }
        // Build mapping: model index -> ordinal content line (skip stanza separators)
        int contentOrdinal = 0;
        for (int i = 0; i < model.size(); i++) {
            boolean stanza = i < stanzaBreakByIndex.size() && Boolean.TRUE.equals(stanzaBreakByIndex.get(i));
            if (stanza) {
                contentLineByModelIndex.add(-1);
            } else {
                contentLineByModelIndex.add(contentOrdinal++);
            }
        }

        // Enhance tooltips with Δtarget info if target set
        if (targetSyllables > 0) {
            for (int i = 0; i < tooltipsByIndex.size() && i < syllablesByIndex.size() && i < stanzaBreakByIndex.size(); i++) {
                if (Boolean.TRUE.equals(stanzaBreakByIndex.get(i))) continue;
                int syl = Math.max(0, syllablesByIndex.get(i));
                int diff = syl - targetSyllables;
                String extra = String.format(Locale.ROOT, "  |  Δtarget: %s%d", diff>=0?"+":"", diff);
                String tip = tooltipsByIndex.get(i);
                tooltipsByIndex.set(i, (tip == null || tip.isBlank() ? "" : tip) + extra);
            }
        }

        if (targetPattern != null && targetPattern.length > 0) {
            for (int i = 0; i < tooltipsByIndex.size()
                    && i < syllablesByIndex.size()
                    && i < stanzaBreakByIndex.size()
                    && i < contentLineByModelIndex.size(); i++) {
                if (Boolean.TRUE.equals(stanzaBreakByIndex.get(i))) continue;
                int ord = contentLineByModelIndex.get(i);
                if (ord < 0) continue;
                int expected = targetPattern[ord % targetPattern.length];
                if (expected <= 0) continue;
                int syl = Math.max(0, syllablesByIndex.get(i));
                int diff = syl - expected;
                String extra = String.format(Locale.ROOT, "  |  Form target: %d (Δ%s%d)", expected, diff>=0?"+":"", diff);
                String tip = tooltipsByIndex.get(i);
                tooltipsByIndex.set(i, (tip == null || tip.isBlank() ? "" : tip) + extra);
            }
        }

        // Append stress pattern hints to tooltips
        for (int i = 0; i < tooltipsByIndex.size() && i < textLineByModelIndex.size(); i++) {
            int tl = textLineByModelIndex.get(i);
            if (tl < 0 || tl >= stressByTextLine.size()) continue;
            int[] stress = stressByTextLine.get(tl);
            if (stress == null || stress.length == 0) continue;
            String pattern = stressPatternString(stress);
            String tip = tooltipsByIndex.get(i);
            tooltipsByIndex.set(i, (tip == null || tip.isBlank() ? "" : tip) + "  |  Stress: " + pattern);
        }

        // Summary footer: lines, stanzas, avg/min/max, stddev, target hit rate
        int stanzaCount = 0;
        java.util.List<Integer> lineSyls = new ArrayList<>();
        for (int i = 0; i < stanzaBreakByIndex.size(); i++) {
            if (Boolean.TRUE.equals(stanzaBreakByIndex.get(i))) stanzaCount++;
            else if (i < syllablesByIndex.size()) lineSyls.add(Math.max(0, syllablesByIndex.get(i)));
        }
        if (a.countedLines > 0) {
            double mean = a.avgSyllables;
            double var = 0.0;
            for (int s : lineSyls) { double d = s - mean; var += d*d; }
            double std = lineSyls.isEmpty() ? 0.0 : Math.sqrt(var / lineSyls.size());
            String base = String.format(Locale.ROOT, "Lines: %d  •  Stanzas: %d  •  Avg: %.1f (σ=%.1f)  •  Min: %d  •  Max: %d",
                    a.countedLines, stanzaCount, mean, std, a.minSyllables, a.maxSyllables);
            StringBuilder sb = new StringBuilder(base);
            if (targetSyllables > 0) {
                int hits = 0, near = 0;
                for (int s : lineSyls) {
                    int d = Math.abs(s - targetSyllables);
                    if (d == 0) hits++; else if (d == 1) near++;
                }
                int n = Math.max(1, lineSyls.size());
                String tgt = String.format(Locale.ROOT, "  •  Target %d: %d%% exact, %d%% within ±1",
                        targetSyllables, Math.round(hits*100.0/n), Math.round((hits+near)*100.0/n));
                sb.append(tgt);
            }

            if (targetPattern != null && targetPattern.length > 0) {
                int hits = 0, near = 0, total = 0;
                for (int i = 0; i < contentLineByModelIndex.size() && i < syllablesByIndex.size(); i++) {
                    int ord = contentLineByModelIndex.get(i);
                    if (ord < 0) continue;
                    int expected = targetPattern[ord % targetPattern.length];
                    if (expected <= 0) continue;
                    int diff = Math.abs(Math.max(0, syllablesByIndex.get(i)) - expected);
                    if (diff == 0) hits++;
                    else if (diff == 1) near++;
                    total++;
                }
                if (total > 0) {
                    sb.append("  •  ");
                    sb.append(targetPatternLabel != null && !targetPatternLabel.isBlank() ? targetPatternLabel : "Form target");
                    sb.append(": ").append(hits).append("/").append(total).append(" on beat");
                    if (near > 0) sb.append(", ").append(near).append(" near");
                    if (patternExpectedLines != null && patternExpectedLines > 0) {
                        sb.append(" (expect ").append(patternExpectedLines).append(" lines)");
                    }
                }
            }

            if (lastDetectedForm != null && !lastDetectedForm.isBlank()) {
                sb.append("  •  Detected: ").append(lastDetectedForm);
            }
            summaryLabel.setText(sb.toString());
        } else {
            summaryLabel.setText(" ");
        }

        if (onAnalysisFinished != null) {
            try { onAnalysisFinished.run(); } catch (Throwable ignored) {}
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

    public void setTargetPattern(int[] pattern, String label, Integer expectedLines) {
        if (pattern == null || pattern.length == 0) {
            targetPattern = new int[0];
            targetPatternLabel = null;
            patternExpectedLines = null;
        } else {
            targetPattern = java.util.Arrays.copyOf(pattern, pattern.length);
            targetPatternLabel = label;
            patternExpectedLines = expectedLines;
        }
        // Keep uniform target dormant when a form pattern is active
        suppressSpinnerEvents = true;
        try {
            if (targetPattern.length > 0) {
                targetSyllables = 0;
                try { targetSpinner.setValue(0); } catch (Throwable ignored) {}
            }
        } finally {
            suppressSpinnerEvents = false;
        }
        list.repaint();
        if (lastText != null) runAnalysisAsync(lastText);
    }

    public void clearTargetPattern() {
        setTargetPattern(null, null, null);
    }

    public String getTargetPatternLabel() { return targetPatternLabel; }

    public String getDetectedForm() { return lastDetectedForm; }

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

    private static String stressPatternString(int[] pattern) {
        if (pattern == null || pattern.length == 0) return "";
        StringBuilder sb = new StringBuilder(pattern.length);
        for (int b : pattern) {
            sb.append(b == 1 ? '/' : '˘');
        }
        return sb.toString();
    }

    // --- Editor sync: notify on row click ---
    /**
     * Register a callback to be notified when the user clicks a sidebar row.
     * The callback receives the corresponding text line index in the editor (0-based).
     */
    public void setRowClickListener(IntConsumer onLineClicked) {
        this.onRowClick = onLineClicked;
    }

    public void setAnalysisFinishedCallback(Runnable onAnalysis) {
        this.onAnalysisFinished = onAnalysis;
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
