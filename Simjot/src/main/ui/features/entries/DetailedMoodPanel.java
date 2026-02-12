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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.function.BiConsumer;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeListener;

import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.slider.MoodSlider;
import main.ui.theme.aero.AeroTheme;

/**
 * Collapsible detailed mood logging panel with animated expand/collapse.
 * Uses frosted rows and computes a composite score (0-100).
 */
public class DetailedMoodPanel extends JPanel {
    private final MoodSlider joy = createEmotionSlider();
    private final MoodSlider calm = createEmotionSlider();
    private final MoodSlider gratitude = createEmotionSlider();
    private final MoodSlider energy = createEmotionSlider();
    private final MoodSlider sadness = createEmotionSlider();
    private final MoodSlider anger = createEmotionSlider();
    private final MoodSlider anxiety = createEmotionSlider();
    private final MoodSlider stress = createEmotionSlider();

    private final JPanel shell;
    private final JLabel summaryLabel;
    private final BiConsumer<Integer, DetailedMoodSnapshot> onChange;
    private boolean expanded = false;
    private boolean hasSnapshot = false;
    private int animMs = 160;
    private boolean suppressCallbacks = false;
    private Timer heightAnimationTimer;
    private int animationFrom = 0;
    private int animationTo = 0;
    private long animationStartedAt = 0L;
    private final Timer changeDebounceTimer;
    private static final int CHANGE_DEBOUNCE_MS = 130;

    public static class DetailedMoodSnapshot {
        public final int joy, calm, gratitude, energy, sadness, anger, anxiety, stress;
        public DetailedMoodSnapshot(int joy, int calm, int gratitude, int energy, int sadness, int anger, int anxiety, int stress) {
            this.joy = joy; this.calm = calm; this.gratitude = gratitude; this.energy = energy;
            this.sadness = sadness; this.anger = anger; this.anxiety = anxiety; this.stress = stress;
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
        title.setFont(AeroTheme.defaultFont().deriveFont(java.awt.Font.BOLD, 13f));
        title.setForeground(AeroTheme.TEXT_PRIMARY);
        summaryLabel = new JLabel();
        summaryLabel.setFont(AeroTheme.defaultFont().deriveFont(12f));
        summaryLabel.setForeground(new Color(90, 90, 90));
        header.add(title, BorderLayout.WEST);
        header.add(summaryLabel, BorderLayout.EAST);
        shell.add(header, BorderLayout.NORTH);

        JPanel inner = new JPanel(new GridBagLayout());
        inner.setOpaque(false);
        inner.setBorder(new EmptyBorder(2, 0, 0, 0));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 6, 6, 6);
        gc.gridx = 0;
        gc.gridy = 0;
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;
        addRow(inner, gc, "Joy", joy);
        gc.gridy++; addRow(inner, gc, "Calm", calm);
        gc.gridy++; addRow(inner, gc, "Gratitude", gratitude);
        gc.gridy++; addRow(inner, gc, "Energy", energy);
        gc.gridy++; addRow(inner, gc, "Sadness", sadness);
        gc.gridy++; addRow(inner, gc, "Anger", anger);
        gc.gridy++; addRow(inner, gc, "Anxiety", anxiety);
        gc.gridy++; addRow(inner, gc, "Stress", stress);
        shell.add(inner, BorderLayout.CENTER);
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

    public boolean isExpanded() { return expanded; }

    public void setExpanded(boolean expand) {
        if (this.expanded == expand) return;
        this.expanded = expand;
        int from = getHeight() > 0 ? getHeight() : Math.max(0, getPreferredSize().height);
        if (expand) {
            // compute target height from inner preferred height
            int targetHeight = calcInnerPreferredHeight();
            animateHeight(from, targetHeight);
        } else {
            animateHeight(from, 0);
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
        // ease-out cubic
        float e = 1 - (float) Math.pow(1 - p, 3);
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

    private void addRow(JPanel panel, GridBagConstraints gc, String label, MoodSlider slider) {
        FrostedGlassPanel row = new FrostedGlassPanel(new BorderLayout(8, 0), 12) {
            @Override
            protected float getOpacityScale() {
                return 0.62f;
            }
        };
        row.setOpaque(false);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(1, 1, 1, 1),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));

        JLabel l = new JLabel(label);
        l.setFont(AeroTheme.defaultFont());
        l.setForeground(AeroTheme.TEXT_PRIMARY);
        row.add(l, BorderLayout.WEST);
        row.add(slider, BorderLayout.CENTER);
        panel.add(row, gc);
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
        double pos = avg(joy, calm, gratitude, energy);
        double neg = avg(sadness, anger, anxiety, stress);
        int val = (int) Math.round(50 + (pos - neg) / 2.0);
        return Math.max(0, Math.min(100, val));
    }

    public DetailedMoodSnapshot captureSnapshot() {
        return new DetailedMoodSnapshot(
                joy.getValue(), calm.getValue(), gratitude.getValue(), energy.getValue(),
                sadness.getValue(), anger.getValue(), anxiety.getValue(), stress.getValue()
        );
    }

    public int computeCompositeScore() {
        return computeComposite();
    }

    private static double avg(MoodSlider... sliders) {
        double sum = 0;
        for (MoodSlider s : sliders) sum += s.getValue();
        return sum / sliders.length;
    }

    public void applySnapshot(DetailedMoodSnapshot s) {
        if (s == null) return;
        changeDebounceTimer.stop();
        suppressCallbacks = true;
        joy.setValue(s.joy);
        calm.setValue(s.calm);
        gratitude.setValue(s.gratitude);
        energy.setValue(s.energy);
        sadness.setValue(s.sadness);
        anger.setValue(s.anger);
        anxiety.setValue(s.anxiety);
        stress.setValue(s.stress);
        suppressCallbacks = false;
        hasSnapshot = true;
        refreshSummaryLabel();
        repaint();
    }

    public void clearSnapshot() {
        changeDebounceTimer.stop();
        suppressCallbacks = true;
        joy.setValue(50);
        calm.setValue(50);
        gratitude.setValue(50);
        energy.setValue(50);
        sadness.setValue(50);
        anger.setValue(50);
        anxiety.setValue(50);
        stress.setValue(50);
        suppressCallbacks = false;
        hasSnapshot = false;
        refreshSummaryLabel();
    }

    public boolean hasSnapshot() {
        return hasSnapshot;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        joy.setEnabled(enabled);
        calm.setEnabled(enabled);
        gratitude.setEnabled(enabled);
        energy.setEnabled(enabled);
        sadness.setEnabled(enabled);
        anger.setEnabled(enabled);
        anxiety.setEnabled(enabled);
        stress.setEnabled(enabled);
    }

    private void installLiveListeners() {
        ChangeListener listener = e -> {
            if (suppressCallbacks) return;
            hasSnapshot = true;
            refreshSummaryLabel();
            changeDebounceTimer.restart();
        };
        joy.addChangeListener(listener);
        calm.addChangeListener(listener);
        gratitude.addChangeListener(listener);
        energy.addChangeListener(listener);
        sadness.addChangeListener(listener);
        anger.addChangeListener(listener);
        anxiety.addChangeListener(listener);
        stress.addChangeListener(listener);
    }

    private void emitChangeNow() {
        if (suppressCallbacks || onChange == null) return;
        onChange.accept(computeComposite(), captureSnapshot());
    }

    private void refreshSummaryLabel() {
        if (!hasSnapshot) {
            summaryLabel.setText("Not set");
            return;
        }
        summaryLabel.setText("Composite " + computeComposite());
    }

    @Override
    public void removeNotify() {
        if (heightAnimationTimer != null) heightAnimationTimer.stop();
        if (changeDebounceTimer != null) changeDebounceTimer.stop();
        super.removeNotify();
    }
}
