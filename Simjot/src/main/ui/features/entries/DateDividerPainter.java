/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.entries;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import main.ui.theme.aero.AeroTheme;

public final class DateDividerPainter {
    static final int DEFAULT_HEIGHT = 64;
    private static final Color LINE_COLOR = new Color(60, 60, 60, 170);
    private static final DateTimeFormatter LABEL_FORMAT = DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy");

    private DateDividerPainter() {}

    public static Font resolveFont(float size) {
        String family = "Zapfino";
        Font f = new Font(family, Font.PLAIN, Math.round(size));
        if (!family.equalsIgnoreCase(f.getFamily())) {
            f = AeroTheme.defaultBoldFont(size);
        }
        return f;
    }

    static String formatDate(LocalDate date) {
        if (date == null) return "";
        return LABEL_FORMAT.format(date);
    }

    static BufferedImage renderImage(int width, int height, String label) {
        int w = Math.max(1, width);
        int h = Math.max(1, height);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
        g2.setFont(resolveFont(16f));
        g2.setColor(new Color(60, 60, 60));
        paint(g2, w, h, label, g2.getFont(), g2.getColor());
        g2.dispose();
        return img;
    }

    static void paint(Graphics2D g2, int w, int h, String label, Font font, Color textColor) {
        if (g2 == null || w <= 0 || h <= 0) return;
        g2.setFont(font == null ? resolveFont(16f) : font);
        g2.setColor(textColor == null ? new Color(60, 60, 60) : textColor);

        FontMetrics fm = g2.getFontMetrics(g2.getFont());
        int centerX = w / 2;
        int lineY = h / 2 + 8;
        int padX = 16;

        String safeLabel = label == null ? "" : label.trim();
        String text = elideText(safeLabel, fm, Math.max(0, w - padX * 2 - 120));
        int textW = text.isEmpty() ? 0 : fm.stringWidth(text);
        int innerGap = textW > 0 ? (textW / 2 + 14) : 16;
        int leftLineEnd = centerX - innerGap;
        int rightLineStart = centerX + innerGap;

        g2.setColor(LINE_COLOR);
        g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int lineStart = padX;
        int lineEnd = w - padX;
        if (leftLineEnd > lineStart + 4) {
            g2.drawLine(lineStart, lineY, leftLineEnd, lineY);
        }
        if (rightLineStart < lineEnd - 4) {
            g2.drawLine(rightLineStart, lineY, lineEnd, lineY);
        }

        int capW = 12;
        int capH = 4;
        if (leftLineEnd > lineStart + 4) {
            g2.fillRoundRect(lineStart - capW / 2, lineY - capH / 2, capW, capH, capH, capH);
        }
        if (rightLineStart < lineEnd - 4) {
            g2.fillRoundRect(lineEnd - capW / 2, lineY - capH / 2, capW, capH, capH, capH);
        }

        int diamond = 10;
        Path2D diamondShape = new Path2D.Float();
        diamondShape.moveTo(centerX, lineY - diamond / 2f);
        diamondShape.lineTo(centerX + diamond / 2f, lineY);
        diamondShape.lineTo(centerX, lineY + diamond / 2f);
        diamondShape.lineTo(centerX - diamond / 2f, lineY);
        diamondShape.closePath();
        g2.fill(diamondShape);

        int leafW = 8;
        int leafH = 3;
        int leafOffset = diamond / 2 + 8;
        g2.fillRoundRect(centerX - leafOffset - leafW / 2, lineY - leafH / 2, leafW, leafH, leafH, leafH);
        g2.fillRoundRect(centerX + leafOffset - leafW / 2, lineY - leafH / 2, leafW, leafH, leafH, leafH);

        if (!text.isEmpty()) {
            int textX = centerX - textW / 2;
            int textY = lineY - 10;
            if (textY < fm.getAscent()) textY = fm.getAscent();
            if (textY > h - fm.getDescent()) textY = h - fm.getDescent();
            g2.setColor(textColor == null ? new Color(60, 60, 60) : textColor);
            g2.setFont(g2.getFont());
            g2.drawString(text, textX, textY);
        }
    }

    private static String elideText(String input, FontMetrics fm, int maxWidth) {
        if (input == null || input.isEmpty()) return "";
        if (maxWidth <= 0) return "";
        if (fm.stringWidth(input) <= maxWidth) return input;
        String ellipsis = "...";
        int max = input.length();
        while (max > 0 && fm.stringWidth(input.substring(0, max) + ellipsis) > maxWidth) {
            max--;
        }
        if (max <= 0) return "";
        return input.substring(0, max).trim() + ellipsis;
    }
}
