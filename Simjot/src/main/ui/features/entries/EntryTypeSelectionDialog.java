package main.ui.features.entries;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.*;
import main.infrastructure.backup.NotebookInfo;
import main.ui.components.containers.RoundedPanel;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.buttons.ToolbarIconButton;

/**
 * Dialog that presents different journal entry types/templates to choose from
 * before creating a new entry. Includes options like gratitude, anxiety, daily log, etc.
 */
public class EntryTypeSelectionDialog extends JDialog {
    private JournalTemplateManager.JournalTemplate selectedTemplate;
    private boolean accepted = false;
    private final JPanel grid;
    private final NotebookInfo notebook;


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

        // Top toolbar-style header
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(230, 230, 230));
        topBar.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        
        JLabel titleLabel = new JLabel("What would you like to write about?");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setForeground(new Color(40, 40, 40));
        topBar.add(titleLabel, BorderLayout.WEST);
        
        // Manage templates button
        ToolbarIconButton manageBtn = new ToolbarIconButton("options");
        manageBtn.setToolTipText("Manage Templates");
        manageBtn.addActionListener(e -> openTemplateManager());
        topBar.add(manageBtn, BorderLayout.EAST);
        
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

        RoundedButton cancelBtn = new RoundedButton("Cancel");
        cancelBtn.setPreferredSize(new Dimension(100, 36));
        cancelBtn.setForeground(Color.DARK_GRAY);
        cancelBtn.addActionListener(e -> {
            accepted = false;
            setVisible(false);
            dispose();
        });

        bottomPanel.add(cancelBtn);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(mainPanel);
        setSize(720, 580);
        setLocationRelativeTo(parent);
    }

    private void refreshTemplates() {
        grid.removeAll();
        List<JournalTemplateManager.JournalTemplate> templates =
                (notebook != null)
                        ? JournalTemplateManager.getInstance().getTemplates(notebook)
                        : JournalTemplateManager.getInstance().getTemplates();
        for (JournalTemplateManager.JournalTemplate template : templates) {
            grid.add(createTemplateCard(template));
        }
        grid.revalidate();
        grid.repaint();
    }

    private JPanel createTemplateCard(JournalTemplateManager.JournalTemplate template) {
        RoundedPanel card = new RoundedPanel();
        card.setArc(12);
        card.setLayout(new BorderLayout(8, 8));
        card.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.setBackground(new Color(248, 249, 252));

        // Text content
        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        JLabel titleLbl = new JLabel(template.getName());
        titleLbl.setFont(titleLbl.getFont().deriveFont(Font.BOLD, 14f));
        titleLbl.setForeground(new Color(40, 40, 40));
        titleLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel descLbl = new JLabel("<html><center>" + template.getDescription() + "</center></html>");
        descLbl.setFont(descLbl.getFont().deriveFont(12f));
        descLbl.setForeground(new Color(100, 100, 100));
        descLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        textPanel.add(titleLbl);
        textPanel.add(Box.createVerticalStrut(4));
        textPanel.add(descLbl);

        card.add(textPanel, BorderLayout.CENTER);

        // Hover and click effects
        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                selectedTemplate = template;
                accepted = true;
                setVisible(false);
                dispose();
            }
            
            @Override
            public void mouseEntered(MouseEvent e) {
                card.setBackground(new Color(240, 245, 255));
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                card.setBackground(new Color(248, 249, 252));
            }
            
            @Override
            public void mousePressed(MouseEvent e) {
                card.setBackground(new Color(220, 230, 255));
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                card.setBackground(new Color(240, 245, 255));
            }
        });

        return card;
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
