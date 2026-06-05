/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.infrastructure.startup;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import main.infrastructure.io.IoLog;

/**
 * Manage macOS LaunchAgent for starting Simjot at login.
 */
public final class MacLoginItem {
    private static final String LABEL = "com.s1mplector.simjot.launchagent";
    private static final String BUNDLE_ID = "com.s1mplector.simjot";
    private static final String START_IN_TRAY_ARG = "--start-in-tray";

    private MacLoginItem() {}

    public static boolean isSupported() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase().contains("mac");
    }

    public static void sync(boolean enabled, boolean startInTray) {
        if (!isSupported()) return;
        if (enabled) {
            enable(startInTray);
        } else {
            disable();
        }
    }

    public static boolean enable(boolean startInTray) {
        if (!isSupported()) return false;
        Path plist = launchAgentPath();
        try {
            Files.createDirectories(plist.getParent());
        } catch (IOException e) {
            IoLog.warn("startup", "Failed to create LaunchAgents dir: " + e.getMessage(), e);
            return false;
        }

        String content = buildPlist(startInTray);
        if (!writeIfChanged(plist, content)) {
            IoLog.warn("startup", "Failed to write LaunchAgent plist", null);
            return false;
        }

        boolean loaded = bootstrap(plist);
        IoLog.info("startup", "LaunchAgent enabled: " + loaded);
        return loaded;
    }

    public static boolean disable() {
        if (!isSupported()) return false;
        Path plist = launchAgentPath();
        bootout(plist);
        try {
            Files.deleteIfExists(plist);
        } catch (IOException e) {
            IoLog.warn("startup", "Failed to remove LaunchAgent plist: " + e.getMessage(), e);
            return false;
        }
        IoLog.info("startup", "LaunchAgent disabled");
        return true;
    }

    private static Path launchAgentPath() {
        String home = System.getProperty("user.home", "");
        return Paths.get(home, "Library", "LaunchAgents", LABEL + ".plist");
    }

    private static boolean writeIfChanged(Path path, String content) {
        try {
            if (Files.exists(path)) {
                String current = Files.readString(path, StandardCharsets.UTF_8);
                if (current.equals(content)) {
                    return true;
                }
            }
            Files.writeString(path, content, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            IoLog.warn("startup", "LaunchAgent write failed: " + e.getMessage(), e);
            return false;
        }
    }

    private static String buildPlist(boolean startInTray) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" ");
        sb.append("\"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n");
        sb.append("<plist version=\"1.0\">\n");
        sb.append("<dict>\n");
        sb.append("  <key>Label</key>\n");
        sb.append("  <string>").append(LABEL).append("</string>\n");
        sb.append("  <key>ProgramArguments</key>\n");
        sb.append("  <array>\n");
        sb.append("    <string>/usr/bin/open</string>\n");
        sb.append("    <string>-g</string>\n");
        sb.append("    <string>-b</string>\n");
        sb.append("    <string>").append(BUNDLE_ID).append("</string>\n");
        if (startInTray) {
            sb.append("    <string>--args</string>\n");
            sb.append("    <string>").append(START_IN_TRAY_ARG).append("</string>\n");
        }
        sb.append("  </array>\n");
        sb.append("  <key>RunAtLoad</key>\n");
        sb.append("  <true/>\n");
        sb.append("  <key>KeepAlive</key>\n");
        sb.append("  <false/>\n");
        sb.append("  <key>LimitLoadToSessionType</key>\n");
        sb.append("  <string>Aqua</string>\n");
        sb.append("</dict>\n");
        sb.append("</plist>\n");
        return sb.toString();
    }

    private static boolean bootstrap(Path plist) {
        int uid = currentUid();
        if (uid <= 0) return false;
        String target = "gui/" + uid;
        int exit = runCommand("launchctl", "bootstrap", target, plist.toString());
        if (exit != 0) {
            // Fallback for older macOS versions.
            exit = runCommand("launchctl", "load", "-w", plist.toString());
        }
        if (exit == 0) {
            runCommand("launchctl", "enable", target + "/" + LABEL);
        }
        return exit == 0;
    }

    private static void bootout(Path plist) {
        int uid = currentUid();
        if (uid <= 0) return;
        String target = "gui/" + uid;
        int exit = runCommand("launchctl", "bootout", target, plist.toString());
        if (exit != 0) {
            runCommand("launchctl", "unload", "-w", plist.toString());
        }
    }

    private static int currentUid() {
        String out = runCommandCapture("id", "-u");
        if (out == null) return -1;
        try {
            return Integer.parseInt(out.trim());
        } catch (NumberFormatException e) {
            IoLog.warn("startup", "Failed to parse uid: " + out, e);
            return -1;
        }
    }

    private static int runCommand(String... cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (InputStream in = p.getInputStream()) {
                // Drain stream to avoid blocking.
                in.transferTo(OutputStream.nullOutputStream());
            }
            return p.waitFor();
        } catch (Throwable t) {
            IoLog.warn("startup", "Command failed: " + String.join(" ", cmd), t);
            return -1;
        }
    }

    private static String runCommandCapture(String... cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (InputStream in = p.getInputStream()) {
                in.transferTo(baos);
            }
            int exit = p.waitFor();
            if (exit != 0) {
                IoLog.warn("startup", "Command failed: " + String.join(" ", cmd), null);
                return null;
            }
            return baos.toString(StandardCharsets.UTF_8);
        } catch (Throwable t) {
            IoLog.warn("startup", "Command failed: " + String.join(" ", cmd), t);
            return null;
        }
    }
}
