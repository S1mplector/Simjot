/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.home;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Path2D;
import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import main.core.service.NotebookStore;
import main.infrastructure.backup.NotebookInfo;
import main.ui.app.JournalApp;
import main.ui.components.buttons.IconMenuButton;

/**
 * Modern mood chart panel with beautiful gradients,
 * smooth animations, and insightful analytics cards.
 * 
 * @author S1mplector
 */
public class ModernMoodChartPanel extends JPanel {
    
    private static final Color BG_GRADIENT_TOP = new Color(250, 252, 255);
    private static final Color BG_GRADIENT_BOTTOM = new Color(235, 242, 250);
    private static final Color CARD_BG = new Color(255, 255, 255, 240);
    private static final Color CARD_SHADOW = new Color(0, 0, 0, 15);
    private static final Color ACCENT_GREEN = new Color(52, 199, 89);
    private static final Color ACCENT_ORANGE = new Color(255, 149, 0);
    private static final Color ACCENT_RED = new Color(255, 59, 48);
    private static final Color ACCENT_BLUE = new Color(0, 122, 255);
    private static final Color ACCENT_PURPLE = new Color(175, 82, 222);
    private static final Color TEXT_PRIMARY = new Color(30, 30, 30);
    private static final Color TEXT_SECONDARY = new Color(120, 120, 130);
    
    private final MoodChartModel model = new MoodChartModel();
    private final JournalApp app;
    private final CardLayout cardLayout;
    private final JPanel cardPanel;
    
    private int selectedRangeIndex = 1; // 30 days default
    private final String[] ranges = {"7 Days", "30 Days", "90 Days", "1 Year", "All Time"};
    
    private Integer hoverIndex = null;
    private Point mousePoint = null;
    
    // Animated values
    private float animatedAvg = 0;
    private Timer animationTimer;
    
    public ModernMoodChartPanel(JournalApp app, CardLayout cardLayout, JPanel cardPanel) {
        this.app = app;
        this.cardLayout = cardLayout;
        this.cardPanel = cardPanel;
        
        setLayout(new BorderLayout());
        setOpaque(false);
        
        // Header with title and range selector
        add(createHeader(), BorderLayout.NORTH);
        
        // Main content: chart area
        JPanel chartArea = createChartArea();
        add(chartArea, BorderLayout.CENTER);
        
        // Footer with back button
        add(createFooter(), BorderLayout.SOUTH);
        
        // Load data
        loadData();
        
        // Animation timer for smooth transitions
        animationTimer = new Timer(16, e -> {
            float target = (float) model.getOverallAverage();
            animatedAvg += (target - animatedAvg) * 0.1f;
            if (Math.abs(target - animatedAvg) < 0.5f) {
                animatedAvg = target;
                animationTimer.stop();
            }
            repaint();
        });
        
        // Refresh on show
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                loadData();
                animationTimer.start();
            }
        });
    }
    
    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Subtle gradient
                GradientPaint gp = new GradientPaint(0, 0, new Color(255, 255, 255, 200),
                    0, getHeight(), new Color(245, 248, 252, 200));
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                
                // Bottom border
                g2.setColor(new Color(200, 210, 220));
                g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
                
                g2.dispose();
            }
        };
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(20, 24, 16, 24));
        
        // Title section
        JPanel titleSection = new JPanel();
        titleSection.setOpaque(false);
        titleSection.setLayout(new BoxLayout(titleSection, BoxLayout.Y_AXIS));
        
        JLabel title = new JLabel("Mood Insights");
        title.setFont(new Font("SF Pro Display", Font.BOLD, 28));
        title.setForeground(TEXT_PRIMARY);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JLabel subtitle = new JLabel("Track your emotional wellness over time");
        subtitle.setFont(new Font("SF Pro Text", Font.PLAIN, 14));
        subtitle.setForeground(TEXT_SECONDARY);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        titleSection.add(title);
        titleSection.add(Box.createVerticalStrut(4));
        titleSection.add(subtitle);
        
        header.add(titleSection, BorderLayout.WEST);
        
        // Range selector
        JPanel rangePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rangePanel.setOpaque(false);
        
        for (int i = 0; i < ranges.length; i++) {
            final int idx = i;
            JButton btn = createRangeButton(ranges[i], i == selectedRangeIndex);
            btn.addActionListener(e -> {
                selectedRangeIndex = idx;
                loadData();
                animationTimer.start();
                updateRangeButtons(rangePanel);
            });
            rangePanel.add(btn);
        }
        
        header.add(rangePanel, BorderLayout.EAST);
        
        return header;
    }
    
    private JButton createRangeButton(String text, boolean selected) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                boolean isSelected = getText().equals(ranges[selectedRangeIndex]);
                if (getModel().isPressed() || isSelected) {
                    g2.setColor(ACCENT_BLUE);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                    g2.setColor(Color.WHITE);
                } else if (getModel().isRollover()) {
                    g2.setColor(new Color(230, 235, 245));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                    g2.setColor(TEXT_PRIMARY);
                } else {
                    g2.setColor(TEXT_SECONDARY);
                }
                
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), x, y);
                
                g2.dispose();
            }
        };
        btn.setFont(new Font("SF Pro Text", Font.PLAIN, 13));
        btn.setPreferredSize(new Dimension(75, 32));
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }
    
    private void updateRangeButtons(JPanel panel) {
        Component[] comps = panel.getComponents();
        for (int i = 0; i < comps.length; i++) {
            if (comps[i] instanceof JButton) {
                comps[i].repaint();
            }
        }
    }
    
    private JPanel createChartArea() {
        JPanel area = new JPanel(new BorderLayout(0, 16)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Background gradient
                GradientPaint gp = new GradientPaint(0, 0, BG_GRADIENT_TOP,
                    0, getHeight(), BG_GRADIENT_BOTTOM);
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                
                g2.dispose();
            }
        };
        area.setOpaque(false);
        area.setBorder(new EmptyBorder(16, 24, 16, 24));
        
        // Stats cards row
        JPanel statsRow = new JPanel(new GridLayout(1, 4, 16, 0));
        statsRow.setOpaque(false);
        statsRow.setPreferredSize(new Dimension(0, 100));
        
        statsRow.add(createStatCard("Overall", () -> String.format("%.0f", animatedAvg), 
            () -> getMoodIcon(animatedAvg), () -> getMoodColor(animatedAvg)));
        statsRow.add(createStatCard("Trend", this::getTrendText, 
            this::getTrendIcon, this::getTrendColor));
        statsRow.add(createStatCard("Streak", this::getStreakText, 
            () -> "*", this::getStreakColor));
        statsRow.add(createStatCard("Entries", () -> String.valueOf(model.getDays().size()),
            () -> "#", () -> ACCENT_PURPLE));
        
        area.add(statsRow, BorderLayout.NORTH);
        
        // Chart panel
        JPanel chartPanel = new ChartPanel();
        area.add(chartPanel, BorderLayout.CENTER);
        
        return area;
    }
    
    private JPanel createStatCard(String label, java.util.function.Supplier<String> valueSupplier,
                                   java.util.function.Supplier<String> iconSupplier,
                                   java.util.function.Supplier<Color> colorSupplier) {
        return new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
                
                // Card shadow
                g2.setColor(CARD_SHADOW);
                g2.fillRoundRect(2, 4, getWidth() - 4, getHeight() - 4, 16, 16);
                
                // Card background
                g2.setColor(CARD_BG);
                g2.fillRoundRect(0, 0, getWidth() - 4, getHeight() - 6, 16, 16);
                
                // Icon circle
                Color accent = colorSupplier.get();
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 30));
                g2.fillOval(16, 16, 40, 40);
                
                // Icon
                g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));
                String icon = iconSupplier.get();
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(icon, 36 - fm.stringWidth(icon) / 2, 42);
                
                // Value
                g2.setFont(new Font("SF Pro Display", Font.BOLD, 24));
                g2.setColor(TEXT_PRIMARY);
                String value = valueSupplier.get();
                g2.drawString(value, 68, 42);
                
                // Label
                g2.setFont(new Font("SF Pro Text", Font.PLAIN, 12));
                g2.setColor(TEXT_SECONDARY);
                g2.drawString(label, 68, 60);
                
                g2.dispose();
            }
        };
    }
    
    private class ChartPanel extends JPanel {
        private static final int MARGIN_LEFT = 50;
        private static final int MARGIN_RIGHT = 20;
        private static final int MARGIN_TOP = 20;
        private static final int MARGIN_BOTTOM = 50;
        
        ChartPanel() {
            setOpaque(false);
            
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    mousePoint = e.getPoint();
                    updateHoverIndex();
                    repaint();
                }
            });
            
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent e) {
                    hoverIndex = null;
                    mousePoint = null;
                    repaint();
                }
                
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (hoverIndex != null && hoverIndex >= 0 && hoverIndex < model.getDays().size()) {
                        LocalDate d = model.getDays().get(hoverIndex);
                        List<File> files = model.getEntriesByDate().get(d);
                        if (files != null && !files.isEmpty()) {
                            openEntry(files.get(0));
                        }
                    }
                }
            });
        }
        
        private void updateHoverIndex() {
            if (mousePoint == null || model.getDays().isEmpty()) {
                hoverIndex = null;
                return;
            }
            
            int chartW = getWidth() - MARGIN_LEFT - MARGIN_RIGHT;
            int n = model.getDays().size();
            if (n <= 1 || chartW <= 0) {
                hoverIndex = null;
                return;
            }
            
            float step = (float) chartW / (n - 1);
            int idx = Math.round((mousePoint.x - MARGIN_LEFT) / step);
            if (idx >= 0 && idx < n) {
                hoverIndex = idx;
            } else {
                hoverIndex = null;
            }
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            
            int w = getWidth();
            int h = getHeight();
            int chartW = w - MARGIN_LEFT - MARGIN_RIGHT;
            int chartH = h - MARGIN_TOP - MARGIN_BOTTOM;
            
            // Card background
            g2.setColor(CARD_SHADOW);
            g2.fillRoundRect(2, 4, w - 4, h - 4, 20, 20);
            g2.setColor(CARD_BG);
            g2.fillRoundRect(0, 0, w - 4, h - 6, 20, 20);
            
            if (model.getDays().isEmpty() || model.getValues().stream().allMatch(Objects::isNull)) {
                g2.setFont(new Font("SF Pro Text", Font.PLAIN, 16));
                g2.setColor(TEXT_SECONDARY);
                String msg = "No mood data yet. Start journaling to see insights!";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, h / 2);
                g2.dispose();
                return;
            }
            
            List<LocalDate> days = model.getDays();
            List<Double> values = model.getValues();
            int n = days.size();
            
            // Draw grid lines
            g2.setColor(new Color(230, 235, 240));
            g2.setStroke(new BasicStroke(1f));
            for (int i = 0; i <= 4; i++) {
                int y = MARGIN_TOP + (int) ((4 - i) / 4.0 * chartH);
                g2.drawLine(MARGIN_LEFT, y, w - MARGIN_RIGHT, y);
                
                // Y-axis labels
                g2.setFont(new Font("SF Pro Text", Font.PLAIN, 11));
                g2.setColor(TEXT_SECONDARY);
                String label = String.valueOf(i * 25);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(label, MARGIN_LEFT - fm.stringWidth(label) - 8, y + 4);
                g2.setColor(new Color(230, 235, 240));
            }
            
            // Build path
            Path2D.Float linePath = new Path2D.Float();
            Path2D.Float fillPath = new Path2D.Float();
            boolean started = false;
            int firstX = 0, lastX = 0;
            
            for (int i = 0; i < n; i++) {
                Double v = values.get(i);
                if (v == null) continue;
                
                float x = MARGIN_LEFT + (n > 1 ? i * (float) chartW / (n - 1) : chartW / 2f);
                float y = MARGIN_TOP + chartH - (float) (v / 100.0 * chartH);
                
                if (!started) {
                    linePath.moveTo(x, y);
                    fillPath.moveTo(x, MARGIN_TOP + chartH);
                    fillPath.lineTo(x, y);
                    firstX = (int) x;
                    started = true;
                } else {
                    linePath.lineTo(x, y);
                    fillPath.lineTo(x, y);
                }
                lastX = (int) x;
            }
            
            if (started) {
                fillPath.lineTo(lastX, MARGIN_TOP + chartH);
                fillPath.lineTo(firstX, MARGIN_TOP + chartH);
                fillPath.closePath();
                
                // Fill gradient
                GradientPaint fillGradient = new GradientPaint(
                    0, MARGIN_TOP, new Color(ACCENT_BLUE.getRed(), ACCENT_BLUE.getGreen(), ACCENT_BLUE.getBlue(), 60),
                    0, MARGIN_TOP + chartH, new Color(ACCENT_BLUE.getRed(), ACCENT_BLUE.getGreen(), ACCENT_BLUE.getBlue(), 10)
                );
                g2.setPaint(fillGradient);
                g2.fill(fillPath);
                
                // Line
                g2.setColor(ACCENT_BLUE);
                g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(linePath);
                
                // Data points
                for (int i = 0; i < n; i++) {
                    Double v = values.get(i);
                    if (v == null) continue;
                    
                    float x = MARGIN_LEFT + (n > 1 ? i * (float) chartW / (n - 1) : chartW / 2f);
                    float y = MARGIN_TOP + chartH - (float) (v / 100.0 * chartH);
                    
                    boolean isHovered = hoverIndex != null && hoverIndex == i;
                    int radius = isHovered ? 6 : 4;
                    
                    // Point
                    g2.setColor(getMoodColor(v));
                    g2.fillOval((int) x - radius, (int) y - radius, radius * 2, radius * 2);
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawOval((int) x - radius, (int) y - radius, radius * 2, radius * 2);
                }
            }
            
            // X-axis date labels
            g2.setFont(new Font("SF Pro Text", Font.PLAIN, 10));
            g2.setColor(TEXT_SECONDARY);
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d");
            int labelStep = Math.max(1, n / 8);
            for (int i = 0; i < n; i += labelStep) {
                float x = MARGIN_LEFT + (n > 1 ? i * (float) chartW / (n - 1) : chartW / 2f);
                String label = fmt.format(days.get(i));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(label, x - fm.stringWidth(label) / 2f, h - MARGIN_BOTTOM + 20);
            }
            
            // Hover tooltip
            if (hoverIndex != null && hoverIndex >= 0 && hoverIndex < n) {
                Double v = values.get(hoverIndex);
                if (v != null) {
                    float x = MARGIN_LEFT + (n > 1 ? hoverIndex * (float) chartW / (n - 1) : chartW / 2f);
                    float y = MARGIN_TOP + chartH - (float) (v / 100.0 * chartH);
                    
                    // Vertical line
                    g2.setColor(new Color(0, 0, 0, 30));
                    g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 
                        10f, new float[]{4f, 4f}, 0f));
                    g2.drawLine((int) x, MARGIN_TOP, (int) x, MARGIN_TOP + chartH);
                    
                    // Tooltip
                    String dateStr = DateTimeFormatter.ofPattern("EEEE, MMM d").format(days.get(hoverIndex));
                    String moodStr = String.format("%.0f/100", v);
                    
                    g2.setFont(new Font("SF Pro Text", Font.BOLD, 12));
                    FontMetrics fm = g2.getFontMetrics();
                    int tw = Math.max(fm.stringWidth(dateStr), fm.stringWidth(moodStr)) + 20;
                    int th = 48;
                    
                    int tx = (int) Math.min(Math.max(x - tw / 2, 10), w - tw - 10);
                    int ty = (int) y - th - 15;
                    if (ty < 10) ty = (int) y + 15;
                    
                    // Tooltip background
                    g2.setColor(new Color(0, 0, 0, 180));
                    g2.fillRoundRect(tx, ty, tw, th, 10, 10);
                    
                    // Tooltip text
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("SF Pro Text", Font.PLAIN, 11));
                    g2.drawString(dateStr, tx + 10, ty + 18);
                    g2.setFont(new Font("SF Pro Text", Font.BOLD, 14));
                    g2.drawString(moodStr, tx + 10, ty + 36);
                }
            }
            
            g2.dispose();
        }
    }
    
    private JPanel createFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER));
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(8, 0, 16, 0));
        
        IconMenuButton backButton = new IconMenuButton("", "back");
        backButton.addActionListener(e -> app.switchCard(JournalApp.MAIN_MENU));
        footer.add(backButton);
        
        return footer;
    }
    
    private void loadData() {
        model.load(selectedRangeIndex);
        repaint();
    }
    
    private void openEntry(File f) {
        try {
            NotebookStore store = new NotebookStore();
            for (NotebookInfo nb : store.list()) {
                File folder = nb.getFolder();
                if (folder != null && f.getAbsolutePath().startsWith(folder.getAbsolutePath() + File.separator)) {
                    app.openExistingEntryEditor(nb, f);
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }
    
    // Helper methods
    private Color getMoodColor(double value) {
        if (value >= 66) return ACCENT_GREEN;
        if (value >= 33) return ACCENT_ORANGE;
        return ACCENT_RED;
    }
    
    private String getMoodIcon(double value) {
        if (value >= 66) return "+";
        if (value >= 33) return "~";
        return "-";
    }
    
    private String getTrendText() {
        double slope = model.getAnalytics() != null ? 
            calculateTrendSlope() : 0;
        if (Math.abs(slope) < 0.5) return "Stable";
        return slope > 0 ? "Improving" : "Declining";
    }
    
    private String getTrendIcon() {
        double slope = calculateTrendSlope();
        if (Math.abs(slope) < 0.5) return "—";
        return slope > 0 ? "/" : "\\";
    }
    
    private Color getTrendColor() {
        double slope = calculateTrendSlope();
        if (Math.abs(slope) < 0.5) return ACCENT_BLUE;
        return slope > 0 ? ACCENT_GREEN : ACCENT_ORANGE;
    }
    
    private double calculateTrendSlope() {
        List<Double> values = model.getValues();
        if (values.size() < 2) return 0;
        
        int n = 0;
        double sumX = 0, sumY = 0;
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) != null) {
                sumX += i;
                sumY += values.get(i);
                n++;
            }
        }
        if (n < 2) return 0;
        
        double meanX = sumX / n;
        double meanY = sumY / n;
        
        double num = 0, denom = 0;
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) != null) {
                double dx = i - meanX;
                double dy = values.get(i) - meanY;
                num += dx * dy;
                denom += dx * dx;
            }
        }
        
        return denom > 0 ? num / denom : 0;
    }
    
    private String getStreakText() {
        int streak = model.getCurrentStreak();
        if (streak > 0) return streak + " good";
        if (streak < 0) return (-streak) + " tough";
        return "None";
    }
    
    private Color getStreakColor() {
        int streak = model.getCurrentStreak();
        if (streak > 0) return ACCENT_GREEN;
        if (streak < 0) return ACCENT_ORANGE;
        return TEXT_SECONDARY;
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Full background gradient
        GradientPaint gp = new GradientPaint(0, 0, BG_GRADIENT_TOP,
            0, getHeight(), BG_GRADIENT_BOTTOM);
        g2.setPaint(gp);
        g2.fillRect(0, 0, getWidth(), getHeight());
        
        g2.dispose();
        
        super.paintComponent(g);
    }
}
