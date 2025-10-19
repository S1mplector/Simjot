package main.ui.app;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import javax.swing.*;
import main.core.service.SettingsStore;
import main.core.sim.api.SimEventBus;
import main.core.sim.data.SimDataGateway;
import main.core.sim.engine.SimBrain;
import main.core.sim.engine.SimScheduler;
import main.core.sim.persona.SimPersonality;
import main.core.sim.prefs.SimSettings;
import main.infrastructure.backup.BackupService;
import main.infrastructure.backup.NotebookInfo;
import main.infrastructure.io.AppDirectories;
import main.infrastructure.monitoring.RamMonitor;
import main.ui.animations.transitions.FadeTransitionPanel;
import main.ui.components.icons.AppIcon;
import main.ui.dialog.confirmation.CustomConfirmDialog;
import main.ui.dialog.setup.SetupWizardDialog;
import main.ui.dialog.setup.TutorialDialog;
import main.ui.features.drawing.DrawingPanel;
import main.ui.features.entries.NotebookEditor;
import main.ui.features.entries.NotebookEditorFactory;
import main.ui.features.entries.NotebookEditorType;
import main.ui.features.entries.NotebookEntriesPanel;
import main.ui.features.gallery.GalleryPanel;
import main.ui.features.home.MainMenuPanel;
import main.ui.features.home.MoodChartPanel;
import main.ui.features.notebooks.NotebookManagerPanel;
import main.ui.features.settings.SettingsPanel;
import main.ui.features.splash.AeroSplashScreen;
import main.ui.sim.overlay.SimOverlay;
import main.ui.theme.aero.AeroLookAndFeel;
import main.ui.scaling.UIScalingManager;

/**
 * The main application window for Simjot.
 * Manages the UI and handles the lifecycle of the application.
 * 
 * @author S1mplector
 */
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

    // Track open editors so we can save on exit
    private final java.util.List<main.ui.features.entries.NotebookEditor> openEditors = new java.util.ArrayList<>();

    // Factory/DI for editors
    private NotebookEditorFactory editorFactory;

    // Sim components
    private SimOverlay simOverlay;
    private SimBrain simBrain;
    private SimScheduler simScheduler;

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

    // Sim runtime control
    public void enableSimFeatures() {
        try {
            if (simOverlay == null) {
                simOverlay = new SimOverlay();
                JLayeredPane lp = getLayeredPane();
                Dimension pref = simOverlay.getPreferredSize();
                simOverlay.setBounds(16, 16, pref.width, pref.height);
                lp.add(simOverlay, JLayeredPane.POPUP_LAYER);
            }
            if (simBrain == null) {
                SimSettings simSettings = SimSettings.get();
                SimPersonality personality = new SimPersonality(simSettings.getPersonality());
                simBrain = new SimBrain(simSettings, personality, SimDataGateway.get());
            }
            if (simScheduler == null) {
                simScheduler = new SimScheduler();
            }
            // Always (re)start scheduler with latest settings and personality
            try {
                SimSettings s = SimSettings.get();
                SimPersonality p = new SimPersonality(s.getPersonality());
                simScheduler.start(s, p, SimDataGateway.get());
            } catch (Throwable ignored) {}
            revalidate();
            repaint();
        } catch (Throwable ignored) {}
    }

    public void disableSimFeatures() {
        try {
            if (simBrain != null) {
                simBrain.shutdown();
                simBrain = null;
            }
            if (simScheduler != null) {
                try { simScheduler.stop(); } catch (Throwable ignored) {}
                simScheduler = null;
            }
            if (simOverlay != null) {
                try { simOverlay.disposeOverlay(); } catch (Throwable ignored) {}
                try { getLayeredPane().remove(simOverlay); } catch (Throwable ignored) {}
                simOverlay = null;
            }
            revalidate();
            repaint();
        } catch (Throwable ignored) {}
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
                        // Guarantee only active sub-directories exist when loading existing root
                        AppDirectories.folder(AppDirectories.Type.NOTEBOOKS);
                        AppDirectories.folder(AppDirectories.Type.MOOD_DATA);
                        AppDirectories.folder(AppDirectories.Type.SETTINGS);
                        AppDirectories.folder(AppDirectories.Type.WALLPAPERS);
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
        }
    }

    private static float readUIScaleFromConfig() {
        try {
            String home = System.getProperty("user.home");
            java.io.File configFile = new java.io.File(home, ".simjournal_config.txt");

            if (!configFile.exists()) return 1.0f;

            // Read the root folder path from config
            String rootPath = null;
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(configFile))) {
                rootPath = reader.readLine();
            }

            if (rootPath == null || rootPath.trim().isEmpty()) return 1.0f;

            // Try to read preferences.properties from the settings folder
            java.io.File settingsDir = new java.io.File(rootPath, "settings");
            java.io.File prefsFile = new java.io.File(settingsDir, "preferences.properties");

            if (!prefsFile.exists()) return 1.0f;

            java.util.Properties props = new java.util.Properties();
            try (java.io.FileInputStream in = new java.io.FileInputStream(prefsFile)) {
                props.load(in);
            }

            String scaleStr = props.getProperty("uiScale", "1.0");
            float scale = Float.parseFloat(scaleStr);
            System.out.println("[UIScaling] Read user scale from config: " + scale);
            return Math.max(0.5f, Math.min(3.0f, scale)); // Clamp to valid range
        } catch (Exception e) {
            System.err.println("[UIScaling] Could not read user scale from config: " + e.getMessage());
        }
        return 1.0f;
    }

    private void initUI() {
        // Apply Windows 7 Aero-inspired look & feel and defaults before building UI
        AeroLookAndFeel.apply();
        // Re-apply UI scaling AFTER L&F so that any defaults it sets are scaled appropriately
        try {
            // Read UI scale from config file directly since SettingsStore isn't available yet
            float userScale = readUIScaleFromConfig();
            UIScalingManager.applyToSwing(userScale);
            // Update global font size based on effective scale
            float effectiveScale = (userScale > 0 && userScale != 1.0f) ? userScale : UIScalingManager.getDetectedScale();
            globalJournalFontSize = Math.round(14 * effectiveScale);
        } catch (Throwable e) {
            System.err.println("[UIScaling] Error in initUI: " + e.getMessage());
            UIScalingManager.applyToSwing(1.0f);
        }
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);

        // Main menu
        mainMenuPanel = createMainMenuPanel();
        cardPanel.add(mainMenuPanel, MAIN_MENU);

        // Editor factory/DI
        editorFactory = new NotebookEditorFactory(this, cardLayout, cardPanel);

        // "New" creation panels (use factory)
        {
            NotebookEditor e = editorFactory.create(NotebookEditorType.ENTRY);
            cardPanel.add(e.getMainComponent(), NEW_ENTRY);
        }
        {
            NotebookEditor p = editorFactory.create(NotebookEditorType.POEM);
            cardPanel.add(p.getMainComponent(), NEW_POEM);
        }


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

        // --- Sim: overlay + brain + scheduler ---
        try {
            if (SimSettings.get().isEnabled()) {
                enableSimFeatures();
            }
        } catch (Throwable ignored) {}

        setVisible(true);
        switchCard(MAIN_MENU);

        // Ensure backup service is watching according to settings
        try { BackupService.get().start(); } catch (Throwable ignored) {}

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

            // Optionally open last note on startup
            try {
                main.core.service.SettingsStore store = main.core.service.SettingsStore.get();
                if (store.isOpenLastOnStartup()) {
                    String path = store.getLastOpenedFilePath();
                    if (path != null && !path.isBlank()) {
                        java.io.File last = new java.io.File(path);
                        if (last.exists() && last.isFile() && last.canRead()) {
                            NotebookEditor editor = editorFactory.createForFile(last);
                            String cardId = "StartupLast_" + last.getName() + "_" + System.currentTimeMillis();
                            cardPanel.add(editor.getMainComponent(), cardId);
                            showCardImmediate(cardId);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        });

        // On close, attempt a due backup synchronously
        try {
            this.addWindowListener(new WindowAdapter(){
                @Override public void windowClosing(WindowEvent e) {
                    // Best-effort: trigger save on all open editors first (persist guided responses)
                    try {
                        for (main.ui.features.entries.NotebookEditor ed : new java.util.ArrayList<>(openEditors)) {
                            try { ed.triggerSave(); } catch (Throwable ignored) {}
                        }
                    } catch (Throwable ignored) {}
                    // Then run due backup
                    try { BackupService.get().triggerOnExit(); } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable ignored) {}

        // JVM shutdown hook: covers cases where windowClosing isn't delivered (e.g., OS-level quit)
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    for (main.ui.features.entries.NotebookEditor ed : new java.util.ArrayList<>(openEditors)) {
                        try { ed.triggerSave(); } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
                try { BackupService.get().triggerOnExit(); } catch (Throwable ignored) {}
            }, "SimjotShutdownSave"));
        } catch (Throwable ignored) {}
    }

    public void switchCard(String cardName) {
        FadeTransitionPanel fadePanel = (FadeTransitionPanel) getGlassPane();
        // Emit Sim event for card switch
        try { SimEventBus.get().emitCardSwitched(cardName); } catch (Throwable ignored) {}

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
     * 
     * For journal entries, shows a template selection dialog first.
     */
    public void openNewEntryEditor(NotebookInfo nb) {
        String cardId = "Editor_" + nb.getName() + "_" + System.currentTimeMillis();
        java.io.File targetFolder = nb.getFolder();
        
        // For journal entries, show template selection dialog
        if (nb.getType() == NotebookInfo.Type.JOURNAL) {
            main.ui.features.entries.EntryTypeSelectionDialog dialog = 
                new main.ui.features.entries.EntryTypeSelectionDialog((Frame) SwingUtilities.getWindowAncestor(this), nb);
            dialog.setVisible(true);
            
            if (!dialog.isAccepted()) {
                return; // User cancelled
            }
            
            NotebookEditor editor = editorFactory.createInFolder(NotebookEditorType.ENTRY, targetFolder);
            openEditors.add(editor);
            
            // If guided mode, set up question flow
            if (dialog.isGuidedMode()) {
                String[] questions = dialog.getGuidedQuestions();
                if (questions != null && questions.length > 0) {
                    editor.setGuidedQuestions(questions);
                }
            }
            
            cardPanel.add(editor.getMainComponent(), cardId);
            switchCard(cardId);
        } else {
            // Poetry or other types: use existing flow
            NotebookEditor editor = editorFactory.createInFolder(NotebookEditorType.POEM, targetFolder);
            openEditors.add(editor);
            cardPanel.add(editor.getMainComponent(), cardId);
            switchCard(cardId);
        }
    }

    /** Opens an existing file in proper editor based on notebook type */
    public void openExistingEntryEditor(NotebookInfo nb, java.io.File file) {
        String cardId = "Edit_" + file.getName();
        if (cardMap.containsKey(cardId)) {
            showCardImmediate(cardId);
            return;
        }

        NotebookEditor editor = editorFactory.createForFile(file);
        cardPanel.add(editor.getMainComponent(), cardId);
        showCardImmediate(cardId);
        cardMap.put(cardId, (JPanel) editor.getMainComponent());
        openEditors.add(editor);
    }

    public JPanel getCardPanel() {
        return cardPanel;
    }

    public NotebookEditorFactory getEditorFactory() {
        return editorFactory;
    }

    public void refreshNotebookManager() {
        for (Component c : cardPanel.getComponents()) {
            if (c instanceof NotebookManagerPanel nm) {
                nm.refresh();
            }
        }
    }

    public static void main(String[] args) {
        // Initialize scaling BEFORE Swing EDT to set JVM properties early
        UIScalingManager.initializeEarly();
        
        SwingUtilities.invokeLater(() -> {
            // Apply LAF and UI scaling before creating the splash to prevent flicker
            try { AeroLookAndFeel.apply(); } catch (Throwable ignored) {}
            // Apply scaling to Swing components (uses the scale detected in initializeEarly)
            UIScalingManager.applyToSwing(UIScalingManager.getDetectedScale());
            // Show Aero-themed splashscreen while bootstrapping
            AeroSplashScreen splash = new AeroSplashScreen();
            splash.setStatus("Starting…");
            splash.setVisible(true);
            final long splashShownAt = System.nanoTime();

            // Run startup pre-warm tasks off the EDT and update splash status
            new javax.swing.SwingWorker<Void, String>() {
                @Override protected Void doInBackground() {
                    publish("Loading settings…");
                    try { main.core.service.SettingsStore.get().getUIScale(); } catch (Throwable ignored) {}
                    try { Thread.sleep(120); } catch (InterruptedException ignored) {}

                    publish("Preparing icons…");
                    try { main.ui.components.icons.AppIcon.generateIconImages(); } catch (Throwable ignored) {}
                    try { Thread.sleep(120); } catch (InterruptedException ignored) {}

                    long start = System.nanoTime();

                    publish("Warming vector icons…");
                    try {
                        int[] sizes = {16,20,24,32,48,64};
                        String[] ids = {"notebook","pencil","image","smile","wrench","clock","tick","breath"};
                        for (int s : sizes) {
                            for (String id : ids) {
                                try { main.ui.components.icons.VectorIconPainter.getImage(id, s); } catch (Throwable ignored2) {}
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
                                        main.infrastructure.io.AppDirectories.setRoot(folder);
                                        // Create only active sub-directories
                                        main.infrastructure.io.AppDirectories.folder(main.infrastructure.io.AppDirectories.Type.NOTEBOOKS);
                                        main.infrastructure.io.AppDirectories.folder(main.infrastructure.io.AppDirectories.Type.MOOD_DATA);
                                        main.infrastructure.io.AppDirectories.folder(main.infrastructure.io.AppDirectories.Type.SETTINGS);
                                        main.infrastructure.io.AppDirectories.folder(main.infrastructure.io.AppDirectories.Type.WALLPAPERS);
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
                    publish("Reading configuration…");
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
                            publish("Checking workspace folders…");
                            java.io.File[] list = root.listFiles();
                            if (list != null) {
                                publish("Indexing top-level items…");
                                int n = Math.min(list.length, 200); // soft cap
                                for (int i=0; i<n; i++) { java.io.File f = list[i]; f.exists(); /* touch */ }
                                publish("Preparing UI…");
                            }
                        }
                    } catch (Throwable ignored) {}

                    // Soft time cap (~2.5s) to avoid lingering on splash
                    try {
                        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
                        if (elapsedMs < 2500) Thread.sleep(50); // tiny debounce, keep UI responsive
                    } catch (Throwable ignored) {}

                    publish("Starting UI…");

                    // Filesystem prep is performed when JournalApp constructs the UI (root selection).
                    // We keep splash visible until the frame is created to avoid flicker.
                    return null;
                }

                @Override protected void process(java.util.List<String> chunks) {
                    if (!chunks.isEmpty()) splash.setStatus(chunks.get(chunks.size()-1));
                }

                @Override protected void done() {
                    // Start the poll timer BEFORE constructing the UI, so that a modal first-run wizard
                    // won't block splash dismissal on the EDT. The timer will dispose the splash once
                    // any other window (e.g., the setup wizard) is showing and the minimum time elapsed.
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

                    // Create the main window (may open a modal SetupWizardDialog on first run).
                    new JournalApp();
                }
            }.execute();
        });
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
