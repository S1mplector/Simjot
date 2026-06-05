/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.components.icons;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * ModernFileIcons provides a set of simple, modern file and folder icons
 * for use in a Java Swing application.
 * These icons are designed to be lightweight and visually appealing,
 * ideal for modern user interfaces.
 * Usage:
 * <pre>
 * Image folderIcon = ModernFileIcons.createFolderIcon();
 * Image fileIcon = ModernFileIcons.createFileIcon();
 * Image upIcon = ModernFileIcons.createUpIcon();
 * Image homeIcon = ModernFileIcons.createHomeIcon();
 * Image newFolderIcon = ModernFileIcons.createNewFolderIcon();
 * </pre>
 * Each method returns an Image object representing the icon.
 * The icons are created using Java's Graphics2D API,
 * ensuring they are rendered with anti-aliasing for smooth edges.
 */
public class ModernFileIcons {
    
    /**
     * Creates a modern trash can icon (32x32) using Java2D.
     */
    public static Image createTrashIcon() {
        int w = 32, h = 32;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Trash can body
        g2.setColor(new Color(200, 210, 220));
        g2.fillRoundRect(8, 10, 16, 16, 6, 6);
        g2.setColor(new Color(140, 150, 160));
        g2.setStroke(new BasicStroke(2f));
        g2.drawRoundRect(8, 10, 16, 16, 6, 6);

        // Lid
        g2.setColor(new Color(180, 190, 200));
        g2.fillRoundRect(7, 7, 18, 6, 4, 4);
        g2.setColor(new Color(120, 130, 140));
        g2.drawRoundRect(7, 7, 18, 6, 4, 4);

        // Handle
        g2.setColor(new Color(120, 130, 140));
        g2.setStroke(new BasicStroke(2f));
        g2.drawLine(14, 7, 18, 7);

        // Vertical lines (can ribs)
        g2.setColor(new Color(170, 180, 190));
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawLine(12, 13, 12, 23);
        g2.drawLine(16, 13, 16, 23);
        g2.drawLine(20, 13, 20, 23);

        g2.dispose();
        return img;
    }

    public static Image createFolderIcon() {
        int w = 20, h = 16;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(255, 204, 77));
        g.fillRoundRect(2, 5, 16, 9, 3, 3);
        g.setColor(new Color(255, 221, 120));
        g.fillRoundRect(2, 3, 10, 5, 2, 2);
        g.dispose();
        return img;
    }

    public static Image createFileIcon() {
        int w = 16, h = 18;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(220, 220, 220));
        g.fillRoundRect(2, 2, 12, 14, 3, 3);
        g.setColor(new Color(200, 200, 200));
        g.drawRoundRect(2, 2, 12, 14, 3, 3);
        g.setColor(new Color(150, 150, 150));
        g.drawLine(5, 6, 11, 6);
        g.drawLine(5, 8, 11, 8);
        g.drawLine(5, 10, 11, 10);
        g.dispose();
        return img;
    }

    public static Image createUpIcon() {
        int w = 16, h = 16;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(120, 180, 255));
        int[] x = {8, 4, 12};
        int[] y = {4, 12, 12};
        g.fillPolygon(x, y, 3);
        g.dispose();
        return img;
    }

    public static Image createHomeIcon() {
        int w = 16, h = 16;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(100, 150, 100));
        int[] x = {8, 2, 14};
        int[] y = {2, 8, 8};
        g.fillPolygon(x, y, 3);
        g.setColor(new Color(180, 150, 100));
        g.fillRect(5, 8, 6, 6);
        g.setColor(new Color(120, 80, 40));
        g.fillRect(7, 11, 2, 3);
        g.dispose();
        return img;
    }

    public static Image createNewFolderIcon() {
        int w = 20, h = 16;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(255, 204, 77));
        g.fillRoundRect(2, 5, 16, 9, 3, 3);
        g.setColor(new Color(255, 221, 120));
        g.fillRoundRect(2, 3, 10, 5, 2, 2);
        g.setColor(new Color(0, 150, 0));
        g.setStroke(new BasicStroke(2f));
        g.drawLine(12, 8, 16, 8);
        g.drawLine(14, 6, 14, 10);
        g.dispose();
        return img;
    }
}
