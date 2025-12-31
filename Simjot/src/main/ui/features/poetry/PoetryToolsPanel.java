/*
 * SIMJOT POETRY ENGINE - PROPRIETARY
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 * 
 * This source code is licensed under the Simjot Poetry Engine Proprietary License.
 * You may inspect this code for educational and research purposes only.
 * Use, modification, or incorporation into other projects is strictly prohibited.
 * 
 * See LICENSE file in this package for full terms.
 */

package main.ui.features.poetry;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import main.core.poetry.LineBreakEngine;
import main.core.poetry.ScansionEngine;
import main.core.poetry.SoundDevicesEngine;
import main.core.poetry.ThematicAnalyzer;
import main.core.poetry.VocabularyAnalyzer;
import main.ui.components.scrollbar.ModernScrollBarUI;

/**
 * PoetryToolsPanel - Comprehensive poetry analysis tools panel.
 * 
 * Integrates all poetry engines:
 * - Scansion analysis with stress marking
 * - Sound devices (alliteration, assonance, consonance)
 * - Vocabulary analysis and richness metrics
 * - Line break suggestions
 * - Thematic analysis with keyword clustering
 */
public class PoetryToolsPanel extends JPanel {
    
    private final JTabbedPane tabbedPane;
    private final ScansionPanel scansionPanel;
    private final SoundDevicesPanel soundPanel;
    private final VocabularyPanel vocabularyPanel;
    private final LineBreakPanel lineBreakPanel;
    private final ThematicPanel thematicPanel;
    
    private Supplier<String> textSupplier;
    private String lastAnalyzedText = "";
    
    public PoetryToolsPanel() {
        super(new BorderLayout());
        setOpaque(false);
        setBorder(new EmptyBorder(8, 8, 8, 8));
        
        // Header
        JPanel header = createHeader();
        add(header, BorderLayout.NORTH);
        
        // Tabbed pane for different tools
        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setFont(new Font("SansSerif", Font.PLAIN, 12));
        tabbedPane.setOpaque(false);
        
        scansionPanel = new ScansionPanel();
        soundPanel = new SoundDevicesPanel();
        vocabularyPanel = new VocabularyPanel();
        lineBreakPanel = new LineBreakPanel();
        thematicPanel = new ThematicPanel();
        
        tabbedPane.addTab("Scansion", createIcon("♩"), scansionPanel, "Analyze meter and stress patterns");
        tabbedPane.addTab("Sound", createIcon("♪"), soundPanel, "Detect alliteration, assonance, consonance");
        tabbedPane.addTab("Vocabulary", createIcon("📚"), vocabularyPanel, "Word frequency and richness metrics");
        tabbedPane.addTab("Line Breaks", createIcon("¶"), lineBreakPanel, "Suggest line breaks based on meter");
        tabbedPane.addTab("Themes", createIcon("🎭"), thematicPanel, "Thematic analysis and keyword clustering");
        
        add(tabbedPane, BorderLayout.CENTER);
        
        setPreferredSize(new Dimension(400, 500));
    }
    
    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(0, 0, 8, 0));
        
        JLabel title = new JLabel("Poetry Analysis Tools");
        title.setFont(new Font("SansSerif", Font.BOLD, 14));
        title.setForeground(new Color(60, 60, 60));
        
        JButton analyzeBtn = new JButton("Analyze");
        analyzeBtn.setFocusable(false);
        analyzeBtn.addActionListener(e -> runAnalysis());
        
        JButton refreshBtn = new JButton("↻");
        refreshBtn.setToolTipText("Refresh analysis");
        refreshBtn.setFocusable(false);
        refreshBtn.setMargin(new Insets(2, 6, 2, 6));
        refreshBtn.addActionListener(e -> runAnalysis());
        
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttons.setOpaque(false);
        buttons.add(analyzeBtn);
        buttons.add(refreshBtn);
        
        header.add(title, BorderLayout.WEST);
        header.add(buttons, BorderLayout.EAST);
        
        return header;
    }
    
    private Icon createIcon(String text) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                g.setFont(new Font("SansSerif", Font.PLAIN, 12));
                g.drawString(text, x, y + 12);
            }
            @Override
            public int getIconWidth() { return 16; }
            @Override
            public int getIconHeight() { return 16; }
        };
    }
    
    /**
     * Set the text supplier for analysis.
     */
    public void setTextSupplier(Supplier<String> supplier) {
        this.textSupplier = supplier;
    }
    
    /**
     * Run analysis on the current text.
     */
    public void runAnalysis() {
        if (textSupplier == null) return;
        
        String text = textSupplier.get();
        if (text == null || text.isBlank()) return;
        
        lastAnalyzedText = text;
        
        // Run analysis in background
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                scansionPanel.analyze(text);
                soundPanel.analyze(text);
                vocabularyPanel.analyze(text);
                lineBreakPanel.analyze(text);
                thematicPanel.analyze(text);
                return null;
            }
        };
        worker.execute();
    }
    
    /**
     * Update analysis with new text.
     */
    public void updateFromText(String text) {
        if (text == null || text.equals(lastAnalyzedText)) return;
        lastAnalyzedText = text;
        runAnalysis();
    }
    
    // ========== Inner Panel Classes ==========
    
    /**
     * Scansion analysis panel.
     */
    private static class ScansionPanel extends JPanel {
        private final JTextPane resultPane;
        private final JLabel meterLabel;
        private final JLabel confidenceLabel;
        private final ScansionEngine engine = new ScansionEngine();
        
        ScansionPanel() {
            super(new BorderLayout(0, 8));
            setOpaque(false);
            setBorder(new EmptyBorder(8, 4, 4, 4));
            
            // Info panel
            JPanel info = new JPanel(new GridLayout(2, 1, 2, 2));
            info.setOpaque(false);
            meterLabel = new JLabel("Meter: -");
            meterLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
            confidenceLabel = new JLabel("Confidence: -");
            confidenceLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
            confidenceLabel.setForeground(new Color(100, 100, 100));
            info.add(meterLabel);
            info.add(confidenceLabel);
            add(info, BorderLayout.NORTH);
            
            // Results
            resultPane = new JTextPane();
            resultPane.setEditable(false);
            resultPane.setFont(new Font("Monospaced", Font.PLAIN, 12));
            resultPane.setBorder(new EmptyBorder(8, 8, 8, 8));
            
            JScrollPane scroll = createScrollPane(resultPane);
            add(scroll, BorderLayout.CENTER);
            
            // Legend
            JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
            legend.setOpaque(false);
            legend.add(new JLabel("/ = stressed"));
            legend.add(new JLabel("˘ = unstressed"));
            legend.add(new JLabel("| = foot boundary"));
            add(legend, BorderLayout.SOUTH);
        }
        
        void analyze(String text) {
            ScansionEngine.PoemScansion result = engine.analyzePoem(text);
            
            SwingUtilities.invokeLater(() -> {
                meterLabel.setText("Dominant Meter: " + result.dominantMeter);
                confidenceLabel.setText(String.format("Confidence: %.0f%% | Syllables: %d | Stressed: %d | Unstressed: %d",
                        result.overallConfidence * 100, result.totalSyllables, 
                        result.totalStressed, result.totalUnstressed));
                
                // Build styled result
                StyledDocument doc = resultPane.getStyledDocument();
                try {
                    doc.remove(0, doc.getLength());
                    
                    Style defaultStyle = resultPane.addStyle("default", null);
                    StyleConstants.setFontFamily(defaultStyle, "Monospaced");
                    StyleConstants.setFontSize(defaultStyle, 12);
                    
                    Style stressedStyle = resultPane.addStyle("stressed", defaultStyle);
                    StyleConstants.setForeground(stressedStyle, new Color(0, 120, 0));
                    StyleConstants.setBold(stressedStyle, true);
                    
                    Style unstressedStyle = resultPane.addStyle("unstressed", defaultStyle);
                    StyleConstants.setForeground(unstressedStyle, new Color(100, 100, 100));
                    
                    Style meterStyle = resultPane.addStyle("meter", defaultStyle);
                    StyleConstants.setForeground(meterStyle, new Color(80, 80, 160));
                    StyleConstants.setItalic(meterStyle, true);
                    
                    for (ScansionEngine.LineScansion line : result.lines) {
                        // Original line
                        doc.insertString(doc.getLength(), line.originalLine + "\n", defaultStyle);
                        
                        // Scansion notation
                        for (char c : line.scansionNotation.toCharArray()) {
                            Style style = c == '/' ? stressedStyle : unstressedStyle;
                            doc.insertString(doc.getLength(), String.valueOf(c) + " ", style);
                        }
                        doc.insertString(doc.getLength(), "\n", defaultStyle);
                        
                        // Meter info
                        doc.insertString(doc.getLength(), "  " + line.meterName + 
                                (line.caesuraPosition >= 0 ? " (caesura at " + (line.caesuraPosition + 1) + ")" : "") +
                                "\n\n", meterStyle);
                    }
                } catch (BadLocationException ignored) {}
            });
        }
    }
    
    /**
     * Sound devices panel.
     */
    private static class SoundDevicesPanel extends JPanel {
        private final JTextPane resultPane;
        private final JLabel summaryLabel;
        private final SoundDevicesEngine engine = new SoundDevicesEngine();
        
        SoundDevicesPanel() {
            super(new BorderLayout(0, 8));
            setOpaque(false);
            setBorder(new EmptyBorder(8, 4, 4, 4));
            
            summaryLabel = new JLabel("Sound devices: -");
            summaryLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
            add(summaryLabel, BorderLayout.NORTH);
            
            resultPane = new JTextPane();
            resultPane.setEditable(false);
            resultPane.setFont(new Font("SansSerif", Font.PLAIN, 12));
            resultPane.setBorder(new EmptyBorder(8, 8, 8, 8));
            
            JScrollPane scroll = createScrollPane(resultPane);
            add(scroll, BorderLayout.CENTER);
        }
        
        void analyze(String text) {
            SoundDevicesEngine.SoundAnalysis result = engine.analyzePoem(text);
            
            SwingUtilities.invokeLater(() -> {
                int total = result.devices.size();
                summaryLabel.setText(String.format("Sound Devices Found: %d | Density: %.1f%%", 
                        total, result.overallSoundDensity * 100));
                
                StyledDocument doc = resultPane.getStyledDocument();
                try {
                    doc.remove(0, doc.getLength());
                    
                    Style defaultStyle = resultPane.addStyle("default", null);
                    Style headerStyle = resultPane.addStyle("header", defaultStyle);
                    StyleConstants.setBold(headerStyle, true);
                    StyleConstants.setFontSize(headerStyle, 13);
                    
                    Style allitStyle = resultPane.addStyle("allit", defaultStyle);
                    StyleConstants.setForeground(allitStyle, new Color(180, 80, 80));
                    
                    Style assonStyle = resultPane.addStyle("asson", defaultStyle);
                    StyleConstants.setForeground(assonStyle, new Color(80, 120, 180));
                    
                    Style consStyle = resultPane.addStyle("cons", defaultStyle);
                    StyleConstants.setForeground(consStyle, new Color(80, 150, 80));
                    
                    Style sibilStyle = resultPane.addStyle("sibil", defaultStyle);
                    StyleConstants.setForeground(sibilStyle, new Color(150, 100, 150));
                    
                    // Group by type
                    Map<SoundDevicesEngine.SoundDevice.Type, List<SoundDevicesEngine.SoundDevice>> byType = new LinkedHashMap<>();
                    for (SoundDevicesEngine.SoundDevice device : result.devices) {
                        byType.computeIfAbsent(device.type, k -> new ArrayList<>()).add(device);
                    }
                    
                    for (Map.Entry<SoundDevicesEngine.SoundDevice.Type, List<SoundDevicesEngine.SoundDevice>> entry : byType.entrySet()) {
                        SoundDevicesEngine.SoundDevice.Type type = entry.getKey();
                        List<SoundDevicesEngine.SoundDevice> devices = entry.getValue();
                        
                        doc.insertString(doc.getLength(), "\n" + type.name + " (" + devices.size() + ")\n", headerStyle);
                        
                        Style style = switch (type) {
                            case ALLITERATION -> allitStyle;
                            case ASSONANCE -> assonStyle;
                            case CONSONANCE -> consStyle;
                            case SIBILANCE -> sibilStyle;
                            default -> defaultStyle;
                        };
                        
                        for (SoundDevicesEngine.SoundDevice device : devices) {
                            String words = device.matches.stream()
                                    .map(m -> m.word)
                                    .distinct()
                                    .limit(6)
                                    .reduce((a, b) -> a + ", " + b)
                                    .orElse("");
                            doc.insertString(doc.getLength(), 
                                    String.format("  Line %d: [%s] %s (%.0f%%)\n", 
                                            device.lineNumber + 1, device.sound, words, device.strength * 100), 
                                    style);
                        }
                    }
                    
                    if (!result.dominantSounds.isEmpty()) {
                        doc.insertString(doc.getLength(), "\nDominant Sounds: " + 
                                String.join(", ", result.dominantSounds) + "\n", defaultStyle);
                    }
                } catch (BadLocationException ignored) {}
            });
        }
    }
    
    /**
     * Vocabulary analysis panel.
     */
    private static class VocabularyPanel extends JPanel {
        private final JTextPane resultPane;
        private final JProgressBar richnessBar;
        private final JLabel statsLabel;
        private final VocabularyAnalyzer analyzer = new VocabularyAnalyzer();
        
        VocabularyPanel() {
            super(new BorderLayout(0, 8));
            setOpaque(false);
            setBorder(new EmptyBorder(8, 4, 4, 4));
            
            // Top panel with richness meter
            JPanel top = new JPanel(new BorderLayout(8, 4));
            top.setOpaque(false);
            
            JLabel richnessLabel = new JLabel("Vocabulary Richness:");
            richnessLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
            
            richnessBar = new JProgressBar(0, 100);
            richnessBar.setStringPainted(true);
            richnessBar.setPreferredSize(new Dimension(150, 20));
            
            statsLabel = new JLabel("-");
            statsLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
            statsLabel.setForeground(new Color(100, 100, 100));
            
            top.add(richnessLabel, BorderLayout.WEST);
            top.add(richnessBar, BorderLayout.CENTER);
            top.add(statsLabel, BorderLayout.SOUTH);
            add(top, BorderLayout.NORTH);
            
            resultPane = new JTextPane();
            resultPane.setEditable(false);
            resultPane.setFont(new Font("SansSerif", Font.PLAIN, 12));
            resultPane.setBorder(new EmptyBorder(8, 8, 8, 8));
            
            JScrollPane scroll = createScrollPane(resultPane);
            add(scroll, BorderLayout.CENTER);
        }
        
        void analyze(String text) {
            VocabularyAnalyzer.VocabularyAnalysis result = analyzer.analyze(text);
            
            SwingUtilities.invokeLater(() -> {
                richnessBar.setValue((int) result.vocabularyRichnessScore);
                richnessBar.setString(String.format("%.0f%%", result.vocabularyRichnessScore));
                
                // Color based on score
                Color barColor;
                if (result.vocabularyRichnessScore >= 70) barColor = new Color(80, 160, 80);
                else if (result.vocabularyRichnessScore >= 40) barColor = new Color(180, 150, 50);
                else barColor = new Color(180, 80, 80);
                richnessBar.setForeground(barColor);
                
                statsLabel.setText(String.format("Words: %d | Unique: %d | TTR: %.2f | Hapax: %d",
                        result.totalWords, result.uniqueWords, result.typeTokenRatio, result.hapaxLegomena));
                
                StyledDocument doc = resultPane.getStyledDocument();
                try {
                    doc.remove(0, doc.getLength());
                    
                    Style defaultStyle = resultPane.addStyle("default", null);
                    Style headerStyle = resultPane.addStyle("header", defaultStyle);
                    StyleConstants.setBold(headerStyle, true);
                    
                    Style keywordStyle = resultPane.addStyle("keyword", defaultStyle);
                    StyleConstants.setForeground(keywordStyle, new Color(80, 80, 160));
                    StyleConstants.setBold(keywordStyle, true);
                    
                    // Readability
                    doc.insertString(doc.getLength(), "Readability\n", headerStyle);
                    doc.insertString(doc.getLength(), String.format("  Flesch Reading Ease: %.1f%s\n",
                            result.fleschReadingEase, getReadabilityLabel(result.fleschReadingEase)), defaultStyle);
                    doc.insertString(doc.getLength(), String.format("  Flesch-Kincaid Grade: %.1f\n\n",
                            result.fleschKincaidGrade), defaultStyle);
                    doc.insertString(doc.getLength(), String.format("  Gunning Fog: %.1f\n", result.gunningFog), defaultStyle);
                    doc.insertString(doc.getLength(), String.format("  SMOG: %.1f\n", result.smogIndex), defaultStyle);
                    doc.insertString(doc.getLength(), String.format("  Coleman-Liau: %.1f\n", result.colemanLiauIndex), defaultStyle);
                    doc.insertString(doc.getLength(), String.format("  ARI: %.1f\n\n", result.automatedReadabilityIndex), defaultStyle);
                    
                    doc.insertString(doc.getLength(), "Lexical Diversity\n", headerStyle);
                    doc.insertString(doc.getLength(), String.format("  MATTR (50): %.3f\n", result.mattr), defaultStyle);
                    doc.insertString(doc.getLength(), String.format("  MTLD: %.1f\n", result.mtld), defaultStyle);
                    doc.insertString(doc.getLength(), String.format("  Yule's K: %.1f\n", result.yulesK), defaultStyle);
                    doc.insertString(doc.getLength(), String.format("  Simpson's D: %.3f\n\n", result.simpsonsD), defaultStyle);
                    
                    doc.insertString(doc.getLength(), "Lexical Sophistication\n", headerStyle);
                    doc.insertString(doc.getLength(), String.format("  Avg Syllables/Word: %.2f\n", result.avgSyllablesPerWord), defaultStyle);
                    doc.insertString(doc.getLength(), String.format("  Polysyllabic Ratio: %.1f%%\n", result.polysyllabicRatio * 100), defaultStyle);
                    doc.insertString(doc.getLength(), String.format("  Sophistication: %.1f%%\n\n", result.lexicalSophistication * 100), defaultStyle);
                    
                    // Keywords
                    if (!result.keywords.isEmpty()) {
                        doc.insertString(doc.getLength(), "Keywords\n", headerStyle);
                        doc.insertString(doc.getLength(), "  " + String.join(", ", result.keywords) + "\n\n", keywordStyle);
                    }
                    
                    // Top words
                    if (!result.topWords.isEmpty()) {
                        doc.insertString(doc.getLength(), "Most Frequent Words\n", headerStyle);
                        for (int i = 0; i < Math.min(10, result.topWords.size()); i++) {
                            VocabularyAnalyzer.WordFrequency wf = result.topWords.get(i);
                            doc.insertString(doc.getLength(), String.format("  %d. %s (%d, %.1f%%) - %s\n",
                                    i + 1, wf.word, wf.count, wf.percentage, wf.partOfSpeech), defaultStyle);
                        }
                        doc.insertString(doc.getLength(), "\n", defaultStyle);
                    }
                    
                    // POS distribution
                    if (!result.posDistribution.isEmpty()) {
                        doc.insertString(doc.getLength(), "Part of Speech Distribution\n", headerStyle);
                        for (Map.Entry<String, Integer> e : result.posDistribution.entrySet()) {
                            double pct = (double) e.getValue() / result.totalWords * 100;
                            doc.insertString(doc.getLength(), String.format("  %s: %d (%.1f%%)\n",
                                    e.getKey(), e.getValue(), pct), defaultStyle);
                        }
                    }
                } catch (BadLocationException ignored) {}
            });
        }
        
        private String getReadabilityLabel(double score) {
            if (score >= 90) return " (Very Easy)";
            if (score >= 80) return " (Easy)";
            if (score >= 70) return " (Fairly Easy)";
            if (score >= 60) return " (Standard)";
            if (score >= 50) return " (Fairly Difficult)";
            if (score >= 30) return " (Difficult)";
            return " (Very Difficult)";
        }
    }
    
    /**
     * Line break suggestions panel.
     */
    private static class LineBreakPanel extends JPanel {
        private final JTextPane resultPane;
        private final JComboBox<String> presetCombo;
        private final JSpinner targetSpinner;
        private final LineBreakEngine engine = new LineBreakEngine();
        private String currentText = "";
        
        LineBreakPanel() {
            super(new BorderLayout(0, 8));
            setOpaque(false);
            setBorder(new EmptyBorder(8, 4, 4, 4));
            
            // Controls
            JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
            controls.setOpaque(false);
            
            controls.add(new JLabel("Preset:"));
            presetCombo = new JComboBox<>(new String[]{"Default", "Free Verse", "Iambic Pentameter", "Haiku"});
            presetCombo.setFocusable(false);
            presetCombo.addActionListener(e -> reanalyze());
            controls.add(presetCombo);
            
            controls.add(new JLabel("Target:"));
            targetSpinner = new JSpinner(new SpinnerNumberModel(10, 0, 20, 1));
            ((JSpinner.DefaultEditor) targetSpinner.getEditor()).getTextField().setColumns(2);
            targetSpinner.addChangeListener(e -> reanalyze());
            controls.add(targetSpinner);
            
            JButton applyBtn = new JButton("Apply Breaks");
            applyBtn.setFocusable(false);
            applyBtn.setToolTipText("Copy reformatted text to clipboard");
            applyBtn.addActionListener(e -> copyToClipboard());
            controls.add(applyBtn);
            
            add(controls, BorderLayout.NORTH);
            
            resultPane = new JTextPane();
            resultPane.setEditable(false);
            resultPane.setFont(new Font("Monospaced", Font.PLAIN, 12));
            resultPane.setBorder(new EmptyBorder(8, 8, 8, 8));
            
            JScrollPane scroll = createScrollPane(resultPane);
            add(scroll, BorderLayout.CENTER);
        }
        
        void analyze(String text) {
            currentText = text;
            reanalyze();
        }
        
        private void reanalyze() {
            if (currentText == null || currentText.isBlank()) return;
            
            LineBreakEngine.BreakConfig config = getConfig();
            LineBreakEngine.BreakAnalysis result = engine.analyze(currentText, config);
            
            SwingUtilities.invokeLater(() -> {
                StyledDocument doc = resultPane.getStyledDocument();
                try {
                    doc.remove(0, doc.getLength());
                    
                    Style defaultStyle = resultPane.addStyle("default", null);
                    Style headerStyle = resultPane.addStyle("header", defaultStyle);
                    StyleConstants.setBold(headerStyle, true);
                    
                    Style reformatStyle = resultPane.addStyle("reformat", defaultStyle);
                    StyleConstants.setForeground(reformatStyle, new Color(60, 60, 120));
                    
                    doc.insertString(doc.getLength(), String.format("Target: %s | Lines: %d | Stanzas: %d\n\n",
                            result.meterTarget, result.suggestedLineCount, result.suggestedStanzaCount), headerStyle);
                    
                    doc.insertString(doc.getLength(), "Reformatted Text:\n", headerStyle);
                    doc.insertString(doc.getLength(), "─".repeat(40) + "\n", defaultStyle);
                    doc.insertString(doc.getLength(), result.reformattedText + "\n", reformatStyle);
                    doc.insertString(doc.getLength(), "─".repeat(40) + "\n\n", defaultStyle);
                    
                    // Enjambment detection on original
                    List<LineBreakEngine.EnjambmentInfo> enjambments = engine.detectEnjambment(currentText);
                    if (!enjambments.isEmpty()) {
                        doc.insertString(doc.getLength(), "Enjambment Detected:\n", headerStyle);
                        for (LineBreakEngine.EnjambmentInfo e : enjambments) {
                            doc.insertString(doc.getLength(), String.format("  Line %d: %s\n",
                                    e.lineIndex + 1, e.description), defaultStyle);
                        }
                    }
                } catch (BadLocationException ignored) {}
            });
        }
        
        private LineBreakEngine.BreakConfig getConfig() {
            String preset = (String) presetCombo.getSelectedItem();
            int target = (Integer) targetSpinner.getValue();
            
            return switch (preset) {
                case "Free Verse" -> LineBreakEngine.BreakConfig.freeVerse();
                case "Iambic Pentameter" -> LineBreakEngine.BreakConfig.iambicPentameter();
                case "Haiku" -> LineBreakEngine.BreakConfig.haiku();
                default -> new LineBreakEngine.BreakConfig(target, 6, 14, true, 4);
            };
        }
        
        private void copyToClipboard() {
            String text = resultPane.getText();
            // Extract just the reformatted text
            int start = text.indexOf("─".repeat(40)) + 41;
            int end = text.lastIndexOf("─".repeat(40));
            if (start > 0 && end > start) {
                String reformatted = text.substring(start, end).trim();
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new java.awt.datatransfer.StringSelection(reformatted), null);
            }
        }
    }
    
    /**
     * Thematic analysis panel.
     */
    private static class ThematicPanel extends JPanel {
        private final JTextPane resultPane;
        private final JLabel themeLabel;
        private final JLabel moodLabel;
        private final ThematicAnalyzer analyzer = new ThematicAnalyzer();
        
        ThematicPanel() {
            super(new BorderLayout(0, 8));
            setOpaque(false);
            setBorder(new EmptyBorder(8, 4, 4, 4));
            
            // Summary
            JPanel summary = new JPanel(new GridLayout(2, 1, 2, 2));
            summary.setOpaque(false);
            themeLabel = new JLabel("Dominant Theme: -");
            themeLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
            moodLabel = new JLabel("Dominant Mood: -");
            moodLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
            moodLabel.setForeground(new Color(100, 100, 100));
            summary.add(themeLabel);
            summary.add(moodLabel);
            add(summary, BorderLayout.NORTH);
            
            resultPane = new JTextPane();
            resultPane.setEditable(false);
            resultPane.setFont(new Font("SansSerif", Font.PLAIN, 12));
            resultPane.setBorder(new EmptyBorder(8, 8, 8, 8));
            
            JScrollPane scroll = createScrollPane(resultPane);
            add(scroll, BorderLayout.CENTER);
        }
        
        void analyze(String text) {
            ThematicAnalyzer.ThematicAnalysis result = analyzer.analyze(text);
            
            SwingUtilities.invokeLater(() -> {
                themeLabel.setText("Dominant Theme: " + result.dominantTheme);
                moodLabel.setText("Dominant Mood: " + result.dominantMood);
                
                StyledDocument doc = resultPane.getStyledDocument();
                try {
                    doc.remove(0, doc.getLength());
                    
                    Style defaultStyle = resultPane.addStyle("default", null);
                    Style headerStyle = resultPane.addStyle("header", defaultStyle);
                    StyleConstants.setBold(headerStyle, true);
                    StyleConstants.setFontSize(headerStyle, 13);
                    
                    Style themeStyle = resultPane.addStyle("theme", defaultStyle);
                    StyleConstants.setForeground(themeStyle, new Color(120, 80, 120));
                    
                    Style symbolStyle = resultPane.addStyle("symbol", defaultStyle);
                    StyleConstants.setForeground(symbolStyle, new Color(80, 120, 80));
                    StyleConstants.setItalic(symbolStyle, true);
                    
                    // Themes
                    if (!result.themes.isEmpty()) {
                        doc.insertString(doc.getLength(), "Themes\n", headerStyle);
                        for (ThematicAnalyzer.ThemeMatch theme : result.themes.stream().limit(5).toList()) {
                            String keywords = String.join(", ", theme.matchedWords.stream().limit(4).toList());
                            doc.insertString(doc.getLength(), String.format("  • %s (%.0f%%) - %s\n",
                                    theme.theme, theme.strength * 100, keywords), themeStyle);
                        }
                        doc.insertString(doc.getLength(), "\n", defaultStyle);
                    }
                    
                    // Moods
                    if (!result.moods.isEmpty()) {
                        doc.insertString(doc.getLength(), "Mood/Tone\n", headerStyle);
                        for (ThematicAnalyzer.MoodMatch mood : result.moods.stream().limit(3).toList()) {
                            doc.insertString(doc.getLength(), String.format("  • %s (%.0f%%)\n",
                                    mood.mood, mood.strength * 100), defaultStyle);
                        }
                        doc.insertString(doc.getLength(), "\n", defaultStyle);
                    }
                    
                    // Imagery
                    if (!result.imagery.isEmpty()) {
                        doc.insertString(doc.getLength(), "Imagery Types\n", headerStyle);
                        for (ThematicAnalyzer.ImageryMatch img : result.imagery.stream().limit(4).toList()) {
                            doc.insertString(doc.getLength(), String.format("  • %s (%.0f%%)\n",
                                    img.type, img.strength * 100), defaultStyle);
                        }
                        doc.insertString(doc.getLength(), "\n", defaultStyle);
                    }
                    
                    // Symbols
                    if (!result.symbols.isEmpty()) {
                        doc.insertString(doc.getLength(), "Symbols\n", headerStyle);
                        for (ThematicAnalyzer.SymbolMatch sym : result.symbols.stream().limit(5).toList()) {
                            doc.insertString(doc.getLength(), String.format("  • %s (%dx) - %s\n",
                                    sym.symbol, sym.occurrences, sym.potentialMeaning), symbolStyle);
                        }
                        doc.insertString(doc.getLength(), "\n", defaultStyle);
                    }
                    
                    // Emotional Arc
                    doc.insertString(doc.getLength(), "Emotional Arc\n", headerStyle);
                    doc.insertString(doc.getLength(), "  Pattern: " + result.emotionalArc.pattern + "\n", defaultStyle);
                    doc.insertString(doc.getLength(), "  " + result.emotionalArc.description + "\n", defaultStyle);
                    
                    // Keyword clusters
                    if (!result.clusters.isEmpty()) {
                        doc.insertString(doc.getLength(), "\nKeyword Clusters\n", headerStyle);
                        for (ThematicAnalyzer.KeywordCluster cluster : result.clusters) {
                            doc.insertString(doc.getLength(), String.format("  %s: %s\n",
                                    cluster.label, String.join(", ", cluster.keywords)), defaultStyle);
                        }
                    }
                } catch (BadLocationException ignored) {}
            });
        }
    }
    
    // ========== Utility Methods ==========
    
    private static JScrollPane createScrollPane(JComponent content) {
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        
        JScrollBar vbar = scroll.getVerticalScrollBar();
        if (vbar != null) {
            vbar.setUI(new ModernScrollBarUI());
            vbar.setUnitIncrement(16);
        }
        JScrollBar hbar = scroll.getHorizontalScrollBar();
        if (hbar != null) {
            hbar.setUI(new ModernScrollBarUI());
        }
        
        return scroll;
    }
}
