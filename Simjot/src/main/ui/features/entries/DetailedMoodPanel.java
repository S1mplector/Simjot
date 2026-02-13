/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.entries;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonModel;
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
    private final JPanel[] sliderRows = new JPanel[EMOTION_NAMES.length];

    private final JPanel shell;
    private final JPanel sliderStack;
    private final JLabel summaryLabel;
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
    private static final int CHANGE_DEBOUNCE_MS = 130;

    public static class DetailedMoodSnapshot {
        public final int joy, calm, gratitude, energy, sadness, anger, anxiety, stress;

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
        summaryLabel = new JLabel();
        summaryLabel.setFont(AeroTheme.defaultFont().deriveFont(12f));
        summaryLabel.setForeground(new Color(90, 90, 90));
        header.add(title, BorderLayout.WEST);
        header.add(summaryLabel, BorderLayout.EAST);
        shell.add(header, BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        JPanel chipsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        chipsRow.setOpaque(false);
        chipsRow.setBorder(new EmptyBorder(2, 0, 0, 0));
        for (int i = 0; i < EMOTION_NAMES.length; i++) {
            EmotionChipButton chip = new EmotionChipButton(EMOTION_NAMES[i], EMOTION_COLORS[i], POSITIVE_EMOTIONS[i]);
            final int idx = i;
            chip.addActionListener(e -> onChipToggled(idx));
            chips[i] = chip;
            chipsRow.add(chip);
        }

        sliderStack = new JPanel();
        sliderStack.setOpaque(false);
        sliderStack.setLayout(new BoxLayout(sliderStack, BoxLayout.Y_AXIS));
        sliderStack.setBorder(new EmptyBorder(1, 0, 0, 0));
        for (int i = 0; i < EMOTION_NAMES.length; i++) {
            JPanel row = createSliderRow(EMOTION_NAMES[i], sliders[i]);
            row.setVisible(false);
            sliderRows[i] = row;
            sliderStack.add(row);
            if (i < EMOTION_NAMES.length - 1) {
                sliderStack.add(Box.createVerticalStrut(4));
            }
        }

        center.add(chipsRow);
        center.add(sliderStack);
        shell.add(center, BorderLayout.CENTER);
        add(shell, BorderLayout.CENTER);

        heightAnimationTimer = new Timer(15, e -> stepHeightAnimation());
        heightAnimationTimer.setRepeats(true);
        changeDebounceTimer = new Timer(CHANGE_DEBOUNCE_MS, e -> emitChangeNow());
        changeDebounceTimer.setRepeats(false);
        installLiveListeners();
        refreshSummaryLabel();

        // Start collapsed
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
            int targetHeight = calcInnerPreferredHeight();
            animateHeight(from, targetHeight);
        } else {
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
            if (selected) {
                sliders[i].setValue(clamp(value));
            } else {
                sliders[i].setValue(50);
            }
        }
        suppressCallbacks = false;

        hasSnapshot = hasAnyChipSelected();
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
        }
        suppressCallbacks = false;
        hasSnapshot = false;
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
    }

    @Override
    public void removeNotify() {
        if (heightAnimationTimer != null) heightAnimationTimer.stop();
        if (changeDebounceTimer != null) changeDebounceTimer.stop();
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

    private int valueForCapture(int idx) {
        if (idx < 0 || idx >= chips.length) return -1;
        return chips[idx].isSelected() ? sliders[idx].getValue() : -1;
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

    private JPanel createSliderRow(String label, MoodSlider slider) {
        FrostedGlassPanel row = new FrostedGlassPanel(new BorderLayout(8, 0), 12) {
            @Override
            protected float getOpacityScale() {
                return 0.62f;
            }
        };
        row.setOpaque(false);
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

    private int computeComposite() {
        Double pos = avgSelected(true);
        Double neg = avgSelected(false);
        double value;
        if (pos != null && neg != null) {
            value = 50 + (pos - neg) / 2.0;
        } else if (pos != null) {
            value = 50 + (pos - 50);
        } else if (neg != null) {
            value = 50 - (neg - 50);
        } else {
            value = 50;
        }
        return clamp((int) Math.round(value));
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

    private void installLiveListeners() {
        for (int i = 0; i < sliders.length; i++) {
            final int idx = i;
            ChangeListener listener = e -> {
                if (suppressCallbacks || !chips[idx].isSelected()) return;
                hasSnapshot = true;
                refreshSummaryLabel();
                changeDebounceTimer.restart();
            };
            sliders[i].addChangeListener(listener);
        }
    }

    private void onChipToggled(int idx) {
        if (suppressCallbacks) return;
        if (idx < 0 || idx >= chips.length) return;
        hasSnapshot = hasAnyChipSelected();
        syncSliderRowVisibility(expanded);
        refreshSummaryLabel();
        changeDebounceTimer.restart();
    }

    private void syncSliderRowVisibility(boolean animateIfExpanded) {
        for (int i = 0; i < sliderRows.length; i++) {
            sliderRows[i].setVisible(chips[i].isSelected());
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

    private boolean hasAnyChipSelected() {
        for (EmotionChipButton chip : chips) {
            if (chip != null && chip.isSelected()) return true;
        }
        return false;
    }

    private void emitChangeNow() {
        if (suppressCallbacks || onChange == null) return;
        onChange.accept(computeComposite(), captureSnapshot());
    }

    private void refreshSummaryLabel() {
        if (!hasAnyChipSelected()) {
            summaryLabel.setText("Not set");
            return;
        }

        List<EmotionIntensity> top = strongestEmotions(captureSnapshot(), 2);
        StringBuilder sb = new StringBuilder("Composite ").append(computeComposite());
        for (EmotionIntensity it : top) {
            sb.append(" · ").append(it.name).append(' ').append(it.intensity).append('%');
        }
        summaryLabel.setText(sb.toString());
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

    private static final class EmotionChipButton extends JToggleButton {
        private final Color accent;
        private final boolean positive;

        EmotionChipButton(String text, Color accent, boolean positive) {
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
            Font f = getFont();
            g2.setFont(f);
            var fm = g2.getFontMetrics();
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
}
