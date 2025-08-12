package main.ui;

import java.awt.*;
import java.io.*;
import javax.swing.*;
import main.dialog.CustomConfirmDialog;
import main.dialog.SetupWizardDialog;
import main.dialog.TutorialDialog;
import main.transitions.FadeTransitionPanel;
import main.ui.panels.DrawingPanel;
import main.ui.panels.EditEntryPanel;
import main.ui.panels.EditPoemPanel;
import main.ui.panels.GalleryPanel;
import main.ui.panels.MainMenuPanel;
import main.ui.panels.MoodChartPanel;
import main.ui.panels.NewEntryPanel;
import main.ui.panels.NotebookEntriesPanel;
import main.ui.panels.NotebookManagerPanel;
import main.ui.panels.PoemPanel;
import main.ui.panels.SettingsPanel;
import main.ui.panels.ViewEntriesPanel;
import main.util.AppDirectories;
import main.util.NotebookInfo;
import main.util.RamMonitor;
import main.util.SettingsStore;
// import main.ui.buttons.MainMenuButton; // Removed as per edit hint
import main.ui.theme.aero.AeroLookAndFeel;
import main.ui.icons.AppIcon;
import main.ui.splash.AeroSplashScreen;

public class JournalApp extends JFrame {
    private static final long serialVersionUID = 1L;

    public static int globalJournalFontSize = 14; // For journal entries

    private CardLayout cardLayout;
    private JPanel cardPanel;
    private File rootFolder;

    // Config file to store the journal folder path
    private File configFile;
    private final String CONFIG_FILENAME = ".simjournal_config.txt";

    // Card identifiers
    public static final String MAIN_MENU = "Main Menu";
    public static final String NEW_ENTRY = "New Entry";
    public static final String VIEW_ENTRIES = "View Entries";
    public static final String MOOD_CHART = "Mood Chart";
    public static final String NEW_POEM = "New Poem";
    public static final String SETTINGS = "Settings";
    public static final String GALLERY = "Gallery";
    public static final String NOTEBOOK_MANAGER = "Notebook Manager";

    // Additional references for panels that might need referencing
    private SettingsPanel settingsPanel;
    private RamMonitor ramUsagePanel;
    private GalleryPanel galleryPanel;

    private boolean firstSwitchDone = false;

    private JPanel mainMenuPanel;

    // Keeps track of dynamically created entry manager panels for notebooks
    private final java.util.Map<String, NotebookEntriesPanel> notebookPanels = new java.util.HashMap<>();

    // Added for openExistingEntryEditor method
    private final java.util.Map<String, JPanel> cardMap = new java.util.HashMap<>();

    public JournalApp() {
        super("Simjot");
        // Set the application icon
        setIconImages(AppIcon.generateIconImages());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        loadOrChooseRootFolder();
        if (rootFolder != null) {
            initUI();
        } else {
            System.exit(0);
        }
    }

    private void loadOrChooseRootFolder() {
        configFile = new File(System.getProperty("user.home"), CONFIG_FILENAME);
        if (configFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
                String path = reader.readLine();
                if (path != null) {
                    File folder = new File(path);
                    if (folder.exists() && folder.isDirectory()) {
                        rootFolder = folder;
                        AppDirectories.setRoot(rootFolder);
                        // Guarantee all standard sub-directories exist when loading existing root
                        for (AppDirectories.Type t : AppDirectories.Type.values()) {
                            AppDirectories.folder(t);
                        }
                        return;
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        SetupWizardDialog dlg = new SetupWizardDialog(this);
        dlg.setVisible(true);
        rootFolder = dlg.getRootFolder();
        if (rootFolder != null) {
            saveJournalFolderConfig();
        }
    }

    private void saveJournalFolderConfig() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(configFile))) {
            writer.println(rootFolder.getAbsolutePath());
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error saving config file.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void initUI() {
        // Apply Windows 7 Aero-inspired look & feel and defaults before building UI
        AeroLookAndFeel.apply();
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);

        // Main menu
        mainMenuPanel = createMainMenuPanel();
        cardPanel.add(mainMenuPanel, MAIN_MENU);

        // "New" creation panels points to new subfolders
        cardPanel.add(
                new NewEntryPanel(this, AppDirectories.folder(AppDirectories.Type.ENTRIES), cardLayout, cardPanel),
                NEW_ENTRY);
        cardPanel.add(new PoemPanel(this, AppDirectories.folder(AppDirectories.Type.POEMS), cardLayout, cardPanel),
                NEW_POEM);

        // View Entries
        cardPanel.add(new ViewEntriesPanel(this, cardLayout, cardPanel), VIEW_ENTRIES);

        // Additional Panels
        cardPanel.add(new MoodChartPanel(this, cardLayout, cardPanel), MOOD_CHART);
        settingsPanel = new SettingsPanel(this, cardLayout, cardPanel);
        cardPanel.add(settingsPanel, SETTINGS);

        // Drawing panel
        DrawingPanel drawingPanel = new DrawingPanel(this);
        cardPanel.add(drawingPanel, "Drawing");

        // Gallery panel uses drawings dir and needs drawingPanel reference
        galleryPanel = new GalleryPanel(AppDirectories.folder(AppDirectories.Type.DRAWINGS), cardLayout, cardPanel,
                this, drawingPanel);
        cardPanel.add(galleryPanel, GALLERY);

        // ----------------- Notebooks Manager Panel -----------------
        NotebookManagerPanel notebookManagerPanel = new NotebookManagerPanel(this);
        cardPanel.add(notebookManagerPanel, NOTEBOOK_MANAGER);

        getContentPane().add(cardPanel);

        // Fade transitions
        FadeTransitionPanel fadePanel = new FadeTransitionPanel();
        setGlassPane(fadePanel);
        fadePanel.setVisible(true);

        setVisible(true);
        switchCard(MAIN_MENU);

        // After UI visible, optionally show tutorial, then force fullscreen
        SwingUtilities.invokeLater(() -> {
            showTutorialIfFirstTime();
            ensureFullScreen();
            // Force widget panel visible and on top
            if (mainMenuPanel instanceof MainMenuPanel mmp) {
                mmp.ensureWidgetPanelOnTopAndVisible();
            } else {
                updateWidgetPanelVisibility();
            }
        });
    }

    public void switchCard(String cardName) {
        FadeTransitionPanel fadePanel = (FadeTransitionPanel) getGlassPane();

        if (SettingsStore.get().isAnimationsDisabled() || !firstSwitchDone) {
            // If animations are off, or it's the first switch, do it instantly.
            cardLayout.show(cardPanel, cardName);

            if (!firstSwitchDone) {
                firstSwitchDone = true;
            }

            // Always ensure the fade panel is hidden if we're not animating.
            if (fadePanel.isVisible()) {
                fadePanel.setVisible(false);
            }

            // Special refresh logic for notebook manager
            if (cardName.equals(NOTEBOOK_MANAGER)) {
                for (Component c : cardPanel.getComponents()) {
                    if (c instanceof NotebookManagerPanel nm) {
                        nm.refresh();
                    }
                }
            }
            // Ensure widgets panel is visible when entering main menu
            if (cardName.equals(MAIN_MENU)) {
                updateWidgetPanelVisibility();
                if (mainMenuPanel != null) {
                    mainMenuPanel.revalidate();
                    mainMenuPanel.repaint();
                }
            }
            return;
        }

        // Animations are enabled and it's not the first switch.
        // Ensure glass pane is visible to start the fade.
        if (!fadePanel.isVisible()) {
            fadePanel.setVisible(true);
        }

        fadePanel.startFadeOut(() -> {
            cardLayout.show(cardPanel, cardName);
            if (cardName.equals(NOTEBOOK_MANAGER)) {
                for (Component c : cardPanel.getComponents()) {
                    if (c instanceof NotebookManagerPanel nm) {
                        nm.refresh();
                    }
                }
            }
            if (cardName.equals(MAIN_MENU)) {
                updateWidgetPanelVisibility();
                if (mainMenuPanel != null) {
                    mainMenuPanel.revalidate();
                    mainMenuPanel.repaint();
                }
            }
            fadePanel.startFadeIn();
        });
    }

    // MAIN MENU: Contains the big background, the clock, the header, and animated
    // buttons
    private JPanel createMainMenuPanel() {
        MainMenuPanel panel = new MainMenuPanel(this);
        // Ensure widgets panel is visible immediately on creation
        panel.updateWidgetPanelVisibility();
        return panel;
    }

    private void showTutorialIfFirstTime() {
        SettingsStore store = SettingsStore.get();
        if (!store.isTutorialSeen()) {
            boolean yes = CustomConfirmDialog.confirm(this, "Quick Tour",
                    "Would you like a quick tour of Simjot's features?");
            if (yes) {
                new TutorialDialog(this).setVisible(true);
            }
            store.setTutorialSeen(true);
            store.save();
        }
    }

    public void ensureFullScreen() {
        SwingUtilities.invokeLater(() -> {
            // If the frame was minimised (iconified) bring it back first
            if ((getExtendedState() & JFrame.ICONIFIED) != 0) {
                setExtendedState(JFrame.NORMAL);
            }

            setExtendedState(JFrame.MAXIMIZED_BOTH);
        });
    }

    // Called by DrawingPanel after saving a new file so gallery updates next time
    public void refreshGallery() {
        if (galleryPanel != null)
            galleryPanel.refresh();
    }

    // Rebuilds the main menu panel (e.g., when wallpaper or theme changes)
    public void recreateMainMenuPanel() {
        if (mainMenuPanel != null) {
            cardPanel.remove(mainMenuPanel);
        }
        mainMenuPanel = createMainMenuPanel();
        cardPanel.add(mainMenuPanel, MAIN_MENU);
        cardLayout.show(cardPanel, MAIN_MENU);
        revalidate();
        repaint();
    }

    // Updates widget panel visibility without recreating the entire main menu
    public void updateWidgetPanelVisibility() {
        if (mainMenuPanel instanceof MainMenuPanel) {
            ((MainMenuPanel) mainMenuPanel).updateWidgetPanelVisibility();
        }
    }

    /**
     * Opens the entry manager panel for the given notebook. If it doesn't
     * exist yet, it will be created and added to the CardLayout on-the-fly.
     */
    public void openNotebookEntries(NotebookInfo nb) {
        String cardId = "NotebookEntries_" + nb.getName();
        if (!notebookPanels.containsKey(cardId)) {
            NotebookEntriesPanel panel = new NotebookEntriesPanel(this, nb);
            notebookPanels.put(cardId, panel);
            cardPanel.add(panel, cardId);
        } else {
            notebookPanels.get(cardId).refresh();
        }
        showCardImmediate(cardId);
    }

    /**
     * Shows a card without triggering the fade transition. Useful for quick
     * panel creations where the fade would otherwise reveal a blank frame.
     */
    public void showCardImmediate(String cardName) {
        CardLayout cl = cardLayout;
        cl.show(cardPanel, cardName);
    }

    /**
     * Launches the appropriate editor panel (journal entry, note, or poem)
     * for creating a new entry inside the given notebook. A unique card ID
     * is generated each time so the user can open multiple editors in the
     * same session.
     */
    public void openNewEntryEditor(NotebookInfo nb) {
        String cardId = "Editor_" + nb.getName() + "_" + System.currentTimeMillis();
        JPanel editor;
        switch (nb.getType()) {
            case JOURNAL -> editor = new NewEntryPanel(this, nb.getFolder(), cardLayout, cardPanel);
            case POETRY -> editor = new PoemPanel(this, nb.getFolder(), cardLayout, cardPanel);
            default -> throw new IllegalStateException("Unexpected value: " + nb.getType());
        }
        cardPanel.add(editor, cardId);
        switchCard(cardId);
    }

    /** Opens an existing file in proper editor based on notebook type */
    public void openExistingEntryEditor(NotebookInfo nb, java.io.File file) {
        String cardId = "Edit_" + file.getName();
        if (cardMap.containsKey(cardId)) {
            showCardImmediate(cardId);
            return;
        }

        JPanel editor;
        switch (nb.getType()) {
            case JOURNAL -> editor = new EditEntryPanel(this, file, nb.getFolder(), cardLayout, cardPanel);
            case POETRY -> editor = new EditPoemPanel(this, file, nb.getFolder(), cardLayout, cardPanel);
            default -> {
                return;
            }
        }
        cardPanel.add(editor, cardId);
        showCardImmediate(cardId);
        cardMap.put(cardId, editor);
    }

    public JPanel getCardPanel() {
        return cardPanel;
    }

    public void refreshNotebookManager() {
        for (Component c : cardPanel.getComponents()) {
            if (c instanceof NotebookManagerPanel nm) {
                nm.refresh();
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Apply LAF and UI scaling before creating the splash to prevent flicker
            try { AeroLookAndFeel.apply(); } catch (Throwable ignored) {}
            applyUIScaling();
            // Show Aero-themed splashscreen while bootstrapping
            AeroSplashScreen splash = new AeroSplashScreen();
            splash.setStatus("Starting…");
            splash.setVisible(true);
            final long splashShownAt = System.nanoTime();

            // Run startup pre-warm tasks off the EDT and update splash status
            new javax.swing.SwingWorker<Void, String>() {
                @Override protected Void doInBackground() {
                    publish("Loading settings…");
                    try { main.util.SettingsStore.get().getUIScale(); } catch (Throwable ignored) {}
                    try { Thread.sleep(120); } catch (InterruptedException ignored) {}

                    publish("Preparing icons…");
                    try { main.ui.icons.AppIcon.generateIconImages(); } catch (Throwable ignored) {}
                    try { Thread.sleep(120); } catch (InterruptedException ignored) {}

                    long start = System.nanoTime();

                    publish("Warming vector icons…");
                    try {
                        int[] sizes = {16,20,24,32,48,64};
                        String[] ids = {"notebook","pencil","image","smile","wrench","clock","tick","breath"};
                        for (int s : sizes) {
                            for (String id : ids) {
                                try { main.ui.icons.VectorIconPainter.getImage(id, s); } catch (Throwable ignored2) {}
                            }
                        }
                    } catch (Throwable ignored) {}
                    try { Thread.sleep(120); } catch (InterruptedException ignored) {}

                    // Theme is already applied before the splash is shown to avoid visual changes while splash is visible

                    publish("Preparing filesystem…");
                    try {
                        // Mirror logic from loadOrChooseRootFolder: check config and ensure subfolders
                        java.io.File cfg = new java.io.File(System.getProperty("user.home"), ".simjournal_config.txt");
                        if (cfg.exists()) {
                            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(cfg))) {
                                String path = r.readLine();
                                if (path != null) {
                                    java.io.File folder = new java.io.File(path);
                                    if (folder.exists() && folder.isDirectory()) {
                                        main.util.AppDirectories.setRoot(folder);
                                        for (main.util.AppDirectories.Type t : main.util.AppDirectories.Type.values()) {
                                            main.util.AppDirectories.folder(t);
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) {}

                    // Warm font/text rendering via an offscreen draw to prime text rasterizer/metrics
                    publish("Priming text rendering…");
                    try {
                        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(400, 120, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g2 = img.createGraphics();
                        g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(java.awt.Color.WHITE);
                        g2.fillRect(0,0,img.getWidth(),img.getHeight());
                        g2.setColor(main.ui.theme.aero.AeroTheme.TEXT_PRIMARY);
                        g2.setFont(g2.getFont().deriveFont(java.awt.Font.BOLD, 28f));
                        g2.drawString("Simjot", 16, 48);
                        g2.setFont(g2.getFont().deriveFont(java.awt.Font.PLAIN, 15f));
                        g2.setColor(new java.awt.Color(0,0,0,180));
                        g2.drawString("Starting…", 16, 78);
                        g2.dispose();
                    } catch (Throwable ignored) {}

                    // Light metadata warmup: discover root from config and touch top-level entries
                    publish("Scanning workspace…");
                    try {
                        java.io.File cfg = new java.io.File(System.getProperty("user.home"), ".simjournal_config.txt");
                        java.io.File root = null;
                        if (cfg.exists()) {
                            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(cfg))) {
                                String path = r.readLine();
                                if (path != null) {
                                    java.io.File folder = new java.io.File(path);
                                    if (folder.exists() && folder.isDirectory()) root = folder;
                                }
                            }
                        }
                        if (root != null) {
                            java.io.File[] list = root.listFiles();
                            if (list != null) {
                                int n = Math.min(list.length, 200); // soft cap
                                for (int i=0; i<n; i++) { java.io.File f = list[i]; f.exists(); /* touch */ }
                            }
                        }
                    } catch (Throwable ignored) {}

                    // Soft time cap (~2.5s) to avoid lingering on splash
                    try {
                        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
                        if (elapsedMs < 2500) Thread.sleep(50); // tiny debounce, keep UI responsive
                    } catch (Throwable ignored) {}

                    // Filesystem prep is performed when JournalApp constructs the UI (root selection).
                    // We keep splash visible until the frame is created to avoid flicker.
                    return null;
                }

                @Override protected void process(java.util.List<String> chunks) {
                    if (!chunks.isEmpty()) splash.setStatus(chunks.get(chunks.size()-1));
                }

                @Override protected void done() {
                    // Create the main window; keep splash until ANY app window shows AND min duration elapsed
                    new JournalApp();
                    final int minMs = 2000; // minimum splash time
                    javax.swing.Timer wait = new javax.swing.Timer(40, ev -> {
                        java.awt.Window[] wins = java.awt.Window.getWindows();
                        boolean otherVisible = false;
                        for (java.awt.Window w : wins) {
                            if (w != splash && w.isShowing()) { otherVisible = true; break; }
                        }
                        long elapsedMs = (System.nanoTime() - splashShownAt) / 1_000_000L;
                        if (otherVisible && elapsedMs >= minMs) {
                            ((javax.swing.Timer) ev.getSource()).stop();
                            splash.dispose();
                        }
                    });
                    wait.setRepeats(true);
                    wait.start();
                }
            }.execute();
        });
    }

    private static void applyUIScaling() {
        try {
            // Get the UI scale from settings
            float uiScale = SettingsStore.get().getUIScale();

            if (uiScale != 1.0f) {
                // Scale all UI fonts
                javax.swing.UIDefaults defaults = UIManager.getDefaults();
                java.util.Enumeration<Object> keys = defaults.keys();

                while (keys.hasMoreElements()) {
                    Object key = keys.nextElement();
                    Object value = defaults.get(key);

                    if (value instanceof javax.swing.plaf.FontUIResource) {
                        javax.swing.plaf.FontUIResource font = (javax.swing.plaf.FontUIResource) value;
                        int newSize = Math.round(font.getSize() * uiScale);
                        javax.swing.plaf.FontUIResource scaledFont = new javax.swing.plaf.FontUIResource(
                                font.getName(), font.getStyle(), newSize);
                        UIManager.put(key, scaledFont);
                    }
                }

                // Scale component sizes
                UIManager.put("Button.margin", new java.awt.Insets(
                        Math.round(2 * uiScale), Math.round(14 * uiScale),
                        Math.round(2 * uiScale), Math.round(14 * uiScale)));
                UIManager.put("TextField.margin", new java.awt.Insets(
                        Math.round(2 * uiScale), Math.round(6 * uiScale),
                        Math.round(2 * uiScale), Math.round(6 * uiScale)));
                UIManager.put("ComboBox.padding", new java.awt.Insets(
                        Math.round(3 * uiScale), Math.round(3 * uiScale),
                        Math.round(3 * uiScale), Math.round(3 * uiScale)));

                // Scale scroll bar width
                UIManager.put("ScrollBar.width", Math.round(16 * uiScale));

                // Update the global font size used by the application
                globalJournalFontSize = Math.round(globalJournalFontSize * uiScale);
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not apply UI scaling: " + e.getMessage());
        }
    }

    // ---------- Cork Icon ---------
    private static class CorkIcon implements javax.swing.Icon {
        private final int w, h;

        CorkIcon(int w, int h) {
            this.w = w;
            this.h = h;
        }

        @Override
        public int getIconWidth() {
            return w;
        }

        @Override
        public int getIconHeight() {
            return h;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(194, 149, 93)); // cork brown
            g2.fillRoundRect(x, y, w, h, 4, 4);
            g2.setColor(new Color(150, 100, 60));
            g2.drawRoundRect(x, y, w - 1, h - 1, 4, 4);
            // decorative dots
            int dots = 8;
            java.util.Random rnd = new java.util.Random();
            for (int i = 0; i < dots; i++) {
                int dx = rnd.nextInt(w - 4) + 2;
                int dy = rnd.nextInt(h - 4) + 2;
                g2.fillOval(x + dx, y + dy, 2, 2);
            }
            g2.dispose();
        }
    }

    // ----------- Time Info Panel ----------
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
            javax.swing.Timer t = new javax.swing.Timer(1000, e -> {
                update();
            });
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
