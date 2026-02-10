/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.home;

import java.awt.BorderLayout;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.border.EmptyBorder;

import main.core.service.SettingsStore;
import main.infrastructure.io.ResourceLoader;
import main.infrastructure.io.QuoteLibrary;
import main.ui.app.JournalApp;
import main.ui.components.buttons.ToolbarMenuIconButton;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.features.gallery.GeneratedWallpapers;
import main.ui.theme.Theme;
import main.ui.theme.aero.AeroTheme;
import main.ui.util.AccentColorUtil;

/**
 * Fullscreen quote gallery with manual navigation and favorites.
 */
public class QuoteGalleryPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final Color BORDER_SOFT = new Color(198, 206, 218, 180);
    private static final Color TEXT_PRIMARY = new Color(32, 38, 48);
    private static final Color TEXT_SECONDARY = new Color(98, 110, 126);
    private static final Color TEXT_MUTED = new Color(130, 140, 156);
    private static final Color HEART_ON = new Color(232, 74, 92);
    private static final Color HEART_OFF = new Color(145, 155, 170);

    private final JournalApp app;

    private final List<QuoteLibrary.QuoteEntry> allQuotes = new ArrayList<>();
    private List<QuoteLibrary.QuoteEntry> viewQuotes = new ArrayList<>();
    private final java.util.Set<String> favoriteKeys = new LinkedHashSet<>();

    private int selectedFilter = 0; // 0 = all, 1 = favorites
    private int quoteIndex = 0;

    private QuoteDisplay display;
    private JLabel countLabel;
    private HeartToggleButton favoriteButton;
    private ToolbarMenuIconButton prevButton;
    private ToolbarMenuIconButton nextButton;
    private FilterButton allButton;
    private FilterButton favoritesButton;
    private Color accent = AccentColorUtil.defaultAccent();

    public QuoteGalleryPanel(JournalApp app) {
        this.app = app;
        setLayout(new BorderLayout());
        setOpaque(false);

        JPanel background = buildBackgroundLayer();
        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);
        content.add(createHeader(), BorderLayout.NORTH);
        content.add(createBody(), BorderLayout.CENTER);
        background.add(content, BorderLayout.CENTER);
        add(background, BorderLayout.CENTER);

        refreshFromStore();

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                refreshFromStore();
            }
        });
    }

    public void showQuote(String quoteText, String quoteAuthor) {
        if (quoteText == null || quoteText.trim().isEmpty()) return;
        String text = quoteText.trim();
        String author = (quoteAuthor == null || quoteAuthor.trim().isEmpty()) ? null : quoteAuthor.trim();
        QuoteLibrary.QuoteEntry target = new QuoteLibrary.QuoteEntry(text, author);
        String key = quoteKey(target);

        if (indexOfKey(allQuotes, key) < 0) {
            allQuotes.add(target);
        }

        selectedFilter = 0;
        if (allButton != null) allButton.repaint();
        if (favoritesButton != null) favoritesButton.repaint();
        rebuildView(key);
    }

    private JPanel buildBackgroundLayer() {
        String bgPath = SettingsStore.get().getBackgroundImage();
        Color nextAccent = AccentColorUtil.defaultAccent();
        JPanel base;
        if (bgPath != null && !bgPath.isEmpty()) {
            if (bgPath.startsWith("gen:")) {
                Image img = GeneratedWallpapers.render(bgPath, 2560, 1440);
                try { nextAccent = AccentColorUtil.extractAccent(img); } catch (Throwable ignored) { }
                BackgroundPanel bg = new BackgroundPanel(img);
                bg.setOpacityOverride(1.0f);
                base = bg;
            } else if (bgPath.startsWith("res:")) {
                String resPath = bgPath.substring(4);
                Image img = ResourceLoader.createImage("Simjot/" + resPath);
                if (img != null) {
                    try { nextAccent = AccentColorUtil.extractAccent(img); } catch (Throwable ignored) { }
                    BackgroundPanel bg = new BackgroundPanel(img);
                    bg.setOpacityOverride(1.0f);
                    base = bg;
                } else {
                    base = new JPanel();
                }
            } else {
                Image img = new ImageIcon(bgPath).getImage();
                try { nextAccent = AccentColorUtil.extractAccent(img); } catch (Throwable ignored) { }
                BackgroundPanel bg = new BackgroundPanel(bgPath);
                bg.setOpacityOverride(1.0f);
                base = bg;
            }
            base.setBackground(Color.BLACK);
        } else {
            base = new JPanel();
            base.setBackground(Color.WHITE);
        }
        accent = nextAccent != null ? nextAccent : AccentColorUtil.defaultAccent();
        base.setLayout(new BorderLayout());
        return base;
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();

                if (!Theme.isPlainWhite()) {
                    g2.setPaint(new LinearGradientPaint(0, 0, 0, h,
                            new float[]{0f, 1f},
                            new Color[]{new Color(255, 255, 255, 170), new Color(235, 240, 248, 180)}));
                    g2.fillRoundRect(8, 6, w - 16, h - 12, 20, 20);
                    g2.setColor(new Color(180, 190, 205, 120));
                    g2.drawRoundRect(8, 6, w - 16, h - 12, 20, 20);
                }

                g2.setColor(new Color(200, 210, 222, 150));
                g2.drawLine(24, h - 2, w - 24, h - 2);
                g2.dispose();
            }
        };
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(18, 24, 12, 24));

        ToolbarMenuIconButton backButton = new ToolbarMenuIconButton("", "back");
        backButton.setToolTipText("Back to main menu");
        backButton.addActionListener(e -> app.switchCard(JournalApp.MAIN_MENU));

        JLabel title = new JLabel("Quote Gallery");
        title.setFont(resolveTitleFont(24f));
        title.setForeground(TEXT_PRIMARY);

        JLabel subtitle = new JLabel("Slow down and meet the words");
        subtitle.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 12.5f));
        subtitle.setForeground(TEXT_SECONDARY);

        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.add(title);
        titleBlock.add(Box.createVerticalStrut(4));
        titleBlock.add(subtitle);

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
        left.add(backButton);
        left.add(Box.createHorizontalStrut(12));
        left.add(titleBlock);

        JPanel filterPanel = new JPanel();
        filterPanel.setOpaque(false);
        filterPanel.setLayout(new BoxLayout(filterPanel, BoxLayout.X_AXIS));
        allButton = new FilterButton("All", 0);
        favoritesButton = new FilterButton("Favorites", 1);
        filterPanel.add(allButton);
        filterPanel.add(Box.createHorizontalStrut(8));
        filterPanel.add(favoritesButton);

        header.add(left, BorderLayout.WEST);
        header.add(filterPanel, BorderLayout.EAST);

        return header;
    }

    private JPanel createBody() {
        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(10, 24, 22, 24));

        FrostedGlassPanel quoteCard = new FrostedGlassPanel(new BorderLayout(), 22);
        quoteCard.setOpacityScale(0.9f);
        quoteCard.setOpaque(false);

        display = new QuoteDisplay();
        JPanel quoteInset = new JPanel(new BorderLayout());
        quoteInset.setOpaque(false);
        quoteInset.setBorder(new EmptyBorder(18, 28, 18, 28));
        quoteInset.add(display, BorderLayout.CENTER);

        quoteCard.add(quoteInset, BorderLayout.CENTER);

        body.add(quoteCard, BorderLayout.CENTER);
        body.add(createControlsRow(), BorderLayout.SOUTH);

        return body;
    }

    private JPanel createControlsRow() {
        JPanel controls = new JPanel(new BorderLayout());
        controls.setOpaque(false);

        prevButton = new ToolbarMenuIconButton("", "back");
        prevButton.setToolTipText("Previous quote");
        prevButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        prevButton.addActionListener(e -> showQuoteAt(quoteIndex - 1));

        nextButton = new ToolbarMenuIconButton("", "back");
        nextButton.setIconRotationRadians(Math.PI);
        nextButton.setToolTipText("Next quote");
        nextButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        nextButton.addActionListener(e -> showQuoteAt(quoteIndex + 1));

        JPanel nav = new JPanel();
        nav.setOpaque(false);
        nav.setLayout(new BoxLayout(nav, BoxLayout.X_AXIS));
        nav.add(prevButton);
        nav.add(Box.createHorizontalStrut(8));
        nav.add(nextButton);

        countLabel = new JLabel("– / –");
        countLabel.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 12.5f));
        countLabel.setForeground(TEXT_MUTED);
        JPanel countPanel = new JPanel();
        countPanel.setOpaque(false);
        countPanel.setLayout(new BoxLayout(countPanel, BoxLayout.X_AXIS));
        countPanel.add(countLabel);

        favoriteButton = new HeartToggleButton();
        favoriteButton.setToolTipText("Favorite");
        favoriteButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        favoriteButton.addActionListener(e -> toggleFavorite());

        JPanel favPanel = new JPanel();
        favPanel.setOpaque(false);
        favPanel.setLayout(new BoxLayout(favPanel, BoxLayout.X_AXIS));
        favPanel.add(favoriteButton);

        controls.add(nav, BorderLayout.WEST);
        controls.add(countPanel, BorderLayout.CENTER);
        controls.add(favPanel, BorderLayout.EAST);

        return controls;
    }

    private void refreshFromStore() {
        String currentKey = currentQuoteKey();
        loadQuotes();
        loadFavorites();
        rebuildView(currentKey);
    }

    private void loadQuotes() {
        allQuotes.clear();
        java.util.Set<String> seen = new LinkedHashSet<>();
        List<QuoteLibrary.QuoteEntry> base = QuoteLibrary.loadQuoteEntries();
        if (base != null) {
            for (QuoteLibrary.QuoteEntry entry : base) {
                if (entry == null || entry.text == null || entry.text.trim().isEmpty()) continue;
                String key = quoteKey(entry);
                if (seen.add(key)) {
                    allQuotes.add(entry);
                }
            }
        }
        try {
            String[] custom = SettingsStore.get().getHeaderCustomQuotes();
            if (custom != null) {
                for (String q : custom) {
                    if (q == null || q.trim().isEmpty()) continue;
                    QuoteLibrary.QuoteEntry entry = new QuoteLibrary.QuoteEntry(q.trim(), null);
                    String key = quoteKey(entry);
                    if (seen.add(key)) {
                        allQuotes.add(entry);
                    }
                }
            }
        } catch (Throwable ignored) { }

        if (allQuotes.isEmpty()) {
            allQuotes.add(new QuoteLibrary.QuoteEntry("Take a deep breath. You are enough.", null));
        }
    }

    private void loadFavorites() {
        favoriteKeys.clear();
        try {
            favoriteKeys.addAll(SettingsStore.get().getFavoriteQuoteKeys());
        } catch (Throwable ignored) { }
    }

    private void rebuildView(String preferredKey) {
        if (showingFavorites()) {
            viewQuotes = buildFavoritesView();
        } else {
            viewQuotes = new ArrayList<>(allQuotes);
        }

        if (viewQuotes.isEmpty()) {
            quoteIndex = -1;
        } else if (preferredKey != null) {
            int idx = indexOfKey(viewQuotes, preferredKey);
            quoteIndex = idx >= 0 ? idx : Math.min(Math.max(quoteIndex, 0), viewQuotes.size() - 1);
        } else {
            quoteIndex = Math.min(Math.max(quoteIndex, 0), viewQuotes.size() - 1);
        }

        updateViewState();
    }

    private List<QuoteLibrary.QuoteEntry> buildFavoritesView() {
        List<QuoteLibrary.QuoteEntry> list = new ArrayList<>();
        for (String key : favoriteKeys) {
            QuoteLibrary.QuoteEntry entry = decodeKey(key);
            if (entry != null) {
                list.add(entry);
            }
        }
        return list;
    }

    private void updateViewState() {
        QuoteLibrary.QuoteEntry current = getCurrentQuote();
        if (current == null) {
            display.setQuote(null);
            display.setEmptyMessage(showingFavorites()
                    ? "No favorites yet. Tap Favorite to save a quote."
                    : "No quotes available.");
        } else {
            display.setEmptyMessage(null);
            display.setQuote(current);
        }

        if (viewQuotes.isEmpty()) {
            countLabel.setText(showingFavorites() ? "No favorites" : "No quotes");
        } else {
            countLabel.setText((quoteIndex + 1) + " / " + viewQuotes.size());
        }

        boolean hasMany = viewQuotes.size() > 1;
        prevButton.setEnabled(hasMany);
        nextButton.setEnabled(hasMany);

        syncFavoriteButton();
    }

    private void showQuoteAt(int idx) {
        if (viewQuotes.isEmpty()) return;
        int size = viewQuotes.size();
        int next = idx % size;
        if (next < 0) next += size;
        quoteIndex = next;
        updateViewState();
    }

    private void toggleFavorite() {
        QuoteLibrary.QuoteEntry current = getCurrentQuote();
        if (current == null) return;
        String key = quoteKey(current);
        boolean favorited = favoriteButton.isSelected();
        if (favorited) {
            favoriteKeys.add(key);
        } else {
            favoriteKeys.remove(key);
        }
        SettingsStore store = SettingsStore.get();
        store.setFavoriteQuoteKeys(favoriteKeys);
        store.save();

        if (showingFavorites() && !favorited) {
            rebuildView(null);
        } else {
            syncFavoriteButton();
        }
    }

    private void syncFavoriteButton() {
        QuoteLibrary.QuoteEntry current = getCurrentQuote();
        if (current == null) {
            favoriteButton.setEnabled(false);
            favoriteButton.setSelected(false);
            favoriteButton.setToolTipText("Favorite");
            return;
        }
        boolean fav = favoriteKeys.contains(quoteKey(current));
        favoriteButton.setEnabled(true);
        favoriteButton.setSelected(fav);
        favoriteButton.setToolTipText(fav ? "Favorited" : "Favorite");
    }

    private QuoteLibrary.QuoteEntry getCurrentQuote() {
        if (viewQuotes == null || viewQuotes.isEmpty() || quoteIndex < 0 || quoteIndex >= viewQuotes.size()) {
            return null;
        }
        return viewQuotes.get(quoteIndex);
    }

    private boolean showingFavorites() {
        return selectedFilter == 1;
    }

    private String currentQuoteKey() {
        QuoteLibrary.QuoteEntry current = getCurrentQuote();
        return current == null ? null : quoteKey(current);
    }

    private void setFilter(int index) {
        if (selectedFilter == index) return;
        selectedFilter = index;
        rebuildView(currentQuoteKey());
        if (allButton != null) allButton.repaint();
        if (favoritesButton != null) favoritesButton.repaint();
    }

    private static int indexOfKey(List<QuoteLibrary.QuoteEntry> list, String key) {
        if (list == null || key == null) return -1;
        for (int i = 0; i < list.size(); i++) {
            QuoteLibrary.QuoteEntry entry = list.get(i);
            if (entry == null) continue;
            if (key.equals(quoteKey(entry))) return i;
        }
        return -1;
    }

    private static String quoteKey(QuoteLibrary.QuoteEntry entry) {
        if (entry == null) return "";
        String text = entry.text == null ? "" : entry.text;
        String author = entry.author == null ? "" : entry.author;
        return encodeKey(text) + ":" + encodeKey(author);
    }

    private static QuoteLibrary.QuoteEntry decodeKey(String key) {
        if (key == null || key.isBlank()) return null;
        try {
            String[] parts = key.split(":", -1);
            if (parts.length == 0) return null;
            String text = decodeKeyPart(parts[0]);
            String author = parts.length > 1 ? decodeKeyPart(parts[1]) : null;
            if (text == null || text.isBlank()) return null;
            if (author != null && author.isBlank()) author = null;
            return new QuoteLibrary.QuoteEntry(text, author);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String encodeKey(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeKeyPart(String value) {
        if (value == null || value.isEmpty()) return "";
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private Font resolveTitleFont(float size) {
        Font f = new Font("Palatino Linotype", Font.BOLD, Math.round(size));
        if (f.getFamily() == null || f.getFamily().equalsIgnoreCase("Dialog")) {
            f = new Font("Georgia", Font.BOLD, Math.round(size));
        }
        return f.deriveFont(size);
    }

    private static Font resolveQuoteFont(float size, int style) {
        Font f = new Font("Palatino Linotype", style, Math.round(size));
        if (f.getFamily() == null || f.getFamily().equalsIgnoreCase("Dialog")) {
            f = new Font("Georgia", style, Math.round(size));
        }
        return f.deriveFont(size);
    }

    private class FilterButton extends JButton {
        private final int index;

        FilterButton(String text, int index) {
            super(text);
            this.index = index;
            setOpaque(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 12f));
            setMargin(new java.awt.Insets(6, 12, 6, 12));
            addActionListener(e -> setFilter(this.index));
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            d.height = 28;
            d.width = Math.max(d.width, 70);
            return d;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean selected = index == selectedFilter;
            boolean hover = getModel().isRollover();
            boolean pressed = getModel().isPressed();

            int w = getWidth();
            int h = getHeight();
            int arc = 14;

            Color top;
            Color bottom;
            Color border;
            Color text;

            if (selected || pressed) {
                top = new Color(255, 235, 205, 230);
                bottom = new Color(253, 218, 160, 220);
                border = new Color(230, 168, 95);
                text = new Color(80, 50, 30);
            } else if (hover) {
                top = new Color(245, 248, 252);
                bottom = new Color(226, 234, 242);
                border = new Color(190, 202, 214);
                text = TEXT_PRIMARY;
            } else {
                top = new Color(250, 252, 255, 210);
                bottom = new Color(232, 238, 246, 210);
                border = BORDER_SOFT;
                text = TEXT_PRIMARY;
            }

            g2.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.setColor(border);
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            if (selected || hover) {
                g2.setColor(new Color(255, 180, 90, selected ? 140 : 70));
                g2.drawRoundRect(1, 1, w - 3, h - 3, arc - 2, arc - 2);
            }

            g2.setColor(text);
            FontMetrics fm = g2.getFontMetrics();
            int tx = (w - fm.stringWidth(getText())) / 2;
            int ty = (h + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(getText(), tx, ty);

            g2.dispose();
        }
    }

    private class HeartToggleButton extends JToggleButton {
        HeartToggleButton() {
            setOpaque(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setPreferredSize(new Dimension(44, 44));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            boolean hover = getModel().isRollover();
            boolean pressed = getModel().isPressed();
            boolean selected = isSelected();

            int arc = 12;
            Color top = pressed ? new Color(230, 234, 242, 220)
                    : (hover ? new Color(250, 252, 255, 220) : new Color(245, 248, 252, 200));
            Color bottom = pressed ? new Color(210, 216, 226, 220)
                    : (hover ? new Color(226, 234, 242, 220) : new Color(225, 230, 238, 190));
            g2.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.setColor(new Color(170, 180, 195, 200));
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

            Shape heart = createHeartShape(w, h);
            Color fill = selected ? HEART_ON : (hover ? AccentColorUtil.lighten(HEART_OFF, 0.15f) : HEART_OFF);
            Color stroke = selected ? AccentColorUtil.darken(HEART_ON, 0.18f) : new Color(110, 120, 135, 190);

            if (selected) {
                Graphics2D glow = (Graphics2D) g2.create();
                Color glowBase = AccentColorUtil.blend(HEART_ON, accent, 0.25f);
                glow.setColor(new Color(glowBase.getRed(), glowBase.getGreen(), glowBase.getBlue(), 70));
                glow.setStroke(new BasicStroke(4.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                glow.draw(heart);
                glow.dispose();
            }

            g2.setColor(fill);
            g2.fill(heart);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(stroke);
            g2.draw(heart);

            if (!selected && hover) {
                g2.setColor(new Color(255, 255, 255, 120));
                g2.setStroke(new BasicStroke(1f));
                g2.draw(heart);
            }

            g2.dispose();
        }

        private Shape createHeartShape(int w, int h) {
            Path2D.Double path = new Path2D.Double();
            path.moveTo(0, -20);
            path.curveTo(-25, -50, -60, -10, 0, 30);
            path.curveTo(60, -10, 25, -50, 0, -20);
            path.closePath();
            Rectangle2D bounds = path.getBounds2D();
            double scale = Math.min((w * 0.58) / bounds.getWidth(), (h * 0.58) / bounds.getHeight());
            AffineTransform at = new AffineTransform();
            at.translate(w / 2.0, h / 2.0);
            at.scale(scale, scale);
            at.translate(-bounds.getCenterX(), -bounds.getCenterY());
            return at.createTransformedShape(path);
        }
    }

    private static class QuoteDisplay extends JPanel {
        private QuoteLibrary.QuoteEntry quote;
        private String emptyMessage;

        QuoteDisplay() {
            setOpaque(false);
        }

        void setQuote(QuoteLibrary.QuoteEntry entry) {
            this.quote = entry;
            repaint();
        }

        void setEmptyMessage(String message) {
            this.emptyMessage = message;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                g2.dispose();
                return;
            }

            if (quote == null || quote.text == null || quote.text.isBlank()) {
                String msg = emptyMessage != null ? emptyMessage : "No quotes available.";
                g2.setFont(AeroTheme.defaultFont().deriveFont(Font.ITALIC, 14f));
                g2.setColor(TEXT_MUTED);
                FontMetrics fm = g2.getFontMetrics();
                int x = (w - fm.stringWidth(msg)) / 2;
                int y = (h + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(msg, x, y);
                g2.dispose();
                return;
            }

            String text = quote.text.trim();
            String author = quote.author;
            int padX = 12;
            int padY = 8;
            int maxWidth = Math.max(180, w - padX * 2);
            int availableHeight = Math.max(120, h - padY * 2);

            int fontSize = 26;
            int minSize = 16;
            List<String> lines;
            Font quoteFont;
            FontMetrics fm;
            int totalHeight;
            int authorGap = 12;
            Font authorFont = AeroTheme.defaultFont().deriveFont(Font.PLAIN, 14f);
            FontMetrics afm = g2.getFontMetrics(authorFont);

            while (true) {
                quoteFont = resolveQuoteFont(fontSize, Font.ITALIC);
                fm = g2.getFontMetrics(quoteFont);
                lines = wrapText(text, fm, maxWidth);
                totalHeight = lines.size() * fm.getHeight();
                if (author != null && !author.isBlank()) {
                    authorFont = AeroTheme.defaultFont().deriveFont(Font.PLAIN, Math.max(12f, fontSize - 8));
                    afm = g2.getFontMetrics(authorFont);
                    totalHeight += authorGap + afm.getHeight();
                }
                if (totalHeight <= availableHeight || fontSize <= minSize) {
                    break;
                }
                fontSize -= 2;
            }

            int y = (h - totalHeight) / 2 + fm.getAscent();
            g2.setFont(quoteFont);
            g2.setColor(TEXT_PRIMARY);

            for (String line : lines) {
                int lineW = fm.stringWidth(line);
                int x = (w - lineW) / 2;
                g2.drawString(line, x, y);
                y += fm.getHeight();
            }

            if (author != null && !author.isBlank()) {
                y += authorGap;
                String authorText = formatAuthorText(author);
                g2.setFont(authorFont);
                g2.setColor(TEXT_SECONDARY);
                FontMetrics af = g2.getFontMetrics();
                int ax = (w - af.stringWidth(authorText)) / 2;
                int ay = y + af.getAscent();
                g2.drawString(authorText, ax, ay);
            }

            g2.dispose();
        }

        private static List<String> wrapText(String text, FontMetrics fm, int maxWidth) {
            List<String> lines = new ArrayList<>();
            if (text == null || text.isEmpty()) return lines;
            String[] words = text.split("\\s+");
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                if (line.length() == 0) {
                    line.append(word);
                } else {
                    String test = line + " " + word;
                    if (fm.stringWidth(test) > maxWidth) {
                        lines.add(line.toString());
                        line.setLength(0);
                        line.append(word);
                    } else {
                        line.append(" ").append(word);
                    }
                }
            }
            if (line.length() > 0) {
                lines.add(line.toString());
            }
            return lines;
        }

        private static String formatAuthorText(String author) {
            if (author == null) return "";
            String trimmed = author.trim();
            if (trimmed.isEmpty()) return "";
            if (trimmed.startsWith("—") || trimmed.startsWith("-")) {
                return trimmed;
            }
            return "— " + trimmed;
        }
    }
}
