/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.entries;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.StyledDocument;
import javax.swing.text.rtf.RTFEditorKit;

import main.core.security.EncryptionManager;
import main.core.security.crypto.EncryptedMetadata;
import main.core.service.SettingsStore;
import main.infrastructure.backup.EntryHistoryManager;
import main.infrastructure.backup.NotebookInfo;
import main.infrastructure.ffi.NativeAccess;
import main.infrastructure.io.AppDirectories;
import main.infrastructure.io.FileIO;
import main.infrastructure.io.MacSecurityBookmarkStore;
import main.infrastructure.io.MoodFile;
import main.infrastructure.io.ResourceLoader;
import main.ui.app.JournalApp;
import main.ui.components.buttons.ToolbarIconButton;
import main.ui.components.buttons.ToolbarMenuIconButton;
import main.ui.components.combobox.ModernComboBoxUI;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.datepicker.ModernDatePicker;
import main.ui.components.input.AeroTextField;
import main.ui.components.scrollbar.ModernScrollBarUI;
import main.ui.components.spinner.ModernSpinner;
import main.ui.dialog.confirmation.CustomConfirmDialog;
import main.ui.theme.aero.AeroTheme;

public class NotebookEntriesPanel extends JPanel {
    private static final DateTimeFormatter ENTRY_TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter ENTRY_DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy");
    private static final ThreadLocal<SimpleDateFormat> ENTRY_LIST_DATE_FORMAT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("dd/MM/yy HH:mm"));
    private static final int LAZY_FILE_THRESHOLD = 50;
    private static final int LAZY_INITIAL_ROWS = 36;
    private static final int LAZY_PAGE_ROWS = 30;
    private static final int LAZY_PREFETCH_ROWS = 10;
    private static final int LAZY_APPEND_STEP_ROWS = 8;
    private static final int LAZY_APPEND_TICK_MS = 20;
    private static final int META_PUBLISH_BATCH_SIZE = 6;
    private static final int FAST_ANIM_TICK_MS = 24;
    private static final String CENTER_CARD_LIST = "list";
    private static final String CENTER_CARD_CALENDAR = "calendar";
    private static final String VIEW_MODE_PREF_KEY_PREFIX = "entries.view.mode.";

    private enum EntryViewMode {
        COMFORT,
        CALENDAR
    }

    private final JournalApp app;
    private final NotebookInfo nb;
    private final DefaultListModel<EntryRow> model = new DefaultListModel<>();
    private final JList<EntryRow> list = new JList<>(model);
    private final JTextField searchField = new AeroTextField(20);
    private final JComboBox<String> sortBox = new JComboBox<>(new String[]{
            "Date (Newest)",
            "Date (Oldest)",
            "Name (A-Z)",
            "Name (Z-A)",
            "Word Count (High→Low)",
            "Word Count (Low→High)"});
    private EntryViewMode viewMode = EntryViewMode.COMFORT;
    private ToolbarIconButton comfortViewBtn;
    private ToolbarIconButton calendarViewBtn;

    private final java.util.Map<File,Integer> wordCounts = new java.util.HashMap<>();
    private final java.util.Map<File,String> titles = new java.util.HashMap<>();
    private final java.util.Map<File,Integer> moodValues = new java.util.HashMap<>();
    private final java.util.Map<File, LocalDate> entryDates = new java.util.HashMap<>();
    private final java.util.Map<File, Long> entryTimestamps = new java.util.HashMap<>();
    private final java.util.Map<File, MetaSnapshot> metaCache = new java.util.HashMap<>();
    private final java.util.Map<File, PreviewSnapshot> previewCache = new java.util.HashMap<>();
    private final java.util.Map<File, int[]> moodTrendCache = new java.util.HashMap<>();
    private final java.util.Map<File, Boolean> encryptedFlags = new java.util.HashMap<>();
    private final java.util.Map<File, Integer> rowIndexByFile = new java.util.HashMap<>();
    private List<File> allFiles = new ArrayList<>();
    private List<File> lastOrderedFiles = java.util.Collections.emptyList();
    private List<EntryRow> fullRows = java.util.Collections.emptyList();
    private int loadedRows = 0;
    private int lazyAppendTargetRows = -1;
    private String lazySignature = "";
    private SwingWorker<Void, PreviewSnapshot> previewLoader;
    private volatile boolean disposed = false;
    private CardLayout centerCardLayout;
    private JPanel centerCardPanel;
    private CalendarEntriesPanel calendarPanel;

    // Debounced search and background metadata loader
    private final javax.swing.Timer searchDebounce = new javax.swing.Timer(100, e -> update());
    private SwingWorker<Void, FileMeta> metaLoader;

    // Debounced list rebuild to avoid frequent model churn on metadata updates
    private final javax.swing.Timer listUpdateDebounce = new javax.swing.Timer(120, e -> applyFilterSort());
    // Track which files have computed metadata and which are enqueued
    private final java.util.Set<File> metaComputed = new java.util.HashSet<>();
    private final java.util.Set<File> metaQueued = new java.util.HashSet<>();
    private javax.swing.JScrollPane listScroll;
    private final javax.swing.Timer lazyAppendTimer = new javax.swing.Timer(LAZY_APPEND_TICK_MS, e -> {
        try {
            if (disposed || fullRows == null || fullRows.isEmpty()) {
                ((javax.swing.Timer) e.getSource()).stop();
                lazyAppendTargetRows = -1;
                return;
            }
            if (loadedRows >= fullRows.size()) {
                ((javax.swing.Timer) e.getSource()).stop();
                lazyAppendTargetRows = loadedRows;
                return;
            }
            int boundedTarget = Math.max(loadedRows, Math.min(fullRows.size(), lazyAppendTargetRows));
            if (boundedTarget <= loadedRows) {
                ((javax.swing.Timer) e.getSource()).stop();
                return;
            }
            int next = Math.min(boundedTarget, loadedRows + LAZY_APPEND_STEP_ROWS);
            appendRowsTo(next);
            if (loadedRows >= boundedTarget || loadedRows >= fullRows.size()) {
                ((javax.swing.Timer) e.getSource()).stop();
            }
        } catch (Throwable ignored) {}
    });

    // Debounced folder watch refresh to coalesce rapid file system events
    private final javax.swing.Timer watchDebounce = new javax.swing.Timer(100, e -> refresh());

    // Lightweight animation when items are reordered (fade highlight)
    private final java.util.Map<File, Float> reorderAnimProgress = new java.util.HashMap<>();
    
    // Delete animation state: file -> progress (0=start, 1=gone)
    private final java.util.Map<File, Float> deleteAnimProgress = new java.util.HashMap<>();
    private File pendingDeleteFile = null;
    private final javax.swing.Timer deleteAnimTimer = new javax.swing.Timer(FAST_ANIM_TICK_MS, e -> {
        try {
            if (pendingDeleteFile == null || !deleteAnimProgress.containsKey(pendingDeleteFile)) {
                ((javax.swing.Timer) e.getSource()).stop();
                return;
            }
            Float fv = deleteAnimProgress.get(pendingDeleteFile);
            float v = (fv == null ? 0f : fv.floatValue());
            v += 0.08f; // animation speed
            if (v >= 1f) {
                // Animation complete - perform actual delete
                ((javax.swing.Timer) e.getSource()).stop();
                File toDelete = pendingDeleteFile;
                deleteAnimProgress.remove(toDelete);
                pendingDeleteFile = null;
                performActualDelete(toDelete);
            } else {
                deleteAnimProgress.put(pendingDeleteFile, v);
                repaintFileRow(pendingDeleteFile, 24);
            }
        } catch (Throwable ignored) {}
    });
    
    private final javax.swing.Timer reorderAnimTimer = new javax.swing.Timer(FAST_ANIM_TICK_MS, e -> {
        try {
            java.util.List<File> toRemove = new java.util.ArrayList<>();
            java.util.List<File> toRepaint = new java.util.ArrayList<>();
            for (java.util.Map.Entry<File, Float> en : reorderAnimProgress.entrySet()) {
                Float fv = en.getValue();
                float v = (fv == null ? 0f : fv.floatValue());
                v *= 0.88f; // exponential decay
                if (v < 0.06f) {
                    toRemove.add(en.getKey());
                } else {
                    en.setValue(v);
                    toRepaint.add(en.getKey());
                }
            }
            for (File f : toRemove) reorderAnimProgress.remove(f);
            if (reorderAnimProgress.isEmpty()) {
                ((javax.swing.Timer) e.getSource()).stop();
            }
            repaintFileRows(toRepaint, 18);
        } catch (Throwable ignored) {}
    });

    private int hoverIndex = -1;
    private File hoverFile = null;
    private final float[] selectionSweepPhase = new float[]{0f};
    private final javax.swing.Timer selectionSweepTimer = new javax.swing.Timer(33, e -> {
        try {
            selectionSweepPhase[0] += 0.018f;
            if (selectionSweepPhase[0] > 1f) selectionSweepPhase[0] -= 1f;
            repaintRow(hoverIndex, 12);
        } catch (Throwable ignored) {}
    });
    
    private final float[] dashedBorderPhase = new float[]{0f};
    private int dashedAnimatedIndex = -1;
    private final javax.swing.Timer dashedBorderTimer = new javax.swing.Timer(50, e -> {
        try {
            dashedBorderPhase[0] += 0.8f; // Speed of conveyor belt movement
            if (dashedBorderPhase[0] > 10f) dashedBorderPhase[0] -= 10f; // Reset after full cycle
            if (dashedAnimatedIndex < 0) dashedAnimatedIndex = list.getSelectedIndex();
            repaintRow(dashedAnimatedIndex, 8);
        } catch (Throwable ignored) {}
    });

    // Folder watch
    private WatchService watchService;
    private Thread watchThread;
    private volatile boolean watchRunning;
    private static final int PREVIEW_MAX_CHARS = 260;

    private static class FileMeta {
        final File file; final int wc; final String title; final int mood;
        FileMeta(File f, int wc, String title, int mood){ this.file=f; this.wc=wc; this.title=title; this.mood=mood; }
    }

    private static class MetaSnapshot {
        final long lastModified;
        final long length;
        final int wordCount;
        final String title;
        final int mood;
        MetaSnapshot(long lastModified, long length, int wordCount, String title, int mood) {
            this.lastModified = lastModified;
            this.length = length;
            this.wordCount = wordCount;
            this.title = title;
            this.mood = mood;
        }
        boolean isFresh(File f) {
            return f != null && f.lastModified() == lastModified && f.length() == length;
        }
    }

    private static class PreviewSnapshot {
        final File file;
        final long lastModified;
        final long length;
        final String title;
        final String snippet;
        PreviewSnapshot(File file, long lastModified, long length, String title, String snippet) {
            this.file = file;
            this.lastModified = lastModified;
            this.length = length;
            this.title = title == null ? "" : title;
            this.snippet = snippet == null ? "" : snippet;
        }
        boolean isFresh(File f) {
            return f != null && f.lastModified() == lastModified && f.length() == length;
        }
    }

    private static class TitleMood {
        final String title;
        final int mood;
        TitleMood(String title, int mood) {
            this.title = title == null ? "" : title;
            this.mood = mood;
        }
    }

    // Renderer for entry cards inside a notebook
    private static class EntryCardRenderer extends JPanel implements ListCellRenderer<EntryRow> {
        private final JLabel title = new JLabel();
        private final JLabel sizeLabel = new JLabel();
        private final JLabel wordsLabel = new JLabel();
        private final JLabel createdLabel = new JLabel();
        private final JLabel editedLabel = new JLabel();
        private final JLabel snippet = new JLabel();
        private final JLabel editedContextLabel = new JLabel();
        private final JPanel statsPanel = new JPanel();
        private final Color cardBg = new Color(252, 253, 255);
        private final Color cardBorder = new Color(190, 200, 214);
        private final Color metaColor = new Color(105, 110, 120);
        private final Color accent = new Color(88, 133, 255);
        private final Color selectedBg = new Color(236, 244, 255);
        private final Color selectedBorder = new Color(110, 160, 255);
        private static final int HEIGHT_COMFORT = 126;
        private final javax.swing.border.EmptyBorder comfortPadding = new javax.swing.border.EmptyBorder(8, 18, 8, 12);
        private boolean selected;
        private float reorderGlow = 0f;
        private float deleteProgress = 0f; // 0=normal, 1=fully gone
        private float selectionSweepPhase = 0f;
        private float dashedBorderPhase = 0f; // Current animated phase for dashed border
        private boolean hovered = false;
        private boolean encrypted = false;
        private final DateDividerRenderer divider = new DateDividerRenderer();
        private static final int SNIPPET_CACHE_MAX = 768;
        private static final int TIME_CACHE_MAX = 1024;
        private final Map<File, SnippetHtmlCache> snippetHtmlCache =
                new LinkedHashMap<>(128, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<File, SnippetHtmlCache> eldest) {
                        return size() > SNIPPET_CACHE_MAX;
                    }
                };
        private final Map<File, TimeLabelCache> timeLabelCache =
                new LinkedHashMap<>(128, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<File, TimeLabelCache> eldest) {
                        return size() > TIME_CACHE_MAX;
                    }
                };

        // Background image cache (scaled+blurred per size)
        private static BufferedImage ENTRY_BG_BASE;
        private static final Map<String, BufferedImage> BG_CACHE = new HashMap<>();
        private static BufferedImage loadBase(){
            if (ENTRY_BG_BASE != null) return ENTRY_BG_BASE;
            Image img = ResourceLoader.createImage("img/background/entrybg.png");
            if (img == null) return null;
            int w = Math.max(1, new ImageIcon(img).getIconWidth());
            int h = Math.max(1, new ImageIcon(img).getIconHeight());
            BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = bi.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.drawImage(img, 0, 0, null);
            g2.dispose();
            ENTRY_BG_BASE = bi;
            return ENTRY_BG_BASE;
        }
        private static BufferedImage getBg(int w, int h){
            String key = w+"x"+h;
            BufferedImage cached = BG_CACHE.get(key);
            if (cached != null) return cached;
            BufferedImage base = loadBase();
            if (base == null || w <= 0 || h <= 0) return null;
            // Scale to cover (center-crop)
            double sx = w / (double) base.getWidth();
            double sy = h / (double) base.getHeight();
            double scale = Math.max(sx, sy);
            int sw = (int) Math.ceil(base.getWidth() * scale);
            int sh = (int) Math.ceil(base.getHeight() * scale);
            BufferedImage scaled = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB);
            Graphics2D gs = scaled.createGraphics();
            gs.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            gs.drawImage(base, 0, 0, sw, sh, null);
            gs.dispose();
            // Crop center to requested size
            int x = (sw - w) / 2; if (x < 0) x = 0;
            int y = (sh - h) / 2; if (y < 0) y = 0;
            BufferedImage cropped = scaled.getSubimage(x, y, Math.min(w, sw), Math.min(h, sh));
            BufferedImage blurred = new BufferedImage(cropped.getWidth(), cropped.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D gb = blurred.createGraphics();
            gb.drawImage(cropped, 0, 0, null);
            gb.dispose();
            if (NativeAccess.imageBlur(blurred, 2)) {
                BG_CACHE.put(key, blurred);
                return blurred;
            }
            // Apply a small blur kernel for softness
            float[] kernel = {
                    1f/16, 2f/16, 1f/16,
                    2f/16, 4f/16, 2f/16,
                    1f/16, 2f/16, 1f/16
            };
            ConvolveOp op = new ConvolveOp(new Kernel(3,3,kernel), ConvolveOp.EDGE_NO_OP, null);
            op.filter(cropped, blurred);
            BG_CACHE.put(key, blurred);
            return blurred;
        }

        private static final class SnippetHtmlCache {
            final long modifiedAt;
            final long length;
            final String token;
            final int wrapWidth;
            final String snippet;
            final String html;

            private SnippetHtmlCache(long modifiedAt, long length, String token, int wrapWidth, String snippet, String html) {
                this.modifiedAt = modifiedAt;
                this.length = length;
                this.token = token == null ? "" : token;
                this.wrapWidth = wrapWidth;
                this.snippet = snippet == null ? "" : snippet;
                this.html = html == null ? "" : html;
            }
        }

        private static final class TimeLabelCache {
            final long createdTs;
            final long modifiedTs;
            final long minuteBucket;
            final String createdText;
            final String editedText;
            final String editedContextText;

            private TimeLabelCache(long createdTs, long modifiedTs, long minuteBucket, String createdText, String editedText, String editedContextText) {
                this.createdTs = createdTs;
                this.modifiedTs = modifiedTs;
                this.minuteBucket = minuteBucket;
                this.createdText = createdText == null ? "" : createdText;
                this.editedText = editedText == null ? "" : editedText;
                this.editedContextText = editedContextText == null ? "" : editedContextText;
            }
        }

        EntryCardRenderer() {
            setOpaque(false);
            setLayout(new BorderLayout(12, 0));
            
            // Left content panel: title + preview, right-aligned
            JPanel content = new JPanel();
            content.setLayout(new javax.swing.BoxLayout(content, javax.swing.BoxLayout.Y_AXIS));
            content.setOpaque(false);
            
            title.setFont(new Font("Snell Roundhand", Font.BOLD, 18));
            title.setForeground(new Color(0x2B, 0x2B, 0x2B));
            title.setHorizontalAlignment(JLabel.LEFT);
            
            // Wrap title in left-aligned flow panel to prevent BoxLayout stretch
            JPanel titleWrapper = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
            titleWrapper.setOpaque(false);
            titleWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
            titleWrapper.add(title);
            
            snippet.setFont(snippet.getFont().deriveFont(Font.PLAIN, 12f));
            snippet.setForeground(new Color(90, 95, 110));
            snippet.setVerticalAlignment(JLabel.TOP);
            snippet.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

            editedContextLabel.setFont(snippet.getFont().deriveFont(Font.PLAIN, 11f));
            editedContextLabel.setForeground(new Color(118, 126, 141));
            editedContextLabel.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));

            content.add(titleWrapper);
            content.add(snippet);
            content.add(editedContextLabel);
            add(content, BorderLayout.CENTER);
            
            // Right stats panel: vertically stacked stats
            statsPanel.setLayout(new javax.swing.BoxLayout(statsPanel, javax.swing.BoxLayout.Y_AXIS));
            statsPanel.setOpaque(false);
            statsPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
            
            Font statsFont = title.getFont().deriveFont(Font.PLAIN, 11f);
            Color statsColor = metaColor;
            
            sizeLabel.setFont(statsFont);
            sizeLabel.setForeground(statsColor);
            sizeLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
            
            wordsLabel.setFont(statsFont);
            wordsLabel.setForeground(statsColor);
            wordsLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
            
            createdLabel.setFont(statsFont);
            createdLabel.setForeground(statsColor);
            createdLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
            
            editedLabel.setFont(statsFont);
            editedLabel.setForeground(statsColor);
            editedLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
            
            statsPanel.add(sizeLabel);
            statsPanel.add(javax.swing.Box.createVerticalStrut(2));
            statsPanel.add(wordsLabel);
            statsPanel.add(javax.swing.Box.createVerticalStrut(2));
            statsPanel.add(createdLabel);
            statsPanel.add(javax.swing.Box.createVerticalStrut(2));
            statsPanel.add(editedLabel);
            statsPanel.add(javax.swing.Box.createVerticalGlue());
            
            add(statsPanel, BorderLayout.EAST);
            
            // Minimal left padding - title should be at very left
            setBorder(comfortPadding);
            setPreferredSize(new Dimension(1, HEIGHT_COMFORT));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends EntryRow> list, EntryRow value, int index, boolean isSelected, boolean cellHasFocus) {
            if (value != null && value.isHeader()) {
                divider.setLabel(value.label);
                return divider;
            }
            // Title will be set externally via putClientProperty on the list for this renderer
            @SuppressWarnings("unchecked") Map<File,String> titles = (Map<File,String>) list.getClientProperty("titles");
            @SuppressWarnings("unchecked") Map<File,Integer> wordCounts = (Map<File,Integer>) list.getClientProperty("wordCounts");
            @SuppressWarnings("unchecked") Map<File,Long> timestamps = (Map<File,Long>) list.getClientProperty("entryTimestamps");
            @SuppressWarnings("unchecked") Map<File,Float> reorderAnim = (Map<File,Float>) list.getClientProperty("reorderAnim");
            @SuppressWarnings("unchecked") Map<File,Float> deleteAnim = (Map<File,Float>) list.getClientProperty("deleteAnim");
            @SuppressWarnings("unchecked") Map<File, PreviewSnapshot> previews = (Map<File, PreviewSnapshot>) list.getClientProperty("previews");
            @SuppressWarnings("unchecked") Map<File, Boolean> encryptedState = (Map<File, Boolean>) list.getClientProperty("encryptedFlags");
            String searchQuery = String.valueOf(list.getClientProperty("searchQuery") == null
                    ? ""
                    : list.getClientProperty("searchQuery"));
            String token = firstQueryToken(searchQuery);
            File file = value != null ? value.file : null;
            String fallback = file != null ? file.getName() : "";
            int dotIdx = fallback.lastIndexOf('.');
            if (dotIdx > 0) fallback = fallback.substring(0, dotIdx);
            String t = file != null && titles != null ? titles.get(file) : null;
            int wc = file != null && wordCounts != null ? wordCounts.getOrDefault(file, 0) : 0;
            Float glow = file != null && reorderAnim != null ? reorderAnim.get(file) : null;
            reorderGlow = glow == null ? 0f : glow;
            Float delProg = file != null && deleteAnim != null ? deleteAnim.get(file) : null;
            deleteProgress = delProg == null ? 0f : delProg;
            encrypted = false;
            if (file != null) {
                if (encryptedState != null) {
                    Boolean cachedEncrypted = encryptedState.get(file);
                    if (cachedEncrypted == null) {
                        cachedEncrypted = EncryptionManager.isEncrypted(file);
                        encryptedState.put(file, cachedEncrypted);
                    }
                    encrypted = cachedEncrypted;
                } else {
                    encrypted = EncryptionManager.isEncrypted(file);
                }
            }
            Object phaseObj = list.getClientProperty("selectionSweepPhase");
            if (phaseObj instanceof float[] arr && arr.length > 0) {
                selectionSweepPhase = arr[0];
            } else {
                selectionSweepPhase = 0f;
            }
            Object dashedPhaseObj = list.getClientProperty("dashedBorderPhase");
            dashedBorderPhase = dashedPhaseObj instanceof float[] dashedArr && dashedArr.length > 0 ? dashedArr[0] : 0f;
            Object hoverObj = list.getClientProperty("hoverIndex");
            int hoverIdx = hoverObj instanceof Integer ? (Integer) hoverObj : -1;
            hovered = (hoverIdx == index);
            EntryViewMode viewMode = resolveViewMode(list);
            boolean showStats = viewMode == EntryViewMode.COMFORT;
            boolean showSnippet = viewMode == EntryViewMode.COMFORT;
            boolean showEditedContext = viewMode == EntryViewMode.COMFORT;

            // Created from filename if matches yyyyMMdd_HHmmss, else fallback to modified
            long createdTs = -1L;
            if (file != null && timestamps != null) {
                Long ts = timestamps.get(file);
                if (ts != null) createdTs = ts;
            }
            if (createdTs <= 0 && file != null) {
                createdTs = entrySortTimestamp(file);
                if (timestamps != null) timestamps.put(file, createdTs);
            }
            long modifiedTs = file != null ? file.lastModified() : System.currentTimeMillis();

            String displayTitle = (t==null||t.isBlank()) ? fallback : t;
            title.setText(displayTitle);
            String size = file != null ? NotebookEntriesPanel.humanReadableSize(file.length()) : "-";
            
            // Update individual stat labels
            sizeLabel.setText(size);
            wordsLabel.setText(wc + " words");
            TimeLabelCache labels = resolveTimeLabels(file, createdTs, modifiedTs);
            createdLabel.setText(labels.createdText);
            editedLabel.setText(labels.editedText);
            PreviewSnapshot snap = (file != null && previews != null) ? previews.get(file) : null;
            if (showSnippet) {
                int wrapWidth = list.getWidth() > 0 ? Math.max(260, list.getWidth() - 360) : 540;
                wrapWidth = (wrapWidth / 24) * 24;
                snippet.setText(resolveSnippetHtml(file, snap, token, wrapWidth));
            } else {
                snippet.setText("");
            }
            editedContextLabel.setText(labels.editedContextText);

            statsPanel.setVisible(showStats);
            snippet.setVisible(showSnippet);
            editedContextLabel.setVisible(showEditedContext);
            setBorder(comfortPadding);
            setPreferredSize(new Dimension(1, HEIGHT_COMFORT));
            this.selected = isSelected;
            return this;
        }

        private static EntryViewMode resolveViewMode(JList<? extends EntryRow> list) {
            if (list == null) return EntryViewMode.COMFORT;
            Object modeObj = list.getClientProperty("entryViewMode");
            if (modeObj instanceof EntryViewMode mode) {
                return mode;
            }
            if (modeObj instanceof String name) {
                try {
                    return EntryViewMode.valueOf(name);
                } catch (IllegalArgumentException ignored) {
                    return EntryViewMode.COMFORT;
                }
            }
            return EntryViewMode.COMFORT;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            boolean hoverActive = hovered;
            boolean selectedOnly = selected && !hoverActive;

            // Apply delete animation (fade + slide + shrink)
            if (deleteProgress > 0f) {
                float visibility = main.infrastructure.ffi.NativeAccess.disappearValue(deleteProgress);
                g2.setComposite(AlphaComposite.SrcOver.derive(visibility));
                // Slide left effect
                int slideX = (int) (deleteProgress * 40);
                g2.translate(-slideX, 0);
            }
            
            int arc = 16;
            int w = getWidth(), h = getHeight();
            RoundRectangle2D.Float shape = new RoundRectangle2D.Float(4, 3, w - 8, h - 6, arc, arc);

            // Soft shadow for depth
            g2.setColor(new Color(0, 0, 0, 22));
            g2.fillRoundRect(6, 5, w - 8, h - 8, arc, arc);

            // Base glass gradient
            Color top = hoverActive ? new Color(241, 248, 255) : (selectedOnly ? new Color(246, 249, 253) : cardBg);
            Color bottom = hoverActive ? new Color(228, 236, 248) : (selectedOnly ? new Color(235, 240, 246) : new Color(235, 240, 248));
            g2.setPaint(new GradientPaint(0, 3, top, 0, h - 3, bottom));
            g2.fill(shape);

            // Subtle background texture
            java.awt.Shape oldClip = g2.getClip();
            g2.setClip(shape);
            BufferedImage bg = getBg(Math.max(1, w - 8), Math.max(1, h - 6));
            if (bg != null) {
                Composite old = g2.getComposite();
                g2.setComposite(AlphaComposite.SrcOver.derive(0.12f));
                g2.drawImage(bg, 4, 3, null);
                g2.setComposite(old);
            }

            // Glass highlight
            int glossH = Math.max(10, (int) ((h - 6) * 0.55f));
            g2.setPaint(new GradientPaint(0, 3, new Color(255, 255, 255, 150), 0, 3 + glossH, new Color(255, 255, 255, 0)));
            g2.fill(new RoundRectangle2D.Float(4, 3, w - 8, glossH, arc, arc));

            if (reorderGlow > 0f) {
                float a = Math.max(0f, Math.min(0.18f, reorderGlow * 0.18f));
                g2.setComposite(AlphaComposite.SrcOver.derive(a));
                g2.setColor(accent);
                g2.fill(shape);
                g2.setComposite(AlphaComposite.SrcOver);
            }
            g2.setClip(oldClip);

            // Selected sweep highlight (subtle Aero-like sheen)
            if (hoverActive && deleteProgress <= 0.01f) {
                int bandW = Math.max(80, Math.round(w * 0.35f));
                float startX = (selectionSweepPhase * 1.4f - 0.2f) * w;
                Shape sweepClip = g2.getClip();
                g2.setClip(shape);
                Composite old = g2.getComposite();
                g2.setComposite(AlphaComposite.SrcOver.derive(0.16f));
                LinearGradientPaint sweep = new LinearGradientPaint(
                    startX, 0, startX + bandW, 0,
                    new float[]{0f, 0.5f, 1f},
                    new Color[]{
                        new Color(255, 255, 255, 0),
                        new Color(255, 255, 255, 180),
                        new Color(255, 255, 255, 0)
                    }
                );
                g2.setPaint(sweep);
                g2.fill(shape);
                g2.setComposite(old);
                g2.setClip(sweepClip);
            }

            // Borders
            if (hoverActive) {
                g2.setColor(new Color(120, 170, 255, 90));
                g2.drawRoundRect(3, 2, w - 6, h - 4, arc + 2, arc + 2);
            }
            if (hoverActive) {
                g2.setColor(selectedBorder);
            } else if (selectedOnly) {
                g2.setColor(new Color(140, 160, 190, 120));
            } else {
                g2.setColor(cardBorder);
            }
            g2.drawRoundRect(4, 3, w - 8, h - 6, arc, arc);
            g2.setColor(new Color(255, 255, 255, 140));
            g2.drawRoundRect(5, 4, w - 10, h - 8, arc - 1, arc - 1);
            
            // Draw dashed border overlay for selected state (when not hovered)
            if (selected && !hoverActive && deleteProgress <= 0.01f) {
                // Create animated dashed stroke for selection indicator
                float[] dashPattern = {6.0f, 4.0f}; // Dash length 6, gap 4
                java.awt.BasicStroke dashedStroke = new java.awt.BasicStroke(2.0f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND, 0.0f, dashPattern, dashedBorderPhase);
                g2.setStroke(dashedStroke);
                g2.setColor(new Color(88, 133, 255, 180)); // Accent color with good visibility
                g2.drawRoundRect(2, 1, w - 4, h - 2, arc + 3, arc + 3);
                
                // Reset stroke for any subsequent drawing
                g2.setStroke(new java.awt.BasicStroke(1.0f));
            }
            
            // Draw lock icon for encrypted entries (top-left corner)
            if (encrypted) {
                int lockX = 20, lockY = 12;
                
                // Subtle shadow
                g2.setColor(new Color(0, 0, 0, 20));
                g2.fillOval(lockX - 1, lockY, 13, 13);
                
                // Lock body - elegant dark style
                g2.setColor(new Color(50, 55, 65));
                int bodyW = 9, bodyH = 7;
                int bodyX = lockX, bodyY = lockY + 5;
                g2.fillRoundRect(bodyX, bodyY, bodyW, bodyH, 2, 2);
                
                // Lock shackle
                g2.setStroke(new java.awt.BasicStroke(2f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                int shackleW = 6, shackleH = 5;
                int shackleCx = lockX + bodyW / 2;
                g2.drawArc(shackleCx - shackleW / 2, bodyY - shackleH + 1, shackleW, shackleH * 2, 0, 180);
                
                // Keyhole highlight
                g2.setColor(new Color(255, 255, 255, 180));
                g2.fillOval(shackleCx - 1, bodyY + 2, 3, 3);
            }
            g2.dispose();
            super.paintComponent(g);
        }

        private TimeLabelCache resolveTimeLabels(File file, long createdTs, long modifiedTs) {
            long minuteBucket = System.currentTimeMillis() / 60_000L;
            if (file == null) {
                Date created = createdTs > 0 ? new Date(createdTs) : new Date();
                Date modified = modifiedTs > 0 ? new Date(modifiedTs) : new Date();
                SimpleDateFormat df = ENTRY_LIST_DATE_FORMAT.get();
                return new TimeLabelCache(
                        createdTs,
                        modifiedTs,
                        minuteBucket,
                        "Created " + df.format(created),
                        "Edited " + df.format(modified),
                        formatLastEditedContext(modifiedTs, minuteBucket)
                );
            }
            TimeLabelCache cached = timeLabelCache.get(file);
            if (cached != null
                    && cached.createdTs == createdTs
                    && cached.modifiedTs == modifiedTs
                    && cached.minuteBucket == minuteBucket) {
                return cached;
            }
            Date created = createdTs > 0 ? new Date(createdTs) : new Date();
            Date modified = modifiedTs > 0 ? new Date(modifiedTs) : new Date();
            SimpleDateFormat df = ENTRY_LIST_DATE_FORMAT.get();
            TimeLabelCache computed = new TimeLabelCache(
                    createdTs,
                    modifiedTs,
                    minuteBucket,
                    "Created " + df.format(created),
                    "Edited " + df.format(modified),
                    formatLastEditedContext(modifiedTs, minuteBucket)
            );
            timeLabelCache.put(file, computed);
            return computed;
        }

        private String resolveSnippetHtml(File file, PreviewSnapshot snap, String token, int wrapPx) {
            String src = snap != null ? snap.snippet : "";
            if (file == null) {
                return buildHighlightedSnippetHtml(src, token, wrapPx);
            }
            long modifiedAt = snap != null ? snap.lastModified : file.lastModified();
            long length = snap != null ? snap.length : file.length();
            String normalizedToken = token == null ? "" : token;
            SnippetHtmlCache cached = snippetHtmlCache.get(file);
            if (cached != null
                    && cached.modifiedAt == modifiedAt
                    && cached.length == length
                    && cached.wrapWidth == wrapPx
                    && Objects.equals(cached.token, normalizedToken)
                    && Objects.equals(cached.snippet, src)) {
                return cached.html;
            }
            String html = buildHighlightedSnippetHtml(src, normalizedToken, wrapPx);
            snippetHtmlCache.put(file, new SnippetHtmlCache(modifiedAt, length, normalizedToken, wrapPx, src, html));
            return html;
        }

        private static String formatLastEditedContext(long modifiedAt, long minuteBucket) {
            long now = minuteBucket * 60_000L;
            long deltaMs = Math.max(0L, now - Math.max(0L, modifiedAt));
            long minutes = deltaMs / 60_000L;
            if (minutes < 1) return "Last edited just now";
            if (minutes < 60) return "Last edited " + minutes + "m ago";
            long hours = minutes / 60;
            if (hours < 24) return "Last edited " + hours + "h ago";
            long days = hours / 24;
            if (days < 30) return "Last edited " + days + "d ago";
            long months = days / 30;
            if (months < 12) return "Last edited " + months + "mo ago";
            long years = months / 12;
            return "Last edited " + years + "y ago";
        }

        private static String buildHighlightedSnippetHtml(String snippet, String token, int wrapPx) {
            String src = snippet == null ? "" : snippet.trim();
            if (src.isEmpty()) {
                return "<html><div style='color:#8A92A3'>No preview available.</div></html>";
            }
            String rendered;
            if (token.isEmpty()) {
                rendered = escapeHtml(src);
            } else {
                rendered = highlightToken(src, token);
            }
            return "<html><div style='width:" + Math.max(220, wrapPx) + "px;'>"
                    + rendered + "</div></html>";
        }

        private static String firstQueryToken(String query) {
            if (query == null) return "";
            for (String part : query.trim().split("\\s+")) {
                String token = part.trim();
                if (token.length() >= 2) return token;
            }
            return "";
        }

        private static String highlightToken(String src, String token) {
            if (src == null || src.isEmpty() || token == null || token.isEmpty()) {
                return escapeHtml(src == null ? "" : src);
            }
            String lower = src.toLowerCase(Locale.ROOT);
            String needle = token.toLowerCase(Locale.ROOT);
            StringBuilder out = new StringBuilder(src.length() + 48);
            int from = 0;
            while (from < src.length()) {
                int idx = lower.indexOf(needle, from);
                if (idx < 0) {
                    out.append(escapeHtml(src.substring(from)));
                    break;
                }
                out.append(escapeHtml(src.substring(from, idx)));
                String hit = src.substring(idx, idx + needle.length());
                out.append("<span style='background:#FFE8A3;color:#37445A;padding:0 1px;border-radius:3px;'>")
                        .append(escapeHtml(hit))
                        .append("</span>");
                from = idx + needle.length();
            }
            return out.toString();
        }

        private static String escapeHtml(String text) {
            if (text == null || text.isEmpty()) return "";
            return text.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
        }

    }

    private static final class DateDividerRenderer extends JComponent {
        private static final int HEIGHT = 64;
        private String label = "";

        private DateDividerRenderer() {
            setOpaque(false);
            setFont(DateDividerPainter.resolveFont(16f));
            setForeground(new Color(60, 60, 60));
            setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        }

        void setLabel(String label) {
            this.label = label == null ? "" : label.trim();
            setPreferredSize(new Dimension(1, HEIGHT));
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(180, HEIGHT);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) { g2.dispose(); return; }
            DateDividerPainter.paint(g2, w, h, label, getFont(), getForeground());
            g2.dispose();
        }
    }

    private static final class EntryRow {
        enum Kind { HEADER, FILE }
        final Kind kind;
        final File file;
        final LocalDate date;
        final String label;

        private EntryRow(Kind kind, File file, LocalDate date, String label) {
            this.kind = kind;
            this.file = file;
            this.date = date;
            this.label = label;
        }

        static EntryRow header(LocalDate date, String label) {
            return new EntryRow(Kind.HEADER, null, date, label);
        }

        static EntryRow file(File file) {
            return new EntryRow(Kind.FILE, file, null, null);
        }

        boolean isHeader() { return kind == Kind.HEADER; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof EntryRow other)) return false;
            if (kind != other.kind) return false;
            if (kind == Kind.FILE) return Objects.equals(file, other.file);
            return Objects.equals(date, other.date);
        }

        @Override
        public int hashCode() {
            return kind == Kind.FILE ? Objects.hash(kind, file) : Objects.hash(kind, date);
        }
    }

    private final class CalendarEntriesPanel extends JPanel {
        private static final DateTimeFormatter DAY_ENTRY_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
        private final JLabel monthLabel = new JLabel("", JLabel.CENTER);
        private final JLabel hintLabel = new JLabel("Hover a day to preview. Click a day to lock selection. Double-click opens latest.");
        private final JPanel weekHeader = new JPanel(new java.awt.GridLayout(1, 7, 4, 2));
        private final JPanel daysGrid = new JPanel(new java.awt.GridLayout(6, 7, 4, 4));
        private final FrostedGlassPanel dayEntriesPanel = new FrostedGlassPanel(new BorderLayout(0, 6), 10);
        private final JLabel dayEntriesLabel = new JLabel("Entries", JLabel.LEFT);
        private final JPanel dayEntriesList = new JPanel();
        private final CalendarDayCell[] dayCells = new CalendarDayCell[42];
        private final DayOfWeek firstDayOfWeek = WeekFields.of(Locale.getDefault()).getFirstDayOfWeek();
        private final Map<LocalDate, List<File>> filesByDate = new HashMap<>();
        private YearMonth month = YearMonth.now();
        private LocalDate selectedDate = null;
        private LocalDate hoveredDate = null;
        private LocalDate previewDate = null;
        private boolean previewSelectedSource = false;
        private boolean monthResolvedFromData = false;
        private boolean userNavigatedMonth = false;
        private String dayEntriesRenderSignature = "";
        private float selectedBorderPhase = 0f;
        private final Timer selectedBorderTimer = new Timer(48, e -> {
            selectedBorderPhase += 0.85f;
            if (selectedBorderPhase > 24f) selectedBorderPhase -= 24f;
            repaintSelectedDayCell();
        });
        private final Timer previewRefreshTimer = new Timer(90, e -> refreshPreviewFromState());

        private CalendarEntriesPanel() {
            setOpaque(false);
            setLayout(new BorderLayout(8, 8));
            setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 12));

            JPanel header = new FrostedGlassPanel(new BorderLayout(6, 0), 12);
            header.setOpaque(false);
            header.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            ToolbarIconButton prev = new ToolbarIconButton("back");
            prev.setToolTipText("Previous month");
            prev.setPreferredSize(new Dimension(32, 26));
            prev.setMinimumSize(new Dimension(32, 26));
            prev.setMaximumSize(new Dimension(32, 26));
            prev.addActionListener(e -> shiftMonth(-1));

            ToolbarIconButton next = new ToolbarIconButton("back");
            next.setIconRotationRadians(Math.PI);
            next.setToolTipText("Next month");
            next.setPreferredSize(new Dimension(32, 26));
            next.setMinimumSize(new Dimension(32, 26));
            next.setMaximumSize(new Dimension(32, 26));
            next.addActionListener(e -> shiftMonth(1));

            monthLabel.setFont(AeroTheme.defaultFont().deriveFont(Font.BOLD, 15f));
            monthLabel.setForeground(new Color(58, 66, 82));
            header.add(prev, BorderLayout.WEST);
            header.add(monthLabel, BorderLayout.CENTER);
            header.add(next, BorderLayout.EAST);

            weekHeader.setOpaque(false);
            for (int i = 0; i < 7; i++) {
                DayOfWeek day = firstDayOfWeek.plus(i);
                JLabel lbl = new JLabel(day.getDisplayName(TextStyle.SHORT, Locale.getDefault()), JLabel.CENTER);
                lbl.setFont(AeroTheme.defaultFont().deriveFont(Font.BOLD, 11.5f));
                lbl.setForeground(new Color(110, 118, 132));
                weekHeader.add(lbl);
            }

            daysGrid.setOpaque(false);
            for (int i = 0; i < dayCells.length; i++) {
                CalendarDayCell cell = new CalendarDayCell();
                dayCells[i] = cell;
                daysGrid.add(cell);
            }

            hintLabel.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 11.5f));
            hintLabel.setForeground(new Color(108, 116, 132));
            hintLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 0, 4));

            dayEntriesPanel.setOpaque(false);
            dayEntriesPanel.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));
            dayEntriesLabel.setFont(AeroTheme.defaultFont().deriveFont(Font.BOLD, 11.5f));
            dayEntriesLabel.setForeground(new Color(78, 88, 108));

            dayEntriesList.setOpaque(false);
            dayEntriesList.setLayout(new javax.swing.BoxLayout(dayEntriesList, javax.swing.BoxLayout.Y_AXIS));
            JScrollPane dayEntriesScroll = new JScrollPane(dayEntriesList);
            dayEntriesScroll.setOpaque(false);
            dayEntriesScroll.getViewport().setOpaque(false);
            dayEntriesScroll.setBorder(BorderFactory.createEmptyBorder());
            dayEntriesScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            dayEntriesScroll.setPreferredSize(new Dimension(1, 120));
            dayEntriesScroll.getVerticalScrollBar().setUnitIncrement(14);

            dayEntriesPanel.add(dayEntriesLabel, BorderLayout.NORTH);
            dayEntriesPanel.add(dayEntriesScroll, BorderLayout.CENTER);
            dayEntriesPanel.setVisible(true);
            previewRefreshTimer.setRepeats(false);

            JPanel center = new FrostedGlassPanel(new BorderLayout(6, 6), 12);
            center.setOpaque(false);
            center.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));
            center.add(weekHeader, BorderLayout.NORTH);
            center.add(daysGrid, BorderLayout.CENTER);

            JPanel footer = new JPanel(new BorderLayout(0, 6));
            footer.setOpaque(false);
            footer.add(hintLabel, BorderLayout.NORTH);
            footer.add(dayEntriesPanel, BorderLayout.CENTER);

            add(header, BorderLayout.NORTH);
            add(center, BorderLayout.CENTER);
            add(footer, BorderLayout.SOUTH);
            rebuildGrid();
        }

        private void setEntries(List<File> orderedFiles) {
            filesByDate.clear();
            if (orderedFiles != null) {
                for (File f : orderedFiles) {
                    if (f == null) continue;
                    LocalDate date = entryDates.getOrDefault(f, resolveEntryDate(entrySortTimestampCached(f)));
                    filesByDate.computeIfAbsent(date, k -> new ArrayList<>()).add(f);
                }
            }
            // Auto-anchor only once on first data hydrate. After user navigation, never snap month.
            if (!monthResolvedFromData && !userNavigatedMonth && !filesByDate.isEmpty() && !hasEntriesForMonth(month)) {
                LocalDate latest = null;
                for (LocalDate d : filesByDate.keySet()) {
                    if (latest == null || d.isAfter(latest)) latest = d;
                }
                if (latest != null) {
                    month = YearMonth.from(latest);
                }
            }
            monthResolvedFromData = true;
            if (selectedDate != null && !filesByDate.containsKey(selectedDate)) {
                selectedDate = null;
            }
            if (hoveredDate != null && !filesByDate.containsKey(hoveredDate)) {
                hoveredDate = null;
            }
            rebuildGrid();
        }

        private boolean hasEntriesForMonth(YearMonth ym) {
            if (ym == null || filesByDate.isEmpty()) return false;
            for (LocalDate d : filesByDate.keySet()) {
                if (d != null && YearMonth.from(d).equals(ym)) return true;
            }
            return false;
        }

        private void shiftMonth(int delta) {
            userNavigatedMonth = true;
            month = month.plusMonths(delta);
            selectedDate = null;
            hoveredDate = null;
            rebuildGrid();
        }

        private void rebuildGrid() {
            monthLabel.setText(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")));
            LocalDate first = month.atDay(1);
            int offset = (first.getDayOfWeek().getValue() - firstDayOfWeek.getValue() + 7) % 7;
            LocalDate cursor = first.minusDays(offset);
            LocalDate today = LocalDate.now();
            for (CalendarDayCell cell : dayCells) {
                if (cell == null) continue;
                LocalDate date = cursor;
                boolean inMonth = date.getMonth() == month.getMonth() && date.getYear() == month.getYear();
                List<File> files = filesByDate.getOrDefault(date, java.util.Collections.emptyList());
                int count = files.size();
                int avgMood = -1;
                if (count > 0) {
                    int moodSum = 0;
                    int moodCount = 0;
                    for (File f : files) {
                        int mood = moodValues.getOrDefault(f, -1);
                        if (mood >= 0) {
                            moodSum += mood;
                            moodCount++;
                        }
                    }
                    if (moodCount > 0) {
                        avgMood = Math.max(0, Math.min(100, Math.round(moodSum / (float) moodCount)));
                    }
                }
                boolean selected = selectedDate != null && selectedDate.equals(date);
                cell.setDay(date, inMonth, date.equals(today), selected, count, avgMood);
                cursor = cursor.plusDays(1);
            }
            LocalDate focusDate = hoveredDate != null ? hoveredDate : selectedDate;
            if (focusDate != null) {
                boolean fromSelection = hoveredDate == null && selectedDate != null && selectedDate.equals(focusDate);
                showDayEntriesPreview(focusDate, filesByDate.getOrDefault(focusDate, java.util.Collections.emptyList()), fromSelection);
            } else {
                showDayEntriesPreview(null, java.util.Collections.emptyList(), false);
            }
            updateSelectedBorderAnimation();
            revalidate();
            repaint();
        }

        private void onDayClicked(LocalDate date, int clickCount) {
            if (date == null) return;
            selectedDate = date;
            hoveredDate = null; // lock preview to selected day until selection changes/clears
            rebuildGrid();
            List<File> files = filesByDate.getOrDefault(date, java.util.Collections.emptyList());
            if (files.isEmpty()) {
                hintLabel.setText(date + " · no entries");
                showDayEntriesPreview(date, files, true);
                return;
            }
            hintLabel.setText(date + " · " + files.size() + (files.size() == 1 ? " entry" : " entries")
                    + " (choose one below or double-click day to open newest)");
            showDayEntriesPreview(date, files, true);
            if (clickCount >= 2) {
                File newest = null;
                long newestTs = Long.MIN_VALUE;
                for (File f : files) {
                    long ts = entrySortTimestampCached(f);
                    if (newest == null || ts > newestTs) {
                        newest = f;
                        newestTs = ts;
                    }
                }
                if (newest != null) {
                    openFile(newest);
                }
            }
        }

        private void onDayHovered(LocalDate date) {
            if (date == null) return;
            if (selectedDate != null) return;
            hoveredDate = date;
            List<File> files = filesByDate.getOrDefault(date, java.util.Collections.emptyList());
            if (files.isEmpty()) {
                hintLabel.setText(date + " · no entries");
                showDayEntriesPreview(date, files, false);
                return;
            }
            hintLabel.setText(date + " · " + files.size() + (files.size() == 1 ? " entry" : " entries")
                    + " (click an item below to open)");
            showDayEntriesPreview(date, files, false);
        }

        private void showDayEntriesPreview(LocalDate date, List<File> files, boolean selectedSource) {
            previewDate = date;
            previewSelectedSource = selectedSource;
            if (date == null) {
                String signature = "none";
                if (signature.equals(dayEntriesRenderSignature)) return;
                dayEntriesRenderSignature = signature;
                dayEntriesList.removeAll();
                dayEntriesLabel.setText("Day entries");
                JLabel placeholder = new JLabel("Hover or select a day to list entries.");
                placeholder.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 11.5f));
                placeholder.setForeground(new Color(112, 122, 138));
                dayEntriesList.add(placeholder);
                dayEntriesPanel.revalidate();
                dayEntriesPanel.repaint();
                return;
            }
            if (files == null || files.isEmpty()) {
                String signature = "empty|" + selectedSource + "|" + date;
                if (signature.equals(dayEntriesRenderSignature)) return;
                dayEntriesRenderSignature = signature;
                dayEntriesList.removeAll();
                String prefix = selectedSource ? "Selected day" : "Hovered day";
                dayEntriesLabel.setText(prefix + " · " + formatEntryDate(date));
                JLabel empty = new JLabel("No entries on this day.");
                empty.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 11.5f));
                empty.setForeground(new Color(112, 122, 138));
                dayEntriesList.add(empty);
                dayEntriesPanel.revalidate();
                dayEntriesPanel.repaint();
                return;
            }

            List<File> ordered = new ArrayList<>(files);
            ordered.sort(Comparator.comparingLong(NotebookEntriesPanel.this::entrySortTimestampCached).reversed());
            String prefix = selectedSource ? "Selected day" : "Hovered day";
            List<File> missingMetadata = new ArrayList<>();
            StringBuilder signature = new StringBuilder(128)
                    .append("rows|").append(selectedSource).append('|').append(date).append('|').append(ordered.size()).append('|');

            int maxRows = Math.min(10, ordered.size());
            for (int i = 0; i < maxRows; i++) {
                File file = ordered.get(i);
                boolean loading = file != null && !metaComputed.contains(file);
                if (loading) {
                    missingMetadata.add(file);
                }
                signature.append(file == null ? "<null>" : file.getAbsolutePath())
                        .append('@').append(loading ? 'L' : 'R')
                        .append(':').append(loading ? "<loading>" : resolveEntryTitle(file))
                        .append(';');
            }

            String nextSignature = signature.toString();
            if (nextSignature.equals(dayEntriesRenderSignature)) {
                if (!missingMetadata.isEmpty()) {
                    startPrioritizedMetaLoader(missingMetadata);
                }
                return;
            }
            dayEntriesRenderSignature = nextSignature;
            dayEntriesList.removeAll();
            dayEntriesLabel.setText(prefix + " · " + formatEntryDate(date));

            for (int i = 0; i < maxRows; i++) {
                File file = ordered.get(i);
                boolean loading = file != null && !metaComputed.contains(file);
                if (loading) {
                    dayEntriesList.add(buildDayEntryLoadingRow(file));
                } else {
                    dayEntriesList.add(buildDayEntryRow(file));
                }
                if (i < maxRows - 1) {
                    dayEntriesList.add(Box.createVerticalStrut(4));
                }
            }
            if (ordered.size() > maxRows) {
                dayEntriesList.add(Box.createVerticalStrut(6));
                JLabel more = new JLabel("+" + (ordered.size() - maxRows) + " more entries");
                more.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 11f));
                more.setForeground(new Color(110, 120, 136));
                dayEntriesList.add(more);
            }

            dayEntriesPanel.revalidate();
            dayEntriesPanel.repaint();
            if (!missingMetadata.isEmpty()) {
                startPrioritizedMetaLoader(missingMetadata);
            }
        }

        private void onMetadataUpdated(List<File> changedFiles) {
            if (changedFiles == null || changedFiles.isEmpty() || previewDate == null) return;
            List<File> files = filesByDate.getOrDefault(previewDate, java.util.Collections.emptyList());
            if (files.isEmpty()) return;
            for (File changed : changedFiles) {
                if (changed != null && files.contains(changed)) {
                    previewRefreshTimer.restart();
                    return;
                }
            }
        }

        private void refreshPreviewFromState() {
            LocalDate date = previewDate;
            if (date == null) {
                showDayEntriesPreview(null, java.util.Collections.emptyList(), false);
                return;
            }
            List<File> files = filesByDate.getOrDefault(date, java.util.Collections.emptyList());
            showDayEntriesPreview(date, files, previewSelectedSource);
        }

        private javax.swing.JComponent buildDayEntryLoadingRow(File file) {
            long ts = entrySortTimestampCached(file);
            LocalDateTime ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault());
            String time = ldt.format(DAY_ENTRY_TIME_FORMAT);
            return new DayEntryLoadingRow(time);
        }

        private void updateSelectedBorderAnimation() {
            if (selectedDate != null) {
                if (!selectedBorderTimer.isRunning()) selectedBorderTimer.start();
            } else {
                if (selectedBorderTimer.isRunning()) selectedBorderTimer.stop();
                selectedBorderPhase = 0f;
            }
        }

        private void repaintSelectedDayCell() {
            if (selectedDate == null) return;
            for (CalendarDayCell cell : dayCells) {
                if (cell == null || cell.date == null) continue;
                if (selectedDate.equals(cell.date)) {
                    cell.repaint();
                    return;
                }
            }
            daysGrid.repaint();
        }

        @Override
        public void removeNotify() {
            try { selectedBorderTimer.stop(); } catch (Throwable ignored) {}
            try { previewRefreshTimer.stop(); } catch (Throwable ignored) {}
            super.removeNotify();
        }

        private javax.swing.JComponent buildDayEntryRow(File file) {
            long ts = entrySortTimestampCached(file);
            LocalDateTime ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault());
            String time = ldt.format(DAY_ENTRY_TIME_FORMAT);
            String title = resolveEntryTitle(file);

            JLabel row = new JLabel(time + "  \u00b7  " + title);
            row.setOpaque(true);
            row.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 12f));
            row.setForeground(new Color(62, 72, 90));
            row.setBackground(new Color(246, 250, 255, 186));
            row.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(176, 188, 204, 150)),
                    BorderFactory.createEmptyBorder(5, 8, 5, 8)));
            row.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            row.setToolTipText("Open " + title);
            row.addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) {
                    row.setBackground(new Color(233, 243, 255, 210));
                }
                @Override public void mouseExited(MouseEvent e) {
                    row.setBackground(new Color(246, 250, 255, 186));
                }
                @Override public void mouseClicked(MouseEvent e) {
                    if (file != null && file.exists()) {
                        openFile(file);
                    }
                }
            });
            return row;
        }

        private String resolveEntryTitle(File file) {
            if (file == null) return "";
            String title = titles.get(file);
            if (title != null && !title.isBlank()) return title;
            String nm = file.getName();
            int dot = nm.lastIndexOf('.');
            return dot > 0 ? nm.substring(0, dot) : nm;
        }

        private final class DayEntryLoadingRow extends JPanel {
            private final ModernSpinner spinner;

            private DayEntryLoadingRow(String timeLabel) {
                super(new FlowLayout(FlowLayout.LEFT, 6, 0));
                setOpaque(true);
                setBackground(new Color(246, 250, 255, 186));
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(176, 188, 204, 150)),
                        BorderFactory.createEmptyBorder(3, 7, 3, 8)));

                spinner = new ModernSpinner(12, new Color(96, 132, 188));
                spinner.setPreferredSize(new Dimension(12, 12));
                spinner.setMinimumSize(new Dimension(12, 12));
                spinner.setMaximumSize(new Dimension(12, 12));
                add(spinner);

                JLabel text = new JLabel(timeLabel + "  ·  Loading title...");
                text.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 12f));
                text.setForeground(new Color(90, 102, 122));
                add(text);
            }

            @Override
            public void removeNotify() {
                try { spinner.stop(); } catch (Throwable ignored) {}
                super.removeNotify();
            }
        }

        private final class CalendarDayCell extends JPanel {
            private LocalDate date;
            private boolean inMonth;
            private boolean today;
            private boolean selected;
            private boolean hovered;
            private int count;
            private int avgMood;

            private CalendarDayCell() {
                setOpaque(false);
                setPreferredSize(new Dimension(1, 74));
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (date != null) onDayClicked(date, e.getClickCount());
                    }
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hovered = true;
                        if (date != null) onDayHovered(date);
                        repaint();
                    }
                    @Override
                    public void mouseExited(MouseEvent e) {
                        hovered = false;
                        repaint();
                    }
                });
            }

            private void setDay(LocalDate date, boolean inMonth, boolean today, boolean selected, int count, int avgMood) {
                this.date = date;
                this.inMonth = inMonth;
                this.today = today;
                this.selected = selected;
                this.count = Math.max(0, count);
                this.avgMood = avgMood;
                repaint();
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                if (w <= 2 || h <= 2 || date == null) {
                    g2.dispose();
                    return;
                }
                Color bg = inMonth ? new Color(248, 250, 255, 170) : new Color(242, 245, 250, 120);
                if (selected) bg = new Color(226, 238, 255, 205);
                if (hovered && !selected && selectedDate == null) bg = new Color(232, 243, 255, 212);
                if (today && !selected) bg = new Color(238, 247, 255, 195);
                g2.setColor(bg);
                g2.fillRoundRect(1, 1, w - 2, h - 2, 10, 10);
                g2.setColor(selected
                        ? new Color(112, 156, 238, 210)
                        : ((hovered && selectedDate == null) ? new Color(128, 160, 218, 180) : new Color(180, 190, 205, 150)));
                g2.drawRoundRect(1, 1, w - 2, h - 2, 10, 10);
                if (selected) {
                    g2.setColor(new Color(88, 133, 255, 95));
                    g2.drawRoundRect(0, 0, w - 1, h - 1, 11, 11);
                    float[] dashPattern = {6.0f, 4.0f};
                    g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, dashPattern, selectedBorderPhase));
                    g2.setColor(new Color(88, 133, 255, 188));
                    g2.drawRoundRect(2, 2, w - 4, h - 4, 9, 9);
                    g2.setStroke(new BasicStroke(1.0f));
                }

                g2.setFont(AeroTheme.defaultFont().deriveFont(today ? Font.BOLD : Font.PLAIN, 12.5f));
                g2.setColor(inMonth ? new Color(50, 60, 78) : new Color(140, 146, 160));
                g2.drawString(Integer.toString(date.getDayOfMonth()), 8, 18);

                if (count > 0) {
                    String txt = Integer.toString(count);
                    int badgeW = Math.max(18, 10 + g2.getFontMetrics(AeroTheme.defaultFont().deriveFont(Font.BOLD, 11f)).stringWidth(txt));
                    int bx = w - badgeW - 6;
                    int by = h - 22;
                    Color mood = avgMood >= 0 ? moodColorAt(avgMood) : new Color(120, 150, 210);
                    g2.setColor(new Color(mood.getRed(), mood.getGreen(), mood.getBlue(), 170));
                    g2.fillRoundRect(bx, by, badgeW, 15, 8, 8);
                    g2.setColor(new Color(255, 255, 255, 220));
                    g2.setFont(AeroTheme.defaultFont().deriveFont(Font.BOLD, 11f));
                    int tx = bx + (badgeW - g2.getFontMetrics().stringWidth(txt)) / 2;
                    g2.drawString(txt, tx, by + 11);
                }
                g2.dispose();
            }
        }
    }

    public NotebookEntriesPanel(JournalApp app, NotebookInfo nb){
        this.app = app; this.nb = nb;
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);
        this.viewMode = loadPersistedViewMode();

        // Top bar
        JPanel top = new FrostedGlassPanel(new FlowLayout(FlowLayout.LEFT, 8, 6), 16);
        top.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        // Replace text back button with PNG back icon button
        ToolbarMenuIconButton backBtn = new ToolbarMenuIconButton("", "back");
        backBtn.setToolTipText("Back to Notebooks");
        backBtn.addActionListener(e->app.switchCard(JournalApp.NOTEBOOK_MANAGER));
        Dimension bigButtonSize = new Dimension(80, 80);

        ToolbarMenuIconButton newBtn = new ToolbarMenuIconButton("", "new_entry");
        newBtn.addActionListener(e->createNew());
        ToolbarMenuIconButton deleteBtn = new ToolbarMenuIconButton("", "delete");
        deleteBtn.addActionListener(e->deleteSelected());

        ToolbarMenuIconButton delNbBtn = new ToolbarMenuIconButton("", "delete_notebook");
        delNbBtn.addActionListener(e->deleteNotebook());
        for (ToolbarMenuIconButton btn : new ToolbarMenuIconButton[]{newBtn, deleteBtn, delNbBtn}) {
            btn.setPreferredSize(bigButtonSize);
            btn.setMinimumSize(bigButtonSize);
            btn.setMaximumSize(new Dimension(96, 80));
        }

        top.add(backBtn);
        top.add(new JLabel(nb.getName()));
        top.add(Box.createHorizontalStrut(20));
        top.add(new JLabel("Search:")); top.add(searchField);
        top.add(new JLabel("Sort:")); top.add(sortBox);
        top.add(new JLabel("View:"));
        JPanel viewModeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        viewModeRow.setOpaque(false);
        comfortViewBtn = createViewModeButton("paragraph_selector", "Classic card view", EntryViewMode.COMFORT);
        calendarViewBtn = createViewModeButton("calendar_selector", "Calendar view", EntryViewMode.CALENDAR);
        viewModeRow.add(comfortViewBtn);
        viewModeRow.add(calendarViewBtn);
        top.add(viewModeRow);
        top.add(newBtn); top.add(deleteBtn); top.add(delNbBtn);
        applyViewModeSelectionState();
        add(top,BorderLayout.NORTH);

        // Debounce search updates to avoid frequent resorting/filtering while typing
        searchDebounce.setRepeats(false);
        searchField.getDocument().addDocumentListener(new DocumentListener(){
            @Override public void insertUpdate(DocumentEvent e){ searchDebounce.restart(); }
            @Override public void removeUpdate(DocumentEvent e){ searchDebounce.restart(); }
            @Override public void changedUpdate(DocumentEvent e){ searchDebounce.restart(); }
        });
        sortBox.setUI(new ModernComboBoxUI());
        sortBox.setRenderer(new ModernComboBoxUI.ModernComboBoxRenderer());
        sortBox.addActionListener(e->update());

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // Attach shared maps for renderer access
        list.putClientProperty("titles", titles);
        list.putClientProperty("wordCounts", wordCounts);
        list.putClientProperty("moods", moodValues);
        list.putClientProperty("entryTimestamps", entryTimestamps);
        list.putClientProperty("reorderAnim", reorderAnimProgress);
        list.putClientProperty("selectionSweepPhase", selectionSweepPhase);
        list.putClientProperty("dashedBorderPhase", dashedBorderPhase);
        list.putClientProperty("previews", previewCache);
        list.putClientProperty("moodTrends", moodTrendCache);
        list.putClientProperty("encryptedFlags", encryptedFlags);
        list.putClientProperty("searchQuery", "");
        list.putClientProperty("entryViewMode", viewMode);
        list.putClientProperty("hoverIndex", -1);
        list.setBackground(new Color(247, 247, 249));
        list.setFixedCellHeight(-1);
        list.setCellRenderer(new EntryCardRenderer());
        MouseAdapter hoverListener = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e){
                if(e.getClickCount()==2){ openSelected(); }
            }
            @Override public void mouseExited(MouseEvent e) {
                clearHoverIndex();
            }
            @Override public void mouseMoved(MouseEvent e) {
                updateHoverFromPoint(e.getPoint());
            }
        };
        list.addMouseListener(hoverListener);
        list.addMouseMotionListener(hoverListener);
        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int idx = list.getSelectedIndex();
            if (idx < 0 || idx >= model.size()) return;
            EntryRow row = model.get(idx);
            if (row != null && row.isHeader()) {
                int next = findNextFileIndex(idx);
                if (next >= 0) {
                    list.setSelectedIndex(next);
                } else {
                    int prev = findPrevFileIndex(idx);
                    if (prev >= 0) list.setSelectedIndex(prev);
                    else list.clearSelection();
                }
            }
            updateSelectionSweepState();
            updateDashedBorderState();
        });
        listScroll = new JScrollPane(list);
        try {
            JScrollBar vbar = listScroll.getVerticalScrollBar();
            vbar.setUI(new ModernScrollBarUI());
            vbar.setPreferredSize(new Dimension(12, Integer.MAX_VALUE));
            vbar.setOpaque(false);
            vbar.setUnitIncrement(16);
            JScrollBar hbar = listScroll.getHorizontalScrollBar();
            hbar.setUI(new ModernScrollBarUI());
            hbar.setPreferredSize(new Dimension(Integer.MAX_VALUE, 12));
            hbar.setOpaque(false);
        } catch (Throwable ignored) {}
        centerCardLayout = new CardLayout();
        centerCardPanel = new JPanel(centerCardLayout);
        centerCardPanel.setOpaque(false);
        centerCardPanel.add(listScroll, CENTER_CARD_LIST);
        calendarPanel = new CalendarEntriesPanel();
        centerCardPanel.add(calendarPanel, CENTER_CARD_CALENDAR);
        add(centerCardPanel, BorderLayout.CENTER);
        syncCenterViewMode();
        // Prioritize metadata for visible items on scroll/resize
        try {
            listScroll.getVerticalScrollBar().addAdjustmentListener(e -> {
                if (viewMode == EntryViewMode.CALENDAR) return;
                ensureLazyRowsForViewport();
                if (!e.getValueIsAdjusting()) {
                    ensureMetaForVisibleRange();
                    ensurePreviewForVisibleRange();
                }
            });
        } catch (Throwable t) {
            logWarn("Failed to attach scroll listener", t);
        }

        // Configure debouncers
        listUpdateDebounce.setRepeats(false);
        watchDebounce.setRepeats(false);
        lazyAppendTimer.setRepeats(true);
        reorderAnimTimer.setRepeats(true);

        loadFiles();
        update();
        // After initial layout, compute meta for visible items first
        SwingUtilities.invokeLater(() -> {
            ensureMetaForVisibleRange();
            ensurePreviewForVisibleRange();
        });
    }

    private ToolbarIconButton createViewModeButton(String iconId, String tooltip, EntryViewMode mode) {
        ToolbarIconButton button = new ToolbarIconButton(iconId);
        Dimension size = new Dimension(42, 38);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMaximumSize(size);
        button.setToolTipText(tooltip);
        button.addActionListener(e -> setViewMode(mode));
        return button;
    }

    private void setViewMode(EntryViewMode mode) {
        EntryViewMode next = mode == null ? EntryViewMode.COMFORT : mode;
        if (viewMode == next) return;
        viewMode = next;
        applyViewModeSelectionState();
        persistViewMode(next);
        list.putClientProperty("entryViewMode", viewMode);
        syncCenterViewMode();
        list.revalidate();
        list.repaint();
    }

    private EntryViewMode loadPersistedViewMode() {
        try {
            SettingsStore store = SettingsStore.get();
            String raw = store.getValue(viewModeSettingsKey(), EntryViewMode.COMFORT.name());
            if (raw == null || raw.isBlank()) return EntryViewMode.COMFORT;
            return EntryViewMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Throwable ignored) {
            return EntryViewMode.COMFORT;
        }
    }

    private void persistViewMode(EntryViewMode mode) {
        if (mode == null) return;
        try {
            SettingsStore store = SettingsStore.get();
            store.setValue(viewModeSettingsKey(), mode.name());
            store.save();
        } catch (Throwable ignored) {}
    }

    private String viewModeSettingsKey() {
        String id = "";
        try {
            if (nb != null && nb.getFolder() != null) {
                id = nb.getFolder().getAbsolutePath();
            } else if (nb != null && nb.getName() != null) {
                id = nb.getName();
            }
        } catch (Throwable ignored) {}
        String hash = Integer.toHexString((id == null ? "" : id.toLowerCase(Locale.ROOT)).hashCode());
        return VIEW_MODE_PREF_KEY_PREFIX + hash;
    }

    private void applyViewModeSelectionState() {
        if (comfortViewBtn != null) comfortViewBtn.setSelected(viewMode == EntryViewMode.COMFORT);
        if (calendarViewBtn != null) calendarViewBtn.setSelected(viewMode == EntryViewMode.CALENDAR);
    }

    private void syncCenterViewMode() {
        if (centerCardLayout == null || centerCardPanel == null) return;
        if (viewMode == EntryViewMode.CALENDAR) {
            centerCardLayout.show(centerCardPanel, CENTER_CARD_CALENDAR);
            if (calendarPanel != null) {
                calendarPanel.setEntries(lastOrderedFiles);
            }
        } else {
            centerCardLayout.show(centerCardPanel, CENTER_CARD_LIST);
        }
    }

    private void loadFiles(){
        if (disposed) return;
        // Cancel any ongoing metadata load
        if (metaLoader != null && !metaLoader.isDone()) {
            metaLoader.cancel(true);
        }

        File folder = nb.getFolder();
        try { AppDirectories.restoreMacScopedAccess(AppDirectories.getRoot()); } catch (Throwable ignored) {}
        try { MacSecurityBookmarkStore.ensureAccess(folder); } catch (Throwable ignored) {}
        java.util.Set<String> exts = app.getEditorFactory().getRegisteredExtensions();
        
        // Build extensions string for native call (e.g. ".txt,.md,.rtf")
        String extString = String.join(",", exts);
        
        // Try native directory listing first for maximum performance
        File[] arr = null;
        String nativeResult = main.infrastructure.ffi.NativeAccess.fsListFiltered(
            folder.getAbsolutePath(), extString, false);
        
        if (nativeResult != null && !nativeResult.isEmpty()) {
            // Parse native result: type|mtime|size|name\n
            java.util.List<File> files = new java.util.ArrayList<>();
            for (String line : nativeResult.split("\n")) {
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\|", 4);
                if (parts.length >= 4 && "f".equals(parts[0])) {
                    // Only include files, not directories
                    files.add(new File(folder, parts[3]));
                }
            }
            arr = files.toArray(new File[0]);
        }
        
        // Fallback to NIO if native failed
        if (arr == null) {
            try {
                java.nio.file.Path folderPath = folder.toPath();
                if (java.nio.file.Files.isDirectory(folderPath)) {
                    try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.list(folderPath)) {
                        arr = stream
                            .filter(p -> {
                                String name = p.getFileName().toString();
                                if (name.startsWith(".")) return false;
                                String s = name.toLowerCase();
                                int dot = s.lastIndexOf('.');
                                String ext = dot >= 0 ? s.substring(dot) : "";
                                return exts.contains(ext);
                            })
                            .map(java.nio.file.Path::toFile)
                            .toArray(File[]::new);
                    }
                }
            } catch (Exception e) {
                arr = null;
            }
        }
        
        // Final fallback to File.listFiles
        if (arr == null) {
            arr = folder.listFiles((d,name)->{
                if (name.startsWith(".")) return false;
                String s = name.toLowerCase();
                int dot = s.lastIndexOf('.');
                String ext = dot>=0 ? s.substring(dot) : "";
                return exts.contains(ext);
            });
        }
        if (arr != null) {
            allFiles = new ArrayList<>(Arrays.asList(arr));
            java.util.Set<File> current = new java.util.HashSet<>(allFiles);
            // Prune caches for removed files but preserve known titles to avoid flicker
            titles.keySet().retainAll(current);
            wordCounts.keySet().retainAll(current);
            moodValues.keySet().retainAll(current);
            entryDates.keySet().retainAll(current);
            entryTimestamps.keySet().retainAll(current);
            metaComputed.retainAll(current);
            synchronized (metaQueued) { metaQueued.retainAll(current); }
            metaCache.keySet().retainAll(current);
            previewCache.keySet().retainAll(current);
            moodTrendCache.keySet().retainAll(current);
            encryptedFlags.keySet().retainAll(current);
            rowIndexByFile.keySet().retainAll(current);

            // Seed provisional values for new files
            java.util.Map<File, MetaSnapshot> refreshedCache = new java.util.HashMap<>();
            for (File f : allFiles) {
                MetaSnapshot snap = metaCache.get(f);
                if (snap != null && snap.isFresh(f)) {
                    titles.put(f, snap.title);
                    wordCounts.put(f, snap.wordCount);
                    moodValues.put(f, snap.mood);
                    metaComputed.add(f);
                    refreshedCache.put(f, snap);
                } else {
                    metaComputed.remove(f);
                    synchronized (metaQueued) { metaQueued.remove(f); }
                    encryptedFlags.remove(f);
                }
                if (!titles.containsKey(f)) {
                    String nm = f.getName();
                    int dot = nm.lastIndexOf('.');
                    titles.put(f, (dot > 0 ? nm.substring(0, dot) : nm));
                }
                if (!wordCounts.containsKey(f)) wordCounts.put(f, 0);
                if (!moodValues.containsKey(f)) moodValues.put(f, -1);
                if (!entryTimestamps.containsKey(f)) entryTimestamps.put(f, entrySortTimestamp(f));
                if (!entryDates.containsKey(f)) entryDates.put(f, resolveEntryDate(entryTimestamps.get(f)));
            }
            metaCache.clear();
            metaCache.putAll(refreshedCache);
            lastOrderedFiles = java.util.Collections.emptyList();
            fullRows = java.util.Collections.emptyList();
            loadedRows = 0;
            lazyAppendTargetRows = -1;
            lazySignature = "";
            try { lazyAppendTimer.stop(); } catch (Throwable ignored) {}
        } else {
            allFiles = new ArrayList<>();
            titles.clear();
            wordCounts.clear();
            moodValues.clear();
            entryDates.clear();
            entryTimestamps.clear();
            metaComputed.clear();
            metaQueued.clear();
            metaCache.clear();
            previewCache.clear();
            moodTrendCache.clear();
            encryptedFlags.clear();
            rowIndexByFile.clear();
            lastOrderedFiles = java.util.Collections.emptyList();
            fullRows = java.util.Collections.emptyList();
            loadedRows = 0;
            lazyAppendTargetRows = -1;
            lazySignature = "";
            try { lazyAppendTimer.stop(); } catch (Throwable ignored) {}
        }
    }

    private void update(){
        if (disposed) return;
        // Debounce model rebuilds
        listUpdateDebounce.restart();
    }

    private void applyFilterSort(){
        if (disposed) return;
        // Preserve selection and scroll to minimize visible twitch
        File sel = getSelectedFile();
        int scrollVal = 0;
        try { scrollVal = listScroll.getVerticalScrollBar().getValue(); } catch (Throwable ignored) {}

        String q = searchField.getText()==null? "" : searchField.getText().toLowerCase();
        boolean hasQuery = !q.isEmpty();
        List<File> filtered = new ArrayList<>(allFiles.size());
        for (File f : allFiles) {
            if (hasQuery) {
                String name = f.getName().toLowerCase();
                String title = java.util.Objects.toString(titles.get(f), f.getName()).toLowerCase();
                boolean textMatch = NativeAccess.searchContains(name, q) || NativeAccess.searchContains(title, q);
                if (!textMatch) continue;
            }
            // Apply date filter if set
            if (filterStartDate != null || filterEndDate != null) {
                java.time.LocalDate fileDate = java.time.Instant.ofEpochMilli(f.lastModified())
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                if (filterStartDate != null && fileDate.isBefore(filterStartDate)) continue;
                if (filterEndDate != null && fileDate.isAfter(filterEndDate)) continue;
            }
            filtered.add(f);
        }
        Comparator<File> withinDate;
        boolean dateDesc = true;
        switch (sortBox.getSelectedIndex()) {
            case 0 -> {
                dateDesc = true;
                withinDate = Comparator.comparingLong(this::entrySortTimestampCached)
                    .reversed()
                    .thenComparing(f -> f.getName().toLowerCase(java.util.Locale.ROOT));
            }
            case 1 -> {
                dateDesc = false;
                withinDate = Comparator.comparingLong(this::entrySortTimestampCached)
                    .thenComparing(f -> f.getName().toLowerCase(java.util.Locale.ROOT));
            }
            case 2 -> withinDate = Comparator
                    .comparing((File fl)-> java.util.Objects.toString(titles.get(fl), fl.getName()), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(f -> f.getName().toLowerCase(java.util.Locale.ROOT));
            case 3 -> withinDate = Comparator
                    .comparing((File fl)-> java.util.Objects.toString(titles.get(fl), fl.getName()), String.CASE_INSENSITIVE_ORDER)
                    .reversed()
                    .thenComparing(f -> f.getName().toLowerCase(java.util.Locale.ROOT));
            case 4 -> withinDate = Comparator
                    .comparingInt((File f)->wordCounts.getOrDefault(f,0))
                    .reversed()
                    .thenComparing(f -> f.getName().toLowerCase(java.util.Locale.ROOT));
            case 5 -> withinDate = Comparator
                    .comparingInt((File f)->wordCounts.getOrDefault(f,0))
                    .thenComparing(f -> f.getName().toLowerCase(java.util.Locale.ROOT));
            default -> withinDate = Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER);
        }
        List<File> ordered = orderByDateGroups(filtered, dateDesc, withinDate);
        lastOrderedFiles = java.util.List.copyOf(ordered);
        if (calendarPanel != null) {
            calendarPanel.setEntries(lastOrderedFiles);
        }
        rebuildMoodTrendCache(ordered);
        String rawQuery = searchField.getText() == null ? "" : searchField.getText().trim();
        list.putClientProperty("searchQuery", rawQuery);
        List<EntryRow> rows = buildGroupedRows(ordered);
        String nextSignature = sortBox.getSelectedIndex()
                + "|" + rawQuery.toLowerCase(Locale.ROOT)
                + "|" + (filterStartDate != null ? filterStartDate : "-")
                + "|" + (filterEndDate != null ? filterEndDate : "-");
        boolean lazyMode = shouldUseLazyLoading(ordered.size());
        if (!lazyMode) {
            fullRows = rows;
            loadedRows = rows.size();
            lazyAppendTargetRows = loadedRows;
            lazySignature = nextSignature;
        } else {
            boolean signatureChanged = !Objects.equals(lazySignature, nextSignature);
            fullRows = rows;
            if (signatureChanged || loadedRows <= 0) {
                loadedRows = Math.min(rows.size(), LAZY_INITIAL_ROWS);
                lazySignature = nextSignature;
            } else {
                loadedRows = Math.min(loadedRows, rows.size());
            }
            if (sel != null) {
                int selectedRow = indexOfFileRow(rows, sel);
                if (selectedRow >= 0 && selectedRow >= loadedRows) {
                    loadedRows = Math.min(rows.size(), selectedRow + LAZY_PREFETCH_ROWS);
                }
            }
            lazyAppendTargetRows = loadedRows;
            rows = rows.subList(0, loadedRows);
        }
        // If order hasn't changed, skip rebuild to avoid flicker
        boolean sameOrder = (model.size() == rows.size());
        if (sameOrder) {
            for (int i = 0; i < model.size(); i++) {
                if (!Objects.equals(model.get(i), rows.get(i))) { sameOrder = false; break; }
            }
        }
        if (!sameOrder) {
            java.util.Set<File> changed = updateModelWithMinimalChanges(rows);
            bumpReorderAnimation(changed);
        }
        rebuildRowIndexCache();

        // Restore selection without forcing scroll
        if (sel != null && ordered.contains(sel)) {
            selectFile(sel, false);
        }
        updateSelectionSweepState();
        // Restore approximate scroll position
        try {
            JScrollBar bar = listScroll.getVerticalScrollBar();
            bar.setValue(Math.min(scrollVal, Math.max(0, bar.getMaximum() - bar.getVisibleAmount())));
        } catch (Throwable ignored) {}

        // After resort/filter, make sure visible items are prioritized for list views.
        if (viewMode != EntryViewMode.CALENDAR) {
            ensureMetaForVisibleRange();
            ensurePreviewForVisibleRange();
            ensureLazyRowsForViewport();
        }
    }

    private void rebuildMoodTrendCache(List<File> orderedFiles) {
        if (orderedFiles == null || orderedFiles.isEmpty()) {
            moodTrendCache.clear();
            return;
        }

        java.util.Set<File> keep = new java.util.HashSet<>(orderedFiles);
        moodTrendCache.keySet().retainAll(keep);

        List<File> timeline = new ArrayList<>(orderedFiles);
        timeline.sort(Comparator.comparingLong(this::entrySortTimestampCached));

        for (int i = 0; i < timeline.size(); i++) {
            File anchor = timeline.get(i);
            if (anchor == null) continue;
            int start = Math.max(0, i - 6);
            java.util.List<Integer> samples = new ArrayList<>(7);
            for (int j = start; j <= i; j++) {
                File point = timeline.get(j);
                int mood = moodValues.getOrDefault(point, -1);
                if (mood >= 0) samples.add(Math.max(0, Math.min(100, mood)));
            }
            if (samples.isEmpty()) {
                int mood = moodValues.getOrDefault(anchor, -1);
                if (mood >= 0) {
                    samples.add(Math.max(0, Math.min(100, mood)));
                }
            }
            int[] arr = new int[samples.size()];
            for (int k = 0; k < samples.size(); k++) arr[k] = samples.get(k);
            moodTrendCache.put(anchor, arr);
        }
    }

    // Returns set of files that were inserted or moved
    private java.util.Set<File> updateModelWithMinimalChanges(java.util.List<EntryRow> target){
        java.util.Set<File> changed = new java.util.HashSet<>();
        int i = 0;
        while (i < target.size()) {
            EntryRow desired = target.get(i);
            if (i < model.size() && java.util.Objects.equals(model.get(i), desired)) {
                i++;
                continue;
            }
            int existingIdx = indexOfInModel(desired, i+1);
            if (existingIdx >= 0) {
                // Move existing item to new position
                model.remove(existingIdx);
                model.add(i, desired);
                if (!desired.isHeader()) changed.add(desired.file);
            } else {
                // Insert new item
                model.add(i, desired);
                if (!desired.isHeader()) changed.add(desired.file);
            }
            i++;
        }
        // Remove trailing extras
        while (model.size() > target.size()) {
            model.remove(model.size()-1);
        }
        return changed;
    }

    private int indexOfInModel(EntryRow row, int start){
        for (int i = Math.max(0, start); i < model.size(); i++) {
            if (java.util.Objects.equals(model.get(i), row)) return i;
        }
        return -1;
    }

    private boolean shouldUseLazyLoading(int fileCount) {
        return fileCount > LAZY_FILE_THRESHOLD;
    }

    private int indexOfFileRow(List<EntryRow> rows, File file) {
        if (rows == null || file == null) return -1;
        for (int i = 0; i < rows.size(); i++) {
            EntryRow row = rows.get(i);
            if (row != null && !row.isHeader() && Objects.equals(row.file, file)) {
                return i;
            }
        }
        return -1;
    }

    private void ensureLazyRowsForViewport() {
        if (disposed) return;
        if (fullRows == null || fullRows.isEmpty()) return;
        if (loadedRows >= fullRows.size()) return;
        int lastVisible = list.getLastVisibleIndex();
        if (lastVisible < 0) lastVisible = model.size() - 1;
        if (lastVisible < 0) return;
        if ((loadedRows - lastVisible) > LAZY_PREFETCH_ROWS) return;
        int target = Math.min(fullRows.size(), loadedRows + LAZY_PAGE_ROWS);
        scheduleLazyAppend(target);
    }

    private void scheduleLazyAppend(int targetCount) {
        if (disposed || fullRows == null || fullRows.isEmpty()) return;
        int bounded = Math.max(0, Math.min(targetCount, fullRows.size()));
        if (bounded <= loadedRows) return;
        lazyAppendTargetRows = Math.max(lazyAppendTargetRows, bounded);
        if (!lazyAppendTimer.isRunning()) {
            try { lazyAppendTimer.start(); } catch (Throwable ignored) {}
        }
    }

    private void appendRowsTo(int targetCount) {
        if (disposed) return;
        if (fullRows == null || fullRows.isEmpty()) return;
        int bounded = Math.max(0, Math.min(targetCount, fullRows.size()));
        if (bounded <= loadedRows) return;
        java.util.Set<File> changed = updateModelWithMinimalChanges(fullRows.subList(0, bounded));
        loadedRows = bounded;
        if (lazyAppendTargetRows < loadedRows) lazyAppendTargetRows = loadedRows;
        rebuildRowIndexCache();
        if (!changed.isEmpty()) {
            bumpReorderAnimation(changed);
        }
        ensureMetaForVisibleRange();
        ensurePreviewForVisibleRange();
    }

    private void rebuildRowIndexCache() {
        rowIndexByFile.clear();
        for (int i = 0; i < model.size(); i++) {
            EntryRow row = model.get(i);
            if (row != null && !row.isHeader() && row.file != null) {
                rowIndexByFile.put(row.file, i);
            }
        }
    }

    private int rowIndexForFile(File file) {
        if (file == null) return -1;
        Integer idx = rowIndexByFile.get(file);
        if (idx != null && idx >= 0 && idx < model.size()) {
            EntryRow row = model.get(idx);
            if (row != null && !row.isHeader() && Objects.equals(row.file, file)) {
                return idx;
            }
        }
        for (int i = 0; i < model.size(); i++) {
            EntryRow row = model.get(i);
            if (row != null && !row.isHeader() && Objects.equals(row.file, file)) {
                rowIndexByFile.put(file, i);
                return i;
            }
        }
        rowIndexByFile.remove(file);
        return -1;
    }

    private void repaintRow(int index, int padding) {
        if (index < 0 || index >= model.size()) return;
        java.awt.Rectangle bounds = list.getCellBounds(index, index);
        if (bounds == null) return;
        int pad = Math.max(0, padding);
        int x = Math.max(0, bounds.x - pad);
        int y = Math.max(0, bounds.y - pad);
        int w = bounds.width + (pad * 2);
        int h = bounds.height + (pad * 2);
        list.repaint(x, y, w, h);
    }

    private void repaintFileRow(File file, int padding) {
        repaintRow(rowIndexForFile(file), padding);
    }

    private void repaintFileRows(List<File> files, int padding) {
        if (files == null || files.isEmpty()) return;
        int pad = Math.max(0, padding);
        java.util.List<java.awt.Rectangle> dirtyRows = new ArrayList<>();
        for (File file : files) {
            int index = rowIndexForFile(file);
            if (index < 0 || index >= model.size()) continue;
            java.awt.Rectangle bounds = list.getCellBounds(index, index);
            if (bounds == null) continue;
            dirtyRows.add(new java.awt.Rectangle(
                    Math.max(0, bounds.x - pad),
                    Math.max(0, bounds.y - pad),
                    bounds.width + (pad * 2),
                    bounds.height + (pad * 2)));
        }
        if (dirtyRows.isEmpty()) return;
        dirtyRows.sort(Comparator.comparingInt(r -> r.y));
        java.awt.Rectangle merged = dirtyRows.get(0);
        for (int i = 1; i < dirtyRows.size(); i++) {
            java.awt.Rectangle next = dirtyRows.get(i);
            if (next.y <= merged.y + merged.height + pad) {
                merged = merged.union(next);
            } else {
                list.repaint(merged.x, merged.y, merged.width, merged.height);
                merged = next;
            }
        }
        list.repaint(merged.x, merged.y, merged.width, merged.height);
    }

    private void bumpReorderAnimation(java.util.Set<File> files){
        if (files == null || files.isEmpty()) return;
        for (File f : files) reorderAnimProgress.put(f, 1f);
        repaintFileRows(new ArrayList<>(files), 16);
        if (!reorderAnimTimer.isRunning()) {
            try { reorderAnimTimer.start(); } catch (Throwable ignored) {}
        }
    }

    private void createNew(){
        app.openNewEntryEditor(nb);
    }

    private void deleteSelected(){
        File f = getSelectedFile();
        if(f==null) return;
        String title = java.util.Objects.toString(titles.get(f), f.getName());
        boolean ok = CustomConfirmDialog.confirm(this, "Delete Entry", "Delete entry '"+title+"'?");
        if(!ok) return;
        if(!f.exists()){
            loadFiles(); update();
            return;
        }
        // Start delete animation
        pendingDeleteFile = f;
        deleteAnimProgress.put(f, 0f);
        list.putClientProperty("deleteAnim", deleteAnimProgress);
        if (!deleteAnimTimer.isRunning()) {
            deleteAnimTimer.start();
        }
        repaintFileRow(f, 24);
    }
    
    private void performActualDelete(File f) {
        if (f == null) {
            refreshImmediate();
            return;
        }
        try { app.closeEditorsForFile(f); } catch (Throwable ignored) {}
        Map<String, Set<Integer>> moodKeys = resolveMoodLogKeys(f);
        String title = java.util.Objects.toString(titles.get(f), f.getName());
        if (!f.exists()) {
            cleanupDeletedEntry(f, moodKeys);
            refreshImmediate();
            return;
        }
        // Use robust deletion with verification and retries
        boolean deleted = main.infrastructure.io.FileIO.deleteWithVerify(f.toPath());
        if(!deleted){
            // Try move to trash as fallback
            deleted = main.infrastructure.io.FileIO.moveToTrash(f.toPath());
        }
        if(!deleted){
            JOptionPane.showMessageDialog(this, "Could not delete '"+title+"'. The file may be in use.", "Delete Failed", JOptionPane.ERROR_MESSAGE);
            main.ui.components.toast.ToastOverlay.error("Failed to delete entry");
        } else {
            cleanupDeletedEntry(f, moodKeys);
            main.ui.components.toast.ToastOverlay.success("Entry deleted");
        }
        // Use immediate refresh for instant UI update after deletion
        refreshImmediate();
    }

    private void cleanupDeletedEntry(File file, Map<String, Set<Integer>> moodKeys) {
        cleanupEntryCaches(file);
        cleanupEntryArtifacts(file);
        cleanupMoodLogEntries(moodKeys);
        clearLastOpenedMetadata(file);
    }

    private void cleanupEntryCaches(File file) {
        if (file == null) return;
        titles.remove(file);
        wordCounts.remove(file);
        moodValues.remove(file);
        entryDates.remove(file);
        entryTimestamps.remove(file);
        metaCache.remove(file);
        previewCache.remove(file);
        encryptedFlags.remove(file);
        rowIndexByFile.remove(file);
        metaComputed.remove(file);
        synchronized (metaQueued) {
            metaQueued.remove(file);
        }
        deleteAnimProgress.remove(file);
        reorderAnimProgress.remove(file);
    }

    private void cleanupEntryArtifacts(File file) {
        if (file == null) return;
        try {
            EntryHistoryManager.deleteHistory(file);
        } catch (Throwable ignored) {}
        try {
            NativeAutosaveCoordinator.cleanupArtifacts(file.getAbsolutePath());
        } catch (Throwable ignored) {}
        cleanupLegacyRecoveryFiles(file);
        cleanupTempCopies(file);
    }

    private void cleanupLegacyRecoveryFiles(File file) {
        if (file == null) return;
        File parent = file.getParentFile();
        if (parent == null) return;
        File recoveryDir = new File(parent, ".recovery");
        File rec = new File(recoveryDir, file.getName() + ".recover");
        try {
            FileIO.deleteWithVerify(rec.toPath());
        } catch (Throwable ignored) {}
    }

    private void cleanupTempCopies(File file) {
        if (file == null) return;
        File parent = file.getParentFile();
        if (parent == null) return;
        String base = file.getName() + ".tmp";
        File[] temps = parent.listFiles((dir, name) -> name.startsWith(base) && name.endsWith(".tmp"));
        if (temps == null) return;
        for (File tmp : temps) {
            if (tmp == null) continue;
            try {
                FileIO.deleteWithVerify(tmp.toPath());
            } catch (Throwable ignored) {}
        }
    }

    private Map<String, Set<Integer>> resolveMoodLogKeys(File file) {
        Map<String, Set<Integer>> keys = new HashMap<>();
        if (nb == null || nb.getType() != NotebookInfo.Type.JOURNAL) return keys;

        MoodMeta meta = readEntryMoodMeta(file);
        if (meta != null) {
            addMoodKey(keys, meta.savedAt, meta.mood);
        }

        String filenameStamp = extractFilenameTimestamp(file);
        int mood = meta != null ? meta.mood : moodValues.getOrDefault(file, -1);
        if (filenameStamp != null && mood >= 0) {
            addMoodKey(keys, filenameStamp, mood);
        }

        try {
            for (EntryHistoryManager.Snapshot snap : EntryHistoryManager.listSnapshots(file)) {
                MoodMeta snapMeta = readEntryMoodMeta(snap.file);
                if (snapMeta != null) {
                    addMoodKey(keys, snapMeta.savedAt, snapMeta.mood);
                }
            }
        } catch (Throwable ignored) {}

        return keys;
    }

    private void addMoodKey(Map<String, Set<Integer>> keys, long savedAt, int mood) {
        String stamp = formatMoodTimestamp(savedAt);
        if (stamp == null) return;
        addMoodKey(keys, stamp, mood);
    }

    private void addMoodKey(Map<String, Set<Integer>> keys, String stamp, int mood) {
        if (stamp == null || stamp.isBlank() || mood < 0) return;
        keys.computeIfAbsent(stamp, k -> new HashSet<>()).add(mood);
    }

    private String formatMoodTimestamp(long savedAt) {
        if (savedAt <= 0) return null;
        try {
            return ENTRY_TS_FORMAT.format(Instant.ofEpochMilli(savedAt).atZone(ZoneId.systemDefault()));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String extractFilenameTimestamp(File file) {
        if (file == null) return null;
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        if (base.length() != 15 || base.charAt(8) != '_') return null;
        for (int i = 0; i < base.length(); i++) {
            if (i == 8) continue;
            char c = base.charAt(i);
            if (c < '0' || c > '9') return null;
        }
        return base;
    }

    private MoodMeta readEntryMoodMeta(File file) {
        if (file == null || !file.exists()) return null;
        try {
            if (EncryptionManager.isEncrypted(file)) {
                EncryptedMetadata.Meta meta = EncryptionManager.readMetadata(file);
                if (meta == null) return null;
                return new MoodMeta(meta.savedAt, meta.mood);
            }
            try (BufferedReader br = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                String first = br.readLine();
                EntryFileFormat.EntryMeta meta = EntryFileFormat.parseHeader(first);
                if (meta == null) return null;
                return new MoodMeta(meta.savedAt, meta.mood);
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void cleanupMoodLogEntries(Map<String, Set<Integer>> moodKeys) {
        if (moodKeys == null || moodKeys.isEmpty()) return;
        try {
            MoodFile.removeRecordsByTimestampAndValue(moodKeys);
        } catch (Throwable ignored) {}
    }

    private void clearLastOpenedMetadata(File file) {
        if (file == null) return;
        try {
            SettingsStore store = SettingsStore.get();
            String last = store.getLastOpenedFilePath();
            if (last != null && !last.isBlank() && file.equals(new File(last))) {
                store.setLastOpenedFilePath(null);
                store.save();
            }
        } catch (Throwable ignored) {}
    }

    private static final class MoodMeta {
        final long savedAt;
        final int mood;

        private MoodMeta(long savedAt, int mood) {
            this.savedAt = savedAt;
            this.mood = mood;
        }
    }

    private void deleteNotebook(){
        boolean ok = CustomConfirmDialog.confirm(this, "Delete Notebook", "Delete entire notebook '"+nb.getName()+"'?" );
        if(!ok) return;
        try { app.closeEditorsInFolder(nb.getFolder()); } catch (Throwable ignored) {}
        // Access store to delete
        new main.core.service.NotebookStore().delete(nb);
        main.ui.components.toast.ToastOverlay.success("Notebook deleted");
        clearLastOpenedMetadataInFolder(nb.getFolder());
        // refresh manager panel
        app.refreshNotebookManager();
        app.switchCard(JournalApp.NOTEBOOK_MANAGER);
    }

    private void clearLastOpenedMetadataInFolder(File folder) {
        if (folder == null) return;
        try {
            SettingsStore store = SettingsStore.get();
            String last = store.getLastOpenedFilePath();
            if (last == null || last.isBlank()) return;
            String root = folder.getAbsolutePath();
            String path = new File(last).getAbsolutePath();
            if (path.equals(root) || path.startsWith(root + File.separator)) {
                store.setLastOpenedFilePath(null);
                store.save();
            }
        } catch (Throwable ignored) {}
    }
    
    private java.time.LocalDate filterStartDate = null;
    private java.time.LocalDate filterEndDate = null;
    
    private void showDateFilter() {
        // Create a popup with date range picker
        javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();
        popup.setLayout(new java.awt.BorderLayout());
        popup.setBorder(BorderFactory.createEmptyBorder());
        
        JPanel content = new JPanel();
        content.setLayout(new java.awt.GridBagLayout());
        content.setBackground(Color.WHITE);
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        
        java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
        gbc.insets = new java.awt.Insets(4, 4, 4, 4);
        gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        
        // Title
        JLabel title = new JLabel("Filter by Date Range");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        content.add(title, gbc);
        
        // From date
        gbc.gridwidth = 1; gbc.gridy = 1; gbc.gridx = 0;
        content.add(new JLabel("From:"), gbc);
        ModernDatePicker fromPicker = new ModernDatePicker(filterStartDate);
        gbc.gridx = 1;
        content.add(fromPicker, gbc);
        
        // To date
        gbc.gridy = 2; gbc.gridx = 0;
        content.add(new JLabel("To:"), gbc);
        ModernDatePicker toPicker = new ModernDatePicker(filterEndDate);
        gbc.gridx = 1;
        content.add(toPicker, gbc);
        
        // Buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        
        ToolbarMenuIconButton clearBtn = new ToolbarMenuIconButton("", "delete");
        clearBtn.setToolTipText("Clear filter");
        clearBtn.addActionListener(e -> {
            filterStartDate = null;
            filterEndDate = null;
            popup.setVisible(false);
            update();
        });
        
        ToolbarMenuIconButton applyBtn = new ToolbarMenuIconButton("", "check");
        applyBtn.setToolTipText("Apply filter");
        applyBtn.addActionListener(e -> {
            filterStartDate = fromPicker.getSelectedDate();
            filterEndDate = toPicker.getSelectedDate();
            popup.setVisible(false);
            update();
        });
        
        buttons.add(clearBtn);
        buttons.add(applyBtn);
        gbc.gridy = 3; gbc.gridx = 0; gbc.gridwidth = 2;
        content.add(buttons, gbc);
        
        popup.add(content);
        popup.show(this, 200, 40);
    }

    private void openSelected(){ File f = getSelectedFile(); if(f!=null) openFile(f); }

    private void openFile(File f){
        app.openExistingEntryEditor(nb, f);
    }

    private int calculateWordCount(File f){
        // Skip expensive counting for very large files to keep UI responsive
        if (f.length() > 1_500_000L) {
            return 0;
        }
        if (EncryptionManager.isEncrypted(f)) {
            EncryptedMetadata.Meta meta = EncryptionManager.readMetadata(f);
            if (meta != null && meta.words >= 0) return meta.words;
            return 0;
        }
        // Try native word count first (faster, memory efficient)
        int nativeCount = main.infrastructure.ffi.NativeAccess.countWordsFile(f.getAbsolutePath());
        if (nativeCount >= 0) return nativeCount;
        
        // Java fallback
        int count = 0;
        try(Scanner sc=new Scanner(f)){
            while(sc.hasNext()){
                sc.next(); count++;
            }
        }catch(Exception ex){
            logWarn("Word count failed for " + f.getName(), ex);
        }
        return count;
    }

    private String extractTitle(File f){
        return extractTitleAndMood(f).title;
    }

    private TitleMood extractTitleAndMood(File f){
        if (f == null) return new TitleMood("", -1);
        if (EncryptionManager.isEncrypted(f)) {
            EncryptedMetadata.Meta meta = EncryptionManager.readMetadata(f);
            String title = meta != null && meta.title != null ? meta.title.trim() : "";
            int mood = meta != null ? meta.mood : -1;
            return new TitleMood(title, mood);
        }
        String nm = f.getName();
        String lower = nm.toLowerCase();
        if(lower.endsWith(".note")||lower.endsWith(".poem")||lower.endsWith(".txt")||lower.endsWith(".rtf")||lower.endsWith(".ntk")){
            // Read header for mood + title (first line only)
            try(BufferedReader br = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)){
                String first = br.readLine();
                if (first != null) {
                    EntryFileFormat.EntryMeta meta = EntryFileFormat.parseHeader(first);
                    if (meta != null) {
                        String title = meta.title != null ? meta.title.trim() : "";
                        if (title.isBlank()) {
                            String next = br.readLine();
                            if (next != null && next.isBlank()) next = br.readLine();
                            if (next != null && !next.isBlank()) title = next.trim();
                        }
                        return new TitleMood(title, meta.mood);
                    }
                    if (!first.isBlank()) {
                        return new TitleMood(first.trim(), -1);
                    }
                }
            } catch (Exception ignore) {}
            // Fallback to native title extraction
            String nativeTitle = main.infrastructure.ffi.NativeAccess.extractTitle(f.getAbsolutePath());
            if (nativeTitle != null && !nativeTitle.isEmpty()) {
                EntryFileFormat.EntryMeta meta = EntryFileFormat.parseHeader(nativeTitle);
                if (meta != null) {
                    String title = meta.title != null ? meta.title.trim() : "";
                    return new TitleMood(title, meta.mood);
                }
                return new TitleMood(nativeTitle.trim(), -1);
            }
        }
        // fallback: strip extension
        int dot = nm.lastIndexOf('.');
        String fallback = dot>0? nm.substring(0,dot): nm;
        return new TitleMood(fallback, -1);
    }

    /** Reload the file list and update the UI */
    public void refresh(){
        if (disposed) return;
        loadFiles();
        update();
    }

    /** Immediate refresh bypassing debounce - use after critical operations like delete */
    public void refreshImmediate(){
        if (disposed) return;
        // Stop any pending debounced updates
        try { listUpdateDebounce.stop(); } catch (Throwable ignored) {}
        try { watchDebounce.stop(); } catch (Throwable ignored) {}
        // Reload and apply immediately
        loadFiles();
        applyFilterSort();
        // Ensure UI is updated on EDT
        SwingUtilities.invokeLater(() -> {
            list.revalidate();
            list.repaint();
        });
    }

    /** Stop background work so the panel can be disposed without hanging the app. */
    public void disposeResources(){
        disposed = true;
        stopWatching();
        try { searchDebounce.stop(); } catch (Throwable ignored) {}
        try { listUpdateDebounce.stop(); } catch (Throwable ignored) {}
        try { watchDebounce.stop(); } catch (Throwable ignored) {}
        try { reorderAnimTimer.stop(); } catch (Throwable ignored) {}
        try { deleteAnimTimer.stop(); } catch (Throwable ignored) {}
        try { selectionSweepTimer.stop(); } catch (Throwable ignored) {}
        try { dashedBorderTimer.stop(); } catch (Throwable ignored) {}
        try {
            if (metaLoader != null && !metaLoader.isDone()) {
                metaLoader.cancel(true);
            }
        } catch (Throwable ignored) {}
        metaLoader = null;
        try {
            if (previewLoader != null && !previewLoader.isDone()) {
                previewLoader.cancel(true);
            }
        } catch (Throwable ignored) {}
        previewLoader = null;
        
        // Clear all cached data to free memory
        synchronized (metaQueued) { metaQueued.clear(); }
        metaComputed.clear();
        metaCache.clear();
        wordCounts.clear();
        titles.clear();
        allFiles.clear();
        lastOrderedFiles = java.util.Collections.emptyList();
        reorderAnimProgress.clear();
        deleteAnimProgress.clear();
        pendingDeleteFile = null;
        try { lazyAppendTimer.stop(); } catch (Throwable ignored) {}
        model.clear();
        previewCache.clear();
        encryptedFlags.clear();
        rowIndexByFile.clear();
        fullRows = java.util.Collections.emptyList();
        loadedRows = 0;
        lazyAppendTargetRows = -1;
        lazySignature = "";
    }
    

    // ensure list refresh when panel becomes visible
    @Override public void addNotify(){
        super.addNotify();
        if (disposed) return;
        startWatching();
        refresh();
        SwingUtilities.invokeLater(() -> {
            ensureMetaForVisibleRange();
            ensurePreviewForVisibleRange();
        });
    }

    @Override public void removeNotify(){
        disposeResources();
        super.removeNotify();
    }

    private void startWatching(){
        if (disposed) return;
        stopWatching();
        try {
            try { AppDirectories.restoreMacScopedAccess(AppDirectories.getRoot()); } catch (Throwable ignored) {}
            try { MacSecurityBookmarkStore.ensureAccess(nb.getFolder()); } catch (Throwable ignored) {}
            Path path = nb.getFolder().toPath();
            watchService = FileSystems.getDefault().newWatchService();
            path.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
            watchRunning = true;
            watchThread = new Thread(() -> {
                while (watchRunning) {
                    try {
                        WatchKey key = watchService.take();
                        for (WatchEvent<?> event : key.pollEvents()) {
                            // Debounce via EDT timer to coalesce rapid saves/changes
                            SwingUtilities.invokeLater(() -> {
                                try { watchDebounce.restart(); } catch (Throwable ignored2) {}
                            });
                        }
                        key.reset();
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception ex) {
                        if (watchRunning) {
                            logWarn("Notebook watch error", ex);
                        }
                    }
                }
            }, "NotebookEntriesWatcher");
            watchThread.setDaemon(true);
            watchThread.start();
        } catch (Exception ex) {
            // If watch service fails, log and continue without auto-refresh
            logWarn("File watch setup failed", ex);
        }
    }

    private void stopWatching(){
        watchRunning = false;
        if (watchThread != null) {
            watchThread.interrupt();
            watchThread = null;
        }
        if (watchService != null) {
            try { watchService.close(); } catch (Exception ex) { logWarn("WatchService close failed", ex); }
            watchService = null;
        }
    }

    // Prioritize metadata loading for currently visible items
    private void ensureMetaForVisibleRange(){
        if (disposed) return;
        try {
            int first = list.getFirstVisibleIndex();
            int last = list.getLastVisibleIndex();
            if (first < 0 || last < 0 || last < first) return;
            java.util.List<File> visible = new java.util.ArrayList<>();
            for (int i = first; i <= last && i < model.size(); i++) {
                EntryRow row = model.get(i);
                if (row != null && !row.isHeader() && row.file != null) {
                    visible.add(row.file);
                }
            }
            if (!visible.isEmpty()) {
                startPrioritizedMetaLoader(visible);
            }
        } catch (Throwable ignored) {}
    }

    private void startPrioritizedMetaLoader(java.util.List<File> preferredFirst){
        if (disposed) return;
        if (preferredFirst == null) preferredFirst = java.util.Collections.emptyList();
        if (preferredFirst.isEmpty()) return;
        // Cancel ongoing worker to re-prioritize
        if (metaLoader != null && !metaLoader.isDone()) {
            metaLoader.cancel(true);
        }
        java.util.LinkedHashSet<File> order = new java.util.LinkedHashSet<>(preferredFirst);
        metaLoader = new SwingWorker<>() {
            @Override protected Void doInBackground() {
                java.util.ArrayList<FileMeta> batch = new java.util.ArrayList<>(META_PUBLISH_BATCH_SIZE);
                for (File f : order) {
                    if (isCancelled()) break;
                    if (f == null || !f.exists()) continue;
                    if (metaComputed.contains(f)) continue;
                    synchronized (metaQueued) {
                        if (metaQueued.contains(f)) continue;
                        metaQueued.add(f);
                    }
                    int wc = calculateWordCount(f);
                    TitleMood tm = extractTitleAndMood(f);
                    String t = tm.title;
                    int mood = tm.mood;
                    batch.add(new FileMeta(f, wc, t, mood));
                    if (batch.size() >= META_PUBLISH_BATCH_SIZE) {
                        publish(batch.toArray(new FileMeta[0]));
                        batch.clear();
                    }
                    if (isCancelled()) break;
                }
                if (!batch.isEmpty() && !isCancelled()) {
                    publish(batch.toArray(new FileMeta[0]));
                }
                return null;
            }
            @Override protected void process(java.util.List<FileMeta> chunks) {
                java.util.List<File> changedFiles = new ArrayList<>(chunks.size());
                for (FileMeta m : chunks) {
                    if (m == null || m.file == null || !m.file.exists()) continue;
                    titles.put(m.file, m.title);
                    wordCounts.put(m.file, m.wc);
                    moodValues.put(m.file, m.mood);
                    metaComputed.add(m.file);
                    metaCache.put(m.file, new MetaSnapshot(
                            m.file.lastModified(),
                            m.file.length(),
                            m.wc,
                            m.title,
                            m.mood
                    ));
                    changedFiles.add(m.file);
                }
                if (changedFiles.isEmpty()) return;
                repaintFileRows(changedFiles, 12);
                if (viewMode == EntryViewMode.CALENDAR && calendarPanel != null) {
                    calendarPanel.onMetadataUpdated(changedFiles);
                }
                if (shouldRefreshOrderingForMetadata()) {
                    update();
                }
            }
        };
        metaLoader.execute();
    }

    private boolean shouldRefreshOrderingForMetadata() {
        int sortIdx = sortBox.getSelectedIndex();
        if (sortIdx >= 2) return true;
        String q = searchField.getText();
        return q != null && !q.isBlank();
    }

    // --- Helpers ---
    static String humanReadableSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = ("KMGTPE").charAt(exp - 1) + "";
        return String.format(java.util.Locale.US, "%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private static void logWarn(String msg, Throwable t){
        System.err.println("[NotebookEntriesPanel] " + msg + (t != null ? " (" + t.getClass().getSimpleName() + ": " + t.getMessage() + ")" : ""));
        if (t != null) t.printStackTrace(System.err);
    }

    private List<EntryRow> buildGroupedRows(List<File> files) {
        List<EntryRow> rows = new ArrayList<>();
        if (files == null || files.isEmpty()) return rows;
        LocalDate current = null;
        for (File f : files) {
            LocalDate date = entryDates.getOrDefault(f, resolveEntryDate(f));
            if (current == null || !current.equals(date)) {
                current = date;
                rows.add(EntryRow.header(date, formatEntryDate(date)));
            }
            rows.add(EntryRow.file(f));
        }
        return rows;
    }

    private List<File> orderByDateGroups(List<File> files, boolean dateDesc, Comparator<File> withinDate) {
        if (files == null || files.isEmpty()) return java.util.Collections.emptyList();
        Map<LocalDate, List<File>> grouped = new HashMap<>();
        for (File f : files) {
            LocalDate date = entryDates.getOrDefault(f, resolveEntryDate(f));
            grouped.computeIfAbsent(date, k -> new ArrayList<>()).add(f);
        }
        List<LocalDate> dates = new ArrayList<>(grouped.keySet());
        dates.sort(dateDesc ? Comparator.reverseOrder() : Comparator.naturalOrder());
        List<File> ordered = new ArrayList<>(files.size());
        for (LocalDate date : dates) {
            List<File> group = grouped.get(date);
            if (group == null) continue;
            group.sort(withinDate);
            ordered.addAll(group);
        }
        return ordered;
    }

    private File getSelectedFile() {
        EntryRow row = list.getSelectedValue();
        if (row == null || row.isHeader()) return null;
        return row.file;
    }

    private void selectFile(File file, boolean scroll) {
        if (file == null) return;
        for (int i = 0; i < model.size(); i++) {
            EntryRow row = model.get(i);
            if (row != null && !row.isHeader() && Objects.equals(file, row.file)) {
                list.setSelectedIndex(i);
                if (scroll) list.ensureIndexIsVisible(i);
                return;
            }
        }
    }

    private void updateSelectionSweepState() {
        boolean active = hoverIndex >= 0;
        if (active) {
            if (!selectionSweepTimer.isRunning()) selectionSweepTimer.start();
        } else {
            if (selectionSweepTimer.isRunning()) selectionSweepTimer.stop();
            selectionSweepPhase[0] = 0f;
        }
    }
    
    private void updateDashedBorderState() {
        int selectedIndex = list.getSelectedIndex();
        boolean hasSelection = selectedIndex >= 0;
        if (hasSelection) {
            if (dashedAnimatedIndex != selectedIndex) {
                repaintRow(dashedAnimatedIndex, 8);
                dashedAnimatedIndex = selectedIndex;
                repaintRow(dashedAnimatedIndex, 8);
            }
            if (!dashedBorderTimer.isRunning()) dashedBorderTimer.start();
        } else {
            if (dashedBorderTimer.isRunning()) dashedBorderTimer.stop();
            dashedBorderPhase[0] = 0f;
            repaintRow(dashedAnimatedIndex, 8);
            dashedAnimatedIndex = -1;
        }
    }

    private void updateHoverFromPoint(java.awt.Point p) {
        int idx = list.locationToIndex(p);
        if (idx < 0 || idx >= model.size()) {
            clearHoverIndex();
            return;
        }
        java.awt.Rectangle bounds = list.getCellBounds(idx, idx);
        if (bounds == null || !bounds.contains(p)) {
            clearHoverIndex();
            return;
        }
        EntryRow row = model.get(idx);
        if (row == null || row.isHeader() || row.file == null) {
            clearHoverIndex();
            return;
        }
        if (idx == hoverIndex && Objects.equals(row.file, hoverFile)) return;
        int oldHoverIndex = hoverIndex;
        hoverIndex = idx;
        hoverFile = row.file;
        list.putClientProperty("hoverIndex", hoverIndex);
        updateSelectionSweepState();
        repaintRow(oldHoverIndex, 12);
        repaintRow(hoverIndex, 12);
    }

    private void clearHoverIndex() {
        if (hoverIndex < 0) return;
        int oldHoverIndex = hoverIndex;
        hoverIndex = -1;
        hoverFile = null;
        list.putClientProperty("hoverIndex", -1);
        updateSelectionSweepState();
        repaintRow(oldHoverIndex, 12);
    }

    private void ensurePreviewForVisibleRange() {
        if (disposed) return;
        try {
            int first = list.getFirstVisibleIndex();
            int last = list.getLastVisibleIndex();
            if (first < 0 || last < 0 || last < first) return;
            java.util.List<File> visible = new java.util.ArrayList<>();
            for (int i = first; i <= last && i < model.size(); i++) {
                EntryRow row = model.get(i);
                if (row != null && !row.isHeader() && row.file != null) {
                    PreviewSnapshot snap = previewCache.get(row.file);
                    if (snap == null || !snap.isFresh(row.file)) {
                        visible.add(row.file);
                    }
                }
            }
            if (!visible.isEmpty()) {
                startPreviewLoader(visible);
            }
        } catch (Throwable ignored) {}
    }

    private void startPreviewLoader(java.util.List<File> preferredFirst) {
        if (disposed) return;
        if (preferredFirst == null) preferredFirst = java.util.Collections.emptyList();
        if (preferredFirst.isEmpty()) return;
        if (previewLoader != null && !previewLoader.isDone()) {
            previewLoader.cancel(true);
        }
        java.util.LinkedHashSet<File> order = new java.util.LinkedHashSet<>(preferredFirst);
        previewLoader = new SwingWorker<>() {
            @Override protected Void doInBackground() {
                for (File f : order) {
                    if (isCancelled()) break;
                    if (f == null || !f.exists()) continue;
                    PreviewSnapshot snap = previewCache.get(f);
                    if (snap != null && snap.isFresh(f)) continue;
                    PreviewSnapshot computed = buildPreviewSnapshot(f);
                    if (computed != null) publish(computed);
                    if (isCancelled()) break;
                }
                return null;
            }
            @Override protected void process(java.util.List<PreviewSnapshot> chunks) {
                java.util.List<File> changedFiles = new ArrayList<>(chunks.size());
                for (PreviewSnapshot snap : chunks) {
                    if (snap == null || snap.file == null) continue;
                    previewCache.put(snap.file, snap);
                    changedFiles.add(snap.file);
                }
                repaintFileRows(changedFiles, 12);
            }
        };
        previewLoader.execute();
    }

    private PreviewSnapshot buildPreviewSnapshot(File f) {
        if (f == null || !f.exists()) return null;
        String title = titles.getOrDefault(f, f.getName());
        if (EncryptionManager.isEncrypted(f)) {
            return new PreviewSnapshot(f, f.lastModified(), f.length(), title, "Encrypted entry.");
        }
        String snippet = "";
        try {
            byte[] raw = FileIO.readAllBytes(f.toPath());
            snippet = buildPreviewSnippet(raw, f.getName());
        } catch (IOException ignored) {
            snippet = "";
        }
        return new PreviewSnapshot(f, f.lastModified(), f.length(), title, snippet);
    }

    private static String buildPreviewSnippet(byte[] raw, String name) throws IOException {
        if (raw == null || raw.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(raw), StandardCharsets.UTF_8))) {
            String first = br.readLine();
            if (first == null) return "";
            EntryFileFormat.EntryMeta meta = EntryFileFormat.parseHeader(first);
            if (meta != null) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            } else {
                // Skip title line (and optional blank line)
                String line = br.readLine();
                if (line != null && !line.isBlank()) {
                    sb.append(line).append('\n');
                }
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
        }
        String body = stripImageManifest(sb.toString());
        String plain = rtfToPlain(body);
        plain = stripGuidedMarkers(plain);
        plain = stripImageTokens(plain);
        String collapsed = collapseSpaces(plain);
        return trimSnippet(collapsed, PREVIEW_MAX_CHARS);
    }

    private static String stripImageManifest(String body) {
        if (body == null) return "";
        String trimmed = body.stripLeading();
        if (trimmed.startsWith("IMGMAP:")) {
            int nl = trimmed.indexOf('\n');
            if (nl >= 0) return trimmed.substring(nl + 1).stripLeading();
            return "";
        }
        return body;
    }

    private static String rtfToPlain(String text) {
        if (text == null) return "";
        String trimmed = text.stripLeading();
        if (!trimmed.startsWith("{\\rtf")) return text;
        try {
            RTFEditorKit kit = new RTFEditorKit();
            StyledDocument doc = (StyledDocument) kit.createDefaultDocument();
            kit.read(new ByteArrayInputStream(trimmed.getBytes(StandardCharsets.UTF_8)), doc, 0);
            return doc.getText(0, doc.getLength());
        } catch (Exception e) {
            return text;
        }
    }

    private static String stripGuidedMarkers(String text) {
        if (text == null || text.isBlank()) return "";
        StringBuilder out = new StringBuilder(text.length());
        try (BufferedReader br = new BufferedReader(new StringReader(text))) {
            String line;
            while ((line = br.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("[GUIDED_MODE:")) continue;
                if (trimmed.startsWith("[Q") && trimmed.endsWith("]")) continue;
                out.append(line).append('\n');
            }
        } catch (IOException ignored) {}
        return out.toString();
    }

    private static String stripImageTokens(String text) {
        if (text == null || text.isBlank()) return "";
        return text.replaceAll("\\[\\[IMG\\|[^\\]]+\\]\\]", " ");
    }

    private static String collapseSpaces(String text) {
        if (text == null || text.isEmpty()) return "";
        String nativeCollapsed = NativeAccess.patternCollapseSpaces(text);
        if (nativeCollapsed != null) return nativeCollapsed;
        return text.replaceAll("\\s+", " ").trim();
    }

    private static String trimSnippet(String text, int max) {
        if (text == null || text.isBlank()) return "";
        if (text.length() <= max) return text;
        return text.substring(0, max - 3).trim() + "...";
    }

    private int findNextFileIndex(int start) {
        for (int i = Math.max(0, start + 1); i < model.size(); i++) {
            EntryRow row = model.get(i);
            if (row != null && !row.isHeader()) return i;
        }
        return -1;
    }

    private int findPrevFileIndex(int start) {
        for (int i = Math.min(model.size() - 1, start - 1); i >= 0; i--) {
            EntryRow row = model.get(i);
            if (row != null && !row.isHeader()) return i;
        }
        return -1;
    }

    private static LocalDate resolveEntryDate(File file) {
        long ts = entrySortTimestamp(file);
        return resolveEntryDate(ts);
    }

    private static LocalDate resolveEntryDate(long timestamp) {
        return Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate();
    }

    private long entrySortTimestampCached(File file) {
        if (file == null) return System.currentTimeMillis();
        Long cached = entryTimestamps.get(file);
        if (cached != null) return cached;
        long ts = entrySortTimestamp(file);
        entryTimestamps.put(file, ts);
        return ts;
    }

    private static long entrySortTimestamp(File file) {
        if (file == null) return System.currentTimeMillis();
        // Use creation date from filename (yyyyMMdd_HHmmss) so entries stay at their original date
        long parsed = parseTimestampFromName(file.getName());
        if (parsed > 0) return parsed;
        // Fallback to last modified if filename doesn't match expected format
        return file.lastModified();
    }

    private static String formatEntryDate(LocalDate date) {
        if (date == null) return "";
        return ENTRY_DATE_FORMAT.format(date);
    }

    private static long parseTimestampFromName(String name) {
        if (name == null) return -1L;
        String base = name;
        int dot = name.lastIndexOf('.');
        if (dot > 0) base = name.substring(0, dot);
        if (base.length() != 15 || base.charAt(8) != '_') return -1L;
        try {
            LocalDateTime dt = LocalDateTime.parse(base, ENTRY_TS_FORMAT);
            return dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (RuntimeException ignored) {
            return -1L;
        }
    }

    private static Color moodColorAt(int mood) {
        if (mood < 0) {
            return new Color(102, 168, 255);
        }
        float v = Math.max(0f, Math.min(100f, mood)) / 100f;
        Color cool = new Color(0, 122, 204);
        Color mid = new Color(200, 200, 200);
        Color warm = new Color(255, 120, 50);
        if (v <= 0.5f) {
            return lerp(cool, mid, v / 0.5f);
        }
        return lerp(mid, warm, (v - 0.5f) / 0.5f);
    }

    private static Color lerp(Color a, Color b, float t) {
        float clamped = Math.max(0f, Math.min(1f, t));
        int r = Math.round(a.getRed() + (b.getRed() - a.getRed()) * clamped);
        int g = Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * clamped);
        int bl = Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * clamped);
        int alpha = Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * clamped);
        return new Color(r, g, bl, alpha);
    }
}
