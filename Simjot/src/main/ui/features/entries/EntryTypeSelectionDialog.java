package main.ui.features.entries;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.*;
import main.ui.components.containers.RoundedPanel;
import main.ui.components.buttons.RoundedButton;

/**
 * Dialog that presents different journal entry types/templates to choose from
 * before creating a new entry. Includes options like gratitude, anxiety, daily log, etc.
 */
public class EntryTypeSelectionDialog extends JDialog {
    private EntryTemplate selectedTemplate;
    private boolean accepted = false;

    public enum EntryTemplate {
        BLANK("Blank Entry", "Start with a fresh page", new String[0]),
        GRATITUDE("Gratitude Journal", "What are you grateful for today?", new String[]{
            "What are you grateful for today? (Item 1)",
            "What else are you grateful for? (Item 2)",
            "One more thing you're grateful for (Item 3)"
        }),
        ANXIETY("Anxiety Processing", "Work through anxious thoughts", new String[]{
            "What's making you anxious?",
            "What's within your control?",
            "What can you let go of?"
        }),
        DAILY_LOG("Daily Log", "Recap your day", new String[]{
            "How was your morning?",
            "How was your afternoon?",
            "How was your evening?",
            "What were the highlights of today?"
        }),
        MOOD_TRACKER("Mood & Energy", "Track how you're feeling", new String[]{
            "What's your current mood and energy level?",
            "What influenced your mood today?",
            "What helped you feel better?"
        }),
        GOAL_PLANNING("Goal Planning", "Set intentions and plan", new String[]{
            "What are your top 3 priorities for today?",
            "What are your goals for this week?",
            "What are your goals for this month?"
        }),
        REFLECTION("Evening Reflection", "Reflect on your day", new String[]{
            "What went well today?",
            "What could have gone better?",
            "What did you learn today?",
            "What will you do tomorrow?"
        });

        private final String title;
        private final String description;
        private final String[] questions;

        EntryTemplate(String title, String description, String[] questions) {
            this.title = title;
            this.description = description;
            this.questions = questions;
        }

        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String[] getQuestions() { return questions; }
        public boolean hasGuidedMode() { return questions.length > 0; }
    }

    public EntryTypeSelectionDialog(Frame parent) {
        super(parent, "Choose Entry Type", true);
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
        
        mainPanel.add(topBar, BorderLayout.NORTH);

        // Grid of template cards
        JPanel grid = new JPanel(new GridLayout(0, 2, 16, 16));
        grid.setOpaque(true);
        grid.setBackground(Color.WHITE);
        grid.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        for (EntryTemplate template : EntryTemplate.values()) {
            grid.add(createTemplateCard(template));
        }

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

    private JPanel createTemplateCard(EntryTemplate template) {
        JPanel card = new JPanel() {
            private boolean hover = false;
            private boolean pressed = false;

            {
                setOpaque(false);
                setLayout(new BorderLayout(8, 8));
                setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int arc = 12;
                int w = getWidth();
                int h = getHeight();

                // Background
                if (pressed) {
                    g2.setColor(new Color(220, 230, 255));
                } else if (hover) {
                    g2.setColor(new Color(240, 245, 255));
                } else {
                    g2.setColor(new Color(248, 249, 252));
                }
                g2.fillRoundRect(0, 0, w, h, arc, arc);

                // Border
                if (hover || pressed) {
                    g2.setColor(new Color(88, 133, 255, pressed ? 200 : 120));
                    g2.setStroke(new BasicStroke(2f));
                } else {
                    g2.setColor(new Color(210, 216, 228));
                    g2.setStroke(new BasicStroke(1f));
                }
                g2.drawRoundRect(1, 1, w - 2, h - 2, arc, arc);

                g2.dispose();
                super.paintComponent(g);
            }

            @Override
            protected void processMouseEvent(MouseEvent e) {
                switch (e.getID()) {
                    case MouseEvent.MOUSE_ENTERED -> { hover = true; repaint(); }
                    case MouseEvent.MOUSE_EXITED -> { hover = false; pressed = false; repaint(); }
                    case MouseEvent.MOUSE_PRESSED -> { pressed = true; repaint(); }
                    case MouseEvent.MOUSE_RELEASED -> { pressed = false; repaint(); }
                }
                super.processMouseEvent(e);
            }
        };

        // Text content
        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        JLabel titleLbl = new JLabel(template.getTitle());
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

        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                selectedTemplate = template;
                accepted = true;
                setVisible(false);
                dispose();
            }
        });

        return card;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public EntryTemplate getSelectedTemplate() {
        return selectedTemplate;
    }

    public String[] getGuidedQuestions() {
        return selectedTemplate != null ? selectedTemplate.getQuestions() : null;
    }

    public boolean isGuidedMode() {
        return selectedTemplate != null && selectedTemplate.hasGuidedMode();
    }
}
