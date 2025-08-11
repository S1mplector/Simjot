package main.ui.panels;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;
import main.dialog.CustomMessageDialog;
import main.dialog.PoemBackgroundDialog;
import main.ui.JournalApp;
import main.ui.buttons.RoundedButton;
import main.ui.buttons.ToolbarIconButton;
import main.util.ResourceLoader;
import main.util.SettingsStore;

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

    // Background system
    private Image backgroundImage;
    private BufferedImage cachedScaled;
    private int cachedPanelW = -1;
    private int cachedPanelH = -1;
    private int cachedX = 0;
    private int cachedY = 0;
    private float cachedOpacity = -1f;
    private String cachedBgPath = null;
    private File currentFile = null; // Track the current file being edited

    public PoemPanel(JournalApp app, File journalFolder, CardLayout cardLayout, JPanel cardPanel) {
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

    // Paint the background image scaled to fill the panel with white default.
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        // Draw white background first
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, getWidth(), getHeight());
        
        // Draw the background image with opacity if available
        String bgPath = SettingsStore.get().getPoemBackgroundImage();
        if (bgPath != null && !bgPath.isEmpty()) {
            // Invalidate cached image if the path changed
            if (cachedBgPath == null || !cachedBgPath.equals(bgPath)) {
                backgroundImage = null;
                cachedScaled = null;
                cachedPanelW = cachedPanelH = -1;
                cachedOpacity = -1f;
                cachedBgPath = bgPath;
            }
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
                float opacity = SettingsStore.get().getPoemBackgroundOpacity();
                
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
            } else {
                // Graceful fallback: subtle paper-like gradient when image missing
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Rectangle r = new Rectangle(0, 0, getWidth(), getHeight());
                main.ui.theme.aero.AeroPainters.paintVerticalGradient(g2, r,
                        new Color(250, 250, 250), new Color(235, 235, 235), 0);
                main.ui.theme.aero.AeroPainters.paintGlassOverlay(g2, r, 0);
                g2.dispose();
            }
        } else {
            // No path configured: draw theme fallback
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Rectangle r = new Rectangle(0, 0, getWidth(), getHeight());
            main.ui.theme.aero.AeroPainters.paintVerticalGradient(g2, r,
                    new Color(250, 250, 250), new Color(235, 235, 235), 0);
            main.ui.theme.aero.AeroPainters.paintGlassOverlay(g2, r, 0);
            g2.dispose();
        }
    }

    private void initUI() {
        // --- Modern Toolbar Container ---
        JPanel toolbarContainer = new JPanel(new BorderLayout(0, 5));
        // Solid background so the page wallpaper does not seep through the toolbar
        toolbarContainer.setOpaque(true);
        toolbarContainer.setBackground(new Color(0xE7, 0xE7, 0xE7)); // #e7e7e7
        toolbarContainer.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top toolbar row
        JPanel topToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topToolbar.setOpaque(false);

        // Back button
        RoundedButton backButton = new RoundedButton("Back");
        backButton.addActionListener(e -> app.switchCard(JournalApp.MAIN_MENU));
        topToolbar.add(backButton);
        
        // Right-side settings (cork icon) button
        JPanel rightToolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightToolbar.setOpaque(false);
        ToolbarIconButton settingsBtn = new ToolbarIconButton("cork");
        settingsBtn.setToolTipText("Background Settings");
        settingsBtn.addActionListener(e -> {
            PoemBackgroundDialog dialog = new PoemBackgroundDialog((java.awt.Frame)SwingUtilities.getWindowAncestor(this));
            dialog.setVisible(true);
            // Refresh the background if it was changed
            backgroundImage = null;
            cachedScaled = null;
            repaint();
        });
        rightToolbar.add(settingsBtn);

        // Title label & field
        JLabel titleLabel = new JLabel("Poem Title:");
        titleLabel.setForeground(Color.DARK_GRAY);
        titleLabel.setFont(new Font("Serif", Font.BOLD, 16));
        topToolbar.add(Box.createHorizontalStrut(6));
        topToolbar.add(titleLabel);

        poemTitleField = new ModernTextField(24);
        poemTitleField.setFont(new Font("Serif", Font.BOLD, 16));
        topToolbar.add(poemTitleField);

        // Font buttons (A- / A+)
        RoundedButton decFont = new RoundedButton("A-");
        RoundedButton incFont = new RoundedButton("A+");
        decFont.addActionListener(e -> changeFontSize(-1));
        incFont.addActionListener(e -> changeFontSize(1));
        topToolbar.add(Box.createHorizontalStrut(6));
        topToolbar.add(decFont);
        topToolbar.add(incFont);

        // Bottom toolbar row with font selector
        JPanel bottomToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottomToolbar.setOpaque(false);
        
        JLabel fontLabel = new JLabel("Font:");
        fontLabel.setForeground(Color.DARK_GRAY);
        fontLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        bottomToolbar.add(fontLabel);

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
        bottomToolbar.add(fontSelector);

        // Add both toolbar rows to the container
        toolbarContainer.add(topToolbar, BorderLayout.NORTH);
        toolbarContainer.add(bottomToolbar, BorderLayout.CENTER);
        toolbarContainer.add(rightToolbar, BorderLayout.EAST);

        add(toolbarContainer, BorderLayout.NORTH);

        // --- Center Panel: Poem Text Area with a cursive feel ---
        JPanel textWrapper = new TranslucentPanel(); // Use the new panel
        textWrapper.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        poemTextArea = new JTextArea();
        poemTextArea.setLineWrap(true);
        poemTextArea.setWrapStyleWord(true);
        poemTextArea.setOpaque(false); // Make the text area fully transparent
        poemTextArea.setForeground(new Color(40, 40, 40));
        
        // Load font size directly from settings to ensure persistence
        int savedFontSize = SettingsStore.get().getPoemFontSize();
        poemTextArea.setFont(new Font("Serif", Font.ITALIC, savedFontSize));
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
        RoundedButton inspireButton = new RoundedButton("✨ Inspire Me");
        inspireButton.addActionListener(e -> showInspirationalWord());
        JPanel centerFlow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        centerFlow.setOpaque(false);
        centerFlow.add(inspireButton);
        bottomPanel.add(centerFlow, BorderLayout.CENTER);

        RoundedButton saveButton = new RoundedButton("Save Poem");
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
        
        try {
            File poemFile;
            boolean isNewFile = false;
            
            if (currentFile == null) {
                // First save - create new file
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
                String timestamp = sdf.format(new Date());
                String filename = timestamp + ".poem";
                poemFile = new File(journalFolder, filename);
                currentFile = poemFile;
                isNewFile = true;
            } else {
                // Subsequent saves - use existing file
                poemFile = currentFile;
            }
            
            try (PrintWriter writer = new PrintWriter(new FileWriter(poemFile))) {
                writer.println(title);
                writer.println();
                writer.println(content);
            }
            
            String message = isNewFile ? "Poem saved successfully!" : "Poem updated successfully!";
            new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this), "Success", message, false).showDialog();
            
            // Don't clear fields - keep content like NewEntryPanel does
            // This allows continuous editing of the same poem
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

// Modern rounded text field identical to NewEntryPanel
class ModernTextField extends JTextField{
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
