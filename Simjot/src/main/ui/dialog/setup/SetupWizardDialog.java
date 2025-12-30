package main.ui.dialog.setup;

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
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import main.core.AppInfo;
import main.core.service.SettingsStore;
import main.infrastructure.io.AppDirectories;
import main.ui.components.buttons.IconMenuButton;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.spinner.ModernSpinner;
import main.ui.theme.aero.AeroTheme;

/**
 * Enhanced first-launch wizard with multi-step flow, progress indicators,
 * and detailed validation. Guides the user through setting up their Simjot
 * data folder with visual feedback at each step.
 * 
 * @since 1.0.0
 * @version 1.0.0
 * @author S1mplector
 */
public class SetupWizardDialog extends JDialog {
    
    private static final int DIALOG_WIDTH = 560;
    private static final int DIALOG_HEIGHT = 520;
    private static final Color ACCENT_COLOR = new Color(0, 120, 215);
    private static final Color SUCCESS_COLOR = new Color(76, 175, 80);
    private static final Color PENDING_COLOR = new Color(158, 158, 158);
    private static final Color TEXT_SECONDARY = new Color(100, 100, 100);
    
    private File rootFolder;
    private int currentStep = 0;
    private CardLayout cardLayout;
    private JPanel cardPanel;
    private StepIndicator stepIndicator;
    
    // Step panels
    private JPanel welcomePanel;
    private JPanel locationPanel;
    private JPanel initializingPanel;
    private JPanel completePanel;
    
    // Location step components
    private JLabel selectedPathLabel;
    private RoundedButton nextButton;
    
    // Initialization step components
    private List<SetupTask> setupTasks;
    private JPanel tasksPanel;

    public SetupWizardDialog(JFrame owner) {
        super(owner, "Simjot – First-time Setup", true);
        initUI();
    }

    public File getRootFolder() {
        return rootFolder;
    }

    private void initUI() {
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setUndecorated(true);
        setSize(DIALOG_WIDTH, DIALOG_HEIGHT);
        setLocationRelativeTo(getOwner());
        
        // Make dialog rounded
        setBackground(new Color(0, 0, 0, 0));
        
        // Main container with rounded corners
        JPanel mainPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(250, 250, 252));
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 20, 20));
                g2.setColor(new Color(200, 200, 200));
                g2.draw(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 20, 20));
                g2.dispose();
            }
        };
        mainPanel.setOpaque(false);
        mainPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
        
        // Header with step indicator
        JPanel headerPanel = createHeaderPanel();
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        
        // Card panel for step content
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.setOpaque(false);
        cardPanel.setBorder(new EmptyBorder(0, 24, 24, 24));
        
        // Create step panels
        welcomePanel = createWelcomePanel();
        locationPanel = createLocationPanel();
        initializingPanel = createInitializingPanel();
        completePanel = createCompletePanel();
        
        cardPanel.add(welcomePanel, "welcome");
        cardPanel.add(locationPanel, "location");
        cardPanel.add(initializingPanel, "initializing");
        cardPanel.add(completePanel, "complete");
        
        mainPanel.add(cardPanel, BorderLayout.CENTER);
        
        setContentPane(mainPanel);
        
        // Start at welcome
        showStep(0);
    }
    
    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Gradient header background
                GradientPaint gp = new GradientPaint(0, 0, new Color(245, 247, 250),
                        0, getHeight(), new Color(238, 240, 245));
                g2.setPaint(gp);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight() + 10, 20, 20));
                // Bottom border
                g2.setColor(new Color(220, 222, 228));
                g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
                g2.dispose();
            }
        };
        header.setOpaque(false);
        header.setPreferredSize(new Dimension(DIALOG_WIDTH, 112));
        header.setBorder(new EmptyBorder(16, 24, 12, 24));
        
        // Title row
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        
        JLabel titleLabel = new JLabel("Simjot Setup");
        titleLabel.setFont(AeroTheme.defaultBoldFont(20f));
        titleLabel.setForeground(AeroTheme.TEXT_PRIMARY);
        titleRow.add(titleLabel, BorderLayout.WEST);
        
        JLabel versionLabel = new JLabel("v" + AppInfo.VERSION);
        versionLabel.setFont(AeroTheme.defaultFont().deriveFont(11f));
        versionLabel.setForeground(TEXT_SECONDARY);
        titleRow.add(versionLabel, BorderLayout.EAST);
        
        header.add(titleRow, BorderLayout.NORTH);
        
        // Step indicator
        stepIndicator = new StepIndicator(new String[]{"Welcome", "Location", "Setup", "Ready"});
        header.add(stepIndicator, BorderLayout.CENTER);
        
        return header;
    }
    
    private JPanel createWelcomePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        
        panel.add(Box.createVerticalStrut(20));
        
        // Welcome message
        JLabel welcomeLabel = new JLabel("Welcome to Simjot!");
        welcomeLabel.setFont(AeroTheme.defaultBoldFont(24f));
        welcomeLabel.setForeground(AeroTheme.TEXT_PRIMARY);
        welcomeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(welcomeLabel);
        
        panel.add(Box.createVerticalStrut(12));
        
        JLabel subtitleLabel = new JLabel("Your personal journaling companion");
        subtitleLabel.setFont(AeroTheme.defaultFont().deriveFont(14f));
        subtitleLabel.setForeground(TEXT_SECONDARY);
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(subtitleLabel);
        
        panel.add(Box.createVerticalStrut(32));
        
        // Feature highlights
        String[] features = {
            "- Organize your thoughts in beautiful notebooks",
            "- Write journal entries and poetry with rich formatting",
            "- Track your mood and visualize patterns over time",
            "- Keep your data safe with encryption",
            "- Sync across devices with cloud storage"
        };
        
        JPanel featuresPanel = new JPanel();
        featuresPanel.setLayout(new BoxLayout(featuresPanel, BoxLayout.Y_AXIS));
        featuresPanel.setOpaque(false);
        featuresPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        for (String feature : features) {
            JLabel featureLabel = new JLabel(feature);
            featureLabel.setFont(AeroTheme.defaultFont().deriveFont(13f));
            featureLabel.setForeground(AeroTheme.TEXT_PRIMARY);
            featureLabel.setBorder(new EmptyBorder(6, 0, 6, 0));
            featureLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            featuresPanel.add(featureLabel);
        }
        
        // Center the features panel
        JPanel featureWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER));
        featureWrapper.setOpaque(false);
        featureWrapper.add(featuresPanel);
        panel.add(featureWrapper);
        
        panel.add(Box.createVerticalGlue());
        
        // Get Started button
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.setOpaque(false);
        
        IconMenuButton getStartedBtn = createNavButton("Get Started", "install");
        getStartedBtn.setPreferredSize(new Dimension(140, 84));
        getStartedBtn.addActionListener(e -> showStep(1));
        buttonPanel.add(getStartedBtn);
        
        panel.add(buttonPanel);
        panel.add(Box.createVerticalStrut(16));
        
        return panel;
    }
    
    private JPanel createLocationPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        
        panel.add(Box.createVerticalStrut(16));
        
        // Title
        JLabel titleLabel = new JLabel("Choose Data Location");
        titleLabel.setFont(AeroTheme.defaultBoldFont(18f));
        titleLabel.setForeground(AeroTheme.TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(titleLabel);
        
        panel.add(Box.createVerticalStrut(8));
        
        JLabel descLabel = new JLabel("Where should Simjot store your journals and data?");
        descLabel.setFont(AeroTheme.defaultFont().deriveFont(13f));
        descLabel.setForeground(TEXT_SECONDARY);
        descLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(descLabel);
        
        panel.add(Box.createVerticalStrut(24));
        
        // Info box
        FrostedGlassPanel infoBox = new FrostedGlassPanel(12);
        infoBox.setLayout(new BorderLayout());
        infoBox.setBorder(new EmptyBorder(14, 16, 14, 16));
        infoBox.setMaximumSize(new Dimension(480, 120));
        infoBox.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        String infoHtml = "<html><div style='width:420px;'>" +
                "<b>Tip:</b> Choose a cloud-synced folder (iCloud, Dropbox, OneDrive) " +
                "to access your journals from multiple devices.<br><br>" +
                "A <b>Simjot</b> folder will be created at your chosen location containing " +
                "all your notebooks, settings, and mood data." +
                "</div></html>";
        JLabel infoLabel = new JLabel(infoHtml);
        infoLabel.setFont(AeroTheme.defaultFont().deriveFont(12f));
        infoBox.add(infoLabel, BorderLayout.CENTER);
        
        panel.add(infoBox);
        
        panel.add(Box.createVerticalStrut(20));
        
        // Location buttons
        JPanel locationButtons = new JPanel(new GridLayout(2, 1, 0, 10));
        locationButtons.setOpaque(false);
        locationButtons.setMaximumSize(new Dimension(440, 140));
        locationButtons.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        JComponent docsBtn = createLocationButton(
            "Documents",
            "Recommended for most users",
            "open_folder",
            () -> {
                File docs = javax.swing.filechooser.FileSystemView.getFileSystemView().getDefaultDirectory();
                if (docs == null) {
                    String home = System.getProperty("user.home");
                    if (home != null && !home.isBlank()) {
                        docs = new File(home);
                    }
                }
                if (docs != null) {
                    selectRootFolder(docs, false);
                }
            }
        );
        locationButtons.add(docsBtn);
        
        JComponent customBtn = createLocationButton(
            "Custom...",
            "Select any folder on your computer",
            "folder_open",
            () -> {
                DirectoryChooserDialog dirDlg = new DirectoryChooserDialog(
                    (JFrame) SwingUtilities.getWindowAncestor(this));
                dirDlg.setVisible(true);
                File chosen = dirDlg.getSelectedDirectory();
                if (chosen != null) {
                    selectRootFolder(chosen, true);
                }
            }
        );
        locationButtons.add(customBtn);
        
        panel.add(locationButtons);
        
        panel.add(Box.createVerticalStrut(16));
        
        // Selected path display
        JPanel pathPanel = new JPanel(new BorderLayout());
        pathPanel.setOpaque(false);
        pathPanel.setMaximumSize(new Dimension(480, 50));
        pathPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        JLabel pathTitle = new JLabel("Selected location:");
        pathTitle.setFont(AeroTheme.defaultFont().deriveFont(11f));
        pathTitle.setForeground(TEXT_SECONDARY);
        pathPanel.add(pathTitle, BorderLayout.NORTH);
        
        selectedPathLabel = new JLabel("No location selected");
        selectedPathLabel.setFont(AeroTheme.defaultFont().deriveFont(12f));
        selectedPathLabel.setForeground(AeroTheme.TEXT_PRIMARY);
        selectedPathLabel.setBorder(new EmptyBorder(4, 0, 0, 0));
        pathPanel.add(selectedPathLabel, BorderLayout.CENTER);
        
        panel.add(pathPanel);
        
        panel.add(Box.createVerticalGlue());
        
        // Navigation buttons
        JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        navPanel.setOpaque(false);
        
        IconMenuButton backBtn = createNavButton("Back", "back");
        backBtn.setPreferredSize(new Dimension(110, 84));
        backBtn.addActionListener(e -> showStep(0));
        navPanel.add(backBtn);
        
        nextButton = new RoundedButton("Continue");
        nextButton.setEnabled(false);
        nextButton.addActionListener(e -> proceedFromLocation());
        navPanel.add(nextButton);
        
        panel.add(navPanel);
        panel.add(Box.createVerticalStrut(16));
        
        return panel;
    }

    private JComponent createLocationButton(String title, String subtitle, String iconId, Runnable action) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(6, 10, 6, 10));
        row.setMaximumSize(new Dimension(420, 90));

        IconMenuButton iconBtn = new IconMenuButton(title, iconId);
        iconBtn.setPreferredSize(new Dimension(84, 80));
        iconBtn.setMinimumSize(new Dimension(84, 80));
        iconBtn.setMaximumSize(new Dimension(96, 90));
        iconBtn.setToolTipText(title);
        iconBtn.addActionListener(e -> action.run());

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(AeroTheme.defaultBoldFont(13f));
        titleLabel.setForeground(AeroTheme.TEXT_PRIMARY);
        JLabel subtitleLabel = new JLabel(subtitle);
        subtitleLabel.setFont(AeroTheme.defaultFont().deriveFont(11f));
        subtitleLabel.setForeground(TEXT_SECONDARY);
        text.add(titleLabel);
        text.add(Box.createVerticalStrut(4));
        text.add(subtitleLabel);

        row.add(iconBtn, BorderLayout.WEST);
        row.add(text, BorderLayout.CENTER);

        Cursor hand = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
        row.setCursor(hand);
        text.setCursor(hand);
        titleLabel.setCursor(hand);
        subtitleLabel.setCursor(hand);

        MouseAdapter clicker = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { iconBtn.doClick(); }
        };
        row.addMouseListener(clicker);
        text.addMouseListener(clicker);
        titleLabel.addMouseListener(clicker);
        subtitleLabel.addMouseListener(clicker);

        return row;
    }

    private static IconMenuButton createNavButton(String label, String iconId) {
        IconMenuButton btn = new IconMenuButton(label, iconId);
        btn.setToolTipText(label);
        btn.setFont(AeroTheme.defaultBoldFont(13f));
        return btn;
    }
    
    private void updateSelectedPath() {
        if (rootFolder != null) {
            String path = rootFolder.getAbsolutePath();
            if (path.length() > 50) {
                path = "..." + path.substring(path.length() - 47);
            }
            selectedPathLabel.setText(path);
            selectedPathLabel.setForeground(SUCCESS_COLOR);
            nextButton.setEnabled(true);
        }
    }

    private void selectRootFolder(File baseFolder, boolean autoProceed) {
        if (baseFolder == null) return;
        rootFolder = new File(baseFolder, "Simjot");
        updateSelectedPath();
        if (autoProceed) {
            proceedFromLocation();
        }
    }

    private void proceedFromLocation() {
        if (rootFolder != null) {
            showStep(2);
            startInitialization();
        }
    }
    
    private JPanel createInitializingPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        
        panel.add(Box.createVerticalStrut(20));
        
        // Title
        JLabel titleLabel = new JLabel("Setting Up Simjot");
        titleLabel.setFont(AeroTheme.defaultBoldFont(18f));
        titleLabel.setForeground(AeroTheme.TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(titleLabel);
        
        panel.add(Box.createVerticalStrut(8));
        
        JLabel descLabel = new JLabel("Please wait while we prepare everything for you…");
        descLabel.setFont(AeroTheme.defaultFont().deriveFont(13f));
        descLabel.setForeground(TEXT_SECONDARY);
        descLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(descLabel);
        
        panel.add(Box.createVerticalStrut(32));
        
        // Tasks panel
        tasksPanel = new JPanel();
        tasksPanel.setLayout(new BoxLayout(tasksPanel, BoxLayout.Y_AXIS));
        tasksPanel.setOpaque(false);
        tasksPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        tasksPanel.setMaximumSize(new Dimension(400, 300));
        
        // Initialize task list
        setupTasks = new ArrayList<>();
        setupTasks.add(new SetupTask("Creating Simjot folder", () -> {
            if (!rootFolder.exists()) {
                return rootFolder.mkdirs();
            }
            return rootFolder.isDirectory();
        }));
        setupTasks.add(new SetupTask("Setting up notebooks directory", () -> {
            AppDirectories.setRoot(rootFolder);
            File f = AppDirectories.folder(AppDirectories.Type.NOTEBOOKS);
            return f.exists() && f.isDirectory();
        }));
        setupTasks.add(new SetupTask("Creating mood data folder", () -> {
            File f = AppDirectories.folder(AppDirectories.Type.MOOD_DATA);
            return f.exists() && f.isDirectory();
        }));
        setupTasks.add(new SetupTask("Setting up settings directory", () -> {
            File f = AppDirectories.folder(AppDirectories.Type.SETTINGS);
            return f.exists() && f.isDirectory();
        }));
        setupTasks.add(new SetupTask("Creating wallpapers folder", () -> {
            File f = AppDirectories.folder(AppDirectories.Type.WALLPAPERS);
            return f.exists() && f.isDirectory();
        }));
        setupTasks.add(new SetupTask("Initializing preferences", () -> {
            try {
                SettingsStore.get().setBackgroundImage("");
                SettingsStore.get().save();
                return true;
            } catch (Exception e) {
                return false;
            }
        }));
        setupTasks.add(new SetupTask("Validating setup", () -> {
            // Final validation
            return rootFolder.exists() && rootFolder.isDirectory();
        }));
        
        // Create task UI components
        for (SetupTask task : setupTasks) {
            tasksPanel.add(task.createPanel());
            tasksPanel.add(Box.createVerticalStrut(8));
        }
        
        JPanel taskWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER));
        taskWrapper.setOpaque(false);
        taskWrapper.add(tasksPanel);
        panel.add(taskWrapper);
        
        panel.add(Box.createVerticalGlue());
        
        return panel;
    }
    
    private void startInitialization() {
        // Reset all tasks to pending
        for (SetupTask task : setupTasks) {
            task.reset();
        }
        
        // Run tasks sequentially with delays for visual feedback
        SwingWorker<Void, Integer> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                for (int i = 0; i < setupTasks.size(); i++) {
                    SetupTask task = setupTasks.get(i);
                    
                    // Small delay before starting each task
                    Thread.sleep(300);
                    
                    // Update to in-progress
                    // Mark task in progress
                    SwingUtilities.invokeLater(() -> task.setInProgress());
                    
                    // Execute the task
                    Thread.sleep(400 + (int)(Math.random() * 300)); // Simulate work
                    boolean success = task.execute();
                    
                    // Update status
                    SwingUtilities.invokeLater(() -> task.setComplete(success));
                    
                    if (!success) {
                        // Task failed, stop here
                        return null;
                    }
                }
                return null;
            }
            
            @Override
            protected void done() {
                // Check if all tasks succeeded
                boolean allSuccess = setupTasks.stream().allMatch(t -> t.isSuccess());
                if (allSuccess) {
                    // Small delay before showing complete screen
                    Timer timer = new Timer(500, e -> showStep(3));
                    timer.setRepeats(false);
                    timer.start();
                }
            }
        };
        worker.execute();
    }
    
    private JPanel createCompletePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        
        panel.add(Box.createVerticalStrut(40));
        
        // Success icon
        JLabel checkLabel = new JLabel("✓");
        checkLabel.setFont(new Font("SansSerif", Font.BOLD, 64));
        checkLabel.setForeground(SUCCESS_COLOR);
        checkLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(checkLabel);
        
        panel.add(Box.createVerticalStrut(20));
        
        // Title
        JLabel titleLabel = new JLabel("You're All Set!");
        titleLabel.setFont(AeroTheme.defaultBoldFont(24f));
        titleLabel.setForeground(AeroTheme.TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(titleLabel);
        
        panel.add(Box.createVerticalStrut(12));
        
        JLabel descLabel = new JLabel("Simjot is ready to use. Start writing!");
        descLabel.setFont(AeroTheme.defaultFont().deriveFont(14f));
        descLabel.setForeground(TEXT_SECONDARY);
        descLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(descLabel);
        
        panel.add(Box.createVerticalStrut(32));
        
        // Summary box
        FrostedGlassPanel summaryBox = new FrostedGlassPanel(12);
        summaryBox.setLayout(new BorderLayout());
        summaryBox.setBorder(new EmptyBorder(16, 20, 16, 20));
        summaryBox.setMaximumSize(new Dimension(400, 100));
        summaryBox.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        JLabel summaryLabel = new JLabel("<html><div style='width:340px;'>" +
                "<b>Your data will be stored in:</b><br>" +
                "<span style='color:#0078D7;'>" + 
                (rootFolder != null ? rootFolder.getAbsolutePath() : "Documents/Simjot") + 
                "</span>" +
                "</div></html>");
        summaryLabel.setFont(AeroTheme.defaultFont().deriveFont(12f));
        summaryBox.add(summaryLabel, BorderLayout.CENTER);
        
        panel.add(summaryBox);
        
        panel.add(Box.createVerticalGlue());
        
        // Start button
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.setOpaque(false);
        
        IconMenuButton startBtn = createNavButton("Start", "check");
        startBtn.setPreferredSize(new Dimension(150, 90));
        startBtn.addActionListener(e -> dispose());
        buttonPanel.add(startBtn);
        
        panel.add(buttonPanel);
        panel.add(Box.createVerticalStrut(24));
        
        return panel;
    }
    
    private void showStep(int step) {
        currentStep = step;
        stepIndicator.setCurrentStep(step);
        
        String[] cards = {"welcome", "location", "initializing", "complete"};
        cardLayout.show(cardPanel, cards[step]);
        
        // Refresh complete panel with actual path
        if (step == 3 && rootFolder != null) {
            cardPanel.remove(completePanel);
            completePanel = createCompletePanel();
            cardPanel.add(completePanel, "complete");
            cardLayout.show(cardPanel, "complete");
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // INNER CLASSES
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Visual step indicator showing progress through the wizard.
     */
    private static class StepIndicator extends JPanel {
        private final String[] steps;
        private int currentStep = 0;
        
        StepIndicator(String[] steps) {
            this.steps = steps;
            setOpaque(false);
            setPreferredSize(new Dimension(460, 60));
        }
        
        void setCurrentStep(int step) {
            this.currentStep = step;
            repaint();
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            
            int w = getWidth();
            int h = getHeight();
            int stepWidth = w / steps.length;
            int dotSize = 10;
            int labelY = Math.max(18, h - 6);
            int lineY = Math.max(dotSize + 6, labelY - 18);
            
            // Draw connecting lines
            g2.setStroke(new BasicStroke(2));
            for (int i = 0; i < steps.length - 1; i++) {
                int x1 = (i * stepWidth) + (stepWidth / 2) + dotSize / 2 + 4;
                int x2 = ((i + 1) * stepWidth) + (stepWidth / 2) - dotSize / 2 - 4;
                g2.setColor(i < currentStep ? SUCCESS_COLOR : new Color(200, 200, 200));
                g2.drawLine(x1, lineY, x2, lineY);
            }
            
            // Draw step dots and labels
            g2.setFont(AeroTheme.defaultFont().deriveFont(10f));
            FontMetrics fm = g2.getFontMetrics();
            
            for (int i = 0; i < steps.length; i++) {
                int cx = (i * stepWidth) + (stepWidth / 2);
                
                // Dot
                if (i < currentStep) {
                    g2.setColor(SUCCESS_COLOR);
                    g2.fillOval(cx - dotSize / 2, lineY - dotSize / 2, dotSize, dotSize);
                    // Checkmark
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("Segoe UI", Font.BOLD, 8));
                    g2.drawString("✓", cx - 3, lineY + 3);
                    g2.setFont(AeroTheme.defaultFont().deriveFont(10f));
                } else if (i == currentStep) {
                    g2.setColor(ACCENT_COLOR);
                    g2.fillOval(cx - dotSize / 2, lineY - dotSize / 2, dotSize, dotSize);
                } else {
                    g2.setColor(new Color(200, 200, 200));
                    g2.fillOval(cx - dotSize / 2, lineY - dotSize / 2, dotSize, dotSize);
                }
                
                // Label
                g2.setColor(i <= currentStep ? AeroTheme.TEXT_PRIMARY : TEXT_SECONDARY);
                int labelWidth = fm.stringWidth(steps[i]);
                g2.drawString(steps[i], cx - labelWidth / 2, labelY);
            }
            
            g2.dispose();
        }
    }
    
    /**
     * Represents a single setup task with visual state.
     */
    private static class SetupTask {
        private final String name;
        private final java.util.function.BooleanSupplier action;
        private JPanel panel;
        private JLabel statusIcon;
        private JLabel nameLabel;
        private ModernSpinner spinner;
        private boolean success = false;
        
        SetupTask(String name, java.util.function.BooleanSupplier action) {
            this.name = name;
            this.action = action;
        }
        
        JPanel createPanel() {
            panel = new JPanel(new BorderLayout(8, 0));
            panel.setOpaque(false);
            panel.setMaximumSize(new Dimension(380, 28));
            
            // Status icon (pending dot initially)
            statusIcon = new JLabel("○");
            statusIcon.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            statusIcon.setForeground(PENDING_COLOR);
            statusIcon.setPreferredSize(new Dimension(24, 24));
            statusIcon.setHorizontalAlignment(SwingConstants.CENTER);
            panel.add(statusIcon, BorderLayout.WEST);
            
            // Task name
            nameLabel = new JLabel(name);
            nameLabel.setFont(AeroTheme.defaultFont().deriveFont(13f));
            nameLabel.setForeground(PENDING_COLOR);
            panel.add(nameLabel, BorderLayout.CENTER);
            
            // Spinner (hidden initially)
            spinner = new ModernSpinner(18, ACCENT_COLOR);
            spinner.setVisible(false);
            panel.add(spinner, BorderLayout.EAST);
            
            return panel;
        }
        
        void reset() {
            success = false;
            statusIcon.setText("○");
            statusIcon.setForeground(PENDING_COLOR);
            nameLabel.setForeground(PENDING_COLOR);
            spinner.setVisible(false);
        }
        
        void setInProgress() {
            statusIcon.setText("");
            spinner.setVisible(true);
            nameLabel.setForeground(AeroTheme.TEXT_PRIMARY);
        }
        
        boolean execute() {
            try {
                success = action.getAsBoolean();
                return success;
            } catch (Exception e) {
                success = false;
                return false;
            }
        }
        
        void setComplete(boolean success) {
            this.success = success;
            spinner.setVisible(false);
            spinner.stop();
            if (success) {
                statusIcon.setText("✓");
                statusIcon.setForeground(SUCCESS_COLOR);
                nameLabel.setForeground(SUCCESS_COLOR);
            } else {
                statusIcon.setText("✗");
                statusIcon.setForeground(new Color(244, 67, 54));
                nameLabel.setForeground(new Color(244, 67, 54));
            }
        }
        
        boolean isSuccess() {
            return success;
        }
    }
}
