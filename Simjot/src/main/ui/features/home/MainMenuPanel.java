/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import main.core.AppInfo;
import main.core.analytics.MoodAnalyticsEngine;
import main.core.analytics.MoodAnalyticsEngine.AnalyticsResult;
import main.core.service.LastSaveTracker;
import main.core.service.NotebookStore;
import main.core.service.SettingsStore;
import main.infrastructure.backup.NotebookInfo;
import main.infrastructure.io.AppDirectories;
import main.infrastructure.io.ResourceLoader;
import main.infrastructure.monitoring.RamMonitor;
import main.ui.app.JournalApp;
import main.ui.components.DragController;
import main.ui.components.buttons.IconMenuButton;
import main.ui.components.buttons.MainMenuButton;
import main.ui.components.calendars.CircularCalendar;
import main.ui.components.calendars.ClannadCalendar;
import main.ui.components.calendars.DotMatrixCalendar;
import main.ui.components.calendars.GlassCalendar;
import main.ui.components.calendars.MinimalistCalendar;
import main.ui.components.calendars.NeonCalendar;
import main.ui.components.calendars.PostItCalendar;
import main.ui.components.calendars.RetroCalendar;
import main.ui.components.calendars.StampCalendar;
import main.ui.components.calendars.TornPageCalendar;
import main.ui.components.calendars.VerticalCalendar;
import main.ui.components.clocks.MinimalistClock;
import main.ui.components.clocks.NeonClock;
import main.ui.components.clocks.OrbitalClock;
import main.ui.components.clocks.PolarClock;
import main.ui.components.clocks.SegmentClock;
import main.ui.components.clocks.SunburstClock;
import main.ui.components.clocks.SwissRailwayClock;
import main.ui.components.clocks.WordClock;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.icons.ImageIconRenderer;
import main.ui.theme.Theme;
import main.ui.theme.aero.AeroPainters;
import main.ui.theme.aero.AeroTheme;
import main.ui.util.AccentColorUtil;

/**
 * Main menu panel shown on app launch, and accessible from the header.
 * Sectioned layout with buttons for primary features.
 * Contains header, clock, calendar, quick access buttons.
 * Manages the lifecycle of child components.
 */

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
            String res = ImageIconRenderer.mapIdToResource("sticky_widget");
            JButton b = new JButton(label, res != null ? ImageIconRenderer.icon(res, 16, false) : null);
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

    //  App Context Indicators (center of status bar)
    private static class AppContextIndicators extends JPanel {
        private final JournalApp app;
        private final JLabel countsLbl = new JLabel();
        private final JLabel autosaveLbl = new JLabel();
        private final JLabel sizeLbl = new JLabel();
        private final JButton moodPulseChip = new JButton("Mood avg – • Trend –");

        private final NotebookStore nbStore = new NotebookStore();

        private volatile String lastSizeText = "…";
        private List<Double> lastSparklineValues = List.of();

        private long lastCountsMillis = 0L;
        private long lastMoodMillis = 0L;

        private JPopupMenu moodPopup;
        private MoodSparkline moodSparklineView;
        private Timer moodPopupHideTimer;
        private boolean moodChipHovered = false;
        private boolean moodPopupHovered = false;

        AppContextIndicators(JournalApp app) {
            this.app = app;
            setOpaque(false);
            setLayout(new FlowLayout(FlowLayout.CENTER, 8, 0));
            Font bradleyHand = new Font("Bradley Hand", Font.PLAIN, 13);
            Color c = AeroTheme.TEXT_PRIMARY;

            JPanel nbIcon = new JPanel() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    String res = main.ui.components.icons.ImageIconRenderer.mapIdToResource("notebook");
                    if (res != null) {
                        main.ui.components.icons.ImageIconRenderer.draw(g2, res, 0, 0, 18, this, false);
                    }
                    g2.dispose();
                }
            };
            nbIcon.setPreferredSize(new Dimension(18, 18));
            nbIcon.setOpaque(false);
            add(nbIcon);

            for (JLabel l : new JLabel[]{countsLbl, autosaveLbl, sizeLbl}) {
                l.setForeground(c);
                l.setFont(bradleyHand);
                add(l);
            }

            configureChip(moodPulseChip, "smile");

            moodPulseChip.addActionListener(e -> {
                ElegantMoodChartPanel.requestRangeSelection(1);
                app.switchCard(JournalApp.MOOD_CHART);
            });
            moodPulseChip.addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) {
                    moodChipHovered = true;
                    showMoodSparklinePopup();
                }

                @Override public void mouseExited(MouseEvent e) {
                    moodChipHovered = false;
                    hideMoodPopupSoon();
                }
            });

            countsLbl.setText("– notebooks  •  – entries");
            autosaveLbl.setText(" |  Autosave: –  •  Last: –");
            sizeLbl.setText(" |  Size: " + lastSizeText);

            add(Box.createHorizontalStrut(6));
            add(moodPulseChip);

            updateCounts();
            updateMoodPulse();
            updateAutosave();

            javax.swing.Timer uiTimer = new javax.swing.Timer(1000, e -> updateFast());
            uiTimer.start();

            javax.swing.Timer sizeTimer = new javax.swing.Timer(30000, e -> computeSizeAsync());
            sizeTimer.setInitialDelay(0);
            sizeTimer.start();
        }

        private void configureChip(JButton chip, String iconId) {
            chip.setFocusPainted(false);
            chip.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(190, 198, 210)),
                    BorderFactory.createEmptyBorder(3, 8, 3, 8)));
            chip.setBackground(new Color(255, 255, 255, 235));
            chip.setOpaque(true);
            chip.setForeground(AeroTheme.TEXT_PRIMARY);
            chip.setFont(new Font("Bradley Hand", Font.PLAIN, 12));
            chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            String res = ImageIconRenderer.mapIdToResource(iconId);
            if (res != null) {
                chip.setIcon(ImageIconRenderer.icon(res, 14, false));
                chip.setIconTextGap(5);
            }
        }

        private void updateFast() {
            long now = System.currentTimeMillis();
            if (now / 10000 != (lastCountsMillis / 10000)) {
                updateCounts();
                lastCountsMillis = now;
            }
            if (now / 10000 != (lastMoodMillis / 10000)) {
                updateMoodPulse();
                lastMoodMillis = now;
            }

            updateAutosave();
            sizeLbl.setText(" |  Size: " + lastSizeText);
        }

        private void updateCounts() {
            int notebooks = 0;
            try {
                notebooks = nbStore.list().size();
            } catch (Exception ignore) { }

            int entries = countEntriesSafe();
            countsLbl.setText(notebooks + " notebooks  •  " + entries + " entries");
        }

        private int countEntriesSafe() {
            try {
                int total = 0;
                List<NotebookInfo> notebooks = nbStore.list();
                for (NotebookInfo nb : notebooks) {
                    total += countEntryFilesInFolder(nb == null ? null : nb.getFolder());
                }
                if (total > 0) {
                    return total;
                }
            } catch (Exception ignore) {
                // Fall back to legacy folders below.
            }

            int legacyTotal = 0;
            legacyTotal += countEntryFilesInFolder(AppDirectories.folder(AppDirectories.Type.ENTRIES));
            legacyTotal += countEntryFilesInFolder(AppDirectories.folder(AppDirectories.Type.POEMS));
            return legacyTotal;
        }

        private int countEntryFilesInFolder(File dir) {
            if (dir == null || !dir.exists() || !dir.isDirectory()) return 0;
            int total = 0;
            try {
                File[] files = dir.listFiles();
                if (files == null) return 0;
                for (File f : files) {
                    if (f == null || !f.isFile()) continue;
                    if (isEntryFile(f.getName())) total++;
                }
            } catch (Exception e) {
                return 0;
            }
            return total;
        }

        private void updateMoodPulse() {
            AnalyticsResult scoped = MoodAnalyticsEngine.get().analyze(30, 7);
            if (scoped == null || scoped.dates == null || scoped.dates.isEmpty()) {
                moodPulseChip.setText("Mood avg – • Trend –");
            } else {
                int avg = (int) Math.round(scoped.overallAverage);
                String arrow = trendArrow(scoped.smoothedAverages);
                moodPulseChip.setText("Mood avg " + avg + " • Trend " + arrow);
            }

            AnalyticsResult spark = MoodAnalyticsEngine.get().analyze(30, 3);
            lastSparklineValues = spark == null ? List.of() : spark.dailyAverages;
        }

        private void showMoodSparklinePopup() {
            if (lastSparklineValues == null || lastSparklineValues.isEmpty()) {
                return;
            }
            ensureMoodPopup();
            cancelMoodPopupHide();
            if (moodSparklineView != null) {
                moodSparklineView.setValues(lastSparklineValues);
            }
            if (!moodPopup.isVisible()) {
                moodPopup.show(moodPulseChip, 0, moodPulseChip.getHeight() + 4);
            }
        }

        private void hideMoodPopupSoon() {
            cancelMoodPopupHide();
            moodPopupHideTimer = new Timer(360, e -> {
                if (!moodChipHovered && !moodPopupHovered && moodPopup != null) {
                    moodPopup.setVisible(false);
                }
            });
            moodPopupHideTimer.setRepeats(false);
            moodPopupHideTimer.start();
        }

        private void ensureMoodPopup() {
            if (moodPopup != null) return;
            moodPopup = new JPopupMenu();
            moodPopup.setBorder(BorderFactory.createLineBorder(new Color(184, 194, 208)));

            JPanel wrap = new JPanel(new BorderLayout(0, 6));
            wrap.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            wrap.setBackground(Color.WHITE);
            wrap.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    moodPopupHovered = true;
                    cancelMoodPopupHide();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    moodPopupHovered = false;
                    hideMoodPopupSoon();
                }
            });

            JLabel title = new JLabel("30-day mood pulse");
            title.setFont(new Font("Bradley Hand", Font.PLAIN, 12));
            title.setForeground(AeroTheme.TEXT_PRIMARY);
            wrap.add(title, BorderLayout.NORTH);

            moodSparklineView = new MoodSparkline(lastSparklineValues);
            wrap.add(moodSparklineView, BorderLayout.CENTER);
            moodPopup.add(wrap);
        }

        private void cancelMoodPopupHide() {
            if (moodPopupHideTimer != null) {
                moodPopupHideTimer.stop();
                moodPopupHideTimer = null;
            }
        }

        private String trendArrow(List<Double> values) {
            if (values == null || values.isEmpty()) return "→";
            Double last = null;
            Double prev = null;
            for (int i = values.size() - 1; i >= 0; i--) {
                Double v = values.get(i);
                if (v == null) continue;
                if (last == null) {
                    last = v;
                } else {
                    prev = v;
                    break;
                }
            }
            if (last == null || prev == null) return "→";
            double delta = last - prev;
            if (delta > 1.0d) return "↑";
            if (delta < -1.0d) return "↓";
            return "→";
        }

        private boolean isEntryFile(String name) {
            if (name == null) return false;
            String s = name.toLowerCase(Locale.ROOT);
            return s.endsWith(".txt")
                    || s.endsWith(".md")
                    || s.endsWith(".rtf")
                    || s.endsWith(".note")
                    || s.endsWith(".poem")
                    || s.endsWith(".ntk");
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
            if (last > 0) {
                java.time.LocalDateTime dt = java.time.Instant.ofEpochMilli(last)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDateTime();
                lastTxt = dt.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            }
            autosaveLbl.setText(" |  Autosave: " + auto + "  •  Last: " + lastTxt);
        }

        private void computeSizeAsync() {
            Thread t = new Thread(() -> {
                long size = 0L;
                try {
                    for (AppDirectories.Type typ : AppDirectories.Type.values()) {
                        size += folderSize(AppDirectories.folder(typ));
                    }
                } catch (Exception ignored) {}
                lastSizeText = humanSize(size);
                SwingUtilities.invokeLater(() -> sizeLbl.setText(" |  Size: " + lastSizeText));
            }, "lib-size-worker");
            t.setDaemon(true);
            t.start();
        }

        private long folderSize(File f) {
            if (f == null || !f.exists()) return 0L;
            if (f.isFile()) return f.length();
            long total = 0L;
            File[] list = f.listFiles();
            if (list != null) {
                for (File ch : list) {
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

        private static final class MoodSparkline extends JComponent {
            private List<Double> values;

            private MoodSparkline(List<Double> values) {
                this.values = values == null ? List.of() : values;
                setPreferredSize(new Dimension(210, 48));
                setOpaque(false);
            }

            private void setValues(List<Double> values) {
                this.values = values == null ? List.of() : values;
                repaint();
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                int l = 6, r = 6, t = 6, b = 6;
                int cw = Math.max(1, w - l - r);
                int ch = Math.max(1, h - t - b);

                g2.setColor(new Color(236, 242, 249));
                g2.fillRoundRect(0, 0, w - 1, h - 1, 8, 8);
                g2.setColor(new Color(201, 211, 224));
                g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);

                List<Point> pts = new ArrayList<>();
                int n = values.size();
                for (int i = 0; i < n; i++) {
                    Double v = values.get(i);
                    if (v == null) continue;
                    int x = l + (int) Math.round((i / (double) Math.max(1, n - 1)) * cw);
                    int y = t + ch - (int) Math.round((Math.max(0, Math.min(100, v)) / 100.0) * ch);
                    pts.add(new Point(x, y));
                }
                g2.setColor(new Color(145, 156, 172, 120));
                g2.drawLine(l, t + ch / 2, l + cw, t + ch / 2);

                if (pts.size() >= 2) {
                    g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.setColor(new Color(66, 138, 223));
                    Point prev = pts.get(0);
                    for (int i = 1; i < pts.size(); i++) {
                        Point p = pts.get(i);
                        g2.drawLine(prev.x, prev.y, p.x, p.y);
                        prev = p;
                    }
                    Point last = pts.get(pts.size() - 1);
                    g2.fillOval(last.x - 2, last.y - 2, 4, 4);
                } else if (pts.size() == 1) {
                    Point p = pts.get(0);
                    g2.setColor(new Color(66, 138, 223));
                    g2.fillOval(p.x - 2, p.y - 2, 4, 4);
                }
                g2.dispose();
            }
        }
    }

    private void buildUI() {
        String bgPath = SettingsStore.get().getBackgroundImage();
        final boolean hasWallpaper = bgPath != null && !bgPath.isEmpty();
        final Color wallpaperFallback = new Color(246, 249, 252);
        Color accent = AccentColorUtil.defaultAccent();
        JPanel content;
        if (hasWallpaper) {
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
            content.setBackground(wallpaperFallback);
        } else {
            // Blank / default – just use a plain white panel
            content = new JPanel();
            content.setBackground(Color.WHITE);
        }
        // Keep icon cache in sync after accent changes (tinting disabled for current icon set).
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
        HeaderPanel header = new HeaderPanel(app, accent);
        header.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(Box.createRigidArea(new Dimension(0, 10)));
        content.add(header);

        // ---- Clock and Today Calendar side-by-side ----
        JPanel clockPanel = createClockForStyle(SettingsStore.get().getClockStyle(), accent);
        clockPanel.setPreferredSize(new Dimension(200, 200));
        clockPanel.setMaximumSize(new Dimension(220, 220));

        JPanel calendarPanel = createCalendarForStyle(SettingsStore.get().getCalendarStyle(), accent);
        calendarPanel.setPreferredSize(new Dimension(220, 240));
        calendarPanel.setMaximumSize(new Dimension(260, 280));

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

        // Glass Dock Bar - Frutiger Aero style
        main.ui.components.dock.GlassDockBar dockBar = new main.ui.components.dock.GlassDockBar();
        dockBar.addItem("Write", "fountain_pen", () -> app.switchCard(JournalApp.NOTEBOOK_MANAGER));
        if (SHOW_GALLERY) {
            dockBar.addItem("Gallery", "image", () -> app.switchCard(JournalApp.GALLERY));
        }
        dockBar.addItem("Mood", "smile", () -> app.switchCard(JournalApp.MOOD_CHART));
        dockBar.addItem("Settings", "wrench", () -> app.switchCard(JournalApp.SETTINGS));
        dockBar.addItem("Exit", "saveandexit", () -> {
            if (app != null) {
                app.exitGracefully();
            }
        });
        dockBar.setAlignmentX(Component.CENTER_ALIGNMENT);

        content.add(Box.createRigidArea(new Dimension(0, 40)));
        content.add(dockBar);
        content.add(Box.createRigidArea(new Dimension(0, 30))); // Lift from bottom toolbar

        header.startAnimation();
        // Buttons are visible immediately

        // Container setup
        setLayout(new BorderLayout());
        setBackground(hasWallpaper ? wallpaperFallback : Color.WHITE);

        // Widgets panel is disabled for now; keep the layeredPane for future use
        // widgetPanel = new JPanel();
        widgetPanel = null;
        // Use a layered pane to allow dragging over other components (even though widget panel is omitted)
        final JPanel contentRef = content;
        layeredPane = new JLayeredPane() {
            @Override
            public Dimension getPreferredSize() {
                return contentRef.getPreferredSize();
            }
            
            @Override
            public Dimension getMinimumSize() {
                return new Dimension(1, 1);
            }

            @Override
            public void doLayout() {
                super.doLayout();
                syncLayeredContentBounds(contentRef);
            }
            
            @Override
            public void setBounds(int x, int y, int width, int height) {
                super.setBounds(x, y, width, height);
                syncLayeredContentBounds(contentRef);
            }
        };
        layeredPane.setLayout(null); // Absolute positioning for draggable widget; content is stretched in doLayout.
        layeredPane.setOpaque(true);
        layeredPane.setBackground(wallpaperFallback);
        layeredPane.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                syncLayeredContentBounds(contentRef);
            }

            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                syncLayeredContentBounds(contentRef);
            }
        });

        // Add the main content panel (bounds will be set by doLayout before first paint)
        layeredPane.add(content, Integer.valueOf(JLayeredPane.DEFAULT_LAYER));
        SwingUtilities.invokeLater(() -> syncLayeredContentBounds(contentRef));

        // Widget panel intentionally not added

        // Prime the debounce so first layout settles quickly
        scheduleResizeClamp();

        // South status bar as a frosted glass strip.
        FrostedGlassPanel southPanel = new FrostedGlassPanel(new BorderLayout(), 14) {
            @Override
            protected float getOpacityScale() {
                return Theme.isPlainWhite() ? 0.45f : 0.42f;
            }
        };
        southPanel.setOpaque(false);
        southPanel.setPreferredSize(new Dimension(10, 56));
        southPanel.setBorder(BorderFactory.createEmptyBorder(3, 8, 6, 8));

        // Left: version info with about icon
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        left.setOpaque(false);
        Font bradleyHand = new Font("Bradley Hand", Font.PLAIN, 13);
        
        // About icon
        JPanel aboutIcon = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                String res = main.ui.components.icons.ImageIconRenderer.mapIdToResource("about_settings");
                if (res != null) {
                    main.ui.components.icons.ImageIconRenderer.draw(g2, res, 0, 0, 18, this, false);
                }
                g2.dispose();
            }
        };
        aboutIcon.setPreferredSize(new Dimension(18, 18));
        aboutIcon.setOpaque(false);
        left.add(aboutIcon);
        
        JLabel versionLabel = new JLabel(AppInfo.versionString());
        versionLabel.setForeground(AeroTheme.TEXT_PRIMARY);
        versionLabel.setFont(bradleyHand);
        left.add(versionLabel);
        southPanel.add(left, BorderLayout.WEST);

        // Center: App context indicators (entries/notebooks, autosave, size)
        JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 4));
        center.setOpaque(false);
        center.add(new AppContextIndicators(app));
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

    private void syncLayeredContentBounds(JPanel contentRef) {
        if (layeredPane == null || contentRef == null || contentRef.getParent() != layeredPane) {
            return;
        }
        int w = Math.max(0, layeredPane.getWidth());
        int h = Math.max(0, layeredPane.getHeight());
        if (contentRef.getX() != 0 || contentRef.getY() != 0 || contentRef.getWidth() != w || contentRef.getHeight() != h) {
            contentRef.setBounds(0, 0, w, h);
        }
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

    private static JPanel createClockForStyle(String style, Color accent) {
        return switch (style) {
            case "Minimalist" -> new MinimalistClock();
            case "Neon" -> new NeonClock(accent);
            case "Swiss" -> new SwissRailwayClock();
            case "Sunburst" -> new SunburstClock(accent);
            case "Segment" -> new SegmentClock(accent);
            case "Polar" -> new PolarClock(accent);
            case "Orbital" -> new OrbitalClock(accent);
            case "Word" -> new WordClock(accent);
            case "Binary", "Flip", "Radar" -> new AnalogClockPanel();
            default -> new AnalogClockPanel();
        };
    }

    private static JPanel createCalendarForStyle(String style, Color accent) {
        return switch (style) {
            case "Minimalist" -> new MinimalistCalendar(accent);
            case "TornPage" -> new TornPageCalendar(accent);
            case "Circular" -> new CircularCalendar(accent);
            case "PostIt" -> new PostItCalendar(accent);
            case "Glass" -> new GlassCalendar(accent);
            case "Vertical" -> new VerticalCalendar(accent);
            case "DotMatrix" -> new DotMatrixCalendar(accent);
            case "Stamp" -> new StampCalendar(accent);
            case "Retro" -> new RetroCalendar(accent);
            case "Neon" -> new NeonCalendar(accent);
            case "Clannad" -> new ClannadCalendar(accent);
            default -> new TodayCalendarPanel(accent);
        };
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
                MainMenuButton btn = new MainMenuButton(displayName, iconId);
                btn.setText(displayName);
                btn.setToolTipText(displayName);
                btn.setForeground(AeroTheme.TEXT_PRIMARY);
                btn.setFont(btn.getFont().deriveFont(Font.PLAIN, 16f));
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

    private IconMenuButton createIconButton(String text, String cardName, String icon) {
        IconMenuButton button = new IconMenuButton(text, icon).setAeroGlowEnabled(true);
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.addActionListener(e -> app.switchCard(cardName));
        return button;
    }

    // Time info panel removed; keep layout focused on the header and main widgets.
}
