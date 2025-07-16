package main.ui.panels;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import main.dialog.CustomMessageDialog;
import main.transitions.FadingButton;
import main.ui.JournalApp;
import main.ui.buttons.RoundedButton;
import main.ui.components.MoodSlider;
import main.util.AppDirectories;

public class NewEntryPanel extends JPanel {
    protected CardLayout cardLayout;
    protected JPanel cardPanel;
    protected File journalFolder;
    protected JournalApp app;

    // UI components for the entry
    protected JTextField titleField;
    protected JTextArea contentArea;
    protected MoodSlider moodSlider;

    // Background image (paper)
    private BufferedImage backgroundImage;

    public NewEntryPanel(JournalApp app, File journalFolder, CardLayout cardLayout, JPanel cardPanel) {
        this.app = app;
        this.journalFolder = journalFolder;
        this.cardLayout = cardLayout;
        this.cardPanel = cardPanel;
        setLayout(new BorderLayout());
        setOpaque(false);
        setBackground(new Color(250, 250, 245)); // An off-white, paper-like color
        initUI();
    }

    // Load the paper background image from "img/paper.png"
    /*
    private void loadBackground() {
        try {
            backgroundImage = ImageIO.read(new File("Simjournal/img/paper.png"));
        } catch (IOException ex) {
            ex.printStackTrace();
            backgroundImage = null;
        }
    }
    */

    // Paint the background image scaled to fill the panel.
    @Override
    protected void paintComponent(Graphics g) {
        // Since we're not using a background image anymore,
        // we can just let the default paintComponent handle the solid color.
        super.paintComponent(g);
    }

    private void initUI() {
        // --- Modern Flat Toolbar (matching DrawingPanel) ---
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.setBackground(new Color(230,230,230));

        // Back button
        RoundedButton backButton = new RoundedButton("Back");
        backButton.addActionListener(e -> app.switchCard(JournalApp.MAIN_MENU));
        toolbar.add(backButton);

        // Title label & field
        JLabel titleLabel = new JLabel("Title:");
        titleLabel.setForeground(Color.DARK_GRAY);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        toolbar.add(Box.createHorizontalStrut(6));
        toolbar.add(titleLabel);

        titleField = new ModernTextField(24);
        titleField.setFont(new Font("SansSerif", Font.PLAIN, 16));
        toolbar.add(titleField);

        // Font buttons (A- / A+)
        RoundedButton decFont = new RoundedButton("A-");
        RoundedButton incFont = new RoundedButton("A+");
        decFont.addActionListener(e -> changeFontSize(-1));
        incFont.addActionListener(e -> changeFontSize(1));
        toolbar.add(Box.createHorizontalStrut(6));
        toolbar.add(decFont);
        toolbar.add(incFont);

        add(toolbar, BorderLayout.NORTH);

        // --- Middle Panel: Mood Buttons and Content Area ---
        JPanel middlePanel = new JPanel(new BorderLayout(5,5));
        middlePanel.setOpaque(false);

        // Mood Buttons Panel
        JPanel moodPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 5));
        moodPanel.setOpaque(false);
        moodSlider = new MoodSlider();
        moodPanel.add(moodSlider);

        middlePanel.add(moodPanel, BorderLayout.NORTH);

        // Content Area: Text editor
        contentArea = new JTextArea();
        contentArea.setFont(new Font("SansSerif", Font.PLAIN, 16));
        contentArea.setLineWrap(true);
        contentArea.setWrapStyleWord(true);
        contentArea.setOpaque(false);
        contentArea.setForeground(Color.DARK_GRAY);

        // Add a listener to update the word count
        contentArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                updateWordCount();
            }
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                updateWordCount();
            }
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                updateWordCount();
            }
        });

        JScrollPane scrollPane = new JScrollPane(contentArea);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);

        middlePanel.add(scrollPane, BorderLayout.CENTER);

        add(middlePanel, BorderLayout.CENTER);

        // --- Bottom Panel: Save Button ---
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.setOpaque(false);
        FadingButton saveButton = new FadingButton("Save Entry");
        saveButton.setBackground(Color.DARK_GRAY);
        saveButton.setForeground(Color.WHITE);
        saveButton.addActionListener(e -> saveEntry());

        // Word count label
        JLabel wordCountLabel = new JLabel("Words: 0");
        wordCountLabel.setForeground(Color.GRAY);
        wordCountLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
        wordCountLabel.setBorder(new EmptyBorder(0, 10, 5, 0));
        bottomPanel.add(wordCountLabel);
        bottomPanel.add(saveButton);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void updateWordCount() {
        String text = contentArea.getText();
        String[] words = text.trim().split("\\s+");
        int count = text.trim().isEmpty() ? 0 : words.length;
        
        // Find the word count label and update it
        // This is a bit of a workaround, but effective for this structure
        for (Component comp : getComponents()) {
            if (comp instanceof JPanel) {
                JPanel bottomPanel = (JPanel) comp;
                for (Component innerComp : bottomPanel.getComponents()) {
                    if (innerComp instanceof JLabel && ((JLabel) innerComp).getText().startsWith("Words:")) {
                        ((JLabel) innerComp).setText("Words: " + count);
                        return;
                    }
                }
            }
        }
    }

    // Called by the "Save Entry" button.
    // This is overridden in EditEntryPanel to update an existing file.
    protected void saveEntry() {
        String title = titleField.getText().trim();
        String content = contentArea.getText();
        if (title.isEmpty() && content.trim().isEmpty()) {
            new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this), "Error", "Please enter a title or content.", true).showDialog();
            return;
        }
        int moodValue = moodSlider.getValue(); // 0 - 100
        recordMood(moodValue);

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
            String timestamp = sdf.format(new Date());
            String filename = timestamp + ".txt";
            File file = new File(journalFolder, filename);
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                writer.println(title);
                writer.println(); // Blank line for separation
                writer.print(content); // Use print to avoid extra newline
            }
            new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this), "Success", "Journal entry saved successfully!", false).showDialog();
            app.switchCard(JournalApp.MAIN_MENU);
        } catch (IOException ex) {
            ex.printStackTrace();
            new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this), "Error", "Error saving entry.", true).showDialog();
        }
    }

    private void recordMood(int moodValue) {
        File moodFile = new File(AppDirectories.folder(AppDirectories.Type.MOOD_DATA), "mood_log.txt");
        try (PrintWriter writer = new PrintWriter(new FileWriter(moodFile, true))) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
            String timestamp = sdf.format(new Date());
            writer.println(timestamp + "," + moodValue);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void changeFontSize(int delta){
        Font f = contentArea.getFont();
        int newSize = Math.max(8, Math.min(72, f.getSize()+delta));
        contentArea.setFont(f.deriveFont((float)newSize));
    }

    // Modern rounded text field identical to NotePanel
    private static class ModernTextField extends JTextField{
        public ModernTextField(int cols){ super(cols); setOpaque(false); setBorder(BorderFactory.createEmptyBorder(6,10,6,10)); }
        @Override protected void paintComponent(Graphics g){
            Graphics2D g2=(Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0,0,getWidth(),getHeight(),10,10);
            super.paintComponent(g2);
            g2.dispose();
        }
        @Override protected void paintBorder(Graphics g){
            Graphics2D g2=(Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.LIGHT_GRAY);
            g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,10,10);
            g2.dispose();
        }
    }
}

enum Mood {
    HAPPY, NEUTRAL, SAD
}

class MoodButton extends JToggleButton {
    private Mood mood;
    private final Color SELECTED_COLOR = new Color(135, 206, 250); // Light Sky Blue
    
    public MoodButton(Mood mood) {
        this.mood = mood;
        init();
    }
    
    private void init() {
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorderPainted(false);
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        int width = getWidth();
        int height = getHeight();
        
        // Draw background circle based on selection state
        if (isSelected()) {
            g2.setColor(SELECTED_COLOR);
            g2.fillOval(0, 0, width, height);
        } else {
            g2.setColor(Color.LIGHT_GRAY);
            g2.drawOval(0, 0, width-1, height-1);
        }
        
        g2.setColor(Color.DARK_GRAY);
        g2.setStroke(new BasicStroke(2));
        
        // Draw minimalist face
        switch (mood) {
            case HAPPY:
                g2.drawArc(width/4, height/4, width/2, height/2, 200, 140); // Smile
                g2.fillOval(width/4 + 5, height/3, 4, 4); // Left eye
                g2.fillOval(width*3/4 - 10, height/3, 4, 4); // Right eye
                break;
            case NEUTRAL:
                g2.drawLine(width/4, height/2, width*3/4, height/2); // Mouth
                g2.fillOval(width/4 + 5, height/3, 4, 4); // Left eye
                g2.fillOval(width*3/4 - 10, height/3, 4, 4); // Right eye
                break;
            case SAD:
                g2.drawArc(width/4, height/2, width/2, height/2, 20, 140); // Frown
                g2.fillOval(width/4 + 5, height/3, 4, 4); // Left eye
                g2.fillOval(width*3/4 - 10, height/3, 4, 4); // Right eye
                break;
        }
        g2.dispose();
    }
}
