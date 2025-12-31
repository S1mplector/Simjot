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

package main.ui.features.settings;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import main.infrastructure.ffi.NativeAccess;
import main.infrastructure.ffi.NativeLibrary;
import main.ui.theme.aero.AeroTheme;

class DebugSettingsPage extends JPanel implements SettingsPage {
    private static final long MB = 1024L * 1024L;
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    
    // Modern color palette
    private static final Color CARD_BG = new Color(250, 250, 252);
    private static final Color CARD_BORDER = new Color(230, 230, 235);
    private static final Color CPU_COLOR = new Color(59, 130, 246);      // Blue
    private static final Color RAM_COLOR = new Color(16, 185, 129);      // Green
    private static final Color SYS_RAM_COLOR = new Color(139, 92, 246);  // Purple
    private static final Color ACCENT_SUBTLE = new Color(100, 116, 139); // Slate
    private static final Color SUCCESS_COLOR = new Color(34, 197, 94);
    private static final Color WARNING_COLOR = new Color(245, 158, 11);
    private static final Color ERROR_COLOR = new Color(239, 68, 68);

    // Performance components
    private MetricCard cpuCard;
    private MetricCard ramCard;
    private MetricCard sysRamCard;
    private JLabel coreLabel;
    private JLabel cpuTimeLabel;
    private JLabel statusIndicator;
    
    // Binary health components
    private BinaryHealthCard appHealthCard;
    private BinaryHealthCard nativeHealthCard;
    
    private NativeAccess.PerfSnapshot lastSnapshot;
    private double lastCpuPercent;

    DebugSettingsPage() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(true);
        setBackground(Color.WHITE);
        setBorder(new EmptyBorder(0, 0, 20, 0));
        
        // Header
        JPanel header = SettingsUi.header("Debug", "Performance diagnostics and binary health checks");
        header.setAlignmentX(LEFT_ALIGNMENT);
        add(header);
        add(Box.createVerticalStrut(20));
        
        // Performance section
        add(buildPerformanceSection());
        add(Box.createVerticalStrut(24));
        
        // Binary Health section
        add(buildBinaryHealthSection());
        
        // Start refresh timer
        Timer timer = new Timer(1000, e -> refreshPerformance());
        timer.start();
    }

    @Override
    public JComponent getComponent() {
        return this;
    }

    @Override
    public void apply() {
        // No persisted settings for debug.
    }
    
    private JPanel buildPerformanceSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setOpaque(false);
        section.setAlignmentX(LEFT_ALIGNMENT);
        
        // Section header with status
        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setOpaque(false);
        headerRow.setAlignmentX(LEFT_ALIGNMENT);
        headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        
        JLabel sectionTitle = new JLabel("Performance");
        sectionTitle.setFont(AeroTheme.defaultFont().deriveFont(Font.BOLD, 14f));
        sectionTitle.setForeground(new Color(30, 41, 59));
        
        statusIndicator = new JLabel("● Connecting...");
        statusIndicator.setFont(AeroTheme.defaultFont().deriveFont(11f));
        statusIndicator.setForeground(WARNING_COLOR);
        
        headerRow.add(sectionTitle, BorderLayout.WEST);
        headerRow.add(statusIndicator, BorderLayout.EAST);
        section.add(headerRow);
        section.add(Box.createVerticalStrut(12));
        
        // Metric cards row
        JPanel cardsPanel = new JPanel(new GridLayout(1, 3, 12, 0));
        cardsPanel.setOpaque(false);
        cardsPanel.setAlignmentX(LEFT_ALIGNMENT);
        cardsPanel.setMaximumSize(new Dimension(560, 100));
        
        cpuCard = new MetricCard("CPU Usage", "--", CPU_COLOR);
        ramCard = new MetricCard("App Memory", "--", RAM_COLOR);
        sysRamCard = new MetricCard("System RAM", "--", SYS_RAM_COLOR);
        
        cardsPanel.add(cpuCard);
        cardsPanel.add(ramCard);
        cardsPanel.add(sysRamCard);
        section.add(cardsPanel);
        section.add(Box.createVerticalStrut(12));
        
        // Additional info row
        JPanel infoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 0));
        infoRow.setOpaque(false);
        infoRow.setAlignmentX(LEFT_ALIGNMENT);
        
        coreLabel = createInfoLabel("Cores", "--");
        cpuTimeLabel = createInfoLabel("CPU Time", "--");
        
        infoRow.add(coreLabel);
        infoRow.add(cpuTimeLabel);
        section.add(infoRow);
        
        return section;
    }
    
    private JLabel createInfoLabel(String title, String value) {
        JLabel label = new JLabel("<html><span style='color:#64748b;font-size:10px;'>" + title + "</span><br>" +
                "<span style='color:#1e293b;font-size:12px;font-weight:bold;'>" + value + "</span></html>");
        label.setFont(AeroTheme.defaultFont());
        return label;
    }
    
    private void updateInfoLabel(JLabel label, String title, String value) {
        label.setText("<html><span style='color:#64748b;font-size:10px;'>" + title + "</span><br>" +
                "<span style='color:#1e293b;font-size:12px;font-weight:bold;'>" + value + "</span></html>");
    }
    
    private JPanel buildBinaryHealthSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setOpaque(false);
        section.setAlignmentX(LEFT_ALIGNMENT);
        
        JLabel sectionTitle = new JLabel("Binary Health");
        sectionTitle.setFont(AeroTheme.defaultFont().deriveFont(Font.BOLD, 14f));
        sectionTitle.setForeground(new Color(30, 41, 59));
        sectionTitle.setAlignmentX(LEFT_ALIGNMENT);
        section.add(sectionTitle);
        
        JLabel subtitle = new JLabel("Verify integrity of critical application files");
        subtitle.setFont(AeroTheme.defaultFont().deriveFont(11f));
        subtitle.setForeground(ACCENT_SUBTLE);
        subtitle.setAlignmentX(LEFT_ALIGNMENT);
        section.add(subtitle);
        section.add(Box.createVerticalStrut(12));
        
        // Health cards
        JPanel cardsPanel = new JPanel(new GridLayout(1, 2, 12, 0));
        cardsPanel.setOpaque(false);
        cardsPanel.setAlignmentX(LEFT_ALIGNMENT);
        cardsPanel.setMaximumSize(new Dimension(560, 160));
        
        appHealthCard = new BinaryHealthCard("Application Binary", "JAR executable", e -> verifyAppBinary());
        nativeHealthCard = new BinaryHealthCard("Native Library", "Platform-specific library", e -> verifyNativeBinary());
        
        cardsPanel.add(appHealthCard);
        cardsPanel.add(nativeHealthCard);
        section.add(cardsPanel);
        
        return section;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // METRIC CARD COMPONENT
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static class MetricCard extends JPanel {
        private final JLabel valueLabel;
        private final JLabel subtitleLabel;
        private final Color accentColor;
        private double progress = 0;
        
        MetricCard(String title, String value, Color accent) {
            this.accentColor = accent;
            setLayout(new BorderLayout(0, 4));
            setOpaque(false);
            setBorder(new EmptyBorder(12, 14, 12, 14));
            setPreferredSize(new Dimension(160, 90));
            
            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(AeroTheme.defaultFont().deriveFont(11f));
            titleLabel.setForeground(ACCENT_SUBTLE);
            
            valueLabel = new JLabel(value);
            valueLabel.setFont(AeroTheme.defaultFont().deriveFont(Font.BOLD, 20f));
            valueLabel.setForeground(new Color(30, 41, 59));
            
            subtitleLabel = new JLabel(" ");
            subtitleLabel.setFont(AeroTheme.defaultFont().deriveFont(10f));
            subtitleLabel.setForeground(accent);
            
            JPanel textPanel = new JPanel();
            textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
            textPanel.setOpaque(false);
            textPanel.add(titleLabel);
            textPanel.add(Box.createVerticalStrut(2));
            textPanel.add(valueLabel);
            textPanel.add(Box.createVerticalStrut(2));
            textPanel.add(subtitleLabel);
            
            add(textPanel, BorderLayout.CENTER);
        }
        
        void setValue(String value) {
            valueLabel.setText(value);
        }
        
        void setSubtitle(String text) {
            subtitleLabel.setText(text);
        }
        
        void setProgress(double p) {
            this.progress = Math.max(0, Math.min(1, p));
            repaint();
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            int w = getWidth();
            int h = getHeight();
            int arc = 12;
            
            // Card background
            g2.setColor(CARD_BG);
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, arc, arc));
            
            // Border
            g2.setColor(CARD_BORDER);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, arc, arc));
            
            // Progress bar at bottom
            if (progress > 0) {
                int barHeight = 3;
                int barY = h - barHeight - 8;
                int barWidth = w - 28;
                int barX = 14;
                
                // Background track
                g2.setColor(new Color(226, 232, 240));
                g2.fillRoundRect(barX, barY, barWidth, barHeight, barHeight, barHeight);
                
                // Progress fill
                int fillWidth = (int) (barWidth * progress);
                if (fillWidth > 0) {
                    g2.setColor(accentColor);
                    g2.fillRoundRect(barX, barY, fillWidth, barHeight, barHeight, barHeight);
                }
            }
            
            // Left accent line
            g2.setColor(accentColor);
            g2.fillRoundRect(0, 12, 3, h - 24, 2, 2);
            
            g2.dispose();
            super.paintComponent(g);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // BINARY HEALTH CARD COMPONENT
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static class BinaryHealthCard extends JPanel {
        private static final int STATUS_UNCHECKED = 0;
        private static final int STATUS_CHECKING = 1;
        private static final int STATUS_OK = 2;
        private static final int STATUS_ERROR = 3;
        
        private int status = STATUS_UNCHECKED;
        private final JLabel statusLabel;
        private final JLabel detailsLabel;
        private final JButton verifyButton;
        
        BinaryHealthCard(String title, String subtitle, java.awt.event.ActionListener onVerify) {
            setLayout(new BorderLayout(0, 8));
            setOpaque(false);
            setBorder(new EmptyBorder(14, 16, 14, 16));
            setPreferredSize(new Dimension(260, 150));
            
            // Top section with title
            JPanel topPanel = new JPanel(new BorderLayout());
            topPanel.setOpaque(false);
            
            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(AeroTheme.defaultFont().deriveFont(Font.BOLD, 13f));
            titleLabel.setForeground(new Color(30, 41, 59));
            
            JLabel subtitleLabel = new JLabel(subtitle);
            subtitleLabel.setFont(AeroTheme.defaultFont().deriveFont(10f));
            subtitleLabel.setForeground(ACCENT_SUBTLE);
            
            JPanel titlePanel = new JPanel();
            titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
            titlePanel.setOpaque(false);
            titlePanel.add(titleLabel);
            titlePanel.add(subtitleLabel);
            topPanel.add(titlePanel, BorderLayout.WEST);
            
            // Status indicator
            statusLabel = new JLabel("Not verified");
            statusLabel.setFont(AeroTheme.defaultFont().deriveFont(10f));
            statusLabel.setForeground(ACCENT_SUBTLE);
            topPanel.add(statusLabel, BorderLayout.EAST);
            
            add(topPanel, BorderLayout.NORTH);
            
            // Details area
            detailsLabel = new JLabel("<html><span style='color:#94a3b8;'>Click verify to check integrity</span></html>");
            detailsLabel.setFont(AeroTheme.defaultFont().deriveFont(10f));
            detailsLabel.setVerticalAlignment(JLabel.TOP);
            add(detailsLabel, BorderLayout.CENTER);
            
            // Button
            verifyButton = new JButton("Verify");
            verifyButton.setFont(AeroTheme.defaultFont().deriveFont(11f));
            verifyButton.setFocusPainted(false);
            verifyButton.addActionListener(e -> {
                setStatus(STATUS_CHECKING);
                onVerify.actionPerformed(e);
            });
            
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            buttonPanel.setOpaque(false);
            buttonPanel.add(verifyButton);
            add(buttonPanel, BorderLayout.SOUTH);
        }
        
        void setStatus(int newStatus) {
            this.status = newStatus;
            switch (status) {
                case STATUS_UNCHECKED:
                    statusLabel.setText("Not verified");
                    statusLabel.setForeground(ACCENT_SUBTLE);
                    break;
                case STATUS_CHECKING:
                    statusLabel.setText("Checking...");
                    statusLabel.setForeground(WARNING_COLOR);
                    detailsLabel.setText("<html><span style='color:#94a3b8;'>Verifying file integrity...</span></html>");
                    break;
                case STATUS_OK:
                    statusLabel.setText("● Verified");
                    statusLabel.setForeground(SUCCESS_COLOR);
                    break;
                case STATUS_ERROR:
                    statusLabel.setText("● Error");
                    statusLabel.setForeground(ERROR_COLOR);
                    break;
            }
            repaint();
        }
        
        void setDetails(String html) {
            detailsLabel.setText(html);
        }
        
        void setOk(String path, String size, String modified, String hash) {
            setStatus(STATUS_OK);
            String shortPath = path.length() > 35 ? "..." + path.substring(path.length() - 32) : path;
            String shortHash = hash.length() > 16 ? hash.substring(0, 16) + "..." : hash;
            setDetails("<html><div style='font-size:9px;color:#64748b;line-height:1.4;'>" +
                    "<b>Path:</b> " + shortPath + "<br>" +
                    "<b>Size:</b> " + size + "<br>" +
                    "<b>Modified:</b> " + modified + "<br>" +
                    "<b>SHA-256:</b> " + shortHash +
                    "</div></html>");
        }
        
        void setError(String message) {
            setStatus(STATUS_ERROR);
            setDetails("<html><span style='color:#ef4444;font-size:10px;'>" + message + "</span></html>");
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            int w = getWidth();
            int h = getHeight();
            int arc = 12;
            
            // Card background
            g2.setColor(CARD_BG);
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, arc, arc));
            
            // Border color based on status
            Color borderColor = CARD_BORDER;
            if (status == STATUS_OK) borderColor = new Color(187, 247, 208);
            else if (status == STATUS_ERROR) borderColor = new Color(254, 202, 202);
            
            g2.setColor(borderColor);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, arc, arc));
            
            // Status accent line at top
            if (status == STATUS_OK) {
                g2.setColor(SUCCESS_COLOR);
                g2.fillRoundRect(12, 0, w - 24, 3, 2, 2);
            } else if (status == STATUS_ERROR) {
                g2.setColor(ERROR_COLOR);
                g2.fillRoundRect(12, 0, w - 24, 3, 2, 2);
            }
            
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private void refreshPerformance() {
        NativeAccess.PerfSnapshot snap = NativeAccess.perfSnapshot();
        if (snap == null) {
            statusIndicator.setText("● Unavailable");
            statusIndicator.setForeground(ERROR_COLOR);
            cpuCard.setValue("--");
            cpuCard.setSubtitle("No data");
            cpuCard.setProgress(0);
            ramCard.setValue("--");
            ramCard.setSubtitle("No data");
            ramCard.setProgress(0);
            sysRamCard.setValue("--");
            sysRamCard.setSubtitle("No data");
            sysRamCard.setProgress(0);
            updateInfoLabel(coreLabel, "Cores", "--");
            updateInfoLabel(cpuTimeLabel, "CPU Time", "--");
            return;
        }

        statusIndicator.setText("● Live");
        statusIndicator.setForeground(SUCCESS_COLOR);
        
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

        // Update CPU card
        cpuCard.setValue(String.format(Locale.ROOT, "%.1f%%", cpuPercent));
        cpuCard.setSubtitle(cpuPercent < 30 ? "Normal" : cpuPercent < 70 ? "Moderate" : "High");
        cpuCard.setProgress(cpuPercent / 100.0);
        
        // Update RAM card
        long rss = snap.rssBytes;
        ramCard.setValue(formatBytes(rss));
        ramCard.setSubtitle(rss < 500 * MB ? "Normal" : rss < 1000 * MB ? "Moderate" : "High");
        ramCard.setProgress(Math.min(1.0, rss / (2000.0 * MB))); // Scale to 2GB max
        
        // Update System RAM card
        long sysTotal = snap.sysTotalBytes;
        long sysAvail = snap.sysAvailBytes;
        long sysUsed = (sysTotal > 0 && sysAvail > 0) ? Math.max(0, sysTotal - sysAvail) : 0;
        if (sysTotal > 0) {
            double usedPercent = (sysUsed / (double) sysTotal) * 100;
            sysRamCard.setValue(String.format(Locale.ROOT, "%.0f%%", usedPercent));
            sysRamCard.setSubtitle(formatBytes(sysUsed) + " / " + formatBytes(sysTotal));
            sysRamCard.setProgress(usedPercent / 100.0);
        } else {
            sysRamCard.setValue("--");
            sysRamCard.setSubtitle("No data");
            sysRamCard.setProgress(0);
        }
        
        // Update info labels
        updateInfoLabel(coreLabel, "Cores", String.valueOf(Math.max(1, snap.cpuCount)));
        updateInfoLabel(cpuTimeLabel, "CPU Time", formatSeconds((snap.cpuUserNs + snap.cpuSystemNs) / 1_000_000_000.0));
    }

    private void verifyAppBinary() {
        Path path = resolveAppBinaryPath();
        updateBinaryHealth(appHealthCard, path);
    }

    private void verifyNativeBinary() {
        Path path = NativeLibrary.defaultLibraryPath();
        updateBinaryHealth(nativeHealthCard, path);
    }

    private void updateBinaryHealth(BinaryHealthCard card, Path path) {
        if (path == null) {
            card.setError("Path not available");
            return;
        }
        if (!Files.exists(path)) {
            card.setError("File not found: " + path.getFileName());
            return;
        }
        NativeAccess.BinaryHealth health = NativeAccess.binaryHealth(path);
        if (health == null) {
            card.setError("Health check unavailable");
            return;
        }
        String modified = health.modifiedEpochSeconds > 0
                ? TIME_FMT.format(Instant.ofEpochSecond(health.modifiedEpochSeconds))
                : "unknown";
        card.setOk(path.toString(), formatBytes(health.sizeBytes), modified, health.sha256Hex);
    }

    private static Path resolveAppBinaryPath() {
        try {
            URL url = DebugSettingsPage.class.getProtectionDomain().getCodeSource().getLocation();
            if (url == null) return null;
            Path path = Paths.get(url.toURI());
            if (Files.isDirectory(path)) {
                Path target = Paths.get(System.getProperty("user.dir"), "target");
                if (Files.isDirectory(target)) {
                    Path newest = null;
                    long newestTime = 0;
                    try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(target, "*simjot*.jar")) {
                        for (Path jar : stream) {
                            long mtime = Files.getLastModifiedTime(jar).toMillis();
                            if (newest == null || mtime > newestTime) {
                                newest = jar;
                                newestTime = mtime;
                            }
                        }
                    } catch (Throwable ignored) {}
                    if (newest != null) return newest;
                }
                return null;
            }
            return path;
        } catch (Throwable ignored) {}
        return null;
    }

    private static String formatBytes(long bytes) {
        if (bytes <= 0) return "0 MB";
        double mb = bytes / (double) MB;
        if (mb >= 1024) {
            return String.format(Locale.ROOT, "%.1f GB", mb / 1024.0);
        }
        return String.format(Locale.ROOT, "%.0f MB", mb);
    }

    private static String formatSeconds(double seconds) {
        if (seconds < 60) {
            return String.format(Locale.ROOT, "%.1f s", seconds);
        }
        double minutes = seconds / 60.0;
        if (minutes < 60) {
            return String.format(Locale.ROOT, "%.1f min", minutes);
        }
        double hours = minutes / 60.0;
        return String.format(Locale.ROOT, "%.1f h", hours);
    }
}
