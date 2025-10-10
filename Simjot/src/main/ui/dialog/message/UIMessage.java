package main.ui.dialog.message;

import javax.swing.*;
import java.awt.*;

/**
 * Centralized, user-friendly message dialogs with guidance.
 * Use these instead of raw JOptionPane for consistent UX.
 */
public final class UIMessage {

    private UIMessage() {}

    public static void info(Component parent, String title, String message, String nextStep) {
        show(parent, title, message, nextStep, JOptionPane.INFORMATION_MESSAGE);
    }

    public static void warn(Component parent, String title, String message, String nextStep) {
        show(parent, title, message, nextStep, JOptionPane.WARNING_MESSAGE);
    }

    public static void error(Component parent, String title, String message, String nextStep) {
        show(parent, title, message, nextStep, JOptionPane.ERROR_MESSAGE);
    }

    public static void error(Component parent, String title, String message, String nextStep, Throwable t) {
        String details = (t == null || t.getMessage() == null) ? "" : "\n\nDetails: " + t.getMessage();
        show(parent, title, message + details, nextStep, JOptionPane.ERROR_MESSAGE);
    }

    private static void show(Component parent, String title, String message, String nextStep, int type) {
        String safeTitle = (title == null || title.isBlank()) ? "Notice" : title.trim();
        boolean isError = (type == JOptionPane.ERROR_MESSAGE);
        if (isError && (title == null || title.isBlank())) safeTitle = "Error";

        // Build rich HTML content
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:Sans-Serif; font-size:12px; width: 420px;'>");

        if (message != null && !message.isBlank()) {
            String msg = message.trim()
                .replace("\n\n", "<br><br>")
                .replace("\n", "<br>");
            html.append(msg);
        }

        if (nextStep != null && !nextStep.isBlank()) {
            html.append("<div style='margin-top:10px'><span style='text-decoration:underline;'>What you can do:</span>");
            html.append("<ul style='margin-top:6px; margin-left:18px;'>");
            for (String line : nextStep.split("\n")) {
                String item = line.trim();
                if (item.isEmpty()) continue;
                if (item.startsWith("-")) item = item.substring(1).trim();
                html.append("<li>").append(item).append("</li>");
            }
            html.append("</ul></div>");
        }

        html.append("</body></html>");

        String content = html.toString();

        // Route through our custom dialog for consistent styling
        try {
            CustomMessageDialog.display(parent, safeTitle, content, isError);
        } catch (Throwable ignored) {
            // Fallback to JOptionPane if custom dialog fails for any reason
            JOptionPane.showMessageDialog(
                    SwingUtilities.getWindowAncestor(parent) instanceof Component ? SwingUtilities.getWindowAncestor(parent) : parent,
                    content, safeTitle, type);
        }
    }
}
