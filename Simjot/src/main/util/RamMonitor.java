package main.util;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayDeque;
import java.util.Deque;
import main.ui.theme.aero.AeroTheme;

@SuppressWarnings("serial")
public class RamMonitor extends JPanel {
    private JLabel ramLabel;
    private Timer timer;
    private final RamGraph graph;

    public RamMonitor() {
        // Vertical stack: label on top, graph below
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setBackground(new Color(0,0,0,0));
        
        ramLabel = new JLabel("RAM: 0 MB");
        ramLabel.setForeground(AeroTheme.TEXT_PRIMARY);
        ramLabel.setFont(AeroTheme.defaultFont());
        ramLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(ramLabel);

        graph = new RamGraph();
        graph.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(Box.createVerticalStrut(2));
        add(graph);

        // Update every second (1000 ms)
        timer = new Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Runtime runtime = Runtime.getRuntime();
                long usedMemory = runtime.totalMemory() - runtime.freeMemory();
                long usedMB = usedMemory / (1024 * 1024);
                ramLabel.setText("RAM: " + usedMB + " MB");
                graph.addSample(usedMB, runtime.maxMemory() / (1024 * 1024));
                graph.repaint();
            }
        });
        timer.start();
    }

    // Small line graph for RAM usage
    private static class RamGraph extends JComponent {
        private static final int MAX_SAMPLES = 180; // ~3 minutes at 1 Hz
        private final Deque<Double> samples = new ArrayDeque<>(MAX_SAMPLES);
        private double maxRamMB = 1024.0; // default guard

        RamGraph() {
            setOpaque(false);
            setPreferredSize(new Dimension(200, 28));
            setMinimumSize(new Dimension(120, 24));
        }

        void addSample(long usedMB, long maxMB) {
            if (maxMB > 0) {
                this.maxRamMB = maxMB;
            }
            double v = Math.max(0, Math.min(maxRamMB, (double) usedMB));
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

            // Subtle translucent background so the plot is readable
            g2.setColor(new Color(255, 255, 255, 120));
            g2.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);
            g2.setColor(new Color(180, 180, 180, 160));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);

            if (samples.isEmpty() || maxRamMB <= 0) {
                g2.dispose();
                return;
            }

            // Prepare polyline points
            int n = samples.size();
            int plotW = Math.max(1, w - 12); // padding
            int plotH = Math.max(1, h - 10);
            int x0 = 6, y0 = 4;

            double[] pts = new double[n];
            int i = 0;
            for (Double v : samples) {
                pts[i++] = v;
            }

            // Fill under curve
            Polygon area = new Polygon();
            for (int k = 0; k < n; k++) {
                double norm = pts[k] / maxRamMB; // 0..1
                int x = x0 + (int) Math.round((k / (double) (MAX_SAMPLES - 1)) * plotW);
                int y = y0 + plotH - (int) Math.round(norm * plotH);
                area.addPoint(x, y);
            }
            // Close to bottom
            area.addPoint(x0 + (int) Math.round((n - 1) / (double) (MAX_SAMPLES - 1) * plotW), y0 + plotH);
            area.addPoint(x0, y0 + plotH);
            g2.setPaint(new GradientPaint(0, y0, new Color(0, 120, 215, 80), 0, y0 + plotH, new Color(0, 120, 215, 20)));
            g2.fill(area);

            // Draw line
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(0, 120, 215));
            int prevX = -1, prevY = -1;
            for (int k = 0; k < n; k++) {
                double norm = pts[k] / maxRamMB;
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
