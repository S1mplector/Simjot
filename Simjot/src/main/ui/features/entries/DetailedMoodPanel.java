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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeListener;

import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.slider.MoodSlider;
import main.ui.theme.aero.AeroTheme;

/**
 * Collapsible detailed mood logging panel with animated expand/collapse.
 * Uses emotion chips to enable only the rows the user cares about.
 */
public class DetailedMoodPanel extends JPanel {
    private static final boolean SHOW_HEADER_SUMMARY = false;

    private static final String[] EMOTION_NAMES = {
            "Joy", "Calm", "Gratitude", "Energy",
            "Sadness", "Anger", "Anxiety", "Stress"
    };

    private static final boolean[] POSITIVE_EMOTIONS = {
            true, true, true, true,
            false, false, false, false
    };

    private static final Color[] EMOTION_COLORS = {
            new Color(236, 181, 72),  // Joy
            new Color(115, 172, 220), // Calm
            new Color(110, 191, 122), // Gratitude
            new Color(240, 144, 76),  // Energy
            new Color(139, 150, 174), // Sadness
            new Color(204, 104, 90),  // Anger
            new Color(191, 126, 203), // Anxiety
            new Color(165, 109, 85)   // Stress
    };

    private static final double PRIMARY_WEIGHT = 0.58;
    private static final double SECONDARY_WEIGHT = 0.30;
    private static final double BACKGROUND_WEIGHT = 0.12;

    private static final int IDX_JOY = 0;
    private static final int IDX_CALM = 1;
    private static final int IDX_GRATITUDE = 2;
    private static final int IDX_ENERGY = 3;
    private static final int IDX_SADNESS = 4;
    private static final int IDX_ANGER = 5;
    private static final int IDX_ANXIETY = 6;
    private static final int IDX_STRESS = 7;

    private static final int CHANGE_DEBOUNCE_MS = 130;
    private static final int STAGGER_SETTLE_MS = 28;
    private static final int STAGGER_DELAY_MS = 20;
    private static final int STAGGER_DURATION_MS = 120;
    private static final int HEIGHT_ANIM_TICK_MS = 16;

    private final MoodSlider joy = createEmotionSlider();
    private final MoodSlider calm = createEmotionSlider();
    private final MoodSlider gratitude = createEmotionSlider();
    private final MoodSlider energy = createEmotionSlider();
    private final MoodSlider sadness = createEmotionSlider();
    private final MoodSlider anger = createEmotionSlider();
    private final MoodSlider anxiety = createEmotionSlider();
    private final MoodSlider stress = createEmotionSlider();

    private final MoodSlider[] sliders = {
            joy, calm, gratitude, energy,
            sadness, anger, anxiety, stress
    };

    private final EmotionChipButton[] chips = new EmotionChipButton[EMOTION_NAMES.length];
    private final EmotionSliderRow[] sliderRows = new EmotionSliderRow[EMOTION_NAMES.length];

    private final JPanel shell;
    private final JPanel sliderStack;
    private final JLabel summaryLabel;
    private final JLabel utilityStatusLabel;
    private final MoodBalanceMeter balanceMeter;
    private final UtilityActionButton clearActionButton;

    private final BiConsumer<Integer, DetailedMoodSnapshot> onChange;
    private boolean expanded = false;
    private boolean hasSnapshot = false;
    private int animMs = 160;
    private boolean suppressCallbacks = false;

    private final Timer heightAnimationTimer;
    private int animationFrom = 0;
    private int animationTo = 0;
    private long animationStartedNanos = 0L;
    private int animationFrameCounter = 0;

    private final Timer changeDebounceTimer;

    private final Timer staggerTimer;
    private boolean staggerExpanding = false;
    private long staggerStartNanos = 0L;
    private final List<Integer> activeStaggerIndices = new ArrayList<>();

    private int primaryEmotion = -1;
    private int secondaryEmotion = -1;
    private int backgroundEmotion = -1;

    public static class DetailedMoodSnapshot {
        public final int joy;
        public final int calm;
        public final int gratitude;
        public final int energy;
        public final int sadness;
        public final int anger;
        public final int anxiety;
        public final int stress;

        public DetailedMoodSnapshot(int joy, int calm, int gratitude, int energy,
                                    int sadness, int anger, int anxiety, int stress) {
            this.joy = joy;
            this.calm = calm;
            this.gratitude = gratitude;
            this.energy = energy;
            this.sadness = sadness;
            this.anger = anger;
            this.anxiety = anxiety;
            this.stress = stress;
        }
    }

    public static final class EmotionIntensity {
        public final int index;
        public final String name;
        public final int value;
        public final int intensity;

        private EmotionIntensity(int index, String name, int value, int intensity) {
            this.index = index;
            this.name = name;
            this.value = value;
            this.intensity = intensity;
        }
    }

    private enum ComposerRole {
        PRIMARY,
        SECONDARY,
        BACKGROUND
    }

    public DetailedMoodPanel(BiConsumer<Integer, DetailedMoodSnapshot> onChange) {
        this.onChange = onChange;
        setOpaque(false);
        setLayout(new BorderLayout());

        shell = new FrostedGlassPanel(new BorderLayout(10, 8), 16) {
            @Override
            protected float getOpacityScale() {
                return 0.84f;
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                if (w > 0 && h > 0) {
                    int arc = 16;
                    g2.setPaint(new GradientPaint(
                            0, 0, new Color(255, 255, 255, 64),
                            0, Math.max(1, h / 3), new Color(255, 255, 255, 0)));
                    g2.fillRoundRect(1, 1, Math.max(1, w - 2), Math.max(1, h - 2), arc, arc);
                    g2.setColor(new Color(108, 132, 172, 44));
                    g2.drawRoundRect(1, 1, Math.max(1, w - 3), Math.max(1, h - 3), arc, arc);
                }
                g2.dispose();
            }
        };
        shell.setOpaque(false);
        shell.setBorder(new EmptyBorder(10, 12, 12, 12));

        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setOpaque(false);

        JLabel title = new JLabel("Detailed Emotions");
        title.setFont(AeroTheme.defaultFont().deriveFont(Font.BOLD, 13f));
        title.setForeground(AeroTheme.TEXT_PRIMARY);

        summaryLabel = new JLabel();
        summaryLabel.setFont(AeroTheme.defaultFont().deriveFont(12f));
        summaryLabel.setForeground(new Color(90, 90, 90));

        utilityStatusLabel = new JLabel();
        utilityStatusLabel.setFont(AeroTheme.defaultFont().deriveFont(Font.BOLD, 11.5f));
        utilityStatusLabel.setForeground(new Color(88, 96, 112));

        balanceMeter = new MoodBalanceMeter();
        balanceMeter.setPreferredSize(new Dimension(120, 12));

        header.add(title, BorderLayout.WEST);
        if (SHOW_HEADER_SUMMARY) {
            JPanel headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            headerRight.setOpaque(false);
            headerRight.add(summaryLabel);
            headerRight.add(utilityStatusLabel);
            headerRight.add(balanceMeter);
            header.add(headerRight, BorderLayout.EAST);
        }
        shell.add(header, BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        JPanel chipsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        chipsRow.setOpaque(false);
        chipsRow.setBorder(new EmptyBorder(2, 0, 2, 0));
        for (int i = 0; i < EMOTION_NAMES.length; i++) {
            EmotionChipButton chip = new EmotionChipButton(EMOTION_NAMES[i], EMOTION_COLORS[i], POSITIVE_EMOTIONS[i]);
            final int idx = i;
            chip.addActionListener(e -> onChipToggled(idx));
            chips[i] = chip;
            chipsRow.add(chip);
        }

        JPanel utilityRow = new JPanel(new BorderLayout(8, 0));
        utilityRow.setOpaque(false);
        utilityRow.setBorder(new EmptyBorder(6, 0, 2, 0));

        JPanel utilityActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 1));
        utilityActions.setOpaque(false);

        clearActionButton = new UtilityActionButton("Reset");
        clearActionButton.addActionListener(e -> applyUtilityClear());

        utilityActions.add(clearActionButton);

        utilityRow.add(utilityActions, BorderLayout.WEST);

        sliderStack = new JPanel();
        sliderStack.setOpaque(false);
        sliderStack.setLayout(new BoxLayout(sliderStack, BoxLayout.Y_AXIS));
        sliderStack.setBorder(new EmptyBorder(0, 0, 0, 0));
        for (int i = 0; i < EMOTION_NAMES.length; i++) {
            EmotionSliderRow row = createSliderRow(EMOTION_NAMES[i], sliders[i], EMOTION_COLORS[i], POSITIVE_EMOTIONS[i]);
            row.setVisible(false);
            sliderRows[i] = row;
            sliderStack.add(row);
            if (i < EMOTION_NAMES.length - 1) {
                sliderStack.add(Box.createVerticalStrut(4));
            }
        }

        center.add(chipsRow);
        center.add(utilityRow);
        center.add(sliderStack);

        shell.add(center, BorderLayout.CENTER);
        add(shell, BorderLayout.CENTER);

        heightAnimationTimer = new Timer(HEIGHT_ANIM_TICK_MS, e -> stepHeightAnimation());
        heightAnimationTimer.setRepeats(true);
        heightAnimationTimer.setCoalesce(true);

        changeDebounceTimer = new Timer(CHANGE_DEBOUNCE_MS, e -> emitChangeNow());
        changeDebounceTimer.setRepeats(false);
        changeDebounceTimer.setCoalesce(true);

        staggerTimer = new Timer(16, e -> stepStaggerAnimation());
        staggerTimer.setRepeats(true);
        staggerTimer.setCoalesce(true);

        installLiveListeners();
        refreshAllRowMeta();
        refreshSummaryLabel();

        setPreferredSize(new Dimension(0, 0));
        setVisible(false);
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expand) {
        if (this.expanded == expand) return;
        this.expanded = expand;

        int from = getHeight() > 0 ? getHeight() : Math.max(0, getPreferredSize().height);

        if (expand) {
            syncSliderRowVisibility(false);
            prepareRowsForStagger(true);
            int targetHeight = calcInnerPreferredHeight();
            animateHeight(from, targetHeight);
            startStaggerAnimation(true);
        } else {
            prepareRowsForStagger(false);
            startStaggerAnimation(false);
            animateHeight(from, 0);
        }
    }

    public DetailedMoodSnapshot captureSnapshot() {
        return new DetailedMoodSnapshot(
                valueForCapture(0), valueForCapture(1), valueForCapture(2), valueForCapture(3),
                valueForCapture(4), valueForCapture(5), valueForCapture(6), valueForCapture(7)
        );
    }

    public int computeCompositeScore() {
        return computeComposite();
    }

    public void applySnapshot(DetailedMoodSnapshot snapshot) {
        if (snapshot == null) {
            clearSnapshot();
            return;
        }

        int[] values = snapshotToArray(snapshot);
        changeDebounceTimer.stop();
        suppressCallbacks = true;
        for (int i = 0; i < EMOTION_NAMES.length; i++) {
            int value = values[i];
            boolean selected = value >= 0;
            chips[i].setSelected(selected);
            sliders[i].setValue(selected ? clamp(value) : 50);
            sliderRows[i].setReveal(1f);
        }
        suppressCallbacks = false;

        hasSnapshot = hasAnyChipSelected();
        refreshAllRowMeta();
        syncSliderRowVisibility(expanded);
        refreshSummaryLabel();
        repaint();
    }

    public void clearSnapshot() {
        changeDebounceTimer.stop();
        suppressCallbacks = true;
        for (int i = 0; i < EMOTION_NAMES.length; i++) {
            chips[i].setSelected(false);
            sliders[i].setValue(50);
            sliderRows[i].setReveal(1f);
        }
        suppressCallbacks = false;

        hasSnapshot = false;
        primaryEmotion = -1;
        secondaryEmotion = -1;
        backgroundEmotion = -1;
        refreshAllRowMeta();
        syncSliderRowVisibility(expanded);
        refreshSummaryLabel();
    }

    public boolean hasSnapshot() {
        return hasSnapshot;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        for (MoodSlider slider : sliders) {
            slider.setEnabled(enabled);
        }
        for (EmotionChipButton chip : chips) {
            if (chip != null) chip.setEnabled(enabled);
        }
        clearActionButton.setEnabled(enabled);
    }

    @Override
    public void removeNotify() {
        if (heightAnimationTimer != null) heightAnimationTimer.stop();
        if (changeDebounceTimer != null) changeDebounceTimer.stop();
        if (staggerTimer != null) staggerTimer.stop();
        super.removeNotify();
    }

    public static List<EmotionIntensity> strongestEmotions(DetailedMoodSnapshot snapshot, int limit) {
        if (snapshot == null || limit <= 0) return List.of();
        int[] values = snapshotToArray(snapshot);
        List<EmotionIntensity> ranked = new ArrayList<>(EMOTION_NAMES.length);
        for (int i = 0; i < values.length; i++) {
            int value = values[i];
            if (value < 0) continue;
            int clamped = clamp(value);
            ranked.add(new EmotionIntensity(i, EMOTION_NAMES[i], clamped, intensityFromValue(clamped)));
        }
        ranked.sort(Comparator
                .comparingInt((EmotionIntensity e) -> e.intensity).reversed()
                .thenComparingInt((EmotionIntensity e) -> e.value).reversed());
        if (ranked.size() <= limit) return ranked;
        return new ArrayList<>(ranked.subList(0, limit));
    }

    public static String emotionName(int index) {
        if (index < 0 || index >= EMOTION_NAMES.length) return "Emotion";
        return EMOTION_NAMES[index];
    }

    public static Color emotionColor(int index) {
        if (index < 0 || index >= EMOTION_COLORS.length) return new Color(180, 185, 195);
        return EMOTION_COLORS[index];
    }

    public static String semanticIntensityLabel(int emotionIndex, int value) {
        int v = clamp(value);
        int band;
        if (v < 20) band = 0;
        else if (v < 40) band = 1;
        else if (v < 60) band = 2;
        else if (v < 80) band = 3;
        else band = 4;

        return switch (emotionIndex) {
            case 0 -> switch (band) {
                case 0 -> "Flat";
                case 1 -> "Muted";
                case 2 -> "Steady";
                case 3 -> "Bright";
                default -> "Radiant";
            };
            case 1 -> switch (band) {
                case 0 -> "Restless";
                case 1 -> "Uneasy";
                case 2 -> "Balanced";
                case 3 -> "Grounded";
                default -> "Serene";
            };
            case 2 -> switch (band) {
                case 0 -> "Numb";
                case 1 -> "Distant";
                case 2 -> "Aware";
                case 3 -> "Thankful";
                default -> "Deeply thankful";
            };
            case 3 -> switch (band) {
                case 0 -> "Drained";
                case 1 -> "Low";
                case 2 -> "Even";
                case 3 -> "Active";
                default -> "Energized";
            };
            case 4 -> switch (band) {
                case 0 -> "Light";
                case 1 -> "Tender";
                case 2 -> "Heavy";
                case 3 -> "Downcast";
                default -> "Deeply low";
            };
            case 5 -> switch (band) {
                case 0 -> "Composed";
                case 1 -> "Irritated";
                case 2 -> "Frustrated";
                case 3 -> "Intense";
                default -> "Fuming";
            };
            case 6 -> switch (band) {
                case 0 -> "At ease";
                case 1 -> "Alert";
                case 2 -> "Uneasy";
                case 3 -> "Elevated";
                default -> "Overwhelmed";
            };
            case 7 -> switch (band) {
                case 0 -> "Clear";
                case 1 -> "Loaded";
                case 2 -> "Pressured";
                case 3 -> "Tense";
                default -> "Overclocked";
            };
            default -> "Steady";
        };
    }

    private void applyUtilityClear() {
        clearSnapshot();
        changeDebounceTimer.restart();
    }

    private void runBatchUpdate(Runnable updates, boolean restagger) {
        changeDebounceTimer.stop();
        suppressCallbacks = true;
        updates.run();
        suppressCallbacks = false;

        hasSnapshot = hasAnyChipSelected();
        refreshAllRowMeta();
        syncSliderRowVisibility(expanded);
        if (restagger && expanded) {
            prepareRowsForStagger(true);
            startStaggerAnimation(true);
        }
        refreshSummaryLabel();
        changeDebounceTimer.restart();
    }

    private void ensureSelected(int idx) {
        if (idx < 0 || idx >= chips.length) return;
        chips[idx].setSelected(true);
    }

    private void shiftValue(int idx, int delta, boolean autoSelect) {
        if (idx < 0 || idx >= sliders.length) return;
        if (autoSelect) {
            ensureSelected(idx);
        }
        if (!chips[idx].isSelected()) return;
        sliders[idx].setValue(clamp(sliders[idx].getValue() + delta));
    }

    private void nudgeTowards(int idx, int target, float factor) {
        if (idx < 0 || idx >= sliders.length) return;
        int current = sliders[idx].getValue();
        int next = current + Math.round((target - current) * factor);
        sliders[idx].setValue(clamp(next));
    }

    private void refreshAllRowMeta() {
        for (int i = 0; i < sliderRows.length; i++) {
            refreshRowMeta(i);
        }
    }

    private void onChipToggled(int idx) {
        boolean selected = chips[idx].isSelected();
        if (!selected) {
            sliders[idx].setValue(50);
        }
        hasSnapshot = hasAnyChipSelected();
        refreshAllRowMeta();
        syncSliderRowVisibility(expanded);
        refreshSummaryLabel();
        changeDebounceTimer.restart();
    }

    private void refreshRowMeta(int idx) {
        if (idx < 0 || idx >= sliderRows.length) return;
        int value = sliders[idx].getValue();
        String semantic = semanticIntensityLabel(idx, value);
        sliderRows[idx].updateMeta(semantic, value, chips[idx].isSelected());
    }

    private void syncSliderRowVisibility(boolean animateIfExpanded) {
        for (int i = 0; i < sliderRows.length; i++) {
            boolean visible = expanded && chips[i].isSelected();
            sliderRows[i].setVisible(visible);
            if (!visible) {
                sliderRows[i].setReveal(1f);
            }
        }

        sliderStack.revalidate();
        sliderStack.repaint();

        if (!expanded) {
            revalidate();
            repaint();
            return;
        }

        int targetHeight = calcInnerPreferredHeight();
        if (animateIfExpanded && isShowing()) {
            int from = getHeight() > 0 ? getHeight() : Math.max(0, getPreferredSize().height);
            animateHeight(from, targetHeight);
        } else {
            applyAnimatedHeight(targetHeight, true);
            setVisible(targetHeight > 0);
        }
    }

    private void prepareRowsForStagger(boolean expanding) {
        List<Integer> selected = selectedEmotionIndices();
        for (int idx : selected) {
            EmotionSliderRow row = sliderRows[idx];
            row.setVisible(true);
            row.setReveal(expanding ? 0f : 1f, false);
        }
        sliderStack.repaint();
    }

    private void startStaggerAnimation(boolean expanding) {
        if (staggerTimer.isRunning()) {
            staggerTimer.stop();
        }

        activeStaggerIndices.clear();
        activeStaggerIndices.addAll(selectedEmotionIndices());
        if (activeStaggerIndices.isEmpty()) {
            return;
        }
        if (!expanding) {
            Collections.reverse(activeStaggerIndices);
        }

        staggerExpanding = expanding;
        staggerStartNanos = System.nanoTime();
        staggerTimer.start();
    }

    private void stepStaggerAnimation() {
        if (activeStaggerIndices.isEmpty()) {
            staggerTimer.stop();
            return;
        }
        float elapsed = (System.nanoTime() - staggerStartNanos) / 1_000_000f;
        boolean allDone = true;

        for (int order = 0; order < activeStaggerIndices.size(); order++) {
            int idx = activeStaggerIndices.get(order);
            EmotionSliderRow row = sliderRows[idx];

            long startOffset = (staggerExpanding ? STAGGER_SETTLE_MS : 0L) + (long) order * STAGGER_DELAY_MS;
            float t = clamp01((elapsed - startOffset) / (float) STAGGER_DURATION_MS);
            float eased = staggerExpanding ? easeOutCubic(t) : easeInCubic(t);
            float reveal = staggerExpanding ? eased : (1f - eased);

            if (staggerExpanding && !row.isVisible()) {
                row.setVisible(true);
            }
            row.setReveal(reveal, false);

            if (!staggerExpanding && reveal <= 0.01f) {
                row.setVisible(false);
            }

            if (t < 1f) {
                allDone = false;
            }
        }
        sliderStack.repaint();

        if (allDone) {
            staggerTimer.stop();
            for (int idx : activeStaggerIndices) {
                EmotionSliderRow row = sliderRows[idx];
                row.setReveal(staggerExpanding ? 1f : 0f, false);
                row.setVisible(staggerExpanding && expanded);
            }
            sliderStack.repaint();
            activeStaggerIndices.clear();
        }
    }

    private int calcInnerPreferredHeight() {
        int innerHeight = Math.max(0, shell.getPreferredSize().height);
        Insets insets = getInsets();
        return innerHeight + Math.max(0, insets.top) + Math.max(0, insets.bottom);
    }

    private void animateHeight(int from, int to) {
        int safeFrom = Math.max(0, from);
        int safeTo = Math.max(0, to);
        if (safeFrom == safeTo) {
            applyAnimatedHeight(safeTo, true);
            setVisible(safeTo > 0);
            return;
        }

        setVisible(true);
        animationFrom = safeFrom;
        animationTo = safeTo;
        animationStartedNanos = System.nanoTime();
        animationFrameCounter = 0;
        if (heightAnimationTimer.isRunning()) {
            heightAnimationTimer.stop();
        }
        heightAnimationTimer.start();
    }

    private void stepHeightAnimation() {
        float elapsedMs = (System.nanoTime() - animationStartedNanos) / 1_000_000f;
        float p = Math.min(1f, elapsedMs / (float) Math.max(1, animMs));
        float e = 1f - (float) Math.pow(1f - p, 3f);
        int h = animationFrom + Math.round((animationTo - animationFrom) * e);
        animationFrameCounter++;
        boolean fullLayoutPass = (animationFrameCounter % 3 == 0) || p >= 1f;
        applyAnimatedHeight(h, fullLayoutPass);
        if (p >= 1f) {
            heightAnimationTimer.stop();
            if (animationTo == 0) {
                setVisible(false);
            }
        }
    }

    private void applyAnimatedHeight(int height, boolean reflow) {
        int safeHeight = Math.max(0, height);
        Insets insets = getInsets();
        int prefW = Math.max(0, getPreferredSize().width);
        if (prefW <= 0) {
            prefW = Math.max(0, shell.getPreferredSize().width + Math.max(0, insets.left) + Math.max(0, insets.right));
        }
        Dimension next = new Dimension(prefW, safeHeight);
        if (!next.equals(getPreferredSize())) {
            setPreferredSize(next);
        }
        if (reflow) {
            revalidate();
        }
        repaint();
    }

    private void installLiveListeners() {
        for (int i = 0; i < sliders.length; i++) {
            final int idx = i;
            ChangeListener listener = e -> {
                if (suppressCallbacks || !chips[idx].isSelected()) return;
                hasSnapshot = true;
                refreshRowMeta(idx);
                refreshSummaryLabel();
                changeDebounceTimer.restart();
            };
            sliders[i].addChangeListener(listener);
        }
    }

    private void emitChangeNow() {
        if (suppressCallbacks || onChange == null) return;
        onChange.accept(computeComposite(), captureSnapshot());
    }

    private int computeComposite() {
        Double pos = avgSelected(true);
        Double neg = avgSelected(false);
        double base;
        if (pos != null && neg != null) {
            base = 50 + (pos - neg) / 2.0;
        } else if (pos != null) {
            base = 50 + (pos - 50);
        } else if (neg != null) {
            base = 50 - (neg - 50);
        } else {
            base = 50;
        }

        return clamp((int) Math.round(base));
    }

    private Double avgSelected(boolean positiveBucket) {
        double sum = 0d;
        int count = 0;
        for (int i = 0; i < EMOTION_NAMES.length; i++) {
            if (POSITIVE_EMOTIONS[i] != positiveBucket) continue;
            if (!chips[i].isSelected()) continue;
            sum += sliders[i].getValue();
            count++;
        }
        return count > 0 ? (sum / count) : null;
    }

    private void refreshSummaryLabel() {
        if (!SHOW_HEADER_SUMMARY) return;

        int selectedCount = selectedEmotionIndices().size();
        if (selectedCount == 0) {
            summaryLabel.setText("Not set");
            utilityStatusLabel.setText("0/8 active");
            balanceMeter.setValue(50);
            return;
        }

        int composite = computeComposite();
        List<EmotionIntensity> top = strongestEmotions(captureSnapshot(), 2);
        StringBuilder sb = new StringBuilder("Composite ").append(composite);
        for (EmotionIntensity it : top) {
            sb.append(" · ")
              .append(it.name)
              .append(" · ")
              .append(semanticIntensityLabel(it.index, it.value));
        }
        summaryLabel.setText(sb.toString());
        utilityStatusLabel.setText(selectedCount + "/8 active");
        balanceMeter.setValue(composite);
    }

    private int valueForCapture(int idx) {
        if (idx < 0 || idx >= chips.length) return -1;
        return chips[idx].isSelected() ? sliders[idx].getValue() : -1;
    }

    private boolean hasAnyChipSelected() {
        for (EmotionChipButton chip : chips) {
            if (chip != null && chip.isSelected()) return true;
        }
        return false;
    }

    private List<Integer> selectedEmotionIndices() {
        List<Integer> selected = new ArrayList<>();
        for (int i = 0; i < chips.length; i++) {
            if (chips[i].isSelected()) selected.add(i);
        }
        return selected;
    }

    private static EmotionSliderRow createSliderRow(String label, MoodSlider slider, Color accent, boolean positive) {
        return new EmotionSliderRow(label, slider, accent, positive);
    }

    private static MoodSlider createEmotionSlider() {
        MoodSlider s = new MoodSlider();
        s.setHoverFadeEnabled(false);
        s.setOpaque(false);
        s.setFocusable(false);
        s.setPreferredSize(new Dimension(220, 34));
        return s;
    }

    private static int[] snapshotToArray(DetailedMoodSnapshot snapshot) {
        if (snapshot == null) return new int[] {-1, -1, -1, -1, -1, -1, -1, -1};
        return new int[] {
                snapshot.joy, snapshot.calm, snapshot.gratitude, snapshot.energy,
                snapshot.sadness, snapshot.anger, snapshot.anxiety, snapshot.stress
        };
    }

    private static int intensityFromValue(int value) {
        return clamp(Math.abs(clamp(value) - 50) * 2);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static int clamp255(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static float easeOutCubic(float t) {
        float x = 1f - clamp01(t);
        return 1f - (x * x * x);
    }

    private static float easeInCubic(float t) {
        float x = clamp01(t);
        return x * x * x;
    }

    private static final class EmotionSliderRow extends FrostedGlassPanel {
        private final Color accent;
        private final boolean positive;
        private final JLabel semanticLabel;
        private final JLabel valueLabel;
        private float reveal = 1f;
        private float activeMix = 0f;

        private EmotionSliderRow(String label, MoodSlider slider, Color accent, boolean positive) {
            super(new BorderLayout(8, 0), 12);
            this.accent = accent;
            this.positive = positive;
            setOpaque(false);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createEmptyBorder(1, 1, 1, 1),
                    BorderFactory.createEmptyBorder(4, 10, 6, 10)
            ));

            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            left.setOpaque(false);
            left.add(new DotIndicator(accent));

            JLabel labelComp = new JLabel(label);
            labelComp.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 12.5f));
            labelComp.setForeground(AeroTheme.TEXT_PRIMARY);
            left.add(labelComp);
            add(left, BorderLayout.WEST);

            add(slider, BorderLayout.CENTER);

            JPanel meta = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            meta.setOpaque(false);
            semanticLabel = new JLabel("Steady");
            semanticLabel.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 11.5f));
            semanticLabel.setForeground(new Color(90, 98, 114));
            valueLabel = new JLabel("50%");
            valueLabel.setFont(AeroTheme.defaultFont().deriveFont(Font.BOLD, 11.5f));
            valueLabel.setForeground(new Color(70, 78, 94));
            meta.add(semanticLabel);
            meta.add(valueLabel);
            add(meta, BorderLayout.EAST);
        }

        @Override
        protected float getOpacityScale() {
            return 0.58f + 0.12f * activeMix;
        }

        private void updateMeta(String semantic, int value, boolean active) {
            semanticLabel.setText(semantic);
            valueLabel.setText(clamp(value) + "%");

            float target = active ? 1f : 0f;
            activeMix += (target - activeMix) * 0.34f;
            if (Math.abs(target - activeMix) < 0.01f) {
                activeMix = target;
            }

            Color semanticColor = active
                    ? blend(accent, new Color(62, 70, 84), 0.35f)
                    : new Color(96, 104, 120);
            semanticLabel.setForeground(semanticColor);
            valueLabel.setForeground(active
                    ? blend(accent, new Color(52, 60, 72), 0.28f)
                    : new Color(82, 90, 105));
            repaint();
        }

        private void setReveal(float value) {
            setReveal(value, true);
        }

        private void setReveal(float value, boolean repaintNow) {
            float next = clamp01(value);
            if (Math.abs(next - reveal) < 0.003f) return;
            reveal = next;
            if (repaintNow) repaint();
        }

        @Override
        public void paint(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            float alpha = 0.2f + 0.8f * reveal;
            int dy = Math.round((1f - reveal) * 4f);
            float scale = 0.985f + 0.015f * reveal;
            int w = Math.max(1, getWidth());
            int h = Math.max(1, getHeight());
            g2.translate(0, dy);
            g2.setComposite(AlphaComposite.SrcOver.derive(alpha));
            g2.translate((w * (1f - scale)) / 2f, (h * (1f - scale)) / 2f);
            g2.scale(scale, scale);
            super.paint(g2);

            float baseGlow = positive ? 0.16f : 0.2f;
            int glowAlpha = Math.round((baseGlow + 0.56f * activeMix) * 255f * reveal);
            Color glow = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), clamp255(glowAlpha));
            g2.setColor(glow);
            g2.fillRoundRect(2, 4, 4, Math.max(4, h - 8), 4, 4);
            g2.dispose();
        }

        private static Color blend(Color a, Color b, float t) {
            float k = Math.max(0f, Math.min(1f, t));
            float inv = 1f - k;
            int r = Math.round(a.getRed() * k + b.getRed() * inv);
            int g = Math.round(a.getGreen() * k + b.getGreen() * inv);
            int bl = Math.round(a.getBlue() * k + b.getBlue() * inv);
            int al = Math.round(a.getAlpha() * k + b.getAlpha() * inv);
            return new Color(r, g, bl, al);
        }

        private static final class DotIndicator extends JComponent {
            private final Color color;

            private DotIndicator(Color color) {
                this.color = color;
                setPreferredSize(new Dimension(10, 10));
                setMinimumSize(new Dimension(10, 10));
                setMaximumSize(new Dimension(10, 10));
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = Math.max(2, getWidth() - 1);
                int h = Math.max(2, getHeight() - 1);
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 220));
                g2.fillOval(0, 0, w, h);
                g2.setColor(new Color(255, 255, 255, 135));
                g2.fillOval(2, 2, Math.max(1, w / 3), Math.max(1, h / 3));
                g2.dispose();
            }
        }
    }

    private static final class EmotionChipButton extends JToggleButton {
        private final Color accent;
        private final boolean positive;
        private float hoverMix = 0f;
        private float selectMix = 0f;
        private float pressMix = 0f;
        private final Timer animationTimer;

        private EmotionChipButton(String text, Color accent, boolean positive) {
            super(text);
            this.accent = accent;
            this.positive = positive;
            setOpaque(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 12f));
            setMargin(new java.awt.Insets(6, 11, 7, 11));

            animationTimer = new Timer(16, e -> stepAnimation());
            animationTimer.setRepeats(true);
            animationTimer.setCoalesce(true);
            getModel().addChangeListener(e -> requestAnimation());
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            d.height = Math.max(30, d.height + 4);
            d.width += 6;
            return d;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arc = Math.max(16, h - 8);
            float hover = clamp01(hoverMix);
            float selected = clamp01(selectMix);
            float pressed = clamp01(pressMix);

            Color idleTop = new Color(255, 255, 255, 205);
            Color idleBottom = new Color(238, 242, 249, 205);
            Color hoverTop = new Color(251, 253, 255, 230);
            Color hoverBottom = new Color(236, 242, 252, 225);
            Color selectedTop = blend(accent, Color.WHITE, 0.74f);
            Color selectedBottom = blend(accent, Color.WHITE, 0.46f);

            Color top = blend(idleTop, hoverTop, hover);
            top = blend(top, selectedTop, selected);
            Color bottom = blend(idleBottom, hoverBottom, hover);
            bottom = blend(bottom, selectedBottom, selected);
            if (pressed > 0f) {
                top = blend(top, new Color(232, 238, 248, 220), pressed);
                bottom = blend(bottom, new Color(221, 230, 244, 220), pressed);
            }

            Color border = blend(new Color(191, 202, 216, 170), new Color(176, 191, 210, 190), hover);
            border = blend(border, blend(accent, new Color(94, 102, 116), 0.4f), selected);
            Color text = blend(new Color(62, 70, 84), new Color(44, 50, 61), selected);

            g2.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.setColor(border);
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

            if (selected > 0.01f || hover > 0.01f) {
                int glowAlpha = Math.round((selected * 112f) + (hover * 58f));
                Color glow = positive
                        ? new Color(120, 185, 128, clamp255(glowAlpha))
                        : new Color(198, 122, 112, clamp255(glowAlpha));
                g2.setColor(glow);
                g2.drawRoundRect(1, 1, w - 3, h - 3, arc - 2, arc - 2);
            }

            int dotSize = 7;
            int dotX = 9;
            int dotY = (h - dotSize) / 2;
            Color dot = blend(new Color(194, 204, 218), accent, Math.max(0.2f, selected));
            g2.setColor(new Color(dot.getRed(), dot.getGreen(), dot.getBlue(), 215));
            g2.fillOval(dotX, dotY, dotSize, dotSize);
            g2.setColor(new Color(255, 255, 255, 130));
            g2.fillOval(dotX + 2, dotY + 1, Math.max(1, dotSize / 3), Math.max(1, dotSize / 3));

            g2.setColor(text);
            g2.setFont(getFont());
            java.awt.FontMetrics fm = g2.getFontMetrics();
            int tx = (w - fm.stringWidth(getText())) / 2 + 4;
            int ty = (h + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(getText(), tx, ty);
            g2.dispose();
        }

        @Override
        public void removeNotify() {
            if (animationTimer != null) {
                animationTimer.stop();
            }
            super.removeNotify();
        }

        private void requestAnimation() {
            if (!animationTimer.isRunning()) {
                animationTimer.start();
            }
        }

        private void stepAnimation() {
            ButtonModel model = getModel();
            hoverMix = approach(hoverMix, model.isRollover() ? 1f : 0f, 0.24f);
            selectMix = approach(selectMix, model.isSelected() ? 1f : 0f, 0.21f);
            pressMix = approach(pressMix, model.isPressed() ? 1f : 0f, 0.30f);
            repaint();

            if (isSettled(hoverMix, model.isRollover())
                    && isSettled(selectMix, model.isSelected())
                    && isSettled(pressMix, model.isPressed())) {
                animationTimer.stop();
            }
        }

        private static float approach(float current, float target, float factor) {
            float next = current + (target - current) * factor;
            if (Math.abs(target - next) < 0.005f) {
                return target;
            }
            return next;
        }

        private static boolean isSettled(float current, boolean targetOn) {
            float target = targetOn ? 1f : 0f;
            return Math.abs(current - target) <= 0.01f;
        }

        private static Color blend(Color a, Color b, float t) {
            float k = Math.max(0f, Math.min(1f, t));
            float inv = 1f - k;
            int r = Math.round(a.getRed() * k + b.getRed() * inv);
            int g = Math.round(a.getGreen() * k + b.getGreen() * inv);
            int bl = Math.round(a.getBlue() * k + b.getBlue() * inv);
            int al = Math.round(a.getAlpha() * k + b.getAlpha() * inv);
            return new Color(r, g, bl, al);
        }
    }

    private static final class UtilityActionButton extends JButton {
        private float hoverMix = 0f;
        private float pressMix = 0f;
        private final Timer animationTimer;

        private UtilityActionButton(String text) {
            super(text);
            setOpaque(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 11.5f));
            setMargin(new java.awt.Insets(5, 10, 6, 10));

            animationTimer = new Timer(16, e -> stepAnimation());
            animationTimer.setRepeats(true);
            animationTimer.setCoalesce(true);
            getModel().addChangeListener(e -> requestAnimation());
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            d.height = Math.max(24, d.height + 2);
            d.width += 6;
            return d;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int arc = Math.max(13, h - 6);

            Color top = blend(new Color(250, 252, 255, 186), new Color(255, 255, 255, 212), hoverMix);
            Color bottom = blend(new Color(236, 241, 249, 186), new Color(228, 236, 247, 212), hoverMix);
            if (pressMix > 0.01f) {
                top = blend(top, new Color(226, 233, 245, 215), pressMix);
                bottom = blend(bottom, new Color(216, 226, 241, 215), pressMix);
            }

            g2.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.setColor(blend(new Color(184, 196, 212, 170), new Color(162, 178, 199, 190), hoverMix));
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

            g2.setColor(new Color(62, 70, 86));
            g2.setFont(getFont());
            java.awt.FontMetrics fm = g2.getFontMetrics();
            int tx = (w - fm.stringWidth(getText())) / 2;
            int ty = (h + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(getText(), tx, ty);
            g2.dispose();
        }

        @Override
        public void removeNotify() {
            if (animationTimer != null) {
                animationTimer.stop();
            }
            super.removeNotify();
        }

        private void requestAnimation() {
            if (!animationTimer.isRunning()) {
                animationTimer.start();
            }
        }

        private void stepAnimation() {
            ButtonModel model = getModel();
            hoverMix = approach(hoverMix, model.isRollover() ? 1f : 0f, 0.24f);
            pressMix = approach(pressMix, model.isPressed() ? 1f : 0f, 0.30f);
            repaint();
            if (isSettled(hoverMix, model.isRollover()) && isSettled(pressMix, model.isPressed())) {
                animationTimer.stop();
            }
        }

        private static float approach(float current, float target, float factor) {
            float next = current + (target - current) * factor;
            if (Math.abs(target - next) < 0.005f) {
                return target;
            }
            return next;
        }

        private static boolean isSettled(float current, boolean targetOn) {
            float target = targetOn ? 1f : 0f;
            return Math.abs(current - target) <= 0.01f;
        }

        private static Color blend(Color a, Color b, float t) {
            float k = Math.max(0f, Math.min(1f, t));
            float inv = 1f - k;
            int r = Math.round(a.getRed() * k + b.getRed() * inv);
            int g = Math.round(a.getGreen() * k + b.getGreen() * inv);
            int bl = Math.round(a.getBlue() * k + b.getBlue() * inv);
            int al = Math.round(a.getAlpha() * k + b.getAlpha() * inv);
            return new Color(r, g, bl, al);
        }
    }

    private static final class MoodBalanceMeter extends JComponent {
        private float displayValue = 50f;
        private float targetValue = 50f;
        private final Timer animationTimer;

        private MoodBalanceMeter() {
            setOpaque(false);
            animationTimer = new Timer(16, e -> stepAnimation());
            animationTimer.setRepeats(true);
        }

        private void setValue(int value) {
            targetValue = clamp(value);
            if (!animationTimer.isRunning()) {
                animationTimer.start();
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = Math.max(1, getWidth());
            int h = Math.max(1, getHeight());
            int arc = Math.max(10, h - 2);

            GradientPaint track = new GradientPaint(
                    0, 0, new Color(201, 129, 129, 155),
                    w / 2f, 0, new Color(206, 212, 220, 168),
                    true
            );
            g2.setPaint(track);
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);

            g2.setPaint(new GradientPaint(
                    w / 2f, 0, new Color(208, 214, 224, 180),
                    w, 0, new Color(122, 181, 231, 185),
                    true
            ));
            g2.fillRoundRect(w / 2, 0, Math.max(1, w / 2), h - 1, arc, arc);

            g2.setColor(new Color(160, 174, 192, 188));
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

            int markerX = Math.round((displayValue / 100f) * (w - 1));
            markerX = Math.max(3, Math.min(w - 4, markerX));
            g2.setColor(new Color(255, 255, 255, 210));
            g2.fillOval(markerX - 3, Math.max(1, h / 2 - 3), 6, 6);
            g2.setColor(new Color(104, 120, 142, 190));
            g2.drawOval(markerX - 3, Math.max(1, h / 2 - 3), 6, 6);
            g2.dispose();
        }

        private void stepAnimation() {
            displayValue += (targetValue - displayValue) * 0.27f;
            if (Math.abs(targetValue - displayValue) < 0.3f) {
                displayValue = targetValue;
                animationTimer.stop();
            }
            repaint();
        }

        @Override
        public void removeNotify() {
            if (animationTimer != null) {
                animationTimer.stop();
            }
            super.removeNotify();
        }
    }

    private static final class ComposerModeToggle extends JToggleButton {
        private ComposerModeToggle() {
            super("Composer");
            setOpaque(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 11.5f));
            setMargin(new java.awt.Insets(4, 10, 4, 10));
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            d.height = Math.max(24, d.height + 1);
            return d;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arc = Math.max(14, h - 6);
            boolean selected = getModel().isSelected();
            boolean hover = getModel().isRollover();

            Color top = selected ? new Color(220, 236, 255, 210) : new Color(246, 248, 252, 190);
            Color bottom = selected ? new Color(197, 222, 252, 210) : new Color(230, 236, 246, 190);
            Color border = selected ? new Color(125, 162, 210, 190) : new Color(182, 194, 210, 160);
            if (hover && !selected) {
                top = new Color(250, 252, 255, 215);
                bottom = new Color(237, 242, 250, 215);
            }

            g2.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.setColor(border);
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

            g2.setColor(new Color(56, 64, 79));
            g2.setFont(getFont());
            java.awt.FontMetrics fm = g2.getFontMetrics();
            int tx = (w - fm.stringWidth(getText())) / 2;
            int ty = (h + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(getText(), tx, ty);

            g2.dispose();
        }
    }

    private static final class RoleSelectorButton extends JButton {
        private String roleText = "";

        private RoleSelectorButton() {
            setOpaque(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 11.5f));
            setMargin(new java.awt.Insets(5, 10, 5, 10));
        }

        private void setRoleText(String role, String value) {
            this.roleText = role + ": " + value;
            setText(this.roleText);
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            d.height = Math.max(24, d.height + 1);
            return d;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arc = Math.max(14, h - 6);
            boolean hover = getModel().isRollover();

            Color top = hover ? new Color(248, 251, 255, 215) : new Color(242, 246, 252, 205);
            Color bottom = hover ? new Color(233, 240, 249, 215) : new Color(225, 233, 245, 205);

            g2.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.setColor(new Color(184, 196, 212, 185));
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

            g2.setColor(new Color(54, 62, 76));
            g2.setFont(getFont());
            java.awt.FontMetrics fm = g2.getFontMetrics();
            String text = roleText == null ? "" : roleText;
            int tx = (w - fm.stringWidth(text)) / 2;
            int ty = (h + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(text, tx, ty);
            g2.dispose();
        }
    }

    private class ComposerRingPreview extends JComponent {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int cx = w / 2;
            int cy = h / 2;

            drawRing(g2, cx, cy, 24, 10f, primaryEmotion, PRIMARY_WEIGHT, "P");
            drawRing(g2, cx, cy, 16, 8f, secondaryEmotion, SECONDARY_WEIGHT, "S");
            drawRing(g2, cx, cy, 9, 6f, backgroundEmotion, BACKGROUND_WEIGHT, "B");

            g2.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 11f));
            g2.setColor(new Color(88, 96, 112));
            String weights = "P 58%  •  S 30%  •  B 12%";
            int tw = g2.getFontMetrics().stringWidth(weights);
            g2.drawString(weights, Math.max(8, (w - tw) / 2), h - 6);

            g2.dispose();
        }

        private void drawRing(Graphics2D g2, int cx, int cy, int radius, float stroke,
                              int emotionIndex, double weight, String label) {
            Color color = emotionIndex >= 0 ? emotionColor(emotionIndex) : new Color(192, 198, 208);
            int alpha = emotionIndex >= 0 ? 210 : 110;

            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
            g2.setStroke(new java.awt.BasicStroke(stroke, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            int d = radius * 2;
            g2.drawOval(cx - radius, cy - radius, d, d);

            if (emotionIndex >= 0) {
                g2.setFont(AeroTheme.defaultFont().deriveFont(Font.BOLD, 10f));
                g2.setColor(new Color(55, 62, 76));
                String txt = label;
                java.awt.FontMetrics fm = g2.getFontMetrics();
                int tx = cx + radius + 6;
                int ty = cy - radius + fm.getAscent();
                g2.drawString(txt, tx, ty);
            }
        }
    }
}
