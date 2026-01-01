/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.infrastructure.monitoring;

import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

import main.infrastructure.ffi.NativeAccess;
import main.ui.theme.aero.AeroTheme;

@SuppressWarnings("serial")
public class RamMonitor extends JPanel {
    private static final long MB = 1024L * 1024L;
    private static final int UPDATE_MS = 1000;

    private final JLabel cpuLabel;
    private final JLabel ramLabel;
    private final MetricSparkline cpuGraph;
    private final MetricSparkline ramGraph;
    private NativeAccess.PerfSnapshot lastSnapshot;
    private double lastCpuPercent;

    public RamMonitor() {
        setLayout(new GridBagLayout());
        setOpaque(false);
        setBackground(new Color(0, 0, 0, 0));

        cpuLabel = new JLabel("CPU: --");
        cpuLabel.setForeground(AeroTheme.TEXT_PRIMARY);
        cpuLabel.setFont(AeroTheme.defaultFont().deriveFont(11f));
        cpuLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        ramLabel = new JLabel("RAM: --");
        ramLabel.setForeground(AeroTheme.TEXT_PRIMARY);
        ramLabel.setFont(AeroTheme.defaultFont().deriveFont(11f));
        ramLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        cpuGraph = new MetricSparkline(new Color(240, 140, 30), new Color(240, 140, 30, 70));
        ramGraph = new MetricSparkline(new Color(0, 120, 215), new Color(0, 120, 215, 70));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(0, 2, 2, 6);
        gc.gridx = 0;
        gc.gridy = 0;
        gc.anchor = GridBagConstraints.WEST;
        add(cpuLabel, gc);

        gc.gridx = 1;
        gc.weightx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        add(cpuGraph, gc);

        gc.gridx = 0;
        gc.gridy = 1;
        gc.weightx = 0;
        gc.fill = GridBagConstraints.NONE;
        gc.insets = new Insets(2, 2, 0, 6);
        add(ramLabel, gc);

        gc.gridx = 1;
        gc.weightx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        add(ramGraph, gc);

        Timer timer = new Timer(UPDATE_MS, e -> refreshMetrics());
        timer.start();
    }

    private void refreshMetrics() {
        NativeAccess.PerfSnapshot snap = NativeAccess.perfSnapshot();
        if (snap == null) {
            Runtime runtime = Runtime.getRuntime();
            long used = runtime.totalMemory() - runtime.freeMemory();
            long max = runtime.maxMemory();
            double memPercent = max > 0 ? Math.min(1.0, used / (double) max) : 0.0;
            ramLabel.setText("RAM: " + formatBytes(used) + " app");
            ramLabel.setToolTipText("Heap usage");
            ramGraph.addSample(memPercent);
            cpuLabel.setText("CPU: --");
            cpuLabel.setToolTipText("Native metrics unavailable");
            cpuGraph.addSample(0.0);
            cpuGraph.repaint();
            ramGraph.repaint();
            return;
        }

        double cpuPercent = lastCpuPercent;
        if (lastSnapshot != null && snap.timestampNs > lastSnapshot.timestampNs) {
            long cpuNow = snap.cpuUserNs + snap.cpuSystemNs;
            long cpuPrev = lastSnapshot.cpuUserNs + lastSnapshot.cpuSystemNs;
            long cpuDelta = cpuNow - cpuPrev;
            long timeDelta = snap.timestampNs - lastSnapshot.timestampNs;
            int cores = snap.cpuCount > 0 ? snap.cpuCount : 1;
            if (timeDelta > 0) {
                cpuPercent = (cpuDelta / (double) timeDelta) * 100.0 / cores;
                cpuPercent = Math.max(0.0, Math.min(100.0, cpuPercent));
            }
        }
        lastSnapshot = snap;
        lastCpuPercent = cpuPercent;

        cpuLabel.setText(String.format(Locale.ROOT, "CPU: %.0f%%", cpuPercent));
        cpuLabel.setToolTipText("Process CPU usage");
        cpuGraph.addSample(cpuPercent / 100.0);

        long rss = snap.rssBytes;
        long sysTotal = snap.sysTotalBytes;
        long sysAvail = snap.sysAvailBytes;
        long sysUsed = (sysTotal > 0 && sysAvail > 0) ? Math.max(0, sysTotal - sysAvail) : 0;
        ramLabel.setText("RAM: " + formatBytes(rss) + " app");
        if (sysTotal > 0) {
            ramLabel.setToolTipText("System: " + formatBytes(sysUsed) + " / " + formatBytes(sysTotal));
        } else {
            ramLabel.setToolTipText("Process RSS");
        }
        double memPercent = sysTotal > 0 ? Math.min(1.0, sysUsed / (double) sysTotal) : 0.0;
        ramGraph.addSample(memPercent);

        cpuGraph.repaint();
        ramGraph.repaint();
    }

    private static String formatBytes(long bytes) {
        if (bytes <= 0) return "0 MB";
        double mb = bytes / (double) MB;
        if (mb >= 1024) {
            return String.format(Locale.ROOT, "%.1f GB", mb / 1024.0);
        }
        return String.format(Locale.ROOT, "%.0f MB", mb);
    }

    private static final class MetricSparkline extends javax.swing.JComponent {
        private static final int MAX_SAMPLES = 120;
        private final Deque<Double> samples = new ArrayDeque<>(MAX_SAMPLES);
        private final Color lineColor;
        private final Color fillColor;

        MetricSparkline(Color lineColor, Color fillColor) {
            this.lineColor = lineColor;
            this.fillColor = fillColor;
            setOpaque(false);
            setPreferredSize(new Dimension(150, 14));
            setMinimumSize(new Dimension(90, 12));
        }

        void addSample(double value) {
            double v = Math.max(0, Math.min(1, value));
            if (samples.size() >= MAX_SAMPLES) {
                samples.removeFirst();
            }
            samples.addLast(v);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            g2.setColor(new Color(255, 255, 255, 120));
            g2.fillRoundRect(0, 0, w - 1, h - 1, 8, 8);
            g2.setColor(new Color(180, 180, 180, 140));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);

            if (samples.isEmpty()) {
                g2.dispose();
                return;
            }

            int n = samples.size();
            int plotW = Math.max(1, w - 10);
            int plotH = Math.max(1, h - 6);
            int x0 = 5;
            int y0 = 3;

            double[] pts = new double[n];
            int i = 0;
            for (Double v : samples) pts[i++] = v;

            Polygon area = new Polygon();
            for (int k = 0; k < n; k++) {
                double norm = pts[k];
                int x = x0 + (int) Math.round((k / (double) (MAX_SAMPLES - 1)) * plotW);
                int y = y0 + plotH - (int) Math.round(norm * plotH);
                area.addPoint(x, y);
            }
            area.addPoint(x0 + (int) Math.round((n - 1) / (double) (MAX_SAMPLES - 1) * plotW), y0 + plotH);
            area.addPoint(x0, y0 + plotH);

            g2.setPaint(new GradientPaint(0, y0, fillColor, 0, y0 + plotH, new Color(fillColor.getRed(), fillColor.getGreen(), fillColor.getBlue(), 20)));
            g2.fill(area);

            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(lineColor);
            int prevX = -1, prevY = -1;
            for (int k = 0; k < n; k++) {
                double norm = pts[k];
                int x = x0 + (int) Math.round((k / (double) (MAX_SAMPLES - 1)) * plotW);
                int y = y0 + plotH - (int) Math.round(norm * plotH);
                if (prevX >= 0) {
                    g2.drawLine(prevX, prevY, x, y);
                }
                prevX = x;
                prevY = y;
            }

            g2.dispose();
        }
    }
}
