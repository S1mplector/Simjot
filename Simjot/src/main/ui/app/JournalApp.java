/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.app;

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import main.core.AppInfo;
import main.core.security.LockController;
import main.core.service.NotebookStore;
import main.core.service.SettingsStore;
import main.core.sim.api.SimEventBus;
import main.core.sim.data.SimDataGateway;
import main.core.sim.engine.SimBrain;
import main.core.sim.engine.SimScheduler;
import main.core.sim.persona.SimPersonality;
import main.core.sim.prefs.SimSettings;
import main.infrastructure.backup.BackupService;
import main.infrastructure.backup.NotebookInfo;
import main.infrastructure.hotkeys.GlobalHotkeyManager;
import main.infrastructure.io.AppDirectories;
import main.infrastructure.io.CrashReporter;
import main.infrastructure.io.FileIO;
import main.infrastructure.io.ResourceLoader;
import main.infrastructure.monitoring.RamMonitor;
import main.infrastructure.monitoring.AppPerf;
import main.infrastructure.startup.MacLoginItem;
import main.ui.animations.transitions.FadeTransitionPanel;
import main.ui.components.icons.AppIcon;
import main.ui.components.icons.ImageIconRenderer;
import main.ui.dialog.confirmation.CustomChoiceDialog;
import main.ui.dialog.message.CustomMessageDialog;
import main.ui.dialog.security.ElegantLockScreen;
import main.ui.dialog.setup.SetupWizardDialog;
import main.ui.features.drawing.DrawingPanel;
import main.ui.features.entries.GlobalSearchDialog;
import main.ui.features.entries.NotebookEditor;
import main.ui.features.entries.NotebookEditorFactory;
import main.ui.features.entries.NotebookEditorType;
import main.ui.features.entries.NotebookEntriesPanel;
import main.ui.features.entries.QuickCaptureDialog;
import main.ui.features.entries.TemplateManagerDialog;
import main.ui.features.entries.JournalTemplateManager;
import main.ui.features.gallery.GalleryPanel;
import main.ui.features.gallery.GeneratedWallpapers;
import main.ui.features.home.QuoteGalleryPanel;
import main.ui.features.home.MainMenuPanel;
import main.ui.features.home.ElegantMoodChartPanel;
import main.ui.features.notebooks.NotebookManagerPanel;
import main.ui.features.settings.SettingsPanel;
import main.ui.features.splash.AeroSplashScreen;
import main.ui.sim.chat.SimChatPanel;
import main.ui.sim.overlay.SimOverlay;
import main.ui.theme.aero.AeroLookAndFeel;
import main.ui.util.AccentColorUtil;

/**
 * The main application window for Simjot.
 * <p>This class serves as the primary entry point and UI controller for the Simjot journaling application.
 * It manages the main JFrame, handles navigation between different panels using a CardLayout,
 * and coordinates the lifecycle of various application components including:</p>
 * 
 * <ul>
 *   <li><strong>UI Navigation:</strong> CardLayout-based panel switching with fade transitions</li>
 *   <li><strong>Notebook Management:</strong> Dynamic creation and management of notebook panels</li>
 *   <li><strong>Editor Management:</strong> Factory-based creation and tracking of open editors</li>
 *   <li><strong>Sim Integration:</strong> AI assistant overlay and brain components</li>
 *   <li><strong>Backup Service:</strong> Automatic and manual backup operations</li>
 *   <li><strong>Security:</strong> Lock screen and notebook encryption</li>
 *   <li><strong>Global Hotkeys:</strong> Quick capture and other shortcuts</li>
 * </ul>
 * 
 * <p>The application is a singleton for the main window and uses lazy loading
 * for many UI components to optimize startup performance. All major operations are performed
 * on the Event Dispatch Thread (EDT) with proper synchronization for background tasks.</p>
 * 
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li>Aero-themed UI with glass-morphism effects</li>
 *   <li>Smooth fade transitions between panels</li>
 *   <li>Comprehensive splash screen with startup progress</li>
 *   <li>Graceful shutdown with backup and cleanup</li>
 *   <li>Template-based journal entry creation</li>
 *   <li>Quick capture functionality with global hotkeys</li>
 * </ul>
 * 
 * @author S1mplector
 * @see ui, infrastructure and core submodules
 * @version 0.1.1
 */
public class JournalApp extends JFrame {
    /**
     * Serialization version for compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Global journal font size used across all journal entry editors.
     * This value is loaded from settings during initialization.
     */
    public static int globalJournalFontSize = 14; // For journal entries

    /**
     * CardLayout manager for switching between different UI panels.
     */
    private CardLayout cardLayout;
    
    /**
     * Main container panel that holds all UI cards.
     */
    private JPanel cardPanel;
    
    /**
     * Root folder where all Simjot data is stored.
     */
    private File rootFolder;

    /**
     * Configuration file storing the journal folder path.
     * Located in user's home directory for persistence across sessions.
     */
    private File configFile;
    
    /**
     * Filename for the configuration file.
     */
    private final String CONFIG_FILENAME = ".simjournal_config.txt";

    private static final String ICLOUD_PROMPT_FLAG = "icloud.migration.prompted";
    
    /**
     * Timeout for the exit watchdog thread to force application termination.
     * Ensures the application doesn't hang indefinitely during shutdown.
     */
    private static final long EXIT_WATCHDOG_TIMEOUT_MS = 25_000L;
    
    /**
     * Minimum time to show the startup splash screen.
     * Provides visual consistency and allows background tasks to complete.
     */
    private static final int STARTUP_MIN_SPLASH_MS = 6500;
    
    /**
     * Minimum time to show the exit splash screen.
     * Ensures users see the exit animation even for quick shutdowns.
     */
    private static final int EXIT_MIN_SPLASH_MS = 5500;
    
    /**
     * Interval for updating the exit splash screen status messages.
     * Provides dynamic feedback during shutdown operations.
     */
    private static final long EXIT_PULSE_MS = 1200L;
    
    /**
     * Command line arguments passed to the application.
     * Used for relaunch functionality and debugging.
     */
    private static String[] launchArgs = new String[0];
    private static final String START_IN_TRAY_ARG = "--start-in-tray";
    private static boolean startInTrayOnLaunch = false;

    // ====================
    // CARD IDENTIFIERS
    // ====================
    
    /**
     * Card identifier for the main menu panel.
     */
    public static final String MAIN_MENU = "Main Menu";
    
    /**
     * Card identifier for new journal entry creation.
     */
    public static final String NEW_ENTRY = "New Entry";
    
    /**
     * Card identifier for mood chart visualization.
     */
    public static final String MOOD_CHART = "Mood Chart";

    /**
     * Card identifier for quote gallery.
     */
    public static final String QUOTE_GALLERY = "Quote Gallery";
    
    /**
     * Card identifier for new poem creation.
     */
    public static final String NEW_POEM = "New Poem";
    
    /**
     * Card identifier for application settings panel.
     */
    public static final String SETTINGS = "Settings";
    
    /**
     * Card identifier for drawing gallery.
     */
    public static final String GALLERY = "Gallery";
    
    /**
     * Card identifier for notebook management interface.
     */
    public static final String NOTEBOOK_MANAGER = "Notebook Manager";

    /**
     * Card identifier for fullscreen Sim chat interface.
     */
    public static final String SIM_CHAT = "Sim Chat";

    // ====================
    // UI COMPONENT REFERENCES
    // ====================
    
    /**
     * Reference to the settings panel for dynamic updates.
     */
    private SettingsPanel settingsPanel;
    
    /**
     * RAM monitoring panel for system resource tracking.
     */
    private RamMonitor ramUsagePanel;
    
    /**
     * Gallery panel for managing drawings and wallpapers.
     */
    private GalleryPanel galleryPanel;

    /**
     * Quote gallery panel for browsing and favoriting quotes.
     */
    private QuoteGalleryPanel quoteGalleryPanel;

    /**
     * Flag to track if the first panel switch has occurred.
     * Used to disable animations for the initial switch to improve performance.
     */
    private boolean firstSwitchDone = false;

    /**
     * Reference to the main menu panel for dynamic updates.
     */
    private JPanel mainMenuPanel;

    // ====================
    // DYNAMIC PANEL MANAGEMENT
    // ====================
    
    /**
     * Map of dynamically created notebook entry panels.
     * Key: card identifier, Value: NotebookEntriesPanel instance.
     * Allows for lazy creation and reuse of notebook panels.
     */
    private final java.util.Map<String, NotebookEntriesPanel> notebookPanels = new java.util.HashMap<>();

    /**
     * Map of all dynamically created cards for quick lookup.
     * Used to prevent duplicate card creation and enable fast navigation.
     */
    private final java.util.Map<String, JPanel> cardMap = new java.util.HashMap<>();

    /**
     * List of currently open editors for save-on-exit functionality.
     * Tracks all active NotebookEditor instances to ensure proper cleanup.
     */
    private final java.util.List<main.ui.features.entries.NotebookEditor> openEditors = new java.util.ArrayList<>();

    /**
     * Factory for creating notebook editors with proper dependency injection.
     * Centralizes editor creation logic and ensures consistent configuration.
     */
    private NotebookEditorFactory editorFactory;

    // ====================
    // QUICK CAPTURE FUNCTIONALITY
    // ====================
    
    /**
     * The last active notebook for quick capture operations.
     * Persists across quick capture sessions to improve user experience.
     */
    private NotebookInfo lastActiveNotebook;
    
    /**
     * Key event dispatcher for in-app quick capture hotkey.
     * Used when global hotkey registration fails.
     */
    private KeyEventDispatcher quickCaptureDispatcher;

    /**
     * Set of static card identifiers that have been created.
     * Used for lazy loading to optimize startup performance.
     */
    private final java.util.Set<String> createdStaticCards = new java.util.HashSet<>();

    // ====================
    // SIM AI ASSISTANT COMPONENTS
    // ====================
    
    /**
     * Visual overlay component for the Sim AI assistant.
     * Provides chat interface and AI interaction capabilities.
     */
    private SimOverlay simOverlay;

    /**
     * Fullscreen chat panel used by Sim.
     */
    private SimChatPanel simChatPanel;

    /**
     * Last card before entering Sim chat, used for return navigation.
     */
    private String simChatReturnCard = MAIN_MENU;
    
    /**
     * Core AI brain component for Sim.
     * Handles reasoning, memory, and response generation.
     */
    private SimBrain simBrain;
    
    /**
     * Scheduler for proactive Sim interactions.
     * Manages timed events and context-aware responses.
     */
    private SimScheduler simScheduler;
    
    /**
     * Atomic flag to prevent re-entrant shutdown operations.
     * Ensures graceful shutdown occurs only once.
     */
    private final AtomicBoolean exitInProgress = new AtomicBoolean(false);

    /**
     * Constructs the main application window.
     * 
     * <p>This constructor performs the following initialization steps:</p>
     * <ol>
     *   <li>Sets up the JFrame with application title and icons</li>
     *   <li>Loads or prompts for the root journal folder</li>
     *   <li>Initializes the UI if the root folder is successfully established</li>
     *   <li>Exits the application if setup fails</li>
     * </ol>
     * 
     * <p>The constructor uses DO_NOTHING_ON_CLOSE to enable graceful shutdown
     * with proper backup and cleanup operations.</p>
     */
    public JournalApp() {
        super(AppInfo.fullTitle());
        // Set the application icon using the AppIcon utility
        setIconImages(AppIcon.generateIconImages());
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        loadOrChooseRootFolder();
        if (rootFolder != null) {
            initUI();
        } else {
            System.exit(0);
        }
    }

    // ===========================
    // SIM AI ASSISTANT MANAGEMENT
    // ===========================
    
    /**
     * Enables and initializes Sim AI assistant features.
     * 
     * <p>This method creates and configures all Sim components:
     * <ul>
     *   <li>SimOverlay: Visual chat interface overlay</li>
     *   <li>SimBrain: Core AI reasoning engine</li>
     *   <li>SimScheduler: Proactive interaction scheduler</li>
     * </ul>
     * 
     * <p>Components are created lazily and restarted with current settings
     * to ensure configuration changes take effect immediately.</p>
     */
    public void enableSimFeatures() {
        try {
            // Create overlay if not exists
            if (simOverlay == null) {
                simOverlay = new SimOverlay(this);
                JLayeredPane lp = getLayeredPane();
                Dimension pref = simOverlay.getPreferredSize();
                simOverlay.setBounds(16, 16, pref.width, pref.height);
                lp.add(simOverlay, JLayeredPane.POPUP_LAYER);
            }
            // Create AI brain if not exists
            if (simBrain == null) {
                SimSettings simSettings = SimSettings.get();
                SimPersonality personality = new SimPersonality(simSettings.getPersonality());
                simBrain = new SimBrain(simSettings, personality, SimDataGateway.get());
            }
            // Create scheduler if not exists
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

    /**
     * Disables and shuts down Sim AI assistant features.
     * 
     * <p>This method performs graceful shutdown of all Sim components:
     * <ul>
     *   <li>Stops the AI brain and releases resources</li>
     *   <li>Stops the proactive scheduler</li>
     *   <li>Disposes of the visual overlay</li>
     * </ul>
     * 
     * <p>All operations are wrapped in try-catch blocks to ensure
     * shutdown continues even if individual components fail.</p>
     */
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
            if (simChatPanel != null) {
                try { simChatPanel.disposePanel(); } catch (Throwable ignored) {}
            }
            revalidate();
            repaint();
        } catch (Throwable ignored) {}
    }

    // =======================
    // CONFIGURATION AND SETUP  
    // =======================
    
    /**
     * Loads the root journal folder from configuration or prompts user to choose one.
     * 
     * <p>This method follows these steps:
     * <ol>
     *   <li>Attempts to read the configuration file from user's home directory</li>
     *   <li>Validates that the stored path exists and is a directory</li>
     *   <li>Sets up required subdirectories if valid folder found</li>
     *   <li>Shows setup wizard if no valid configuration exists</li>
     *   <li>Saves the chosen folder path to configuration file</li>
     * </ol>
     * 
     * <p>Cleanup of temporary files is performed when loading an existing root folder.</p>
     */
    private void loadOrChooseRootFolder() {
        configFile = new File(System.getProperty("user.home"), CONFIG_FILENAME);
        File configRoot = null;

        String nativePath = main.infrastructure.ffi.NativeAccess.readConfig(configFile.getAbsolutePath());
        if (nativePath != null && !nativePath.isBlank()) {
            File folder = new File(nativePath.trim());
            if (AppDirectories.isDirectoryOrNoPermission(folder)) {
                configRoot = folder;
            }
        }

        if (configRoot == null && configFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
                String path = reader.readLine();
                if (path != null && !path.isBlank()) {
                    File folder = new File(path.trim());
                    if (AppDirectories.isDirectoryOrNoPermission(folder)) {
                        configRoot = folder;
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        File icloudOnly = AppDirectories.resolveIcloudRoot(configRoot);
        if (icloudOnly != null) {
            int configScore = AppDirectories.estimateDataScore(configRoot);
            int icloudScore = AppDirectories.estimateDataScore(icloudOnly);
            main.infrastructure.io.IoLog.info("root-select", "config=" + pathOrNull(configRoot) +
                    " score=" + configScore + ", icloud=" + pathOrNull(icloudOnly) +
                    " score=" + icloudScore + ", selected=iCloud");
            if (configRoot != null && !AppDirectories.isIcloudRoot(configRoot)) {
                main.infrastructure.io.IoLog.warn("root-select", "Config root not in iCloud; switching to " +
                        icloudOnly.getAbsolutePath(), null);
            }
            configureRootFolder(icloudOnly, !icloudOnly.equals(configRoot));
            return;
        }
        
        // Show setup wizard
        SetupWizardDialog dlg = new SetupWizardDialog(this);
        dlg.setVisible(true);
        rootFolder = dlg.getRootFolder();
        if (rootFolder != null) {
            saveJournalFolderConfig();
        }
    }

    private static String pathOrNull(File f) {
        return f == null ? "null" : f.getAbsolutePath();
    }

    private void configureRootFolder(File folder, boolean saveConfig) {
        if (folder == null) return;
        if (!folder.exists()) {
            if (!folder.mkdirs()) return;
        }
        if (!folder.isDirectory()) return;
        rootFolder = folder;
        AppDirectories.setRoot(rootFolder);
        try { main.infrastructure.ffi.NativeAccess.setupInit(rootFolder.getAbsolutePath()); } catch (Throwable ignored) {}
        AppDirectories.folder(AppDirectories.Type.NOTEBOOKS);
        AppDirectories.folder(AppDirectories.Type.MOOD_DATA);
        AppDirectories.folder(AppDirectories.Type.SETTINGS);
        AppDirectories.folder(AppDirectories.Type.WALLPAPERS);
        AppDirectories.folder(AppDirectories.Type.CUSTOM_FONTS);
        FileIO.cleanupTempFiles(rootFolder.toPath(), ".tmp", 24L * 60L * 60L * 1000L);
        if (saveConfig) {
            saveJournalFolderConfig();
        }
        try { AppConfig.setRootFolder(rootFolder); } catch (Throwable ignored) {}
    }

    private void maybePromptIcloudSwitch(File icloudRoot) {
        if (!AppLifecycle.isMacOS()) return;
        if (icloudRoot == null || rootFolder == null) return;
        if (AppDirectories.isIcloudRoot(rootFolder)) return;
        if (!AppDirectories.looksLikeSimjotRoot(icloudRoot)) return;
        SettingsStore store = SettingsStore.get();
        if (store.getFlag(ICLOUD_PROMPT_FLAG, false)) return;

        String msg = "<html><div style='width:360px;'>" +
                "We found Simjot data in iCloud Drive. " +
                "Use it to sync across your Macs?<br><br>" +
                "<b>iCloud folder:</b><br>" + icloudRoot.getAbsolutePath() +
                "</div></html>";
        String[] options = new String[] { "Use iCloud Data", "Keep Local", "Not Now" };
        int choice = CustomChoiceDialog.choose(this, "iCloud Sync", msg, options);
        if (choice >= 0) {
            store.setFlag(ICLOUD_PROMPT_FLAG, true);
            store.save();
        }
        if (choice == 0) {
            configureRootFolder(icloudRoot, true);
        }
    }

    /**
     * Utility method to safely run a task on the Event Dispatch Thread with timeout.
     * 
     * <p>This method provides robust EDT execution with the following features:</p>
     * <ul>
     *   <li>Immediate execution if already on EDT</li>
     *   <li>Timeout protection to prevent indefinite blocking</li>
     *   <li>Error reporting for both execution failures and timeouts</li>
     *   <li>Proper interrupt handling</li>
     * </ul>
     * 
     * @param task the Runnable to execute on EDT
     * @param timeoutMs maximum time to wait for completion (ms)
     * @return true if task completed successfully, false if timed out or failed
     */
    private static boolean runOnEdtWithTimeout(Runnable task, long timeoutMs) {
        if (task == null) return true;
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                task.run();
                return true;
            }
        } catch (Throwable ignored) {}

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> err = new AtomicReference<>();
        try {
            SwingUtilities.invokeLater(() -> {
                try {
                    task.run();
                } catch (Throwable t) {
                    err.set(t);
                } finally {
                    latch.countDown();
                }
            });
        } catch (Throwable t) {
            try { CrashReporter.report("edt-invoke", Thread.currentThread(), t); } catch (Throwable ignored) {}
            return false;
        }

        boolean ok = false;
        try {
            ok = latch.await(Math.max(1L, timeoutMs), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            ok = false;
        }

        Throwable t = err.get();
        if (t != null) {
            try { CrashReporter.report("edt-task", Thread.currentThread(), t); } catch (Throwable ignored) {}
        }
        if (!ok) {
            try { CrashReporter.report("edt-timeout", Thread.currentThread(), new RuntimeException("EDT task timed out")); } catch (Throwable ignored) {}
        }
        return ok;
    }

    /**
     * Saves the selected journal folder path to the configuration file.
     * 
     * <p>Writes the absolute path of the chosen root folder to the config file
     * in the user's home directory. This ensures persistence across application sessions.</p>
     */
    private void saveJournalFolderConfig() {
        // Use native atomic write for reliability
        boolean written = main.infrastructure.ffi.NativeAccess.writeConfig(
            configFile.getAbsolutePath(), 
            rootFolder.getAbsolutePath()
        );
        
        // Fallback to Java if native fails
        if (!written) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(configFile))) {
                writer.println(rootFolder.getAbsolutePath());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void initUI() {
        // Apply Windows 7 Aero-inspired look & feel and defaults before building UI
        AeroLookAndFeel.apply();
        // UI scaling disabled for now; use stored journal font size as-is
        try { globalJournalFontSize = SettingsStore.get().getJournalFontSize(); } catch (Throwable ignored) {}
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);

        // Main menu
        mainMenuPanel = createMainMenuPanel();
        cardPanel.add(mainMenuPanel, MAIN_MENU);

        // Editor factory/DI
        editorFactory = new NotebookEditorFactory(this, cardLayout, cardPanel);

        // Remaining panels are created lazily upon first navigation

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
        } catch (Throwable t) {
            logWarn("enableSimFeatures", t);
        }

        boolean keepInTray = shouldKeepRunningInTray();
        if (keepInTray) {
            try { AppConfig.initMenuBarService(); } catch (Throwable t) { logWarn("Menu bar init", t); }
        }
        try { syncMacLaunchOnLogin(keepInTray); } catch (Throwable t) { logWarn("Launch on login sync", t); }

        // Start maximized before showing to avoid small initial window
        try { setExtendedState(JFrame.MAXIMIZED_BOTH); } catch (Throwable ignored) {}
        boolean startHidden = keepInTray && startInTrayOnLaunch;
        setVisible(true);
        if (!startHidden) {
            try { toFront(); requestFocus(); } catch (Throwable ignored) {}
        }
        switchCard(MAIN_MENU);
        if (startHidden) {
            hideToTray();
        }

        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop d = java.awt.Desktop.getDesktop();
                java.awt.desktop.QuitStrategy qs = keepInTray
                    ? java.awt.desktop.QuitStrategy.NORMAL_EXIT
                    : java.awt.desktop.QuitStrategy.CLOSE_ALL_WINDOWS;
                try { d.setQuitStrategy(qs); } catch (Throwable ignored) {}
                if (d.isSupported(java.awt.Desktop.Action.APP_QUIT_HANDLER)) {
                    d.setQuitHandler((e, response) -> {
                        try { exitGracefully(); } catch (Throwable t) { logWarn("App quit handler", t); }
                        try { response.cancelQuit(); } catch (Throwable ignored2) {}
                    });
                }
            }
        } catch (Throwable t) {
            logWarn("Desktop quit handler wiring", t);
        }

        // Ensure backup service is watching according to settings
        try { BackupService.get().start(); } catch (Throwable t) { logWarn("BackupService start", t); }

        // After UI visible, optionally show tutorial, then force fullscreen
        SwingUtilities.invokeLater(() -> {
            // Initialize lock controller and optionally lock immediately on startup
            try {
                LockController.get().init(this);
                if (SettingsStore.get().isLockEnabled() && SettingsStore.get().isLockRequireOnStart()) {
                    // Show full-screen Aero-styled lock screen on startup
                    new ElegantLockScreen(this).blockUntilUnlocked();
                }
            } catch (Throwable t) {
                logWarn("Lock init", t);
            }
            ensureFullScreen();
            installQuickCaptureHotkey();
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
            } catch (Throwable t) {
                logWarn("Open last on startup", t);
            }
        });

        // On close, attempt a due backup synchronously
        try {
            this.addWindowListener(new WindowAdapter(){
                @Override public void windowClosing(WindowEvent e) {
                    if (shouldKeepRunningInTray()) {
                        try { AppConfig.initMenuBarService(); } catch (Throwable ignored) {}
                        hideToTray();
                        return;
                    }
                    // Unified graceful shutdown (shows exiting splash, closes resources, exits)
                    exitGracefully();
                }
            });
        } catch (Throwable t) {
            logWarn("windowClosing listener", t);
        }

        // JVM shutdown hook: covers cases where windowClosing isn't delivered (e.g., OS-level quit)
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    CustomMessageDialog.setGlobalSuppressed(true);
                    for (main.ui.features.entries.NotebookEditor ed : new java.util.ArrayList<>(openEditors)) {
                        try { ed.triggerSave(); } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
                // Run backup in a daemon helper and wait briefly; if it takes too long, let JVM exit
                try {
                    Thread t = new Thread(() -> {
                        try { BackupService.get().triggerOnExit(); } catch (Throwable t1) { logWarn("BackupService triggerOnExit (shutdown hook)", t1); }
                        try { BackupService.get().shutdown(); } catch (Throwable t2) { logWarn("BackupService shutdown (hook)", t2); }
                    }, "BackupOnShutdown");
                    t.setDaemon(true);
                    t.start();
                    try { t.join(5000L); } catch (InterruptedException ie) { /* ignore */ }
                } catch (Throwable ignored) {}
                // Hard failsafe: guarantee termination even if other hooks misbehave
                try { Runtime.getRuntime().halt(0); } catch (Throwable ignored) {}
            }, "SimjotShutdownSave"));
        } catch (Throwable ignored) {}
    }

    public void switchCard(String cardName) {
        String currentCard = resolveVisibleCardName();
        handleSimChatTransition(currentCard, cardName);
        FadeTransitionPanel fadePanel = (FadeTransitionPanel) getGlassPane();
        // Emit Sim event for card switch
        try { SimEventBus.get().emitCardSwitched(cardName); } catch (Throwable ignored) {}

        // Ensure target card exists (lazy creation) before showing
        maybeCreateLazyCard(cardName);

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
            maybeCreateLazyCard(cardName);
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

    public void openQuoteViewer(String quoteText, String quoteAuthor) {
        try {
            maybeCreateLazyCard(QUOTE_GALLERY);
            if (quoteGalleryPanel != null) {
                quoteGalleryPanel.showQuote(quoteText, quoteAuthor);
            }
        } catch (Throwable ignored) { }
        switchCard(QUOTE_GALLERY);
    }

    private void maybeCreateLazyCard(String cardName) {
        if (cardName == null) return;
        if (createdStaticCards.contains(cardName)) return;
        try {
            if (cardName.equals(MOOD_CHART)) {
                cardPanel.add(new ElegantMoodChartPanel(this, cardLayout, cardPanel), MOOD_CHART);
                createdStaticCards.add(MOOD_CHART);
            } else if (cardName.equals(QUOTE_GALLERY)) {
                if (quoteGalleryPanel == null) {
                    quoteGalleryPanel = new QuoteGalleryPanel(this);
                }
                cardPanel.add(quoteGalleryPanel, QUOTE_GALLERY);
                createdStaticCards.add(QUOTE_GALLERY);
            } else if (cardName.equals(SETTINGS)) {
                settingsPanel = new SettingsPanel(this, cardLayout, cardPanel);
                cardPanel.add(settingsPanel, SETTINGS);
                createdStaticCards.add(SETTINGS);
            } else if (cardName.equals(GALLERY)) {
                DrawingPanel dp = new DrawingPanel(this);
                cardPanel.add(dp, "Drawing");
                createdStaticCards.add("Drawing");
                galleryPanel = new GalleryPanel(AppDirectories.folder(AppDirectories.Type.DRAWINGS), cardLayout, cardPanel,
                        this, dp);
                cardPanel.add(galleryPanel, GALLERY);
                createdStaticCards.add(GALLERY);
            } else if (cardName.equals(NOTEBOOK_MANAGER)) {
                NotebookManagerPanel notebookManagerPanel = new NotebookManagerPanel(this);
                cardPanel.add(notebookManagerPanel, NOTEBOOK_MANAGER);
                createdStaticCards.add(NOTEBOOK_MANAGER);
            } else if (cardName.equals(SIM_CHAT)) {
                simChatPanel = new SimChatPanel(this::closeSimChatPanel);
                cardPanel.add(simChatPanel, SIM_CHAT);
                createdStaticCards.add(SIM_CHAT);
            } else if (cardName.equals(MAIN_MENU)) {
                // already created in initUI
                createdStaticCards.add(MAIN_MENU);
            }
        } catch (Throwable ignored) {}
    }

    // MAIN MENU: Contains the big background, the clock, the header, and animated
    // buttons
    private JPanel createMainMenuPanel() {
        MainMenuPanel panel = new MainMenuPanel(this);
        // Ensure widgets panel is visible immediately on creation
        panel.updateWidgetPanelVisibility();
        return panel;
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

    public void restartAfterSettingsChange() {
        relaunchCurrentProcess();
        exitGracefully(false);
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

    private boolean shouldKeepRunningInTray() {
        if (!AppLifecycle.isMacOS()) return false;
        try {
            return SettingsStore.get().isMenuBarServiceEnabled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void hideToTray() {
        try { setVisible(false); } catch (Throwable ignored) {}
    }

    private void syncMacLaunchOnLogin(boolean keepInTray) {
        if (!AppLifecycle.isMacOS()) return;
        try {
            SettingsStore store = SettingsStore.get();
            boolean enabled = store.isLaunchOnLoginEnabled();
            MacLoginItem.sync(enabled, keepInTray);
        } catch (Throwable t) {
            logWarn("Launch on login", t);
        }
    }

    // Fail-safe to guarantee termination even if cleanup threads stall
    // Now delegates to native watchdog via AppLifecycle
    private void startExitWatchdog() {
        try {
            AppLifecycle.startExitWatchdog(EXIT_WATCHDOG_TIMEOUT_MS);
        } catch (Throwable t) {
            logWarn("exit watchdog", t);
        }
    }

    // Now delegates to AppLifecycle
    private void relaunchCurrentProcess() {
        try {
            AppLifecycle.relaunch();
        } catch (Throwable t) {
            logWarn("relaunch", t);
        }
    }

    private static boolean isWindows() {
        return AppLifecycle.isWindows();
    }

    /**
     * Shows an exiting splash and performs a graceful shutdown:
     * saves open editors, stops Sim components, stops services, triggers a final backup,
     * then exits the JVM.
     */
    public void exitGracefully() {
        exitGracefully(true);
    }

    private void exitGracefully(boolean showSplash) {
        if (!exitInProgress.compareAndSet(false, true)) {
            return; // Already exiting; avoid re-entrant shutdown work
        }
        startExitWatchdog();
        try {
            disposeNotebookPanelsSafely();
            try {
                GlobalHotkeyManager.unregister();
                if (quickCaptureDispatcher != null) {
                    KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(quickCaptureDispatcher);
                    quickCaptureDispatcher = null;
                }
            } catch (Throwable ignored) {}
            CustomMessageDialog.setGlobalSuppressed(true);
            final AeroSplashScreen splash = showSplash ? new AeroSplashScreen() : null;
            if (splash != null) {
                splash.setStatus("Exiting…");
                splash.setVisible(true);
            }
            final long exitSplashShownAt = showSplash ? System.nanoTime() : 0L;

            if (splash != null) {
                try {
                    Thread pulse = new Thread(() -> {
                        try {
                            while (exitInProgress.get()) {
                                long elapsedMs = (System.nanoTime() - exitSplashShownAt) / 1_000_000L;
                                if (elapsedMs >= 9000 && elapsedMs < 18000) {
                                    SwingUtilities.invokeLater(() -> {
                                        try { splash.setStatus("Still exiting…"); } catch (Throwable ignored) {}
                                    });
                                } else if (elapsedMs >= 18000 && elapsedMs < (EXIT_WATCHDOG_TIMEOUT_MS - 500)) {
                                    SwingUtilities.invokeLater(() -> {
                                        try { splash.setStatus("Finishing up…"); } catch (Throwable ignored) {}
                                    });
                                }
                                Thread.sleep(EXIT_PULSE_MS);
                            }
                        } catch (Throwable ignored) {}
                    }, "SimjotExitPulse");
                    pulse.setDaemon(true);
                    pulse.start();
                } catch (Throwable ignored) {}
            }

            try { setEnabled(false); } catch (Throwable ignored) {}
            try { setVisible(false); } catch (Throwable ignored) {}

            final java.util.List<main.ui.features.entries.NotebookEditor> editors = new java.util.ArrayList<>(openEditors);

            new javax.swing.SwingWorker<Void, String>() {
                @Override protected Void doInBackground() {
                    publish("Saving open editors…");
                    try {
                        JournalApp.runOnEdtWithTimeout(() -> {
                            for (main.ui.features.entries.NotebookEditor ed : editors) {
                                try { ed.triggerSave(); } catch (Throwable ignored) {}
                            }
                        }, 2500);
                    } catch (Throwable ignored) {}

                    try { Thread.sleep(220); } catch (Throwable ignored) {}

                    publish("Stopping Sim components…");
                    try {
                        JournalApp.runOnEdtWithTimeout(() -> {
                            try { if (simOverlay != null) simOverlay.disposeOverlay(); } catch (Throwable ignored) {}
                            try { if (simScheduler != null) simScheduler.stop(); } catch (Throwable ignored) {}
                            try { if (simBrain != null) simBrain.shutdown(); } catch (Throwable ignored) {}
                        }, 2500);
                    } catch (Throwable ignored) {}

                    publish("Stopping services…");
                    try { main.infrastructure.backup.BackupService.get().stop(); } catch (Throwable t) { logWarn("BackupService stop", t); }

                    publish("Finalizing backup…");
                    try { main.infrastructure.backup.BackupService.get().triggerOnExit(); } catch (Throwable t) { logWarn("BackupService triggerOnExit", t); }
                    try { main.infrastructure.backup.BackupService.get().shutdown(); } catch (Throwable t) { logWarn("BackupService shutdown", t); }
                    try { main.infrastructure.io.MacSecurityBookmarkStore.releaseAll(); } catch (Throwable t) { logWarn("Bookmark release", t); }

                    try { Thread.sleep(150); } catch (Throwable ignored) {}
                    return null;
                }
                @Override protected void process(java.util.List<String> chunks) {
                    if (splash != null && !chunks.isEmpty()) {
                        splash.setStatus(chunks.get(chunks.size()-1));
                    }
                }
                @Override protected void done() {
                    if (splash == null) {
                        try { System.exit(0); } catch (Throwable ignored) {}
                        return;
                    }
                    long elapsedMs = (System.nanoTime() - exitSplashShownAt) / 1_000_000L;
                    int remaining = EXIT_MIN_SPLASH_MS - (int) elapsedMs;
                    if (remaining < 0) remaining = 0;
                    javax.swing.Timer t = new javax.swing.Timer(remaining, ev -> {
                        ((javax.swing.Timer) ev.getSource()).stop();
                        splash.fadeOutAndDispose(() -> {
                            try { System.exit(0); } catch (Throwable ignored) {}
                        });
                    });
                    t.setRepeats(false);
                    t.start();
                }
            }.execute();
        } catch (Throwable t) {
            try { System.exit(0); } catch (Throwable ignored) {}
        }
    }

    // Tear down notebook entry panels to avoid lingering watchers/threads on exit
    private void disposeNotebookPanelsSafely() {
        try {
            for (NotebookEntriesPanel panel : notebookPanels.values()) {
                if (panel != null) {
                    try { panel.disposeResources(); } catch (Throwable t) { logWarn("NotebookEntriesPanel dispose", t); }
                }
            }
        } catch (Throwable t) {
            logWarn("NotebookEntriesPanel dispose", t);
        }
    }

    /**
     * Opens the entry manager panel for the given notebook. If it doesn't
     * exist yet, it will be created and added to the CardLayout on-the-fly.
     */
    public void openNotebookEntries(NotebookInfo nb) {
        try {
            // Gate behind lock if notebook is locked
            if (!LockController.get().promptUnlockNotebook(nb.getName())) {
                return;
            }
        } catch (Throwable ignored) {}
        lastActiveNotebook = nb;
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
        String currentCard = resolveVisibleCardName();
        handleSimChatTransition(currentCard, cardName);
        CardLayout cl = cardLayout;
        cl.show(cardPanel, cardName);
    }

    private static void logWarn(String action, Throwable t) {
        if (t == null) return;
        System.err.println("[JournalApp] " + action + " failed (" + t.getClass().getSimpleName() + "): " + t.getMessage());
        t.printStackTrace(System.err);
    }

    private static Color deriveAccentColorSafe() {
        Color fallback = AccentColorUtil.defaultAccent();
        // If the app root isn't initialized yet (e.g., before setup completes), bail fast.
        try { main.infrastructure.io.AppDirectories.getRoot(); } catch (Throwable t) { return fallback; }

        try {
            SettingsStore store = SettingsStore.get();
            int cached = store.getMainMenuAccentRGB();
            if (cached != Integer.MIN_VALUE) return new Color(cached, true);

            Color accent = fallback;
            String bgPath = store.getBackgroundImage();
            Image img = null;
            if (bgPath != null && !bgPath.isEmpty()) {
                try {
                    if (bgPath.startsWith("gen:")) {
                        img = GeneratedWallpapers.render(bgPath, 1920, 1080);
                    } else if (bgPath.startsWith("res:")) {
                        String resPath = bgPath.substring(4);
                        img = ResourceLoader.createImage("Simjot/" + resPath);
                    } else {
                        File f = new File(bgPath);
                        if (f.exists()) {
                            img = new javax.swing.ImageIcon(bgPath).getImage();
                        } else {
                            System.err.println("[JournalApp] Wallpaper not found, using default accent: " + bgPath);
                        }
                    }
                } catch (Throwable t) {
                    logWarn("Accent derive (wallpaper)", t);
                }
            }

            if (img != null) {
                try {
                    Color extracted = AccentColorUtil.extractAccent(img);
                    if (extracted != null) accent = extracted;
                } catch (Throwable t) {
                    logWarn("Accent derive (extract)", t);
                }
            }

            try { store.setMainMenuAccentRGB(accent.getRGB()); store.save(); } catch (Throwable ignored) {}
            return accent;
        } catch (Throwable t) {
            logWarn("Accent derive", t);
            return fallback;
        }
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
        try {
            if (!LockController.get().promptUnlockNotebook(nb.getName())) {
                return;
            }
        } catch (Throwable ignored) {}
        lastActiveNotebook = nb;
        String cardId = "Editor_" + nb.getName() + "_" + System.currentTimeMillis();
        java.io.File targetFolder = nb.getFolder();
        
        // For journal entries, show template selection dialog
        if (nb.getType() == NotebookInfo.Type.JOURNAL) {
            main.ui.features.entries.ModernTemplateSelector dialog = 
                new main.ui.features.entries.ModernTemplateSelector((Frame) SwingUtilities.getWindowAncestor(this), nb);
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
        } else if (nb.getType() == NotebookInfo.Type.NOTETAKING) {
            // Notetaking: open the dedicated editor
            NotebookEditor editor = editorFactory.createInFolder(NotebookEditorType.NOTETAKING, targetFolder);
            openEditors.add(editor);
            cardPanel.add(editor.getMainComponent(), cardId);
            switchCard(cardId);
        } else {
            // Poetry
            NotebookEditor editor = editorFactory.createInFolder(NotebookEditorType.POEM, targetFolder);
            openEditors.add(editor);
            cardPanel.add(editor.getMainComponent(), cardId);
            switchCard(cardId);
        }
    }

    /** Opens an existing file in proper editor based on notebook type */
    public void openExistingEntryEditor(NotebookInfo nb, java.io.File file) {
        try {
            if (!LockController.get().promptUnlockNotebook(nb.getName())) {
                return;
            }
        } catch (Throwable ignored) {}
        lastActiveNotebook = nb;
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

    /**
     * Close any open editors that are bound to the given file.
     * Used when an entry is deleted so autosave doesn't recreate it.
     */
    public void closeEditorsForFile(java.io.File file) {
        if (file == null) return;
        boolean removed = false;
        for (main.ui.features.entries.NotebookEditor editor : new java.util.ArrayList<>(openEditors)) {
            java.io.File current = editor.getCurrentFile();
            if (current != null && current.equals(file)) {
                openEditors.remove(editor);
                removed = true;
                try { cardPanel.remove(editor.getMainComponent()); } catch (Throwable ignored) {}
                cardMap.entrySet().removeIf(e -> e.getValue() == editor.getMainComponent());
            }
        }
        if (removed) {
            try { cardPanel.revalidate(); } catch (Throwable ignored) {}
            try { cardPanel.repaint(); } catch (Throwable ignored) {}
        }
    }

    /**
     * Close any open editors that belong to a folder (used when deleting a notebook).
     */
    public void closeEditorsInFolder(java.io.File folder) {
        if (folder == null) return;
        String root = folder.getAbsolutePath();
        boolean removed = false;
        for (main.ui.features.entries.NotebookEditor editor : new java.util.ArrayList<>(openEditors)) {
            java.io.File current = editor.getCurrentFile();
            if (current == null) continue;
            String path = current.getAbsolutePath();
            if (path.equals(root) || path.startsWith(root + java.io.File.separator)) {
                openEditors.remove(editor);
                removed = true;
                try { cardPanel.remove(editor.getMainComponent()); } catch (Throwable ignored) {}
                cardMap.entrySet().removeIf(e -> e.getValue() == editor.getMainComponent());
            }
        }
        if (removed) {
            try { cardPanel.revalidate(); } catch (Throwable ignored) {}
            try { cardPanel.repaint(); } catch (Throwable ignored) {}
        }
    }

    private void installQuickCaptureHotkey() {
        boolean ok = GlobalHotkeyManager.registerQuickCapture(() -> SwingUtilities.invokeLater(this::showQuickCapture));
        if (!ok) {
            installInAppQuickCaptureHotkey();
        }
    }

    private void installInAppQuickCaptureHotkey() {
        if (quickCaptureDispatcher != null) return;
        quickCaptureDispatcher = e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED && matchesQuickCaptureShortcut(e)) {
                showQuickCapture();
                return true;
            }
            return false;
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(quickCaptureDispatcher);
    }

    private boolean matchesQuickCaptureShortcut(KeyEvent e) {
        boolean isMac = System.getProperty("os.name", "").toLowerCase().contains("mac");
        int mod = isMac ? KeyEvent.META_DOWN_MASK : KeyEvent.CTRL_DOWN_MASK;
        int mask = e.getModifiersEx();
        return e.getKeyCode() == KeyEvent.VK_J
                && (mask & mod) != 0
                && (mask & KeyEvent.SHIFT_DOWN_MASK) != 0;
    }

    public void showQuickCapture() {
        NotebookInfo nb = resolveQuickCaptureNotebook();
        if (nb == null) {
            CustomMessageDialog.display(this, "Quick Capture", "No notebook available. Create one first.", true);
            return;
        }
        QuickCaptureDialog dialog = new QuickCaptureDialog(this, nb, () -> {
            String cardId = "NotebookEntries_" + nb.getName();
            NotebookEntriesPanel panel = notebookPanels.get(cardId);
            if (panel != null) panel.refresh();
        });
        dialog.setVisible(true);
    }

    public void showGlobalSearch() {
        GlobalSearchDialog dialog = new GlobalSearchDialog(this, this);
        dialog.setVisible(true);
    }

    private NotebookInfo resolveQuickCaptureNotebook() {
        if (lastActiveNotebook != null) return lastActiveNotebook;
        try {
            java.util.List<NotebookInfo> all = new NotebookStore().list();
            if (all == null || all.isEmpty()) return null;
            if (all.size() == 1) {
                lastActiveNotebook = all.get(0);
                return lastActiveNotebook;
            }
            String[] names = all.stream().map(NotebookInfo::getName).toArray(String[]::new);
            Object choice = JOptionPane.showInputDialog(
                    this,
                    "Choose a notebook for Quick Capture:",
                    "Quick Capture",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    names,
                    names[0]
            );
            if (choice instanceof String) {
                for (NotebookInfo nb : all) {
                    if (nb.getName().equals(choice)) {
                        lastActiveNotebook = nb;
                        return nb;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private Component getVisibleCardComponent() {
        if (cardPanel == null) return null;
        for (Component c : cardPanel.getComponents()) {
            if (c != null && c.isVisible()) return c;
        }
        return null;
    }

    private String resolveVisibleCardName() {
        Component visible = getVisibleCardComponent();
        if (visible == null) return MAIN_MENU;
        if (visible == simChatPanel) return SIM_CHAT;
        if (visible == mainMenuPanel) return MAIN_MENU;
        if (visible == settingsPanel) return SETTINGS;
        if (visible == galleryPanel) return GALLERY;
        if (visible == quoteGalleryPanel) return QUOTE_GALLERY;
        if (visible instanceof ElegantMoodChartPanel) return MOOD_CHART;
        if (visible instanceof NotebookManagerPanel) return NOTEBOOK_MANAGER;
        for (java.util.Map.Entry<String, NotebookEntriesPanel> e : notebookPanels.entrySet()) {
            if (e.getValue() == visible) return e.getKey();
        }
        for (java.util.Map.Entry<String, JPanel> e : cardMap.entrySet()) {
            if (e.getValue() == visible) return e.getKey();
        }
        return MAIN_MENU;
    }

    private void handleSimChatTransition(String fromCard, String toCard) {
        String from = fromCard == null ? "" : fromCard;
        String to = toCard == null ? "" : toCard;
        boolean enteringSimChat = !SIM_CHAT.equals(from) && SIM_CHAT.equals(to);
        boolean leavingSimChat = SIM_CHAT.equals(from) && !SIM_CHAT.equals(to);
        if (enteringSimChat) {
            maybeCreateLazyCard(SIM_CHAT);
            if (simOverlay != null) {
                try { simOverlay.enterCompanionPanelMode(); } catch (Throwable ignored) {}
            }
            if (simChatPanel != null) {
                try { simChatPanel.onPanelShown(); } catch (Throwable ignored) {}
            }
            if (!from.isBlank()) simChatReturnCard = from;
        } else if (leavingSimChat) {
            if (simChatPanel != null) {
                try { simChatPanel.onPanelHidden(); } catch (Throwable ignored) {}
            }
            if (simOverlay != null) {
                try { simOverlay.exitCompanionPanelMode(); } catch (Throwable ignored) {}
            }
        }
    }

    public void openSimChatPanel() {
        String current = resolveVisibleCardName();
        if (!SIM_CHAT.equals(current)) {
            simChatReturnCard = current;
        }
        switchCard(SIM_CHAT);
    }

    public void closeSimChatPanel() {
        String target = (simChatReturnCard == null || simChatReturnCard.isBlank()) ? MAIN_MENU : simChatReturnCard;
        switchCard(target);
    }

    public boolean isSimGuidanceAvailableForCurrentCard() {
        Component visible = getVisibleCardComponent();
        if (visible instanceof main.ui.features.entries.EntryPanel ep) {
            return ep.isSimGuidanceAvailable();
        }
        return false;
    }

    public boolean requestSimGuidanceForCurrentCard() {
        Component visible = getVisibleCardComponent();
        if (visible instanceof main.ui.features.entries.EntryPanel ep) {
            return ep.requestSimGuidanceFromOverlay();
        }
        return false;
    }

    public boolean isSimTemplateGenerationAvailableForCurrentCard() {
        Component visible = getVisibleCardComponent();
        if (visible instanceof main.ui.features.entries.EntryPanel ep) {
            return ep.isSimTemplateGenerationAvailable();
        }
        return false;
    }

    public boolean requestSimTemplateGenerationForCurrentCard() {
        Component visible = getVisibleCardComponent();
        if (visible instanceof main.ui.features.entries.EntryPanel ep) {
            return ep.requestSimTemplateGenerationFromOverlay();
        }
        return false;
    }

    public boolean startSimTemplateGenerationChat(String notebookName) {
        if (simOverlay == null) return false;
        try {
            simOverlay.startTemplateGenerationChat(notebookName);
            return true;
        } catch (Throwable t) {
            logWarn("startSimTemplateGenerationChat", t);
            return false;
        }
    }

    public boolean addSimTemplateAndOpenManager(String notebookName, String name, String description, String[] questions) {
        try {
            String templateName = (name == null ? "" : name.trim());
            if (templateName.isEmpty()) {
                templateName = "Sim Template " + new java.text.SimpleDateFormat("HHmmss").format(new java.util.Date());
            }
            String templateDescription = (description == null ? "" : description.trim());
            if (templateDescription.isEmpty()) {
                templateDescription = "Generated by Sim from your current entry.";
            }
            java.util.List<String> cleaned = new java.util.ArrayList<>();
            if (questions != null) {
                for (String q : questions) {
                    if (q == null) continue;
                    String s = q.trim();
                    if (s.isEmpty()) continue;
                    if (s.length() > 180) s = s.substring(0, 180).trim();
                    cleaned.add(s);
                    if (cleaned.size() >= 6) break;
                }
            }
            if (cleaned.isEmpty()) {
                cleaned.add("What feels most important for me to process right now?");
                cleaned.add("What pattern do I notice in this experience?");
                cleaned.add("What one supportive action can I take next?");
            }

            NotebookInfo targetNotebook = resolveJournalNotebookByName(notebookName);
            String id = "SIM_" + System.currentTimeMillis();
            JournalTemplateManager.JournalTemplate t = new JournalTemplateManager.JournalTemplate(
                    id, templateName, templateDescription, cleaned.toArray(new String[0]), true
            );
            if (targetNotebook != null) {
                t.setScope(JournalTemplateManager.Scope.NOTEBOOK_CUSTOM);
                t.setNotebookName(targetNotebook.getName());
                JournalTemplateManager.getInstance().addTemplateForNotebook(targetNotebook, t);
            } else {
                t.setScope(JournalTemplateManager.Scope.GLOBAL_CUSTOM);
                JournalTemplateManager.getInstance().addTemplate(t);
            }

            showTemplateManager(targetNotebook);
            return true;
        } catch (Throwable t) {
            logWarn("addSimTemplateAndOpenManager", t);
            return false;
        }
    }

    private NotebookInfo resolveJournalNotebookByName(String notebookName) {
        String wanted = notebookName == null ? "" : notebookName.trim();
        if (wanted.isEmpty()) return null;
        try {
            for (NotebookInfo nb : new NotebookStore().list()) {
                if (nb == null || nb.getType() != NotebookInfo.Type.JOURNAL) continue;
                if (wanted.equalsIgnoreCase(nb.getName())) return nb;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void showTemplateManager(NotebookInfo notebook) {
        try {
            if (notebook != null) {
                TemplateManagerDialog dialog = new TemplateManagerDialog((Frame) this, notebook);
                dialog.setModalityType(java.awt.Dialog.ModalityType.MODELESS);
                dialog.setModal(false);
                dialog.setVisible(true);
                dialog.toFront();
                dialog.requestFocus();
                return;
            }
            TemplateManagerDialog dialog = new TemplateManagerDialog((Frame) this);
            dialog.setModalityType(java.awt.Dialog.ModalityType.MODELESS);
            dialog.setModal(false);
            dialog.setVisible(true);
            dialog.toFront();
            dialog.requestFocus();
        } catch (Throwable t) {
            logWarn("showTemplateManager", t);
        }
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

    public void handleNotebookRename(String oldName, String newName) {
        if (oldName == null || newName == null || oldName.equalsIgnoreCase(newName)) return;

        String oldCardId = "NotebookEntries_" + oldName;
        NotebookEntriesPanel panel = notebookPanels.remove(oldCardId);
        if (panel != null) {
            try { panel.disposeResources(); } catch (Throwable t) { logWarn("NotebookEntriesPanel dispose", t); }
            cardPanel.remove(panel);
        }

        if (lastActiveNotebook != null && lastActiveNotebook.getName().equalsIgnoreCase(oldName)) {
            for (NotebookInfo nb : new NotebookStore().list()) {
                if (nb.getName().equalsIgnoreCase(newName)) {
                    lastActiveNotebook = nb;
                    break;
                }
            }
        }

        cardPanel.revalidate();
        cardPanel.repaint();
    }

    public static void main(String[] args) {
        launchArgs = (args == null) ? new String[0] : args.clone();
        startInTrayOnLaunch = hasArg(args, START_IN_TRAY_ARG);
        try { CrashReporter.install(); } catch (Throwable ignored) {}
        try { AppPerf.applySystemHints(); } catch (Throwable ignored) {}
        SwingUtilities.invokeLater(() -> {
            // Apply LAF before creating the splash to prevent flicker
            try { AeroLookAndFeel.apply(); } catch (Throwable ignored) {}
            // Show Aero-themed splashscreen while bootstrapping
            AeroSplashScreen splash = new AeroSplashScreen();
            splash.setStatus("Starting…");
            splash.setVisible(true);
            final long splashShownAt = System.nanoTime();

            // Run startup pre-warm tasks off the EDT and update splash status
            new javax.swing.SwingWorker<Void, String>() {
                @Override protected Void doInBackground() {
                    publish("Preparing icons…");
                    try { main.ui.components.icons.AppIcon.generateIconImages(); } catch (Throwable ignored) {}
                    try { Thread.sleep(120); } catch (InterruptedException ignored) {}

                    long start = System.nanoTime();

                    // Derive wallpaper accent and configure icon tinting
                    publish("Deriving accent color…");
                    try {
                        java.awt.Color accent = deriveAccentColorSafe();
                        ImageIconRenderer.setAccentTint(accent);
                    } catch (Throwable ignored) {}

                    publish("Warming accented icons…");
                    try {
                        int[] sizes = {16,20,24,32,48};
                        String[] ids = {
                            "settings","write","save","back","list","close","trash","delete_entry","load",
                            "fullscreen","export","rhyme","explorer","refreshsizes","revealselected",
                            "general_settings","appearance_settings","storage_settings","sim_settings","about_settings",
                            "notebook","smile","breathing_widget","pomodoro_widget","sticky_widget"
                        };
                        for (int s : sizes) {
                            for (String id : ids) {
                                try {
                                    String res = ImageIconRenderer.mapIdToResource(id);
                                    if (res != null) {
                                        ImageIconRenderer.get(res, s, false);
                                        ImageIconRenderer.get(res, s, true);
                                    }
                                } catch (Throwable ignored2) {}
                            }
                        }
                    } catch (Throwable ignored) {}

                    try { Thread.sleep(60); } catch (InterruptedException ignored) {}

                    // Warm template manager (built-ins + globals) so template dialogs are instant
                    publish("Loading templates…");
                    try {
                        main.ui.features.entries.JournalTemplateManager.getInstance().getTemplates();
                    } catch (Throwable ignored) {}
                    try { Thread.sleep(60); } catch (InterruptedException ignored) {}

                    // Prime common UI paints (Aero panels/fields) and load custom UI classes
                    publish("Priming UI components…");
                    try {
                        java.awt.image.BufferedImage off = new java.awt.image.BufferedImage(640, 140, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        java.awt.Graphics2D g2 = off.createGraphics();
                        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                        main.ui.components.containers.AeroPanel ap = new main.ui.components.containers.AeroPanel(14);
                        ap.setSize(620, 60);
                        ap.doLayout();
                        ap.paint(g2);
                        main.ui.components.input.AeroTextField tf = new main.ui.components.input.AeroTextField(22);
                        tf.setText("Loading…");
                        tf.setSize(320, 28);
                        tf.paint(g2);
                        g2.dispose();
                        // Touch custom UIs to load classes and static resources
                        new main.ui.components.scrollbar.ModernScrollBarUI();
                        new main.ui.components.combobox.ModernComboBoxUI();
                    } catch (Throwable ignored) {}
                    try { Thread.sleep(60); } catch (InterruptedException ignored) {}

                    // Decode current background image / render generated wallpaper to warm caches
                    publish("Priming backgrounds…");
                    try {
                        String bg = main.core.service.SettingsStore.get().getBackgroundImage();
                        if (bg != null && !bg.isEmpty()) {
                            java.awt.Image img = null;
                            if (bg.startsWith("gen:")) {
                                img = main.ui.features.gallery.GeneratedWallpapers.render(bg, 1600, 900);
                            } else if (bg.startsWith("res:")) {
                                img = main.infrastructure.io.ResourceLoader.createImage("Simjot/" + bg.substring(4));
                            } else {
                                img = new javax.swing.ImageIcon(bg).getImage();
                            }
                            if (img != null) { img.getWidth(null); img.getHeight(null); }
                        }
                    } catch (Throwable ignored) {}
                    try { Thread.sleep(60); } catch (InterruptedException ignored) {}

                    // Load transition classes (fade panels) to avoid first-use class load hiccup
                    publish("Priming transitions…");
                    try { Class.forName("main.ui.animations.transitions.FadeTransitionPanel"); } catch (Throwable ignored) {}
                    try { Thread.sleep(30); } catch (InterruptedException ignored) {}

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
                                        main.infrastructure.io.AppDirectories.folder(main.infrastructure.io.AppDirectories.Type.CUSTOM_FONTS);
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) {}

                    publish("Checking disk space…");
                    try {
                        java.io.File root = null;
                        try { root = main.infrastructure.io.AppDirectories.getRoot(); } catch (Throwable ignored) {}
                        java.io.File base = (root != null) ? root : new java.io.File(System.getProperty("user.home"));
                        long usable = base.getUsableSpace();
                        // warn only; avoid blocking startup
                        if (usable > 0 && usable < 150L * 1024L * 1024L) {
                            main.infrastructure.io.IoLog.warn("disk-space", "Low disk space detected: " + usable + " bytes", null);
                        }
                    } catch (Throwable ignored) {}

                    publish("Loading notebooks…");
                    try {
                        main.core.service.NotebookStore nb = new main.core.service.NotebookStore();
                        nb.reload();
                        try {
                            int count = nb.list() == null ? 0 : nb.list().size();
                            publish("Notebooks: " + count);
                        } catch (Throwable ignored2) {}
                    } catch (Throwable ignored) {}

                    publish("Warming writing tools…");
                    try { main.core.spelling.SpellCheckEngine.get(); } catch (Throwable ignored) {}
                    try { main.core.spelling.IntelligentAutocorrect.get(); } catch (Throwable ignored) {}
                    try { Thread.sleep(80); } catch (Throwable ignored) {}

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

                    // Soft time cap (~4.5s) to intentionally keep splash visible while warming caches
                    try {
                        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
                        if (elapsedMs < 4500) Thread.sleep(80);
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
                    final int minMs = STARTUP_MIN_SPLASH_MS; // minimum splash time
                    javax.swing.Timer wait = new javax.swing.Timer(40, ev -> {
                        java.awt.Window[] wins = java.awt.Window.getWindows();
                        boolean otherVisible = false;
                        boolean setupVisible = false;
                        for (java.awt.Window w : wins) {
                            if (w != splash && w.isShowing()) {
                                otherVisible = true;
                                if (w instanceof main.ui.dialog.setup.SetupWizardDialog) {
                                    setupVisible = true;
                                }
                            }
                        }
                        long elapsedMs = (System.nanoTime() - splashShownAt) / 1_000_000L;
                        int requiredMs = setupVisible ? 900 : minMs;
                        if (otherVisible && elapsedMs >= requiredMs) {
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

    private static boolean hasArg(String[] args, String flag) {
        if (args == null || flag == null) return false;
        for (String a : args) {
            if (flag.equalsIgnoreCase(a)) return true;
        }
        return false;
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
