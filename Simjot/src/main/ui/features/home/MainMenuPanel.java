package main.ui.features.home;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import main.core.service.LastSaveTracker;
import main.core.service.NotebookStore;
import main.core.service.SettingsStore;
import main.infrastructure.io.AppDirectories;
import main.infrastructure.io.ResourceLoader;
import main.infrastructure.monitoring.RamMonitor;
import main.ui.animations.transitions.FadingButton;
import main.ui.app.JournalApp;
import main.ui.components.DragController;
import main.ui.components.buttons.MainMenuButton;
import main.ui.components.icons.ImageIconRenderer;
import main.ui.theme.Theme;
import main.ui.theme.aero.AeroPainters;
import main.ui.theme.aero.AeroTheme;
import main.ui.util.AccentColorUtil;

public class MainMenuPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    // Toggle to quickly hide the Gallery feature without removing code/resources.
    private static final boolean SHOW_GALLERY = false;

    private final JournalApp app;
    private main.ui.features.widgets.WidgetManager widgetManager = new main.ui.features.widgets.WidgetManager();
    private DraggableWidgetPanel widgetPanel;
    private JLayeredPane layeredPane;
    private JPanel pinnedRow;
    // Debounce timer to batch resize handling and reduce repaint storms
    private javax.swing.Timer resizeDebounce;
    private static final int RESIZE_DEBOUNCE_MS = 120;

    public MainMenuPanel(JournalApp app) {
        this.app = app;
        buildUI();
    }

    private JPanel buildPinnedStickiesRow() {
        java.util.Set<String> ids = SettingsStore.get().getPinnedStickyIds();
        if (ids == null || ids.isEmpty()) return null;
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        row.setOpaque(false);
        JLabel title = new JLabel("Pinned Stickies");
        title.setForeground(AeroTheme.TEXT_PRIMARY);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        row.add(title);
        for (String id : ids) {
            String label = readStickyTitle(id);
            if (label == null) continue;
            JButton b = new JButton(label, new ImageIcon(ImageIconRenderer.get("img/icons/sticky_widget.png", 16, false)));
            b.setFocusPainted(false);
            b.setOpaque(false);
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.addActionListener(e -> openSticky(id));
            row.add(b);
        }
        if (row.getComponentCount() <= 1) return null; // nothing valid
        return row;
    }

    private void openSticky(String id) {
        main.ui.features.widgets.Widget w = widgetManager.get("Idea Sticky");
        if (w instanceof main.ui.features.widgets.IdeaStickyWidget sw) {
            sw.openAndFocus(id);
        } else if (w != null) {
            // Fallback: enable generic widget if not the expected type
            w.setEnabled(true);
        }
    }

    private String readStickyTitle(String id){
        try {
            java.io.File dir = new java.io.File(AppDirectories.folder(AppDirectories.Type.SETTINGS), "stickies");
            java.io.File f = new java.io.File(dir, id + ".txt");
            if (!f.exists()) return null;
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f))) {
                String first = r.readLine();
                java.util.List<String> lines = new java.util.ArrayList<>();
                String line;
                if (first != null && !first.startsWith("COLOR ")) lines.add(first);
                while ((line = r.readLine()) != null) lines.add(line);
                // For HTML notes, strip tags fast
                StringBuilder sb = new StringBuilder();
                for (String ln : lines) {
                    String s = ln.replaceAll("<[^>]+>", "").trim();
                    if (!s.isEmpty()) { sb.append(s); break; }
                }
                String t = sb.toString();
                if (t.isEmpty()) t = "Untitled";
                return t.length() > 28 ? t.substring(0, 28) + "…" : t;
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ---------- App Context Indicators (center of status bar) ----------
    private static class AppContextIndicators extends JPanel {
        private final JLabel countsLbl = new JLabel();
        private final JLabel autosaveLbl = new JLabel();
        private final JLabel sizeLbl = new JLabel();

        private final NotebookStore nbStore = new NotebookStore();

        private long lastSizeCompute = 0L;
        private volatile String lastSizeText = "Library: …";

        AppContextIndicators() {
            setOpaque(false);
            setLayout(new FlowLayout(FlowLayout.CENTER, 16, 0));
            Font f = AeroTheme.defaultFont();
            Color c = AeroTheme.TEXT_PRIMARY;

            for (JLabel l : new JLabel[]{countsLbl, autosaveLbl, sizeLbl}) {
                l.setForeground(c);
                l.setFont(f);
                add(l);
            }

            countsLbl.setText("Notebooks: – • Entries: –");
            autosaveLbl.setText("Autosave: – | Last save: –");
            sizeLbl.setText(lastSizeText);

            javax.swing.Timer uiTimer = new javax.swing.Timer(1000, e -> updateFast());
            uiTimer.start();

            // Size recompute on a slower cadence, off-EDT
            javax.swing.Timer sizeTimer = new javax.swing.Timer(30000, e -> computeSizeAsync());
            sizeTimer.setInitialDelay(0);
            sizeTimer.start();
        }

        private void updateFast() {
            // Notebooks and entries every 10s to reduce FS churn
            long now = System.currentTimeMillis();
            if (now / 10000 != (lastCountsMillis / 10000)) {
                updateCounts();
                lastCountsMillis = now;
            }
            updateAutosave();
            sizeLbl.setText(lastSizeText);
        }

        private long lastCountsMillis = 0L;

        private void updateCounts() {
            int notebooks = 0;
            try {
                notebooks = nbStore.list().size();
            } catch (Exception ignore) { }

            int entries = countFilesSafe(AppDirectories.folder(AppDirectories.Type.ENTRIES));
            countsLbl.setText("Notebooks: " + notebooks + " • Entries: " + entries);
        }

        private int countFilesSafe(java.io.File dir) {
            try {
                java.io.File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".txt"));
                return files == null ? 0 : files.length;
            } catch (Exception e) {
                return 0;
            }
        }

        private void updateAutosave() {
            int delayMs = SettingsStore.get().getAutosaveDelayMs();
            String auto;
            if (delayMs <= 0) {
                auto = "Off";
            } else {
                java.text.DecimalFormat df = new java.text.DecimalFormat("0.#");
                auto = df.format(delayMs / 1000.0) + " s";
            }
            long last = LastSaveTracker.getLastSaveMillis();
            String lastTxt = (last == 0L) ? "–" : java.time.LocalTime.now()
                    .withNano(0)
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
            // Prefer showing the actual time of save if desired: formatInstant(last)
            if (last > 0) {
                java.time.LocalDateTime dt = java.time.Instant.ofEpochMilli(last)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDateTime();
                lastTxt = dt.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            }
            autosaveLbl.setText("Autosave: " + auto + " | Last save: " + lastTxt);
        }

        private void computeSizeAsync() {
            final java.io.File root;
            try {
                root = AppDirectories.getRoot();
            } catch (Exception ex) {
                return;
            }
            Thread t = new Thread(() -> {
                long size = 0L;
                try {
                    for (AppDirectories.Type typ : AppDirectories.Type.values()) {
                        size += folderSize(AppDirectories.folder(typ));
                    }
                } catch (Exception ignored) {}
                final String txt = "Library: " + humanSize(size);
                lastSizeCompute = System.currentTimeMillis();
                lastSizeText = txt;
                // Trigger EDT repaint
                SwingUtilities.invokeLater(() -> sizeLbl.setText(lastSizeText));
            }, "lib-size-worker");
            t.setDaemon(true);
            t.start();
        }

        private long folderSize(java.io.File f) {
            if (f == null || !f.exists()) return 0L;
            if (f.isFile()) return f.length();
            long total = 0L;
            java.io.File[] list = f.listFiles();
            if (list != null) {
                for (java.io.File ch : list) {
                    total += folderSize(ch);
                }
            }
            return total;
        }

        private String humanSize(long bytes) {
            final String[] units = {"B", "KB", "MB", "GB", "TB"};
            double v = bytes;
            int u = 0;
            while (v >= 1024 && u < units.length - 1) { v /= 1024; u++; }
            return String.format(java.util.Locale.US, (u <= 1 ? "%.0f %s" : "%.2f %s"), v, units[u]);
        }
    }

    private void buildUI() {
        String bgPath = SettingsStore.get().getBackgroundImage();
        Color accent = AccentColorUtil.defaultAccent();
        JPanel content;
        if (bgPath != null && !bgPath.isEmpty()) {
            if (bgPath.startsWith("gen:")) {
                // Generated vector wallpaper (gradient variations)
                String id = bgPath;
                // Render a large backing image; BackgroundPanel will scale efficiently and cache
                Image img = main.ui.features.gallery.GeneratedWallpapers.render(id, 2560, 1440);
                try { accent = AccentColorUtil.extractAccent(img); } catch (Throwable ignored) {}
                BackgroundPanel bg = new BackgroundPanel(img);
                bg.setOpacityOverride(1.0f);
                content = bg;
            } else if (bgPath.startsWith("res:")) {
                // Built-in resource (class-path) – strip prefix
                String resPath = bgPath.substring(4);
                Image img = ResourceLoader.createImage("Simjot/" + resPath);
                if (img != null) {
                    try { accent = AccentColorUtil.extractAccent(img); } catch (Throwable ignored) {}
                    BackgroundPanel bg = new BackgroundPanel(img);
                    bg.setOpacityOverride(1.0f);
                    content = bg;
                } else {
                    content = new JPanel();
                }
            } else {
                // User-selected file path
                try { accent = AccentColorUtil.extractAccent(new ImageIcon(bgPath).getImage()); } catch (Throwable ignored) {}
                BackgroundPanel bg = new BackgroundPanel(bgPath);
                bg.setOpacityOverride(1.0f);
                content = bg;
            }
            content.setBackground(Color.BLACK);
        } else {
            // Blank / default – just use a plain white panel
            content = new JPanel();
            content.setBackground(Color.WHITE);
        }
        // Apply the accent tint globally for PNG icons so all menu icons recolor immediately
        try { main.ui.components.icons.ImageIconRenderer.setAccentTint(accent); } catch (Throwable ignored) {}
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        // -------- Widgets registration (centralized) ---------
        widgetManager.initializeDefault(app);
        // Apply persisted per-widget enabled states (breathing widget works independently of main menu animations)
        try {
            java.util.Map<String, main.ui.features.widgets.Widget> all = widgetManager.getAll();
            for (java.util.Map.Entry<String, main.ui.features.widgets.Widget> e : all.entrySet()) {
                boolean shouldBeEnabled = SettingsStore.get().isWidgetEnabled(e.getKey());
                e.getValue().setEnabled(shouldBeEnabled);
            }
        } catch (Exception ignored) { }

        // Add header and clock.
        HeaderPanel header = new HeaderPanel(accent);
        header.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(Box.createRigidArea(new Dimension(0, 10)));
        content.add(header);

        // Time info right below header quote
        TimeInfoPanel timePanelTop = new TimeInfoPanel();
        timePanelTop.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(Box.createRigidArea(new Dimension(0, 6)));
        content.add(timePanelTop);

        // ---- Clock and Today Calendar side-by-side ----
        AnalogClockPanel clockPanel = new AnalogClockPanel();
        clockPanel.setPreferredSize(new Dimension(200, 200));
        clockPanel.setMaximumSize(new Dimension(220, 220));

        TodayCalendarPanel calendarPanel = new TodayCalendarPanel(accent);
        calendarPanel.setPreferredSize(new Dimension(150, 150));
        calendarPanel.setMaximumSize(new Dimension(170, 170));

        JPanel clockRow = new JPanel();
        clockRow.setOpaque(false);
        clockRow.setLayout(new BoxLayout(clockRow, BoxLayout.X_AXIS));
        clockRow.add(Box.createHorizontalGlue());
        clockRow.add(clockPanel);
        clockRow.add(Box.createRigidArea(new Dimension(18, 0))); // spacing between clock and calendar
        clockRow.add(calendarPanel);
        clockRow.add(Box.createHorizontalGlue());

        clockRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(Box.createRigidArea(new Dimension(0, 8)));
        content.add(clockRow);

        // ----- Pinned Stickies row -----
        pinnedRow = buildPinnedStickiesRow();
        if (pinnedRow != null) {
            pinnedRow.setAlignmentX(Component.CENTER_ALIGNMENT);
            content.add(Box.createRigidArea(new Dimension(0, 8)));
            content.add(pinnedRow);
        }

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
        notebooksButton.setForeground(AeroTheme.TEXT_PRIMARY);
        notebooksButton.setToolTipText("Create and manage your journals and notebooks");
        buttonPanel.add(notebooksButton);
        buttonPanel.add(Box.createRigidArea(new Dimension(0, 12)));

        // Planning section has been removed
        // ---------- ARTS section (hidden unless there are visible items) ----------

        // Canvas button temporarily removed from the menu (functionality kept for later transformation)
        FadingButton galleryButton = createMenuButtonWithIcon("Gallery", JournalApp.GALLERY, "image");
        galleryButton.setForeground(Color.WHITE);
        galleryButton.setFont(galleryButton.getFont().deriveFont(Font.BOLD, 20f));
        galleryButton.setBorder(BorderFactory.createEmptyBorder(12, 24, 12, 24));
        galleryButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        galleryButton.addActionListener(e -> app.switchCard(JournalApp.GALLERY));

        List<FadingButton> artsBtns = new ArrayList<>();
        if (SHOW_GALLERY) {
            artsBtns.add(galleryButton);
        }
        if (!artsBtns.isEmpty()) {
            JLabel artsHeader = new JLabel("Arts & Gallery");
            artsHeader.setAlignmentX(Component.CENTER_ALIGNMENT);
            artsHeader.setForeground(Color.WHITE);
            artsHeader.setFont(artsHeader.getFont().deriveFont(Font.BOLD, 22f));
            buttonPanel.add(artsHeader);
            buttonPanel.add(Box.createRigidArea(new Dimension(0, 6)));
            for (FadingButton b : artsBtns) {
                b.setAlpha(1f);
                b.setAlignmentX(Component.CENTER_ALIGNMENT);
                buttonPanel.add(b);
                buttonPanel.add(Box.createRigidArea(new Dimension(0, 6)));
            }
            buttonPanel.add(Box.createRigidArea(new Dimension(0, 12)));
        }

        // ---------- INSIGHTS section ----------
        JLabel insightsHeader = new JLabel("Insights");
        insightsHeader.setAlignmentX(Component.CENTER_ALIGNMENT);
        insightsHeader.setForeground(Color.WHITE);
        insightsHeader.setFont(insightsHeader.getFont().deriveFont(Font.BOLD, 22f));

        FadingButton moodChartButton = createMenuButtonWithIcon("Mood Chart", JournalApp.MOOD_CHART, "smile");
        moodChartButton.setToolTipText("View your mood trends and emotional patterns over time");

        buttonPanel.add(insightsHeader);
        buttonPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        moodChartButton.setAlpha(1f);
        moodChartButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        buttonPanel.add(moodChartButton);
        buttonPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        buttonPanel.add(Box.createRigidArea(new Dimension(0, 12)));

        FadingButton settingsButton = createMenuButtonWithIcon("Settings", JournalApp.SETTINGS, "wrench");
        settingsButton.setToolTipText("Customize appearance, storage, security, and more");
        settingsButton.setAlpha(1f);
        settingsButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        buttonPanel.add(settingsButton);
        buttonPanel.add(Box.createRigidArea(new Dimension(0, 6)));

        // ---- Exit button (bottom) ----
        buttonPanel.add(Box.createRigidArea(new Dimension(0, 16)));
        FadingButton exitButton = new MainMenuButton("Exit", "close");
        exitButton.setForeground(AeroTheme.TEXT_PRIMARY);
        exitButton.setToolTipText("Save all work and close Simjot");
        exitButton.setFont(exitButton.getFont().deriveFont(Font.BOLD, 20f));
        exitButton.setBorder(BorderFactory.createEmptyBorder(12, 24, 12, 24));
        exitButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        exitButton.addActionListener(e -> {
            // Use the owning app instance to perform a graceful shutdown with exiting splash
            if (app != null) {
                app.exitGracefully();
            }
        });
        buttonPanel.add(exitButton);

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

        // Widgets panel is disabled for now; keep the layeredPane for future use
        widgetPanel = null;
        // Use a layered pane to allow dragging over other components (even though widget panel is omitted)
        layeredPane = new JLayeredPane() {
            @Override
            public Dimension getPreferredSize() {
                return content.getPreferredSize();
            }

            @Override
            public void doLayout() {
                // Always stretch the main content to the full size immediately,
                // so first paint is centered and not left-aligned.
                if (content.getParent() == this) {
                    content.setBounds(0, 0, getWidth(), getHeight());
                }
            }
        };
        layeredPane.setLayout(null); // Absolute positioning for draggable widget; content is stretched in doLayout.

        // Add the main content panel (bounds will be set by doLayout before first paint)
        layeredPane.add(content, Integer.valueOf(JLayeredPane.DEFAULT_LAYER));

        // Widget panel intentionally not added

        // Prime the debounce so first layout settles quickly
        scheduleResizeClamp();

        // South Status Bar (Aero-themed)
        JPanel southPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();
                Rectangle r = new Rectangle(0, 0, w, h);

                if (Theme.isPlainWhite()) {
                    g2.setColor(Color.WHITE);
                    g2.fillRect(0, 0, w, h);
                    g2.setColor(new Color(200, 200, 200));
                    g2.drawLine(0, 0, w, 0);
                } else {
                    AeroPainters.paintOuterGlow(g2, new Rectangle(2, 0, w - 4, h), 12,
                            new Color(0, 120, 215, 120), 6, 70);

                    AeroPainters.paintVerticalGradient(g2, r,
                            new Color(250, 250, 250, 220), new Color(226, 226, 226, 220), 12);
                    AeroPainters.paintGlassOverlay(g2, r, 12);
                    AeroPainters.paintInnerStroke(g2, r, 12, new Color(180, 180, 180, 180));

                    g2.setColor(new Color(0, 120, 215));
                    g2.fillRect(0, 0, w, 1);
                }

                g2.dispose();
            }
        };
        southPanel.setOpaque(false);
        southPanel.setPreferredSize(new Dimension(10, 56));
        southPanel.setBorder(BorderFactory.createEmptyBorder(3, 8, 6, 8));

        // Left: version info
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        left.setOpaque(false);
        JLabel versionLabel = new JLabel("Version 1.0 - By Ilgaz, with love");
        versionLabel.setForeground(AeroTheme.TEXT_PRIMARY);
        versionLabel.setFont(AeroTheme.defaultFont());
        left.add(versionLabel);
        southPanel.add(left, BorderLayout.WEST);

        // Center: App context indicators (entries/notebooks, autosave, size)
        JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 4));
        center.setOpaque(false);
        center.add(new AppContextIndicators());
        southPanel.add(center, BorderLayout.CENTER);

        // Right: RAM usage
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        right.setOpaque(false);
        RamMonitor ramPanel = new RamMonitor();
        right.add(ramPanel);
        southPanel.add(right, BorderLayout.EAST);

        add(layeredPane, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);

        // Quick Settings: install middle-click overlay with animated orbs (encapsulated in new classes)
        main.ui.features.quicksettings.QuickSettingsController.install(layeredPane, new main.ui.features.quicksettings.QuickSettingsController.HostApi() {
            @Override
            public java.awt.Window getWindow() {
                return SwingUtilities.getWindowAncestor(MainMenuPanel.this);
            }

            @Override
            public void setWidgetsPanelVisible(boolean visible) {
                if (widgetPanel != null) {
                    widgetPanel.setVisible(visible);
                    layeredPane.revalidate();
                    layeredPane.repaint();
                }
                // Persist
                SettingsStore.get().setWidgetPanelVisible(visible);
                SettingsStore.get().save();
            }

            @Override
            public boolean isWidgetsPanelVisible() {
                return SettingsStore.get().isWidgetPanelVisible();
            }

            @Override
            public java.util.List<String> getWidgetNames() {
                java.util.List<String> names = new java.util.ArrayList<>();
                for (java.util.Map.Entry<String, main.ui.features.widgets.Widget> e : widgetManager.getAll().entrySet()) {
                    names.add(e.getKey());
                }
                return names;
            }

            @Override
            public boolean isWidgetEnabled(String name) {
                // Prefer persisted state; fall back to runtime if absent
                boolean persisted = SettingsStore.get().isWidgetEnabled(name);
                main.ui.features.widgets.Widget w = widgetManager.get(name);
                if (w != null) {
                    // Keep widget runtime state in sync with persisted
                    if (w.isEnabled() != persisted) {
                        w.setEnabled(persisted);
                    }
                }
                return persisted;
            }

            @Override
            public void setWidgetEnabled(String name, boolean enabled) {
                main.ui.features.widgets.Widget w = widgetManager.get(name);
                if (w != null) {
                    w.setEnabled(enabled);
                }
                // Persist
                SettingsStore.get().setWidgetEnabled(name, enabled);
                SettingsStore.get().save();
            }
        });
    }

    // Debounced resize handler to clamp the draggable widget after resizing settles
    private void scheduleResizeClamp() {
        if (resizeDebounce == null) {
            resizeDebounce = new javax.swing.Timer(RESIZE_DEBOUNCE_MS, e -> {
                // one-shot
                if (resizeDebounce != null) {
                    resizeDebounce.stop();
                    resizeDebounce = null;
                }
                clampWidgetToBounds();
                revalidate();
                repaint();
            });
            resizeDebounce.setRepeats(false);
        }
        resizeDebounce.restart();
    }

    // Keeps the draggable widget inside the layered pane bounds
    private void clampWidgetToBounds() {
        if (layeredPane == null || widgetPanel == null) return;
        Dimension paneSize = layeredPane.getSize();
        if (paneSize.width <= 0 || paneSize.height <= 0) return;
        Point location = widgetPanel.getLocation();
        Dimension widgetSize = widgetPanel.getSize();

        int maxX = Math.max(0, paneSize.width - widgetSize.width);
        int maxY = Math.max(0, paneSize.height - widgetSize.height);
        int newX = Math.min(Math.max(0, location.x), maxX);
        int newY = Math.min(Math.max(0, location.y), maxY);

        if (newX != location.x || newY != location.y) {
            widgetPanel.setBounds(newX, newY, widgetSize.width, widgetSize.height);
        }
    }

    /**
     * Draggable and collapsible widget panel that can be minimized to just an
     * icon or expanded to show the full widget list.
     */
    private class DraggableWidgetPanel extends JPanel {

        private boolean isExpanded = true;
        private boolean suppressAutoBounds = false; // prevents updateLayout from resizing during animations
        private Timer resizeTimer;
        private JPanel expandedContent;
        private JButton toggleButton;
        private DragController drag;

        public DraggableWidgetPanel() {
            setOpaque(false);
            setLayout(new BorderLayout());
            // Initialize drag controller BEFORE building components so initial updateLayout() can attach handles
            drag = new DragController(this)
                    .setConstrainToParentBounds(true)
                    .setCursorOnDrag(true)
                    .setDragThreshold(3);
            initializeComponents();
        }

        private void setupTitleBarDrag(Component component) {
            // Use DragController for dragging by specific handles in expanded state
            if (drag != null && component != null) {
                drag.addHandle(component);
            }
        }

        // createDragHandler removed (handled by DragController)

        private void initializeComponents() {
            // Create toggle button (always visible)
            toggleButton = new JButton("") { // Vector-rendered icon (pushpin or chevron)
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    int w = getWidth(), h = getHeight();
                    if (Theme.isPlainWhite()) {
                        g2.setColor(Color.WHITE);
                        g2.fillRoundRect(0, 0, w, h, 20, 20);
                        g2.setColor(new Color(170, 170, 170));
                        g2.setStroke(new BasicStroke(1f));
                        g2.drawRoundRect(0, 0, w - 1, h - 1, 20, 20);
                    } else {
                        Paint gp = new LinearGradientPaint(0, 0, 0, h,
                                new float[]{0f, 0.5f, 1f},
                                new Color[]{new Color(252,252,252,210), new Color(235,235,235,210), new Color(220,220,220,210)});
                        g2.setPaint(gp);
                        g2.fillRoundRect(0, 0, w, h, 20, 20);
                        g2.setPaint(new GradientPaint(0, 0, new Color(255,255,255,170), 0, h/2f, new Color(255,255,255,0)));
                        g2.fillRoundRect(1, 1, w-2, h/2, 18, 18);
                        g2.setColor(new Color(170, 170, 170));
                        g2.setStroke(new BasicStroke(1f));
                        g2.drawRoundRect(0, 0, w - 1, h - 1, 20, 20);
                    }

                    // ----- Vector icon -----
                    int cx = w/2, cy = h/2;
                    boolean expanded = DraggableWidgetPanel.this.isExpanded;

                    if (expanded) {
                        // Windows 7 style chevron-left: glossy, soft shadow
                        int aw = 14, ah = 18;
                        int x = cx - aw/2, y = cy - ah/2;

                        // Shadow
                        Graphics2D s = (Graphics2D) g2.create();
                        s.setComposite(AlphaComposite.SrcOver.derive(0.20f));
                        s.setPaint(new RadialGradientPaint(new Point(cx+4, cy+6), 14f,
                                new float[]{0f,1f}, new Color[]{new Color(0,0,0,90), new Color(0,0,0,0)}));
                        s.fillOval(x-2, y+ah-6, aw+8, 10);
                        s.dispose();

                        // Body gradient (light top to darker bottom)
                        Paint body = new LinearGradientPaint(x, y, x, y+ah,
                                new float[]{0f, 0.5f, 1f},
                                new Color[]{new Color(255,255,255), new Color(230,230,230), new Color(200,200,200)});
                        g2.setPaint(body);
                        java.awt.geom.GeneralPath chevron = new java.awt.geom.GeneralPath();
                        chevron.moveTo(x+aw*0.65, y+2);
                        chevron.lineTo(x+aw*0.20, y+ah/2f);
                        chevron.lineTo(x+aw*0.65, y+ah-2);
                        chevron.quadTo(x+aw*0.55, y+ah-1, x+aw*0.52, y+ah-4);
                        chevron.lineTo(x+aw*0.12, y+ah/2f);
                        chevron.lineTo(x+aw*0.52, y+4);
                        chevron.quadTo(x+aw*0.56, y+2, x+aw*0.65, y+2);
                        chevron.closePath();
                        g2.fill(chevron);

                        // Outline and top gloss stroke
                        g2.setColor(new Color(140,140,140));
                        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2.draw(chevron);
                        g2.setColor(new Color(255,255,255,150));
                        g2.setStroke(new BasicStroke(1.0f));
                        g2.drawLine((int)(x+aw*0.52), y+4, (int)(x+aw*0.22), y+ah/2);
                    } else {
                        // Windows 7 style pushpin: blue glossy head with metallic pin
                        int r = 16; // head radius
                        int hx = cx, hy = cy-2;

                        // Head shadow
                        Graphics2D s = (Graphics2D) g2.create();
                        s.setComposite(AlphaComposite.SrcOver.derive(0.22f));
                        s.setPaint(new RadialGradientPaint(new Point(hx, hy+r/2+6), r,
                                new float[]{0f,1f}, new Color[]{new Color(0,0,0,90), new Color(0,0,0,0)}));
                        s.fillOval(hx-r/2-6, hy+r/2, r+12, 10);
                        s.dispose();

                        // Blue glass head
                        Paint head = new RadialGradientPaint(new Point(hx-3, hy-3), r/1.6f,
                                new float[]{0f, 0.6f, 1f},
                                new Color[]{new Color(120,180,240), new Color(70,130,210), new Color(40,90,170)});
                        g2.setPaint(head);
                        g2.fillOval(hx - r/2, hy - r/2, r, r);
                        // Head gloss
                        g2.setPaint(new RadialGradientPaint(new Point(hx-5, hy-6), r/2f,
                                new float[]{0f,1f}, new Color[]{new Color(255,255,255,200), new Color(255,255,255,0)}));
                        g2.fillOval(hx - r/2 + 2, hy - r/2 + 2, r-6, r-8);
                        // Head ring
                        g2.setColor(new Color(255,255,255,140));
                        g2.drawOval(hx - r/2, hy - r/2, r, r);
                        g2.setColor(new Color(70,70,70,150));
                        g2.drawOval(hx - r/2, hy - r/2, r, r);

                        // Metallic pin
                        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        Paint pin = new LinearGradientPaint(hx, hy+2, hx, hy+16,
                                new float[]{0f,1f}, new Color[]{new Color(230,230,230), new Color(150,150,150)});
                        g2.setPaint(pin);
                        g2.drawLine(hx, hy + r/2 - 2, hx, hy + r/2 + 10);
                    }

                    g2.dispose();
                }
            };
            toggleButton.setPreferredSize(new Dimension(50, 50));
            toggleButton.setOpaque(false);
            toggleButton.setContentAreaFilled(false);
            toggleButton.setBorderPainted(false);
            toggleButton.setFocusPainted(false);
            toggleButton.setFont(new Font("SansSerif", Font.BOLD, 18));
            toggleButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            toggleButton.addActionListener(e -> {
                // Prevent accidental toggle when this click follows a drag
                if (drag != null && drag.shouldSuppressClickAndReset()) {
                    return;
                }
                toggleExpansion();
            });

            // Create expanded content panel
            expandedContent = new JPanel();
            expandedContent.setOpaque(false);
            expandedContent.setLayout(new BoxLayout(expandedContent, BoxLayout.Y_AXIS));
            expandedContent.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

            // Add title
            JLabel title = new JLabel("Widgets");
            title.setForeground(Color.BLACK);
            title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
            title.setAlignmentX(Component.CENTER_ALIGNMENT);
            expandedContent.add(title);
            expandedContent.add(Box.createRigidArea(new Dimension(0, 6)));

            // Add widget buttons
            for (java.util.Map.Entry<String, main.ui.features.widgets.Widget> entry : widgetManager.getAll().entrySet()) {
                final String widgetKey = entry.getKey();
                final main.ui.features.widgets.Widget widget = entry.getValue();
                final String displayName = (widget != null && widget.getName() != null && !widget.getName().trim().isEmpty())
                        ? widget.getName().trim()
                        : (widgetKey == null || widgetKey.trim().isEmpty() ? "Widget" : widgetKey.trim());
                String iconId = (widget != null && widget.getIconId() != null && !widget.getIconId().trim().isEmpty())
                        ? widget.getIconId().trim()
                        : "lines";
                FadingButton btn = new MainMenuButton(displayName, iconId);
                btn.setText(displayName);
                btn.setToolTipText(displayName);
                // Keep widget labels visible (no hover-fade)
                btn.putClientProperty("disableHoverFade", Boolean.TRUE);
                // Hide sliding icons for widgets to avoid overlapping graphics
                btn.putClientProperty("hideIcon", Boolean.TRUE);
                btn.setForeground(AeroTheme.TEXT_PRIMARY);
                btn.setFont(btn.getFont().deriveFont(Font.PLAIN, 16f));
                btn.setAlpha(1f);
                btn.setAlignmentX(Component.CENTER_ALIGNMENT);
                btn.addActionListener(e -> {
                    if ("Breathing".equalsIgnoreCase(displayName)) {
                        // First show our custom confirmation dialog
                        boolean startBreathing = main.ui.dialog.confirmation.CustomConfirmDialog.confirm(
                                MainMenuPanel.this,
                                "Breathing Exercise",
                                "Would you like to start a guided breathing exercise?\n\nThis will display a calming animation overlay."
                        );

                        if (startBreathing) {
                            // Show configuration dialog for breathing widget
                            main.ui.dialog.config.BreathingConfigDialog dialog
                                    = new main.ui.dialog.config.BreathingConfigDialog((JFrame) SwingUtilities.getWindowAncestor(MainMenuPanel.this));
                            dialog.setVisible(true);

                            if (dialog.isConfirmed()) {
                                // Open breathing exercise in its own window
                                main.ui.dialog.utils.BreathingExerciseWindow exerciseWindow
                                        = new main.ui.dialog.utils.BreathingExerciseWindow((JFrame) SwingUtilities.getWindowAncestor(MainMenuPanel.this));
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
                        boolean enable = widget == null || !widget.isEnabled();
                        if (widget != null) widget.setEnabled(enable);
                        SettingsStore.get().setWidgetEnabled(widgetKey, enable);
                        SettingsStore.get().save();
                    }
                });
                expandedContent.add(btn);
                expandedContent.add(Box.createRigidArea(new Dimension(0, 4)));
            }

            updateLayout();
        }

        // Old ad-hoc drag behavior removed in favor of DragController

        private void toggleExpansion() {
            // Prevent accidental toggle if a drag just occurred
            if (drag != null && drag.shouldSuppressClickAndReset()) {
                return;
            }
            // If an animation is already running, stop it
            if (resizeTimer != null && resizeTimer.isRunning()) {
                resizeTimer.stop();
            }

            // Animate size change between collapsed (50x50) and expanded preferred size (e.g., 160x220)
            final Dimension start = getSize();
            final Dimension end;
            final boolean collapsing = isExpanded; // current state -> target is opposite

            if (collapsing) {
                // Collapse: animate to small, then switch content
                end = new Dimension(50, 50);
            } else {
                // Expand: first switch content to expanded to measure preferred size, then animate
                isExpanded = true;
                suppressAutoBounds = true; // don't let layout snap size while measuring preferred size
                updateLayout();
                suppressAutoBounds = false;
                end = getPreferredSize();
            }

            final long duration = 220; // ms
            final long startTime = System.currentTimeMillis();
            final Point pos = getLocation();

            resizeTimer = new Timer(15, null);
            resizeTimer.addActionListener(e -> {
                long now = System.currentTimeMillis();
                float t = Math.min(1f, (now - startTime) / (float) duration);
                // ease in-out
                float tt = (float) (0.5 - 0.5 * Math.cos(Math.PI * t));
                int w = (int) (start.width + (end.width - start.width) * tt);
                int h = (int) (start.height + (end.height - start.height) * tt);
                setBounds(pos.x, pos.y, w, h);
                revalidate();
                repaint();
                if (t >= 1f) {
                    resizeTimer.stop();
                    if (collapsing) {
                        // After collapse, switch content to collapsed view
                        isExpanded = false;
                        updateLayout();
                        setBounds(pos.x, pos.y, end.width, end.height);
                    }
                }
            });
            resizeTimer.start();

            // If we started a collapse, keep expanded view until animation finishes
            if (!collapsing) {
                // Already set to expanded and updated layout above
            }
        }

        private void updateLayout() {
            removeAll();

            if (isExpanded) {
                // Show full widget panel with semi-transparent background
                JPanel backgroundPanel = new JPanel(new BorderLayout()) {
                    @Override
                    protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                        int w = getWidth(), h = getHeight();
                        // Silvery background gradient
                        Paint bg = new LinearGradientPaint(0, 0, 0, h,
                                new float[]{0f, 0.5f, 1f},
                                new Color[]{new Color(252,252,252,200), new Color(236,236,236,200), new Color(222,222,222,200)});
                        g2.setPaint(bg);
                        g2.fillRoundRect(0, 0, w, h, 15, 15);

                        // Glass overlay
                        g2.setPaint(new GradientPaint(0, 0, new Color(255,255,255,170), 0, h/2f, new Color(255,255,255,0)));
                        g2.fillRoundRect(1, 1, w-2, h/2, 14, 14);

                        // Title bar strip with subtle separation
                        Paint tb = new LinearGradientPaint(0, 0, 0, 50,
                                new float[]{0f,1f}, new Color[]{new Color(255,255,255,160), new Color(230,230,230,140)});
                        g2.setPaint(tb);
                        g2.fillRoundRect(1, 1, w-2, 50, 14, 14);
                        g2.setColor(new Color(180,180,180,180));
                        g2.drawLine(8, 50, w-8, 50);

                        // Border
                        g2.setColor(new Color(170, 170, 170));
                        g2.setStroke(new BasicStroke(1f));
                        g2.drawRoundRect(0, 0, w - 1, h - 1, 15, 15);

                        g2.dispose();
                    }
                };
                backgroundPanel.setOpaque(false);

                // Create title bar with toggle button and visual grip drag area
                JPanel titleBar = new JPanel(new BorderLayout());
                titleBar.setOpaque(false);
                titleBar.setPreferredSize(new Dimension(0, 50));

                // Visual grip: subtle dotted handle that hints drag (with hover cue)
                JComponent grip = new JComponent() {
                    private boolean hover = false;
                    {
                        addMouseListener(new MouseAdapter() {
                            @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                            @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                        });
                    }
                    @Override protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        int w = getWidth();
                        int h = getHeight();
                        int cols = 6, rows = 3; // 6x3 dots
                        int spacingX = w / (cols + 1);
                        int spacingY = h / (rows + 1);
                        Color dot = new Color(0,0,0, hover ? 150 : 90);
                        g2.setColor(dot);
                        int radius = hover ? 5 : 4;
                        for (int r = 1; r <= rows; r++) {
                            for (int c = 1; c <= cols; c++) {
                                int cx = c * spacingX;
                                int cy = r * spacingY + 6; // slight offset down from the top
                                g2.fillOval(cx - radius/2, cy - radius/2, radius, radius);
                            }
                        }
                        g2.dispose();
                    }
                };
                grip.setOpaque(false);
                grip.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                titleBar.add(grip, BorderLayout.CENTER);
                titleBar.add(toggleButton, BorderLayout.EAST);

                backgroundPanel.add(titleBar, BorderLayout.NORTH);
                backgroundPanel.add(expandedContent, BorderLayout.CENTER);
                add(backgroundPanel, BorderLayout.CENTER);
                toggleButton.setText("<"); // use '<' to visually hint minimizing/collapsing
                toggleButton.setToolTipText("Collapse widgets");

                // Configure drag handles for expanded state
                if (drag != null) {
                    drag.clearHandles();
                    setupTitleBarDrag(titleBar);
                    setupTitleBarDrag(grip);
                }
            } else {
                // Show only the toggle button
                add(toggleButton, BorderLayout.CENTER);
                toggleButton.setText("⚙"); // Widget icon when collapsed
                toggleButton.setToolTipText("Expand widgets");
                // In collapsed state, drag via the toggle button
                if (drag != null) {
                    drag.clearHandles();
                    drag.addHandle(toggleButton);
                }
            }

            // Update bounds when layout changes (skip if animating or suppressed)
            if (!suppressAutoBounds && (resizeTimer == null || !resizeTimer.isRunning())) {
                Dimension newSize = getPreferredSize();
                setBounds(getX(), getY(), newSize.width, newSize.height);
            }
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            if (isExpanded) {
                int w = 170;
                int baseH = 80; // header + padding
                int contentH = (expandedContent != null) ? expandedContent.getPreferredSize().height : 0;
                int h = baseH + contentH;
                return new Dimension(w, Math.max(220, h));
            } else {
                return new Dimension(50, 50);
            }
        }
    }

    public void updateWidgetPanelVisibility() {
        // Widget panel disabled
    }

    // Force widget panel to the top-most layer and visible; useful after the UI is shown
    public void ensureWidgetPanelOnTopAndVisible() {
        // Widget panel disabled
    }

    private FadingButton createMenuButtonWithIcon(String text, String cardName, String icon) {
        FadingButton button = new MainMenuButton(text, icon);
        // Main menu button uses theme primary text color for better contrast
        button.setForeground(AeroTheme.TEXT_PRIMARY);
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
            java.time.LocalTime now = java.time.LocalTime.now(java.time.ZoneId.systemDefault());
            timeLbl.setText("It's currently " + now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
            int seconds = now.toSecondOfDay();
            double pct = seconds / 86400.0 * 100.0;
            pctLbl.setText(String.format("%.1f%% of the day has passed.", pct));
        }
    }
}
