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
import java.util.List;
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

import main.core.service.NotebookStore;
import main.infrastructure.backup.NotebookInfo;
import main.ui.app.JournalApp;
import main.ui.components.buttons.ToolbarMenuIconButton;
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

    private static final int CHART_MARGIN = 60;

    private final MoodChartModel model = new MoodChartModel();
    private final MoodChartSettings settings = new MoodChartSettings();
    private final MoodChartRenderer renderer = new MoodChartRenderer(settings);

    private final JournalApp app;

    private int selectedRangeIndex = 1;
    private final String[] ranges = {"7d", "30d", "90d", "1y", "All"};

    private float revealProgress = 0f;
    private Timer revealTimer;

    private ChartCanvas chartCanvas;
    private EmotionTrendCanvas emotionTrendCanvas;
    private JLabel dominantEmotionLabel;

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
        body.setBorder(new EmptyBorder(10, 24, 22, 24));

        JPanel chartCard = new ChartCard();
        chartCard.setLayout(new BorderLayout());
        chartCard.setOpaque(false);
        chartCanvas = new ChartCanvas();
        JPanel chartInset = new JPanel(new BorderLayout());
        chartInset.setOpaque(false);
        chartInset.setBorder(new EmptyBorder(14, 14, 16, 14));
        chartInset.add(chartCanvas, BorderLayout.CENTER);
        chartCard.add(chartInset, BorderLayout.CENTER);

        JLabel hint = new JLabel("Tip: Click a point to open the nearest entry. Hold Cmd for closest by time.");
        hint.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 11.5f));
        hint.setForeground(TEXT_MUTED);
        hint.setHorizontalAlignment(JLabel.CENTER);

        JPanel emotionCard = createEmotionInsightsCard();

        JPanel footer = new JPanel();
        footer.setOpaque(false);
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.add(emotionCard);
        footer.add(Box.createVerticalStrut(10));
        footer.add(hint);

        body.add(chartCard, BorderLayout.CENTER);
        body.add(footer, BorderLayout.SOUTH);

        return body;
    }

    private void loadData() {
        model.load(selectedRangeIndex);
        renderer.invalidate();
        if (chartCanvas != null) {
            chartCanvas.repaint();
        }
        if (emotionTrendCanvas != null) {
            emotionTrendCanvas.repaint();
        }
        refreshDominantEmotionSummary();
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

    private JPanel createEmotionInsightsCard() {
        JPanel emotionCard = new ChartCard();
        emotionCard.setLayout(new BorderLayout());
        emotionCard.setOpaque(false);
        emotionCard.setPreferredSize(new Dimension(10, 196));

        JPanel inset = new JPanel(new BorderLayout(0, 8));
        inset.setOpaque(false);
        inset.setBorder(new EmptyBorder(12, 14, 12, 14));

        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);

        JLabel title = new JLabel("Emotion Stacks");
        title.setForeground(TEXT_PRIMARY);
        title.setFont(AeroTheme.defaultBoldFont(14f));

        dominantEmotionLabel = new JLabel("Dominant this week: no detailed emotion data yet.");
        dominantEmotionLabel.setForeground(TEXT_SECONDARY);
        dominantEmotionLabel.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 12f));

        header.add(title, BorderLayout.WEST);
        header.add(dominantEmotionLabel, BorderLayout.EAST);

        emotionTrendCanvas = new EmotionTrendCanvas();
        emotionTrendCanvas.setPreferredSize(new Dimension(10, 110));

        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        legend.setOpaque(false);
        for (int i = 0; i < 8; i++) {
            legend.add(new EmotionLegendPill(
                    main.ui.features.entries.DetailedMoodPanel.emotionName(i),
                    main.ui.features.entries.DetailedMoodPanel.emotionColor(i)
            ));
        }

        inset.add(header, BorderLayout.NORTH);
        inset.add(emotionTrendCanvas, BorderLayout.CENTER);
        inset.add(legend, BorderLayout.SOUTH);
        emotionCard.add(inset, BorderLayout.CENTER);
        return emotionCard;
    }

    private void refreshDominantEmotionSummary() {
        if (dominantEmotionLabel == null) return;
        List<LocalDate> days = model.getDays();
        if (days.isEmpty()) {
            dominantEmotionLabel.setText("Dominant this week: no detailed emotion data yet.");
            return;
        }

        LocalDate latest = days.get(days.size() - 1);
        LocalDate weekStart = latest.minusDays(6);
        double[] sums = new double[8];
        int[] counts = new int[8];

        for (LocalDate day : days) {
            if (day.isBefore(weekStart)) continue;
            double[] avg = averageEmotionValuesForDay(day);
            if (avg == null) continue;
            for (int i = 0; i < avg.length; i++) {
                if (avg[i] < 0) continue;
                sums[i] += avg[i];
                counts[i]++;
            }
        }

        int dominantIndex = -1;
        double dominantAvg = -1d;
        for (int i = 0; i < sums.length; i++) {
            if (counts[i] <= 0) continue;
            double avg = sums[i] / counts[i];
            if (avg > dominantAvg) {
                dominantAvg = avg;
                dominantIndex = i;
            }
        }

        if (dominantIndex < 0) {
            dominantEmotionLabel.setText("Dominant this week: no detailed emotion data yet.");
            return;
        }

        int rounded = (int) Math.round(dominantAvg);
        int intensity = Math.max(0, Math.min(100, Math.abs(rounded - 50) * 2));
        dominantEmotionLabel.setText("Dominant this week: "
                + main.ui.features.entries.DetailedMoodPanel.emotionName(dominantIndex)
                + " • " + rounded + " (" + intensity + "% intensity)");
    }

    private double[] averageEmotionValuesForDay(LocalDate day) {
        if (day == null) return null;
        java.util.List<MoodChartModel.Details> details = model.getDetailsByDate().get(day);
        if (details == null || details.isEmpty()) return null;

        double[] sums = new double[8];
        int[] counts = new int[8];
        for (MoodChartModel.Details item : details) {
            if (item == null) continue;
            for (int i = 0; i < 8; i++) {
                int value = detailAt(item, i);
                if (value < 0) continue;
                sums[i] += value;
                counts[i]++;
            }
        }

        boolean any = false;
        double[] out = new double[8];
        for (int i = 0; i < out.length; i++) {
            if (counts[i] > 0) {
                out[i] = sums[i] / counts[i];
                any = true;
            } else {
                out[i] = -1d;
            }
        }
        return any ? out : null;
    }

    private int detailAt(MoodChartModel.Details detail, int index) {
        return switch (index) {
            case 0 -> detail.joy;
            case 1 -> detail.calm;
            case 2 -> detail.gratitude;
            case 3 -> detail.energy;
            case 4 -> detail.sadness;
            case 5 -> detail.anger;
            case 6 -> detail.anxiety;
            case 7 -> detail.stress;
            default -> -1;
        };
    }

    private Font resolveTitleFont(float size) {
        Font f = new Font("Palatino Linotype", Font.BOLD, Math.round(size));
        if (f.getFamily() == null || f.getFamily().equalsIgnoreCase("Dialog")) {
            f = new Font("Georgia", Font.BOLD, Math.round(size));
        }
        return f.deriveFont(size);
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

            addMouseListener(new MouseAdapter() {
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

            g2.dispose();
        }
    }

    private class EmotionTrendCanvas extends JComponent {
        private static final int MARGIN_LEFT = 10;
        private static final int MARGIN_RIGHT = 10;
        private static final int MARGIN_TOP = 12;
        private static final int MARGIN_BOTTOM = 24;

        EmotionTrendCanvas() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            int w = getWidth();
            int h = getHeight();
            int chartW = Math.max(1, w - MARGIN_LEFT - MARGIN_RIGHT);
            int chartH = Math.max(1, h - MARGIN_TOP - MARGIN_BOTTOM);
            int baseY = MARGIN_TOP + chartH;

            List<LocalDate> days = model.getDays();
            if (days.isEmpty()) {
                drawNoDetails(g2, w, h);
                g2.dispose();
                return;
            }

            int n = days.size();
            float slotW = chartW / (float) n;
            int barW = Math.max(3, Math.min(18, Math.round(slotW - 3f)));

            boolean anyDetailedDay = false;
            for (int i = 0; i < n; i++) {
                LocalDate day = days.get(i);
                double[] avg = averageEmotionValuesForDay(day);
                if (avg == null) continue;

                double sum = 0d;
                for (double v : avg) {
                    if (v >= 0) sum += v;
                }
                if (sum <= 0d) continue;
                anyDetailedDay = true;

                int x = Math.round(MARGIN_LEFT + i * slotW + (slotW - barW) * 0.5f);
                int yCursor = baseY;
                for (int emotionIdx = 0; emotionIdx < avg.length; emotionIdx++) {
                    double v = avg[emotionIdx];
                    if (v < 0) continue;
                    int segH = Math.max(1, (int) Math.round((v / sum) * chartH));
                    int y = Math.max(MARGIN_TOP, yCursor - segH);
                    Color fill = main.ui.features.entries.DetailedMoodPanel.emotionColor(emotionIdx);
                    g2.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 205));
                    g2.fillRect(x, y, barW, yCursor - y);
                    yCursor = y;
                    if (yCursor <= MARGIN_TOP) break;
                }
            }

            if (!anyDetailedDay) {
                drawNoDetails(g2, w, h);
                g2.dispose();
                return;
            }

            g2.setColor(new Color(176, 188, 204, 190));
            g2.setStroke(new BasicStroke(1f));
            g2.drawLine(MARGIN_LEFT, baseY, w - MARGIN_RIGHT, baseY);
            g2.drawLine(MARGIN_LEFT, MARGIN_TOP, MARGIN_LEFT, baseY);

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d");
            int labelStep = Math.max(1, n / 6);
            g2.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 10.5f));
            g2.setColor(TEXT_MUTED);
            for (int i = 0; i < n; i += labelStep) {
                String label = fmt.format(days.get(i));
                int x = Math.round(MARGIN_LEFT + i * slotW + slotW * 0.5f);
                int textW = g2.getFontMetrics().stringWidth(label);
                g2.drawString(label, x - textW / 2, h - 6);
            }

            g2.dispose();
        }

        private void drawNoDetails(Graphics2D g2, int w, int h) {
            g2.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 12f));
            g2.setColor(TEXT_SECONDARY);
            String msg = "Detailed emotion stacks appear after using emotion chips while saving entries.";
            int sw = g2.getFontMetrics().stringWidth(msg);
            g2.drawString(msg, Math.max(8, (w - sw) / 2), Math.max(24, h / 2));
        }
    }

    private static final class EmotionLegendPill extends JLabel {
        private final Color baseColor;

        private EmotionLegendPill(String text, Color baseColor) {
            super(text);
            this.baseColor = baseColor;
            setOpaque(false);
            setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 10.5f));
            setForeground(new Color(54, 60, 72));
            setBorder(new EmptyBorder(2, 8, 2, 8));
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            d.height = Math.max(20, d.height);
            return d;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int arc = Math.max(12, h - 6);
            Color top = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 78);
            Color bottom = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 48);
            g2.setPaint(new LinearGradientPaint(0, 0, 0, h,
                    new float[]{0f, 1f},
                    new Color[]{top, bottom}));
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.setColor(new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 130));
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
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
