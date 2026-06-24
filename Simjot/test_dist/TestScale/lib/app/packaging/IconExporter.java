import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

/**
 * Standalone utility to export Simjot app icons to PNG files.
 * These can then be converted to .icns for macOS.
 * 
 * Usage: java IconExporter.java [output_directory] [color]
 * 
 * Color can be:
 *   - A preset name: blue (default), green, purple, red, orange, teal, pink, gold
 *   - A hex color: #FF5500 or FF5500
 */
public class IconExporter {
    
    // Preset color schemes (top color, bottom color)
    private static final int[][] PRESETS = {
        {0x5AA0E6, 0x1F5FA9},  // blue (default)
        {0x5AE68A, 0x1FA94F},  // green
        {0xA05AE6, 0x5F1FA9},  // purple
        {0xE65A5A, 0xA91F1F},  // red
        {0xE6A05A, 0xA95F1F},  // orange
        {0x5AE6E6, 0x1FA9A9},  // teal
        {0xE65AA0, 0xA91F5F},  // pink
        {0xE6C85A, 0xA9881F},  // gold
    };
    private static final String[] PRESET_NAMES = {"blue", "green", "purple", "red", "orange", "teal", "pink", "gold"};
    
    private static int topColor = PRESETS[0][0];
    private static int bottomColor = PRESETS[0][1];
    
    public static void main(String[] args) throws Exception {
        String outputDir = args.length > 0 ? args[0] : ".";
        String colorArg = args.length > 1 ? args[1].toLowerCase() : "blue";
        
        // Parse color argument
        parseColor(colorArg);
        
        File dir = new File(outputDir);
        dir.mkdirs();
        
        // macOS .icns requires these specific sizes
        int[] sizes = {16, 32, 64, 128, 256, 512, 1024};
        
        System.out.println("Exporting Simjot icons to: " + dir.getAbsolutePath());
        
        for (int size : sizes) {
            BufferedImage img = renderIcon(size);
            File outFile = new File(dir, "icon_" + size + "x" + size + ".png");
            ImageIO.write(img, "PNG", outFile);
            System.out.println("  Created: " + outFile.getName());
        }
        
        // Also create @2x versions for Retina displays
        int[] retinaBaseSizes = {16, 32, 128, 256, 512};
        for (int baseSize : retinaBaseSizes) {
            int actualSize = baseSize * 2;
            BufferedImage img = renderIcon(actualSize);
            File outFile = new File(dir, "icon_" + baseSize + "x" + baseSize + "@2x.png");
            ImageIO.write(img, "PNG", outFile);
            System.out.println("  Created: " + outFile.getName() + " (Retina)");
        }
        
        System.out.println("\nDone! Use iconutil to create .icns:");
        System.out.println("  iconutil -c icns " + outputDir + " -o app.icns");
    }
    
    /**
     * Render the Simjot icon at the specified size.
     * This is a copy of AppIcon.render() to make this file standalone.
     */
    public static BufferedImage renderIcon(int size) {
        int s = Math.max(16, size);
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

            // Drop shadow
            float shadowInset = s * 0.06f;
            float arc = s * 0.22f;
            Shape shadow = new RoundRectangle2D.Float(shadowInset, shadowInset + s * 0.04f,
                    s - shadowInset * 2, s - shadowInset * 2, arc, arc);
            g.setColor(new Color(0, 0, 0, 35));
            g.fill(shadow);

            // Base rounded rectangle
            float inset = s * 0.08f;
            Shape base = new RoundRectangle2D.Float(inset, inset, s - inset * 2, s - inset * 2, arc, arc);

            // Windows 7 style gradient (uses configured colors)
            GradientPaint gp = new GradientPaint(0, inset,
                    new Color(topColor),
                    0, s - inset,
                    new Color(bottomColor));
            g.setPaint(gp);
            g.fill(base);

            // Inner border (glossy)
            g.setStroke(new BasicStroke(Math.max(1f, s * 0.03f)));
            g.setColor(new Color(255, 255, 255, 110));
            g.draw(base);

            // Outer subtle stroke
            float outInset = inset - s * 0.01f;
            Shape outer = new RoundRectangle2D.Float(outInset, outInset, s - outInset * 2, s - outInset * 2, arc, arc);
            g.setColor(new Color(0, 0, 0, 40));
            g.setStroke(new BasicStroke(Math.max(1f, s * 0.02f)));
            g.draw(outer);

            // Top gloss highlight
            Shape gloss = new RoundRectangle2D.Float(inset + s * 0.02f, inset + s * 0.02f,
                    s - (inset + s * 0.02f) * 2, (s - inset * 2) * 0.55f, arc * 0.9f, arc * 0.9f);
            Paint glossPaint = new GradientPaint(0, (float) ((RoundRectangle2D) gloss).getMinY(),
                    new Color(255, 255, 255, 140),
                    0, (float) ((RoundRectangle2D) gloss).getMaxY(),
                    new Color(255, 255, 255, 0));
            Composite oldComp = g.getComposite();
            g.setComposite(AlphaComposite.SrcOver);
            g.setPaint(glossPaint);
            g.fill(gloss);
            g.setComposite(oldComp);

            // Central glyph: a clean "S" lettermark (for Simjot) with soft shadow
            String glyph = "S";
            float glyphSize = s * 0.60f;
            Font font = new Font(Font.SANS_SERIF, Font.BOLD, Math.round(glyphSize));
            GlyphVector gv = font.createGlyphVector(g.getFontRenderContext(), glyph);
            Shape text = gv.getOutline();
            Rectangle bounds = text.getBounds();
            AffineTransform at = new AffineTransform();
            double cx = s / 2.0;
            double cy = s / 2.0 + s * 0.02; // slight optical shift
            at.translate(cx - bounds.getWidth() / 2.0 - bounds.getX(),
                         cy - bounds.getHeight() / 2.0 - bounds.getY());
            Shape centered = at.createTransformedShape(text);

            // Soft shadow for glyph
            g.setColor(new Color(0, 0, 0, 70));
            g.translate(s * 0.01, s * 0.015);
            g.fill(centered);
            g.translate(-s * 0.01, -s * 0.015);

            // Glyph fill with subtle vertical gradient
            Paint glyphPaint = new GradientPaint(0, (float) (cy - glyphSize * 0.5),
                    new Color(250, 250, 250),
                    0, (float) (cy + glyphSize * 0.5),
                    new Color(230, 230, 230));
            g.setPaint(glyphPaint);
            g.fill(centered);

            // Glyph stroke
            g.setColor(new Color(0, 0, 0, 50));
            g.setStroke(new BasicStroke(Math.max(1f, s * 0.04f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(centered);
        } finally {
            g.dispose();
        }
        return img;
    }
    
    /**
     * Parse the color argument and set topColor/bottomColor.
     */
    private static void parseColor(String colorArg) {
        // Check preset names
        for (int i = 0; i < PRESET_NAMES.length; i++) {
            if (PRESET_NAMES[i].equals(colorArg)) {
                topColor = PRESETS[i][0];
                bottomColor = PRESETS[i][1];
                System.out.println("Using color preset: " + colorArg);
                return;
            }
        }
        
        // Try parsing as hex color
        String hex = colorArg.startsWith("#") ? colorArg.substring(1) : colorArg;
        if (hex.length() == 6) {
            try {
                int baseColor = Integer.parseInt(hex, 16);
                topColor = lighten(baseColor, 0.3f);
                bottomColor = darken(baseColor, 0.3f);
                System.out.println("Using custom color: #" + hex);
                return;
            } catch (NumberFormatException e) {
                // Fall through to default
            }
        }
        
        System.out.println("Unknown color '" + colorArg + "', using default blue");
    }
    
    /**
     * Lighten a color by a factor (0.0 to 1.0).
     */
    private static int lighten(int rgb, float factor) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = Math.min(255, (int) (r + (255 - r) * factor));
        g = Math.min(255, (int) (g + (255 - g) * factor));
        b = Math.min(255, (int) (b + (255 - b) * factor));
        return (r << 16) | (g << 8) | b;
    }
    
    /**
     * Darken a color by a factor (0.0 to 1.0).
     */
    private static int darken(int rgb, float factor) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = (int) (r * (1 - factor));
        g = (int) (g * (1 - factor));
        b = (int) (b * (1 - factor));
        return (r << 16) | (g << 8) | b;
    }
}
