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
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.basic.BasicTabbedPaneUI;

import main.core.poetry.PoetryAnalyzer;
import main.core.poetry.PoetryAnalyzer.FootType;
import main.core.poetry.PoetryAnalyzer.LexicalAnalysis;
import main.core.poetry.PoetryAnalyzer.LineScansion;
import main.core.poetry.PoetryAnalyzer.PhoneticAnalysis;
import main.core.poetry.PoetryAnalyzer.PoemAnalysis;
import main.core.poetry.PoetryAnalyzer.ProsodicAnalysis;
import main.core.poetry.PoetryAnalyzer.SentimentAnalysis;
import main.core.poetry.PoetryAnalyzer.SoundDevice;
import main.core.poetry.PoetryAnalyzer.StructuralAnalysis;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.scrollbar.AeroScrollBarUI;
import main.ui.theme.aero.AeroPainters;
import main.ui.theme.aero.AeroTheme;

/**
 * Panel displaying comprehensive poetry analysis results.
 * Shows prosodic, phonetic, structural, lexical, and sentiment analysis.
 * 
 * Features:
 * - Real-time analysis with background processing
 * - Responsive UI with loading states
 * - Error handling and display
 * - Comprehensive analysis results
 * 
 * Uses PoetryAnalyzer for core analysis.
 * 
 * @author S1mplector
 * @version 1.0.0
 */
public class PoetryAnalysisPanel extends JPanel {
    
    private static final Color BG_COLOR = new Color(255, 255, 255, 0);
    private static final Color SECTION_BG = new Color(255, 255, 255, 170);
    private static final Color CARD_BG = new Color(255, 255, 255, 190);
    private static final Color ACCENT = AeroTheme.AERO_BLUE;
    private static final Color TEXT_PRIMARY = AeroTheme.TEXT_PRIMARY;
    private static final Color TEXT_SECONDARY = new Color(95, 95, 95);
    private static final Color BORDER_SOFT = new Color(190, 190, 205, 120);
    private static final Font TITLE_FONT = AeroTheme.defaultBoldFont(18f);
    private static final Font SECTION_FONT = AeroTheme.defaultBoldFont(12f);
    private static final Font LABEL_FONT = AeroTheme.defaultFont().deriveFont(12f);
    private static final Font MONO_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 11);
    
    private final JTabbedPane tabbedPane;
    private final AeroTabStrip tabStrip;
    private PoemAnalysis currentAnalysis;
    private SwingWorker<PoemAnalysis, Void> analysisWorker;
    
    public PoetryAnalysisPanel() {
        setLayout(new BorderLayout());
        setOpaque(false);
        
        FrostedGlassPanel chrome = new FrostedGlassPanel(new BorderLayout(8, 10), 18);
        chrome.setBorder(new EmptyBorder(12, 12, 12, 12));
        add(chrome, BorderLayout.CENTER);
        
        // Header
        JPanel headerWrap = new JPanel();
        headerWrap.setOpaque(false);
        headerWrap.setLayout(new BoxLayout(headerWrap, BoxLayout.Y_AXIS));

        JLabel header = new JLabel("Poetry Analysis");
        header.setFont(TITLE_FONT);
        header.setForeground(ACCENT);
        header.setBorder(new EmptyBorder(2, 4, 6, 0));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerWrap.add(header);

        tabStrip = new AeroTabStrip();
        tabStrip.setVisible(false);
        JPanel tabRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        tabRow.setOpaque(false);
        tabRow.add(tabStrip);
        tabRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerWrap.add(tabRow);
        chrome.add(headerWrap, BorderLayout.NORTH);

        // Tabbed pane for analysis sections (tabs rendered by Aero tab strip)
        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setFont(LABEL_FONT);
        tabbedPane.setOpaque(false);
        tabbedPane.setBorder(null);
        tabbedPane.setUI(new BasicTabbedPaneUI() {
            @Override
            protected int calculateTabAreaHeight(int tabPlacement, int runCount, int maxTabHeight) {
                return 0;
            }
            @Override
            protected void paintTabArea(Graphics g, int tabPlacement, int selectedIndex) {}
            @Override
            protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {}
        });
        tabStrip.bind(tabbedPane);
        chrome.add(tabbedPane, BorderLayout.CENTER);
        
        showEmptyState();
    }
    
    /**
     * Analyzes and displays results for a poem.
     */
    public void analyzePoem(String title, String text) {
        analyzePoemAsync(title, text);
    }

    /**
     * Analyze in the background so the dialog stays responsive.
     */
    public void analyzePoemAsync(String title, String text) {
        if (analysisWorker != null) {
            analysisWorker.cancel(true);
            analysisWorker = null;
        }
        if (text == null || text.isBlank()) {
            showEmptyState();
            return;
        }
        showLoadingState();
        analysisWorker = new SwingWorker<>() {
            @Override
            protected PoemAnalysis doInBackground() {
                return PoetryAnalyzer.analyze(title, text);
            }

            @Override
            protected void done() {
                if (isCancelled()) return;
                try {
                    currentAnalysis = get();
                    displayAnalysis();
                } catch (Exception ex) {
                    showErrorState(ex);
                } finally {
                    analysisWorker = null;
                }
            }
        };
        analysisWorker.execute();
    }
    
    /**
     * Displays the current analysis in tabs.
     */
    private void displayAnalysis() {
        tabbedPane.removeAll();
        
        if (currentAnalysis == null) {
            showEmptyState();
            return;
        }
        tabStrip.setVisible(true);
        
        // Overview tab
        tabbedPane.addTab("Overview", createIcon("📊"), createOverviewPanel(), "Summary of analysis");
        
        // Prosody tab
        tabbedPane.addTab("Prosody", createIcon("🎵"), createProsodyPanel(), "Meter and rhythm analysis");
        
        // Phonetics tab
        tabbedPane.addTab("Phonetics", createIcon("🔊"), createPhoneticsPanel(), "Sound devices and rhyme");
        
        // Structure tab
        tabbedPane.addTab("Structure", createIcon("📐"), createStructurePanel(), "Form and organization");
        
        // Lexical tab
        tabbedPane.addTab("Lexical", createIcon("📝"), createLexicalPanel(), "Vocabulary analysis");
        
        // Sentiment tab
        tabbedPane.addTab("Sentiment", createIcon("💭"), createSentimentPanel(), "Tone and emotion");
        
        // Scansion tab
        tabbedPane.addTab("Scansion", createIcon("📖"), createScansionPanel(), "Line-by-line scansion");

        tabStrip.setTabs(new String[] {
                "Overview",
                "Prosody",
                "Phonetics",
                "Structure",
                "Lexical",
                "Sentiment",
                "Scansion"
        });
        tabStrip.setSelectedIndex(0);
        
        revalidate();
        repaint();
    }
    
    private void showEmptyState() {
        tabbedPane.removeAll();
        tabStrip.setTabs(new String[0]);
        tabStrip.setVisible(false);
        JPanel empty = new JPanel(new GridBagLayout());
        empty.setOpaque(false);
        JLabel msg = new JLabel("<html><center><font size='4' color='gray'>No poem to analyze</font><br><br>" +
            "<font size='3' color='#999'>Enter a poem and click Analyze</font></center></html>");
        msg.setHorizontalAlignment(SwingConstants.CENTER);
        empty.add(msg);
        tabbedPane.addTab("Analysis", empty);
    }

    private void showLoadingState() {
        tabbedPane.removeAll();
        tabStrip.setTabs(new String[0]);
        tabStrip.setVisible(false);
        JPanel loading = new JPanel(new GridBagLayout());
        loading.setOpaque(false);
        JLabel msg = new JLabel("<html><center><font size='4' color='gray'>Analyzing...</font><br><br>" +
                "<font size='3' color='#999'>Crunching meter, rhyme, and structure</font></center></html>");
        msg.setHorizontalAlignment(SwingConstants.CENTER);
        loading.add(msg);
        tabbedPane.addTab("Analysis", loading);
        SwingUtilities.invokeLater(this::repaint);
    }

    private void showErrorState(Exception ex) {
        tabbedPane.removeAll();
        tabStrip.setTabs(new String[0]);
        tabStrip.setVisible(false);
        JPanel error = new JPanel(new GridBagLayout());
        error.setOpaque(false);
        String message = (ex == null || ex.getMessage() == null) ? "Unknown error" : ex.getMessage();
        JLabel msg = new JLabel("<html><center><font size='4' color='gray'>Analysis failed</font><br><br>" +
                "<font size='3' color='#999'>" + escapeHtml(message) + "</font></center></html>");
        msg.setHorizontalAlignment(SwingConstants.CENTER);
        error.add(msg);
        tabbedPane.addTab("Analysis", error);
        SwingUtilities.invokeLater(this::repaint);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // OVERVIEW TAB
    // ═══════════════════════════════════════════════════════════════════════════
    
    private JComponent createOverviewPanel() {
        JPanel panel = createScrollablePanel();
        
        // Title
        if (currentAnalysis.title != null && !currentAnalysis.title.isEmpty()) {
            addSection(panel, "Title", currentAnalysis.title);
        }
        
        // Quick stats grid
        JPanel stats = new JPanel(new GridLayout(3, 4, 15, 10));
        stats.setOpaque(false);
        
        addStatCard(stats, "Lines", String.valueOf(currentAnalysis.structure.lineCount));
        addStatCard(stats, "Stanzas", String.valueOf(currentAnalysis.structure.stanzaCount));
        addStatCard(stats, "Words", String.valueOf(currentAnalysis.lexical.totalWords));
        addStatCard(stats, "Unique Words", String.valueOf(currentAnalysis.lexical.uniqueWords));
        
        addStatCard(stats, "Meter", currentAnalysis.prosody.meterName);
        addStatCard(stats, "Form", currentAnalysis.structure.form);
        addStatCard(stats, "Rhyme Scheme", formatRhymeScheme(currentAnalysis.phonetics.rhymeScheme));
        addStatCard(stats, "Tone", currentAnalysis.sentiment.tone);
        
        addStatCard(stats, "Avg Syllables/Line", String.format("%.1f", currentAnalysis.prosody.avgSyllables));
        addStatCard(stats, "Metrical Regularity", String.format("%.0f%%", currentAnalysis.prosody.regularity * 100));
        addStatCard(stats, "Type-Token Ratio", String.format("%.3f", currentAnalysis.lexical.typeTokenRatio));
        addStatCard(stats, "Readability", String.format("%.1f", currentAnalysis.lexical.readability));
        
        panel.add(createSectionPanel("Quick Statistics", stats));
        
        // Key findings
        JTextArea findings = new JTextArea();
        findings.setFont(LABEL_FONT);
        findings.setEditable(false);
        findings.setLineWrap(true);
        findings.setWrapStyleWord(true);
        findings.setBackground(SECTION_BG);
        findings.setText(generateKeyFindings());
        
        panel.add(createSectionPanel("Key Findings", findings));
        panel.add(Box.createVerticalGlue());
        
        return wrapInScroll(panel);
    }
    
    private String generateKeyFindings() {
        StringBuilder sb = new StringBuilder();
        
        // Form
        sb.append("• Form: This poem appears to be ").append(currentAnalysis.structure.form.toLowerCase());
        if (currentAnalysis.structure.hasRefrain) {
            sb.append(" with a refrain");
        }
        sb.append(".\n\n");
        
        // Meter
        sb.append("• Meter: The dominant meter is ").append(currentAnalysis.prosody.meterName.toLowerCase());
        sb.append(" with ").append(String.format("%.0f%%", currentAnalysis.prosody.regularity * 100));
        sb.append(" regularity.\n\n");
        
        // Rhyme
        if (!currentAnalysis.phonetics.rhymeScheme.isEmpty()) {
            sb.append("• Rhyme: The poem follows a ");
            String scheme = currentAnalysis.phonetics.rhymeScheme;
            if (scheme.length() <= 8) sb.append(scheme);
            else sb.append(scheme.substring(0, 8) + "...");
            sb.append(" rhyme scheme.\n\n");
        }
        
        // Sound devices
        int devices = currentAnalysis.phonetics.alliterations.size() + 
                     currentAnalysis.phonetics.assonances.size();
        if (devices > 0) {
            sb.append("• Sound Devices: Found ").append(devices);
            sb.append(" instances of alliteration and assonance.\n\n");
        }
        
        // Tone
        sb.append("• Tone: The overall tone is ").append(currentAnalysis.sentiment.tone.toLowerCase());
        sb.append(" with ").append(String.format("%.0f%%", currentAnalysis.sentiment.intensity * 100));
        sb.append(" emotional intensity.");
        
        return sb.toString();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // PROSODY TAB
    // ═══════════════════════════════════════════════════════════════════════════
    
    private JComponent createProsodyPanel() {
        JPanel panel = createScrollablePanel();
        ProsodicAnalysis p = currentAnalysis.prosody;
        
        // Meter info
        JPanel meterPanel = new JPanel(new GridLayout(0, 2, 10, 5));
        meterPanel.setOpaque(false);
        
        addLabelValue(meterPanel, "Dominant Meter:", p.meterName);
        addLabelValue(meterPanel, "Dominant Foot:", p.dominantFoot != null ? p.dominantFoot.name : "N/A");
        addLabelValue(meterPanel, "Foot Symbol:", p.dominantFoot != null ? p.dominantFoot.symbol : "N/A");
        addLabelValue(meterPanel, "Metrical Regularity:", String.format("%.1f%%", p.regularity * 100));
        addLabelValue(meterPanel, "Avg Syllables/Line:", String.format("%.1f", p.avgSyllables));
        
        panel.add(createSectionPanel("Meter Analysis", meterPanel));
        
        // Foot distribution
        if (!p.footDistribution.isEmpty()) {
            JPanel distPanel = new JPanel(new GridLayout(0, 2, 10, 5));
            distPanel.setOpaque(false);
            
            int total = p.footDistribution.values().stream().mapToInt(i -> i).sum();
            for (var entry : p.footDistribution.entrySet()) {
                String pct = String.format("%.1f%%", (double) entry.getValue() / total * 100);
                addLabelValue(distPanel, entry.getKey().name + ":", entry.getValue() + " (" + pct + ")");
            }
            
            panel.add(createSectionPanel("Foot Distribution", distPanel));
        }
        
        // Foot legend
        JPanel legend = new JPanel(new GridLayout(0, 3, 15, 5));
        legend.setOpaque(false);
        for (FootType ft : FootType.values()) {
            JLabel l = new JLabel(ft.name + " " + ft.symbol);
            l.setFont(MONO_FONT);
            legend.add(l);
        }
        panel.add(createSectionPanel("Foot Types Legend", legend));
        
        panel.add(Box.createVerticalGlue());
        return wrapInScroll(panel);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // PHONETICS TAB
    // ═══════════════════════════════════════════════════════════════════════════
    
    private JComponent createPhoneticsPanel() {
        JPanel panel = createScrollablePanel();
        PhoneticAnalysis ph = currentAnalysis.phonetics;
        
        // Rhyme scheme
        JPanel rhymePanel = new JPanel(new BorderLayout(10, 5));
        rhymePanel.setOpaque(false);
        
        JLabel schemeLabel = new JLabel("Rhyme Scheme: ");
        schemeLabel.setFont(LABEL_FONT);
        JLabel schemeValue = new JLabel(formatRhymeScheme(ph.rhymeScheme));
        schemeValue.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        schemeValue.setForeground(ACCENT);
        
        JPanel schemeRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        schemeRow.setOpaque(false);
        schemeRow.add(schemeLabel);
        schemeRow.add(schemeValue);
        rhymePanel.add(schemeRow, BorderLayout.NORTH);
        
        // Rhyme groups
        if (!ph.rhymeGroups.isEmpty()) {
            JTextArea groups = new JTextArea();
            groups.setFont(MONO_FONT);
            groups.setEditable(false);
            groups.setBackground(SECTION_BG);
            StringBuilder sb = new StringBuilder();
            for (var e : ph.rhymeGroups.entrySet()) {
                sb.append("Lines ").append(e.getValue()).append(" rhyme (").append(e.getKey()).append(")\n");
            }
            groups.setText(sb.toString());
            rhymePanel.add(groups, BorderLayout.CENTER);
        }
        
        panel.add(createSectionPanel("Rhyme Analysis", rhymePanel));
        
        // Sound devices
        JPanel soundPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        soundPanel.setOpaque(false);
        
        addSoundDeviceSection(soundPanel, "Alliteration", ph.alliterations);
        addSoundDeviceSection(soundPanel, "Assonance", ph.assonances);
        addSoundDeviceSection(soundPanel, "Consonance", ph.consonances);
        
        panel.add(createSectionPanel("Sound Devices", soundPanel));
        
        // Phonetic density
        JLabel density = new JLabel(String.format("Phonetic Device Density: %.2f per line", ph.density));
        density.setFont(LABEL_FONT);
        panel.add(createSectionPanel("Density", density));
        
        panel.add(Box.createVerticalGlue());
        return wrapInScroll(panel);
    }
    
    private void addSoundDeviceSection(JPanel panel, String name, List<SoundDevice> devices) {
        if (devices.isEmpty()) {
            panel.add(new JLabel(name + ": None detected"));
            return;
        }
        
        StringBuilder sb = new StringBuilder("<html><b>" + name + " (" + devices.size() + "):</b><br>");
        for (SoundDevice d : devices) {
            sb.append("Line ").append(d.line).append(": '").append(d.sound).append("' in ");
            sb.append(String.join(", ", d.words)).append("<br>");
        }
        sb.append("</html>");
        
        JLabel label = new JLabel(sb.toString());
        label.setFont(LABEL_FONT);
        panel.add(label);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // STRUCTURE TAB
    // ═══════════════════════════════════════════════════════════════════════════
    
    private JComponent createStructurePanel() {
        JPanel panel = createScrollablePanel();
        StructuralAnalysis s = currentAnalysis.structure;
        
        // Form info
        JPanel formPanel = new JPanel(new GridLayout(0, 2, 10, 5));
        formPanel.setOpaque(false);
        
        addLabelValue(formPanel, "Detected Form:", s.form);
        addLabelValue(formPanel, "Total Lines:", String.valueOf(s.lineCount));
        addLabelValue(formPanel, "Stanzas:", String.valueOf(s.stanzaCount));
        addLabelValue(formPanel, "End-Stopped Lines:", String.valueOf(s.endStopped));
        addLabelValue(formPanel, "Enjambed Lines:", String.valueOf(s.enjambment));
        
        panel.add(createSectionPanel("Form Analysis", formPanel));
        
        // Stanza structure
        if (!s.stanzaLengths.isEmpty()) {
            StringBuilder stanzaInfo = new StringBuilder();
            for (int i = 0; i < s.stanzaLengths.size(); i++) {
                stanzaInfo.append("Stanza ").append(i + 1).append(": ")
                         .append(s.stanzaLengths.get(i)).append(" lines\n");
            }
            JTextArea stanzaText = new JTextArea(stanzaInfo.toString());
            stanzaText.setFont(MONO_FONT);
            stanzaText.setEditable(false);
            stanzaText.setBackground(SECTION_BG);
            panel.add(createSectionPanel("Stanza Structure", stanzaText));
        }
        
        // Refrain
        if (s.hasRefrain && s.refrain != null) {
            JLabel refrainLabel = new JLabel("<html><b>Refrain detected:</b><br>\"" + s.refrain + "\"</html>");
            refrainLabel.setFont(LABEL_FONT);
            panel.add(createSectionPanel("Refrain", refrainLabel));
        }
        
        // Line lengths
        if (!s.syllableCounts.isEmpty()) {
            StringBuilder sylInfo = new StringBuilder();
            for (int i = 0; i < s.syllableCounts.size(); i++) {
                sylInfo.append(String.format("Line %2d: %2d syllables\n", i + 1, s.syllableCounts.get(i)));
            }
            JTextArea sylText = new JTextArea(sylInfo.toString());
            sylText.setFont(MONO_FONT);
            sylText.setEditable(false);
            sylText.setBackground(SECTION_BG);
            panel.add(createSectionPanel("Syllable Counts", new JScrollPane(sylText)));
        }
        
        panel.add(Box.createVerticalGlue());
        return wrapInScroll(panel);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // LEXICAL TAB
    // ═══════════════════════════════════════════════════════════════════════════
    
    private JComponent createLexicalPanel() {
        JPanel panel = createScrollablePanel();
        LexicalAnalysis l = currentAnalysis.lexical;
        
        // Stats
        JPanel statsPanel = new JPanel(new GridLayout(0, 2, 10, 5));
        statsPanel.setOpaque(false);
        
        addLabelValue(statsPanel, "Total Words:", String.valueOf(l.totalWords));
        addLabelValue(statsPanel, "Unique Words:", String.valueOf(l.uniqueWords));
        addLabelValue(statsPanel, "Type-Token Ratio:", String.format("%.4f", l.typeTokenRatio));
        addLabelValue(statsPanel, "Avg Word Length:", String.format("%.2f", l.avgWordLength));
        addLabelValue(statsPanel, "Polysyllabic Words:", String.valueOf(l.polysyllabic));
        addLabelValue(statsPanel, "Readability Score:", String.format("%.1f", l.readability));
        
        panel.add(createSectionPanel("Vocabulary Statistics", statsPanel));
        
        // Top words
        if (!l.topWords.isEmpty()) {
            StringBuilder topInfo = new StringBuilder();
            for (int i = 0; i < l.topWords.size(); i++) {
                String word = l.topWords.get(i);
                int count = l.wordFrequency.getOrDefault(word, 0);
                topInfo.append(String.format("%2d. %-15s (%d)\n", i + 1, word, count));
            }
            JTextArea topText = new JTextArea(topInfo.toString());
            topText.setFont(MONO_FONT);
            topText.setEditable(false);
            topText.setBackground(SECTION_BG);
            panel.add(createSectionPanel("Most Frequent Content Words", topText));
        }
        
        // POS distribution
        if (!l.posDistribution.isEmpty()) {
            StringBuilder posInfo = new StringBuilder();
            int total = l.posDistribution.values().stream().mapToInt(i -> i).sum();
            l.posDistribution.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(e -> {
                    double pct = (double) e.getValue() / total * 100;
                    posInfo.append(String.format("%-12s: %3d (%.1f%%)\n", e.getKey(), e.getValue(), pct));
                });
            JTextArea posText = new JTextArea(posInfo.toString());
            posText.setFont(MONO_FONT);
            posText.setEditable(false);
            posText.setBackground(SECTION_BG);
            panel.add(createSectionPanel("Parts of Speech Distribution", posText));
        }
        
        panel.add(Box.createVerticalGlue());
        return wrapInScroll(panel);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SENTIMENT TAB
    // ═══════════════════════════════════════════════════════════════════════════
    
    private JComponent createSentimentPanel() {
        JPanel panel = createScrollablePanel();
        SentimentAnalysis s = currentAnalysis.sentiment;
        
        // Tone
        JPanel tonePanel = new JPanel(new GridLayout(0, 2, 10, 5));
        tonePanel.setOpaque(false);
        
        addLabelValue(tonePanel, "Overall Tone:", s.tone);
        addLabelValue(tonePanel, "Emotional Intensity:", String.format("%.0f%%", s.intensity * 100));
        addLabelValue(tonePanel, "Positive Score:", String.format("%.1f%%", s.positive * 100));
        addLabelValue(tonePanel, "Negative Score:", String.format("%.1f%%", s.negative * 100));
        addLabelValue(tonePanel, "Neutral Score:", String.format("%.1f%%", s.neutral * 100));
        
        panel.add(createSectionPanel("Tone Analysis", tonePanel));
        
        // Sentiment bar
        JPanel barPanel = new JPanel(new BorderLayout(5, 5));
        barPanel.setOpaque(false);
        barPanel.add(new JLabel("Sentiment Distribution:"), BorderLayout.NORTH);
        barPanel.add(createSentimentBar(s.positive, s.negative, s.neutral), BorderLayout.CENTER);
        panel.add(createSectionPanel("Visual", barPanel));
        
        // Emotions
        if (!s.emotions.isEmpty()) {
            JLabel emotionsLabel = new JLabel("Detected Emotions: " + String.join(", ", s.emotions));
            emotionsLabel.setFont(LABEL_FONT);
            panel.add(createSectionPanel("Emotions", emotionsLabel));
        }
        
        // Imagery
        if (!s.imagery.isEmpty()) {
            JLabel imageryLabel = new JLabel("Imagery Types: " + String.join(", ", s.imagery));
            imageryLabel.setFont(LABEL_FONT);
            panel.add(createSectionPanel("Imagery", imageryLabel));
        }
        
        panel.add(Box.createVerticalGlue());
        return wrapInScroll(panel);
    }
    
    private JPanel createSentimentBar(double pos, double neg, double neut) {
        return new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int w = getWidth() - 10;
                int h = 24;
                int y = (getHeight() - h) / 2;
                
                // Positive (green)
                g2.setColor(new Color(76, 175, 80));
                g2.fillRoundRect(5, y, (int)(w * pos), h, 4, 4);
                
                // Negative (red)
                g2.setColor(new Color(244, 67, 54));
                g2.fillRoundRect(5 + (int)(w * pos), y, (int)(w * neg), h, 0, 0);
                
                // Neutral (gray)
                g2.setColor(new Color(158, 158, 158));
                g2.fillRoundRect(5 + (int)(w * (pos + neg)), y, (int)(w * neut), h, 4, 4);
                
                // Border
                g2.setColor(Color.DARK_GRAY);
                g2.drawRoundRect(5, y, w, h, 4, 4);
            }
            
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(200, 40);
            }
        };
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SCANSION TAB
    // ═══════════════════════════════════════════════════════════════════════════
    
    private JComponent createScansionPanel() {
        JPanel panel = createScrollablePanel();
        
        if (currentAnalysis.prosody.scansions.isEmpty()) {
            panel.add(new JLabel("No scansion data available"));
            return wrapInScroll(panel);
        }
        
        JTextArea scansionText = new JTextArea();
        scansionText.setFont(MONO_FONT);
        scansionText.setEditable(false);
        scansionText.setBackground(SECTION_BG);
        
        StringBuilder sb = new StringBuilder();
        sb.append("LINE-BY-LINE SCANSION\n");
        sb.append("═".repeat(60)).append("\n\n");
        sb.append("Legend: ´ = stressed, ˘ = unstressed\n\n");
        
        for (LineScansion ls : currentAnalysis.prosody.scansions) {
            sb.append(String.format("Line %d (%d syllables):\n", ls.lineNum, ls.syllables));
            sb.append("  Text:     ").append(ls.line).append("\n");
            sb.append("  Scansion: ").append(ls.scansionMarks).append("\n");
            sb.append("  Pattern:  ").append(ls.stressPattern).append("\n");
            sb.append("  Feet:     ");
            for (FootType ft : ls.feet) {
                sb.append(ft.name).append(" ");
            }
            sb.append("\n\n");
        }
        
        scansionText.setText(sb.toString());
        scansionText.setCaretPosition(0);
        
        panel.add(new JScrollPane(scansionText));
        return panel;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════════
    
    private JPanel createScrollablePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBackground(BG_COLOR);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        return panel;
    }
    
    private JScrollPane wrapInScroll(JPanel panel) {
        JScrollPane scroll = new JScrollPane(panel);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUI(new AeroScrollBarUI());
        scroll.getHorizontalScrollBar().setUI(new AeroScrollBarUI());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }
    
    private JPanel createSectionPanel(String title, Component content) {
        JPanel section = new JPanel(new BorderLayout(5, 5));
        section.setBackground(SECTION_BG);
        section.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(BORDER_SOFT),
                title, TitledBorder.LEFT, TitledBorder.TOP, SECTION_FONT, TEXT_PRIMARY
            ),
            new EmptyBorder(10, 10, 10, 10)
        ));
        section.add(content, BorderLayout.CENTER);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, section.getPreferredSize().height + 20));
        if (content instanceof JScrollPane) {
            JScrollPane sp = (JScrollPane) content;
            sp.setBorder(null);
            sp.setOpaque(false);
            sp.getViewport().setOpaque(false);
            sp.getVerticalScrollBar().setUI(new AeroScrollBarUI());
        }
        return section;
    }
    
    private void addSection(JPanel panel, String title, String value) {
        JLabel label = new JLabel("<html><b>" + title + ":</b> " + value + "</html>");
        label.setFont(LABEL_FONT);
        label.setBorder(new EmptyBorder(5, 0, 5, 0));
        panel.add(label);
    }
    
    private void addStatCard(JPanel panel, String label, String value) {
        JPanel card = new JPanel(new BorderLayout(2, 2));
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_SOFT),
            new EmptyBorder(8, 10, 8, 10)
        ));
        
        JLabel valLabel = new JLabel(value);
        valLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        valLabel.setForeground(ACCENT);
        
        JLabel nameLabel = new JLabel(label);
        nameLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        nameLabel.setForeground(TEXT_SECONDARY);
        
        card.add(valLabel, BorderLayout.CENTER);
        card.add(nameLabel, BorderLayout.SOUTH);
        panel.add(card);
    }
    
    private void addLabelValue(JPanel panel, String label, String value) {
        JLabel l = new JLabel(label);
        l.setFont(LABEL_FONT);
        l.setForeground(TEXT_SECONDARY);
        
        JLabel v = new JLabel(value);
        v.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        v.setForeground(TEXT_PRIMARY);
        
        panel.add(l);
        panel.add(v);
    }
    
    private String formatRhymeScheme(String scheme) {
        if (scheme == null || scheme.isEmpty()) return "None";
        if (scheme.length() <= 16) return scheme;
        return scheme.substring(0, 14) + "...";
    }
    
    private Icon createIcon(String emoji) {
        return null; // Icons handled by tabs themselves
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static final class AeroTabStrip extends FrostedGlassPanel {
        private final List<AeroTabButton> buttons = new ArrayList<>();
        private JTabbedPane boundPane;

        private AeroTabStrip() {
            super(new GridLayout(1, 0, 0, 0), 16);
            setBorder(new EmptyBorder(4, 8, 4, 8));
            setOpaque(false);
        }

        private void bind(JTabbedPane pane) {
            this.boundPane = pane;
            pane.addChangeListener(e -> setSelectedIndex(pane.getSelectedIndex()));
        }

        private void setTabs(String[] labels) {
            removeAll();
            buttons.clear();
            ButtonGroup group = new ButtonGroup();
            for (int i = 0; i < labels.length; i++) {
                AeroTabButton button = new AeroTabButton(labels[i]);
                final int index = i;
                button.addActionListener(e -> {
                    if (boundPane != null) boundPane.setSelectedIndex(index);
                });
                group.add(button);
                buttons.add(button);
                add(button);
            }
            revalidate();
            repaint();
        }

        private void setSelectedIndex(int index) {
            if (index < 0 || index >= buttons.size()) return;
            for (int i = 0; i < buttons.size(); i++) {
                buttons.get(i).setSelected(i == index);
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (buttons.isEmpty()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int top = 6;
            int bottom = getHeight() - 6;
            for (int i = 0; i < buttons.size() - 1; i++) {
                Rectangle r = buttons.get(i).getBounds();
                int x = r.x + r.width;
                g2.setColor(new Color(255, 255, 255, 120));
                g2.drawLine(x, top, x, bottom);
                g2.setColor(new Color(0, 0, 0, 35));
                g2.drawLine(x + 1, top, x + 1, bottom);
            }
            g2.dispose();
        }
    }

    private static final class AeroTabButton extends JToggleButton {
        private static final int ARC = 12;

        private AeroTabButton(String text) {
            super(text);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setFont(AeroTheme.defaultBoldFont(12f));
            setBorder(new EmptyBorder(4, 14, 4, 14));
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean selected = getModel().isSelected();
            boolean hover = getModel().isRollover();
            int w = getWidth();
            int h = getHeight();
            if (selected || hover) {
                Rectangle r = new Rectangle(1, 1, w - 2, h - 2);
                Color top = selected ? AeroTheme.BUTTON_BG_TOP : AeroTheme.BUTTON_HOVER_TOP;
                Color bottom = selected ? AeroTheme.BUTTON_BG_BOTTOM : AeroTheme.BUTTON_HOVER_BOTTOM;
                AeroPainters.paintVerticalGradient(g2, r, top, bottom, ARC);
                AeroPainters.paintGlassOverlay(g2, r, ARC);
                g2.setColor(new Color(180, 180, 190, 150));
                g2.drawRoundRect(r.x, r.y, r.width, r.height, ARC, ARC);
                if (selected) {
                    g2.setColor(new Color(255, 255, 255, 120));
                    g2.drawRoundRect(r.x + 1, r.y + 1, r.width - 2, r.height - 2, ARC - 2, ARC - 2);
                }
            }
            setForeground(selected ? TEXT_PRIMARY : TEXT_SECONDARY);
            super.paintComponent(g2);
            g2.dispose();
        }
    }
    
    /**
     * Gets the current analysis result.
     */
    public PoemAnalysis getCurrentAnalysis() {
        return currentAnalysis;
    }
}
