/*
 * SIMJOT - No Derivatives License
 *
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 *
 * See LICENSE for full terms.
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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeListener;

import main.ui.components.buttons.RoundedButton;
import main.ui.components.checkbox.ModernCheckBoxUI;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.slider.MoodSlider;
import main.ui.theme.aero.AeroTheme;

/**
 * Explicit mood logging surface opened from the editor toolbar.
 */
final class MoodLoggingDialog extends JDialog {
    private static final String[] EMOTION_NAMES = {
            "Joy", "Calm", "Gratitude", "Energy",
            "Sadness", "Anger", "Anxiety", "Stress"
    };

    private static final boolean[] POSITIVE = {
            true, true, true, true,
            false, false, false, false
    };

    private static final Color[] EMOTION_COLORS = {
            new Color(236, 181, 72),
            new Color(115, 172, 220),
            new Color(110, 191, 122),
            new Color(240, 144, 76),
            new Color(139, 150, 174),
            new Color(204, 104, 90),
            new Color(191, 126, 203),
            new Color(165, 109, 85)
    };

    private final MoodSlider overallSlider = new MoodSlider();
    private final JLabel scoreLabel = new JLabel("50", SwingConstants.RIGHT);
    private final MoodPreview preview = new MoodPreview();
    private final JCheckBox[] emotionChecks = new JCheckBox[EMOTION_NAMES.length];
    private final JPanel[] emotionRows = new JPanel[EMOTION_NAMES.length];
    private final MoodSlider[] emotionSliders = new MoodSlider[EMOTION_NAMES.length];
    private final JLabel[] emotionValueLabels = new JLabel[EMOTION_NAMES.length];
    private final JLabel[] emotionSemanticLabels = new JLabel[EMOTION_NAMES.length];

    private boolean saved;
    private int savedMood = -1;
    private DetailedMoodPanel.DetailedMoodSnapshot savedDetails;
    private boolean suppressEvents;

    MoodLoggingDialog(Window owner, int initialMood, DetailedMoodPanel.DetailedMoodSnapshot initialDetails) {
        super(owner, "Log Mood", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        int startMood = initialMood >= 0 ? clamp(initialMood) : 50;
        overallSlider.setValue(startMood);
        overallSlider.setPreferredSize(new Dimension(280, 42));
        overallSlider.setGradientVisible(false);

        JPanel root = new FrostedGlassPanel(new BorderLayout(14, 12), 18) {
            @Override
            protected float getOpacityScale() {
                return 0.92f;
            }
        };
        root.setBorder(new EmptyBorder(16, 18, 14, 18));

        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildBody(), BorderLayout.CENTER);
        root.add(buildActions(), BorderLayout.SOUTH);
        setContentPane(root);

        installListeners();
        applyInitialDetails(initialDetails);
        refreshFromCurrentState();

        pack();
        setMinimumSize(new Dimension(620, 520));
        setLocationRelativeTo(owner);
    }

    boolean isSaved() {
        return saved;
    }

    int getSavedMood() {
        return savedMood;
    }

    DetailedMoodPanel.DetailedMoodSnapshot getSavedDetails() {
        return savedDetails;
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);

        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Log Mood");
        title.setFont(AeroTheme.defaultBoldFont(20f));
        title.setForeground(AeroTheme.TEXT_PRIMARY);
        JLabel subtitle = new JLabel("Capture a single score, or add the emotions behind it.");
        subtitle.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 12.5f));
        subtitle.setForeground(new Color(84, 94, 112));
        titleBlock.add(title);
        titleBlock.add(Box.createVerticalStrut(3));
        titleBlock.add(subtitle);

        scoreLabel.setFont(AeroTheme.defaultBoldFont(30f));
        scoreLabel.setForeground(AeroTheme.TEXT_PRIMARY);
        scoreLabel.setPreferredSize(new Dimension(82, 42));

        header.add(titleBlock, BorderLayout.CENTER);
        header.add(scoreLabel, BorderLayout.EAST);
        return header;
    }

    private JPanel buildBody() {
        JPanel body = new JPanel(new BorderLayout(12, 12));
        body.setOpaque(false);

        FrostedGlassPanel scorePanel = new FrostedGlassPanel(new BorderLayout(12, 8), 14);
        scorePanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        scorePanel.add(preview, BorderLayout.WEST);
        scorePanel.add(overallSlider, BorderLayout.CENTER);
        body.add(scorePanel, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.gridy = 0;
        gc.weightx = 1.0;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.insets = new Insets(4, 0, 4, 0);

        for (int i = 0; i < EMOTION_NAMES.length; i++) {
            grid.add(createEmotionRow(i), gc);
            gc.gridy++;
        }
        body.add(grid, BorderLayout.CENTER);
        return body;
    }

    private JPanel createEmotionRow(int index) {
        FrostedGlassPanel row = new FrostedGlassPanel(new BorderLayout(8, 0), 12) {
            @Override
            protected float getOpacityScale() {
                return emotionChecks[index] != null && emotionChecks[index].isSelected() ? 0.72f : 0.48f;
            }
        };
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(1, 1, 1, 1),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        emotionRows[index] = row;

        JCheckBox check = new JCheckBox(EMOTION_NAMES[index]);
        check.setOpaque(false);
        check.setUI(new ModernCheckBoxUI());
        check.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 12.5f));
        check.setForeground(AeroTheme.TEXT_PRIMARY);
        emotionChecks[index] = check;

        MoodSlider slider = new MoodSlider();
        slider.setPreferredSize(new Dimension(230, 34));
        slider.setGradientVisible(false);
        emotionSliders[index] = slider;

        JLabel semantic = new JLabel(DetailedMoodPanel.semanticIntensityLabel(index, slider.getValue()));
        semantic.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 11.5f));
        semantic.setForeground(new Color(82, 91, 108));
        emotionSemanticLabels[index] = semantic;

        JLabel value = new JLabel(slider.getValue() + "%", SwingConstants.RIGHT);
        value.setFont(AeroTheme.defaultBoldFont(11.5f));
        value.setForeground(new Color(62, 70, 84));
        value.setPreferredSize(new Dimension(42, 22));
        emotionValueLabels[index] = value;

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(semantic);
        right.add(value);

        row.add(check, BorderLayout.WEST);
        row.add(slider, BorderLayout.CENTER);
        row.add(right, BorderLayout.EAST);
        return row;
    }

    private JPanel buildActions() {
        JPanel actions = new JPanel(new BorderLayout(8, 0));
        actions.setOpaque(false);

        RoundedButton clear = new RoundedButton("Clear Log");
        clear.setPreferredSize(new Dimension(116, 32));
        clear.addActionListener(e -> {
            suppressEvents = true;
            overallSlider.setValue(50);
            for (int i = 0; i < EMOTION_NAMES.length; i++) {
                emotionChecks[i].setSelected(false);
                emotionSliders[i].setValue(50);
            }
            suppressEvents = false;
            refreshFromCurrentState();
        });
        actions.add(clear, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        RoundedButton cancel = new RoundedButton("Cancel");
        cancel.setPreferredSize(new Dimension(104, 32));
        cancel.addActionListener(e -> dispose());
        RoundedButton save = new RoundedButton("Save Mood").withIcon("moodchart");
        save.setPreferredSize(new Dimension(128, 32));
        save.addActionListener(e -> {
            savedMood = currentMood();
            savedDetails = anyEmotionSelected() ? captureDetails() : null;
            saved = true;
            dispose();
        });
        right.add(cancel);
        right.add(save);
        actions.add(right, BorderLayout.EAST);
        return actions;
    }

    private void installListeners() {
        overallSlider.addChangeListener(e -> {
            if (suppressEvents) return;
            refreshFromCurrentState();
        });

        ChangeListener emotionSliderListener = e -> {
            if (suppressEvents) return;
            refreshFromCurrentState();
        };

        for (int i = 0; i < EMOTION_NAMES.length; i++) {
            final int idx = i;
            emotionChecks[i].addActionListener(e -> {
                if (suppressEvents) return;
                refreshFromCurrentState();
                if (emotionRows[idx] != null) emotionRows[idx].repaint();
            });
            emotionSliders[i].addChangeListener(emotionSliderListener);
            emotionSliders[i].setEnabled(false);
            emotionSemanticLabels[i].setEnabled(false);
            emotionValueLabels[i].setEnabled(false);
        }
    }

    private void applyInitialDetails(DetailedMoodPanel.DetailedMoodSnapshot snapshot) {
        if (snapshot == null) return;
        int[] values = snapshotToArray(snapshot);
        suppressEvents = true;
        for (int i = 0; i < values.length; i++) {
            boolean selected = values[i] >= 0;
            emotionChecks[i].setSelected(selected);
            emotionSliders[i].setValue(selected ? clamp(values[i]) : 50);
        }
        if (hasAnyValue(values)) {
            overallSlider.setValue(computeCompositeFromDetails());
        }
        suppressEvents = false;
    }

    private void refreshFromCurrentState() {
        boolean hasDetails = anyEmotionSelected();
        if (hasDetails) {
            int composite = computeCompositeFromDetails();
            if (overallSlider.getValue() != composite) {
                suppressEvents = true;
                overallSlider.setValue(composite);
                suppressEvents = false;
            }
        }

        for (int i = 0; i < EMOTION_NAMES.length; i++) {
            boolean active = emotionChecks[i].isSelected();
            emotionSliders[i].setEnabled(active);
            emotionSemanticLabels[i].setEnabled(active);
            emotionValueLabels[i].setEnabled(active);

            int value = emotionSliders[i].getValue();
            emotionSemanticLabels[i].setText(DetailedMoodPanel.semanticIntensityLabel(i, value));
            emotionSemanticLabels[i].setForeground(active
                    ? blend(EMOTION_COLORS[i], new Color(62, 70, 84), 0.42f)
                    : new Color(104, 112, 128));
            emotionValueLabels[i].setText(value + "%");
            emotionValueLabels[i].setForeground(active
                    ? blend(EMOTION_COLORS[i], new Color(48, 56, 70), 0.36f)
                    : new Color(104, 112, 128));
        }

        int mood = currentMood();
        scoreLabel.setText(String.valueOf(mood));
        preview.setMood(mood, selectedColors());
    }

    private int currentMood() {
        return clamp(overallSlider.getValue());
    }

    private boolean anyEmotionSelected() {
        for (JCheckBox check : emotionChecks) {
            if (check != null && check.isSelected()) return true;
        }
        return false;
    }

    private DetailedMoodPanel.DetailedMoodSnapshot captureDetails() {
        return new DetailedMoodPanel.DetailedMoodSnapshot(
                valueFor(0), valueFor(1), valueFor(2), valueFor(3),
                valueFor(4), valueFor(5), valueFor(6), valueFor(7)
        );
    }

    private int valueFor(int index) {
        if (index < 0 || index >= emotionChecks.length) return -1;
        return emotionChecks[index].isSelected() ? clamp(emotionSliders[index].getValue()) : -1;
    }

    private int computeCompositeFromDetails() {
        Double pos = averageSelected(true);
        Double neg = averageSelected(false);
        double base;
        if (pos != null && neg != null) {
            base = 50 + (pos - neg) / 2.0;
        } else if (pos != null) {
            base = 50 + (pos - 50);
        } else if (neg != null) {
            base = 50 - (neg - 50);
        } else {
            base = overallSlider.getValue();
        }
        return clamp((int) Math.round(base));
    }

    private Double averageSelected(boolean positive) {
        double sum = 0d;
        int count = 0;
        for (int i = 0; i < EMOTION_NAMES.length; i++) {
            if (POSITIVE[i] != positive || !emotionChecks[i].isSelected()) continue;
            sum += emotionSliders[i].getValue();
            count++;
        }
        return count > 0 ? sum / count : null;
    }

    private List<Color> selectedColors() {
        List<ColorIntensity> ranked = new ArrayList<>();
        for (int i = 0; i < EMOTION_NAMES.length; i++) {
            if (!emotionChecks[i].isSelected()) continue;
            int value = emotionSliders[i].getValue();
            int intensity = Math.abs(value - 50) * 2;
            ranked.add(new ColorIntensity(EMOTION_COLORS[i], intensity));
        }
        ranked.sort(Comparator.comparingInt((ColorIntensity item) -> item.intensity).reversed());
        List<Color> colors = new ArrayList<>();
        for (int i = 0; i < Math.min(3, ranked.size()); i++) {
            colors.add(ranked.get(i).color);
        }
        return colors;
    }

    private static int[] snapshotToArray(DetailedMoodPanel.DetailedMoodSnapshot snapshot) {
        if (snapshot == null) return new int[] {-1, -1, -1, -1, -1, -1, -1, -1};
        return new int[] {
                snapshot.joy, snapshot.calm, snapshot.gratitude, snapshot.energy,
                snapshot.sadness, snapshot.anger, snapshot.anxiety, snapshot.stress
        };
    }

    private static boolean hasAnyValue(int[] values) {
        if (values == null) return false;
        for (int value : values) {
            if (value >= 0) return true;
        }
        return false;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static Color blend(Color a, Color b, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        float inv = 1f - k;
        int r = Math.round(a.getRed() * inv + b.getRed() * k);
        int g = Math.round(a.getGreen() * inv + b.getGreen() * k);
        int bl = Math.round(a.getBlue() * inv + b.getBlue() * k);
        int alpha = Math.round(a.getAlpha() * inv + b.getAlpha() * k);
        return new Color(r, g, bl, alpha);
    }

    private record ColorIntensity(Color color, int intensity) {}

    private static final class MoodPreview extends JPanel {
        private int mood = 50;
        private List<Color> colors = List.of();

        private MoodPreview() {
            setOpaque(false);
            setPreferredSize(new Dimension(82, 82));
            setMinimumSize(new Dimension(82, 82));
        }

        private void setMood(int mood, List<Color> colors) {
            this.mood = clamp(mood);
            this.colors = colors == null ? List.of() : List.copyOf(colors);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int size = Math.min(getWidth(), getHeight()) - 8;
            int x = (getWidth() - size) / 2;
            int y = (getHeight() - size) / 2;
            Color base = moodColor(mood);
            Color top = colors.isEmpty() ? blend(base, Color.WHITE, 0.28f) : blend(colors.get(0), Color.WHITE, 0.22f);
            Color bottom = colors.size() > 1 ? blend(colors.get(1), base, 0.44f) : base;

            g2.setPaint(new GradientPaint(0, y, top, 0, y + size, bottom));
            g2.fillOval(x, y, size, size);
            g2.setColor(new Color(255, 255, 255, 130));
            g2.fillOval(x + size / 5, y + size / 7, size / 3, size / 5);
            g2.setColor(new Color(72, 84, 104, 130));
            g2.drawOval(x, y, size - 1, size - 1);

            int arcWidth = Math.max(16, size / 2);
            int arcHeight = Math.max(8, size / 4);
            int mouthY = y + size / 2 + Math.round((50 - mood) / 100f * 18f);
            int start = mood >= 50 ? 200 : 20;
            int extent = mood >= 50 ? 140 : 140;
            g2.setColor(new Color(52, 62, 78, 150));
            if (mood >= 50) {
                g2.drawArc(x + size / 2 - arcWidth / 2, mouthY - arcHeight / 2, arcWidth, arcHeight, start, extent);
            } else {
                g2.drawArc(x + size / 2 - arcWidth / 2, mouthY, arcWidth, arcHeight, start, extent);
            }
            g2.dispose();
        }

        private static Color moodColor(int mood) {
            int safe = clamp(mood);
            if (safe < 50) {
                float t = safe / 50f;
                return blend(new Color(75, 143, 210), new Color(196, 200, 205), t);
            }
            float t = (safe - 50) / 50f;
            return blend(new Color(196, 200, 205), new Color(242, 140, 74), t);
        }
    }
}
