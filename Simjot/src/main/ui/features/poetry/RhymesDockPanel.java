/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.poetry;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import main.core.poetry.PoetryUtils;
import main.core.sim.llm.api.DefaultSimSuggestionService;
import main.core.sim.llm.api.SimSuggestionService;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.scrollbar.ModernScrollBarUI;

/**
 * Simple dock showing rhyme key and lightweight rhyme/synonym suggestions
 * for the caret word. Keeps heuristics local and fast.
 */
public class RhymesDockPanel extends JPanel {
    private final JLabel title = new JLabel("Rhymes & Synonyms");
    private final JTextArea body = new JTextArea();
    private final SimSuggestionService simService;

    public RhymesDockPanel(){
        this(new DefaultSimSuggestionService());
    }

    public RhymesDockPanel(SimSuggestionService simService){
        super(new BorderLayout());
        this.simService = simService;
        setOpaque(false);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setBorder(BorderFactory.createEmptyBorder(6,6,4,6));

        body.setOpaque(false);
        body.setEditable(false);
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setFont(new Font("SansSerif", Font.PLAIN, 12));
        body.setForeground(new Color(70,70,70));
        JScrollPane sp = new JScrollPane(body){
            { setBorder(BorderFactory.createEmptyBorder()); setOpaque(false); getViewport().setOpaque(false);} };
        JScrollBar vbar = sp.getVerticalScrollBar();
        if (vbar != null) {
            vbar.setUI(new ModernScrollBarUI());
            vbar.setUnitIncrement(14);
            vbar.setPreferredSize(new Dimension(12, Integer.MAX_VALUE));
        }

        FrostedGlassPanel chrome = new FrostedGlassPanel(new BorderLayout(), 16);
        chrome.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        chrome.add(title, BorderLayout.NORTH);
        chrome.add(sp, BorderLayout.CENTER);
        add(chrome, BorderLayout.CENTER);
        setPreferredSize(new Dimension(220, 0));
        setBorder(BorderFactory.createEmptyBorder(6,6,6,6));
    }

    public void updateForWord(String word){
        update(word, null);
    }

    /**
     * Update using caret word and optional full text context to mine poem-local rhymes.
     */
    public void update(String word, String fullText){
        if (word == null || word.isBlank()) {
            body.setText("Place cursor on a word.");
            return;
        }
        String key = PoetryUtils.rhymeKey(word);
        int sylls = PoetryUtils.countSyllables(word);
        List<String> builtInRhymes = naiveRhymes(word);
        List<String> syns = naiveSynonyms(word);

        // Mine poem-local candidates
        List<String> exact = new ArrayList<>();
        List<String> near = new ArrayList<>();
        if (fullText != null && !fullText.isBlank() && key != null) {
            java.util.Set<String> seen = new java.util.LinkedHashSet<>();
            for (String line : PoetryUtils.splitLines(fullText)) {
                for (String w : PoetryUtils.wordsInLine(line)) {
                    String norm = w.toLowerCase(Locale.ROOT);
                    if (norm.equalsIgnoreCase(word)) continue;
                    if (!seen.add(norm)) continue;
                    String k = PoetryUtils.rhymeKey(norm);
                    if (k == null) continue;
                    if (k.equals(key)) exact.add(norm);
                    else if (k.endsWith(safeTail(key, 2)) || key.endsWith(safeTail(k, 2))) near.add(norm);
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Word: ").append(word).append("  •  ")
          .append(sylls).append(" syllable").append(sylls==1?"":"s").append('\n');
        sb.append("Rhyme key: ").append(key!=null?key:"-").append('\n');

        if (!exact.isEmpty()) sb.append('\n').append("Exact rhymes in poem: ").append(joinCap(exact)).append('\n');
        if (!near.isEmpty()) sb.append("Near rhymes in poem: ").append(joinCap(near)).append('\n');

        // Built-in fallback suggestions
        if (!builtInRhymes.isEmpty()) sb.append('\n').append("Suggestions: ").append(joinCap(builtInRhymes)).append('\n');

        // Synonyms
        sb.append('\n').append("Synonyms: ");
        if (syns.isEmpty()) sb.append("(none)"); else sb.append(joinCap(syns));

        body.setText(sb.toString());
        body.setCaretPosition(0);

        // Hybrid: optionally augment with Sim in background (non-blocking)
        simService.queryAsync(word, key, sylls, fullText, simOut -> {
            if (simOut != null && !simOut.isBlank()) {
                appendSimSection(simOut);
            }
        });
    }

    // Use the comprehensive RhymeDatabase for rhyme suggestions
    private List<String> naiveRhymes(String word){
        String key = PoetryUtils.rhymeKey(word);
        if (key == null) return List.of();
        
        // Try the new database first
        List<String> dbRhymes = main.core.poetry.RhymeDatabase.getRhymesFor(word);
        if (!dbRhymes.isEmpty()) return dbRhymes;
        
        // Fallback to simple heuristics
        String[][] tiny = new String[][]{
                {"ight","light","night","flight","sight","bright","might","right"},
                {"ow","glow","slow","snow","flow","grow","show","know"},
                {"ay","day","play","say","away","delay","stay","way"},
                {"ing","sing","ring","wing","spring","thing","bring","king"},
                {"ove","love","dove","glove","above","shove"}
        };
        List<String> out = new ArrayList<>();
        for (String[] row : tiny){
            if (key.endsWith(row[0])){
                for (int i=1;i<row.length;i++) if (!row[i].equalsIgnoreCase(word)) out.add(row[i]);
            }
        }
        return out;
    }

    // Use the comprehensive RhymeDatabase for synonym suggestions
    private List<String> naiveSynonyms(String word){
        String w = word.toLowerCase(Locale.ROOT);
        
        // Try the new database first
        List<String> dbSyns = main.core.poetry.RhymeDatabase.getSynonymsFor(w);
        if (!dbSyns.isEmpty()) return dbSyns;
        
        // Fallback for common words
        return switch (w) {
            case "love" -> List.of("affection","ardor","devotion","fondness","passion");
            case "dark" -> List.of("dim","murky","dusky","tenebrous","shadowy");
            case "light" -> List.of("glow","gleam","radiance","luster","brilliance");
            case "river" -> List.of("stream","brook","current","waters","flow");
            case "heart" -> List.of("soul","spirit","core","essence");
            case "dream" -> List.of("vision","fantasy","reverie","aspiration");
            default -> List.of();
        };
    }

    private static String safeTail(String s, int n){
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(s.length()-n);
    }

    private static String joinCap(List<String> items){
        java.util.List<String> out = new java.util.ArrayList<>(items.size());
        for (String i : items) out.add(cap(i));
        return String.join(", ", out);
    }

    private static String cap(String s){
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // --- Sim augmentation is delegated to SimSuggestionService ---

    private void appendSimSection(String text){
        SwingUtilities.invokeLater(() -> {
            String t = body.getText();
            StringBuilder sb = new StringBuilder(t.length() + text.length() + 64);
            sb.append(t);
            sb.append("\n\n— Sim suggestions —\n");
            sb.append(SimSuggestionFormatter.format(text.trim()));
            body.setText(sb.toString());
            body.setCaretPosition(0);
        });
    }
}
