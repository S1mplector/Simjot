package main.ui.panels;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;
import main.dialog.CustomMessageDialog;
import main.transitions.FadingButton;
import main.ui.JournalApp;
import main.ui.buttons.RoundedButton;

public class PoemPanel extends JPanel {
    protected CardLayout cardLayout;
    protected JPanel cardPanel;
    protected File journalFolder;
    protected JournalApp app;

    // Components for poem writing
    protected JTextField poemTitleField;
    protected JTextArea poemTextArea;

    private final String[] INSPIRATIONAL_WORDS = {
        "Ethereal", "Ephemeral", "Sonder", "Solitude", "Cascade", "Labyrinthine",
        "Mellifluous", "Nostalgia", "Petrichor", "Ineffable", "Serendipity", "Halcyon",
        "Luminescence", "Redolent", "Somnambulist", "Susurrus", "Opalescent", "Reverie"
    };

    // Floral background image
    private BufferedImage bgImage;

    public PoemPanel(JournalApp app, File journalFolder, CardLayout cardLayout, JPanel cardPanel) {
        this.app = app;
        this.journalFolder = journalFolder;
        this.cardLayout = cardLayout;
        this.cardPanel = cardPanel;
        setLayout(new BorderLayout());
        setOpaque(false);
        loadBackground();
        initUI();
    }

    // Load a floral or pastel image from img/poem_bg.jpg
    private void loadBackground() {
        try {
            bgImage = ImageIO.read(new File("Simjournal/img/poem.png")); 
            // Change the filename above if yours is different
        } catch (IOException ex) {
            ex.printStackTrace();
            bgImage = null;
        }
    }

    // Paint the background image scaled to fill the entire panel.
    @Override
    protected void paintComponent(Graphics g) {
        if (bgImage != null) {
            g.drawImage(bgImage, 0, 0, getWidth(), getHeight(), this);
        }
        super.paintComponent(g);
    }

    private void initUI() {
        // --- Top Panel with a fancy rounded background for the poem title ---
        RoundedPanel topPanel = new RoundedPanel();
        topPanel.setBackground(new Color(255, 255, 255, 180));
        topPanel.setLayout(new BorderLayout(5, 5));
        topPanel.setOpaque(false);
        topPanel.setPreferredSize(new Dimension(0, 60));

        JLabel titleLabel = new JLabel("Poem Title:");
        titleLabel.setFont(new Font("Serif", Font.BOLD, 18));
        titleLabel.setForeground(new Color(60, 50, 50)); // a soft dark color
        topPanel.add(titleLabel, BorderLayout.WEST);

        poemTitleField = new JTextField();
        poemTitleField.setFont(new Font("Serif", Font.BOLD, 16));
        poemTitleField.setForeground(new Color(70, 60, 60));
        poemTitleField.setOpaque(false);
        poemTitleField.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        topPanel.add(poemTitleField, BorderLayout.CENTER);

        // --- Font Selection & Back Button ---
        JPanel eastControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        eastControls.setOpaque(false);

        String[] fonts = {"Serif", "Georgia", "Verdana", "Cursive"};
        JComboBox<String> fontSelector = new JComboBox<>(fonts);
        fontSelector.setUI(new StyledComboBoxUI());
        fontSelector.setBackground(new Color(230, 220, 250, 200));
        fontSelector.setSelectedItem("Serif"); // Default font
        fontSelector.addActionListener(e -> {
            String selectedFont = (String) fontSelector.getSelectedItem();
            Font currentFont = poemTextArea.getFont();
            poemTextArea.setFont(new Font(selectedFont, currentFont.getStyle(), currentFont.getSize()));
        });
        eastControls.add(new JLabel("Font:"));
        eastControls.add(fontSelector);

        // Font size buttons
        RoundedButton decFont = new RoundedButton("A-");
        RoundedButton incFont = new RoundedButton("A+");
        decFont.setPreferredSize(new Dimension(40,24));
        incFont.setPreferredSize(new Dimension(40,24));
        decFont.addActionListener(e->changeFontSize(-1));
        incFont.addActionListener(e->changeFontSize(1));
        eastControls.add(decFont);
        eastControls.add(incFont);

        // "Back" button in pastel style
        FadingButton backButton = new FadingButton("Back");
        backButton.setBackground(new Color(180, 170, 220)); 
        // Slightly pastel purple 
        backButton.setForeground(Color.WHITE);
        backButton.addActionListener(e -> app.switchCard(JournalApp.MAIN_MENU));
        eastControls.add(backButton);

        topPanel.add(eastControls, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);

        // --- Center Panel: Poem Text Area with a cursive feel ---
        JPanel textWrapper = new TranslucentPanel(); // Use the new panel
        textWrapper.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        poemTextArea = new JTextArea();
        poemTextArea.setLineWrap(true);
        poemTextArea.setWrapStyleWord(true);
        poemTextArea.setOpaque(false); // Make the text area fully transparent
        poemTextArea.setForeground(new Color(40, 40, 40));
        poemTextArea.setFont(new Font("Serif", Font.ITALIC, 16));
        /*
          NOTE: If you want a truly cursive font, pick one installed on your system, 
          e.g. new Font("Gabriola", Font.PLAIN, 18) or "Lucida Handwriting", etc.
        */

        JScrollPane scrollPane = new JScrollPane(poemTextArea);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        textWrapper.add(scrollPane, BorderLayout.CENTER);

        // Add some vertical space between title and text area
        JPanel centerContainer = new JPanel(new BorderLayout());
        centerContainer.setOpaque(false);
        centerContainer.add(Box.createRigidArea(new Dimension(0, 15)), BorderLayout.NORTH);
        centerContainer.add(textWrapper, BorderLayout.CENTER);
        add(centerContainer, BorderLayout.CENTER);

        // --- Bottom Panel: "Save Poem" button and Stanza Counter ---
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        JLabel stanzaLabel = new JLabel("Stanzas: 1");
        stanzaLabel.setForeground(Color.DARK_GRAY);
        stanzaLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
        bottomPanel.add(stanzaLabel, BorderLayout.WEST);
        
        // Listener to update the stanza count
        poemTextArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateStanzaCount(stanzaLabel); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateStanzaCount(stanzaLabel); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateStanzaCount(stanzaLabel); }
        });

        // Add the "Inspire Me" button to the center
        FadingButton inspireButton = new FadingButton("✨ Inspire Me");
        inspireButton.addActionListener(e -> showInspirationalWord());
        JPanel centerFlow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        centerFlow.setOpaque(false);
        centerFlow.add(inspireButton);
        bottomPanel.add(centerFlow, BorderLayout.CENTER);

        FadingButton saveButton = new FadingButton("Save Poem");
        saveButton.setBackground(new Color(220, 150, 150)); // pastel pinkish
        saveButton.setForeground(Color.WHITE);
        saveButton.addActionListener(e -> savePoem());
        bottomPanel.add(saveButton, BorderLayout.EAST);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void updateStanzaCount(JLabel label) {
        String text = poemTextArea.getText();
        if (text.trim().isEmpty()) {
            label.setText("Stanzas: 0");
            return;
        }
        // A stanza is a block of text separated by one or more newlines
        int stanzas = text.split("\\n\\s*\\n").length;
        label.setText("Stanzas: " + stanzas);
    }

    private void showInspirationalWord() {
        Random rand = new Random();
        String word = INSPIRATIONAL_WORDS[rand.nextInt(INSPIRATIONAL_WORDS.length)];
        
        // Use the new custom dialog
        CustomInspirationDialog dialog = new CustomInspirationDialog((JFrame) SwingUtilities.getWindowAncestor(this), word);
        dialog.setVisible(true);
    }

    // "Save Poem" logic for a new poem
    protected void savePoem() {
        String title = poemTitleField.getText().trim();
        String content = poemTextArea.getText();
        if (title.isEmpty() && content.trim().isEmpty()) {
            new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this), "Error", "Please enter a title or some content for your poem.", true).showDialog();
            return;
        }
        // 'journalFolder' already points to the poems directory provided by AppDirectories
        if (!journalFolder.exists()) {
            journalFolder.mkdirs();
        }
        // Use a timestamp-based filename
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String timestamp = sdf.format(new Date());
        String filename = timestamp + ".poem";
        File poemFile = new File(journalFolder, filename);
        try (PrintWriter writer = new PrintWriter(new FileWriter(poemFile))) {
            writer.println(title);
            writer.println();
            writer.println(content);
            new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this), "Success", "Poem saved successfully!", false).showDialog();
            // Clear fields
            poemTitleField.setText("");
            poemTextArea.setText("");
            // Return to main menu
            app.switchCard(JournalApp.MAIN_MENU);
        } catch (IOException ex) {
            ex.printStackTrace();
            new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this), "Error", "Error saving poem.", true).showDialog();
        }
    }

    private void changeFontSize(int delta){
        Font f = poemTextArea.getFont();
        int newSize = Math.max(8, Math.min(72, f.getSize()+delta));
        poemTextArea.setFont(f.deriveFont((float)newSize));
    }
}

class CustomInspirationDialog extends JDialog {
    private float opacity = 1.0f;
    private Timer fadeOutTimer;

    public CustomInspirationDialog(JFrame parent, String word) {
        super(parent, true);
        setUndecorated(true);
        setBackground(new Color(0,0,0,0)); // Transparent background
        setLayout(new BorderLayout());
        
        JLabel label = new JLabel(word, SwingConstants.CENTER);
        label.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 28));
        label.setForeground(Color.WHITE);
        label.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        // Panel with a dark, translucent background
        JPanel contentPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(30, 30, 30, 220));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 30, 30);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        contentPanel.setOpaque(false);
        contentPanel.add(label);
        add(contentPanel);

        pack();
        setLocationRelativeTo(parent);
        
        // Fade out after a delay
        startFadeOut();
    }

    private void startFadeOut() {
        fadeOutTimer = new Timer(50, e -> {
            opacity -= 0.05f;
            if (opacity <= 0) {
                opacity = 0;
                ((Timer)e.getSource()).stop();
                setVisible(false);
                dispose();
            }
            setOpacity(opacity);
        });
        fadeOutTimer.setInitialDelay(1500); // Wait 1.5s before starting to fade
        fadeOutTimer.start();
    }
}

class TranslucentPanel extends JPanel {
    public TranslucentPanel() {
        super(new BorderLayout());
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(255, 255, 255, 160)); // White with transparency
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
        g2.dispose();
    }
}

class StyledComboBoxUI extends BasicComboBoxUI {
    @Override
    protected JButton createArrowButton() {
        JButton button = new JButton("▼");
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setForeground(new Color(80, 70, 100));
        return button;
    }

    @Override
    public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
        g.setColor(new Color(230, 220, 250, 150));
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    @Override
    protected ComboPopup createPopup() {
        BasicComboPopup popup = (BasicComboPopup) super.createPopup();
        popup.setBorder(BorderFactory.createLineBorder(new Color(200, 190, 220), 1));
        return popup;
    }
}
