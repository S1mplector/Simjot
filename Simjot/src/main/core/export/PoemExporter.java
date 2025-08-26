package main.core.export;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

public final class PoemExporter {
    public enum Format { MARKDOWN, HTML, TXT, PNG }

    public static final class Options {
        public boolean includeTitle = true;
        public boolean includeMetadata = true;
        public boolean includeStats = false;
        public boolean lineNumbers = false; // for text-like exports
        public String htmlTheme = "light"; // light | dark
        // Optional: editor styling passthrough (if provided by caller)
        public String fontFamily = null;   // e.g., "Serif" or "Georgia"
        public Integer fontSizePx = null;  // e.g., 16
        public Float lineHeight = null;    // CSS line-height (e.g., 1.0, 1.2, 1.5)
    }

    private PoemExporter() {}

    public static File buildTargetFile(File journalFolder, File currentPoemFile, Format fmt) {
        String base;
        if (currentPoemFile != null) {
            base = currentPoemFile.getName().replaceFirst("\\.poem$", "");
        } else {
            base = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        }
        String ext = switch (fmt) {
            case MARKDOWN -> ".md";
            case HTML -> ".html";
            case TXT -> ".txt";
            case PNG -> ".png";
        };
        return new File(journalFolder, base + ext);
    }

    public static void exportTextual(File outFile, Format fmt, String title, String content, Options opt, Map<String, String> stats) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8)) {
            switch (fmt) {
                case MARKDOWN -> writeMarkdown(w, title, content, opt, stats);
                case HTML -> writeHtml(w, title, content, opt, stats);
                case TXT -> writeTxt(w, title, content, opt, stats);
                default -> throw new IllegalArgumentException("Unsupported text format: " + fmt);
            }
        }
    }

    public static void exportPng(File outFile, String title, String content, Options opt, JComponent toRender) throws IOException {
        // If a component is provided, render it; otherwise render a simple layout with title + text
        BufferedImage img;
        if (toRender != null) {
            int w = Math.max(600, toRender.getWidth());
            int h = Math.max(400, toRender.getHeight());
            img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, w, h);
            toRender.paint(g2);
            g2.dispose();
        } else {
            // Render text ourselves
            Font titleFont = new Font("Serif", Font.BOLD, 22);
            Font textFont = new Font("Serif", Font.PLAIN, 16);
            int width = 1000;
            int margin = 32;
            int lineHeight = 22;
            int y = margin + 10;
            // Approximate height
            int lines = 1 + content.split("\\R").length;
            int height = Math.max(400, margin * 2 + 50 + lines * lineHeight);
            img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, width, height);
            if (opt.includeTitle && title != null && !title.isBlank()) {
                g2.setColor(new Color(30,30,30));
                g2.setFont(titleFont);
                g2.drawString(title, margin, y);
                y += 36;
            }
            g2.setColor(new Color(40,40,40));
            g2.setFont(textFont);
            for (String line : content.split("\\R", -1)) {
                if (opt.lineNumbers) {
                    g2.drawString(String.format("%3d ", y/lineHeight), margin, y);
                    g2.drawString(line, margin + 40, y);
                } else {
                    g2.drawString(line, margin, y);
                }
                y += lineHeight;
            }
            g2.dispose();
        }
        javax.imageio.ImageIO.write(img, "png", outFile);
    }

    private static void writeMarkdown(Writer w, String title, String content, Options opt, Map<String, String> stats) throws IOException {
        if (opt.includeTitle && title != null && !title.isBlank()) {
            w.write("# " + title + "\n\n");
        }
        if (opt.includeMetadata) {
            w.write("_Exported: " + new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date()) + "_\n\n");
        }
        w.write(content);
        if (opt.includeStats && stats != null && !stats.isEmpty()) {
            w.write("\n\n---\n");
            w.write("Stats:\n");
            for (var e : stats.entrySet()) {
                w.write(String.format("- %s: %s\n", e.getKey(), e.getValue()));
            }
        }
    }

    private static void writeTxt(Writer w, String title, String content, Options opt, Map<String, String> stats) throws IOException {
        if (opt.includeTitle && title != null && !title.isBlank()) {
            w.write(title + "\n\n");
        }
        w.write(content);
        if (opt.includeStats && stats != null && !stats.isEmpty()) {
            w.write("\n\n---\n");
            for (var e : stats.entrySet()) {
                w.write(String.format("%s: %s\n", e.getKey(), e.getValue()));
            }
        }
    }

    private static void writeHtml(Writer w, String title, String content, Options opt, Map<String, String> stats) throws IOException {
        String bg = opt.htmlTheme != null && opt.htmlTheme.equals("dark") ? "#0f0f10" : "#ffffff";
        String fg = opt.htmlTheme != null && opt.htmlTheme.equals("dark") ? "#e9e9ea" : "#222222";
        // Derive CSS from options, with sensible fallbacks
        String fontFamily = (opt.fontFamily != null && !opt.fontFamily.isBlank()) ? opt.fontFamily : "Georgia";
        int fontSize = (opt.fontSizePx != null && opt.fontSizePx > 0) ? opt.fontSizePx : 16;
        String lineHeightCss = (opt.lineHeight != null && opt.lineHeight > 0f) ? String.valueOf(opt.lineHeight) : "normal";
        w.write("<!DOCTYPE html><html><head><meta charset='utf-8'>");
        w.write("<style>" +
                "body{background:"+bg+";color:"+fg+";margin:40px;" +
                "font:"+fontSize+"px '"+escapeCss(fontFamily)+"', serif;" +
                "}" +
                "h1{font-size:"+(int)Math.round(fontSize*1.75)+"px;margin:0 0 16px;}" +
                "pre{white-space:pre-wrap;line-height:"+lineHeightCss+";}" +
                "</style>");
        w.write("</head><body>\n");
        if (opt.includeTitle && title != null && !title.isBlank()) {
            w.write("<h1>" + escapeHtml(title) + "</h1>\n");
        }
        if (opt.includeMetadata) {
            w.write("<div style='opacity:.7;font-size:13px;margin-bottom:10px'>Exported " +
                    escapeHtml(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date())) + "</div>\n");
        }
        if (opt.lineNumbers) {
            StringBuilder numbered = new StringBuilder();
            String[] lines = content.split("\\R", -1);
            for (int i=0;i<lines.length;i++) {
                numbered.append(String.format("%3d  %s\n", i+1, lines[i]));
            }
            w.write("<pre>" + escapeHtml(numbered.toString()) + "</pre>\n");
        } else {
            w.write("<pre>" + escapeHtml(content) + "</pre>\n");
        }
        if (opt.includeStats && stats != null && !stats.isEmpty()) {
            w.write("<hr><ul>\n");
            for (var e : stats.entrySet()) {
                w.write("<li>" + escapeHtml(e.getKey()) + ": " + escapeHtml(e.getValue()) + "</li>\n");
            }
            w.write("</ul>\n");
        }
        w.write("</body></html>");
    }

    private static String escapeHtml(String s) {
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }

    // Very small helper for CSS string safety (not fully foolproof, but adequate for font family)
    private static String escapeCss(String s) {
        return s.replace("\\", "").replace("\"", "\"");
    }
}
