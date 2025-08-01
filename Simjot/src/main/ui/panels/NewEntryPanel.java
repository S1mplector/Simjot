package main.ui.panels;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import main.dialog.CustomMessageDialog;
import main.dialog.EntryBackgroundDialog;
import main.ui.JournalApp;
import main.ui.buttons.RoundedButton;
import main.ui.components.MoodSlider;
import main.util.AppDirectories;
import main.util.ResourceLoader;
import main.util.SettingsStore;
import main.util.UndoRedoManager;

public class NewEntryPanel extends JPanel {
    protected CardLayout cardLayout;
    protected JPanel cardPanel;
    protected File journalFolder;
    protected JournalApp app;

    // UI components for the entry
    protected JTextField titleField;
    protected JTextArea contentArea;
    protected MoodSlider moodSlider;
    private Image backgroundImage;
    private BufferedImage cachedScaled;
    private int cachedPanelW = -1;
    private int cachedPanelH = -1;
    private int cachedX = 0;
    private int cachedY = 0;
    private float cachedOpacity = -1f;
    private File currentFile = null; // Track the current file being edited



    public NewEntryPanel(JournalApp app, File journalFolder, CardLayout cardLayout, JPanel cardPanel) {
        this.app = app;
        this.journalFolder = journalFolder;
        this.cardLayout = cardLayout;
        this.cardPanel = cardPanel;
        setLayout(new BorderLayout());
        setOpaque(false);
        // Set a transparent background so the parent's background can show through
        setBackground(new Color(0, 0, 0, 0));
        initUI();
    }

    // Load the paper background image from "img/paper.png"
    /*
    private void loadBackground() {
        try {
            backgroundImage = ImageIO.read(new File("Simjot/img/paper.png"));
        } catch (IOException ex) {
            ex.printStackTrace();
            backgroundImage = null;
        }
    }
    */

    // Paint the background image scaled to fill the panel.
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        // Draw the background image with opacity if available
        String bgPath = SettingsStore.get().getEntryBackgroundImage();
        if (bgPath != null && !bgPath.isEmpty()) {
            // Load the background image if not already loaded
            if (backgroundImage == null) {
                if (bgPath.startsWith("res:")) {
                    // Built-in resource
                    String resPath = bgPath.substring(4);
                    backgroundImage = ResourceLoader.createImage("Simjot/" + resPath);
                } else {
                    // User-selected file
                    backgroundImage = new ImageIcon(bgPath).getImage();
                }
            }
            
            if (backgroundImage != null) {
                int panelW = getWidth();
                int panelH = getHeight();
                float opacity = SettingsStore.get().getEntryBackgroundOpacity();
                
                // Recreate cache only if necessary
                if (cachedScaled == null || panelW != cachedPanelW || panelH != cachedPanelH || opacity != cachedOpacity) {
                    int imgW = backgroundImage.getWidth(this);
                    int imgH = backgroundImage.getHeight(this);
                    
                    if (imgW > 0 && imgH > 0) {
                        // Calculate scale factor to cover the panel while maintaining aspect ratio
                        double scale = Math.max((double) panelW / imgW, (double) panelH / imgH);
                        int drawW = (int) Math.round(imgW * scale);
                        int drawH = (int) Math.round(imgH * scale);
                        
                        cachedX = (panelW - drawW) / 2;
                        cachedY = (panelH - drawH) / 2;
                        cachedPanelW = panelW;
                        cachedPanelH = panelH;
                        cachedOpacity = opacity;
                        
                        // Create a new image with the current opacity
                        BufferedImage tmp = new BufferedImage(drawW, drawH, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D cg = tmp.createGraphics();
                        
                        // Set the composite with the current opacity
                        AlphaComposite ac = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity);
                        cg.setComposite(ac);
                        
                        // Draw the image with the applied opacity
                        cg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        cg.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                        cg.drawImage(backgroundImage, 0, 0, drawW, drawH, this);
                        cg.dispose();
                        
                        cachedScaled = tmp;
                    }
                }
                
                // Draw the cached image
                if (cachedScaled != null) {
                    g.drawImage(cachedScaled, cachedX, cachedY, this);
                }
            }
        }
    }

    private void initUI() {
        // --- Extended Toolbar with Mood Slider ---
        JPanel toolbarContainer = new JPanel(new BorderLayout(0, 5));
        toolbarContainer.setBackground(new Color(230, 230, 230, 200)); // Semi-transparent gray
        toolbarContainer.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top toolbar row
        JPanel topToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topToolbar.setOpaque(false);

        // Back button
        RoundedButton backButton = new RoundedButton("Back");
        backButton.addActionListener(e -> app.switchCard(JournalApp.MAIN_MENU));
        topToolbar.add(backButton);
        
        // Background button
        RoundedButton bgButton = new RoundedButton("Background");
        bgButton.addActionListener(e -> {
            EntryBackgroundDialog dialog = new EntryBackgroundDialog((java.awt.Frame)SwingUtilities.getWindowAncestor(this));
            dialog.setVisible(true);
            // Refresh the background if it was changed
            backgroundImage = null;
            cachedScaled = null;
            repaint();
        });
        topToolbar.add(bgButton);

        // Title label & field
        JLabel titleLabel = new JLabel("Title:");
        titleLabel.setForeground(Color.DARK_GRAY);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        topToolbar.add(Box.createHorizontalStrut(6));
        topToolbar.add(titleLabel);

        titleField = new ModernTextField(24);
        titleField.setFont(new Font("SansSerif", Font.PLAIN, 16));
        topToolbar.add(titleField);

        // Font buttons (A- / A+)
        RoundedButton decFont = new RoundedButton("A-");
        RoundedButton incFont = new RoundedButton("A+");
        decFont.addActionListener(e -> changeFontSize(-1));
        incFont.addActionListener(e -> changeFontSize(1));
        topToolbar.add(Box.createHorizontalStrut(6));
        topToolbar.add(decFont);
        topToolbar.add(incFont);

        // Bottom toolbar row with mood slider
        JPanel bottomToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottomToolbar.setOpaque(false);
        
        moodSlider = new MoodSlider();
        bottomToolbar.add(moodSlider);

        // Add both toolbar rows to the container
        toolbarContainer.add(topToolbar, BorderLayout.NORTH);
        toolbarContainer.add(bottomToolbar, BorderLayout.CENTER);

        add(toolbarContainer, BorderLayout.NORTH);

        // --- Content Area ---
        JPanel contentWrapper = new JPanel(new BorderLayout());
        contentWrapper.setOpaque(false);

        // Content Area: Text editor with undo/redo support
        contentArea = new JTextArea();
        
        // Load font size directly from settings to ensure persistence
        int savedFontSize = SettingsStore.get().getJournalFontSize();
        contentArea.setFont(new Font("Serif", Font.PLAIN, savedFontSize));
        contentArea.setLineWrap(true);
        contentArea.setWrapStyleWord(true);
        contentArea.setOpaque(false);
        contentArea.setForeground(Color.DARK_GRAY);
        
        // Add undo/redo support
        @SuppressWarnings("unused")
        UndoRedoManager contentUndoManager = new UndoRedoManager(contentArea);
        @SuppressWarnings("unused")
        UndoRedoManager titleUndoManager = new UndoRedoManager(titleField);
        
        // Add document listener for word count
        contentArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { updateWordCount(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { updateWordCount(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { updateWordCount(); }
        });
        
        JScrollPane scrollPane = new JScrollPane(contentArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        
        // Add scroll pane to the content wrapper
        contentWrapper.add(scrollPane, BorderLayout.CENTER);
        
        // Add the content wrapper to the main panel
        add(contentWrapper, BorderLayout.CENTER);

        // --- Bottom Panel: Save Button ---
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.setOpaque(false);
        
        RoundedButton saveButton = new RoundedButton("Save Entry");
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
            File file;
            boolean isNewFile = false;
            
            if (currentFile == null) {
                // First save - create new file
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
                String timestamp = sdf.format(new Date());
                String filename = timestamp + ".txt";
                file = new File(journalFolder, filename);
                currentFile = file;
                isNewFile = true;
            } else {
                // Subsequent saves - use existing file
                file = currentFile;
            }
            
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                writer.println(title);
                writer.println(); // Blank line for separation
                writer.print(content); // Use print to avoid extra newline
            }
            
            String message = isNewFile ? "Journal entry saved successfully!" : "Journal entry updated successfully!";
            new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this), "Success", message, false).showDialog();
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
