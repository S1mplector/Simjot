/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.features.notebooks;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.SpinnerNumberModel;
import javax.swing.JTextField;

import main.core.service.NotebookStore;
import main.core.service.SettingsStore;
import main.infrastructure.backup.NotebookInfo;
import main.infrastructure.monitoring.AppPerf;
import main.ui.app.JournalApp;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.combobox.ModernComboBoxUI;
import main.ui.components.buttons.ToolbarMenuIconButton;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.containers.RoundedPanel;
import main.ui.components.containers.ShadowedDialogPanel;
import main.ui.components.editor.CustomFontApplier;
import main.ui.components.fields.ModernTextField;
import main.ui.components.fields.TitleDividerField;
import main.ui.components.icons.ImageIconRenderer;
import main.ui.components.scrollbar.ModernScrollBarUI;
import main.ui.components.spinner.ModernSpinnerUI;
import main.ui.dialog.confirmation.CustomConfirmDialog;
import main.ui.dialog.file.SimjotFileChooser;
import main.ui.dialog.input.CustomInputDialog;
import main.ui.dialog.utils.ModernColorPickerDialog;
import main.ui.features.entries.GlobalSearchEngine;
import main.ui.features.entries.NotebookPersonalization;
import main.ui.theme.aero.AeroTheme;

public class NotebookManagerPanel extends JPanel {
    private final NotebookStore store = new NotebookStore();
    private final JPanel gallery = new JPanel();
    private final JournalApp app;
    private ModernTextField searchField;
    private ModernTextField tagField;
    private ModernTextField fromDateField;
    private ModernTextField toDateField;
    private JSpinner moodMin;
    private JSpinner moodMax;
    private JLabel searchStatus;
    private final Timer searchDebounce;
    private SwingWorker<Void, GlobalSearchEngine.SearchResult> searchWorker;
    private final Map<String, Integer> searchHitCounts = new HashMap<>();
    private final Map<String, GlobalSearchEngine.SearchResult> primarySearchHits = new HashMap<>();
    private JToggleButton searchToggle;
    private JComponent searchPanel;
    private JComponent searchContainer;
    private int searchExpandedHeight = 0;
    private int searchAnimHeight = 0;
    private int searchTargetHeight = 0;
    private Timer searchExpandTimer;

    public NotebookManagerPanel(JournalApp app){
        this.app = app;
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        // Top toolbar matching other panels
        JPanel topBar = new FrostedGlassPanel(new BorderLayout(), 16);
        topBar.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        
        // Left side - back button
        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftButtons.setOpaque(false);
        ToolbarMenuIconButton backBtn = new ToolbarMenuIconButton("", "back");
        backBtn.setToolTipText("Back to Main Menu");
        backBtn.addActionListener(e-> app.switchCard(JournalApp.MAIN_MENU));
        leftButtons.add(backBtn);
        
        // Right side - organize button
        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightButtons.setOpaque(false);
        searchToggle = new AccentIconToggleButton("search");
        searchToggle.setToolTipText("Search notebooks");
        searchToggle.addActionListener(e -> setSearchExpanded(searchToggle.isSelected(), true));
        rightButtons.add(searchToggle);
        rightButtons.add(Box.createHorizontalStrut(4));
        ToolbarMenuIconButton organizeBtn = new ToolbarMenuIconButton("", "settings");
        organizeBtn.setToolTipText("Organize Clusters");
        organizeBtn.addActionListener(e -> {
            SimpleClusterOrganizer.show(
                (Frame) SwingUtilities.getWindowAncestor(this),
                this::refresh);
        });
        rightButtons.add(organizeBtn);

        topBar.add(leftButtons, BorderLayout.WEST);
        topBar.add(rightButtons, BorderLayout.EAST);

        JPanel headerStack = new JPanel();
        headerStack.setOpaque(false);
        headerStack.setLayout(new BoxLayout(headerStack, BoxLayout.Y_AXIS));
        headerStack.add(topBar);
        searchPanel = buildSearchPanel();
        searchContainer = buildSearchContainer(searchPanel);
        headerStack.add(searchContainer);

        add(headerStack, BorderLayout.NORTH);

        searchDebounce = new Timer(200, e -> runSearch());
        searchDebounce.setRepeats(false);
        installSearchHandlers();
        setSearchExpanded(false, false);

        gallery.setOpaque(false);
        gallery.setLayout(new BoxLayout(gallery, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(gallery);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        
        // Apply custom scrollbar UI
        try {
            scroll.getVerticalScrollBar().setUI(new ModernScrollBarUI());
            scroll.getVerticalScrollBar().setPreferredSize(new Dimension(12, Integer.MAX_VALUE));
            scroll.getVerticalScrollBar().setOpaque(false);
            scroll.getHorizontalScrollBar().setUI(new ModernScrollBarUI());
            scroll.getHorizontalScrollBar().setPreferredSize(new Dimension(Integer.MAX_VALUE, 12));
            scroll.getHorizontalScrollBar().setOpaque(false);
        } catch (Throwable ignored) {}
        
        add(scroll,BorderLayout.CENTER);

        refresh();
    }

    private JComponent buildSearchPanel() {
        RoundedPanel searchPanel = new RoundedPanel(16);
        searchPanel.setFlat(true);
        searchPanel.setBackground(new Color(245, 247, 250));
        searchPanel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        searchPanel.setLayout(new BorderLayout());

        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));

        RoundedPanel searchBar = new RoundedPanel(12);
        searchBar.setFlat(true);
        searchBar.setBackground(Color.WHITE);
        searchBar.setLayout(new BorderLayout(10, 0));
        searchBar.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        searchField = new ModernTextField(30);
        searchField.setPlaceholder("Search across notebooks");
        searchField.setFont(AeroTheme.defaultFont().deriveFont(14f));
        searchField.setPreferredSize(new Dimension(420, 32));

        searchBar.add(searchField, BorderLayout.CENTER);
        searchBar.add(Box.createHorizontalStrut(8), BorderLayout.EAST);

        RoundedPanel filterCard = new RoundedPanel(12);
        filterCard.setFlat(true);
        filterCard.setBackground(new Color(250, 250, 252));
        filterCard.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        filterCard.setLayout(new java.awt.GridLayout(2, 1, 0, 6));

        JPanel rowA = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        rowA.setOpaque(false);
        rowA.add(makeLabel("Tags"));
        tagField = new ModernTextField(18);
        tagField.setPlaceholder("tag1, tag2");
        rowA.add(tagField);
        rowA.add(makeLabel("From"));
        fromDateField = new ModernTextField(10);
        fromDateField.setPlaceholder("YYYY-MM-DD");
        rowA.add(fromDateField);
        rowA.add(makeLabel("To"));
        toDateField = new ModernTextField(10);
        toDateField.setPlaceholder("YYYY-MM-DD");
        rowA.add(toDateField);

        JPanel rowB = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        rowB.setOpaque(false);
        rowB.add(makeLabel("Mood"));
        moodMin = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
        moodMax = new JSpinner(new SpinnerNumberModel(100, 0, 100, 1));
        moodMin.setUI(new ModernSpinnerUI());
        moodMax.setUI(new ModernSpinnerUI());
        moodMin.setPreferredSize(new Dimension(64, 26));
        moodMax.setPreferredSize(new Dimension(64, 26));
        rowB.add(moodMin);
        rowB.add(makeLabel("to"));
        rowB.add(moodMax);

        filterCard.add(rowA);
        filterCard.add(rowB);

        searchStatus = new JLabel("Type to search.");
        searchStatus.setForeground(new Color(120, 130, 145));
        searchStatus.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 12f));
        JPanel statusRow = new JPanel(new BorderLayout());
        statusRow.setOpaque(false);
        statusRow.add(searchStatus, BorderLayout.EAST);

        stack.add(searchBar);
        stack.add(Box.createVerticalStrut(6));
        stack.add(filterCard);
        stack.add(Box.createVerticalStrut(4));
        stack.add(statusRow);

        searchPanel.add(stack, BorderLayout.CENTER);
        searchExpandedHeight = searchPanel.getPreferredSize().height;
        return searchPanel;
    }

    private JComponent buildSearchContainer(JComponent content) {
        JComponent container = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                Dimension pref = content.getPreferredSize();
                return new Dimension(pref.width, searchAnimHeight);
            }

            @Override
            public Dimension getMinimumSize() {
                return new Dimension(0, searchAnimHeight);
            }

            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, searchAnimHeight);
            }
        };
        container.setOpaque(false);
        container.setAlignmentX(Component.LEFT_ALIGNMENT);
        container.add(content, BorderLayout.CENTER);
        return container;
    }

    private void setSearchExpanded(boolean expanded, boolean animate) {
        if (expanded && searchPanel != null) {
            searchExpandedHeight = searchPanel.getPreferredSize().height;
        }
        int target = expanded ? searchExpandedHeight : 0;
        searchTargetHeight = target;
        if (searchToggle != null && searchToggle.isSelected() != expanded) {
            searchToggle.setSelected(expanded);
        }
        if (!expanded) {
            if (searchWorker != null && !searchWorker.isDone()) {
                searchWorker.cancel(true);
            }
        }
        if (!animate) {
            searchAnimHeight = target;
            if (searchExpandTimer != null) searchExpandTimer.stop();
            updateSearchContainerHeight();
            return;
        }
        if (searchExpandTimer == null) {
            searchExpandTimer = new Timer(AppPerf.getAnimationDelay(), e -> animateSearchHeight());
        }
        if (!searchExpandTimer.isRunning()) {
            searchExpandTimer.start();
        }
        if (expanded && searchField != null) {
            SwingUtilities.invokeLater(() -> searchField.requestFocusInWindow());
        }
    }

    private void animateSearchHeight() {
        int diff = searchTargetHeight - searchAnimHeight;
        if (Math.abs(diff) <= 2) {
            searchAnimHeight = searchTargetHeight;
            updateSearchContainerHeight();
            if (searchExpandTimer != null) searchExpandTimer.stop();
            return;
        }
        int step = Math.max(2, Math.round(Math.abs(diff) * 0.2f));
        if (diff > 0) {
            searchAnimHeight = Math.min(searchTargetHeight, searchAnimHeight + step);
        } else {
            searchAnimHeight = Math.max(searchTargetHeight, searchAnimHeight - step);
        }
        updateSearchContainerHeight();
    }

    private void updateSearchContainerHeight() {
        if (searchContainer != null) {
            searchContainer.revalidate();
            searchContainer.repaint();
        }
        revalidate();
        repaint();
    }

    private void installSearchHandlers() {
        attachDebounce(searchField);
        attachDebounce(tagField);
        attachDebounce(fromDateField);
        attachDebounce(toDateField);
        moodMin.addChangeListener(e -> searchDebounce.restart());
        moodMax.addChangeListener(e -> searchDebounce.restart());

        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    runSearch();
                }
            }
        });
    }

    private void attachDebounce(JTextField field) {
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { searchDebounce.restart(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { searchDebounce.restart(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { searchDebounce.restart(); }
        });
    }

    private void clearSearch() {
        if (searchWorker != null && !searchWorker.isDone()) {
            searchWorker.cancel(true);
        }
        searchField.setText("");
        tagField.setText("");
        fromDateField.setText("");
        toDateField.setText("");
        moodMin.setValue(0);
        moodMax.setValue(100);
        clearSearchResults();
        searchStatus.setText("Type to search.");
    }

    private void clearSearchResults() {
        searchHitCounts.clear();
        primarySearchHits.clear();
        gallery.repaint();
    }

    private void runSearch() {
        if (searchWorker != null && !searchWorker.isDone()) {
            searchWorker.cancel(true);
        }
        clearSearchResults();

        GlobalSearchEngine.SearchQuery query = buildSearchQuery();
        if (query == null) return;
        if (query.isEmpty()) {
            searchStatus.setText("Type to search.");
            return;
        }

        searchStatus.setText("Searching...");
        searchWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                GlobalSearchEngine.search(query, NotebookManagerPanel.this, result -> publish(result), this::isCancelled);
                return null;
            }

            @Override
            protected void process(List<GlobalSearchEngine.SearchResult> chunks) {
                for (GlobalSearchEngine.SearchResult result : chunks) {
                    registerSearchHit(result);
                }
                searchStatus.setText(getTotalSearchHits() + " result(s)");
                gallery.repaint();
            }

            @Override
            protected void done() {
                if (getTotalSearchHits() == 0) {
                    searchStatus.setText("No results.");
                }
            }
        };
        searchWorker.execute();
    }

    private GlobalSearchEngine.SearchQuery buildSearchQuery() {
        String q = searchField.getText() == null ? "" : searchField.getText().trim();
        String tagText = tagField.getText() == null ? "" : tagField.getText().trim();
        String fromRaw = fromDateField.getText();
        String toRaw = toDateField.getText();
        LocalDate fromDate = parseDate(fromRaw);
        LocalDate toDate = parseDate(toRaw);
        if (fromRaw != null && !fromRaw.trim().isEmpty() && fromDate == null) {
            searchStatus.setText("Invalid from date (use YYYY-MM-DD).");
            return null;
        }
        if (toRaw != null && !toRaw.trim().isEmpty() && toDate == null) {
            searchStatus.setText("Invalid to date (use YYYY-MM-DD).");
            return null;
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            LocalDate tmp = fromDate;
            fromDate = toDate;
            toDate = tmp;
            fromDateField.setText(fromDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
            toDateField.setText(toDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
        }
        int minMood = (Integer) moodMin.getValue();
        int maxMood = (Integer) moodMax.getValue();
        if (minMood > maxMood) {
            int tmp = minMood;
            minMood = maxMood;
            maxMood = tmp;
            moodMin.setValue(minMood);
            moodMax.setValue(maxMood);
        }
        return new GlobalSearchEngine.SearchQuery(q, tagText, fromDate, toDate, minMood, maxMood, false);
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(raw.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            return null;
        }
    }

    private void registerSearchHit(GlobalSearchEngine.SearchResult result) {
        if (result == null || result.notebook == null) return;
        String key = notebookKey(result.notebook);
        if (key.isEmpty()) return;
        int next = searchHitCounts.getOrDefault(key, 0) + 1;
        searchHitCounts.put(key, next);
        GlobalSearchEngine.SearchResult current = primarySearchHits.get(key);
        if (current == null || result.savedAt > current.savedAt) {
            primarySearchHits.put(key, result);
        }
    }

    private int getTotalSearchHits() {
        int total = 0;
        for (int count : searchHitCounts.values()) {
            total += count;
        }
        return total;
    }

    private int getSearchHitCount(NotebookInfo nb) {
        if (nb == null) return 0;
        Integer count = searchHitCounts.get(notebookKey(nb));
        return count == null ? 0 : count;
    }

    private GlobalSearchEngine.SearchResult getPrimarySearchHit(NotebookInfo nb) {
        if (nb == null) return null;
        return primarySearchHits.get(notebookKey(nb));
    }

    private static String notebookKey(NotebookInfo nb) {
        if (nb == null || nb.getName() == null) return "";
        return nb.getName().toLowerCase(Locale.ROOT);
    }

    private static JLabel makeLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(AeroTheme.defaultBoldFont(12f));
        label.setForeground(new Color(55, 60, 70));
        return label;
    }

    public void refresh(){
        store.reload();
        gallery.removeAll();
        
        // Display clusters first
        List<String> clusterIds = store.getClusterIds();
        SettingsStore.get().pruneHiddenClusters(clusterIds);
        java.util.Set<String> hiddenClusters = SettingsStore.get().getHiddenClusterIds();
        for (String clusterId : clusterIds) {
            if (hiddenClusters.contains(clusterId)) continue;
            gallery.add(createClusterPanel(clusterId));
            gallery.add(Box.createVerticalStrut(10));
        }
        
        // Display unclustered notebooks
        List<NotebookInfo> unclustered = store.getUnclusteredNotebooks();
        if (!unclustered.isEmpty()) {
            JPanel unclusteredPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 20));
            unclusteredPanel.setOpaque(false);
            unclusteredPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            for (NotebookInfo nb : unclustered) {
                unclusteredPanel.add(createTile(nb));
            }
            unclusteredPanel.add(createAddTile());
            gallery.add(unclusteredPanel);
        } else {
            // Only add tile if no notebooks at all
            JPanel addPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 20));
            addPanel.setOpaque(false);
            addPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            addPanel.add(createAddTile());
            gallery.add(addPanel);
        }
        
        revalidate(); repaint();
    }
    
    private JPanel createClusterPanel(String clusterId) {
        JPanel clusterPanel = new JPanel(new BorderLayout(8, 8));
        clusterPanel.setOpaque(false);
        clusterPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        clusterPanel.setBorder(BorderFactory.createEmptyBorder(6, 12, 10, 12));
        
        // Cluster header with name and actions
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        header.add(new ClusterDivider(clusterId, true), BorderLayout.CENTER);
        
        // Cluster actions - notebook delete icon button
        ToolbarMenuIconButton disbandBtn = new ToolbarMenuIconButton("", "delete_notebook");
        disbandBtn.setToolTipText("Disband cluster");
        disbandBtn.addActionListener(e -> {
            boolean confirm = CustomConfirmDialog.confirm(this, 
                "Disband Cluster",
                "Disband cluster '" + clusterId + "'?<br>Notebooks will become unclustered.");
            if (confirm) {
                store.disbandCluster(clusterId);
                SettingsStore.get().setClusterHidden(clusterId, false);
                SettingsStore.get().save();
                refresh();
            }
        });
        header.add(disbandBtn, BorderLayout.EAST);
        clusterPanel.add(header, BorderLayout.NORTH);
        
        // Notebooks in cluster
        JPanel notebooksFlow = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        notebooksFlow.setOpaque(false);
        List<NotebookInfo> inCluster = store.getNotebooksInCluster(clusterId);
        for (NotebookInfo nb : inCluster) {
            notebooksFlow.add(createTile(nb));
        }
        clusterPanel.add(notebooksFlow, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        footer.add(new ClusterDivider(clusterId, false), BorderLayout.CENTER);
        clusterPanel.add(footer, BorderLayout.SOUTH);
        
        // Make cluster a drop target
        setupClusterDropTarget(clusterPanel, clusterId);
        
        return clusterPanel;
    }

    private static final class ClusterDivider extends JComponent {
        private static final int HEIGHT = 28;
        private final String text;
        private final boolean showText;

        private ClusterDivider(String text, boolean showText) {
            this.text = text == null ? "" : text.trim();
            this.showText = showText;
            setOpaque(false);
            setFont(resolveClusterFont(15f));
            setForeground(new Color(60, 60, 60));
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
            int lineY = h / 2 + 4;
            int padX = 16;

            String label = showText ? text : "";
            int maxTextWidth = Math.max(0, w - padX * 2 - 120);
            if (!label.isEmpty()) {
                label = elideText(label, fm, maxTextWidth);
            }
            int textW = label.isEmpty() ? 0 : fm.stringWidth(label);
            int innerGap = textW > 0 ? (textW / 2 + 14) : 16;
            int leftLineEnd = centerX - innerGap;
            int rightLineStart = centerX + innerGap;

            Color line = new Color(60, 60, 60, 170);
            g2.setColor(line);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
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
            Path2D diamondShape = new Path2D.Float();
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

            if (!label.isEmpty()) {
                int textX = centerX - textW / 2;
                int textY = lineY - 6;
                if (textY < fm.getAscent()) textY = fm.getAscent();
                if (textY > h - fm.getDescent()) textY = h - fm.getDescent();
                g2.setColor(getForeground());
                g2.setFont(getFont());
                g2.drawString(label, textX, textY);
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

    private static final class AccentIconToggleButton extends JToggleButton {
        private final String iconId;

        private AccentIconToggleButton(String iconId) {
            this.iconId = iconId == null ? "" : iconId.toLowerCase();
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setRolloverEnabled(true);
            Dimension d = new Dimension(48, 36);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(new Dimension(72, 40));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            boolean pressed = getModel().isArmed() && getModel().isPressed();
            boolean hover = getModel().isRollover();
            boolean active = isSelected();

            Color top = active ? new Color(255, 235, 205, 230) : new Color(247, 248, 250, 225);
            Color bot = active ? new Color(253, 218, 160, 220) : new Color(229, 232, 238, 220);
            if (pressed) {
                top = top.darker();
                bot = bot.darker();
            }
            RoundRectangle2D plate = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, 10, 10);
            g2.setPaint(new GradientPaint(0, 0, top, 0, h, bot));
            g2.fill(plate);

            g2.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 140),
                    0, h / 2f, new Color(255, 255, 255, 20)));
            g2.fill(new RoundRectangle2D.Float(1, 1, w - 2, h / 2f + 4, 9, 9));

            g2.setColor(new Color(170, 175, 185, 200));
            g2.draw(plate);

            if (active || hover) {
                g2.setColor(new Color(255, 180, 90, active ? 120 : 70));
                g2.draw(new RoundRectangle2D.Float(1.5f, 1.5f, w - 3f, h - 3f, 9, 9));
            }

            int iconSize = Math.min(w, h) - 16;
            int ix = (w - iconSize) / 2;
            int iy = (h - iconSize) / 2;
            String res = ImageIconRenderer.mapIdToResource(iconId);
            if (res != null && ImageIconRenderer.draw(g2, res, ix, iy, iconSize, this, true)) {
                g2.dispose();
                return;
            }

            g2.setColor(new Color(60, 60, 60, 220));
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int r = Math.max(5, iconSize / 3);
            int cx = w / 2 - 2;
            int cy = h / 2 - 2;
            g2.drawOval(cx - r, cy - r, r * 2, r * 2);
            g2.drawLine(cx + r - 1, cy + r - 1, cx + r + 6, cy + r + 6);

            g2.dispose();
        }
    }

    private static Font resolveClusterFont(float size) {
        String family = "Zapfino";
        Font f = new Font(family, Font.PLAIN, Math.round(size));
        if (!family.equalsIgnoreCase(f.getFamily())) {
            f = AeroTheme.defaultBoldFont(size);
        }
        return f;
    }
    
    private void setupClusterDropTarget(JPanel clusterPanel, String clusterId) {
        new DropTarget(clusterPanel, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_MOVE);
                    Transferable t = dtde.getTransferable();
                    if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                        String nbName = (String) t.getTransferData(DataFlavor.stringFlavor);
                        // Find notebook by name and assign to cluster
                        for (NotebookInfo nb : store.list()) {
                            if (nb.getName().equals(nbName)) {
                                store.assignToCluster(nb, clusterId);
                                break;
                            }
                        }
                        refresh();
                    }
                    dtde.dropComplete(true);
                } catch (Exception ex) {
                    dtde.dropComplete(false);
                }
            }
            
            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                clusterPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 4, 0, 0, new Color(100, 149, 237)),
                    BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(100, 149, 237, 100), 2),
                        BorderFactory.createEmptyBorder(6, 10, 6, 10)
                    )
                ));
            }
            
            @Override
            public void dragExit(DropTargetEvent dte) {
                clusterPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 4, 0, 0, new Color(147, 112, 219, 150)),
                    BorderFactory.createEmptyBorder(8, 12, 8, 12)
                ));
            }
        });
    }

    private JPanel createTile(NotebookInfo nb){
        NotebookTile tile = new NotebookTile(nb);
        tile.setPreferredSize(new Dimension(120,120));
        tile.addMouseListener(new MouseAdapter(){
            @Override public void mouseClicked(MouseEvent e){ if(e.getClickCount()==1) openNotebook(nb); }
        });
        return tile;
    }

    private static final int NOTEBOOK_ICON_SIZE = 72;
    private static final Map<String, BufferedImage> CUSTOM_ICON_CACHE = new HashMap<>();

    private static Icon createIcon(NotebookInfo nb){
        return createIcon(nb, false);
    }

    private static Icon createIcon(NotebookInfo nb, boolean withPen){
        // Always draw the notebook line art. User-selected images are cover art
        // clipped into the notebook mask, not replacements for the notebook frame.
        String res = ImageIconRenderer.mapIdToResource(withPen ? "notebook" : "notebook_nopen");
        if (res != null) {
            return ImageIconRenderer.icon(res, NOTEBOOK_ICON_SIZE, true);
        }

        // Fallback: simple placeholder
        return new ImageIcon(createFallbackIcon(NOTEBOOK_ICON_SIZE));
    }

    private static BufferedImage loadCustomIcon(String path) {
        BufferedImage cached = CUSTOM_ICON_CACHE.get(path);
        if (cached != null) return cached;
        try {
            BufferedImage img = javax.imageio.ImageIO.read(new File(path));
            if (img != null) {
                CUSTOM_ICON_CACHE.put(path, img);
            }
            return img;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static BufferedImage createFallbackIcon(int size) {
        BufferedImage canvas = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = canvas.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(200, 200, 200));
        g2.fillRoundRect(0, 0, size, size, 10, 10);
        g2.setColor(new Color(150, 150, 150));
        g2.drawRoundRect(0, 0, size - 1, size - 1, 10, 10);
        g2.dispose();
        return canvas;
    }

    private static final class HiDpiImageIcon implements Icon {
        private final BufferedImage src;
        private final int size;

        private HiDpiImageIcon(BufferedImage src, int size) {
            this.src = src;
            this.size = size;
        }

        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.drawImage(src, x, y, size, size, null);
            g2.dispose();
        }
    }

    private static int countEntries(NotebookInfo nb){
        if(nb==null) return 0;
        File folder = nb.getFolder();
        if(folder==null || !folder.exists()) return 0;
        File[] files = folder.listFiles((d,name)->{
            String s=name.toLowerCase();
            return s.endsWith(".txt")||s.endsWith(".md")||s.endsWith(".rtf")||s.endsWith(".note")||s.endsWith(".poem")||s.endsWith(".ntk");
        });
        return files==null?0:files.length;
    }

    private class NotebookTile extends JPanel implements MouseListener, DragGestureListener, DragSourceListener {
        private final NotebookInfo nb;
        private final DragSource dragSource;
        
        NotebookTile(NotebookInfo nb){
            this.nb = nb;
            setLayout(new BorderLayout());
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(2,2,2,2));
            addMouseListener(this);
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseMoved(MouseEvent e) { updateHandleTarget(e.getY()); }
                @Override public void mouseDragged(MouseEvent e) { updateHandleTarget(e.getY()); }
            });
            
            // Setup drag source for clustering
            dragSource = new DragSource();
            dragSource.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_MOVE, this);

            baseIcon = createIcon(nb, false);
            hoverIcon = createIcon(nb, true);
            JComponent icon = new NotebookIconPanel();
            icon.setPreferredSize(new Dimension(NOTEBOOK_ICON_SIZE + 8, NOTEBOOK_ICON_SIZE + 8));
            MouseAdapter forward = new MouseAdapter(){
                @Override public void mouseEntered(MouseEvent e){ NotebookTile.this.mouseEntered(e); }
                @Override public void mouseExited(MouseEvent e){ NotebookTile.this.mouseExited(e); }
                @Override public void mouseClicked(MouseEvent e){ NotebookTile.this.mouseClicked(e); }
            };
            icon.addMouseListener(forward);
            add(icon, BorderLayout.CENTER);

            JLabel nameLbl = new JLabel(nb.getName(),SwingConstants.CENTER);
            nameLbl.setForeground(AeroTheme.TEXT_PRIMARY);
            NotebookTile.this.add(nameLbl, BorderLayout.SOUTH);
            
            // Setup as drop target for creating new clusters
            setupDropTarget();
        }
        
        private void setupDropTarget() {
            new DropTarget(this, new DropTargetAdapter() {
                @Override
                public void drop(DropTargetDropEvent dtde) {
                    try {
                        dtde.acceptDrop(DnDConstants.ACTION_MOVE);
                        Transferable t = dtde.getTransferable();
                        if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                            String draggedName = (String) t.getTransferData(DataFlavor.stringFlavor);
                            if (!draggedName.equals(nb.getName())) {
                                // Create new cluster or add to existing
                                String clusterId = nb.isClustered() ? nb.getClusterId() : promptForClusterName();
                                if (clusterId != null && !clusterId.isEmpty()) {
                                    // Assign both notebooks to cluster
                                    for (NotebookInfo notebook : store.list()) {
                                        if (notebook.getName().equals(draggedName) || notebook.getName().equals(nb.getName())) {
                                            store.assignToCluster(notebook, clusterId);
                                        }
                                    }
                                    refresh();
                                }
                            }
                        }
                        dtde.dropComplete(true);
                    } catch (Exception ex) {
                        dtde.dropComplete(false);
                    }
                }
                
                @Override
                public void dragEnter(DropTargetDragEvent dtde) {
                    setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(100, 149, 237), 2),
                        BorderFactory.createEmptyBorder(0, 0, 0, 0)
                    ));
                }
                
                @Override
                public void dragExit(DropTargetEvent dte) {
                    setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
                }
            });
        }
        
        private String promptForClusterName() {
            return CustomInputDialog.prompt(
                NotebookManagerPanel.this,
                "Create Cluster",
                "Enter a name for the new cluster:",
                ""
            );
        }
        
        // DragGestureListener
        @Override
        public void dragGestureRecognized(DragGestureEvent dge) {
            StringSelection transferable = new StringSelection(nb.getName());
            dragSource.startDrag(dge, DragSource.DefaultMoveDrop, transferable, this);
        }
        
        // DragSourceListener methods
        @Override public void dragEnter(DragSourceDragEvent dsde) {}
        @Override public void dragOver(DragSourceDragEvent dsde) {}
        @Override public void dropActionChanged(DragSourceDragEvent dsde) {}
        @Override public void dragExit(DragSourceEvent dse) {}
        @Override public void dragDropEnd(DragSourceDropEvent dsde) {}
        
        private boolean hoverTarget=false;
        private float hoverT=0f;
        private float hoverV=0f;
        private long hoverLastNs=0L;
        private Timer hoverTimer;
        private boolean handleTarget=false;
        private float handleT=0f;
        private Timer handleTimer;
        private static final int HANDLE_REGION_HEIGHT = 28;
        private final Icon baseIcon;
        private final Icon hoverIcon;
        
        @Override protected void paintComponent(Graphics g){
            super.paintComponent(g);
            Graphics2D g2=(Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arc = 16;
            float t = smoothStep(hoverT);

            // Accent color indicator
            Color accent = nb.getAccentColor();
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 60));
            g2.fillRoundRect(0, h - 6, w, 6, 4, 4);

            // Subtle hover background
            if (t > 0.01f) {
                float alpha = 0.35f + 0.45f * t;
                int overlayH = Math.max(1, h - 9);
                RoundRectangle2D overlay = new RoundRectangle2D.Float(1, 1, w - 3f, overlayH, arc, arc);
                Composite old = g2.getComposite();
                g2.setComposite(AlphaComposite.SrcOver.derive(alpha));
                g2.setPaint(new GradientPaint(0, 1, new Color(255, 255, 255, 220), 0, overlayH,
                        new Color(224, 232, 244, 210)));
                g2.fill(overlay);
                g2.setComposite(AlphaComposite.SrcOver.derive(0.25f * t));
                g2.setPaint(new GradientPaint(0, 1, new Color(255, 255, 255, 200),
                        0, overlayH * 0.5f, new Color(255, 255, 255, 0)));
                g2.fill(overlay);

                float sunT = smoothStep(clamp01((t - 0.24f) / 0.76f));
                if (sunT > 0.001f) {
                    java.awt.Shape oldClip = g2.getClip();
                    g2.clip(overlay);

                    Color warmAccent = mix(accent, new Color(255, 222, 152), 0.26f);
                    Color sunCore = withAlpha(mix(warmAccent, Color.WHITE, 0.58f), Math.round(76f * sunT));
                    Color sunMid = withAlpha(mix(accent, new Color(255, 244, 214), 0.38f), Math.round(52f * sunT));
                    Color sunOuter = withAlpha(accent, 0);
                    float radius = Math.max(w, overlayH) * (0.62f + 0.08f * sunT);
                    RadialGradientPaint sunGlow = new RadialGradientPaint(
                            w * 0.42f,
                            overlayH * 0.28f,
                            radius,
                            new float[]{0f, 0.34f, 1f},
                            new Color[]{sunCore, sunMid, sunOuter}
                    );
                    g2.setComposite(AlphaComposite.SrcOver.derive(0.26f + 0.34f * sunT));
                    g2.setPaint(sunGlow);
                    g2.fill(overlay);

                    LinearGradientPaint sunSweep = new LinearGradientPaint(
                            0f, 0f, w * 0.9f, overlayH,
                            new float[]{0f, 0.45f, 1f},
                            new Color[]{
                                    withAlpha(mix(warmAccent, Color.WHITE, 0.42f), Math.round(64f * sunT)),
                                    withAlpha(mix(accent, new Color(255, 240, 205), 0.52f), Math.round(34f * sunT)),
                                    withAlpha(accent, 0)
                            }
                    );
                    g2.setComposite(AlphaComposite.SrcOver.derive(0.18f + 0.28f * sunT));
                    g2.setPaint(sunSweep);
                    g2.fill(overlay);

                    g2.setClip(oldClip);
                }

                g2.setComposite(old);
                g2.setColor(new Color(255, 255, 255, Math.round(130 * t)));
                g2.drawRoundRect(2, 2, w - 5, overlayH - 2, arc - 2, arc - 2);
                g2.setColor(new Color(0, 0, 0, Math.round(50 * t)));
                g2.setStroke(new BasicStroke(1.4f));
                g2.drawRoundRect(1, 1, w - 3, overlayH, arc, arc);
            }

            // Drag handle hint (smooth fade-in near bottom)
            if (handleT > 0.01f) {
                float alpha = Math.min(1f, Math.max(0f, handleT));
                int handleY = h - 18;
                int dots = 7;
                int spacing = 10;
                int radius = 4;
                int startX = w / 2 - (dots - 1) * spacing / 2;
                g2.setComposite(AlphaComposite.SrcOver.derive(0.35f + 0.55f * alpha));
                g2.setColor(new Color(20, 20, 20, Math.round(140 * alpha)));
                for (int i = 0; i < dots; i++) {
                    int cx = startX + i * spacing;
                    g2.fillOval(cx - radius / 2, handleY - radius / 2, radius, radius);
                }
                g2.setComposite(AlphaComposite.SrcOver);
            }

            int hits = getSearchHitCount(nb);
            if (hits > 0) {
                paintSearchBadge(g2, w, h, arc, hits);
            }

            g2.dispose();
        }
        @Override public void mouseEntered(MouseEvent e){ setHoverTarget(true); updateHandleTarget(e.getY()); }
        @Override public void mouseExited(MouseEvent e){ setHoverTarget(false); setHandleTarget(false); }
        @Override public void mouseClicked(MouseEvent e){
            if(SwingUtilities.isLeftMouseButton(e)){
                if(e.getClickCount()==1){ openNotebook(nb); }
                else if(e.getClickCount()==2){ showNotebookOptions(nb); }
            } else if (SwingUtilities.isRightMouseButton(e)) {
                showContextMenu(e);
            }
        }
        @Override public void mousePressed(MouseEvent e){}
        @Override public void mouseReleased(MouseEvent e){}

        private void paintSearchBadge(Graphics2D g2, int w, int h, int arc, int hits) {
            g2.setColor(new Color(90, 160, 255, 180));
            g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(1, 1, w - 3, h - 3, arc, arc);

            String label = hits == 1 ? "Found" : hits + " found";
            Font badgeFont = getFont().deriveFont(Font.BOLD, 10f);
            FontMetrics fm = g2.getFontMetrics(badgeFont);
            int padX = 6;
            int padY = 2;
            int badgeW = fm.stringWidth(label) + padX * 2;
            int badgeH = fm.getAscent() + fm.getDescent() + padY * 2;
            int bx = Math.max(6, w - badgeW - 6);
            int by = 6;
            g2.setColor(new Color(60, 130, 240, 220));
            g2.fillRoundRect(bx, by, badgeW, badgeH, 10, 10);
            g2.setColor(Color.WHITE);
            g2.setFont(badgeFont);
            g2.drawString(label, bx + padX, by + padY + fm.getAscent());
        }

        private void updateHandleTarget(int mouseY) {
            int h = getHeight();
            if (h <= 0) return;
            boolean nearBottom = mouseY >= Math.max(0, h - HANDLE_REGION_HEIGHT);
            if (nearBottom != handleTarget) {
                setHandleTarget(nearBottom);
            }
        }

        private void setHandleTarget(boolean target) {
            if (isHandleAnimationDisabled()) {
                handleTarget = target;
                handleT = target ? 1f : 0f;
                stopHandleTimer();
                repaint();
                return;
            }
            handleTarget = target;
            if (handleTimer == null) {
                handleTimer = new Timer(AppPerf.getAnimationDelay(), e -> animateHandle());
                handleTimer.start();
            }
        }

        private void setHoverTarget(boolean target) {
            if (isHandleAnimationDisabled()) {
                hoverTarget = target;
                hoverT = target ? 1f : 0f;
                hoverV = 0f;
                hoverLastNs = 0L;
                stopHoverTimer();
                repaint();
                return;
            }
            hoverTarget = target;
            if (hoverTimer == null) {
                hoverLastNs = 0L;
                hoverTimer = new Timer(AppPerf.getAnimationDelay(), e -> animateHover());
                hoverTimer.start();
            }
        }

        private void animateHover() {
            float target = hoverTarget ? 1f : 0f;
            long now = System.nanoTime();
            if (hoverLastNs == 0L) hoverLastNs = now;
            float dt = (now - hoverLastNs) / 1_000_000_000f;
            hoverLastNs = now;
            dt = Math.max(0f, Math.min(0.05f, dt));

            float smoothTime = 0.18f;
            float omega = 2f / smoothTime;
            float x = omega * dt;
            float exp = 1f / (1f + x + 0.48f * x * x + 0.235f * x * x * x);
            float change = hoverT - target;
            float temp = (hoverV + omega * change) * dt;
            hoverV = (hoverV - omega * temp) * exp;
            hoverT = target + (change + temp) * exp;
            hoverT = clamp01(hoverT);

            if (Math.abs(hoverT - target) < 0.001f && Math.abs(hoverV) < 0.001f) {
                hoverT = target;
                hoverV = 0f;
                stopHoverTimer();
            }
            repaint();
        }

        private void animateHandle() {
            float target = handleTarget ? 1f : 0f;
            float step = Math.max(0.05f, Math.min(0.2f, AppPerf.getAnimationDelay() / 220f));
            if (handleT < target) {
                handleT = Math.min(target, handleT + step);
            } else if (handleT > target) {
                handleT = Math.max(target, handleT - step);
            }
            if (Math.abs(handleT - target) < 0.001f) {
                handleT = target;
                stopHandleTimer();
            }
            repaint();
        }

        private void stopHandleTimer() {
            if (handleTimer != null) {
                handleTimer.stop();
                handleTimer = null;
            }
        }

        private void stopHoverTimer() {
            if (hoverTimer != null) {
                hoverTimer.stop();
                hoverTimer = null;
            }
        }

        private boolean isHandleAnimationDisabled() {
            try {
                return SettingsStore.get().isMainMenuAnimationsDisabled();
            } catch (Throwable ignored) {
                return false;
            }
        }

        private static float clamp01(float v) {
            return Math.max(0f, Math.min(1f, v));
        }

        private static float smoothStep(float t) {
            float clamped = clamp01(t);
            return clamped * clamped * (3f - 2f * clamped);
        }

        private static Color mix(Color a, Color b, float t) {
            float clamped = clamp01(t);
            float inv = 1f - clamped;
            int r = Math.round(a.getRed() * inv + b.getRed() * clamped);
            int g = Math.round(a.getGreen() * inv + b.getGreen() * clamped);
            int bl = Math.round(a.getBlue() * inv + b.getBlue() * clamped);
            int alpha = Math.round(a.getAlpha() * inv + b.getAlpha() * clamped);
            return new Color(r, g, bl, alpha);
        }

        private static Color withAlpha(Color color, int alpha) {
            int safeAlpha = Math.max(0, Math.min(255, alpha));
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), safeAlpha);
        }

        private class NotebookIconPanel extends JComponent {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int size = NOTEBOOK_ICON_SIZE;
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;
                float t = smoothStep(hoverT);
                Icon base = baseIcon;
                Icon hoverI = hoverIcon;
                boolean customIconCover = nb.getCustomIconPath() != null && !nb.getCustomIconPath().isBlank();
                float coverAlpha = customIconCover ? 0.9f : 0.78f * t;
                NotebookPersonalization.paintNotebookCover(g2, this, nb, x, y, size, coverAlpha);
                if (base != null && hoverI != null && t > 0.001f) {
                    Composite old = g2.getComposite();
                    float baseAlpha = 1f;
                    if (baseAlpha > 0.001f) {
                        g2.setComposite(AlphaComposite.SrcOver.derive(baseAlpha));
                        base.paintIcon(this, g2, x, y);
                    }
                    g2.setComposite(AlphaComposite.SrcOver.derive(t));
                    hoverI.paintIcon(this, g2, x, y);
                    g2.setComposite(old);
                } else if (base != null) {
                    base.paintIcon(this, g2, x, y);
                }
                g2.dispose();
            }
        }
        
        private void showContextMenu(MouseEvent e) {
            JPopupMenu menu = new JPopupMenu();
            JMenuItem openItem = new JMenuItem("Open");
            openItem.addActionListener(ev -> openNotebook(nb));
            menu.add(openItem);
            
            JMenuItem editItem = new JMenuItem("Edit Settings...");
            editItem.addActionListener(ev -> showNotebookOptions(nb));
            menu.add(editItem);

            JMenuItem accentItem = new JMenuItem("Accent Color...");
            accentItem.addActionListener(ev -> chooseNotebookAccent(nb));
            menu.add(accentItem);

            JMenuItem resetAccentItem = new JMenuItem("Use Default Accent");
            resetAccentItem.setEnabled(nb.getAccentColorRaw() != -1);
            resetAccentItem.addActionListener(ev -> resetNotebookAccent(nb));
            menu.add(resetAccentItem);
            
            menu.addSeparator();
            
            if (nb.isClustered()) {
                JMenuItem removeFromCluster = new JMenuItem("Remove from Cluster");
                removeFromCluster.addActionListener(ev -> {
                    store.removeFromCluster(nb);
                    refresh();
                });
                menu.add(removeFromCluster);
            }
            
            menu.show(this, e.getX(), e.getY());
        }
    }
    
    private void showNotebookOptions(NotebookInfo nb) {
        NotebookOptionsDialog dlg = new NotebookOptionsDialog((Frame) SwingUtilities.getWindowAncestor(this), nb, store);
        dlg.setVisible(true);
        if (dlg.wasModified()) {
            refresh();
        }
    }

    private void chooseNotebookAccent(NotebookInfo nb) {
        if (nb == null) return;
        Color picked = ModernColorPickerDialog.showDialog(
                SwingUtilities.getWindowAncestor(this),
                "Notebook Accent",
                nb.getAccentColor());
        if (picked == null) return;
        if (store.updateCustomization(nb, nb.getDescription(), picked.getRGB(), nb.getCustomIconPath())) {
            refresh();
        }
    }

    private void resetNotebookAccent(NotebookInfo nb) {
        if (nb == null || nb.getAccentColorRaw() == -1) return;
        if (store.updateCustomization(nb, nb.getDescription(), -1, nb.getCustomIconPath())) {
            refresh();
        }
    }

    private void promptNew(){
        CreateNotebookDialog dlg = new CreateNotebookDialog((Frame) SwingUtilities.getWindowAncestor(this));
        dlg.setVisible(true);
        if(dlg.isAccepted()){
            String name = dlg.getNotebookName();
            NotebookInfo.Type type = dlg.getNotebookType();
            if(name!=null && !name.isEmpty()){
                try {
                    store.create(name, type, "notebook", "", -1); // Use type default accent
                    main.ui.components.toast.ToastOverlay.success("Notebook created");
                    refresh();
                } catch (IllegalArgumentException ex) {
                    CustomConfirmDialog.confirm(this, "Could not create notebook", ex.getMessage());
                    main.ui.components.toast.ToastOverlay.error("Failed to create notebook");
                }
            }
        }
    }

    private void openNotebook(NotebookInfo nb){
        GlobalSearchEngine.SearchResult hit = getPrimarySearchHit(nb);
        if (hit != null) {
            app.openExistingEntryEditor(hit.notebook, hit.file);
            return;
        }
        app.openNotebookEntries(nb);
    }

    /* Create dialog with customization options */
    private static class CreateNotebookDialog extends JDialog{
        private boolean accepted=false;
        private final TitleDividerField nameField = new TitleDividerField(24);
        private NotebookInfo.Type selectedType = NotebookInfo.Type.POETRY;
        private TypeCard poetryCard, journalCard, notetakingCard;

        // Type-specific colors
        private static final Color POETRY_COLOR = NotebookInfo.defaultAccentFor(NotebookInfo.Type.POETRY);
        private static final Color JOURNAL_COLOR = NotebookInfo.defaultAccentFor(NotebookInfo.Type.JOURNAL);
        private static final Color NOTETAKING_COLOR = NotebookInfo.defaultAccentFor(NotebookInfo.Type.NOTETAKING);

        CreateNotebookDialog(Frame parent){
            super(parent, "Create Notebook", true);
            setUndecorated(true);
            setBackground(new Color(0,0,0,0));
            setLayout(new BorderLayout());

            ShadowedDialogPanel panel = new ShadowedDialogPanel(new BorderLayout(12, 12), 16);
            panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            panel.setFlat(true);
            panel.setFlatColor(Color.WHITE);

            // Header (name field + subtitle)
            JPanel header = new JPanel();
            header.setOpaque(false);
            header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
            header.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));

            nameField.setToolTipText("Enter notebook name");
            nameField.setAlignmentX(Component.LEFT_ALIGNMENT);
            nameField.setEditable(true);
            nameField.setFocusable(true);
            nameField.setPreferredSize(new Dimension(360, 36));
            nameField.setMinimumSize(new Dimension(200, 36));
            nameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            nameField.setPlaceholder("Notebook name");
            try {
                String family = SettingsStore.get().getEditorFontFamily();
                int size = SettingsStore.get().getJournalFontSize();
                nameField.setFont(CustomFontApplier.resolveUiFont(family, size));
            } catch (Throwable ignored) {
                nameField.setFont(nameField.getFont().deriveFont(Font.PLAIN, 16f));
            }
            header.add(nameField);

            JLabel subtitle = new JLabel("Choose a notebook type below");
            subtitle.setForeground(new Color(120, 120, 120));
            subtitle.setFont(subtitle.getFont().deriveFont(12f));
            subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            header.add(Box.createVerticalStrut(6));
            header.add(subtitle);
            panel.add(header, BorderLayout.NORTH);

            // Center content
            JPanel center = new JPanel(new GridBagLayout());
            center.setOpaque(false);
            GridBagConstraints gc = new GridBagConstraints();
            gc.gridx = 0;
            gc.gridy = 0;
            gc.anchor = GridBagConstraints.WEST;
            gc.fill = GridBagConstraints.HORIZONTAL;
            gc.weightx = 1.0;
            gc.insets = new Insets(6, 4, 6, 4);

            JLabel typeLabel = new JLabel("Notebook Type:");
            typeLabel.setForeground(Color.DARK_GRAY);
            typeLabel.setFont(typeLabel.getFont().deriveFont(Font.BOLD, 13f));
            center.add(typeLabel, gc);

            gc.gridy++;
            gc.insets = new Insets(6, 4, 8, 4);

            JPanel typeCards = new JPanel(new GridBagLayout());
            typeCards.setOpaque(false);
            GridBagConstraints tc = new GridBagConstraints();
            tc.fill = GridBagConstraints.BOTH;
            tc.weightx = 1.0;
            tc.insets = new Insets(0, 0, 0, 8);

            poetryCard = new TypeCard(
                "Poetry",
                "For poems, verses, and creative writing",
                POETRY_COLOR,
                NotebookInfo.Type.POETRY
            );
            journalCard = new TypeCard(
                "Journal",
                "For diary entries and personal reflections",
                JOURNAL_COLOR,
                NotebookInfo.Type.JOURNAL
            );
            notetakingCard = new TypeCard(
                "Notetaking",
                "For notes, sketches, and handwriting",
                NOTETAKING_COLOR,
                NotebookInfo.Type.NOTETAKING
            );

            tc.gridx = 0;
            typeCards.add(poetryCard, tc);
            tc.gridx = 1;
            tc.insets = new Insets(0, 6, 0, 6);
            typeCards.add(journalCard, tc);
            tc.gridx = 2;
            tc.insets = new Insets(0, 0, 0, 0);
            typeCards.add(notetakingCard, tc);

            center.add(typeCards, gc);
            panel.add(center, BorderLayout.CENTER);

            // Buttons
            JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 8));
            btns.setOpaque(false);
            RoundedButton okBtn = createDialogButton("Create", "save");
            okBtn.setToolTipText("Create");
            okBtn.setEnabled(false);
            okBtn.addActionListener(e -> { accepted = true; setVisible(false); dispose(); });
            RoundedButton cancel = createDialogButton("Cancel", "exit");
            cancel.setToolTipText("Cancel");
            cancel.addActionListener(e -> { accepted = false; setVisible(false); dispose(); });
            btns.add(okBtn);
            btns.add(cancel);
            panel.add(btns, BorderLayout.SOUTH);

            // Enable Create only when name entered
            nameField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){
                private void upd(){ 
                    SwingUtilities.invokeLater(() -> okBtn.setEnabled(!nameField.getText().trim().isEmpty())); 
                }
                public void insertUpdate(javax.swing.event.DocumentEvent e){ upd(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e){ upd(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e){ upd(); }
            });
            
            // Enter key to submit
            nameField.addActionListener(e -> {
                if (!nameField.getText().trim().isEmpty()) {
                    accepted = true;
                    setVisible(false);
                    dispose();
                }
            });

            // Default selection
            updateTypeSelection(NotebookInfo.Type.POETRY);

            add(panel);
            pack();
            Dimension minSize = new Dimension(480, 400);
            int w = Math.max(minSize.width, getWidth());
            int h = Math.max(minSize.height, getHeight());
            setSize(w, h);
            setMinimumSize(new Dimension(w, h));
            setLocationRelativeTo(parent);
            SwingUtilities.invokeLater(() -> nameField.requestFocusInWindow());
        }

        private void updateTypeSelection(NotebookInfo.Type type) {
            selectedType = type;
            poetryCard.setSelected(type == NotebookInfo.Type.POETRY);
            journalCard.setSelected(type == NotebookInfo.Type.JOURNAL);
            notetakingCard.setSelected(type == NotebookInfo.Type.NOTETAKING);
        }

        private RoundedButton createDialogButton(String text, String iconId) {
            RoundedButton btn = new RoundedButton(text).withIcon(iconId);
            btn.setPreferredSize(new Dimension(132, 40));
            btn.setFocusPainted(false);
            return btn;
        }

        boolean isAccepted(){ return accepted; }
        String getNotebookName(){ return nameField.getText().trim(); }
        NotebookInfo.Type getNotebookType(){ return selectedType; }

        /** Card component for notebook type selection */
        private class TypeCard extends JPanel {
            private final NotebookInfo.Type type;
            private final Color accentColor;
            private boolean selected = false;
            private boolean hovered = false;

            TypeCard(String title, String description, Color accent, NotebookInfo.Type type) {
                this.type = type;
                this.accentColor = accent;
                setOpaque(false);
                setLayout(new BorderLayout(8, 4));
                setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

                JPanel textPanel = new JPanel();
                textPanel.setOpaque(false);
                textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

                JLabel titleLbl = new JLabel(title);
                titleLbl.setFont(titleLbl.getFont().deriveFont(Font.BOLD, 13f));
                titleLbl.setForeground(new Color(50, 50, 50));
                titleLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

                JLabel descLbl = new JLabel("<html><body style='width:140px'>" + description + "</body></html>");
                descLbl.setFont(descLbl.getFont().deriveFont(11f));
                descLbl.setForeground(new Color(110, 110, 110));
                descLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

                textPanel.add(titleLbl);
                textPanel.add(Box.createVerticalStrut(3));
                textPanel.add(descLbl);
                add(textPanel, BorderLayout.CENTER);

                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        updateTypeSelection(type);
                    }
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hovered = true;
                        repaint();
                    }
                    @Override
                    public void mouseExited(MouseEvent e) {
                        hovered = false;
                        repaint();
                    }
                });
            }

            void setSelected(boolean sel) {
                this.selected = sel;
                repaint();
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int arc = 12;
                // Background
                if (selected) {
                    g2.setColor(new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 25));
                } else if (hovered) {
                    g2.setColor(new Color(240, 242, 248));
                } else {
                    g2.setColor(new Color(250, 251, 253));
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);

                // Border
                if (selected) {
                    g2.setColor(accentColor);
                    g2.setStroke(new BasicStroke(2f));
                } else {
                    g2.setColor(hovered ? new Color(200, 205, 215) : new Color(220, 225, 235));
                    g2.setStroke(new BasicStroke(1f));
                }
                g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, arc, arc);

                // Accent stripe on left when selected
                if (selected) {
                    g2.setColor(accentColor);
                    g2.fillRoundRect(0, 4, 4, getHeight() - 8, 2, 2);
                }

                g2.dispose();
                super.paintComponent(g);
            }
        }
    }

    /* Options dialog for editing existing notebooks */
    private class NotebookOptionsDialog extends JDialog{
        private boolean modified = false;
        private final NotebookStore store;
        private final NotebookInfo notebook;
        private final TitleDividerField titleField;
        private final JPanel iconPreview;
        private final JPanel accentPreview;
        private final JLabel accentValueLabel;
        private final JLabel backgroundValueLabel;
        private final JLabel coverValueLabel;
        private final RoundedButton resetAccentBtn;
        private String customIconPath = null;
        private String backgroundImagePath = null;
        private String coverImagePath = null;
        private String editorFontFamily = null;
        private String editorStylePreset = null;
        private int accentColorRaw;
        
        NotebookOptionsDialog(Frame parent, NotebookInfo nb, NotebookStore store){
            super(parent, "Edit: " + nb.getName(), true);
            this.store = store;
            this.notebook = nb;
            this.customIconPath = nb.getCustomIconPath();
            this.backgroundImagePath = nb.getBackgroundImagePath();
            this.coverImagePath = nb.getCoverImagePath();
            this.editorFontFamily = nb.getEditorFontFamily();
            this.editorStylePreset = nb.getEditorStylePreset();
            this.accentColorRaw = nb.getAccentColorRaw();
            
            setUndecorated(true);
            setBackground(new Color(0,0,0,0));
            setLayout(new BorderLayout());

            ShadowedDialogPanel panel = new ShadowedDialogPanel(new BorderLayout(12,12), 16);
            panel.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));
            panel.setFlat(true);
            panel.setFlatColor(Color.WHITE);

            // Title with notebook info (match entry/poem title styling)
            JPanel header = new JPanel();
            header.setOpaque(false);
            header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
            header.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

            titleField = new TitleDividerField(24);
            titleField.setText(nb.getName());
            titleField.setPlaceholder("Notebook title");
            titleField.setToolTipText("Edit notebook title");
            titleField.setAlignmentX(Component.LEFT_ALIGNMENT);
            try {
                String family = SettingsStore.get().getEditorFontFamily();
                int size = SettingsStore.get().getJournalFontSize();
                titleField.setFont(CustomFontApplier.resolveUiFont(family, size));
            } catch (Throwable ignored) {
                titleField.setFont(titleField.getFont().deriveFont(Font.PLAIN, 16f));
            }
            header.add(titleField);

            long daysAgo = (System.currentTimeMillis() - nb.getCreatedMillis()) / 86400000L;
            JLabel info = new JLabel("Created " + daysAgo + " days ago • " + countEntries(nb) + " entries");
            info.setForeground(new Color(120, 120, 120));
            info.setFont(info.getFont().deriveFont(12f));
            info.setAlignmentX(Component.LEFT_ALIGNMENT);
            header.add(Box.createVerticalStrut(6));
            header.add(info);
            panel.add(header, BorderLayout.NORTH);

            // Center content
            JPanel center = new JPanel();
            center.setOpaque(false);
            center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

            JLabel iconLabel = new JLabel("Notebook Icon:");
            iconLabel.setForeground(Color.DARK_GRAY);
            iconLabel.setFont(iconLabel.getFont().deriveFont(Font.BOLD, 13f));
            iconLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            center.add(iconLabel);
            center.add(Box.createVerticalStrut(8));

            JPanel iconRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
            iconRow.setOpaque(false);
            iconRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            // Show current icon preview
            iconPreview = new JPanel() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    // Draw the actual notebook icon (custom or default)
                    String res = main.ui.components.icons.ImageIconRenderer.mapIdToResource("notebook");
                    NotebookInfo previewNotebook = notebook.withCustomization(
                            notebook.getDescription(), accentColorRaw, customIconPath,
                            backgroundImagePath, coverImagePath, editorFontFamily, editorStylePreset);
                    NotebookPersonalization.paintNotebookCover(g2, this, previewNotebook, 0, 0, 48, 0.82f);
                    if (res != null) {
                        main.ui.components.icons.ImageIconRenderer.draw(g2, res, 0, 0, 48, this, true);
                    }
                    Color accent = resolvedAccentColor();
                    int barW = Math.max(12, getWidth() - 8);
                    int barX = (getWidth() - barW) / 2;
                    int barY = getHeight() - 7;
                    g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 115));
                    g2.fillRoundRect(barX, barY, barW, 4, 4, 4);
                    g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 188));
                    g2.drawRoundRect(barX, barY, barW, 4, 4, 4);
                    g2.dispose();
                }
            };
            iconPreview.setPreferredSize(new Dimension(48, 48));
            iconPreview.setBorder(BorderFactory.createLineBorder(new Color(200,200,200)));
            iconRow.add(iconPreview);
            
            // Change icon button
            RoundedButton changeIconBtn = createDialogButton("Change Icon", "backgroundoptions");
            changeIconBtn.setToolTipText("Upload custom icon");
            changeIconBtn.addActionListener(e -> chooseCustomIcon());
            iconRow.add(changeIconBtn);
            
            // Remove custom icon button (only if custom icon is set)
            if (customIconPath != null && !customIconPath.isEmpty()) {
                RoundedButton removeIconBtn = createDialogButton("Reset", "close");
                removeIconBtn.setToolTipText("Reset to default icon");
                removeIconBtn.addActionListener(e -> {
                    customIconPath = null;
                    coverImagePath = null;
                    refreshWritingUi();
                    iconPreview.repaint();
                });
                iconRow.add(removeIconBtn);
            }
            
            center.add(iconRow);
            center.add(Box.createVerticalStrut(16));

            JLabel accentLabel = new JLabel("Notebook Accent:");
            accentLabel.setForeground(Color.DARK_GRAY);
            accentLabel.setFont(accentLabel.getFont().deriveFont(Font.BOLD, 13f));
            accentLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            center.add(accentLabel);
            center.add(Box.createVerticalStrut(8));

            JPanel accentPanel = new JPanel();
            accentPanel.setOpaque(false);
            accentPanel.setLayout(new BoxLayout(accentPanel, BoxLayout.Y_AXIS));
            accentPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

            JPanel accentInfoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
            accentInfoRow.setOpaque(false);
            accentInfoRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            accentPreview = new JPanel() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    Color accent = resolvedAccentColor();
                    int w = Math.max(1, getWidth() - 1);
                    int h = Math.max(1, getHeight() - 1);
                    g2.setPaint(new LinearGradientPaint(
                            0f, 0f, 0f, h,
                            new float[] {0f, 1f},
                            new Color[] {
                                    mix(accent, Color.WHITE, 0.54f),
                                    mix(accent, Color.WHITE, 0.16f)
                            }
                    ));
                    g2.fillRoundRect(0, 0, w, h, 10, 10);
                    g2.setColor(new Color(255, 255, 255, 132));
                    g2.drawRoundRect(0, 0, w, h, 10, 10);
                    g2.dispose();
                }
            };
            accentPreview.setPreferredSize(new Dimension(58, 22));
            accentPreview.setMinimumSize(new Dimension(58, 22));
            accentPreview.setMaximumSize(new Dimension(58, 22));
            accentPreview.setBorder(BorderFactory.createLineBorder(new Color(210, 214, 224)));
            accentInfoRow.add(accentPreview);

            accentValueLabel = new JLabel();
            accentValueLabel.setForeground(new Color(108, 108, 118));
            accentValueLabel.setFont(accentValueLabel.getFont().deriveFont(12f));
            accentInfoRow.add(accentValueLabel);

            JLabel accentHint = new JLabel("Used for notebook stripes, hover glow, and other accents.");
            accentHint.setForeground(new Color(126, 126, 136));
            accentHint.setFont(accentHint.getFont().deriveFont(11f));
            accentHint.setAlignmentX(Component.LEFT_ALIGNMENT);

            accentPanel.add(accentInfoRow);
            accentPanel.add(Box.createVerticalStrut(6));
            accentPanel.add(accentHint);
            accentPanel.add(Box.createVerticalStrut(10));

            JPanel accentButtonsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            accentButtonsRow.setOpaque(false);
            accentButtonsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            RoundedButton pickAccentBtn = createDialogButton("Choose Color", "backgroundoptions");
            pickAccentBtn.setToolTipText("Choose any accent color");
            pickAccentBtn.addActionListener(e -> chooseAccentColor());
            accentButtonsRow.add(pickAccentBtn);

            resetAccentBtn = createDialogButton("Use Default", "close");
            resetAccentBtn.setToolTipText("Reset to the notebook type default");
            resetAccentBtn.addActionListener(e -> {
                accentColorRaw = -1;
                refreshAccentUi();
            });
            accentButtonsRow.add(resetAccentBtn);
            accentPanel.add(accentButtonsRow);

            center.add(accentPanel);
            center.add(Box.createVerticalStrut(16));

            JLabel writingLabel = new JLabel("Notebook Writing:");
            writingLabel.setForeground(Color.DARK_GRAY);
            writingLabel.setFont(writingLabel.getFont().deriveFont(Font.BOLD, 13f));
            writingLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            center.add(writingLabel);
            center.add(Box.createVerticalStrut(8));

            JPanel writingPanel = new JPanel();
            writingPanel.setOpaque(false);
            writingPanel.setLayout(new BoxLayout(writingPanel, BoxLayout.Y_AXIS));
            writingPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

            backgroundValueLabel = new JLabel();
            backgroundValueLabel.setForeground(new Color(108, 108, 118));
            backgroundValueLabel.setFont(backgroundValueLabel.getFont().deriveFont(12f));
            backgroundValueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            writingPanel.add(backgroundValueLabel);
            writingPanel.add(Box.createVerticalStrut(8));

            JPanel backgroundButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            backgroundButtons.setOpaque(false);
            backgroundButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
            RoundedButton chooseBackgroundBtn = createDialogButton("Choose Background", "backgroundoptions");
            chooseBackgroundBtn.setToolTipText("Use a notebook-specific editor background");
            chooseBackgroundBtn.addActionListener(e -> chooseNotebookBackground());
            backgroundButtons.add(chooseBackgroundBtn);
            RoundedButton clearBackgroundBtn = createDialogButton("Use Global", "close");
            clearBackgroundBtn.setToolTipText("Use the global editor background");
            clearBackgroundBtn.addActionListener(e -> {
                backgroundImagePath = null;
                refreshWritingUi();
            });
            backgroundButtons.add(clearBackgroundBtn);
            writingPanel.add(backgroundButtons);
            writingPanel.add(Box.createVerticalStrut(10));

            coverValueLabel = new JLabel();
            coverValueLabel.setForeground(new Color(108, 108, 118));
            coverValueLabel.setFont(coverValueLabel.getFont().deriveFont(12f));
            coverValueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            writingPanel.add(coverValueLabel);
            writingPanel.add(Box.createVerticalStrut(8));

            JPanel coverButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            coverButtons.setOpaque(false);
            coverButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
            RoundedButton chooseCoverBtn = createDialogButton("Choose Cover", "backgroundoptions");
            chooseCoverBtn.setToolTipText("Use a notebook-specific hover cover");
            chooseCoverBtn.addActionListener(e -> chooseNotebookCover());
            coverButtons.add(chooseCoverBtn);
            RoundedButton coverFromBackgroundBtn = createDialogButton("Use Background", "backgroundoptions");
            coverFromBackgroundBtn.setToolTipText("Use the editor background as this notebook cover");
            coverFromBackgroundBtn.addActionListener(e -> {
                coverImagePath = null;
                refreshWritingUi();
            });
            coverButtons.add(coverFromBackgroundBtn);
            writingPanel.add(coverButtons);
            writingPanel.add(Box.createVerticalStrut(10));

            JPanel fontRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            fontRow.setOpaque(false);
            fontRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            fontRow.add(new JLabel("Font:"));
            JComboBox<String> fontBox = new JComboBox<>(new String[]{
                    "Use global", "Serif", "SansSerif", "Monospaced", "Snell Roundhand", "Georgia", "Helvetica Neue"
            });
            fontBox.setUI(new ModernComboBoxUI());
            fontBox.setRenderer(new ModernComboBoxUI.ModernComboBoxRenderer());
            fontBox.setSelectedItem(editorFontFamily == null || editorFontFamily.isBlank() ? "Use global" : editorFontFamily);
            fontBox.addActionListener(e -> {
                Object selected = fontBox.getSelectedItem();
                String value = selected == null ? "" : selected.toString();
                editorFontFamily = "Use global".equals(value) ? null : value;
            });
            fontRow.add(fontBox);
            fontRow.add(new JLabel("Style:"));
            JComboBox<String> styleBox = new JComboBox<>(new String[]{
                    NotebookPersonalization.PRESET_DEFAULT,
                    NotebookPersonalization.PRESET_JOURNAL,
                    NotebookPersonalization.PRESET_DRAFT,
                    NotebookPersonalization.PRESET_POEM
            });
            styleBox.setUI(new ModernComboBoxUI());
            styleBox.setRenderer(new ModernComboBoxUI.ModernComboBoxRenderer());
            styleBox.setSelectedItem(editorStylePreset == null || editorStylePreset.isBlank()
                    ? NotebookPersonalization.PRESET_DEFAULT
                    : editorStylePreset);
            styleBox.addActionListener(e -> {
                Object selected = styleBox.getSelectedItem();
                String value = selected == null ? "" : selected.toString();
                editorStylePreset = NotebookPersonalization.PRESET_DEFAULT.equals(value) ? null : value;
            });
            fontRow.add(styleBox);
            writingPanel.add(fontRow);

            JLabel writingHint = new JLabel("Cover previews are clipped inside the notebook drawing on hover.");
            writingHint.setForeground(new Color(126, 126, 136));
            writingHint.setFont(writingHint.getFont().deriveFont(11f));
            writingHint.setAlignmentX(Component.LEFT_ALIGNMENT);
            writingPanel.add(Box.createVerticalStrut(6));
            writingPanel.add(writingHint);

            center.add(writingPanel);
            
            // Cluster info
            if (nb.isClustered()) {
                center.add(Box.createVerticalStrut(14));
                JLabel clusterLabel = new JLabel("In cluster: " + nb.getClusterId());
                clusterLabel.setForeground(new Color(100, 100, 100));
                clusterLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                center.add(clusterLabel);
            }

            center.add(Box.createVerticalGlue());

            JScrollPane centerScroll = new JScrollPane(center);
            centerScroll.setOpaque(false);
            centerScroll.getViewport().setOpaque(false);
            centerScroll.setBorder(BorderFactory.createEmptyBorder());
            centerScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            centerScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            centerScroll.getVerticalScrollBar().setUnitIncrement(16);
            centerScroll.getVerticalScrollBar().setUI(new ModernScrollBarUI());
            panel.add(centerScroll, BorderLayout.CENTER);

            // Buttons
            JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 8));
            btns.setOpaque(false);
            RoundedButton saveBtn = createDialogButton("Save", "save");
            saveBtn.setToolTipText("Save changes");
            saveBtn.addActionListener(e->{ 
                String newName = titleField.getText() == null ? "" : titleField.getText().trim();
                if (newName.isEmpty()) {
                    CustomConfirmDialog.confirm(this, "Invalid Notebook Name", "Notebook name cannot be empty.");
                    return;
                }

                boolean renamed = false;
                NotebookInfo target = notebook;
                if (!newName.equalsIgnoreCase(notebook.getName())) {
                    if (!store.rename(notebook, newName)) {
                        CustomConfirmDialog.confirm(this, "Rename Failed",
                                "A notebook with that name already exists, or the folder could not be renamed.");
                        return;
                    }
                    renamed = true;
                    target = findNotebookByName(newName);
                    if (target == null) {
                        target = notebook;
                    }
                    app.handleNotebookRename(notebook.getName(), newName);
                }

                boolean updated = false;
                if (target != null) {
                    updated = store.updateCustomization(target, target.getDescription(), accentColorRaw, customIconPath,
                            backgroundImagePath, coverImagePath, editorFontFamily, editorStylePreset);
                }
                modified = renamed || updated;
                setVisible(false); 
                dispose(); 
            });
            RoundedButton deleteBtn = createDialogButton("Delete", "delete_notebook");
            deleteBtn.setToolTipText("Delete this notebook");
            deleteBtn.addActionListener(e->{ 
                boolean confirm = CustomConfirmDialog.confirm(this, 
                    "Delete Notebook",
                    "Delete notebook '" + nb.getName() + "' and all its contents?");
                if (confirm) {
                    store.delete(nb);
                    modified = true;
                    setVisible(false);
                    dispose();
                }
            });
            RoundedButton cancelBtn = createDialogButton("Cancel", "exit");
            cancelBtn.setToolTipText("Cancel and close");
            cancelBtn.addActionListener(e->{ setVisible(false); dispose(); });
            btns.add(saveBtn); btns.add(deleteBtn); btns.add(cancelBtn);
            panel.add(btns, BorderLayout.SOUTH);

            add(panel);
            pack();
            refreshAccentUi();
            refreshWritingUi();
            setSize(540, 700);
            setLocationRelativeTo(parent);
        }

        private Color resolvedAccentColor() {
            return accentColorRaw == -1
                    ? NotebookInfo.defaultAccentFor(notebook.getType())
                    : new Color(accentColorRaw, true);
        }

        private void refreshAccentUi() {
            if (accentPreview != null) {
                accentPreview.repaint();
            }
            if (iconPreview != null) {
                iconPreview.repaint();
            }
            if (accentValueLabel != null) {
                Color accent = resolvedAccentColor();
                String hex = String.format("#%06X", accent.getRGB() & 0xFFFFFF);
                if (accentColorRaw == -1) {
                    accentValueLabel.setText("Using type default • " + hex);
                } else {
                    accentValueLabel.setText("Custom accent • " + hex);
                }
            }
            if (resetAccentBtn != null) {
                resetAccentBtn.setEnabled(accentColorRaw != -1);
            }
        }

        private void refreshWritingUi() {
            if (backgroundValueLabel != null) {
                backgroundValueLabel.setText(backgroundImagePath == null || backgroundImagePath.isBlank()
                        ? "Background: using global editor background"
                        : "Background: " + new File(backgroundImagePath).getName());
            }
            if (coverValueLabel != null) {
                if (coverImagePath != null && !coverImagePath.isBlank()) {
                    coverValueLabel.setText("Cover: " + new File(coverImagePath).getName());
                } else if (backgroundImagePath != null && !backgroundImagePath.isBlank()) {
                    coverValueLabel.setText("Cover: using notebook background");
                } else {
                    coverValueLabel.setText("Cover: default notebook drawing");
                }
            }
            if (iconPreview != null) {
                iconPreview.repaint();
            }
        }

        private NotebookInfo findNotebookByName(String name) {
            if (name == null) return null;
            for (NotebookInfo nb : store.list()) {
                if (name.equalsIgnoreCase(nb.getName())) {
                    return nb;
                }
            }
            return null;
        }

        private void chooseCustomIcon() {
            SimjotFileChooser chooser = new SimjotFileChooser(SwingUtilities.getWindowAncestor(this), "Select Notebook Icon");
            chooser.setMode(SimjotFileChooser.Mode.OPEN);
            chooser.addFileFilter("Images", "png", "jpg", "jpeg", "gif", "bmp", "webp");
            if (customIconPath != null && !customIconPath.isBlank()) {
                File current = new File(customIconPath);
                File dir = current.getParentFile();
                if (dir != null && dir.isDirectory()) {
                    chooser.setCurrentDirectory(dir);
                }
            }
            File selected = chooser.showDialog();
            if (selected == null || !selected.exists()) return;
            try {
                BufferedImage img = ImageIO.read(selected);
                if (img != null) {
                    customIconPath = selected.getAbsolutePath();
                    iconPreview.repaint();
                }
            } catch (IOException ex) {
                CustomConfirmDialog.confirm(this, "Icon Load Failed", "Could not load selected image.");
            }
        }

        private void chooseNotebookBackground() {
            SimjotFileChooser chooser = new SimjotFileChooser(SwingUtilities.getWindowAncestor(this), "Select Notebook Background");
            chooser.setMode(SimjotFileChooser.Mode.OPEN);
            chooser.addFileFilter("Images", "png", "jpg", "jpeg", "gif", "bmp", "webp");
            if (backgroundImagePath != null && !backgroundImagePath.isBlank()) {
                File current = new File(backgroundImagePath);
                File dir = current.getParentFile();
                if (dir != null && dir.isDirectory()) {
                    chooser.setCurrentDirectory(dir);
                }
            }
            File selected = chooser.showDialog();
            if (selected == null || !selected.exists()) return;
            try {
                BufferedImage img = ImageIO.read(selected);
                if (img != null) {
                    backgroundImagePath = selected.getAbsolutePath();
                    refreshWritingUi();
                }
            } catch (IOException ex) {
                CustomConfirmDialog.confirm(this, "Background Load Failed", "Could not load selected image.");
            }
        }

        private void chooseNotebookCover() {
            SimjotFileChooser chooser = new SimjotFileChooser(SwingUtilities.getWindowAncestor(this), "Select Notebook Cover");
            chooser.setMode(SimjotFileChooser.Mode.OPEN);
            chooser.addFileFilter("Images", "png", "jpg", "jpeg", "gif", "bmp", "webp");
            String currentPath = coverImagePath != null && !coverImagePath.isBlank() ? coverImagePath : backgroundImagePath;
            if (currentPath != null && !currentPath.isBlank()) {
                File current = new File(currentPath);
                File dir = current.getParentFile();
                if (dir != null && dir.isDirectory()) {
                    chooser.setCurrentDirectory(dir);
                }
            }
            File selected = chooser.showDialog();
            if (selected == null || !selected.exists()) return;
            try {
                BufferedImage img = ImageIO.read(selected);
                if (img != null) {
                    coverImagePath = selected.getAbsolutePath();
                    refreshWritingUi();
                }
            } catch (IOException ex) {
                CustomConfirmDialog.confirm(this, "Cover Load Failed", "Could not load selected image.");
            }
        }

        private void chooseAccentColor() {
            Color picked = ModernColorPickerDialog.showDialog(this, "Notebook Accent", resolvedAccentColor());
            if (picked == null) return;
            accentColorRaw = picked.getRGB();
            refreshAccentUi();
        }

        private RoundedButton createDialogButton(String text, String iconId) {
            RoundedButton btn = new RoundedButton(text);
            btn.setPreferredSize(new Dimension(132, 40));
            btn.setFocusPainted(false);
            return btn;
        }

        private Color mix(Color a, Color b, float t) {
            float clamped = Math.max(0f, Math.min(1f, t));
            float inv = 1f - clamped;
            int r = Math.round(a.getRed() * inv + b.getRed() * clamped);
            int g = Math.round(a.getGreen() * inv + b.getGreen() * clamped);
            int bl = Math.round(a.getBlue() * inv + b.getBlue() * clamped);
            int alpha = Math.round(a.getAlpha() * inv + b.getAlpha() * clamped);
            return new Color(r, g, bl, alpha);
        }

        boolean wasModified(){ return modified; }
    }

    // Create the permanent tile for adding a new notebook
    private JPanel createAddTile(){
        JPanel tile = new JPanel(){
            private boolean hover=false;
            { setOpaque(false); }
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                // Simple background
                if(hover){
                    g2.setColor(new Color(245,245,245));
                    g2.fillRoundRect(0,0,w,h,12,12);
                }

                // Simple border
                g2.setColor(new Color(200,200,200));
                g2.drawRoundRect(0,0,w-1,h-1,12,12);

                // Center icon for creating a new notebook
                int iconSize = 42;
                String res = main.ui.components.icons.ImageIconRenderer.mapIdToResource("new");
                if (res != null) {
                    int x = (getWidth() - iconSize) / 2;
                    int y = (getHeight() - iconSize) / 2;
                    main.ui.components.icons.ImageIconRenderer.draw(g2, res, x, y, iconSize, this, true);
                }

                g2.dispose();
            }
            @Override protected void processMouseEvent(MouseEvent e){
                switch(e.getID()){
                    case MouseEvent.MOUSE_ENTERED -> { hover=true; repaint(); }
                    case MouseEvent.MOUSE_EXITED -> { hover=false; repaint(); }
                }
                super.processMouseEvent(e);
            }
        };
        tile.setPreferredSize(new Dimension(70,70));
        tile.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        tile.addMouseListener(new MouseAdapter(){
            @Override public void mouseClicked(MouseEvent e){ promptNew(); }
        });
        return tile;
    }

}
