package main.ui.panels;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import main.transitions.FadingButton;
import main.ui.JournalApp;
import main.ui.buttons.MainMenuButton;
import main.util.RamMonitor;
import main.util.ResourceLoader;
import main.util.SettingsStore;

public class MainMenuPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    // Toggle to quickly hide the Gallery feature without removing code/resources.
    private static final boolean SHOW_GALLERY = false;

    private final JournalApp app;
    private java.util.Map<String, main.ui.widgets.Widget> widgets = new java.util.LinkedHashMap<>();

    public MainMenuPanel(JournalApp app) {
        this.app = app;
        buildUI();
    }

    private void buildUI() {
        String bgPath = SettingsStore.get().getBackgroundImage();
        JPanel content;
        if (bgPath != null && !bgPath.isEmpty()) {
            if (bgPath.startsWith("res:")) {
                // Built-in resource (class-path) – strip prefix
                String resPath = bgPath.substring(4);
                Image img = ResourceLoader.createImage("Simjot/" + resPath);
                content = (img != null) ? new BackgroundPanel(img) : new JPanel();
            } else {
                // User-selected file path
                content = new BackgroundPanel(bgPath);
            }
            content.setBackground(Color.BLACK);
        } else {
            // Blank / default – just use a plain white panel
            content = new JPanel();
            content.setBackground(Color.WHITE);
        }
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        // -------- Widgets registration ---------
        // Create a dummy widget just for the menu button
        widgets.put("Breathing", new main.ui.widgets.Widget() {
            private boolean enabled = false;
            @Override public void start() { enabled = true; }
            @Override public void stop() { enabled = false; }
            @Override public boolean isEnabled() { return enabled; }
        });

        // Add header and clock.
        HeaderPanel header = new HeaderPanel();
        header.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(Box.createRigidArea(new Dimension(0, 10)));
        content.add(header);

        // Time info right below header quote
        TimeInfoPanel timePanelTop = new TimeInfoPanel();
        timePanelTop.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(Box.createRigidArea(new Dimension(0, 6)));
        content.add(timePanelTop);

        // ---- Clock and Widgets side-by-side ----
        AnalogClockPanel clockPanel = new AnalogClockPanel();
        clockPanel.setPreferredSize(new Dimension(200, 200));
        clockPanel.setMaximumSize(new Dimension(200, 200));

        JPanel widgetsMenu = buildWidgetsMenu();

        JPanel clockRow = new JPanel();
        clockRow.setOpaque(false);
        clockRow.setLayout(new BoxLayout(clockRow, BoxLayout.X_AXIS));
        clockRow.add(clockPanel);
        clockRow.add(Box.createRigidArea(new Dimension(20, 0)));
        clockRow.add(widgetsMenu);

        clockRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(Box.createRigidArea(new Dimension(0, 5)));
        content.add(clockRow);

        // Create the button panel with animated fade-in
        JPanel buttonPanel = new JPanel();
        buttonPanel.setOpaque(false);
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
        buttonPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // ---------- WRITING section ----------
        JLabel writingHeader = new JLabel("Writing");
        writingHeader.setAlignmentX(Component.CENTER_ALIGNMENT);
        writingHeader.setForeground(Color.WHITE);
        writingHeader.setFont(writingHeader.getFont().deriveFont(Font.BOLD, 22f));
        buttonPanel.add(writingHeader);
        buttonPanel.add(Box.createRigidArea(new Dimension(0, 6)));

        FadingButton notebooksButton = createMenuButtonWithIcon("Notebooks", JournalApp.NOTEBOOK_MANAGER, "notebook");
        buttonPanel.add(notebooksButton);
        buttonPanel.add(Box.createRigidArea(new Dimension(0, 12)));

        // Planning section has been removed

        // ---------- ARTS section ----------
        JLabel artsHeader = new JLabel("Arts & Gallery");
        artsHeader.setAlignmentX(Component.CENTER_ALIGNMENT);
        artsHeader.setForeground(Color.WHITE);
        artsHeader.setFont(artsHeader.getFont().deriveFont(Font.BOLD, 22f));
        buttonPanel.add(artsHeader);
        buttonPanel.add(Box.createRigidArea(new Dimension(0, 6)));

        FadingButton drawingButton = createMenuButtonWithIcon("Canvas", "Drawing", "pencil");
        drawingButton.setForeground(Color.WHITE);
        drawingButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        FadingButton galleryButton = createMenuButtonWithIcon("Gallery", JournalApp.GALLERY, "image");
        galleryButton.setForeground(Color.WHITE);
        galleryButton.setFont(galleryButton.getFont().deriveFont(Font.BOLD, 20f));
        galleryButton.setBorder(BorderFactory.createEmptyBorder(12, 24, 12, 24));
        galleryButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        galleryButton.addActionListener(e -> app.switchCard(JournalApp.GALLERY));

        List<FadingButton> artsBtns = new ArrayList<>();
        artsBtns.add(drawingButton);
        if (SHOW_GALLERY) artsBtns.add(galleryButton);
        for (FadingButton b : artsBtns) {
            b.setAlpha(1f);
            b.setAlignmentX(Component.CENTER_ALIGNMENT);
            buttonPanel.add(b);
            buttonPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        }
        if (SHOW_GALLERY) buttonPanel.add(Box.createRigidArea(new Dimension(0, 12)));

        // ---------- INSIGHTS section ----------
        JLabel insightsHeader = new JLabel("Insights");
        insightsHeader.setAlignmentX(Component.CENTER_ALIGNMENT);
        insightsHeader.setForeground(Color.WHITE);
        insightsHeader.setFont(insightsHeader.getFont().deriveFont(Font.BOLD, 22f));

        FadingButton moodChartButton = createMenuButtonWithIcon("Mood Chart", JournalApp.MOOD_CHART, "smile");

        buttonPanel.add(insightsHeader);
        buttonPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        moodChartButton.setAlpha(1f);
        moodChartButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        buttonPanel.add(moodChartButton);
        buttonPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        buttonPanel.add(Box.createRigidArea(new Dimension(0, 12)));

        FadingButton settingsButton = createMenuButtonWithIcon("Settings", JournalApp.SETTINGS, "wrench");
        settingsButton.setAlpha(1f);
        settingsButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        buttonPanel.add(settingsButton);
        buttonPanel.add(Box.createRigidArea(new Dimension(0, 6)));

        content.add(Box.createRigidArea(new Dimension(0, 20)));
        content.add(buttonPanel);

        header.startAnimation();
        // No fade-in animation; buttons are visible immediately

        // --------- Container setup
        setLayout(new BorderLayout());
        if (bgPath != null && !bgPath.isEmpty()) {
            setBackground(Color.BLACK);
        } else {
            setBackground(Color.WHITE);
        }

        // South Panel with version label and RAM usage
        JPanel southPanel = new JPanel();
        southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.Y_AXIS));
        southPanel.setOpaque(false);
        southPanel.add(Box.createHorizontalGlue());

        JLabel versionLabel = new JLabel("Version 1.0 - By Ilgaz, with love");
        versionLabel.setForeground(Color.WHITE);
        versionLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        versionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        southPanel.add(versionLabel);

        RamMonitor ramPanel = new RamMonitor();
        ramPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        ramPanel.setOpaque(false);
        southPanel.add(ramPanel);

        add(content, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);
    }

    /** Builds the small widget-selection menu shown next to the clock */
    private JPanel buildWidgetsMenu() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Widgets");
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createRigidArea(new Dimension(0,6)));

        for (java.util.Map.Entry<String, main.ui.widgets.Widget> entry : widgets.entrySet()) {
            String name = entry.getKey();
            main.ui.widgets.Widget widget = entry.getValue();
            FadingButton btn = new MainMenuButton(name, "bolt");
            btn.setForeground(Color.WHITE);
            btn.setFont(btn.getFont().deriveFont(Font.PLAIN,16f));
            btn.setAlpha(1f);
            btn.setAlignmentX(Component.CENTER_ALIGNMENT);
            btn.addActionListener(e -> {
                if(name.equals("Breathing")) {
                    // First show our custom confirmation dialog
                    boolean startBreathing = main.dialog.CustomConfirmDialog.confirm(
                        this, 
                        "Breathing Exercise", 
                        "Would you like to start a guided breathing exercise?\n\nThis will display a calming animation overlay."
                    );
                    
                    if(startBreathing) {
                        // Show configuration dialog for breathing widget
                        main.dialog.BreathingConfigDialog dialog = 
                            new main.dialog.BreathingConfigDialog((JFrame) SwingUtilities.getWindowAncestor(this));
                        dialog.setVisible(true);
                        
                        if(dialog.isConfirmed()) {
                            // Open breathing exercise in its own window
                            main.dialog.BreathingExerciseWindow exerciseWindow = 
                                new main.dialog.BreathingExerciseWindow((JFrame) SwingUtilities.getWindowAncestor(this));
                            exerciseWindow.startExercise(
                                dialog.getInhaleTime(),
                                dialog.getHold1Time(),
                                dialog.getExhaleTime(),
                                dialog.getHold2Time(),
                                dialog.getOpacityValue(),
                                dialog.getSizeValue(),
                                dialog.getColor()
                            );
                        }
                    }
                } else {
                    // For other widgets, just toggle
                    boolean enable = !widget.isEnabled();
                    widget.setEnabled(enable);
                }
            });
            panel.add(btn);
            panel.add(Box.createRigidArea(new Dimension(0,4)));
        }
        return panel;
    }

    private FadingButton createMenuButtonWithIcon(String text, String cardName, String icon) {
        FadingButton button = new MainMenuButton(text, icon);
        button.setForeground(Color.WHITE);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 20f));
        button.setBorder(BorderFactory.createEmptyBorder(12, 24, 12, 24));
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.addActionListener(e -> app.switchCard(cardName));
        return button;
    }

    // ---------- Time Info Panel ----------
    private static class TimeInfoPanel extends JPanel {
        private final JLabel timeLbl = new JLabel();
        private final JLabel pctLbl = new JLabel();

        TimeInfoPanel() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            timeLbl.setForeground(Color.WHITE);
            pctLbl.setForeground(Color.WHITE);
            Font quoteFont = new Font("SansSerif", Font.ITALIC, 18);
            timeLbl.setFont(quoteFont);
            pctLbl.setFont(quoteFont);
            timeLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
            pctLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
            add(timeLbl);
            add(pctLbl);
            javax.swing.Timer t = new javax.swing.Timer(1000, e -> update());
            t.start();
            update();
        }

        private void update() {
            java.time.LocalTime now = java.time.LocalTime.now();
            timeLbl.setText("It's currently " + now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
            int seconds = now.toSecondOfDay();
            double pct = seconds / 86400.0 * 100.0;
            pctLbl.setText(String.format("%.1f%% of the day has passed.", pct));
        }
    }
} 