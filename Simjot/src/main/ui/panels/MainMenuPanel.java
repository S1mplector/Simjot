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

        AnalogClockPanel clockPanel = new AnalogClockPanel();
        clockPanel.setPreferredSize(new Dimension(200, 200));
        clockPanel.setMaximumSize(new Dimension(200, 200));
        clockPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(Box.createRigidArea(new Dimension(0, 5)));
        content.add(clockPanel);

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

        // ---------- PLANNING section ----------
        JLabel planningHeader = new JLabel("Planning");
        planningHeader.setAlignmentX(Component.CENTER_ALIGNMENT);
        planningHeader.setForeground(Color.WHITE);
        planningHeader.setFont(planningHeader.getFont().deriveFont(Font.BOLD, 22f));
        buttonPanel.add(planningHeader);
        buttonPanel.add(Box.createRigidArea(new Dimension(0, 6)));

        FadingButton tasksButton = createMenuButtonWithIcon("Tasks", JournalApp.TASKS, "tick");
        buttonPanel.add(tasksButton);
        buttonPanel.add(Box.createRigidArea(new Dimension(0, 12)));

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