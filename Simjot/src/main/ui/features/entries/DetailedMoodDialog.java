/*
 * SIMJOT - PROPRIETARY
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 * 
 * This source code is licensed under the Simjot Source-Available Personal Use License.
 * You may view and study this code for personal, non-commercial use.
 * Distribution, commercial use, and derivative works are strictly prohibited.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.entries;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.LinearGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Window;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JComponent;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicSliderUI;

import main.ui.components.buttons.RoundedButton;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.slider.MoodSlider;
import main.ui.theme.aero.AeroTheme;

/**
 * A modal dialog that lets the user log detailed emotions using multiple sliders.
 * Computes a composite 0-100 mood score with the formula:
 *   composite = clamp(50 + (avgPositive - avgNegative) / 2)
 * where positive = [Joy, Calm, Gratitude, Energy], negative = [Sadness, Anger, Anxiety, Stress].
 */
public class DetailedMoodDialog extends JDialog {
    private final MoodSlider joy = createEmotionSlider();
    private final MoodSlider calm = createEmotionSlider();
    private final MoodSlider gratitude = createEmotionSlider();
    private final MoodSlider energy = createEmotionSlider();
    private final MoodSlider sadness = createEmotionSlider();
    private final MoodSlider anger = createEmotionSlider();
    private final MoodSlider anxiety = createEmotionSlider();
    private final MoodSlider stress = createEmotionSlider();

    private boolean okPressed = false;
    private int compositeScore = 50;

    public DetailedMoodDialog(Window owner) {
        super(owner, "Detailed Mood", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        FrostedGlassPanel root = new FrostedGlassPanel(new BorderLayout(10, 10), 16);
        root.setBorder(new EmptyBorder(12, 16, 8, 16));

        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 6, 6, 6);
        gc.gridx = 0; gc.gridy = 0; gc.anchor = GridBagConstraints.WEST;
        addRow(content, gc, "Joy", joy);
        gc.gridy++; addRow(content, gc, "Calm", calm);
        gc.gridy++; addRow(content, gc, "Gratitude", gratitude);
        gc.gridy++; addRow(content, gc, "Energy", energy);
        gc.gridy++; addRow(content, gc, "Sadness", sadness);
        gc.gridy++; addRow(content, gc, "Anger", anger);
        gc.gridy++; addRow(content, gc, "Anxiety", anxiety);
        gc.gridy++; addRow(content, gc, "Stress", stress);

        root.add(content, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.setOpaque(false);
        RoundedButton cancel = new RoundedButton("Cancel");
        cancel.setForeground(AeroTheme.TEXT_PRIMARY);
        cancel.addActionListener(e -> { okPressed = false; dispose(); });
        RoundedButton ok = new RoundedButton("Save");
        ok.setForeground(AeroTheme.TEXT_PRIMARY);
        ok.addActionListener(e -> {
            compositeScore = computeComposite();
            okPressed = true;
            dispose();
        });
        btnPanel.add(cancel);
        btnPanel.add(ok);
        root.add(btnPanel, BorderLayout.SOUTH);

        setContentPane(root);

        pack();
        setMinimumSize(new Dimension(Math.max(420, getWidth()), Math.max(360, getHeight())));
    }

    private void addRow(JPanel panel, GridBagConstraints gc, String label, JSlider slider) {
        JLabel l = new JLabel(label);
        l.setFont(AeroTheme.defaultFont());
        l.setForeground(AeroTheme.TEXT_PRIMARY);
        panel.add(l, gc);
        gc.gridx = 1; gc.weightx = 1.0; gc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(slider, gc);
        gc.gridx = 0; gc.weightx = 0; gc.fill = GridBagConstraints.NONE;
    }

    private static MoodSlider createEmotionSlider() {
        MoodSlider s = new MoodSlider();
        // Replace UI: keep gradient track but remove emoji face on thumb
        s.setUI(new SimpleGradientSliderUI(s));
        s.setOpaque(false);
        s.setFocusable(false);
        // Slightly narrower for dialog rows
        s.setPreferredSize(new Dimension(220, 40));
        return s;
    }

    /**
     * Basic slider UI with the same gradient track as MoodSlider,
     * but a plain circular thumb (no emoji face).
     */
    private static class SimpleGradientSliderUI extends BasicSliderUI {
        private static final int TRACK_HEIGHT = 8;
        private static final int THUMB_SIZE = 24;

        public SimpleGradientSliderUI(JSlider b) { super(b); }

        @Override
        public Dimension getPreferredSize(JComponent c) {
            Dimension d = super.getPreferredSize(c);
            if (slider.getOrientation() == JSlider.HORIZONTAL) {
                d.height = Math.max(d.height, 40);
            }
            return d;
        }

        @Override
        protected void calculateThumbSize() {
            thumbRect.setSize(THUMB_SIZE, THUMB_SIZE);
        }

        @Override
        public void paintTrack(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int trackLeft = trackRect.x;
            int trackTop = trackRect.y + (trackRect.height - TRACK_HEIGHT) / 2;
            int trackWidth = trackRect.width;
            if (trackWidth <= 0) {
                g2.dispose();
                return;
            }

            int plateH = TRACK_HEIGHT + 10;
            int plateTop = trackTop - 5;
            g2.setPaint(new Color(255, 255, 255, 110));
            g2.fillRoundRect(trackLeft, plateTop, trackWidth, plateH, plateH, plateH);
            g2.setColor(new Color(0, 0, 0, 35));
            g2.drawRoundRect(trackLeft, plateTop, trackWidth, plateH, plateH, plateH);

            // Gradient: blue (0) → grey (50) → orange (100)
            LinearGradientPaint paint = new LinearGradientPaint(
                    trackLeft, 0, trackLeft + trackWidth, 0,
                    new float[] { 0f, 0.5f, 1f },
                    new Color[] {
                            new Color(0, 122, 204),
                            new Color(200, 200, 200),
                            new Color(255, 120, 50)
                    });
            g2.setPaint(paint);
            g2.fillRoundRect(trackLeft, trackTop, trackWidth, TRACK_HEIGHT, TRACK_HEIGHT, TRACK_HEIGHT);
            g2.setColor(new Color(255, 255, 255, 120));
            g2.drawRoundRect(trackLeft, trackTop, trackWidth, TRACK_HEIGHT, TRACK_HEIGHT, TRACK_HEIGHT);
            g2.dispose();
        }

        @Override
        public void paintThumb(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int cx = thumbRect.x;
            int cy = thumbRect.y;
            g2.setColor(new Color(0, 0, 0, 50));
            g2.fillOval(cx + 1, cy + 2, THUMB_SIZE - 2, THUMB_SIZE - 2);
            g2.setPaint(new GradientPaint(0, cy, new Color(255, 255, 255, 245),
                    0, cy + THUMB_SIZE, new Color(235, 235, 235, 215)));
            g2.fillOval(cx, cy, THUMB_SIZE, THUMB_SIZE);
            g2.setColor(new Color(160, 160, 160, 160));
            g2.drawOval(cx, cy, THUMB_SIZE - 1, THUMB_SIZE - 1);
            g2.setColor(new Color(255, 255, 255, 180));
            g2.drawOval(cx + 1, cy + 1, THUMB_SIZE - 3, THUMB_SIZE - 3);
            g2.dispose();
        }
    }

    private int computeComposite() {
        double pos = avg(joy, calm, gratitude, energy);
        double neg = avg(sadness, anger, anxiety, stress);
        int val = (int) Math.round(50 + (pos - neg) / 2.0);
        return Math.max(0, Math.min(100, val));
    }

    private static double avg(JSlider... sliders) {
        double sum = 0;
        for (JSlider s : sliders) sum += s.getValue();
        return sum / sliders.length;
    }

    public boolean isOkPressed() { return okPressed; }
    public int getCompositeScore() { return compositeScore; }
}
