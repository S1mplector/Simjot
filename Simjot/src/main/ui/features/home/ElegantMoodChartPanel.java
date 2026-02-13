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
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.LinearGradientPaint;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.RoundRectangle2D;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import main.core.analytics.MoodAnalyticsEngine.AnalyticsResult;
import main.core.analytics.MoodAnalyticsEngine.DailyStats;
import main.core.analytics.MoodAnalyticsEngine.MoodSample;
import main.core.analytics.mood.EmotionBalanceEngine;
import main.core.analytics.mood.EmotionCoMovementAnalyzer;
import main.core.analytics.mood.EmotionDominanceEngine;
import main.core.analytics.mood.EmotionStackAggregator;
import main.core.analytics.mood.MoodAnalysisCache;
import main.core.analytics.mood.MoodAnomalyDetector;
import main.core.analytics.mood.MoodChangePointDetector;
import main.core.analytics.mood.MoodCoverageAnalyzer;
import main.core.analytics.mood.MoodEmotionCatalog;
import main.core.analytics.mood.MoodInsightComposer;
import main.core.analytics.mood.MoodMomentumEngine;
import main.core.analytics.mood.MoodRegimeSegmenter;
import main.core.analytics.mood.MoodSemanticLabeler;
import main.core.analytics.mood.MoodSeriesResampler;
import main.core.analytics.mood.MoodVolatilityEngine;
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
    private static final int EMOTION_COUNT = 8;
    private static volatile Integer requestedRangeIndex = null;

    public static void requestRangeSelection(int rangeIndex) {
        requestedRangeIndex = switch (rangeIndex) {
            case 0, 1, 2, 3, 4 -> rangeIndex;
            default -> 1;
        };
    }

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
    private JLabel emotionBalanceLabel;
    private JLabel emotionShiftLabel;

    private AtlasMetricCard overallMetricCard;
    private AtlasMetricCard volatilityMetricCard;
    private AtlasMetricCard streakMetricCard;
    private AtlasMetricCard samplesMetricCard;
    private AtlasMetricCard detailCoverageMetricCard;
    private JLabel atlasHintLabel;
    private InspectorDrawer inspectorDrawer;

    private int hoveredEmotionDayIndex = -1;
    private int hoveredChartIndex = -1;
    private int selectedChartIndex = -1;
    private List<EmotionStackAggregator.EmotionStack> emotionStacks = List.of();
    private Map<LocalDate, EmotionStackAggregator.EmotionStack> emotionStacksByDay = Map.of();
    private Map<LocalDate, double[]> emotionAveragesByDay = Map.of();
    private EmotionDominanceEngine.Result currentDominance = EmotionDominanceEngine.Result.empty();
    private EmotionDominanceEngine.Result previousDominance = EmotionDominanceEngine.Result.empty();
    private EmotionBalanceEngine.BalanceResult balanceResult = EmotionBalanceEngine.BalanceResult.empty();
    private MoodCoverageAnalyzer.CoverageResult coverageResult = MoodCoverageAnalyzer.CoverageResult.empty();
    private MoodVolatilityEngine.VolatilityResult volatilityResult = MoodVolatilityEngine.VolatilityResult.empty();
    private MoodMomentumEngine.MomentumResult momentumResult = MoodMomentumEngine.MomentumResult.empty();
    private List<MoodAnomalyDetector.AnomalyPoint> anomalyPoints = List.of();
    private List<MoodRegimeSegmenter.RegimeSegment> regimeSegments = List.of();
    private List<MoodChangePointDetector.ChangePoint> changePoints = List.of();
    private EmotionCoMovementAnalyzer.Result coMovementResult = EmotionCoMovementAnalyzer.Result.empty();
    private Map<Integer, MoodAnomalyDetector.AnomalyPoint> anomalyByIndex = Map.of();
    private Map<Integer, MoodChangePointDetector.ChangePoint> changePointByIndex = Map.of();

    private final MoodSeriesResampler seriesResampler = new MoodSeriesResampler();
    private final MoodVolatilityEngine volatilityEngine = new MoodVolatilityEngine();
    private final MoodMomentumEngine momentumEngine = new MoodMomentumEngine();
    private final EmotionStackAggregator emotionStackAggregator = new EmotionStackAggregator();
    private final EmotionCoMovementAnalyzer coMovementAnalyzer = new EmotionCoMovementAnalyzer();
    private final EmotionDominanceEngine dominanceEngine = new EmotionDominanceEngine();
    private final EmotionBalanceEngine balanceEngine = new EmotionBalanceEngine();
    private final MoodCoverageAnalyzer coverageAnalyzer = new MoodCoverageAnalyzer();
    private final MoodAnomalyDetector anomalyDetector = new MoodAnomalyDetector();
    private final MoodChangePointDetector changePointDetector = new MoodChangePointDetector();
    private final MoodRegimeSegmenter regimeSegmenter = new MoodRegimeSegmenter();
    private final MoodSemanticLabeler semanticLabeler = new MoodSemanticLabeler();
    private final MoodInsightComposer insightComposer = new MoodInsightComposer();
    private final MoodAnalysisCache analysisCache = new MoodAnalysisCache(64);

    public ElegantMoodChartPanel(JournalApp app, CardLayout cardLayout, JPanel cardPanel) {
        this.app = app;
        settings.setShowTrend(false);
        settings.setShowEntryTicks(false);
        setLayout(new BorderLayout());
        setOpaque(true);

        add(createHeader(), BorderLayout.NORTH);
        add(createBody(), BorderLayout.CENTER);

        applyRequestedRangeIfPresent();
        loadData();
        startReveal();

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                applyRequestedRangeIfPresent();
                loadData();
                startReveal();
            }

            @Override
            public void componentResized(ComponentEvent e) {
                renderer.invalidate();
            }
        });
    }

    private void applyRequestedRangeIfPresent() {
        Integer requested = requestedRangeIndex;
        if (requested != null) {
            selectedRangeIndex = requested;
            requestedRangeIndex = null;
        }
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

        JLabel title = new JLabel("Mood Summary");
        title.setFont(resolveTitleFont(24f));
        title.setForeground(TEXT_PRIMARY);

        JLabel subtitle = new JLabel("A compact view of all of your mood data");
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

        JPanel overviewRow = createOverviewRow();

        JPanel chartCard = new ChartCard();
        chartCard.setLayout(new BorderLayout());
        chartCard.setOpaque(false);
        chartCanvas = new ChartCanvas();
        inspectorDrawer = new InspectorDrawer();

        JPanel chartInset = new JPanel(new BorderLayout(12, 0));
        chartInset.setOpaque(false);
        chartInset.setBorder(new EmptyBorder(14, 14, 16, 14));
        chartInset.add(chartCanvas, BorderLayout.CENTER);
        chartInset.add(inspectorDrawer, BorderLayout.EAST);
        chartCard.add(chartInset, BorderLayout.CENTER);

        atlasHintLabel = new JLabel("Tip: Click a point to open the nearest entry. Hold Cmd for closest by time.");
        atlasHintLabel.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 11.5f));
        atlasHintLabel.setForeground(TEXT_MUTED);
        atlasHintLabel.setHorizontalAlignment(JLabel.CENTER);

        JPanel emotionCard = createEmotionInsightsCard();

        JPanel footer = new JPanel();
        footer.setOpaque(false);
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.add(emotionCard);
        footer.add(Box.createVerticalStrut(10));
        footer.add(atlasHintLabel);

        body.add(overviewRow, BorderLayout.NORTH);
        body.add(chartCard, BorderLayout.CENTER);
        body.add(footer, BorderLayout.SOUTH);

        return body;
    }

    private void loadData() {
        model.load(selectedRangeIndex);
        hoveredEmotionDayIndex = -1;
        hoveredChartIndex = -1;
        renderer.invalidate();
        recomputeDerivedAnalytics();
        ensureValidInspectorSelection();
        if (chartCanvas != null) {
            chartCanvas.repaint();
        }
        if (emotionTrendCanvas != null) {
            emotionTrendCanvas.setToolTipText(null);
            emotionTrendCanvas.repaint();
        }
        refreshAtlasMetrics();
        refreshEmotionSummaries();
        refreshHintSummary();
        refreshInspectorDrawer();
        repaint();
    }

    private JPanel createOverviewRow() {
        JPanel row = new JPanel(new GridLayout(1, 5, 10, 0));
        row.setOpaque(false);
        row.setPreferredSize(new Dimension(10, 84));

        overallMetricCard = new AtlasMetricCard("Overall");
        volatilityMetricCard = new AtlasMetricCard("Volatility");
        streakMetricCard = new AtlasMetricCard("Streak");
        samplesMetricCard = new AtlasMetricCard("Samples");
        detailCoverageMetricCard = new AtlasMetricCard("Detail Coverage");

        row.add(overallMetricCard);
        row.add(volatilityMetricCard);
        row.add(streakMetricCard);
        row.add(samplesMetricCard);
        row.add(detailCoverageMetricCard);
        return row;
    }

    private void recomputeDerivedAnalytics() {
        List<LocalDate> days = model.getDays();
        List<Double> values = model.getValues();
        if (days.isEmpty() || values.isEmpty()) {
            emotionStacks = List.of();
            emotionStacksByDay = Map.of();
            emotionAveragesByDay = Map.of();
            currentDominance = EmotionDominanceEngine.Result.empty();
            previousDominance = EmotionDominanceEngine.Result.empty();
            balanceResult = EmotionBalanceEngine.BalanceResult.empty();
            coverageResult = MoodCoverageAnalyzer.CoverageResult.empty();
            volatilityResult = MoodVolatilityEngine.VolatilityResult.empty();
            momentumResult = MoodMomentumEngine.MomentumResult.empty();
            anomalyPoints = List.of();
            regimeSegments = List.of();
            changePoints = List.of();
            coMovementResult = EmotionCoMovementAnalyzer.Result.empty();
            anomalyByIndex = Map.of();
            changePointByIndex = Map.of();
            return;
        }

        long fingerprint = computeModelFingerprint(days, values, model.getDetailsByDate());
        String cachePrefix = "atlas:" + selectedRangeIndex + ":";

        Map<LocalDate, Double> valuesByDay = new LinkedHashMap<>(days.size());
        for (int i = 0; i < days.size(); i++) {
            valuesByDay.put(days.get(i), values.get(i));
        }

        MoodSeriesResampler.ResampledSeries interpolatedSeries = analysisCache.getOrCompute(
                cachePrefix + "series:interpolate",
                fingerprint,
                30_000L,
                () -> seriesResampler.resampleDaily(
                        valuesByDay,
                        days.get(0),
                        days.get(days.size() - 1),
                        MoodSeriesResampler.GapPolicy.INTERPOLATE
                )
        );

        emotionAveragesByDay = analysisCache.getOrCompute(
                cachePrefix + "emotionAverages",
                fingerprint,
                30_000L,
                this::buildDailyEmotionAverages
        );

        emotionStacks = analysisCache.getOrCompute(
                cachePrefix + "emotionStacks",
                fingerprint,
                30_000L,
                () -> emotionStackAggregator.aggregate(
                        days,
                        emotionAveragesByDay,
                        EmotionStackAggregator.WeightingMode.HYBRID
                )
        );
        emotionStacksByDay = emotionStackAggregator.indexByDay(emotionStacks);

        volatilityResult = analysisCache.getOrCompute(
                cachePrefix + "volatility",
                fingerprint,
                30_000L,
                () -> volatilityEngine.analyze(interpolatedSeries.values, 7)
        );

        momentumResult = analysisCache.getOrCompute(
                cachePrefix + "momentum",
                fingerprint,
                30_000L,
                () -> momentumEngine.analyze(interpolatedSeries.values, 7)
        );

        anomalyPoints = analysisCache.getOrCompute(
                cachePrefix + "anomalies",
                fingerprint,
                30_000L,
                () -> anomalyDetector.detect(days, values, 2.8d)
        );
        anomalyByIndex = indexAnomalies(anomalyPoints);

        changePoints = analysisCache.getOrCompute(
                cachePrefix + "changePoints",
                fingerprint,
                30_000L,
                () -> changePointDetector.detect(
                        interpolatedSeries.days,
                        interpolatedSeries.values,
                        4,
                        14.0d,
                        4.5d
                )
        );
        changePointByIndex = indexChangePoints(changePoints);

        regimeSegments = analysisCache.getOrCompute(
                cachePrefix + "regimes",
                fingerprint,
                30_000L,
                () -> regimeSegmenter.segment(interpolatedSeries.days, interpolatedSeries.values, 4)
        );

        coMovementResult = analysisCache.getOrCompute(
                cachePrefix + "coMovement",
                fingerprint,
                30_000L,
                () -> coMovementAnalyzer.analyze(days, emotionAveragesByDay)
        );

        LocalDate latest = days.get(days.size() - 1);
        LocalDate currentStart = latest.minusDays(6);
        LocalDate previousStart = latest.minusDays(13);
        LocalDate previousEnd = latest.minusDays(7);

        currentDominance = analysisCache.getOrCompute(
                cachePrefix + "dominance:current",
                fingerprint,
                30_000L,
                () -> dominanceEngine.analyze(emotionStacks, currentStart, latest)
        );

        previousDominance = analysisCache.getOrCompute(
                cachePrefix + "dominance:previous",
                fingerprint,
                30_000L,
                () -> dominanceEngine.analyze(emotionStacks, previousStart, previousEnd)
        );

        balanceResult = analysisCache.getOrCompute(
                cachePrefix + "balance",
                fingerprint,
                30_000L,
                () -> balanceEngine.analyze(emotionStacks, previousStart, latest)
        );

        coverageResult = analysisCache.getOrCompute(
                cachePrefix + "coverage",
                fingerprint,
                30_000L,
                () -> coverageAnalyzer.analyze(
                        values,
                        emotionStacks,
                        model.getTotalSamples(),
                        countDetailedSamples(model.getAnalytics()))
        );
    }

    private Map<LocalDate, double[]> buildDailyEmotionAverages() {
        Map<LocalDate, double[]> out = new HashMap<>();
        for (LocalDate day : model.getDays()) {
            double[] avg = averageEmotionValuesForDay(day);
            if (avg != null) {
                out.put(day, avg);
            }
        }
        return out;
    }

    private Map<Integer, MoodAnomalyDetector.AnomalyPoint> indexAnomalies(List<MoodAnomalyDetector.AnomalyPoint> anomalies) {
        if (anomalies == null || anomalies.isEmpty()) return Map.of();
        Map<Integer, MoodAnomalyDetector.AnomalyPoint> out = new HashMap<>();
        for (MoodAnomalyDetector.AnomalyPoint anomaly : anomalies) {
            if (anomaly == null || anomaly.index < 0) continue;
            out.put(anomaly.index, anomaly);
        }
        return out;
    }

    private Map<Integer, MoodChangePointDetector.ChangePoint> indexChangePoints(List<MoodChangePointDetector.ChangePoint> points) {
        if (points == null || points.isEmpty()) return Map.of();
        Map<Integer, MoodChangePointDetector.ChangePoint> out = new HashMap<>();
        for (MoodChangePointDetector.ChangePoint point : points) {
            if (point == null || point.index < 0) continue;
            out.put(point.index, point);
        }
        return out;
    }

    private void ensureValidInspectorSelection() {
        if (model.getDays().isEmpty()) {
            selectedChartIndex = -1;
            return;
        }
        if (!isValidMoodIndex(selectedChartIndex)) {
            selectedChartIndex = findLatestMoodIndex();
        }
    }

    private int findLatestMoodIndex() {
        List<Double> values = model.getValues();
        for (int i = values.size() - 1; i >= 0; i--) {
            Double v = values.get(i);
            if (v != null) return i;
        }
        return -1;
    }

    private boolean isValidMoodIndex(int idx) {
        if (idx < 0 || idx >= model.getDays().size()) return false;
        List<Double> values = model.getValues();
        return idx < values.size() && values.get(idx) != null;
    }

    private void refreshInspectorDrawer() {
        if (inspectorDrawer == null) return;
        if (!isValidMoodIndex(selectedChartIndex)) {
            inspectorDrawer.showNoSelection(coMovementResult, regimeSegments, changePoints);
            return;
        }
        int idx = hoveredChartIndex >= 0 ? hoveredChartIndex : selectedChartIndex;
        if (!isValidMoodIndex(idx)) {
            idx = selectedChartIndex;
        }
        inspectorDrawer.showDay(buildInspectorState(idx), coMovementResult);
    }

    private InspectorState buildInspectorState(int idx) {
        LocalDate day = model.getDays().get(idx);
        Double mood = model.getValues().get(idx);
        Double delta = null;
        for (int i = idx - 1; i >= 0; i--) {
            Double previous = model.getValues().get(i);
            if (previous != null) {
                delta = mood - previous;
                break;
            }
        }

        MoodAnomalyDetector.AnomalyPoint anomaly = anomalyByIndex.get(idx);
        MoodChangePointDetector.ChangePoint changePoint = nearestChangePoint(idx);
        MoodRegimeSegmenter.RegimeSegment regime = regimeAt(day);
        EmotionStackAggregator.EmotionStack stack = emotionStacksByDay.get(day);
        List<EmotionScore> topEmotions = topEmotionScores(stack, 3);

        return new InspectorState(
                idx,
                day,
                mood,
                delta,
                MoodAnalyticsEngine.categorize(mood),
                anomaly,
                changePoint,
                regime,
                topEmotions
        );
    }

    private MoodChangePointDetector.ChangePoint nearestChangePoint(int idx) {
        MoodChangePointDetector.ChangePoint exact = changePointByIndex.get(idx);
        if (exact != null) return exact;
        if (changePoints == null || changePoints.isEmpty()) return null;
        MoodChangePointDetector.ChangePoint best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (MoodChangePointDetector.ChangePoint point : changePoints) {
            if (point == null) continue;
            int distance = Math.abs(point.index - idx);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = point;
            }
        }
        if (bestDistance > 7) {
            return null;
        }
        return best;
    }

    private MoodRegimeSegmenter.RegimeSegment regimeAt(LocalDate day) {
        if (day == null || regimeSegments == null || regimeSegments.isEmpty()) return null;
        for (MoodRegimeSegmenter.RegimeSegment segment : regimeSegments) {
            if (segment == null || segment.start == null || segment.end == null) continue;
            if (!day.isBefore(segment.start) && !day.isAfter(segment.end)) {
                return segment;
            }
        }
        return null;
    }

    private List<EmotionScore> topEmotionScores(EmotionStackAggregator.EmotionStack stack, int maxCount) {
        if (stack == null || !stack.hasData) return List.of();
        List<EmotionScore> scores = new ArrayList<>(EMOTION_COUNT);
        for (int i = 0; i < EMOTION_COUNT; i++) {
            double valueRaw = i < stack.values.length ? stack.values[i] : -1d;
            if (valueRaw < 0) continue;
            int value = (int) Math.round(valueRaw);
            int intensity = (int) Math.round(i < stack.intensities.length ? stack.intensities[i] : 0d);
            scores.add(new EmotionScore(i, value, intensity));
        }
        scores.sort(Comparator
                .comparingInt((EmotionScore s) -> s.intensity).reversed()
                .thenComparingInt((EmotionScore s) -> s.value).reversed());
        if (scores.size() <= maxCount) return scores;
        return new ArrayList<>(scores.subList(0, maxCount));
    }

    private long computeModelFingerprint(List<LocalDate> days,
                                         List<Double> values,
                                         Map<LocalDate, List<MoodChartModel.Details>> detailsByDay) {
        long hash = 1469598103934665603L;
        hash = (hash ^ selectedRangeIndex) * 1099511628211L;
        for (int i = 0; i < days.size(); i++) {
            LocalDate day = days.get(i);
            hash = (hash ^ day.toEpochDay()) * 1099511628211L;
            Double value = i < values.size() ? values.get(i) : null;
            long bits = value == null ? 0L : Double.doubleToLongBits(value);
            hash = (hash ^ bits) * 1099511628211L;
            List<MoodChartModel.Details> details = detailsByDay.get(day);
            int detailsCount = details == null ? 0 : details.size();
            hash = (hash ^ detailsCount) * 1099511628211L;
        }
        hash = (hash ^ model.getTotalSamples()) * 1099511628211L;
        return hash;
    }

    private void refreshEmotionSummaries() {
        if (dominantEmotionLabel != null) {
            dominantEmotionLabel.setText(insightComposer.composeDominantLine(currentDominance, semanticLabeler));
        }
        if (emotionBalanceLabel != null) {
            emotionBalanceLabel.setText(insightComposer.composeBalanceLine(balanceResult));
        }
        if (emotionShiftLabel != null) {
            emotionShiftLabel.setText(insightComposer.composeShiftLine(currentDominance, previousDominance, semanticLabeler));
        }
    }

    private void refreshHintSummary() {
        if (atlasHintLabel == null) return;
        String trend = insightComposer.composeMomentumLine(momentumResult);
        String regime = insightComposer.composeRegimeLine(regimeSegments);
        String anomalies = insightComposer.composeAnomalyLine(anomalyPoints);
        String shifts = describeChangePointSummary();
        String coMove = describeCoMovementSummary();
        atlasHintLabel.setText(trend + " • " + regime + " • " + anomalies + " • " + shifts + " • " + coMove);
    }

    private String describeChangePointSummary() {
        if (changePoints == null || changePoints.isEmpty()) {
            return "Shifts: none";
        }
        MoodChangePointDetector.ChangePoint latest = changePoints.get(changePoints.size() - 1);
        String direction = latest.type == MoodChangePointDetector.ShiftType.UPWARD ? "upward" : "downward";
        return "Shifts: " + changePoints.size() + " (" + direction + " near " + latest.day + ")";
    }

    private String describeCoMovementSummary() {
        if (coMovementResult == null || !coMovementResult.hasData()) {
            return "Co-movement: insufficient detail";
        }
        EmotionCoMovementAnalyzer.Pair pair = coMovementResult.strongestPositive;
        if (pair == null) {
            pair = coMovementResult.strongestNegative;
        }
        if (pair == null) {
            return "Co-movement: insufficient detail";
        }
        return "Co-movement: " + coMovementAnalyzer.describePair(pair);
    }

    private void refreshAtlasMetrics() {
        if (overallMetricCard == null) return;

        AnalyticsResult analytics = model.getAnalytics();
        if (analytics == null || analytics.dates.isEmpty()) {
            overallMetricCard.setMetric("--", "No mood data", new Color(140, 148, 162));
            volatilityMetricCard.setMetric("--", "No mood data", new Color(140, 148, 162));
            streakMetricCard.setMetric("--", "No mood data", new Color(140, 148, 162));
            samplesMetricCard.setMetric("--", "No mood data", new Color(140, 148, 162));
            detailCoverageMetricCard.setMetric("--", "No mood data", new Color(140, 148, 162));
            return;
        }

        double overall = model.getOverallAverage();
        overallMetricCard.setMetric(
                String.format("%.0f / 100", overall),
                semanticLabeler.labelComposite(overall),
                MoodAnalyticsEngine.getColor(overall)
        );

        double volatility = volatilityResult.standardDeviation;
        volatilityMetricCard.setMetric(
                String.format("%.1f", volatility),
                volatilityEngine.bandLabel(volatilityResult.band),
                colorForVolatilityBand(volatilityResult.band)
        );

        int streak = model.getCurrentStreak();
        String trendTail = " · " + momentumResult.arrow() + " " + momentumResult.label();
        if (streak > 0) {
            streakMetricCard.setMetric(streak + "d", "Good run" + trendTail, new Color(52, 168, 97));
        } else if (streak < 0) {
            streakMetricCard.setMetric((-streak) + "d", "Challenging" + trendTail, new Color(198, 92, 72));
        } else {
            streakMetricCard.setMetric("0d", "No active streak" + trendTail, new Color(132, 140, 154));
        }

        int totalSamples = model.getTotalSamples();
        int daysWithMood = coverageResult.daysWithMood;
        double logsPerWeek = daysWithMood > 0 ? (totalSamples * 7.0) / daysWithMood : 0d;
        samplesMetricCard.setMetric(
                String.valueOf(totalSamples),
                String.format("%.1f logs/week", logsPerWeek),
                new Color(64, 126, 198)
        );

        int detailedSamples = coverageResult.detailedSamples;
        int coverage = coverageResult.detailCoveragePercent;
        detailCoverageMetricCard.setMetric(
                coverage + "%",
                detailedSamples + "/" + totalSamples + " detailed • " + coverageResult.reliabilityLabel,
                colorForCoverage(coverage)
        );
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
        emotionCard.setPreferredSize(new Dimension(10, 224));

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
        dominantEmotionLabel.setHorizontalAlignment(JLabel.RIGHT);
        dominantEmotionLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

        emotionBalanceLabel = new JLabel("Balance: no detailed emotion data yet.");
        emotionBalanceLabel.setForeground(TEXT_SECONDARY);
        emotionBalanceLabel.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 11.5f));
        emotionBalanceLabel.setHorizontalAlignment(JLabel.RIGHT);
        emotionBalanceLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

        emotionShiftLabel = new JLabel("Shift: need two weeks of detail to compare.");
        emotionShiftLabel.setForeground(TEXT_MUTED);
        emotionShiftLabel.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 11.5f));
        emotionShiftLabel.setHorizontalAlignment(JLabel.RIGHT);
        emotionShiftLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

        JPanel summaryBlock = new JPanel();
        summaryBlock.setOpaque(false);
        summaryBlock.setLayout(new BoxLayout(summaryBlock, BoxLayout.Y_AXIS));
        summaryBlock.add(dominantEmotionLabel);
        summaryBlock.add(Box.createVerticalStrut(2));
        summaryBlock.add(emotionBalanceLabel);
        summaryBlock.add(Box.createVerticalStrut(2));
        summaryBlock.add(emotionShiftLabel);

        header.add(title, BorderLayout.WEST);
        header.add(summaryBlock, BorderLayout.EAST);

        emotionTrendCanvas = new EmotionTrendCanvas();
        emotionTrendCanvas.setPreferredSize(new Dimension(10, 110));

        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        legend.setOpaque(false);
        for (int i = 0; i < EMOTION_COUNT; i++) {
            legend.add(new EmotionLegendPill(
                    MoodEmotionCatalog.emotionName(i),
                    main.ui.features.entries.DetailedMoodPanel.emotionColor(i)
            ));
        }

        inset.add(header, BorderLayout.NORTH);
        inset.add(emotionTrendCanvas, BorderLayout.CENTER);
        inset.add(legend, BorderLayout.SOUTH);
        emotionCard.add(inset, BorderLayout.CENTER);
        return emotionCard;
    }

    private Color colorForVolatilityBand(MoodVolatilityEngine.Band band) {
        if (band == MoodVolatilityEngine.Band.STABLE) return new Color(62, 160, 104);
        if (band == MoodVolatilityEngine.Band.VARIABLE) return new Color(214, 150, 62);
        return new Color(200, 95, 72);
    }

    private Color colorForCoverage(int coveragePercent) {
        if (coveragePercent >= 75) return new Color(62, 160, 104);
        if (coveragePercent >= 45) return new Color(214, 150, 62);
        return new Color(200, 95, 72);
    }

    private int countDetailedSamples(AnalyticsResult analytics) {
        if (analytics == null || analytics.dailyStats == null || analytics.dailyStats.isEmpty()) return 0;
        int count = 0;
        for (DailyStats stats : analytics.dailyStats.values()) {
            if (stats == null || stats.sampleCount <= 0) continue;
            if (stats.samples != null && !stats.samples.isEmpty()) {
                for (MoodSample sample : stats.samples) {
                    if (sample != null && sample.hasDetails()) {
                        count++;
                    }
                }
            } else if (hasAnyDetailedAverages(stats)) {
                count += stats.sampleCount;
            }
        }
        return count;
    }

    private boolean hasAnyDetailedAverages(DailyStats stats) {
        return stats.avgJoy >= 0 || stats.avgCalm >= 0 || stats.avgGratitude >= 0 || stats.avgEnergy >= 0
                || stats.avgSadness >= 0 || stats.avgAnger >= 0 || stats.avgAnxiety >= 0 || stats.avgStress >= 0;
    }

    private double[] averageEmotionValuesForDay(LocalDate day) {
        if (day == null) return null;
        java.util.List<MoodChartModel.Details> details = model.getDetailsByDate().get(day);
        if (details == null || details.isEmpty()) return null;

        double[] sums = new double[EMOTION_COUNT];
        int[] counts = new int[EMOTION_COUNT];
        for (MoodChartModel.Details item : details) {
            if (item == null) continue;
            for (int i = 0; i < EMOTION_COUNT; i++) {
                int value = detailAt(item, i);
                if (value < 0) continue;
                sums[i] += value;
                counts[i]++;
            }
        }

        boolean any = false;
        double[] out = new double[EMOTION_COUNT];
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
                public void mouseExited(MouseEvent e) {
                    hoveredChartIndex = -1;
                    refreshInspectorDrawer();
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    int idx = resolveMoodIndexAtX(e.getX());
                    if (idx >= 0) {
                        selectedChartIndex = idx;
                        refreshInspectorDrawer();
                    }
                    handleChartClick(e);
                }
            });

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int idx = resolveMoodIndexAtX(e.getX());
                    if (hoveredChartIndex != idx) {
                        hoveredChartIndex = idx;
                        refreshInspectorDrawer();
                        repaint();
                    }
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

            int focusIdx = hoveredChartIndex >= 0 ? hoveredChartIndex : selectedChartIndex;
            renderer.paint(g2, this, model.getDays(), model.getValues(), model.getEntriesByDate(),
                    focusIdx >= 0 ? focusIdx : null);
            paintChangePointMarkers(g2);
            paintAnomalyMarkers(g2);

            g2.dispose();
        }

        private int resolveMoodIndexAtX(int x) {
            if (model.getDays().isEmpty()) return -1;
            int n = model.getDays().size();
            int idx = renderer.indexForX(getWidth(), x, n);
            if (!isValidMoodIndex(idx)) return -1;
            return idx;
        }

        private void paintChangePointMarkers(Graphics2D g2) {
            if (changePoints == null || changePoints.isEmpty()) {
                return;
            }
            int top = Math.max(12, getHeight() / 10);
            int bottom = Math.max(top + 8, getHeight() - 58);
            java.awt.Stroke oldStroke = g2.getStroke();
            for (MoodChangePointDetector.ChangePoint point : changePoints) {
                if (point == null || point.index < 0) continue;
                int x = renderer.getX(point.index);
                if (x < 0) continue;

                Color color = point.type == MoodChangePointDetector.ShiftType.UPWARD
                        ? new Color(88, 168, 110, 170)
                        : new Color(206, 108, 88, 170);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(
                        1.1f,
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND,
                        10f,
                        new float[]{3f, 3f},
                        0f));
                g2.drawLine(x, top, x, bottom);

                int markerY = top - 2;
                g2.setStroke(oldStroke);
                g2.fillOval(x - 3, markerY - 3, 6, 6);
            }
            g2.setStroke(oldStroke);
        }

        private void paintAnomalyMarkers(Graphics2D g2) {
            if (anomalyPoints == null || anomalyPoints.isEmpty()) {
                return;
            }
            for (MoodAnomalyDetector.AnomalyPoint anomaly : anomalyPoints) {
                if (anomaly == null || anomaly.index < 0) continue;
                int x = renderer.getX(anomaly.index);
                int y = renderer.getYDaily(anomaly.index);
                if (x < 0 || y == Integer.MIN_VALUE) continue;

                Color accent = anomaly.type == MoodAnomalyDetector.Type.SPIKE
                        ? new Color(242, 128, 76, 210)
                        : new Color(197, 92, 78, 210);
                g2.setColor(accent);
                g2.fillOval(x - 4, y - 4, 8, 8);
                g2.setColor(new Color(255, 255, 255, 220));
                g2.setStroke(new BasicStroke(1f));
                g2.drawOval(x - 5, y - 5, 10, 10);
            }
        }
    }

    private class EmotionTrendCanvas extends JComponent {
        private static final int MARGIN_LEFT = 10;
        private static final int MARGIN_RIGHT = 10;
        private static final int MARGIN_TOP = 12;
        private static final int MARGIN_BOTTOM = 24;

        EmotionTrendCanvas() {
            setOpaque(false);
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int idx = dayIndexForX(e.getX());
                    if (hoveredEmotionDayIndex != idx) {
                        hoveredEmotionDayIndex = idx;
                        repaint();
                    }
                    if (idx < 0 || idx >= model.getDays().size()) {
                        setToolTipText(null);
                        return;
                    }
                    LocalDate day = model.getDays().get(idx);
                    setToolTipText(buildEmotionTooltipForDay(day));
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent e) {
                    hoveredEmotionDayIndex = -1;
                    setToolTipText(null);
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    int idx = dayIndexForX(e.getX());
                    openDayEntriesFromEmotionChart(idx, e);
                }
            });
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
            int chartX = MARGIN_LEFT;
            int chartY = MARGIN_TOP;
            int rightX = w - MARGIN_RIGHT;

            List<LocalDate> days = model.getDays();
            if (days.isEmpty()) {
                drawNoDetails(g2, w, h);
                g2.dispose();
                return;
            }

            // Soft panel treatment for the stack plotting area.
            RoundRectangle2D plotRect = new RoundRectangle2D.Float(chartX, chartY, chartW, chartH, 12, 12);
            g2.setPaint(new LinearGradientPaint(
                    0, chartY, 0, baseY,
                    new float[]{0f, 1f},
                    new Color[]{
                            new Color(248, 252, 255, 145),
                            new Color(234, 241, 249, 115)
                    }));
            g2.fill(plotRect);
            g2.setColor(new Color(185, 198, 214, 110));
            g2.setStroke(new BasicStroke(1f));
            g2.draw(plotRect);

            // Quiet reference lines for intensity depth.
            g2.setColor(new Color(166, 178, 194, 88));
            g2.setStroke(new BasicStroke(1f));
            for (int i = 1; i <= 3; i++) {
                int y = chartY + Math.round((chartH * i) / 4f);
                g2.drawLine(chartX + 4, y, rightX - 4, y);
            }

            int n = days.size();
            float slotW = chartW / (float) n;
            int barW = Math.max(4, Math.min(20, Math.round(slotW * 0.62f)));

            boolean anyDetailedDay = false;
            for (int i = 0; i < n; i++) {
                LocalDate day = days.get(i);
                EmotionStackAggregator.EmotionStack stack = emotionStacksByDay.get(day);
                if (stack == null || !stack.hasData) continue;
                double[] intensity = stack.intensities;
                double intensitySum = 0d;
                int activeCount = 0;
                for (double v : intensity) {
                    if (v < 0) continue;
                    intensitySum += v;
                    activeCount++;
                }
                anyDetailedDay = true;

                int x = Math.round(MARGIN_LEFT + i * slotW + (slotW - barW) * 0.5f);
                if (activeCount <= 0) continue;

                if (intensitySum <= 0d) {
                    // Neutral day: details exist but all selected emotions were close to baseline.
                    g2.setColor(new Color(170, 178, 192, 185));
                    g2.fillRoundRect(x, baseY - 2, barW, 2, 2, 2);
                    continue;
                }

                double avgIntensity = activeCount > 0 ? intensitySum / activeCount : stack.averageIntensity;
                int stackHeight = Math.max(2, (int) Math.round((avgIntensity / 100d) * chartH));
                int yCursor = baseY;
                int stackTop = baseY;
                int remainingHeight = stackHeight;
                int remainingSegments = 0;
                for (double pct : stack.percentages) {
                    if (pct > 0d) remainingSegments++;
                }
                double remainingPct = 100d;
                int stackY = Math.max(chartY, baseY - stackHeight);
                int stackH = Math.max(1, baseY - stackY);
                int stackArc = Math.min(10, Math.max(6, barW - 1));
                RoundRectangle2D stackShape = new RoundRectangle2D.Float(x, stackY, barW, stackH, stackArc, stackArc);

                Shape oldClip = g2.getClip();
                g2.setClip(stackShape);

                for (int emotionIdx = 0; emotionIdx < stack.percentages.length; emotionIdx++) {
                    double pct = stack.percentages[emotionIdx];
                    if (pct <= 0d) continue;
                    int segH;
                    if (remainingSegments <= 1 || remainingPct <= 0d) {
                        segH = Math.max(1, remainingHeight);
                    } else {
                        segH = Math.max(1, (int) Math.round((pct / remainingPct) * remainingHeight));
                    }
                    segH = Math.min(segH, remainingHeight);
                    int y = Math.max(MARGIN_TOP, yCursor - segH);
                    int drawH = Math.max(1, yCursor - y);
                    Color fill = main.ui.features.entries.DetailedMoodPanel.emotionColor(emotionIdx);
                    Color segTop = mix(fill, Color.WHITE, 0.22f, 208);
                    Color segBottom = mix(fill, new Color(34, 42, 58), 0.08f, 226);
                    g2.setPaint(new LinearGradientPaint(
                            x, y, x, y + drawH,
                            new float[]{0f, 1f},
                            new Color[]{segTop, segBottom}
                    ));
                    g2.fillRect(x, y, barW, drawH);

                    // Hairline separators between emotion layers.
                    if (drawH > 1 && y > chartY + 1) {
                        g2.setColor(new Color(255, 255, 255, 56));
                        g2.drawLine(x, y, x + barW - 1, y);
                    }
                    remainingHeight -= (yCursor - y);
                    remainingPct -= pct;
                    remainingSegments--;
                    yCursor = y;
                    stackTop = Math.min(stackTop, y);
                    if (yCursor <= MARGIN_TOP) break;
                }

                g2.setClip(oldClip);

                // Top sheen and outer edge for a polished stack silhouette.
                Shape oldClip2 = g2.getClip();
                g2.setClip(stackShape);
                g2.setPaint(new LinearGradientPaint(
                        x, stackY, x, stackY + Math.max(1, stackH / 2f),
                        new float[]{0f, 1f},
                        new Color[]{
                                new Color(255, 255, 255, 78),
                                new Color(255, 255, 255, 0)
                        }
                ));
                g2.fillRect(x, stackY, barW, Math.max(2, stackH / 2));
                g2.setClip(oldClip2);

                g2.setColor(new Color(72, 88, 112, 84));
                g2.setStroke(new BasicStroke(1f));
                g2.draw(stackShape);

                if (i == hoveredEmotionDayIndex && stackTop < baseY) {
                    int hoverY = Math.max(chartY, stackTop - 3);
                    int hoverH = Math.max(5, baseY - stackTop + 4);
                    Rectangle glowRect = new Rectangle(x - 4, hoverY - 2, barW + 8, hoverH + 4);
                    AeroPainters.paintOuterGlow(g2, glowRect, 8, new Color(86, 152, 224, 138), 4, 96);
                    g2.setColor(new Color(74, 128, 198, 205));
                    g2.setStroke(new BasicStroke(1.35f));
                    g2.drawRoundRect(x - 2, hoverY, barW + 4, hoverH, 8, 8);
                    g2.setColor(new Color(128, 154, 186, 96));
                    g2.setStroke(new BasicStroke(1f));
                    int guideX = x + barW / 2;
                    g2.drawLine(guideX, chartY + 2, guideX, baseY);
                }
            }

            if (!anyDetailedDay) {
                drawNoDetails(g2, w, h);
                g2.dispose();
                return;
            }

            g2.setColor(new Color(162, 176, 196, 198));
            g2.setStroke(new BasicStroke(1.1f));
            g2.drawLine(chartX, baseY, rightX, baseY);
            g2.setColor(new Color(170, 184, 202, 172));
            g2.drawLine(chartX, chartY, chartX, baseY);

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

        private Color mix(Color a, Color b, double ratioA, int alpha) {
            double r = Math.max(0d, Math.min(1d, ratioA));
            double inv = 1d - r;
            int rr = (int) Math.round(a.getRed() * r + b.getRed() * inv);
            int gg = (int) Math.round(a.getGreen() * r + b.getGreen() * inv);
            int bb = (int) Math.round(a.getBlue() * r + b.getBlue() * inv);
            int aa = Math.max(0, Math.min(255, alpha));
            return new Color(rr, gg, bb, aa);
        }

        private int dayIndexForX(int x) {
            List<LocalDate> days = model.getDays();
            if (days.isEmpty()) return -1;
            int n = days.size();
            int chartW = Math.max(1, getWidth() - MARGIN_LEFT - MARGIN_RIGHT);
            float slotW = chartW / (float) n;
            if (slotW <= 0f) return -1;
            int idx = (int) Math.floor((x - MARGIN_LEFT) / slotW);
            if (idx < 0 || idx >= n) return -1;
            return idx;
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

    private static final class AtlasMetricCard extends JPanel {
        private final JLabel valueLabel;
        private final JLabel subtitleLabel;
        private Color accent = new Color(128, 138, 154);

        private AtlasMetricCard(String title) {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(new EmptyBorder(10, 12, 10, 12));

            JLabel titleLabel = new JLabel(title);
            titleLabel.setForeground(new Color(98, 110, 126));
            titleLabel.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 11f));
            titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            valueLabel = new JLabel("--");
            valueLabel.setForeground(TEXT_PRIMARY);
            valueLabel.setFont(AeroTheme.defaultBoldFont(17f));
            valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            subtitleLabel = new JLabel("No data yet");
            subtitleLabel.setForeground(new Color(114, 126, 142));
            subtitleLabel.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 10.5f));
            subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            add(titleLabel);
            add(Box.createVerticalStrut(4));
            add(valueLabel);
            add(Box.createVerticalStrut(2));
            add(subtitleLabel);
        }

        private void setMetric(String value, String subtitle, Color accent) {
            valueLabel.setText(value);
            subtitleLabel.setText(subtitle);
            if (accent != null) {
                this.accent = accent;
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int arc = 16;

            if (Theme.isPlainWhite()) {
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
                g2.setColor(new Color(214, 220, 230));
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            } else {
                Color top = new Color(255, 255, 255, 224);
                Color bottom = new Color(242, 247, 253, 224);
                g2.setPaint(new LinearGradientPaint(0, 0, 0, h, new float[]{0f, 1f}, new Color[]{top, bottom}));
                g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
                g2.setColor(new Color(190, 201, 216, 170));
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            }

            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 205));
            g2.fillRoundRect(10, 8, Math.max(14, w - 20), 3, 3, 3);
            g2.dispose();
        }
    }

    private static final class InspectorState {
        private final int index;
        private final LocalDate day;
        private final double mood;
        private final Double delta;
        private final String category;
        private final MoodAnomalyDetector.AnomalyPoint anomaly;
        private final MoodChangePointDetector.ChangePoint changePoint;
        private final MoodRegimeSegmenter.RegimeSegment regime;
        private final List<EmotionScore> topEmotions;

        private InspectorState(int index,
                               LocalDate day,
                               double mood,
                               Double delta,
                               String category,
                               MoodAnomalyDetector.AnomalyPoint anomaly,
                               MoodChangePointDetector.ChangePoint changePoint,
                               MoodRegimeSegmenter.RegimeSegment regime,
                               List<EmotionScore> topEmotions) {
            this.index = index;
            this.day = day;
            this.mood = mood;
            this.delta = delta;
            this.category = category;
            this.anomaly = anomaly;
            this.changePoint = changePoint;
            this.regime = regime;
            this.topEmotions = topEmotions == null ? List.of() : topEmotions;
        }
    }

    private final class InspectorDrawer extends JPanel {
        private final JLabel dateLabel = new JLabel("Inspector");
        private final JLabel moodLabel = new JLabel("Mood: --");
        private final JLabel deltaLabel = new JLabel("Delta: --");
        private final JLabel phaseLabel = new JLabel("Phase: --");
        private final JLabel anomalyLabel = new JLabel("Anomaly: --");
        private final JLabel shiftLabel = new JLabel("Shift: --");
        private final JLabel topEmotionLabel = new JLabel("Top emotions: --");
        private final JLabel coMoveLabel = new JLabel("Co-movement: --");
        private final JLabel antiMoveLabel = new JLabel("Counter-movement: --");
        private final CoMovementHeatmap heatmap = new CoMovementHeatmap();

        private InspectorDrawer() {
            setOpaque(false);
            setLayout(new BorderLayout(0, 8));
            setPreferredSize(new Dimension(292, 10));
            setMinimumSize(new Dimension(248, 10));
            setBorder(new EmptyBorder(8, 0, 0, 0));

            JPanel stack = new JPanel();
            stack.setOpaque(false);
            stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
            stack.setBorder(new EmptyBorder(12, 12, 12, 12));

            dateLabel.setFont(AeroTheme.defaultBoldFont(13.5f));
            dateLabel.setForeground(TEXT_PRIMARY);

            configureLine(moodLabel, TEXT_SECONDARY, 12f);
            configureLine(deltaLabel, TEXT_MUTED, 11.5f);
            configureLine(phaseLabel, TEXT_MUTED, 11.5f);
            configureLine(anomalyLabel, TEXT_MUTED, 11.5f);
            configureLine(shiftLabel, TEXT_MUTED, 11.5f);
            configureLine(topEmotionLabel, TEXT_SECONDARY, 11.5f);
            configureLine(coMoveLabel, TEXT_MUTED, 11f);
            configureLine(antiMoveLabel, TEXT_MUTED, 11f);

            stack.add(dateLabel);
            stack.add(Box.createVerticalStrut(8));
            stack.add(moodLabel);
            stack.add(Box.createVerticalStrut(3));
            stack.add(deltaLabel);
            stack.add(Box.createVerticalStrut(2));
            stack.add(phaseLabel);
            stack.add(Box.createVerticalStrut(2));
            stack.add(anomalyLabel);
            stack.add(Box.createVerticalStrut(2));
            stack.add(shiftLabel);
            stack.add(Box.createVerticalStrut(6));
            stack.add(topEmotionLabel);
            stack.add(Box.createVerticalStrut(6));
            stack.add(coMoveLabel);
            stack.add(Box.createVerticalStrut(2));
            stack.add(antiMoveLabel);
            stack.add(Box.createVerticalStrut(10));
            stack.add(heatmap);

            add(stack, BorderLayout.CENTER);
        }

        private void configureLine(JLabel label, Color color, float size) {
            label.setForeground(color);
            label.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, size));
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        private void showNoSelection(EmotionCoMovementAnalyzer.Result coMovement,
                                     List<MoodRegimeSegmenter.RegimeSegment> regimes,
                                     List<MoodChangePointDetector.ChangePoint> points) {
            dateLabel.setText("Inspector");
            moodLabel.setText("Mood: hover or click a point");
            deltaLabel.setText("Delta: --");
            phaseLabel.setText("Phase: " + describeLatestRegime(regimes));
            anomalyLabel.setText("Anomaly: --");
            shiftLabel.setText("Shifts: " + (points == null ? 0 : points.size()));
            topEmotionLabel.setText("Top emotions: --");
            applyCoMovement(coMovement);
            heatmap.setResult(coMovement);
        }

        private void showDay(InspectorState state, EmotionCoMovementAnalyzer.Result coMovement) {
            if (state == null) {
                showNoSelection(coMovement, regimeSegments, changePoints);
                return;
            }

            dateLabel.setText(state.day + "  •  Index " + (state.index + 1));
            moodLabel.setText("Mood: " + Math.round(state.mood) + " / 100  (" + state.category + ")");

            if (state.delta == null) {
                deltaLabel.setText("Delta: no previous day in range");
            } else {
                String arrow = state.delta >= 0 ? "↑" : "↓";
                deltaLabel.setText("Delta: " + arrow + " " + Math.abs(Math.round(state.delta)));
            }

            phaseLabel.setText("Phase: " + describeRegime(state.regime));

            if (state.anomaly == null) {
                anomalyLabel.setText("Anomaly: none");
            } else {
                String kind = state.anomaly.type == MoodAnomalyDetector.Type.SPIKE ? "spike" : "dip";
                anomalyLabel.setText("Anomaly: " + kind + " (z="
                        + String.format(java.util.Locale.ROOT, "%.2f", state.anomaly.score) + ")");
            }

            if (state.changePoint == null) {
                shiftLabel.setText("Shift: none nearby");
            } else {
                String dir = state.changePoint.type == MoodChangePointDetector.ShiftType.UPWARD ? "upward" : "downward";
                shiftLabel.setText("Shift: " + dir + " Δ"
                        + String.format(java.util.Locale.ROOT, "%.1f", state.changePoint.delta));
            }

            if (state.topEmotions.isEmpty()) {
                topEmotionLabel.setText("Top emotions: no detail");
            } else {
                StringBuilder sb = new StringBuilder("Top emotions: ");
                for (int i = 0; i < state.topEmotions.size(); i++) {
                    if (i > 0) sb.append(" · ");
                    EmotionScore score = state.topEmotions.get(i);
                    sb.append(MoodEmotionCatalog.emotionName(score.index))
                            .append(" ")
                            .append(score.value);
                }
                topEmotionLabel.setText(sb.toString());
            }

            applyCoMovement(coMovement);
            heatmap.setResult(coMovement);
        }

        private void applyCoMovement(EmotionCoMovementAnalyzer.Result coMovement) {
            if (coMovement == null || !coMovement.hasData()) {
                coMoveLabel.setText("Co-movement: insufficient detail");
                antiMoveLabel.setText("Counter-movement: insufficient detail");
                return;
            }
            coMoveLabel.setText("Co-movement: " + coMovementAnalyzer.describePair(coMovement.strongestPositive));
            antiMoveLabel.setText("Counter-movement: " + coMovementAnalyzer.describePair(coMovement.strongestNegative));
        }

        private String describeRegime(MoodRegimeSegmenter.RegimeSegment regime) {
            if (regime == null) {
                return "unknown";
            }
            return switch (regime.regime) {
                case RECOVERY -> "Recovery";
                case DIP -> "Dip";
                case PLATEAU -> "Plateau";
            };
        }

        private String describeLatestRegime(List<MoodRegimeSegmenter.RegimeSegment> regimes) {
            if (regimes == null || regimes.isEmpty()) {
                return "unknown";
            }
            MoodRegimeSegmenter.RegimeSegment last = regimes.get(regimes.size() - 1);
            return describeRegime(last);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int arc = 16;

            if (Theme.isPlainWhite()) {
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
                g2.setColor(new Color(214, 220, 230));
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            } else {
                Color top = new Color(255, 255, 255, 228);
                Color bottom = new Color(242, 247, 252, 222);
                g2.setPaint(new LinearGradientPaint(0, 0, 0, h, new float[]{0f, 1f}, new Color[]{top, bottom}));
                g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
                g2.setColor(new Color(192, 202, 216, 170));
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class CoMovementHeatmap extends JComponent {
        private EmotionCoMovementAnalyzer.Result result = EmotionCoMovementAnalyzer.Result.empty();

        private CoMovementHeatmap() {
            setOpaque(false);
            setPreferredSize(new Dimension(252, 152));
            setMinimumSize(new Dimension(220, 136));
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        private void setResult(EmotionCoMovementAnalyzer.Result result) {
            this.result = result == null ? EmotionCoMovementAnalyzer.Result.empty() : result;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            int n = EMOTION_COUNT;
            int leftPad = 34;
            int topPad = 16;
            int w = Math.max(10, getWidth() - leftPad - 8);
            int h = Math.max(10, getHeight() - topPad - 18);
            int cell = Math.max(8, Math.min(18, Math.min(w / n, h / n)));
            int gridW = cell * n;
            int gridH = cell * n;
            int x0 = leftPad;
            int y0 = topPad;

            g2.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 9.5f));
            for (int i = 0; i < n; i++) {
                String label = MoodEmotionCatalog.emotionName(i).substring(0, 1);
                g2.setColor(new Color(96, 108, 124));
                g2.drawString(label, 12, y0 + i * cell + cell - 4);
                int tx = x0 + i * cell + cell / 2 - g2.getFontMetrics().stringWidth(label) / 2;
                g2.drawString(label, tx, y0 - 4);
            }

            for (int row = 0; row < n; row++) {
                for (int col = 0; col < n; col++) {
                    double corr = correlationAt(row, col);
                    int samples = samplesAt(row, col);
                    Color fill = colorForCorrelation(corr, samples);
                    int x = x0 + col * cell;
                    int y = y0 + row * cell;
                    g2.setColor(fill);
                    g2.fillRect(x, y, cell, cell);
                    g2.setColor(new Color(255, 255, 255, 95));
                    g2.drawRect(x, y, cell, cell);
                }
            }

            g2.setColor(new Color(170, 182, 198, 190));
            g2.drawRect(x0 - 1, y0 - 1, gridW + 1, gridH + 1);
            g2.setColor(new Color(110, 122, 138));
            g2.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 10f));
            g2.drawString("Emotion Co-movement", x0, y0 + gridH + 14);
            g2.dispose();
        }

        private double correlationAt(int row, int col) {
            if (result == null || result.correlations == null) return 0d;
            if (row < 0 || col < 0 || row >= result.correlations.length || col >= result.correlations[row].length) {
                return 0d;
            }
            return result.correlations[row][col];
        }

        private int samplesAt(int row, int col) {
            if (result == null || result.sampleCounts == null) return 0;
            if (row < 0 || col < 0 || row >= result.sampleCounts.length || col >= result.sampleCounts[row].length) {
                return 0;
            }
            return result.sampleCounts[row][col];
        }

        private Color colorForCorrelation(double correlation, int samples) {
            double strength = Math.max(0d, Math.min(1d, Math.abs(correlation)));
            double reliability = Math.max(0d, Math.min(1d, samples / 10d));
            int alpha = 50 + (int) Math.round(170d * strength * reliability);

            if (!Double.isFinite(correlation)) {
                return new Color(210, 216, 226, 70);
            }
            if (correlation >= 0d) {
                return new Color(95, 176, 132, alpha);
            }
            return new Color(212, 112, 90, alpha);
        }
    }

    private static final class EmotionScore {
        private final int index;
        private final int value;
        private final int intensity;

        private EmotionScore(int index, int value, int intensity) {
            this.index = index;
            this.value = value;
            this.intensity = intensity;
        }
    }

    private String buildEmotionTooltipForDay(LocalDate day) {
        EmotionStackAggregator.EmotionStack stack = emotionStacksByDay.get(day);
        if (stack == null || !stack.hasData) {
            return "<html><b>" + day + "</b><br>No detailed emotion data.</html>";
        }

        List<EmotionScore> ranked = new ArrayList<>(EMOTION_COUNT);
        for (int i = 0; i < EMOTION_COUNT; i++) {
            double v = i < stack.values.length ? stack.values[i] : -1d;
            if (v < 0) continue;
            int value = (int) Math.round(v);
            int intensity = (int) Math.round(i < stack.intensities.length ? Math.max(0d, stack.intensities[i]) : 0d);
            ranked.add(new EmotionScore(i, value, intensity));
        }
        ranked.sort(Comparator
                .comparingInt((EmotionScore s) -> s.intensity).reversed()
                .thenComparingInt((EmotionScore s) -> s.value).reversed());

        StringBuilder sb = new StringBuilder("<html><b>").append(day).append("</b>");
        int shown = 0;
        for (EmotionScore score : ranked) {
            if (shown >= 3) break;
            String semantic = semanticLabeler.labelEmotion(score.index, score.value);
            sb.append("<br>")
                    .append(MoodEmotionCatalog.emotionName(score.index))
                    .append(": ")
                    .append(score.value)
                    .append(" (")
                    .append(semantic)
                    .append(")");
            shown++;
        }
        if (shown == 0) {
            sb.append("<br>No detailed emotion data.");
        }
        sb.append("</html>");
        return sb.toString();
    }

    private void openDayEntriesFromEmotionChart(int dayIndex, MouseEvent e) {
        if (dayIndex < 0 || dayIndex >= model.getDays().size()) return;
        LocalDate day = model.getDays().get(dayIndex);
        openDayEntries(day, emotionTrendCanvas, e.getX(), e.getY(), e.isMetaDown());
    }

    private void openDayEntries(LocalDate day, JComponent anchor, int x, int y, boolean nearest) {
        if (day == null) return;
        java.util.List<File> files = model.getEntriesByDate().get(day);
        if (files == null || files.isEmpty()) return;
        if (nearest) {
            openNearestForDay(day);
            return;
        }

        if (files.size() == 1) {
            NotebookInfo nb = findNotebookFor(files.get(0));
            if (nb != null) app.openExistingEntryEditor(nb, files.get(0));
            return;
        }

        JPopupMenu menu = new JPopupMenu();
        for (File f : files) {
            JMenuItem it = new JMenuItem(safeTitle(f));
            it.addActionListener(ev -> {
                NotebookInfo nb = findNotebookFor(f);
                if (nb != null) app.openExistingEntryEditor(nb, f);
            });
            menu.add(it);
        }
        menu.show(anchor, x, y);
    }

    private void handleChartClick(MouseEvent e) {
        if (model.getDays().isEmpty()) return;
        int n = model.getDays().size();
        int idx = renderer.indexForX(chartCanvas.getWidth(), e.getX(), n);
        if (idx < 0 || idx >= n) return;
        Double raw = model.getValues().get(idx);
        if (raw == null) return;
        LocalDate d = model.getDays().get(idx);
        openDayEntries(d, chartCanvas, e.getX(), e.getY(), e.isMetaDown());
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
