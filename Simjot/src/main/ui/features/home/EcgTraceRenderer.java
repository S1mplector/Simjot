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

package main.ui.features.home;

import java.awt.*;
import java.awt.geom.*;
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

    public void onBeatSync(){ phase = 0.45; }

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
        Path2D path = new Path2D.Double();
        int i = 0;
        for (Float v : buf) {
            int x = xStart + i;
            int y = yBase - Math.round(amplitude * v);
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            i++;
            if (i >= capacity) break;
        }
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

    private static float sample(double p){
        double v = g(p, 0.20, 0.02, 0.15)
                + g(p, 0.45, 0.012, -0.15)
                + g(p, 0.50, 0.006, 1.0)
                + g(p, 0.55, 0.012, -0.25)
                + g(p, 0.70, 0.035, 0.35);
        v += 0.02 * Math.sin(2 * Math.PI * p * 3.0);
        return (float) v;
    }

    private static float g(double x, double m, double s, double a){
        double z = (x - m) / s;
        return (float) (a * Math.exp(-0.5 * z * z));
    }
}
