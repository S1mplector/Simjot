/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.infrastructure.io;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.SwingUtilities;

import main.ui.dialog.message.CustomMessageDialog;

public final class CrashReporter {
    private CrashReporter() {}

    private static final AtomicBoolean installed = new AtomicBoolean(false);
    private static final AtomicBoolean dialogShown = new AtomicBoolean(false);
    private static final long MAX_LOG_BYTES = 1024L * 1024L; // 1 MiB

    public static void install() {
        if (!installed.compareAndSet(false, true)) return;

        try {
            Thread.setDefaultUncaughtExceptionHandler((t, e) -> report("uncaught", t, e));
        } catch (Throwable ignored) {}

        try {
            EventQueue q = Toolkit.getDefaultToolkit().getSystemEventQueue();
            q.push(new EventQueue() {
                @Override
                protected void dispatchEvent(AWTEvent event) {
                    try {
                        super.dispatchEvent(event);
                    } catch (Throwable t) {
                        report("edt", Thread.currentThread(), t);
                    }
                }
            });
        } catch (Throwable ignored) {}
    }

    public static void report(String context, Thread thread, Throwable t) {
        if (t == null) return;

        try {
            appendToCrashLog(context, thread, t);
        } catch (Throwable ignored) {}

        try {
            if (dialogShown.compareAndSet(false, true)) {
                SwingUtilities.invokeLater(() -> {
                    try {
                        String path = "";
                        try {
                            path = getCrashLogFile().getAbsolutePath();
                        } catch (Throwable ignored) {}
                        String msg = "Simjot encountered an unexpected error and recovered.\\n\\n" +
                                "A crash log was written to:\\n" + path + "\\n\\n" +
                                "If this keeps happening, please restart Simjot.";
                        CustomMessageDialog.display(null, "Unexpected Error", msg.replace("\n", "<br>"), true);
                    } catch (Throwable ignored) {
                    } finally {
                        dialogShown.set(false);
                    }
                });
            }
        } catch (Throwable ignored) {}
    }

    private static synchronized void appendToCrashLog(String context, Thread thread, Throwable t) throws Exception {
        File f = getCrashLogFile();
        if (f == null) return;

        try {
            File parent = f.getParentFile();
            if (parent != null) Files.createDirectories(parent.toPath());
        } catch (Throwable ignored) {}

        try {
            if (f.exists() && f.length() >= MAX_LOG_BYTES) {
                File rotated = new File(f.getParentFile(), "crash_" + System.currentTimeMillis() + ".log");
                try {
                    Files.move(f.toPath(), rotated.toPath());
                } catch (Throwable ignored) {
                    // best-effort
                }
            }
        } catch (Throwable ignored) {}

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("---");
        pw.println("time=" + Instant.now());
        pw.println("context=" + (context == null ? "" : context));
        pw.println("thread=" + (thread == null ? "" : thread.getName()));
        t.printStackTrace(pw);
        pw.flush();

        byte[] bytes = sw.toString().getBytes(StandardCharsets.UTF_8);
        try {
            FileIO.ensureSpace(f.toPath(), bytes.length + 4096L, "crash log");
        } catch (Throwable ignored) {}

        Files.write(f.toPath(), bytes, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
    }

    private static File getCrashLogFile() {
        try {
            return new File(AppDirectories.folder(AppDirectories.Type.SETTINGS), "crash.log");
        } catch (Throwable ignored) {
            return new File(System.getProperty("user.home"), "simjot_crash.log");
        }
    }
}
