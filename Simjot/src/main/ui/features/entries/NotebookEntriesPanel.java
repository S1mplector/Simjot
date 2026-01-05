/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.entries;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.stream.Collectors;

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
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.StyledDocument;
import javax.swing.text.rtf.RTFEditorKit;

import main.core.security.EncryptionManager;
import main.core.security.crypto.EncryptedMetadata;
import main.infrastructure.backup.NotebookInfo;
import main.infrastructure.ffi.NativeAccess;
import main.infrastructure.io.FileIO;
import main.infrastructure.io.ResourceLoader;
import main.ui.app.JournalApp;
import main.ui.components.buttons.ToolbarMenuIconButton;
import main.ui.components.combobox.ModernComboBoxUI;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.datepicker.ModernDatePicker;
import main.ui.components.input.AeroTextField;
import main.ui.components.scrollbar.ModernScrollBarUI;
import main.ui.dialog.confirmation.CustomConfirmDialog;
import main.ui.theme.aero.AeroTheme;

public class NotebookEntriesPanel extends JPanel {
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

    private final java.util.Map<File,Integer> wordCounts = new java.util.HashMap<>();
    private final java.util.Map<File,String> titles = new java.util.HashMap<>();
    private final java.util.Map<File,Integer> moodValues = new java.util.HashMap<>();
    private final java.util.Map<File, MetaSnapshot> metaCache = new java.util.HashMap<>();
    private final java.util.Map<File, PreviewSnapshot> previewCache = new java.util.HashMap<>();
    private List<File> allFiles = new ArrayList<>();
    private SwingWorker<Void, PreviewSnapshot> previewLoader;

    // Debounced search and background metadata loader
    private final javax.swing.Timer searchDebounce = new javax.swing.Timer(100, e -> update());
    private SwingWorker<Void, FileMeta> metaLoader;

    // Debounced list rebuild to avoid frequent model churn on metadata updates
    private final javax.swing.Timer listUpdateDebounce = new javax.swing.Timer(50, e -> applyFilterSort());
    // Track which files have computed metadata and which are enqueued
    private final java.util.Set<File> metaComputed = new java.util.HashSet<>();
    private final java.util.Set<File> metaQueued = new java.util.HashSet<>();
    private javax.swing.JScrollPane listScroll;

    // Debounced folder watch refresh to coalesce rapid file system events
    private final javax.swing.Timer watchDebounce = new javax.swing.Timer(100, e -> refresh());

    // Lightweight animation when items are reordered (fade highlight)
    private final java.util.Map<File, Float> reorderAnimProgress = new java.util.HashMap<>();
    
    // Delete animation state: file -> progress (0=start, 1=gone)
    private final java.util.Map<File, Float> deleteAnimProgress = new java.util.HashMap<>();
    private File pendingDeleteFile = null;
    private final javax.swing.Timer deleteAnimTimer = new javax.swing.Timer(16, e -> {
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
                list.repaint();
            }
        } catch (Throwable ignored) {}
    });
    
    private final javax.swing.Timer reorderAnimTimer = new javax.swing.Timer(16, e -> {
        try {
            java.util.List<File> toRemove = new java.util.ArrayList<>();
            for (java.util.Map.Entry<File, Float> en : reorderAnimProgress.entrySet()) {
                Float fv = en.getValue();
                float v = (fv == null ? 0f : fv.floatValue());
                v *= 0.88f; // exponential decay
                if (v < 0.06f) {
                    toRemove.add(en.getKey());
                } else {
                    en.setValue(v);
                }
            }
            for (File f : toRemove) reorderAnimProgress.remove(f);
            if (reorderAnimProgress.isEmpty()) {
                ((javax.swing.Timer) e.getSource()).stop();
            }
            list.repaint();
        } catch (Throwable ignored) {}
    });

    private final float[] selectionSweepPhase = new float[]{0f};
    private final javax.swing.Timer selectionSweepTimer = new javax.swing.Timer(33, e -> {
        try {
            selectionSweepPhase[0] += 0.018f;
            if (selectionSweepPhase[0] > 1f) selectionSweepPhase[0] -= 1f;
            list.repaint();
        } catch (Throwable ignored) {}
    });

    // Folder watch
    private WatchService watchService;
    private Thread watchThread;
    private volatile boolean watchRunning;
    private volatile boolean disposed = false;
    private int hoverIndex = -1;
    private File hoverFile = null;
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
        private final JTextArea snippet = new JTextArea();
        private final Color cardBg = new Color(252, 253, 255);
        private final Color cardBorder = new Color(190, 200, 214);
        private final Color metaColor = new Color(105, 110, 120);
        private final Color accent = new Color(88, 133, 255);
        private final Color selectedBg = new Color(236, 244, 255);
        private final Color selectedBorder = new Color(110, 160, 255);
        private boolean selected;
        private float reorderGlow = 0f;
        private float deleteProgress = 0f; // 0=normal, 1=fully gone
        private int moodValue = -1;
        private float selectionSweepPhase = 0f;
        private boolean hovered = false;
        private final DateDividerRenderer divider = new DateDividerRenderer();

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
            // Apply a small blur kernel for softness
            float[] kernel = {
                    1f/16, 2f/16, 1f/16,
                    2f/16, 4f/16, 2f/16,
                    1f/16, 2f/16, 1f/16
            };
            ConvolveOp op = new ConvolveOp(new Kernel(3,3,kernel), ConvolveOp.EDGE_NO_OP, null);
            BufferedImage blurred = new BufferedImage(cropped.getWidth(), cropped.getHeight(), BufferedImage.TYPE_INT_ARGB);
            op.filter(cropped, blurred);
            BG_CACHE.put(key, blurred);
            return blurred;
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
            snippet.setLineWrap(true);
            snippet.setWrapStyleWord(true);
            snippet.setEditable(false);
            snippet.setOpaque(false);
            snippet.setFocusable(false);
            snippet.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
            snippet.setRows(2);
            
            content.add(titleWrapper);
            content.add(snippet);
            add(content, BorderLayout.CENTER);
            
            // Right stats panel: vertically stacked stats
            JPanel statsPanel = new JPanel();
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
            setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 12));
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
            @SuppressWarnings("unchecked") Map<File,Integer> moods = (Map<File,Integer>) list.getClientProperty("moods");
            @SuppressWarnings("unchecked") Map<File,Float> reorderAnim = (Map<File,Float>) list.getClientProperty("reorderAnim");
            @SuppressWarnings("unchecked") Map<File,Float> deleteAnim = (Map<File,Float>) list.getClientProperty("deleteAnim");
            @SuppressWarnings("unchecked") Map<File, PreviewSnapshot> previews = (Map<File, PreviewSnapshot>) list.getClientProperty("previews");
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
            moodValue = file != null && moods != null ? moods.getOrDefault(file, -1) : -1;
            Object phaseObj = list.getClientProperty("selectionSweepPhase");
            if (phaseObj instanceof float[] arr && arr.length > 0) {
                selectionSweepPhase = arr[0];
            } else {
                selectionSweepPhase = 0f;
            }
            Object hoverObj = list.getClientProperty("hoverIndex");
            int hoverIdx = hoverObj instanceof Integer ? (Integer) hoverObj : -1;
            hovered = (hoverIdx == index);

            // Created from filename if matches yyyyMMdd_HHmmss, else fallback to modified
            Date created = file != null ? resolveCreatedDate(file) : new Date();
            Date modified = file != null ? new Date(file.lastModified()) : new Date();

            String displayTitle = (t==null||t.isBlank()) ? fallback : t;
            title.setText(displayTitle);
            SimpleDateFormat df = new SimpleDateFormat("dd/MM/yy HH:mm");
            String size = file != null ? NotebookEntriesPanel.humanReadableSize(file.length()) : "-";
            
            // Update individual stat labels
            sizeLabel.setText(size);
            wordsLabel.setText(wc + " words");
            createdLabel.setText("Created " + df.format(created));
            editedLabel.setText("Edited " + df.format(modified));
            PreviewSnapshot snap = (file != null && previews != null) ? previews.get(file) : null;
            String previewText = snap != null ? snap.snippet : "";
            snippet.setText(previewText == null ? "" : previewText);
            this.selected = isSelected;
            setPreferredSize(new Dimension(1, 108));
            return this;
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

            // Accent bar
            Color base = moodColorAt(moodValue);
            Color accentTop = shiftColor(base, 0.18f);
            Color accentBottom = shiftColor(base, -0.22f);
            g2.setPaint(new GradientPaint(0, 8, accentTop, 0, h - 8, accentBottom));
            g2.fillRoundRect(6, 9, 6, h - 18, 6, 6);

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
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class DateDividerRenderer extends JComponent {
        private static final int HEIGHT = 64;
        private String label = "";

        private DateDividerRenderer() {
            setOpaque(false);
            setFont(resolveClusterFont(16f));
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

            FontMetrics fm = g2.getFontMetrics(getFont());
            int centerX = w / 2;
            int lineY = h / 2 + 8;
            int padX = 16;

            String text = elideText(label, fm, Math.max(0, w - padX * 2 - 120));
            int textW = text.isEmpty() ? 0 : fm.stringWidth(text);
            int innerGap = textW > 0 ? (textW / 2 + 14) : 16;
            int leftLineEnd = centerX - innerGap;
            int rightLineStart = centerX + innerGap;

            Color line = new Color(60, 60, 60, 170);
            g2.setColor(line);
            g2.setStroke(new java.awt.BasicStroke(1.6f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            int lineStart = padX;
            int lineEnd = w - padX;
            if (leftLineEnd > lineStart + 4) {
                g2.drawLine(lineStart, lineY, leftLineEnd, lineY);
            }
            if (rightLineStart < lineEnd - 4) {
                g2.drawLine(rightLineStart, lineY, lineEnd, lineY);
            }

            int capW = 12;
            int capH = 4;
            if (leftLineEnd > lineStart + 4) {
                g2.fillRoundRect(lineStart - capW / 2, lineY - capH / 2, capW, capH, capH, capH);
            }
            if (rightLineStart < lineEnd - 4) {
                g2.fillRoundRect(lineEnd - capW / 2, lineY - capH / 2, capW, capH, capH, capH);
            }

            int diamond = 10;
            java.awt.geom.Path2D diamondShape = new java.awt.geom.Path2D.Float();
            diamondShape.moveTo(centerX, lineY - diamond / 2f);
            diamondShape.lineTo(centerX + diamond / 2f, lineY);
            diamondShape.lineTo(centerX, lineY + diamond / 2f);
            diamondShape.lineTo(centerX - diamond / 2f, lineY);
            diamondShape.closePath();
            g2.fill(diamondShape);

            int leafW = 8;
            int leafH = 3;
            int leafOffset = diamond / 2 + 8;
            g2.fillRoundRect(centerX - leafOffset - leafW / 2, lineY - leafH / 2, leafW, leafH, leafH, leafH);
            g2.fillRoundRect(centerX + leafOffset - leafW / 2, lineY - leafH / 2, leafW, leafH, leafH, leafH);

            if (!text.isEmpty()) {
                int textX = centerX - textW / 2;
                int textY = lineY - 10;
                if (textY < fm.getAscent()) textY = fm.getAscent();
                if (textY > h - fm.getDescent()) textY = h - fm.getDescent();
                g2.setColor(getForeground());
                g2.setFont(getFont());
                g2.drawString(text, textX, textY);
            }

            g2.dispose();
        }

        private static String elideText(String input, FontMetrics fm, int maxWidth) {
            if (input == null || input.isEmpty()) return "";
            if (maxWidth <= 0) return "";
            if (fm.stringWidth(input) <= maxWidth) return input;
            String ellipsis = "...";
            int max = input.length();
            while (max > 0 && fm.stringWidth(input.substring(0, max) + ellipsis) > maxWidth) {
                max--;
            }
            if (max <= 0) return "";
            return input.substring(0, max).trim() + ellipsis;
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

    public NotebookEntriesPanel(JournalApp app, NotebookInfo nb){
        this.app = app; this.nb = nb;
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

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
        top.add(newBtn); top.add(deleteBtn); top.add(delNbBtn);
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
        list.putClientProperty("reorderAnim", reorderAnimProgress);
        list.putClientProperty("selectionSweepPhase", selectionSweepPhase);
        list.putClientProperty("previews", previewCache);
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
        add(listScroll, BorderLayout.CENTER);
        // Prioritize metadata for visible items on scroll/resize
        try {
            listScroll.getVerticalScrollBar().addAdjustmentListener(e -> {
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
        reorderAnimTimer.setRepeats(true);

        loadFiles();
        update();
        // After initial layout, compute meta for visible items first
        SwingUtilities.invokeLater(() -> {
            ensureMetaForVisibleRange();
            ensurePreviewForVisibleRange();
        });
    }

    private void loadFiles(){
        if (disposed) return;
        // Cancel any ongoing metadata load
        if (metaLoader != null && !metaLoader.isDone()) {
            metaLoader.cancel(true);
        }

        File folder = nb.getFolder();
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
            metaComputed.retainAll(current);
            synchronized (metaQueued) { metaQueued.retainAll(current); }
            metaCache.keySet().retainAll(current);
            previewCache.keySet().retainAll(current);

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
                }
                if (!titles.containsKey(f)) {
                    String nm = f.getName();
                    int dot = nm.lastIndexOf('.');
                    titles.put(f, (dot > 0 ? nm.substring(0, dot) : nm));
                }
                if (!wordCounts.containsKey(f)) wordCounts.put(f, 0);
                if (!moodValues.containsKey(f)) moodValues.put(f, -1);
            }
            metaCache.clear();
            metaCache.putAll(refreshedCache);
            // Start prioritized metadata loading (visible first)
            startPrioritizedMetaLoader(java.util.List.copyOf(allFiles));
        } else {
            allFiles = new ArrayList<>();
            titles.clear();
            wordCounts.clear();
            moodValues.clear();
            metaComputed.clear();
            metaQueued.clear();
            metaCache.clear();
            previewCache.clear();
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
        List<File> filtered = allFiles.stream().filter(f -> {
            String name = f.getName().toLowerCase();
            String title = java.util.Objects.toString(titles.get(f), f.getName()).toLowerCase();
            boolean textMatch = NativeAccess.searchContains(name, q) || NativeAccess.searchContains(title, q);
            if (!textMatch) return false;
            
            // Apply date filter if set
            if (filterStartDate != null || filterEndDate != null) {
                java.time.LocalDate fileDate = java.time.Instant.ofEpochMilli(f.lastModified())
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                if (filterStartDate != null && fileDate.isBefore(filterStartDate)) return false;
                if (filterEndDate != null && fileDate.isAfter(filterEndDate)) return false;
            }
            return true;
        }).collect(Collectors.toList());
        Comparator<File> withinDate;
        boolean dateDesc = true;
        switch (sortBox.getSelectedIndex()) {
            case 0 -> {
                dateDesc = true;
                withinDate = Comparator.comparingLong(NotebookEntriesPanel::entrySortTimestamp)
                    .reversed()
                    .thenComparing(f -> f.getName().toLowerCase(java.util.Locale.ROOT));
            }
            case 1 -> {
                dateDesc = false;
                withinDate = Comparator.comparingLong(NotebookEntriesPanel::entrySortTimestamp)
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
        List<EntryRow> rows = buildGroupedRows(ordered);
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

        // After resort/filter, make sure visible items are prioritized
        ensureMetaForVisibleRange();
        ensurePreviewForVisibleRange();
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

    private void bumpReorderAnimation(java.util.Set<File> files){
        if (files == null || files.isEmpty()) return;
        for (File f : files) reorderAnimProgress.put(f, 1f);
        list.repaint();
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
        list.repaint();
    }
    
    private void performActualDelete(File f) {
        if (f == null || !f.exists()) {
            refreshImmediate();
            return;
        }
        String title = java.util.Objects.toString(titles.get(f), f.getName());
        // Use robust deletion with verification and retries
        boolean deleted = main.infrastructure.io.FileIO.deleteWithVerify(f.toPath());
        if(!deleted){
            // Try move to trash as fallback
            deleted = main.infrastructure.io.FileIO.moveToTrash(f.toPath());
        }
        if(!deleted){
            JOptionPane.showMessageDialog(this, "Could not delete '"+title+"'. The file may be in use.", "Delete Failed", JOptionPane.ERROR_MESSAGE);
        }
        // Use immediate refresh for instant UI update after deletion
        refreshImmediate();
    }

    private void deleteNotebook(){
        boolean ok = CustomConfirmDialog.confirm(this, "Delete Notebook", "Delete entire notebook '"+nb.getName()+"'?" );
        if(!ok) return;
        // Access store to delete
        new main.core.service.NotebookStore().delete(nb);
        // refresh manager panel
        app.refreshNotebookManager();
        app.switchCard(JournalApp.NOTEBOOK_MANAGER);
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
        reorderAnimProgress.clear();
        deleteAnimProgress.clear();
        pendingDeleteFile = null;
        model.clear();
        previewCache.clear();
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
        // Cancel ongoing worker to re-prioritize
        if (metaLoader != null && !metaLoader.isDone()) {
            metaLoader.cancel(true);
        }
        java.util.LinkedHashSet<File> order = new java.util.LinkedHashSet<>(preferredFirst);
        metaLoader = new SwingWorker<>() {
            @Override protected Void doInBackground() {
                for (File f : order) {
                    if (isCancelled()) break;
                    if (metaComputed.contains(f)) continue;
                    synchronized (metaQueued) {
                        if (metaQueued.contains(f)) continue;
                        metaQueued.add(f);
                    }
                    int wc = calculateWordCount(f);
                    TitleMood tm = extractTitleAndMood(f);
                    String t = tm.title;
                    int mood = tm.mood;
                    publish(new FileMeta(f, wc, t, mood));
                    if (isCancelled()) break;
                }
                return null;
            }
            @Override protected void process(java.util.List<FileMeta> chunks) {
                for (FileMeta m : chunks) {
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
                }
                update();
            }
        };
        metaLoader.execute();
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
            LocalDate date = resolveEntryDate(f);
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
            LocalDate date = resolveEntryDate(f);
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
            list.repaint();
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
        hoverIndex = idx;
        hoverFile = row.file;
        list.putClientProperty("hoverIndex", hoverIndex);
        updateSelectionSweepState();
        list.repaint();
    }

    private void clearHoverIndex() {
        if (hoverIndex < 0) return;
        hoverIndex = -1;
        hoverFile = null;
        list.putClientProperty("hoverIndex", -1);
        updateSelectionSweepState();
        list.repaint();
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
        if (previewLoader != null && !previewLoader.isDone()) {
            previewLoader.cancel(true);
        }
        java.util.LinkedHashSet<File> order = new java.util.LinkedHashSet<>(preferredFirst);
        for (File f : allFiles) order.add(f);
        previewLoader = new SwingWorker<>() {
            @Override protected Void doInBackground() {
                for (File f : order) {
                    if (isCancelled()) break;
                    PreviewSnapshot snap = previewCache.get(f);
                    if (snap != null && snap.isFresh(f)) continue;
                    PreviewSnapshot computed = buildPreviewSnapshot(f);
                    if (computed != null) publish(computed);
                    if (isCancelled()) break;
                }
                return null;
            }
            @Override protected void process(java.util.List<PreviewSnapshot> chunks) {
                for (PreviewSnapshot snap : chunks) {
                    if (snap == null || snap.file == null) continue;
                    previewCache.put(snap.file, snap);
                }
                list.repaint();
            }
        };
        previewLoader.execute();
    }

    private PreviewSnapshot buildPreviewSnapshot(File f) {
        if (f == null) return null;
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
        return Instant.ofEpochMilli(ts)
            .atZone(ZoneId.systemDefault())
            .toLocalDate();
    }

    private static long entrySortTimestamp(File file) {
        if (file == null) return System.currentTimeMillis();
        // Use creation date from filename (yyyyMMdd_HHmmss) so entries stay at their original date
        try {
            String nm = file.getName();
            String base = nm.contains(".") ? nm.substring(0, nm.lastIndexOf('.')) : nm;
            SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd_HHmmss");
            return fmt.parse(base).getTime();
        } catch (Exception ignored) {
            // Fallback to last modified if filename doesn't match expected format
            return file.lastModified();
        }
    }

    private static Date resolveCreatedDate(File file) {
        if (file == null) return new Date();
        Date created = new Date(file.lastModified());
        try {
            String nm = file.getName();
            String base = nm.contains(".") ? nm.substring(0, nm.lastIndexOf('.')) : nm;
            SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd_HHmmss");
            created = fmt.parse(base);
        } catch (Exception ignored) {}
        return created;
    }

    private static String formatEntryDate(LocalDate date) {
        if (date == null) return "";
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy");
        return fmt.format(date);
    }

    private static Font resolveClusterFont(float size) {
        String family = "Zapfino";
        Font f = new Font(family, Font.PLAIN, Math.round(size));
        if (!family.equalsIgnoreCase(f.getFamily())) {
            f = AeroTheme.defaultBoldFont(size);
        }
        return f;
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

    private static Color shiftColor(Color base, float delta) {
        if (base == null) return new Color(120, 120, 120);
        float r = base.getRed() / 255f;
        float g = base.getGreen() / 255f;
        float b = base.getBlue() / 255f;
        r = Math.max(0f, Math.min(1f, r + delta));
        g = Math.max(0f, Math.min(1f, g + delta));
        b = Math.max(0f, Math.min(1f, b + delta));
        return new Color(r, g, b, base.getAlpha() / 255f);
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
