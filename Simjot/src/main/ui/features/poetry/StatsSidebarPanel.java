package main.ui.features.poetry;

import main.core.poetry.PoetryUtils;

import javax.swing.*;
import java.awt.*;
import java.util.*;

/**
 * Non-intrusive sidebar showing per-line syllable counts and end-rhyme labels.
 * Keep it lightweight and decoupled from the editor.
 */
public class StatsSidebarPanel extends JPanel {
    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final JList<String> list = new JList<>(model);

    public StatsSidebarPanel(){
        super(new BorderLayout());
        setOpaque(false);
        list.setOpaque(false);
        list.setForeground(new Color(70,70,70));
        list.setFont(new Font("SansSerif", Font.PLAIN, 12));
        add(new JScrollPane(list){
            { setBorder(BorderFactory.createEmptyBorder()); setOpaque(false); getViewport().setOpaque(false);} }, BorderLayout.CENTER);
        setPreferredSize(new Dimension(180, 0));
        setBorder(BorderFactory.createEmptyBorder(6,6,6,6));
    }

    public void updateFromText(String text){
        java.util.List<String> lines = PoetryUtils.splitLines(text);
        // Build rhyme groups
        Map<String, Character> keyToLabel = new HashMap<>();
        model.clear();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
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
        }
    }
}
