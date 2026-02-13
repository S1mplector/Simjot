/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.home;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Path2D;
import java.util.ArrayDeque;

public final class EcgTraceRenderer {
    private final ArrayDeque<Float> buf = new ArrayDeque<>();
    private int capacity = 150;
    private double phase = 0.0;
    private double step = 0.01;

    public void setCapacity(int c){
        capacity = Math.max(20, c);
        while (buf.size() > capacity) buf.removeFirst();
    }
    public int getCapacity(){ return capacity; }

    public void setStep(double s){ step = Math.max(0.001, s); }

    public void onBeatSync(){ phase = 0.31; }

    public void advance(int samples){
        for (int i = 0; i < samples; i++) {
            phase += step;
            if (phase >= 1.0) phase -= 1.0;
            float v = sample(phase);
            buf.addLast(v);
            if (buf.size() > capacity) buf.removeFirst();
        }
    }

    public void paint(Graphics2D g2, int xStart, int yBase, float amplitude){
        if (buf.isEmpty()) return;
        Path2D path = createPath(xStart, yBase, amplitude);
        Stroke oldS = g2.getStroke();
        Color oldC = g2.getColor();
        g2.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(255,255,255, 220));
        g2.draw(path);
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(new Color(255,255,255, 160));
        g2.draw(path);
        g2.setStroke(oldS);
        g2.setColor(oldC);
    }

    public Path2D createPath(int xStart, int yBase, float amplitude) {
        Path2D path = new Path2D.Float();
        if (buf.isEmpty()) return path;
        int i = 0;
        for (Float v : buf) {
            int x = xStart + i;
            int y = yBase - Math.round(amplitude * v);
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
            i++;
            if (i >= capacity) break;
        }
        return path;
    }

    /**
     * Physiologic ECG morphology approximation (Lead-II-like, non-diagnostic).
     *
     * Phase is one full cardiac cycle in [0..1]:
     * - P wave around 0.17
     * - QRS complex around 0.38..0.43
     * - T wave around 0.64
     * - small U wave near 0.81
     */
    private static float sample(double p){
        float phase = (float) (p - Math.floor(p));

        float pWave = gaussian(phase, 0.17f, 0.030f, 0.12f);
        float qWave = gaussian(phase, 0.365f, 0.010f, -0.12f);
        float rWave = gaussian(phase, 0.395f, 0.0075f, 1.08f);
        float sWave = gaussian(phase, 0.425f, 0.012f, -0.26f);

        // Slightly asymmetric T wave (broad rise, longer tail)
        float tCore = gaussian(phase, 0.64f, 0.060f, 0.30f);
        float tTail = gaussian(phase, 0.69f, 0.095f, 0.08f);

        // Optional subtle U wave seen in some healthy traces.
        float uWave = gaussian(phase, 0.81f, 0.022f, 0.030f);

        // Very light baseline wander (no random jitter; keeps morphology clean).
        float baselineWander = (float) (0.010f * Math.sin(2.0 * Math.PI * phase));

        return pWave + qWave + rWave + sWave + tCore + tTail + uWave + baselineWander;
    }

    private static float gaussian(float x, float mean, float sigma, float amplitude) {
        float safeSigma = Math.max(0.001f, sigma);
        float z = (x - mean) / safeSigma;
        return (float) (amplitude * Math.exp(-0.5 * z * z));
    }
}
