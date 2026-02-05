/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoglu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.home;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import main.core.analytics.MoodAnalyticsEngine;
import main.core.service.NotebookStore;
import main.infrastructure.backup.NotebookInfo;
import main.ui.app.JournalApp;
import main.ui.components.buttons.ToolbarMenuIconButton;
import main.ui.components.containers.RoundedPanel;
import main.ui.theme.Theme;
import main.ui.theme.aero.AeroPainters;
import main.ui.theme.aero.AeroTheme;

/**
 * Elegant mood chart panel with a refined, atmospheric layout.
 * Uses the existing MoodChartRenderer for chart rendering.
 */
public class ElegantMoodChartPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final Color BG_TOP = new Color(244, 246, 250);
    private static final Color BG_BOTTOM = new Color(230, 236, 244);
    private static final Color CARD_TOP = new Color(255, 255, 255, 230);
    private static final Color CARD_BOTTOM = new Color(242, 246, 250, 230);
    private static final Color BORDER_SOFT = new Color(198, 206, 218, 180);
    private static final Color TEXT_PRIMARY = new Color(32, 38, 48);
    private static final Color TEXT_SECONDARY = new Color(98, 110, 126);
    private static final Color TEXT_MUTED = new Color(130, 140, 156);
    private static final Color ACCENT_MAIN = new Color(34, 137, 168);
    private static final Color ACCENT_WARM = new Color(223, 150, 94);
    private static final Color ACCENT_SOFT = new Color(76, 181, 157);

    private static final int CHART_MARGIN = 60;

    private final MoodChartModel model = new MoodChartModel();
    private final MoodChartSettings settings = new MoodChartSettings();
    private final MoodChartRenderer renderer = new MoodChartRenderer(settings);

    private final JournalApp app;

    private int selectedRangeIndex = 1;
    private final String[] ranges = {"7d", "30d", "90d", "1y", "All"};

    private float revealProgress = 0f;
    private Timer revealTimer;

    private Integer hoverIndex = null;
    private float hoverAlpha = 0f;
    private float hoverTargetAlpha = 0f;
    private Timer hoverTimer;

    private StatCard overallCard;
    private StatCard volatilityCard;
    private StatCard streakCard;
    private StatCard samplesCard;

    private ChartCanvas chartCanvas;

    public ElegantMoodChartPanel(JournalApp app, CardLayout cardLayout, JPanel cardPanel) {
        this.app = app;
        settings.setShowTrend(false);
        settings.setShowEntryTicks(false);
        setLayout(new BorderLayout());
        setOpaque(true);

        add(createHeader(), BorderLayout.NORTH);
        add(createBody(), BorderLayout.CENTER);

        loadData();
        startReveal();

        hoverTimer = new Timer(16, e -> updateHoverFade());

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                loadData();
                startReveal();
            }

            @Override
            public void componentResized(ComponentEvent e) {
                renderer.invalidate();
            }
        });
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

        JLabel title = new JLabel("Mood Atlas");
        title.setFont(resolveTitleFont(24f));
        title.setForeground(TEXT_PRIMARY);

        JLabel subtitle = new JLabel("A view of your emotional landscape");
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

        JPanel rangePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rangePanel.setOpaque(false);
        for (int i = 0; i < ranges.length; i++) {
            RangeButton btn = new RangeButton(ranges[i], i);
            btn.addActionListener(e -> {
                selectedRangeIndex = btn.index;
                loadData();
                startReveal();
                rangePanel.repaint();
            });
            rangePanel.add(btn);
        }

        header.add(left, BorderLayout.WEST);
        header.add(rangePanel, BorderLayout.EAST);

        return header;
    }

    private JPanel createBody() {
        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(12, 26, 24, 26));

        JPanel statsRow = new JPanel();
        statsRow.setOpaque(false);
        statsRow.setLayout(new java.awt.GridLayout(1, 4, 14, 0));
        statsRow.setPreferredSize(new Dimension(0, 86));

        overallCard = new StatCard("Overall", this::getOverallValue,
                this::getOverallColor, () -> "/100", 0);
        volatilityCard = new StatCard("Volatility", this::getVolatilityValue,
                () -> ACCENT_MAIN, () -> "std dev", 1);
        streakCard = new StatCard("Streak", this::getStreakText,
                this::getStreakColor, () -> "current", 2);
        samplesCard = new StatCard("Samples", () -> String.valueOf(model.getTotalSamples()),
                () -> ACCENT_SOFT, () -> "moods", 3);

        statsRow.add(overallCard);
        statsRow.add(volatilityCard);
        statsRow.add(streakCard);
        statsRow.add(samplesCard);

        JPanel chartCard = new ChartCard();
        chartCard.setLayout(new BorderLayout());
        chartCard.setOpaque(false);
        chartCanvas = new ChartCanvas();
        JPanel chartInset = new JPanel(new BorderLayout());
        chartInset.setOpaque(false);
        chartInset.setBorder(new EmptyBorder(16, 16, 18, 16));
        chartInset.add(chartCanvas, BorderLayout.CENTER);
        chartCard.add(chartInset, BorderLayout.CENTER);

        JLabel hint = new JLabel("Tip: Click a point to open the nearest entry. Hold Cmd for closest by time.");
        hint.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 11.5f));
        hint.setForeground(TEXT_MUTED);
        hint.setHorizontalAlignment(JLabel.CENTER);

        body.add(statsRow, BorderLayout.NORTH);
        body.add(chartCard, BorderLayout.CENTER);
        body.add(hint, BorderLayout.SOUTH);

        return body;
    }

    private void loadData() {
        model.load(selectedRangeIndex);
        renderer.invalidate();
        hoverIndex = null;
        hoverTargetAlpha = 0f;
        hoverAlpha = 0f;
        if (hoverTimer != null && hoverTimer.isRunning()) {
            hoverTimer.stop();
        }
        if (chartCanvas != null) {
            chartCanvas.repaint();
        }
        repaint();
    }

    private void startReveal() {
        revealProgress = 0f;
        if (revealTimer != null && revealTimer.isRunning()) {
            revealTimer.stop();
        }
        revealTimer = new Timer(16, e -> {
            revealProgress = Math.min(1f, revealProgress + 0.05f);
            if (revealProgress >= 1f) {
                revealTimer.stop();
            }
            repaint();
        });
        revealTimer.start();
    }

    private Font resolveTitleFont(float size) {
        Font f = new Font("Palatino Linotype", Font.BOLD, Math.round(size));
        if (f.getFamily() == null || f.getFamily().equalsIgnoreCase("Dialog")) {
            f = new Font("Georgia", Font.BOLD, Math.round(size));
        }
        return f.deriveFont(size);
    }

    private String getStreakText() {
        int streak = model.getCurrentStreak();
        if (streak > 0) return streak + " good";
        if (streak < 0) return (-streak) + " tough";
        return "None";
    }

    private Color getStreakColor() {
        int streak = model.getCurrentStreak();
        if (streak > 0) return ACCENT_SOFT;
        if (streak < 0) return ACCENT_WARM;
        return TEXT_SECONDARY;
    }

    private boolean hasMoodData() {
        if (model.getDays().isEmpty()) return false;
        for (Double v : model.getValues()) {
            if (v != null) return true;
        }
        return false;
    }

    private String getOverallValue() {
        if (!hasMoodData()) return "--";
        return String.format("%.0f", model.getOverallAverage());
    }

    private Color getOverallColor() {
        if (!hasMoodData()) return TEXT_SECONDARY;
        return MoodAnalyticsEngine.getColor(model.getOverallAverage());
    }

    private String getVolatilityValue() {
        if (!hasMoodData()) return "--";
        return String.format("%.1f", model.getVolatility());
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();

        if (Theme.isPlainWhite()) {
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, w, h);
        } else {
            g2.setPaint(new GradientPaint(0, 0, BG_TOP, 0, h, BG_BOTTOM));
            g2.fillRect(0, 0, w, h);

            g2.setComposite(AlphaComposite.SrcOver.derive(0.35f));
            RadialGradientPaint glow = new RadialGradientPaint(new Point(w / 3, h / 4),
                    Math.max(w, h) * 0.6f,
                    new float[]{0f, 1f},
                    new Color[]{new Color(255, 255, 255, 180), new Color(255, 255, 255, 0)});
            g2.setPaint(glow);
            g2.fillRect(0, 0, w, h);

            g2.setComposite(AlphaComposite.SrcOver.derive(0.18f));
            g2.setColor(new Color(240, 200, 160, 120));
            g2.fillOval(30, h - 230, 280, 190);
        }

        g2.dispose();
    }

    private void setHoverIndex(Integer idx) {
        if (Objects.equals(idx, hoverIndex)) {
            return;
        }
        hoverIndex = idx;
        hoverTargetAlpha = idx != null ? 1f : 0f;
        if (hoverTimer != null && !hoverTimer.isRunning()) {
            hoverTimer.start();
        }
    }

    private void updateHoverFade() {
        hoverAlpha += (hoverTargetAlpha - hoverAlpha) * 0.2f;
        if (Math.abs(hoverTargetAlpha - hoverAlpha) < 0.02f) {
            hoverAlpha = hoverTargetAlpha;
            if (hoverAlpha <= 0.01f && hoverIndex == null) {
                hoverAlpha = 0f;
            }
            hoverTimer.stop();
        }
        repaint();
    }

    private class RangeButton extends JButton {
        private final int index;

        RangeButton(String text, int index) {
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
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            d.height = 28;
            d.width = Math.max(d.width, 52);
            return d;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean selected = index == selectedRangeIndex;
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
                top = new Color(58, 146, 192);
                bottom = new Color(34, 122, 170);
                border = new Color(24, 96, 140);
                text = Color.WHITE;
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

            g2.setColor(text);
            FontMetrics fm = g2.getFontMetrics();
            int tx = (w - fm.stringWidth(getText())) / 2;
            int ty = (h + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(getText(), tx, ty);

            g2.dispose();
        }
    }

    private class StatCard extends RoundedPanel {
        private final String label;
        private final java.util.function.Supplier<String> valueSupplier;
        private final java.util.function.Supplier<Color> accentSupplier;
        private final java.util.function.Supplier<String> suffixSupplier;
        private final int index;

        StatCard(String label,
                 java.util.function.Supplier<String> valueSupplier,
                 java.util.function.Supplier<Color> accentSupplier,
                 java.util.function.Supplier<String> suffixSupplier,
                 int index) {
            this.label = label;
            this.valueSupplier = valueSupplier;
            this.accentSupplier = accentSupplier;
            this.suffixSupplier = suffixSupplier;
            this.index = index;
            setArc(16);
            setFlat(true);
            setBackground(Color.WHITE);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            float stagger = Math.max(0f, Math.min(1f, (revealProgress - index * 0.12f) / 0.8f));
            g2.setComposite(AlphaComposite.SrcOver.derive(0.2f + 0.8f * stagger));
            g2.translate(0, (1f - stagger) * 6f);
            super.paintComponent(g2);

            Color accent = accentSupplier.get();
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 30));
            g2.fillOval(14, 16, 28, 28);
            g2.setColor(accent);
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(14, 16, 28, 28);

            g2.setFont(AeroTheme.defaultBoldFont(20f));
            g2.setColor(TEXT_PRIMARY);
            String value = valueSupplier.get();
            FontMetrics fm = g2.getFontMetrics();
            int valueX = 54;
            int valueY = 38;
            g2.drawString(value, valueX, valueY);

            String suffix = suffixSupplier.get();
            if ("--".equals(value)) {
                suffix = "";
            }
            if (suffix != null && !suffix.isBlank()) {
                g2.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 11f));
                g2.setColor(TEXT_MUTED);
                int sx = valueX + fm.stringWidth(value) + 6;
                g2.drawString(suffix, sx, valueY - 2);
            }

            g2.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 12f));
            g2.setColor(TEXT_SECONDARY);
            g2.drawString(label, valueX, getHeight() - 18);

            g2.dispose();
        }
    }

    private class ChartCard extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int arc = 22;

            if (Theme.isPlainWhite()) {
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
                g2.setColor(new Color(210, 216, 224));
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            } else {
                AeroPainters.paintOuterGlow(g2, new Rectangle(3, 3, w - 6, h - 6), arc,
                        new Color(70, 130, 180, 90), 8, 90);
                AeroPainters.paintVerticalGradient(g2, new Rectangle(0, 0, w, h), CARD_TOP, CARD_BOTTOM, arc);
                AeroPainters.paintGlassOverlay(g2, new Rectangle(0, 0, w, h), arc);
                AeroPainters.paintInnerStroke(g2, new Rectangle(0, 0, w, h), arc, new Color(255, 255, 255, 150));
                g2.setColor(BORDER_SOFT);
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private class ChartCanvas extends JComponent {
        ChartCanvas() {
            setOpaque(false);
            setToolTipText("");

            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    updateHover(e);
                }
            });

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent e) {
                    setHoverIndex(null);
                    setToolTipText("");
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    handleChartClick(e);
                }
            });

            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    renderer.invalidate();
                }
            });
        }

        private void updateHover(MouseEvent e) {
            if (model.getDays().isEmpty()) {
                setHoverIndex(null);
                return;
            }
            int n = model.getDays().size();
            int chartW = Math.max(1, getWidth() - 2 * CHART_MARGIN);
            if (n <= 1 || chartW <= 0) {
                setHoverIndex(null);
                return;
            }

            int idx = renderer.indexForX(getWidth(), e.getX(), n);
            if (idx < 0 || idx >= n) {
                setHoverIndex(null);
                return;
            }

            int x = CHART_MARGIN + Math.round(idx * (chartW / (float) Math.max(1, n - 1)));
            if (Math.abs(e.getX() - x) > 18) {
                setHoverIndex(null);
                return;
            }

            Double value = model.getValues().get(idx);
            if (value == null) {
                setHoverIndex(null);
                return;
            }

            setHoverIndex(idx);
            setToolTipText(buildTooltip(idx));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            float alpha = 0.3f + 0.7f * revealProgress;
            g2.setComposite(AlphaComposite.SrcOver.derive(alpha));

            if (model.getDays().isEmpty() || model.getValues().stream().allMatch(Objects::isNull)) {
                g2.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 14f));
                g2.setColor(TEXT_SECONDARY);
                String msg = "No mood data yet. Start journaling to see insights.";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(msg, (getWidth() - fm.stringWidth(msg)) / 2, getHeight() / 2);
                g2.dispose();
                return;
            }

            renderer.paint(g2, this, model.getDays(), model.getValues(), model.getEntriesByDate(), null);

            if (hoverIndex != null && hoverAlpha > 0.02f) {
                int x = renderer.getX(hoverIndex);
                int y = renderer.getYDaily(hoverIndex);
                if (x >= 0 && y != Integer.MIN_VALUE) {
                    int glow = 10;
                    g2.setComposite(AlphaComposite.SrcOver.derive(0.12f * hoverAlpha));
                    g2.setColor(ACCENT_MAIN);
                    g2.fillOval(x - glow, y - glow, glow * 2, glow * 2);

                    int radius = 6;
                    g2.setComposite(AlphaComposite.SrcOver.derive(hoverAlpha));
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(2.2f));
                    g2.drawOval(x - radius, y - radius, radius * 2, radius * 2);
                }

                String pill = buildHoverLabel(hoverIndex);
                if (pill != null && !pill.isBlank()) {
                    g2.setComposite(AlphaComposite.SrcOver.derive(0.9f * hoverAlpha));
                    g2.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 12f));
                    FontMetrics fm = g2.getFontMetrics();
                    int tw = fm.stringWidth(pill) + 18;
                    int th = fm.getHeight() + 6;
                    int px = Math.max(16, getWidth() - tw - 20);
                    int py = 16;
                    g2.setColor(new Color(255, 255, 255, 220));
                    g2.fillRoundRect(px, py, tw, th, 16, 16);
                    g2.setColor(new Color(180, 190, 205, 180));
                    g2.drawRoundRect(px, py, tw, th, 16, 16);
                    g2.setColor(TEXT_PRIMARY);
                    g2.drawString(pill, px + 9, py + th - 6);
                }
            }

            g2.dispose();
        }
    }

    private String buildTooltip(int idx) {
        if (idx < 0 || idx >= model.getDays().size()) return "";
        LocalDate d = model.getDays().get(idx);
        Double raw = model.getValues().get(idx);
        java.util.List<File> files = model.getEntriesByDate().get(d);
        MoodChartModel.Details det = model.getLatestDetailsFor(d);
        String base;
        if (raw == null) {
            base = d + (files == null || files.isEmpty() ? " no entry" : " (" + files.size() + ") click to open");
        } else {
            base = d + " avg mood: " + (int) Math.round(raw) + "/100";
            if (files != null && !files.isEmpty()) {
                base += files.size() == 1 ? " | 1 entry" : " | " + files.size() + " entries";
            }
        }
        if (det != null) {
            String detail = " J:" + det.joy + " C:" + det.calm + " G:" + det.gratitude + " En:" + det.energy
                    + " | Sa:" + det.sadness + " Ang:" + det.anger + " Anx:" + det.anxiety + " St:" + det.stress;
            return base + detail;
        }
        return base;
    }

    private String buildHoverLabel(int idx) {
        if (idx < 0 || idx >= model.getDays().size()) return "";
        LocalDate day = model.getDays().get(idx);
        Double value = model.getValues().get(idx);
        if (value == null) return "";
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d, yyyy");
        return fmt.format(day) + "  " + Math.round(value) + "/100";
    }

    private void handleChartClick(MouseEvent e) {
        if (model.getDays().isEmpty()) return;
        int n = model.getDays().size();
        int idx = renderer.indexForX(chartCanvas.getWidth(), e.getX(), n);
        if (idx < 0 || idx >= n) return;
        Double raw = model.getValues().get(idx);
        if (raw == null) return;
        LocalDate d = model.getDays().get(idx);
        java.util.List<File> files = model.getEntriesByDate().get(d);
        if (files == null || files.isEmpty()) return;
        if (e.isMetaDown()) {
            openNearestForDay(d);
            return;
        }
        if (files.size() == 1) {
            NotebookInfo nb = findNotebookFor(files.get(0));
            if (nb != null) app.openExistingEntryEditor(nb, files.get(0));
        } else {
            JPopupMenu menu = new JPopupMenu();
            for (File f : files) {
                JMenuItem it = new JMenuItem(safeTitle(f));
                it.addActionListener(ev -> {
                    NotebookInfo nb = findNotebookFor(f);
                    if (nb != null) app.openExistingEntryEditor(nb, f);
                });
                menu.add(it);
            }
            menu.show(chartCanvas, e.getX(), e.getY());
        }
    }

    private void openNearestForDay(LocalDate d) {
        java.util.List<MoodChartModel.EntryRef> refs = model.getEntryTimesByDate().get(d);
        java.util.List<LocalDateTime> moodTimes = model.getMoodTimesByDate().get(d);
        if (refs == null || refs.isEmpty()) return;
        File target = null;
        if (moodTimes != null && !moodTimes.isEmpty()) {
            LocalDateTime anchor = moodTimes.get(moodTimes.size() - 1);
            long best = Long.MAX_VALUE;
            for (MoodChartModel.EntryRef r : refs) {
                if (r.ts == null) continue;
                long diff = Math.abs(ChronoUnit.SECONDS.between(anchor, r.ts));
                if (diff < best) {
                    best = diff;
                    target = r.file;
                }
            }
        }
        if (target == null) {
            target = refs.stream()
                    .max(java.util.Comparator.comparingLong(er -> er.file.lastModified()))
                    .map(er -> er.file)
                    .orElse(null);
        }
        if (target != null) {
            NotebookInfo nb = findNotebookFor(target);
            if (nb != null) app.openExistingEntryEditor(nb, target);
        }
    }

    private NotebookInfo findNotebookFor(File f) {
        try {
            NotebookStore store = new NotebookStore();
            for (NotebookInfo nb : store.list()) {
                File folder = nb.getFolder();
                if (folder != null && f.getAbsolutePath().startsWith(folder.getAbsolutePath() + File.separator)) {
                    return nb;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private String safeTitle(File f) {
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String first = br.readLine();
            if (first != null && !first.isBlank()) return first.trim();
        } catch (IOException ignored) {
        }
        String nm = f.getName();
        int dot = nm.lastIndexOf('.');
        return dot > 0 ? nm.substring(0, dot) : nm;
    }
}
