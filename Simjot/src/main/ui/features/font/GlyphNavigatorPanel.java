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
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

import main.core.font.CustomFont;
import main.core.font.GlyphSet;
import main.ui.components.combobox.ModernComboBoxUI;
import main.ui.components.scrollbar.ModernScrollBarUI;

/**
 * Panel for navigating and selecting glyphs in a font.
 */
public class GlyphNavigatorPanel extends JPanel {
    
    private CustomFont font;
    private GlyphSet glyphSet;
    private int selectedCodepoint = -1;
    
    private JPanel gridPanel;
    private JScrollPane scrollPane;
    private JComboBox<GlyphSet> glyphSetCombo;
    
    private final List<GlyphNavigatorListener> listeners = new ArrayList<>();
    
    public interface GlyphNavigatorListener {
        void onGlyphSelected(int codepoint);
    }
    
    public GlyphNavigatorPanel() {
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setBackground(new Color(248, 248, 250));
        
        // Top: Glyph set selector
        JPanel topPanel = new JPanel(new BorderLayout(8, 0));
        topPanel.setOpaque(false);
        
        topPanel.add(new JLabel("Glyph Set:"), BorderLayout.WEST);
        
        glyphSetCombo = new JComboBox<>();
        glyphSetCombo.addItem(GlyphSet.UPPERCASE);
        glyphSetCombo.addItem(GlyphSet.LOWERCASE);
        glyphSetCombo.addItem(GlyphSet.DIGITS);
        glyphSetCombo.addItem(GlyphSet.BASIC_PUNCTUATION);
        glyphSetCombo.addItem(GlyphSet.ASCII_PRINTABLE);
        glyphSetCombo.setUI(new ModernComboBoxUI());
        glyphSetCombo.setRenderer(new ModernComboBoxUI.ModernComboBoxRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, 
                    int index, boolean isSelected, boolean cellHasFocus) {
                Object display = value;
                if (value instanceof GlyphSet) {
                    display = ((GlyphSet) value).getName();
                }
                return super.getListCellRendererComponent(list, display, index, isSelected, cellHasFocus);
            }
        });
        glyphSetCombo.addActionListener(e -> {
            glyphSet = (GlyphSet) glyphSetCombo.getSelectedItem();
            rebuildGrid();
        });
        topPanel.add(glyphSetCombo, BorderLayout.CENTER);
        
        add(topPanel, BorderLayout.NORTH);
        
        // Center: Glyph grid - use WrapLayout so buttons wrap to next row
        gridPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 4));
        gridPanel.setOpaque(false);
        
        scrollPane = new JScrollPane(gridPanel);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(210, 210, 215)));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        
        add(scrollPane, BorderLayout.CENTER);
        
        // Initialize with uppercase
        glyphSet = GlyphSet.UPPERCASE;
        glyphSetCombo.setSelectedItem(glyphSet);
    }
    
    public void addListener(GlyphNavigatorListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    public void removeListener(GlyphNavigatorListener listener) {
        listeners.remove(listener);
    }
    
    public void setFont(CustomFont font) {
        this.font = font;
        rebuildGrid();
    }
    
    public CustomFont getCustomFont() {
        return font;
    }
    
    public void setGlyphSet(GlyphSet glyphSet) {
        this.glyphSet = glyphSet;
        glyphSetCombo.setSelectedItem(glyphSet);
        rebuildGrid();
    }
    
    public int getSelectedCodepoint() {
        return selectedCodepoint;
    }
    
    public void setSelectedCodepoint(int codepoint) {
        this.selectedCodepoint = codepoint;
        repaint();
    }
    
    public void refresh() {
        rebuildGrid();
    }
    
    private void rebuildGrid() {
        gridPanel.removeAll();
        
        if (glyphSet == null) {
            gridPanel.revalidate();
            gridPanel.repaint();
            return;
        }
        
        for (int codepoint : glyphSet.getCodepoints()) {
            GlyphButton button = new GlyphButton(codepoint);
            button.addActionListener(e -> {
                selectedCodepoint = codepoint;
                for (GlyphNavigatorListener l : listeners) {
                    l.onGlyphSelected(codepoint);
                }
                rebuildGrid(); // Refresh to update selection state
            });
            gridPanel.add(button);
        }
        
        gridPanel.revalidate();
        gridPanel.repaint();
    }
    
    /**
     * FlowLayout subclass that properly wraps components in a JScrollPane.
     */
    private static class WrapLayout extends FlowLayout {
        public WrapLayout(int align, int hgap, int vgap) {
            super(align, hgap, vgap);
        }
        
        @Override
        public Dimension preferredLayoutSize(Container target) {
            return layoutSize(target, true);
        }
        
        @Override
        public Dimension minimumLayoutSize(Container target) {
            Dimension min = layoutSize(target, false);
            min.width -= (getHgap() + 1);
            return min;
        }
        
        private Dimension layoutSize(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                int targetWidth = target.getSize().width;
                Container container = target;
                
                while (container.getSize().width == 0 && container.getParent() != null) {
                    container = container.getParent();
                }
                
                targetWidth = container.getSize().width;
                
                if (targetWidth == 0) {
                    targetWidth = Integer.MAX_VALUE;
                }
                
                int hgap = getHgap();
                int vgap = getVgap();
                Insets insets = target.getInsets();
                int horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2);
                int maxWidth = targetWidth - horizontalInsetsAndGap;
                
                Dimension dim = new Dimension(0, 0);
                int rowWidth = 0;
                int rowHeight = 0;
                
                int nmembers = target.getComponentCount();
                
                for (int i = 0; i < nmembers; i++) {
                    Component m = target.getComponent(i);
                    
                    if (m.isVisible()) {
                        Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                        
                        if (rowWidth + d.width > maxWidth) {
                            addRow(dim, rowWidth, rowHeight);
                            rowWidth = 0;
                            rowHeight = 0;
                        }
                        
                        if (rowWidth != 0) {
                            rowWidth += hgap;
                        }
                        
                        rowWidth += d.width;
                        rowHeight = Math.max(rowHeight, d.height);
                    }
                }
                
                addRow(dim, rowWidth, rowHeight);
                
                dim.width += horizontalInsetsAndGap;
                dim.height += insets.top + insets.bottom + vgap * 2;
                
                return dim;
            }
        }
        
        private void addRow(Dimension dim, int rowWidth, int rowHeight) {
            dim.width = Math.max(dim.width, rowWidth);
            
            if (dim.height > 0) {
                dim.height += getVgap();
            }
            
            dim.height += rowHeight;
        }
    }
    
    private class GlyphButton extends JButton {
        private final int codepoint;
        
        GlyphButton(int codepoint) {
            this.codepoint = codepoint;
            
            String display = codepoint >= 32 ? new String(Character.toChars(codepoint)) : "?";
            setText(display);
            
            setFont(new Font("SansSerif", Font.PLAIN, 18));
            setPreferredSize(new Dimension(40, 40));
            setMinimumSize(new Dimension(40, 40));
            setMaximumSize(new Dimension(40, 40));
            
            setFocusPainted(false);
            setBorderPainted(true);
            setContentAreaFilled(true);
            
            updateAppearance();
        }
        
        private void updateAppearance() {
            boolean selected = codepoint == selectedCodepoint;
            boolean defined = font != null && font.hasGlyph(codepoint);
            
            if (selected) {
                setBackground(new Color(80, 120, 200));
                setForeground(Color.WHITE);
                setBorder(BorderFactory.createLineBorder(new Color(60, 100, 180), 2));
            } else if (defined) {
                setBackground(new Color(220, 240, 220));
                setForeground(new Color(40, 80, 40));
                setBorder(BorderFactory.createLineBorder(new Color(180, 210, 180)));
            } else {
                setBackground(Color.WHITE);
                setForeground(new Color(100, 100, 100));
                setBorder(BorderFactory.createLineBorder(new Color(200, 200, 205)));
            }
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            updateAppearance();
            super.paintComponent(g);
        }
    }
}
