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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.Timer;

import main.infrastructure.ffi.NativeAccess;
import main.infrastructure.ffi.NativeLibrary;
import main.ui.theme.aero.AeroTheme;

class DebugSettingsPage extends JPanel implements SettingsPage {
    private static final long MB = 1024L * 1024L;
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final JLabel perfStatus = SettingsUi.label("Native metrics unavailable");
    private final JLabel cpuValue = SettingsUi.label("--");
    private final JLabel cpuTimeValue = SettingsUi.label("--");
    private final JLabel ramValue = SettingsUi.label("--");
    private final JLabel sysRamValue = SettingsUi.label("--");
    private final JLabel coreValue = SettingsUi.label("--");

    private final JTextArea appHealthArea = new JTextArea();
    private final JTextArea nativeHealthArea = new JTextArea();
    private NativeAccess.PerfSnapshot lastSnapshot;
    private double lastCpuPercent;

    DebugSettingsPage() {
        setLayout(new GridBagLayout());
        setOpaque(true);
        setBackground(Color.WHITE);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5, 5, 5, 5);
        gc.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;

        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2;
        add(SettingsUi.header("Debug", "Performance diagnostics and binary health checks"), gc);
        row++;

        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2;
        gc.insets = new Insets(16, 5, 5, 5);
        add(SettingsUi.header("Performance", "Live CPU and memory telemetry"), gc);
        row++;

        gc.insets = new Insets(5, 5, 5, 5);
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2;
        add(buildPerformancePanel(), gc);
        row++;

        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2;
        gc.insets = new Insets(16, 5, 5, 5);
        add(SettingsUi.header("Binary Health", "Verify the integrity of critical binaries"), gc);
        row++;

        gc.insets = new Insets(5, 5, 5, 5);
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2;
        add(buildBinaryHealthPanel(), gc);

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

    private JPanel buildPerformancePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(3, 4, 3, 4);
        gc.anchor = GridBagConstraints.WEST;

        gc.gridx = 0; gc.gridy = 0; panel.add(SettingsUi.label("Status:"), gc);
        gc.gridx = 1; panel.add(perfStatus, gc);

        gc.gridx = 0; gc.gridy = 1; panel.add(SettingsUi.label("CPU usage:"), gc);
        gc.gridx = 1; panel.add(cpuValue, gc);

        gc.gridx = 0; gc.gridy = 2; panel.add(SettingsUi.label("CPU time:"), gc);
        gc.gridx = 1; panel.add(cpuTimeValue, gc);

        gc.gridx = 0; gc.gridy = 3; panel.add(SettingsUi.label("CPU cores:"), gc);
        gc.gridx = 1; panel.add(coreValue, gc);

        gc.gridx = 0; gc.gridy = 4; panel.add(SettingsUi.label("App RAM (RSS):"), gc);
        gc.gridx = 1; panel.add(ramValue, gc);

        gc.gridx = 0; gc.gridy = 5; panel.add(SettingsUi.label("System RAM:"), gc);
        gc.gridx = 1; panel.add(sysRamValue, gc);

        return panel;
    }

    private JPanel buildBinaryHealthPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;

        JButton checkApp = new JButton("Verify app binary");
        JButton checkNative = new JButton("Verify native library");
        checkApp.addActionListener(e -> verifyAppBinary());
        checkNative.addActionListener(e -> verifyNativeBinary());

        appHealthArea.setEditable(false);
        appHealthArea.setLineWrap(true);
        appHealthArea.setWrapStyleWord(true);
        appHealthArea.setOpaque(false);
        appHealthArea.setFont(AeroTheme.defaultFont().deriveFont(11f));
        appHealthArea.setText("Not checked yet.");

        nativeHealthArea.setEditable(false);
        nativeHealthArea.setLineWrap(true);
        nativeHealthArea.setWrapStyleWord(true);
        nativeHealthArea.setOpaque(false);
        nativeHealthArea.setFont(AeroTheme.defaultFont().deriveFont(11f));
        nativeHealthArea.setText("Not checked yet.");

        gc.gridx = 0; gc.gridy = 0; gc.gridwidth = 1;
        panel.add(checkApp, gc);
        gc.gridx = 1; panel.add(checkNative, gc);

        gc.gridx = 0; gc.gridy = 1; gc.gridwidth = 2;
        panel.add(appHealthArea, gc);

        gc.gridy = 2;
        panel.add(nativeHealthArea, gc);

        panel.setPreferredSize(new Dimension(520, 150));
        return panel;
    }

    private void refreshPerformance() {
        NativeAccess.PerfSnapshot snap = NativeAccess.perfSnapshot();
        if (snap == null) {
            perfStatus.setText("Native metrics unavailable");
            cpuValue.setText("--");
            cpuTimeValue.setText("--");
            coreValue.setText("--");
            ramValue.setText("--");
            sysRamValue.setText("--");
            return;
        }

        perfStatus.setText("Live native metrics");
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

        cpuValue.setText(String.format(Locale.ROOT, "%.1f%%", cpuPercent));
        cpuTimeValue.setText(formatSeconds((snap.cpuUserNs + snap.cpuSystemNs) / 1_000_000_000.0));
        coreValue.setText(String.valueOf(Math.max(1, snap.cpuCount)));
        ramValue.setText(formatBytes(snap.rssBytes));

        long sysTotal = snap.sysTotalBytes;
        long sysAvail = snap.sysAvailBytes;
        long sysUsed = (sysTotal > 0 && sysAvail > 0) ? Math.max(0, sysTotal - sysAvail) : 0;
        if (sysTotal > 0) {
            sysRamValue.setText(formatBytes(sysUsed) + " / " + formatBytes(sysTotal));
        } else {
            sysRamValue.setText("--");
        }
    }

    private void verifyAppBinary() {
        Path path = resolveAppBinaryPath();
        updateBinaryHealth(appHealthArea, "App binary", path);
    }

    private void verifyNativeBinary() {
        Path path = NativeLibrary.defaultLibraryPath();
        updateBinaryHealth(nativeHealthArea, "Native library", path);
    }

    private void updateBinaryHealth(JTextArea area, String label, Path path) {
        if (path == null) {
            area.setText(label + ": path not available.");
            return;
        }
        if (!Files.exists(path)) {
            area.setText(label + ": file not found at " + path);
            return;
        }
        NativeAccess.BinaryHealth health = NativeAccess.binaryHealth(path);
        if (health == null) {
            area.setText(label + ": native health check unavailable.");
            return;
        }
        String modified = health.modifiedEpochSeconds > 0
                ? TIME_FMT.format(Instant.ofEpochSecond(health.modifiedEpochSeconds))
                : "unknown";
        area.setText(label + " OK\n"
                + "Path: " + path + "\n"
                + "Size: " + formatBytes(health.sizeBytes) + "\n"
                + "Modified: " + modified + "\n"
                + "SHA-256: " + health.sha256Hex);
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
