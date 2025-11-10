package main.ui.features.home;

import java.awt.*;
import java.awt.geom.Path2D;
import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;

final class MoodChartRenderer {
    private static final int MARGIN = 60;
    private final MoodChartSettings settings;
    private boolean cacheDirty = true;
    private int[] xPts, yPtsDaily, yPtsTrend;

    MoodChartRenderer(MoodChartSettings settings){ this.settings = settings; }

    void invalidate(){ cacheDirty = true; }

    int indexForX(int width, int x, int n){
        int w = Math.max(1, width - 2*MARGIN);
        return Math.round((float)(x - MARGIN) / Math.max(1, w) * Math.max(0, n - 1));
    }

    int getX(int i){ return xPts != null && i>=0 && i<xPts.length ? xPts[i] : -1; }
    int getYDaily(int i){ return yPtsDaily != null && i>=0 && i<yPtsDaily.length ? yPtsDaily[i] : Integer.MIN_VALUE; }

    void paint(Graphics2D g2, JComponent comp,
               List<LocalDate> days,
               List<Double> values,
               Map<LocalDate, List<File>> entriesByDate,
               Integer hoverIdx){
        int width = comp.getWidth();
        int height = comp.getHeight();
        int w = width - 2*MARGIN;
        int h = height - 2*MARGIN;
        if (w <= 0 || h <= 0 || days == null || days.isEmpty()) return;

        ensureCache(comp, days, values);

        // axes
        g2.setColor(Color.DARK_GRAY);
        g2.drawLine(MARGIN, height - MARGIN, MARGIN, MARGIN);
        g2.drawLine(MARGIN, height - MARGIN, width - MARGIN, height - MARGIN);

        for (int t = 0; t <= 4; t++) {
            int pct = t * 25;
            int y = height - MARGIN - (int)(pct / 100.0 * h);
            g2.setColor(new Color(200,200,200));
            g2.drawLine(MARGIN, y, width - MARGIN, y);
            g2.setColor(Color.DARK_GRAY);
            String lab = String.valueOf(pct);
            int tw = g2.getFontMetrics().stringWidth(lab);
            g2.drawString(lab, MARGIN - tw - 8, y + 4);
        }

        java.util.function.Function<Double, Color> colorFor = v -> {
            int p = (int)Math.round(v);
            if (p <= 33) return new Color(200,60,60);
            if (p <= 66) return new Color(230,160,50);
            return new Color(40,160,90);
        };

        int n = days.size();
        if (settings.isShowFill()) {
            LinearGradientPaint fillPaint = new LinearGradientPaint(
                new Point(0, MARGIN), new Point(0, height - MARGIN),
                new float[]{0f, 0.5f, 1f},
                new Color[]{new Color(40,160,90,60), new Color(230,160,50,50), new Color(200,60,60,70)}
            );
            g2.setPaint(fillPaint);
            int i = 0;
            while (i < n) {
                while (i < n && (yPtsDaily == null || yPtsDaily[i] == Integer.MIN_VALUE)) i++;
                if (i >= n) break;
                Path2D p = new Path2D.Double();
                int start = i;
                p.moveTo(xPts[i], yPtsDaily[i]);
                i++;
                while (i < n && yPtsDaily[i] != Integer.MIN_VALUE) { p.lineTo(xPts[i], yPtsDaily[i]); i++; }
                int end = i-1;
                p.lineTo(xPts[end], height - MARGIN);
                p.lineTo(xPts[start], height - MARGIN);
                p.closePath();
                g2.fill(p);
            }
        }

        Integer lastX = null, lastY = null;
        for (int i = 0; i < n; i++) {
            if (yPtsDaily != null && yPtsDaily[i] != Integer.MIN_VALUE) {
                int x = xPts[i];
                int y = yPtsDaily[i];
                if (lastX != null && lastY != null) {
                    g2.setColor(new Color(0,120,215));
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawLine(lastX, lastY, x, y);
                }
                Double v = values.get(i);
                g2.setColor(colorFor.apply(v));
                g2.fillOval(x-3,y-3,6,6);
                lastX = x; lastY = y;
            } else { lastX = null; lastY = null; }
        }

        if (settings.isShowTrend() && yPtsTrend != null) {
            lastX = null; lastY = null;
            g2.setColor(new Color(100,60,180));
            g2.setStroke(new BasicStroke(1.5f));
            for (int i = 0; i < n; i++) {
                if (yPtsTrend[i] != Integer.MIN_VALUE) {
                    int x = xPts[i];
                    int y = yPtsTrend[i];
                    if (lastX != null && lastY != null) g2.drawLine(lastX, lastY, x, y);
                    lastX = x; lastY = y;
                } else { lastX = null; lastY = null; }
            }
        }

        if (settings.isShowEntryTicks() && entriesByDate != null) {
            for (int i = 0; i < n; i++) {
                List<File> files = entriesByDate.get(days.get(i));
                int c = files == null ? 0 : files.size();
                if (c > 0) {
                    int x = xPts[i];
                    int y0 = height - MARGIN;
                    int len = Math.min(8, 2 + c);
                    g2.setColor(new Color(0,0,0, 60 + Math.min(140, c*20)));
                    g2.drawLine(x, y0, x, y0 - len);
                }
            }
        }

        if (hoverIdx != null && hoverIdx >= 0 && hoverIdx < n && xPts != null) {
            int x = xPts[hoverIdx];
            g2.setColor(new Color(0,0,0,40));
            g2.setStroke(new BasicStroke(1f));
            g2.drawLine(x, MARGIN, x, height - MARGIN);
            Double v = values.get(hoverIdx);
            if (v != null && yPtsDaily != null && yPtsDaily[hoverIdx] != Integer.MIN_VALUE) {
                String txt = days.get(hoverIdx) + "  " + (int)Math.round(v) + "/100";
                FontMetrics fm = g2.getFontMetrics();
                int tw = fm.stringWidth(txt) + 14;
                int th = fm.getHeight() + 6;
                int px = Math.min(Math.max(x - tw/2, MARGIN+2), width - MARGIN - tw - 2);
                int py = Math.max(MARGIN + 2, yPtsDaily[hoverIdx] - th - 8);
                g2.setColor(new Color(255,255,255,220));
                g2.fillRoundRect(px, py, tw, th, 8, 8);
                g2.setColor(new Color(0,0,0,140));
                g2.drawRoundRect(px, py, tw, th, 8, 8);
                g2.setColor(Color.DARK_GRAY);
                g2.drawString(txt, px + 7, py + th - 6);
            }
        }

        // date labels
        g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("MM-dd");
        if (!days.isEmpty()) {
            int labelTarget = 8;
            int step = Math.max(1, days.size() / labelTarget);
            for (int i = 0; i < days.size(); i += step) {
                int x = MARGIN + (int)(i * (days.size()>1? (double)w/(days.size()-1): w));
                String txt = fmt.format(days.get(i));
                int tw = g2.getFontMetrics().stringWidth(txt);
                g2.drawString(txt, x - tw/2, height - MARGIN + 15);
            }
        }
    }

    private void ensureCache(JComponent comp, List<LocalDate> days, List<Double> values){
        if (!cacheDirty) return;
        int width = comp.getWidth();
        int height = comp.getHeight();
        int w = Math.max(1, width - 2*MARGIN);
        int h = Math.max(1, height - 2*MARGIN);
        int n = days.size();
        xPts = new int[n];
        yPtsDaily = new int[n];
        yPtsTrend = new int[n];
        for (int i = 0; i < n; i++) {
            xPts[i] = MARGIN + (int)(i * (n>1? (double)w/(n-1): w));
            Double v = values.get(i);
            if (v == null) yPtsDaily[i] = Integer.MIN_VALUE;
            else yPtsDaily[i] = height - MARGIN - (int)(v / 100.0 * h);
        }
        int win = Math.max(1, settings.getTrendWindow());
        for (int i = 0; i < n; i++) {
            int start = Math.max(0, i - (win-1));
            int end = i;
            double sum = 0; int cnt = 0;
            for (int j = start; j <= end; j++) {
                Double v = values.get(j);
                if (v != null) { sum += v; cnt++; }
            }
            if (cnt == 0) yPtsTrend[i] = Integer.MIN_VALUE;
            else yPtsTrend[i] = height - MARGIN - (int)((sum/cnt) / 100.0 * h);
        }
        cacheDirty = false;
    }
}
