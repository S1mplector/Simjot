package main.ui.features.entries;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.LinearGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicSliderUI;

import main.ui.components.buttons.RoundedButton;
import main.ui.components.slider.MoodSlider;
import main.ui.theme.aero.AeroTheme;

/**
 * Collapsible detailed mood logging panel with animated expand/collapse.
 * Uses gradient track sliders (no emoji face) and computes a composite score (0-100).
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

    private final JPanel inner;
    private boolean expanded = false;
    private int animMs = 160;
    private int targetHeight = 0;

    public DetailedMoodPanel(Consumer<Integer> onSave) {
        setOpaque(true);
        setBackground(new Color(0xE7, 0xE7, 0xE7));
        setLayout(new BorderLayout());

        inner = new JPanel(new GridBagLayout());
        inner.setOpaque(true);
        inner.setBackground(getBackground());
        inner.setBorder(new EmptyBorder(12, 16, 8, 16));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 6, 6, 6);
        gc.gridx = 0; gc.gridy = 0; gc.anchor = GridBagConstraints.WEST;
        addRow(inner, gc, "Joy", joy);
        gc.gridy++; addRow(inner, gc, "Calm", calm);
        gc.gridy++; addRow(inner, gc, "Gratitude", gratitude);
        gc.gridy++; addRow(inner, gc, "Energy", energy);
        gc.gridy++; addRow(inner, gc, "Sadness", sadness);
        gc.gridy++; addRow(inner, gc, "Anger", anger);
        gc.gridy++; addRow(inner, gc, "Anxiety", anxiety);
        gc.gridy++; addRow(inner, gc, "Stress", stress);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.setOpaque(true);
        btnPanel.setBackground(getBackground());
        RoundedButton cancel = new RoundedButton("Close");
        cancel.setForeground(AeroTheme.TEXT_PRIMARY);
        cancel.addActionListener(e -> setExpanded(false));
        RoundedButton save = new RoundedButton("Save");
        save.setForeground(AeroTheme.TEXT_PRIMARY);
        save.addActionListener(e -> {
            int composite = computeComposite();
            if (onSave != null) onSave.accept(composite);
            setExpanded(false);
        });
        btnPanel.add(cancel);
        btnPanel.add(save);

        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);
        content.add(inner, BorderLayout.CENTER);
        content.add(btnPanel, BorderLayout.SOUTH);

        add(content, BorderLayout.CENTER);

        // Start collapsed
        setPreferredSize(new Dimension(0, 0));
        setVisible(false);
    }

    public boolean isExpanded() { return expanded; }

    public void setExpanded(boolean expand) {
        if (this.expanded == expand) return;
        this.expanded = expand;
        if (expand) {
            // compute target height from inner preferred height
            targetHeight = calcInnerPreferredHeight();
            animateHeight(0, targetHeight);
        } else {
            int from = getHeight() == 0 ? calcInnerPreferredHeight() : getHeight();
            animateHeight(from, 0);
        }
    }

    private int calcInnerPreferredHeight() {
        inner.doLayout();
        inner.validate();
        return inner.getPreferredSize().height + 16 + 8; // padding from border + buttons
    }

    private void animateHeight(int from, int to) {
        setVisible(true);
        final long start = System.currentTimeMillis();
        final int dur = animMs;
        Timer t = new Timer(15, null);
        t.addActionListener(ev -> {
            float p = Math.min(1f, (System.currentTimeMillis() - start) / (float) dur);
            // ease-out cubic
            float e = 1 - (float) Math.pow(1 - p, 3);
            int h = from + Math.round((to - from) * e);
            setPreferredSize(new Dimension(getWidth(), h));
            revalidate();
            if (p >= 1f) {
                t.stop();
                if (to == 0) setVisible(false);
            }
        });
        t.start();
    }

    private void addRow(JPanel panel, GridBagConstraints gc, String label, MoodSlider slider) {
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
        s.setUI(new SimpleGradientSliderUI(s));
        s.setOpaque(false);
        s.setFocusable(false);
        s.setPreferredSize(new Dimension(220, 40));
        return s;
    }

    private int computeComposite() {
        double pos = avg(joy, calm, gratitude, energy);
        double neg = avg(sadness, anger, anxiety, stress);
        int val = (int) Math.round(50 + (pos - neg) / 2.0);
        return Math.max(0, Math.min(100, val));
    }

    private static double avg(MoodSlider... sliders) {
        double sum = 0;
        for (MoodSlider s : sliders) sum += s.getValue();
        return sum / sliders.length;
    }

    // Slider UI: gradient track, plain thumb (no face)
    private static class SimpleGradientSliderUI extends BasicSliderUI {
        private static final int TRACK_HEIGHT = 8;
        private static final int THUMB_SIZE = 24;

        public SimpleGradientSliderUI(javax.swing.JSlider b) { super(b); }

        @Override
        public Dimension getPreferredSize(JComponent c) {
            Dimension d = super.getPreferredSize(c);
            if (slider.getOrientation() == javax.swing.JSlider.HORIZONTAL) {
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

            Shape clip = g2.getClip();
            if (clip != null) {
                Rectangle cb = clip.getBounds();
                Color bg = new Color(0xE7, 0xE7, 0xE7);
                Color old = g2.getColor();
                g2.setColor(bg);
                g2.fillRect(cb.x, cb.y, cb.width, cb.height);
                g2.setColor(old);
            }

            LinearGradientPaint paint = new LinearGradientPaint(
                trackLeft, 0, trackLeft + trackWidth, 0,
                new float[]{0f, 0.5f, 1f},
                new Color[]{ new Color(0,122,204), new Color(200,200,200), new Color(255,120,50) }
            );
            g2.setPaint(paint);
            g2.fillRoundRect(trackLeft, trackTop, trackWidth, TRACK_HEIGHT, TRACK_HEIGHT, TRACK_HEIGHT);
            g2.dispose();
        }

        @Override
        public void paintThumb(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.WHITE);
            g2.fillOval(thumbRect.x, thumbRect.y, THUMB_SIZE, THUMB_SIZE);
            g2.setColor(new Color(130,130,130,140));
            g2.drawOval(thumbRect.x, thumbRect.y, THUMB_SIZE-1, THUMB_SIZE-1);
            g2.dispose();
        }
    }
}
