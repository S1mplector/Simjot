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
import java.awt.RenderingHints;
import java.util.ArrayList;
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
import javax.swing.JPopupMenu;
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

    private static final int CHANGE_DEBOUNCE_MS = 130;
    private static final int STAGGER_SETTLE_MS = 70;
    private static final int STAGGER_DELAY_MS = 36;
    private static final int STAGGER_DURATION_MS = 170;

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
    private final JPanel composerPanel;
    private final JLabel summaryLabel;
    private final JToggleButton composerToggle;
    private final RoleSelectorButton primaryRoleButton;
    private final RoleSelectorButton secondaryRoleButton;
    private final RoleSelectorButton backgroundRoleButton;
    private final ComposerRingPreview composerRings;

    private final BiConsumer<Integer, DetailedMoodSnapshot> onChange;
    private boolean expanded = false;
    private boolean hasSnapshot = false;
    private int animMs = 160;
    private boolean suppressCallbacks = false;

    private final Timer heightAnimationTimer;
    private int animationFrom = 0;
    private int animationTo = 0;
    private long animationStartedAt = 0L;

    private final Timer changeDebounceTimer;

    private final Timer staggerTimer;
    private boolean staggerExpanding = false;
    private long staggerStartAt = 0L;

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
        };
        shell.setOpaque(false);
        shell.setBorder(new EmptyBorder(10, 12, 12, 12));

        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setOpaque(false);

        JLabel title = new JLabel("Detailed Emotions");
        title.setFont(AeroTheme.defaultFont().deriveFont(Font.BOLD, 13f));
        title.setForeground(AeroTheme.TEXT_PRIMARY);

        JPanel headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        headerRight.setOpaque(false);

        summaryLabel = new JLabel();
        summaryLabel.setFont(AeroTheme.defaultFont().deriveFont(12f));
        summaryLabel.setForeground(new Color(90, 90, 90));

        composerToggle = new ComposerModeToggle();
        composerToggle.setSelected(false);
        composerToggle.addActionListener(e -> setComposerModeEnabled(composerToggle.isSelected()));

        headerRight.add(summaryLabel);
        headerRight.add(composerToggle);

        header.add(title, BorderLayout.WEST);
        header.add(headerRight, BorderLayout.EAST);
        shell.add(header, BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        JPanel chipsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        chipsRow.setOpaque(false);
            chipsRow.setBorder(new EmptyBorder(2, 0, 1, 0));
        for (int i = 0; i < EMOTION_NAMES.length; i++) {
            EmotionChipButton chip = new EmotionChipButton(EMOTION_NAMES[i], EMOTION_COLORS[i], POSITIVE_EMOTIONS[i]);
            final int idx = i;
            chip.addActionListener(e -> onChipToggled(idx));
            chips[i] = chip;
            chipsRow.add(chip);
        }

        composerPanel = new JPanel();
        composerPanel.setOpaque(false);
        composerPanel.setLayout(new BoxLayout(composerPanel, BoxLayout.Y_AXIS));
        composerPanel.setBorder(new EmptyBorder(6, 0, 0, 0));

        JPanel roleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        roleRow.setOpaque(false);
        primaryRoleButton = new RoleSelectorButton();
        secondaryRoleButton = new RoleSelectorButton();
        backgroundRoleButton = new RoleSelectorButton();
        primaryRoleButton.addActionListener(e -> showRolePicker(ComposerRole.PRIMARY, primaryRoleButton));
        secondaryRoleButton.addActionListener(e -> showRolePicker(ComposerRole.SECONDARY, secondaryRoleButton));
        backgroundRoleButton.addActionListener(e -> showRolePicker(ComposerRole.BACKGROUND, backgroundRoleButton));
        roleRow.add(primaryRoleButton);
        roleRow.add(secondaryRoleButton);
        roleRow.add(backgroundRoleButton);

        composerRings = new ComposerRingPreview();
        composerRings.setPreferredSize(new Dimension(250, 66));

        composerPanel.add(roleRow);
        composerPanel.add(Box.createVerticalStrut(5));
        composerPanel.add(composerRings);
        composerPanel.setVisible(false);

        sliderStack = new JPanel();
        sliderStack.setOpaque(false);
        sliderStack.setLayout(new BoxLayout(sliderStack, BoxLayout.Y_AXIS));
        sliderStack.setBorder(new EmptyBorder(0, 0, 0, 0));
        for (int i = 0; i < EMOTION_NAMES.length; i++) {
            EmotionSliderRow row = createSliderRow(EMOTION_NAMES[i], sliders[i]);
            row.setVisible(false);
            sliderRows[i] = row;
            sliderStack.add(row);
            if (i < EMOTION_NAMES.length - 1) {
                sliderStack.add(Box.createVerticalStrut(4));
            }
        }

        center.add(chipsRow);
        center.add(composerPanel);
        center.add(sliderStack);

        shell.add(center, BorderLayout.CENTER);
        add(shell, BorderLayout.CENTER);

        heightAnimationTimer = new Timer(15, e -> stepHeightAnimation());
        heightAnimationTimer.setRepeats(true);

        changeDebounceTimer = new Timer(CHANGE_DEBOUNCE_MS, e -> emitChangeNow());
        changeDebounceTimer.setRepeats(false);

        staggerTimer = new Timer(16, e -> stepStaggerAnimation());
        staggerTimer.setRepeats(true);

        installLiveListeners();
        refreshComposerRoles();
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
        normalizeComposerRoles();
        refreshComposerRoles();
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
        refreshComposerRoles();
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
        composerToggle.setEnabled(enabled);
        primaryRoleButton.setEnabled(enabled);
        secondaryRoleButton.setEnabled(enabled);
        backgroundRoleButton.setEnabled(enabled);
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

    private void setComposerModeEnabled(boolean enabled) {
        composerPanel.setVisible(enabled);
        composerRings.repaint();
        if (!expanded) {
            revalidate();
            repaint();
            return;
        }

        int from = getHeight() > 0 ? getHeight() : Math.max(0, getPreferredSize().height);
        int target = calcInnerPreferredHeight();
        animateHeight(from, target);
    }

    private void showRolePicker(ComposerRole role, JButton anchor) {
        JPopupMenu menu = new JPopupMenu();

        javax.swing.JMenuItem none = new javax.swing.JMenuItem("None");
        none.addActionListener(e -> assignRole(role, -1));
        menu.add(none);

        List<Integer> selected = selectedEmotionIndices();
        if (!selected.isEmpty()) {
            menu.addSeparator();
        }
        for (int idx : selected) {
            String text = EMOTION_NAMES[idx] + " · " + semanticIntensityLabel(idx, sliders[idx].getValue());
            javax.swing.JMenuItem it = new javax.swing.JMenuItem(text);
            final int chosen = idx;
            it.addActionListener(e -> assignRole(role, chosen));
            menu.add(it);
        }

        menu.show(anchor, 0, anchor.getHeight());
    }

    private void assignRole(ComposerRole role, int emotionIndex) {
        if (emotionIndex >= 0 && emotionIndex < chips.length && !chips[emotionIndex].isSelected()) {
            chips[emotionIndex].setSelected(true);
        }

        if (emotionIndex >= 0) {
            if (primaryEmotion == emotionIndex) primaryEmotion = -1;
            if (secondaryEmotion == emotionIndex) secondaryEmotion = -1;
            if (backgroundEmotion == emotionIndex) backgroundEmotion = -1;
        }

        switch (role) {
            case PRIMARY -> primaryEmotion = emotionIndex;
            case SECONDARY -> secondaryEmotion = emotionIndex;
            case BACKGROUND -> backgroundEmotion = emotionIndex;
        }

        normalizeComposerRoles();
        refreshComposerRoles();
        syncSliderRowVisibility(expanded);
        if (expanded) {
            prepareRowsForStagger(true);
            startStaggerAnimation(true);
        }

        hasSnapshot = hasAnyChipSelected();
        refreshSummaryLabel();
        changeDebounceTimer.restart();
    }

    private void normalizeComposerRoles() {
        List<Integer> selected = selectedEmotionIndices();
        List<Integer> ranked = rankedByIntensity(selected);
        if (!selected.contains(primaryEmotion)) primaryEmotion = -1;
        if (!selected.contains(secondaryEmotion)) secondaryEmotion = -1;
        if (!selected.contains(backgroundEmotion)) backgroundEmotion = -1;

        if (primaryEmotion == -1 && !ranked.isEmpty()) {
            primaryEmotion = ranked.get(0);
        }

        if (secondaryEmotion == primaryEmotion) secondaryEmotion = -1;
        if (secondaryEmotion == -1) {
            for (int idx : ranked) {
                if (idx != primaryEmotion) {
                    secondaryEmotion = idx;
                    break;
                }
            }
        }

        if (backgroundEmotion == primaryEmotion || backgroundEmotion == secondaryEmotion) {
            backgroundEmotion = -1;
        }
        if (backgroundEmotion == -1) {
            for (int idx : ranked) {
                if (idx != primaryEmotion && idx != secondaryEmotion) {
                    backgroundEmotion = idx;
                    break;
                }
            }
        }
    }

    private List<Integer> rankedByIntensity(List<Integer> selected) {
        if (selected == null || selected.isEmpty()) return List.of();
        List<Integer> ranked = new ArrayList<>(selected);
        ranked.sort((a, b) -> Integer.compare(
                Math.abs(sliders[b].getValue() - 50),
                Math.abs(sliders[a].getValue() - 50)
        ));
        return ranked;
    }

    private void refreshComposerRoles() {
        primaryRoleButton.setRoleText("Primary", roleLabel(primaryEmotion));
        secondaryRoleButton.setRoleText("Secondary", roleLabel(secondaryEmotion));
        backgroundRoleButton.setRoleText("Background", roleLabel(backgroundEmotion));
        composerRings.repaint();
    }

    private String roleLabel(int emotionIndex) {
        if (emotionIndex < 0 || emotionIndex >= sliders.length) return "Not set";
        return EMOTION_NAMES[emotionIndex] + " · "
                + semanticIntensityLabel(emotionIndex, sliders[emotionIndex].getValue());
    }

    private void onChipToggled(int idx) {
        if (suppressCallbacks) return;
        if (idx < 0 || idx >= chips.length) return;

        hasSnapshot = hasAnyChipSelected();
        normalizeComposerRoles();
        refreshComposerRoles();
        syncSliderRowVisibility(expanded);

        if (expanded) {
            prepareRowsForStagger(true);
            startStaggerAnimation(true);
        }

        refreshSummaryLabel();
        changeDebounceTimer.restart();
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
            int prefW = Math.max(0, getPreferredSize().width);
            setPreferredSize(new Dimension(prefW, targetHeight));
            setVisible(targetHeight > 0);
            revalidate();
            repaint();
        }
    }

    private void prepareRowsForStagger(boolean expanding) {
        List<Integer> selected = selectedEmotionIndices();
        for (int idx : selected) {
            EmotionSliderRow row = sliderRows[idx];
            row.setVisible(true);
            row.setReveal(expanding ? 0f : 1f);
        }
    }

    private void startStaggerAnimation(boolean expanding) {
        if (staggerTimer.isRunning()) {
            staggerTimer.stop();
        }

        staggerExpanding = expanding;
        staggerStartAt = System.currentTimeMillis();
        staggerTimer.start();
    }

    private void stepStaggerAnimation() {
        List<Integer> selected = selectedEmotionIndices();
        if (selected.isEmpty()) {
            staggerTimer.stop();
            return;
        }

        if (!staggerExpanding) {
            selected = new ArrayList<>(selected);
            java.util.Collections.reverse(selected);
        }

        long elapsed = System.currentTimeMillis() - staggerStartAt;
        boolean allDone = true;

        for (int order = 0; order < selected.size(); order++) {
            int idx = selected.get(order);
            EmotionSliderRow row = sliderRows[idx];

            long startOffset = (staggerExpanding ? STAGGER_SETTLE_MS : 0L) + (long) order * STAGGER_DELAY_MS;
            float t = clamp01((elapsed - startOffset) / (float) STAGGER_DURATION_MS);
            float eased = 1f - (float) Math.pow(1f - t, 3f);
            float reveal = staggerExpanding ? eased : (1f - eased);

            if (staggerExpanding) {
                row.setVisible(true);
            }
            row.setReveal(reveal);

            if (!staggerExpanding && reveal <= 0.01f) {
                row.setVisible(false);
            }

            if (t < 1f) {
                allDone = false;
            }
        }

        if (allDone) {
            staggerTimer.stop();
            for (int idx : selectedEmotionIndices()) {
                sliderRows[idx].setReveal(1f);
                if (expanded) {
                    sliderRows[idx].setVisible(true);
                }
            }
        }
    }

    private int calcInnerPreferredHeight() {
        shell.doLayout();
        shell.validate();
        return shell.getPreferredSize().height;
    }

    private void animateHeight(int from, int to) {
        int safeFrom = Math.max(0, from);
        int safeTo = Math.max(0, to);
        if (safeFrom == safeTo) {
            int prefW = Math.max(0, getPreferredSize().width);
            setPreferredSize(new Dimension(prefW, safeTo));
            setVisible(safeTo > 0);
            revalidate();
            repaint();
            return;
        }

        setVisible(true);
        animationFrom = safeFrom;
        animationTo = safeTo;
        animationStartedAt = System.currentTimeMillis();
        if (heightAnimationTimer.isRunning()) {
            heightAnimationTimer.stop();
        }
        heightAnimationTimer.start();
    }

    private void stepHeightAnimation() {
        float p = Math.min(1f, (System.currentTimeMillis() - animationStartedAt) / (float) animMs);
        float e = 1f - (float) Math.pow(1f - p, 3f);
        int h = animationFrom + Math.round((animationTo - animationFrom) * e);
        int prefW = Math.max(0, getPreferredSize().width);
        setPreferredSize(new Dimension(prefW, h));
        revalidate();
        repaint();
        if (p >= 1f) {
            heightAnimationTimer.stop();
            if (animationTo == 0) {
                setVisible(false);
            }
        }
    }

    private void installLiveListeners() {
        for (int i = 0; i < sliders.length; i++) {
            final int idx = i;
            ChangeListener listener = e -> {
                if (suppressCallbacks || !chips[idx].isSelected()) return;
                hasSnapshot = true;
                refreshComposerRoles();
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

        Double composer = composerWeightedScore();
        if (composer != null && composerToggle.isSelected()) {
            base = (base * 0.65) + (composer * 0.35);
        }

        return clamp((int) Math.round(base));
    }

    private Double composerWeightedScore() {
        double signed = 0d;
        double weight = 0d;

        int[] roleIndices = {primaryEmotion, secondaryEmotion, backgroundEmotion};
        double[] roleWeights = {PRIMARY_WEIGHT, SECONDARY_WEIGHT, BACKGROUND_WEIGHT};

        for (int i = 0; i < roleIndices.length; i++) {
            int idx = roleIndices[i];
            if (idx < 0 || idx >= sliders.length) continue;
            if (!chips[idx].isSelected()) continue;

            int value = sliders[idx].getValue();
            double polarity = POSITIVE_EMOTIONS[idx] ? 1d : -1d;
            signed += polarity * (value - 50d) * roleWeights[i];
            weight += roleWeights[i];
        }

        if (weight <= 0d) return null;
        return 50d + (signed / weight);
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
        if (!hasAnyChipSelected()) {
            summaryLabel.setText("Not set");
            return;
        }

        List<EmotionIntensity> top = strongestEmotions(captureSnapshot(), 2);
        StringBuilder sb = new StringBuilder("Composite ").append(computeComposite());
        for (EmotionIntensity it : top) {
            sb.append(" · ")
              .append(it.name)
              .append(" · ")
              .append(semanticIntensityLabel(it.index, it.value));
        }
        summaryLabel.setText(sb.toString());
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

    private static EmotionSliderRow createSliderRow(String label, MoodSlider slider) {
        EmotionSliderRow row = new EmotionSliderRow();
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(1, 1, 1, 1),
                BorderFactory.createEmptyBorder(3, 10, 6, 10)
        ));

        JLabel l = new JLabel(label);
        l.setFont(AeroTheme.defaultFont());
        l.setForeground(AeroTheme.TEXT_PRIMARY);
        row.add(l, BorderLayout.WEST);
        row.add(slider, BorderLayout.CENTER);
        return row;
    }

    private static MoodSlider createEmotionSlider() {
        MoodSlider s = new MoodSlider();
        s.setHoverFadeEnabled(false);
        s.setOpaque(false);
        s.setFocusable(false);
        s.setPreferredSize(new Dimension(205, 34));
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

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static final class EmotionSliderRow extends FrostedGlassPanel {
        private float reveal = 1f;

        private EmotionSliderRow() {
            super(new BorderLayout(8, 0), 12);
            setOpaque(false);
        }

        @Override
        protected float getOpacityScale() {
            return 0.62f;
        }

        private void setReveal(float value) {
            reveal = clamp01(value);
            repaint();
        }

        @Override
        public void paint(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            float alpha = 0.08f + 0.92f * reveal;
            int dy = Math.round((1f - reveal) * 8f);
            g2.translate(0, dy);
            g2.setComposite(AlphaComposite.SrcOver.derive(alpha));
            super.paint(g2);
            g2.dispose();
        }
    }

    private static final class EmotionChipButton extends JToggleButton {
        private final Color accent;
        private final boolean positive;

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
            setMargin(new java.awt.Insets(5, 10, 5, 10));
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            d.height = Math.max(28, d.height + 2);
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
            ButtonModel m = getModel();
            boolean selected = m.isSelected();
            boolean hover = m.isRollover();
            boolean pressed = m.isPressed();

            Color top;
            Color bottom;
            Color border;
            Color text;
            if (selected) {
                top = blend(accent, Color.WHITE, 0.72f);
                bottom = blend(accent, Color.WHITE, 0.45f);
                border = blend(accent, new Color(90, 90, 100), 0.36f);
                text = new Color(40, 40, 52);
            } else if (pressed || hover) {
                top = new Color(250, 252, 255, 230);
                bottom = new Color(231, 238, 248, 230);
                border = new Color(184, 197, 214, 190);
                text = new Color(52, 58, 72);
            } else {
                top = new Color(255, 255, 255, 205);
                bottom = new Color(238, 242, 249, 205);
                border = new Color(191, 202, 216, 170);
                text = new Color(64, 72, 86);
            }

            g2.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.setColor(border);
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

            if (selected || hover) {
                Color glow = positive
                        ? new Color(120, 185, 128, selected ? 95 : 52)
                        : new Color(198, 122, 112, selected ? 95 : 52);
                g2.setColor(glow);
                g2.drawRoundRect(1, 1, w - 3, h - 3, arc - 2, arc - 2);
            }

            g2.setColor(text);
            g2.setFont(getFont());
            java.awt.FontMetrics fm = g2.getFontMetrics();
            int tx = (w - fm.stringWidth(getText())) / 2;
            int ty = (h + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(getText(), tx, ty);
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
