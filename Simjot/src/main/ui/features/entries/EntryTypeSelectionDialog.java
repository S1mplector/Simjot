package main.ui.features.entries;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.*;
import main.infrastructure.backup.NotebookInfo;
import main.ui.components.containers.RoundedPanel;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.buttons.ToolbarIconButton;
import main.ui.components.input.AeroTextField;
import main.ui.components.containers.AeroPanel;

/**
 * Dialog that presents different journal entry types/templates to choose from
 * before creating a new entry. Includes options like gratitude, anxiety, daily log, etc.
 */
public class EntryTypeSelectionDialog extends JDialog {
    private JournalTemplateManager.JournalTemplate selectedTemplate;
    private boolean accepted = false;
    private final JPanel grid;
    private final NotebookInfo notebook;
    private java.util.List<JournalTemplateManager.JournalTemplate> allTemplates;
    private JTextField searchField;
    private RoundedButton useBtn;
    private int currentCols = 2;


    public EntryTypeSelectionDialog(Frame parent) {
        this(parent, null);
    }

    public EntryTypeSelectionDialog(Frame parent, NotebookInfo nb) {
        super(parent, "Choose Entry Type", true);
        this.notebook = nb;
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        setLayout(new BorderLayout());

        RoundedPanel mainPanel = new RoundedPanel();
        mainPanel.setArc(18);
        mainPanel.setLayout(new BorderLayout(0, 0));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        mainPanel.setBackground(Color.WHITE);

        // Top toolbar-style header (Aero panel)
        AeroPanel topBar = new AeroPanel(16);
        topBar.setLayout(new BorderLayout());
        topBar.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        
        JLabel titleLabel = new JLabel("What would you like to write about?");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setForeground(new Color(40, 40, 40));
        topBar.add(titleLabel, BorderLayout.WEST);
        
        // Search field
        JPanel rightTools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightTools.setOpaque(false);
        searchField = new AeroTextField(22);
        searchField.setToolTipText("Search templates…");
        searchField.putClientProperty("JTextField.placeholderText", "Search templates…");
        searchField.addActionListener(e -> refreshTemplates());
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){
            public void insertUpdate(javax.swing.event.DocumentEvent e){ filterAsync(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e){ filterAsync(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e){ filterAsync(); }
            private void filterAsync(){ SwingUtilities.invokeLater(() -> refreshTemplates()); }
        });
        
        // Manage templates button
        ToolbarIconButton manageBtn = new ToolbarIconButton("options");
        manageBtn.setToolTipText("Manage Templates");
        manageBtn.addActionListener(e -> openTemplateManager());
        rightTools.add(searchField);
        rightTools.add(manageBtn);
        topBar.add(rightTools, BorderLayout.EAST);
        
        mainPanel.add(topBar, BorderLayout.NORTH);

        // Grid of template cards
        grid = new JPanel(new GridLayout(0, 2, 16, 16));
        grid.setOpaque(true);
        grid.setBackground(Color.WHITE);
        grid.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        refreshTemplates();

        JScrollPane scroll = new JScrollPane(grid);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(true);
        scroll.setBackground(Color.WHITE);
        scroll.getViewport().setOpaque(true);
        scroll.getViewport().setBackground(Color.WHITE);
        // Responsive: adjust cols with width
        scroll.getViewport().addComponentListener(new java.awt.event.ComponentAdapter(){
            @Override public void componentResized(java.awt.event.ComponentEvent e){ updateGridColumns(); }
        });
        
        // Apply modern, slim scrollbars (same as poetry panel)
        JScrollBar vbar = scroll.getVerticalScrollBar();
        vbar.setUI(new main.ui.components.scrollbar.ModernScrollBarUI());
        vbar.setPreferredSize(new Dimension(10, Integer.MAX_VALUE));
        vbar.setOpaque(false);
        vbar.setUnitIncrement(16);
        JScrollBar hbar = scroll.getHorizontalScrollBar();
        hbar.setUI(new main.ui.components.scrollbar.ModernScrollBarUI());
        hbar.setPreferredSize(new Dimension(Integer.MAX_VALUE, 10));
        hbar.setOpaque(false);
        
        mainPanel.add(scroll, BorderLayout.CENTER);

        // Bottom buttons
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 12));
        bottomPanel.setOpaque(true);
        bottomPanel.setBackground(Color.WHITE);

        useBtn = new RoundedButton("Use Template");
        useBtn.setPreferredSize(new Dimension(140, 36));
        useBtn.setForeground(new Color(30, 30, 30));
        useBtn.setEnabled(false);
        useBtn.addActionListener(e -> {
            if (selectedTemplate != null) {
                accepted = true;
                setVisible(false);
                dispose();
            }
        });

        RoundedButton cancelBtn = new RoundedButton("Cancel");
        cancelBtn.setPreferredSize(new Dimension(100, 36));
        cancelBtn.setForeground(Color.DARK_GRAY);
        cancelBtn.addActionListener(e -> {
            accepted = false;
            setVisible(false);
            dispose();
        });

        bottomPanel.add(useBtn);
        bottomPanel.add(cancelBtn);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(mainPanel);
        setSize(720, 580);
        setLocationRelativeTo(parent);
    }

    private void refreshTemplates() {
        if (allTemplates == null) {
            allTemplates = (notebook != null)
                    ? JournalTemplateManager.getInstance().getTemplates(notebook)
                    : JournalTemplateManager.getInstance().getTemplates();
        } else {
            // Refresh underlying set in case manager changed
            allTemplates = (notebook != null)
                    ? JournalTemplateManager.getInstance().getTemplates(notebook)
                    : JournalTemplateManager.getInstance().getTemplates();
        }
        String q = searchField != null ? searchField.getText().trim().toLowerCase() : "";
        grid.removeAll();
        for (JournalTemplateManager.JournalTemplate t : allTemplates) {
            if (q.isEmpty() || t.getName().toLowerCase().contains(q) || t.getDescription().toLowerCase().contains(q)) {
                grid.add(createTemplateCard(t));
            }
        }
        // Add an inline "Create New" card for quick access
        grid.add(createNewTemplateCard());
        grid.revalidate();
        grid.repaint();
    }

    private JPanel createTemplateCard(JournalTemplateManager.JournalTemplate template) {
        RoundedPanel card = new RoundedPanel();
        card.setArc(12);
        card.setLayout(new BorderLayout(8, 8));
        card.setBorder(BorderFactory.createLineBorder(new Color(230,235,245)));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.setBackground(Color.WHITE);

        // Text content only (no icons)
        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        JLabel titleLbl = new JLabel(template.getName());
        titleLbl.setFont(titleLbl.getFont().deriveFont(Font.BOLD, 14f));
        titleLbl.setForeground(new Color(40, 40, 40));
        titleLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel descLbl = new JLabel("<html><center>" + template.getDescription() + "</center></html>");
        descLbl.setFont(descLbl.getFont().deriveFont(12f));
        descLbl.setForeground(new Color(100, 100, 100));
        descLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Question preview / badge
        String[] qs = template.getQuestions();
        int qCount = (qs == null ? 0 : qs.length);
        JLabel badge = new JLabel(qCount > 0 ? ("Guided • " + qCount + " Qs") : "Freeform");
        badge.setFont(badge.getFont().deriveFont(Font.PLAIN, 11f));
        badge.setForeground(new Color(90, 110, 140));
        badge.setAlignmentX(Component.CENTER_ALIGNMENT);

        String preview = "";
        if (qCount > 0) {
            String p1 = qs[0];
            String p2 = qCount > 1 ? qs[1] : null;
            preview = p1 + (p2 != null ? "<br>" + p2 : "");
        }
        JLabel previewLbl = new JLabel("<html><div style='text-align:center;color:#6D6D6D;font-size:11px;'>" + preview + "</div></html>");
        previewLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        center.add(Box.createVerticalStrut(12));
        center.add(titleLbl);
        center.add(Box.createVerticalStrut(2));
        center.add(descLbl);
        center.add(Box.createVerticalStrut(6));
        center.add(badge);
        if (qCount > 0) {
            center.add(Box.createVerticalStrut(6));
            center.add(previewLbl);
        }

        card.add(center, BorderLayout.CENTER);

        // Hover and click effects
        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                selectedTemplate = template;
                useBtn.setEnabled(true);
                // Double-click to accept immediately
                if (e.getClickCount() >= 2) {
                    accepted = true;
                    setVisible(false);
                    dispose();
                }
            }
            
            @Override
            public void mouseEntered(MouseEvent e) {
                card.setBackground(new Color(245, 248, 255));
                card.setBorder(BorderFactory.createLineBorder(new Color(170, 200, 255)));
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                card.setBackground(Color.WHITE);
                card.setBorder(BorderFactory.createLineBorder(new Color(230,235,245)));
            }
            
            @Override
            public void mousePressed(MouseEvent e) {
                card.setBackground(new Color(230, 238, 255));
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                card.setBackground(new Color(245, 248, 255));
            }
        });

        return card;
    }

    private JPanel createNewTemplateCard(){
        RoundedPanel card = new RoundedPanel();
        card.setArc(12);
        card.setLayout(new BorderLayout());
        card.setBackground(new Color(248, 249, 252));
        JLabel lbl = new JLabel("+ Create New Template", SwingConstants.CENTER);
        lbl.setBorder(BorderFactory.createEmptyBorder(24, 12, 24, 12));
        lbl.setForeground(new Color(60, 90, 160));
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 13f));
        card.add(lbl, BorderLayout.CENTER);
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.addMouseListener(new MouseAdapter(){
            @Override public void mouseClicked(MouseEvent e){ openTemplateManager(); }
            @Override public void mouseEntered(MouseEvent e){ card.setBackground(new Color(240,245,255)); }
            @Override public void mouseExited(MouseEvent e){ card.setBackground(new Color(248,249,252)); }
        });
        return card;
    }

    

    private void updateGridColumns(){
        int w = grid.getParent() != null ? grid.getParent().getWidth() : grid.getWidth();
        if (w <= 0) return;
        int padding = 40;
        int available = Math.max(200, w - padding);
        int col = Math.max(2, Math.min(4, available / 260));
        if (col != currentCols){
            currentCols = col;
            grid.setLayout(new GridLayout(0, currentCols, 16, 16));
            refreshTemplates();
        }
    }

    public boolean isAccepted() {
        return accepted;
    }

    public JournalTemplateManager.JournalTemplate getSelectedTemplate() {
        return selectedTemplate;
    }

    public String[] getGuidedQuestions() {
        return selectedTemplate != null ? selectedTemplate.getQuestions() : null;
    }

    public boolean isGuidedMode() {
        return selectedTemplate != null && selectedTemplate.getQuestions().length > 0;
    }
    
    private void openTemplateManager() {
        TemplateManagerDialog dialog = new TemplateManagerDialog(this, notebook);
        dialog.setVisible(true);
        refreshTemplates(); // Refresh after closing
    }
}
