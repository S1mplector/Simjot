/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.features.entries;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import main.infrastructure.backup.NotebookInfo;
import main.infrastructure.ffi.NativeAccess;
import main.ui.app.JournalApp;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.input.AeroTextField;

/**
 * Modern, polished template selector with smooth animations and clear UX.
 * Features: hover effects, selection state, category filters, keyboard nav.
 * 
 * @author S1mplector
 */
public class ModernTemplateSelector extends JDialog {
    
    private static final Color ACCENT = new Color(70, 130, 220);
    private static final Color CARD_BORDER = new Color(210, 220, 235, 140);
    private static final Color CARD_BORDER_HOVER = new Color(170, 200, 255, 180);
    private static final Color TEXT_PRIMARY = new Color(35, 40, 50);
    private static final Color TEXT_SECONDARY = new Color(100, 110, 125);
    private static final Color TEXT_MUTED = new Color(140, 150, 165);
    
    private JournalTemplateManager.JournalTemplate selectedTemplate;
    private boolean accepted = false;
    private final NotebookInfo notebook;
    private final JournalApp app;
    private final JPanel cardsContainer;
    private final AeroTextField searchField;
    private final JPanel filterPanel;
    private final JPanel viewSwitcher;
    private final JPanel selectorView;
    private final JPanel editorView;
    private RoundedButton useButton;
    private List<JournalTemplateManager.JournalTemplate> allTemplates;
    private List<TemplateCard> cards = new ArrayList<>();
    private TemplateCard selectedCard = null;
    private String activeFilter = "all";
    private Timer searchDebounceTimer;  // Debounce search to avoid twitching
    
    public ModernTemplateSelector(Frame parent) {
        this(parent, null);
    }
    
    public ModernTemplateSelector(Frame parent, NotebookInfo notebook) {
        super(parent, "New Entry", true);
        this.notebook = notebook;
        this.app = (parent instanceof JournalApp ja) ? ja : null;
        
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        setLayout(new BorderLayout());
        
        JPanel main = new JPanel(new BorderLayout(0, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.dispose();
            }
        };
        main.setOpaque(false);
        main.setBorder(new EmptyBorder(0, 0, 0, 0));
        
        // ═══════════════════════════════════════════════════════════════════
        // HEADER
        // ═══════════════════════════════════════════════════════════════════
        JPanel header = createHeader();
        
        // ═══════════════════════════════════════════════════════════════════
        // FILTER TABS
        // ═══════════════════════════════════════════════════════════════════
        filterPanel = createFilterPanel();
        
        // ═══════════════════════════════════════════════════════════════════
        // SEARCH
        // ═══════════════════════════════════════════════════════════════════
        JPanel searchPanel = new JPanel(new BorderLayout(12, 0));
        searchPanel.setOpaque(false);
        searchPanel.setBorder(new EmptyBorder(0, 24, 16, 24));
        
        searchField = new AeroTextField(28);
        searchField.setFont(searchField.getFont().deriveFont(14f));
        searchField.putClientProperty("JTextField.placeholderText", "Search templates...");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { debouncedRefresh(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { debouncedRefresh(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { debouncedRefresh(); }
        });
        
        // ESC clears search, Enter confirms selection
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    if (!searchField.getText().isEmpty()) {
                        searchField.setText("");
                    } else {
                        dispose();
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER && selectedTemplate != null) {
                    accept();
                }
            }
        });
        
        searchPanel.add(searchField, BorderLayout.CENTER);
        
        // Combine filter and search
        JPanel topSection = new JPanel(new BorderLayout());
        topSection.setOpaque(false);
        topSection.add(filterPanel, BorderLayout.NORTH);
        topSection.add(searchPanel, BorderLayout.SOUTH);
        
        // ═══════════════════════════════════════════════════════════════════
        // CARDS GRID - Use simple opaque background for smooth scrolling
        // ═══════════════════════════════════════════════════════════════════
        cardsContainer = new JPanel(new GridLayout(0, 2, 16, 16));
        cardsContainer.setBorder(new EmptyBorder(20, 24, 20, 24));
        cardsContainer.setOpaque(true);
        cardsContainer.setDoubleBuffered(true);
        cardsContainer.setBackground(Color.WHITE);
        
        JScrollPane scroll = new JScrollPane(cardsContainer);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(true);
        scroll.getViewport().setOpaque(true);
        scroll.getViewport().setBackground(Color.WHITE);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        
        // Optimize scroll performance
        scroll.getViewport().setScrollMode(javax.swing.JViewport.BACKINGSTORE_SCROLL_MODE);
        
        try {
            scroll.getVerticalScrollBar().setUI(new main.ui.components.scrollbar.ModernScrollBarUI());
            scroll.getVerticalScrollBar().setPreferredSize(new Dimension(10, Integer.MAX_VALUE));
        } catch (Throwable ignored) {}
        
        // Responsive columns
        scroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateGridColumns();
            }
        });
        
        JPanel centerSection = new JPanel(new BorderLayout());
        centerSection.setOpaque(false);
        centerSection.add(topSection, BorderLayout.NORTH);
        centerSection.add(scroll, BorderLayout.CENTER);
        // ═══════════════════════════════════════════════════════════════════
        // FOOTER
        // ═══════════════════════════════════════════════════════════════════
        JPanel footer = createFooter();

        selectorView = new JPanel(new BorderLayout());
        selectorView.setOpaque(false);
        selectorView.add(header, BorderLayout.NORTH);
        selectorView.add(centerSection, BorderLayout.CENTER);
        selectorView.add(footer, BorderLayout.SOUTH);

        editorView = new JPanel(new BorderLayout());
        editorView.setOpaque(false);

        viewSwitcher = new JPanel(new CardLayout());
        viewSwitcher.setOpaque(false);
        viewSwitcher.add(selectorView, "selector");
        viewSwitcher.add(editorView, "editor");

        main.add(viewSwitcher, BorderLayout.CENTER);
        
        add(main);
        
        // Size and position
        setSize(780, 620);
        setLocationRelativeTo(parent);
        
        // ESC to close
        getRootPane().registerKeyboardAction(e -> dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
        
        // Arrow key navigation
        installKeyboardNav();
        
        // Load templates
        refreshCards();
        
        // Focus search
        SwingUtilities.invokeLater(() -> searchField.requestFocusInWindow());
    }
    
    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(20, 24, 12, 24));
        
        JLabel title = new JLabel("What would you like to write about?");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setForeground(TEXT_PRIMARY);
        
        // Settings button
        JButton settingsBtn = createIconButton("⚙", "Manage Templates");
        settingsBtn.addActionListener(e -> openTemplateManager());
        
        header.add(title, BorderLayout.WEST);
        header.add(settingsBtn, BorderLayout.EAST);
        
        return header;
    }
    
    private JPanel createFilterPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(0, 24, 12, 24));
        
        String[] filters = {"All", "Guided", "Freeform", "Custom"};
        String[] filterKeys = {"all", "guided", "freeform", "custom"};
        
        for (int i = 0; i < filters.length; i++) {
            FilterChip chip = new FilterChip(filters[i], filterKeys[i]);
            chip.setSelected(i == 0);
            panel.add(chip);
        }
        
        return panel;
    }
    
    private JPanel createFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(12, 24, 20, 24));
        
        // Left: hint
        JLabel hint = new JLabel("Double-click or press Enter to use • Arrows to navigate");
        hint.setFont(hint.getFont().deriveFont(11f));
        hint.setForeground(TEXT_MUTED);
        
        // Right: buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        buttons.setOpaque(false);
        
        RoundedButton cancelBtn = new RoundedButton("Cancel");
        cancelBtn.setPreferredSize(new Dimension(100, 38));
        cancelBtn.addActionListener(e -> dispose());

        useButton = new RoundedButton("Use Template");
        useButton.setPreferredSize(new Dimension(140, 38));
        useButton.setEnabled(false);
        useButton.addActionListener(e -> accept());
        
        buttons.add(cancelBtn);
        buttons.add(useButton);
        
        footer.add(hint, BorderLayout.WEST);
        footer.add(buttons, BorderLayout.EAST);
        
        return footer;
    }
    
    /**
     * Debounced refresh to avoid twitching during rapid typing.
     * Waits 150ms after last keystroke before refreshing.
     */
    private void debouncedRefresh() {
        if (searchDebounceTimer != null) {
            searchDebounceTimer.stop();
        }
        searchDebounceTimer = new Timer(150, e -> {
            searchDebounceTimer.stop();
            refreshCards();
        });
        searchDebounceTimer.setRepeats(false);
        searchDebounceTimer.start();
    }
    
    private void refreshCards() {
        cardsContainer.removeAll();
        cards.clear();
        
        allTemplates = (notebook != null)
            ? JournalTemplateManager.getInstance().getTemplates(notebook)
            : JournalTemplateManager.getInstance().getTemplates();
        
        String query = searchField.getText().trim();
        
        // Sort by fuzzy score if searching
        List<JournalTemplateManager.JournalTemplate> filtered = new ArrayList<>();
        
        for (JournalTemplateManager.JournalTemplate t : allTemplates) {
            // Filter by search using native case-insensitive search
            if (!query.isEmpty()) {
                boolean match = nativeSearchMatch(t.getName(), query) 
                    || nativeSearchMatch(t.getDescription(), query);
                if (!match) continue;
            }
            
            // Filter by category
            if (!matchesFilter(t)) continue;
            filtered.add(t);
        }
        
        // Sort by fuzzy score for better relevance when searching
        if (!query.isEmpty()) {
            filtered.sort((a, b) -> {
                int scoreA = nativeFuzzyScore(a.getName(), query) + nativeFuzzyScore(a.getDescription(), query);
                int scoreB = nativeFuzzyScore(b.getName(), query) + nativeFuzzyScore(b.getDescription(), query);
                return Integer.compare(scoreB, scoreA); // Higher score first
            });
        }
        
        for (JournalTemplateManager.JournalTemplate t : filtered) {
            
            TemplateCard card = new TemplateCard(t);
            cards.add(card);
            cardsContainer.add(card);
        }
        
        // Add "Create New" card
        cardsContainer.add(createNewCard());
        
        cardsContainer.revalidate();
        cardsContainer.repaint();
        
        // Re-select if still valid
        if (selectedTemplate != null) {
            for (TemplateCard c : cards) {
                if (c.template.getId().equals(selectedTemplate.getId())) {
                    selectCard(c);
                    break;
                }
            }
        }
    }
    
    private boolean matchesFilter(JournalTemplateManager.JournalTemplate t) {
        if ("all".equals(activeFilter)) return true;
        if ("guided".equals(activeFilter)) return t.getQuestions() != null && t.getQuestions().length > 0;
        if ("freeform".equals(activeFilter)) return t.getQuestions() == null || t.getQuestions().length == 0;
        if ("custom".equals(activeFilter)) return t.isCustom();
        return true;
    }
    
    /**
     * Native case-insensitive search with Java fallback.
     */
    private boolean nativeSearchMatch(String text, String query) {
        if (text == null || query == null || query.isEmpty()) return text != null;
        // Try native first (faster for longer strings)
        try {
            if (NativeAccess.stringOpsReady()) {
                return NativeAccess.stringContainsCi(text, query);
            }
        } catch (Throwable ignored) {}
        // Java fallback
        return text.toLowerCase().contains(query.toLowerCase());
    }
    
    /**
     * Native fuzzy score with Java fallback.
     */
    private int nativeFuzzyScore(String text, String query) {
        if (text == null || query == null) return 0;
        // Try native first
        try {
            if (NativeAccess.textUtilsReady()) {
                return NativeAccess.textFuzzyScore(text, query);
            }
        } catch (Throwable ignored) {}
        // Java fallback: simple score based on position and exact match
        String lower = text.toLowerCase();
        String qLower = query.toLowerCase();
        if (lower.equals(qLower)) return 100;
        if (lower.startsWith(qLower)) return 80;
        if (lower.contains(qLower)) return 50;
        return 0;
    }
    
    private JPanel createNewCard() {
        JPanel card = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createCompoundBorder(
            new RoundedBorder(12, CARD_BORDER),
            new EmptyBorder(24, 16, 24, 16)
        ));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                card.setBorder(BorderFactory.createCompoundBorder(
                    new RoundedBorder(12, CARD_BORDER_HOVER),
                    new EmptyBorder(24, 16, 24, 16)
                ));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                card.setBorder(BorderFactory.createCompoundBorder(
                    new RoundedBorder(12, CARD_BORDER),
                    new EmptyBorder(24, 16, 24, 16)
                ));
            }
            @Override
            public void mouseClicked(MouseEvent e) {
                openTemplateManager();
            }
        });
        
        JLabel plus = new JLabel("+", SwingConstants.CENTER);
        plus.setFont(plus.getFont().deriveFont(Font.BOLD, 28f));
        plus.setForeground(ACCENT);
        
        JLabel text = new JLabel("Create New Template", SwingConstants.CENTER);
        text.setFont(text.getFont().deriveFont(Font.BOLD, 13f));
        text.setForeground(ACCENT);
        
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        plus.setAlignmentX(Component.CENTER_ALIGNMENT);
        text.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(Box.createVerticalGlue());
        content.add(plus);
        content.add(Box.createVerticalStrut(8));
        content.add(text);
        content.add(Box.createVerticalGlue());
        
        card.add(content, BorderLayout.CENTER);
        return card;
    }
    
    private void selectCard(TemplateCard card) {
        if (selectedCard != null) {
            selectedCard.setSelected(false);
        }
        selectedCard = card;
        if (card != null) {
            card.setSelected(true);
            selectedTemplate = card.template;
            useButton.setEnabled(true);
        } else {
            selectedTemplate = null;
            useButton.setEnabled(false);
        }
    }
    
    private void accept() {
        if (selectedTemplate != null) {
            accepted = true;
            dispose();
        }
    }
    
    private void updateGridColumns() {
        int w = cardsContainer.getParent() != null ? cardsContainer.getParent().getWidth() : cardsContainer.getWidth();
        if (w <= 0) return;
        int cols = Math.max(1, Math.min(3, (w - 48) / 280));
        GridLayout layout = (GridLayout) cardsContainer.getLayout();
        if (layout.getColumns() != cols) {
            cardsContainer.setLayout(new GridLayout(0, cols, 16, 16));
            cardsContainer.revalidate();
        }
    }
    
    private void installKeyboardNav() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (!isVisible() || e.getID() != KeyEvent.KEY_PRESSED) return false;
            
            int code = e.getKeyCode();
            if (code == KeyEvent.VK_DOWN || code == KeyEvent.VK_UP || 
                code == KeyEvent.VK_LEFT || code == KeyEvent.VK_RIGHT) {
                
                if (cards.isEmpty()) return false;
                
                int currentIndex = selectedCard != null ? cards.indexOf(selectedCard) : -1;
                int cols = ((GridLayout) cardsContainer.getLayout()).getColumns();
                int newIndex = currentIndex;
                
                switch (code) {
                    case KeyEvent.VK_RIGHT -> newIndex = Math.min(currentIndex + 1, cards.size() - 1);
                    case KeyEvent.VK_LEFT -> newIndex = Math.max(currentIndex - 1, 0);
                    case KeyEvent.VK_DOWN -> newIndex = Math.min(currentIndex + cols, cards.size() - 1);
                    case KeyEvent.VK_UP -> newIndex = Math.max(currentIndex - cols, 0);
                }
                
                if (newIndex >= 0 && newIndex < cards.size() && newIndex != currentIndex) {
                    selectCard(cards.get(newIndex));
                    cards.get(newIndex).scrollRectToVisible(cards.get(newIndex).getBounds());
                }
                return true;
            }
            return false;
        });
    }
    
    private JButton createIconButton(String icon, String tooltip) {
        JButton btn = new JButton(icon) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                if (getModel().isRollover()) {
                    g2.setColor(new Color(240, 242, 248));
                    g2.fillOval(2, 2, getWidth()-4, getHeight()-4);
                }
                
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(btn.getFont().deriveFont(18f));
        btn.setForeground(TEXT_SECONDARY);
        btn.setPreferredSize(new Dimension(36, 36));
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setToolTipText(tooltip);
        return btn;
    }
    
    private void openTemplateManager() {
        showEditor(null);
    }

    private void showEditor(JournalTemplateManager.JournalTemplate existing) {
        editorView.removeAll();
        ModernTemplateEditorPanel editorPanel = new ModernTemplateEditorPanel(notebook, existing, app);
        editorPanel.setOnCancel(() -> showSelector());
        editorPanel.setOnSave(savedTemplate -> {
            refreshCards();
            showSelector();
            if (savedTemplate != null) {
                selectTemplateById(savedTemplate.getId());
            }
        });
        editorView.add(editorPanel, BorderLayout.CENTER);
        editorView.revalidate();
        editorView.repaint();
        ((CardLayout) viewSwitcher.getLayout()).show(viewSwitcher, "editor");
    }

    private void showSelector() {
        ((CardLayout) viewSwitcher.getLayout()).show(viewSwitcher, "selector");
        SwingUtilities.invokeLater(() -> searchField.requestFocusInWindow());
    }

    private void selectTemplateById(String id) {
        if (id == null) return;
        for (TemplateCard card : cards) {
            if (id.equals(card.template.getId())) {
                selectCard(card);
                break;
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════
    
    public boolean isAccepted() { return accepted; }
    public JournalTemplateManager.JournalTemplate getSelectedTemplate() { return selectedTemplate; }
    public String[] getGuidedQuestions() { return selectedTemplate != null ? selectedTemplate.getQuestions() : null; }
    public boolean isGuidedMode() { return selectedTemplate != null && selectedTemplate.getQuestions().length > 0; }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // TEMPLATE CARD
    // ═══════════════════════════════════════════════════════════════════════════
    
    private class TemplateCard extends JPanel {
        final JournalTemplateManager.JournalTemplate template;
        private boolean selected = false;
        private boolean hovered = false;
        private final EmptyBorder contentPadding = new EmptyBorder(16, 16, 16, 16);
        
        TemplateCard(JournalTemplateManager.JournalTemplate template) {
            super(new BorderLayout());
            this.template = template;
            setOpaque(false);
            setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(14, CARD_BORDER),
                contentPadding
            ));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            
            // Content
            JPanel content = new JPanel();
            content.setOpaque(false);
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            
            // Title
            JLabel title = new JLabel(template.getName());
            title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
            title.setForeground(TEXT_PRIMARY);
            title.setAlignmentX(LEFT_ALIGNMENT);
            
            // Description
            JLabel desc = new JLabel("<html><div style='width:200px'>" + template.getDescription() + "</div></html>");
            desc.setFont(desc.getFont().deriveFont(12f));
            desc.setForeground(TEXT_SECONDARY);
            desc.setAlignmentX(LEFT_ALIGNMENT);
            
            // Badge
            String[] qs = template.getQuestions();
            int qCount = qs != null ? qs.length : 0;
            String badgeText = qCount > 0 ? "Guided • " + qCount + " questions" : "Freeform";
            JLabel badge = new JLabel(badgeText);
            badge.setFont(badge.getFont().deriveFont(Font.PLAIN, 11f));
            badge.setForeground(qCount > 0 ? ACCENT : TEXT_MUTED);
            badge.setAlignmentX(LEFT_ALIGNMENT);
            
            content.add(title);
            content.add(Box.createVerticalStrut(4));
            content.add(desc);
            content.add(Box.createVerticalStrut(8));
            content.add(badge);
            
            // Question preview
            if (qCount > 0) {
                content.add(Box.createVerticalStrut(10));
                JPanel previewPanel = new JPanel();
                previewPanel.setOpaque(false);
                previewPanel.setLayout(new BoxLayout(previewPanel, BoxLayout.Y_AXIS));
                previewPanel.setAlignmentX(LEFT_ALIGNMENT);
                
                int showCount = Math.min(2, qCount);
                for (int i = 0; i < showCount; i++) {
                    JLabel q = new JLabel("• " + truncate(qs[i], 45));
                    q.setFont(q.getFont().deriveFont(Font.ITALIC, 11f));
                    q.setForeground(TEXT_MUTED);
                    q.setAlignmentX(LEFT_ALIGNMENT);
                    previewPanel.add(q);
                    if (i < showCount - 1) previewPanel.add(Box.createVerticalStrut(2));
                }
                if (qCount > 2) {
                    JLabel more = new JLabel("+" + (qCount - 2) + " more...");
                    more.setFont(more.getFont().deriveFont(Font.ITALIC, 10f));
                    more.setForeground(new Color(160, 170, 185));
                    more.setAlignmentX(LEFT_ALIGNMENT);
                    previewPanel.add(Box.createVerticalStrut(2));
                    previewPanel.add(more);
                }
                content.add(previewPanel);
            }
            
            // Custom badge
            if (template.isCustom()) {
                content.add(Box.createVerticalStrut(8));
                JLabel customBadge = new JLabel("✦ Custom");
                customBadge.setFont(customBadge.getFont().deriveFont(Font.BOLD, 10f));
                customBadge.setForeground(new Color(100, 160, 100));
                customBadge.setAlignmentX(LEFT_ALIGNMENT);
                content.add(customBadge);
            }
            
            add(content, BorderLayout.CENTER);
            
            // Mouse interaction - instant state change, no animation
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    updateVisualState();
                }
                
                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    updateVisualState();
                }
                
                @Override
                public void mouseClicked(MouseEvent e) {
                    selectCard(TemplateCard.this);
                    if (e.getClickCount() >= 2) {
                        accept();
                    }
                }
            });

            updateVisualState();
        }
        
        void setSelected(boolean sel) {
            this.selected = sel;
            updateVisualState();
        }

        private void updateVisualState() {
            Color border = selected ? ACCENT : (hovered ? CARD_BORDER_HOVER : CARD_BORDER);
            setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(14, border),
                contentPadding
            ));
            repaint();
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            // Plain white background
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);

            if (selected) {
                int cx = getWidth() - 24;
                int cy = 16;
                g2.setColor(ACCENT);
                g2.fillOval(cx - 10, cy - 10, 20, 20);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(cx - 5, cy, cx - 1, cy + 4);
                g2.drawLine(cx - 1, cy + 4, cx + 6, cy - 4);
            }
            g2.dispose();
        }
        
        private String truncate(String s, int max) {
            return s.length() > max ? s.substring(0, max - 3) + "..." : s;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // FILTER CHIP
    // ═══════════════════════════════════════════════════════════════════════════
    
    private class FilterChip extends JPanel {
        private final String label;
        private final String key;
        private boolean selected = false;
        private boolean hovered = false;
        
        FilterChip(String label, String key) {
            this.label = label;
            this.key = key;
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(80, 30));
            
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    repaint();
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    repaint();
                }
                @Override
                public void mouseClicked(MouseEvent e) {
                    selectFilter(FilterChip.this);
                }
            });
        }
        
        void setSelected(boolean sel) {
            this.selected = sel;
            repaint();
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            
            Color bg = selected ? ACCENT : (hovered ? new Color(235, 240, 248) : new Color(245, 247, 252));
            Color fg = selected ? Color.WHITE : TEXT_SECONDARY;
            
            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16));
            
            g2.setColor(fg);
            g2.setFont(getFont().deriveFont(selected ? Font.BOLD : Font.PLAIN, 12f));
            FontMetrics fm = g2.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(label)) / 2;
            int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(label, x, y);
            
            g2.dispose();
        }
    }
    
    private void selectFilter(FilterChip chip) {
        // Deselect all
        for (Component c : filterPanel.getComponents()) {
            if (c instanceof FilterChip fc) {
                fc.setSelected(false);
            }
        }
        chip.setSelected(true);
        activeFilter = chip.key;
        refreshCards();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ROUNDED BORDER HELPER
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static class RoundedBorder implements javax.swing.border.Border {
        private final int radius;
        private final Color color;
        
        RoundedBorder(int radius, Color color) {
            this.radius = radius;
            this.color = color;
        }
        
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.draw(new RoundRectangle2D.Float(x, y, w-1, h-1, radius, radius));
            g2.dispose();
        }
        
        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(1, 1, 1, 1);
        }
        
        @Override
        public boolean isBorderOpaque() {
            return false;
        }
    }
}
