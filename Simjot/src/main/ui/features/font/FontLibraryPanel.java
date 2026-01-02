/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.font;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;

import main.core.font.CustomFont;
import main.infrastructure.font.CustomFontRegistry;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.scrollbar.ModernScrollBarUI;

/**
 * Panel for managing the font library - list, rename, delete fonts.
 */
public class FontLibraryPanel extends JPanel {
    
    private final CustomFontRegistry registry;
    private final DefaultListModel<String> fontListModel;
    private final JList<String> fontList;
    
    private final List<FontLibraryListener> listeners = new ArrayList<>();
    
    public interface FontLibraryListener {
        void onFontSelected(String fontName);
        void onFontCreated(CustomFont font);
        void onFontDeleted(String fontName);
        void onFontRenamed(String oldName, String newName);
    }
    
    public FontLibraryPanel(CustomFontRegistry registry) {
        this.registry = registry;
        
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setBackground(new Color(248, 248, 250));
        
        // Header
        JLabel header = new JLabel("Font Library");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 14f));
        add(header, BorderLayout.NORTH);
        
        // Font list
        fontListModel = new DefaultListModel<>();
        fontList = new JList<>(fontListModel);
        fontList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fontList.setCellRenderer(new FontListCellRenderer());
        fontList.addListSelectionListener(this::onSelectionChanged);
        
        JScrollPane scrollPane = new JScrollPane(fontList);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(210, 210, 215)));
        scrollPane.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buttonPanel.setOpaque(false);
        
        RoundedButton newButton = new RoundedButton("New");
        newButton.setPreferredSize(new Dimension(80, 26));
        newButton.addActionListener(e -> createNewFont());
        buttonPanel.add(newButton);
        
        RoundedButton renameButton = new RoundedButton("Rename");
        renameButton.setPreferredSize(new Dimension(90, 26));
        renameButton.addActionListener(e -> renameSelectedFont());
        buttonPanel.add(renameButton);
        
        RoundedButton duplicateButton = new RoundedButton("Duplicate");
        duplicateButton.setPreferredSize(new Dimension(100, 26));
        duplicateButton.addActionListener(e -> duplicateSelectedFont());
        buttonPanel.add(duplicateButton);
        
        RoundedButton deleteButton = new RoundedButton("Delete");
        deleteButton.setPreferredSize(new Dimension(80, 26));
        deleteButton.addActionListener(e -> deleteSelectedFont());
        buttonPanel.add(deleteButton);
        
        add(buttonPanel, BorderLayout.SOUTH);
        
        // Load fonts
        refreshFontList();
    }
    
    public void addListener(FontLibraryListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    public void removeListener(FontLibraryListener listener) {
        listeners.remove(listener);
    }
    
    public void refreshFontList() {
        String selected = fontList.getSelectedValue();
        
        fontListModel.clear();
        for (String name : registry.listFontNames()) {
            fontListModel.addElement(name);
        }
        
        if (selected != null && fontListModel.contains(selected)) {
            fontList.setSelectedValue(selected, true);
        } else if (!fontListModel.isEmpty()) {
            fontList.setSelectedIndex(0);
        }
    }
    
    public String getSelectedFontName() {
        return fontList.getSelectedValue();
    }
    
    public void setSelectedFontName(String name) {
        fontList.setSelectedValue(name, true);
    }
    
    private void onSelectionChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;
        
        String selected = fontList.getSelectedValue();
        if (selected != null) {
            for (FontLibraryListener l : listeners) {
                l.onFontSelected(selected);
            }
        }
    }
    
    private void createNewFont() {
        String name = JOptionPane.showInputDialog(this, 
            "Enter font name:", "New Font", JOptionPane.PLAIN_MESSAGE);
        
        if (name == null || name.trim().isEmpty()) return;
        
        String author = JOptionPane.showInputDialog(this,
            "Enter author name (optional):", "New Font", JOptionPane.PLAIN_MESSAGE);
        
        CustomFont font = registry.createFont(name.trim(), author != null ? author.trim() : "");
        if (font != null) {
            refreshFontList();
            fontList.setSelectedValue(font.getName(), true);
            
            for (FontLibraryListener l : listeners) {
                l.onFontCreated(font);
            }
        } else {
            JOptionPane.showMessageDialog(this, 
                "Failed to create font.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void renameSelectedFont() {
        String selected = fontList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, 
                "Please select a font to rename.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String newName = JOptionPane.showInputDialog(this,
            "Enter new name:", selected);
        
        if (newName == null || newName.trim().isEmpty() || newName.equals(selected)) return;
        
        if (registry.renameFont(selected, newName.trim())) {
            refreshFontList();
            fontList.setSelectedValue(newName.trim(), true);
            
            for (FontLibraryListener l : listeners) {
                l.onFontRenamed(selected, newName.trim());
            }
        } else {
            JOptionPane.showMessageDialog(this,
                "Failed to rename font. A font with that name may already exist.",
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void duplicateSelectedFont() {
        String selected = fontList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this,
                "Please select a font to duplicate.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String newName = JOptionPane.showInputDialog(this,
            "Enter name for copy:", selected + " Copy");
        
        if (newName == null || newName.trim().isEmpty()) return;
        
        CustomFont copy = registry.duplicateFont(selected, newName.trim());
        if (copy != null) {
            refreshFontList();
            fontList.setSelectedValue(copy.getName(), true);
            
            for (FontLibraryListener l : listeners) {
                l.onFontCreated(copy);
            }
        } else {
            JOptionPane.showMessageDialog(this,
                "Failed to duplicate font.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void deleteSelectedFont() {
        String selected = fontList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this,
                "Please select a font to delete.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        int confirm = JOptionPane.showConfirmDialog(this,
            "Are you sure you want to delete '" + selected + "'?\nThis cannot be undone.",
            "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        
        if (confirm != JOptionPane.YES_OPTION) return;
        
        if (registry.deleteFont(selected)) {
            refreshFontList();
            
            for (FontLibraryListener l : listeners) {
                l.onFontDeleted(selected);
            }
        } else {
            JOptionPane.showMessageDialog(this,
                "Failed to delete font.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private class FontListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            
            if (value instanceof String) {
                String fontName = (String) value;
                CustomFont font = registry.loadFont(fontName);
                
                if (font != null) {
                    int defined = font.getDefinedGlyphCount();
                    int total = font.getGlyphCount();
                    setText(String.format("%s (%d/%d)", fontName, defined, total));
                }
            }
            
            setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            return this;
        }
    }
}
