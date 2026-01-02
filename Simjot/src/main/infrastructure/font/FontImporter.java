/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.infrastructure.font;

import java.awt.Font;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import main.core.font.CustomFont;
import main.core.font.CustomGlyph;
import main.core.font.CustomStroke;
import main.infrastructure.ffi.NativeAccess;
import main.infrastructure.ffi.NativeFonts;

/**
 * Imports fonts from OTF, TTF, and other standard font formats into Simjot's custom font format.
 * Converts vector outlines to stroke-based representation.
 * 
 * IMPORTANT: Native code has strict limits that must be respected:
 * - SJF_MAX_STROKES = 64 strokes per glyph
 * - SJF_MAX_POINTS = 1024 points per stroke
 */
public final class FontImporter {
    
    /** Native format limits - MUST match font_types.h */
    private static final int MAX_STROKES_PER_GLYPH = 60;  // Leave some headroom below 64
    private static final int MAX_POINTS_PER_STROKE = 900; // Leave some headroom below 1024
    
    /** Supported font file extensions */
    public static final String[] SUPPORTED_EXTENSIONS = { ".ttf", ".otf", ".ttc", ".dfont" };
    
    /** Default characters to import (ASCII printable + common extended) */
    public static final String DEFAULT_CHARSET = 
        " !\"#$%&'()*+,-./0123456789:;<=>?@" +
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`" +
        "abcdefghijklmnopqrstuvwxyz{|}~" +
        "¡¢£¤¥¦§¨©ª«¬®¯°±²³´µ¶·¸¹º»¼½¾¿" +
        "ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖ×ØÙÚÛÜÝÞß" +
        "àáâãäåæçèéêëìíîïðñòóôõö÷øùúûüýþÿ";
    
    /** Basic ASCII only */
    public static final String ASCII_CHARSET = 
        " !\"#$%&'()*+,-./0123456789:;<=>?@" +
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`" +
        "abcdefghijklmnopqrstuvwxyz{|}~";
    
    private FontImporter() {}
    
    /**
     * Check if a file has a supported font extension.
     */
    public static boolean isSupportedFile(File file) {
        if (file == null || !file.isFile()) return false;
        String name = file.getName().toLowerCase();
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (name.endsWith(ext)) return true;
        }
        return false;
    }
    
    /**
     * Import a font file and convert to CustomFont format.
     * 
     * @param fontFile The font file to import (TTF, OTF, etc.)
     * @param charset Characters to import (null for default charset)
     * @param strokeThickness Thickness for converted strokes
     * @return The imported CustomFont, or null on failure
     */
    public static CustomFont importFont(File fontFile, String charset, float strokeThickness) throws IOException {
        ImportResult result = importFontWithResult(fontFile, charset, strokeThickness);
        if (result != null && result.font != null) {
            return result.font;
        }
        String message = "Failed to import font.";
        if (result != null && result.warnings != null && !result.warnings.isEmpty()) {
            message = result.warnings.get(0);
        }
        throw new IOException(message);
    }
    
    /**
     * Import from an input stream.
     */
    public static CustomFont importFont(InputStream fontStream, String fontName, String charset, float strokeThickness) throws IOException {
        Font awtFont;
        try {
            awtFont = Font.createFont(Font.TRUETYPE_FONT, fontStream);
        } catch (Exception e) {
            throw new IOException("Failed to load font: " + e.getMessage(), e);
        }
        
        return convertFont(awtFont, fontName, charset, strokeThickness);
    }

    private static CustomFont importFontJava(File fontFile, String charset, float strokeThickness) throws IOException {
        if (fontFile == null || !fontFile.exists()) {
            throw new IOException("Font file does not exist: " + fontFile);
        }

        Font awtFont;
        try (InputStream is = new FileInputStream(fontFile)) {
            awtFont = Font.createFont(Font.TRUETYPE_FONT, is);
        } catch (Exception e) {
            throw new IOException("Failed to load font: " + e.getMessage(), e);
        }

        return convertFont(awtFont, fontFile.getName(), charset, strokeThickness);
    }
    
    /**
     * Convert an AWT Font to CustomFont format.
     */
    public static CustomFont convertFont(Font awtFont, String name, String charset, float strokeThickness) {
        if (awtFont == null) return null;
        
        // Extract font name
        String fontName = name;
        if (fontName == null || fontName.isBlank()) {
            fontName = awtFont.getFontName();
        }
        // Strip extension from filename
        int dotIdx = fontName.lastIndexOf('.');
        if (dotIdx > 0) {
            fontName = fontName.substring(0, dotIdx);
        }
        
        // Create the custom font with standard metrics
        CustomFont customFont = new CustomFont(fontName, "Imported");
        float emSize = customFont.getEmSize(); // 1000
        
        // Use default charset if not specified
        String chars = (charset != null && !charset.isEmpty()) ? charset : DEFAULT_CHARSET;
        
        // Derive a font at em-size scale for consistent conversion
        Font scaledFont = awtFont.deriveFont((float) emSize);
        FontRenderContext frc = new FontRenderContext(null, true, true);
        
        // Convert each character
        for (int i = 0; i < chars.length(); i++) {
            char ch = chars.charAt(i);
            int codepoint = ch;
            
            // Skip if font doesn't have this glyph
            if (!scaledFont.canDisplay(ch)) continue;
            
            try {
                CustomGlyph glyph = convertGlyph(scaledFont, frc, codepoint, emSize, strokeThickness);
                if (glyph != null && glyph.isDefined()) {
                    customFont.setGlyph(codepoint, glyph);
                }
            } catch (Throwable t) {
                // Skip problematic glyphs
                System.err.println("Failed to convert glyph '" + ch + "': " + t.getMessage());
            }
        }
        
        return customFont;
    }
    
    /**
     * Convert a single glyph from AWT Font to CustomGlyph.
     * Enforces native format limits on strokes and points.
     */
    private static CustomGlyph convertGlyph(Font font, FontRenderContext frc, int codepoint, float emSize, float strokeThickness) {
        String str = new String(Character.toChars(codepoint));
        GlyphVector gv = font.createGlyphVector(frc, str);
        
        if (gv.getNumGlyphs() == 0) return null;
        
        Shape outline = gv.getOutline();
        Rectangle2D bounds = outline.getBounds2D();
        
        // Skip empty glyphs (like space) but create placeholder
        if (bounds.getWidth() < 0.1 && bounds.getHeight() < 0.1) {
            CustomGlyph glyph = new CustomGlyph(codepoint);
            glyph.setAdvanceWidth((float) gv.getGlyphMetrics(0).getAdvanceX());
            glyph.setDefined(false);
            return glyph;
        }
        
        // Convert outline to strokes with adaptive flatness
        // Start with coarse flatness and refine if we're within limits
        float flatness = emSize / 50.0f; // Coarser initial flatness
        List<CustomStroke> strokes = outlineToStrokes(outline, strokeThickness, emSize, flatness);
        
        // If too many strokes, use even coarser flatness
        if (strokes.size() > MAX_STROKES_PER_GLYPH) {
            flatness = emSize / 20.0f;
            strokes = outlineToStrokes(outline, strokeThickness, emSize, flatness);
        }
        
        // Still too many? Truncate and warn
        if (strokes.size() > MAX_STROKES_PER_GLYPH) {
            strokes = strokes.subList(0, MAX_STROKES_PER_GLYPH);
        }
        
        CustomGlyph glyph = new CustomGlyph(codepoint);
        for (CustomStroke stroke : strokes) {
            if (stroke.getPointCount() > 0) {
                // Simplify strokes with too many points
                if (stroke.getPointCount() > MAX_POINTS_PER_STROKE) {
                    stroke.simplify(emSize / 100.0f); // Douglas-Peucker simplification
                }
                // Final check - truncate if still too many
                if (stroke.getPointCount() > MAX_POINTS_PER_STROKE) {
                    truncateStroke(stroke, MAX_POINTS_PER_STROKE);
                }
                glyph.addStroke(stroke);
            }
        }
        
        // Set advance width from original metrics
        float advance = (float) gv.getGlyphMetrics(0).getAdvanceX();
        glyph.setAdvanceWidth(advance);
        
        if (glyph.getStrokeCount() > 0) {
            glyph.setDefined(true);
            glyph.computeMetrics(emSize);
        }
        
        return glyph;
    }
    
    /**
     * Truncate a stroke to the specified maximum number of points.
     * Uses uniform sampling to preserve shape.
     */
    private static void truncateStroke(CustomStroke stroke, int maxPoints) {
        if (stroke.getPointCount() <= maxPoints) return;
        
        var points = stroke.getPoints();
        int originalCount = points.size();
        
        // Create new stroke with sampled points
        CustomStroke temp = new CustomStroke(stroke.getThickness());
        temp.setColor(stroke.getColor());
        
        // Always include first and last point
        float step = (float) (originalCount - 1) / (maxPoints - 1);
        for (int i = 0; i < maxPoints - 1; i++) {
            int idx = Math.min((int) (i * step), originalCount - 1);
            var p = points.get(idx);
            temp.addPoint(p.getX(), p.getY(), p.getPressure(), p.getTimestamp());
        }
        // Add last point
        var last = points.get(originalCount - 1);
        temp.addPoint(last.getX(), last.getY(), last.getPressure(), last.getTimestamp());
        
        // Replace original stroke's points
        stroke.clear();
        for (var p : temp.getPoints()) {
            stroke.addPoint(p.getX(), p.getY(), p.getPressure(), p.getTimestamp());
        }
    }
    
    /**
     * Convert a Shape outline to a list of CustomStrokes.
     * Traces the outline path and converts curves to point sequences.
     * 
     * @param outline The shape outline to convert
     * @param thickness Stroke thickness
     * @param emSize Em size for coordinate normalization
     * @param flatness Flatness value for path flattening (higher = fewer points)
     */
    private static List<CustomStroke> outlineToStrokes(Shape outline, float thickness, float emSize, float flatness) {
        List<CustomStroke> strokes = new ArrayList<>();
        
        // Flatten curves to line segments
        PathIterator pi = outline.getPathIterator(null, flatness);
        
        CustomStroke currentStroke = null;
        float[] coords = new float[6];
        float lastX = 0, lastY = 0;
        
        while (!pi.isDone()) {
            int type = pi.currentSegment(coords);
            
            switch (type) {
                case PathIterator.SEG_MOVETO:
                    // Start a new stroke
                    if (currentStroke != null && currentStroke.getPointCount() > 1) {
                        strokes.add(currentStroke);
                    }
                    currentStroke = new CustomStroke(thickness);
                    lastX = coords[0];
                    lastY = flipY(coords[1], emSize);
                    currentStroke.addPoint(lastX, lastY);
                    break;
                    
                case PathIterator.SEG_LINETO:
                    if (currentStroke != null) {
                        lastX = coords[0];
                        lastY = flipY(coords[1], emSize);
                        currentStroke.addPoint(lastX, lastY);
                    }
                    break;
                    
                case PathIterator.SEG_QUADTO:
                    // Quadratic bezier - add interpolated points
                    if (currentStroke != null) {
                        float cx = coords[0];
                        float cy = flipY(coords[1], emSize);
                        float x = coords[2];
                        float y = flipY(coords[3], emSize);
                        addQuadraticPoints(currentStroke, lastX, lastY, cx, cy, x, y, 8);
                        lastX = x;
                        lastY = y;
                    }
                    break;
                    
                case PathIterator.SEG_CUBICTO:
                    // Cubic bezier - add interpolated points
                    if (currentStroke != null) {
                        float c1x = coords[0];
                        float c1y = flipY(coords[1], emSize);
                        float c2x = coords[2];
                        float c2y = flipY(coords[3], emSize);
                        float x = coords[4];
                        float y = flipY(coords[5], emSize);
                        addCubicPoints(currentStroke, lastX, lastY, c1x, c1y, c2x, c2y, x, y, 12);
                        lastX = x;
                        lastY = y;
                    }
                    break;
                    
                case PathIterator.SEG_CLOSE:
                    // Close the path - connect back to start
                    if (currentStroke != null && currentStroke.getPointCount() > 1) {
                        var first = currentStroke.getFirstPoint();
                        if (first != null) {
                            currentStroke.addPoint(first.getX(), first.getY());
                        }
                        strokes.add(currentStroke);
                        currentStroke = null;
                    }
                    break;
            }
            
            pi.next();
        }
        
        // Add final stroke if not closed
        if (currentStroke != null && currentStroke.getPointCount() > 1) {
            strokes.add(currentStroke);
        }
        
        return strokes;
    }
    
    /**
     * Flip Y coordinate (fonts use inverted Y axis).
     */
    private static float flipY(float y, float emSize) {
        return emSize - y;
    }
    
    /**
     * Add points along a quadratic bezier curve.
     */
    private static void addQuadraticPoints(CustomStroke stroke, 
            float x0, float y0, float cx, float cy, float x1, float y1, int segments) {
        for (int i = 1; i <= segments; i++) {
            float t = (float) i / segments;
            float mt = 1.0f - t;
            float x = mt * mt * x0 + 2 * mt * t * cx + t * t * x1;
            float y = mt * mt * y0 + 2 * mt * t * cy + t * t * y1;
            stroke.addPoint(x, y);
        }
    }
    
    /**
     * Add points along a cubic bezier curve.
     */
    private static void addCubicPoints(CustomStroke stroke,
            float x0, float y0, float c1x, float c1y, float c2x, float c2y, float x1, float y1, int segments) {
        for (int i = 1; i <= segments; i++) {
            float t = (float) i / segments;
            float mt = 1.0f - t;
            float mt2 = mt * mt;
            float mt3 = mt2 * mt;
            float t2 = t * t;
            float t3 = t2 * t;
            
            float x = mt3 * x0 + 3 * mt2 * t * c1x + 3 * mt * t2 * c2x + t3 * x1;
            float y = mt3 * y0 + 3 * mt2 * t * c1y + 3 * mt * t2 * c2y + t3 * y1;
            stroke.addPoint(x, y);
        }
    }
    
    /**
     * Get a file filter description for supported formats.
     */
    public static String getFileFilterDescription() {
        return "Font Files (*.ttf, *.otf, *.ttc)";
    }
    
    /**
     * Result of an import operation with details.
     */
    public static final class ImportResult {
        public final CustomFont font;
        public final int glyphCount;
        public final int definedCount;
        public final List<String> warnings;
        
        public ImportResult(CustomFont font, List<String> warnings) {
            this.font = font;
            this.glyphCount = font != null ? font.getGlyphCount() : 0;
            this.definedCount = font != null ? font.getDefinedGlyphCount() : 0;
            this.warnings = warnings != null ? warnings : new ArrayList<>();
        }
        
        public boolean isSuccess() {
            return font != null && definedCount > 0;
        }
    }
    
    /**
     * Import with detailed result reporting.
     */
    public static ImportResult importFontWithResult(File fontFile, String charset, float strokeThickness) {
        List<String> warnings = new ArrayList<>();

        ImportResult nativeResult = importFontNativeWithResult(fontFile, charset, strokeThickness);
        if (nativeResult != null && nativeResult.font != null) {
            return nativeResult;
        }
        if (nativeResult != null && nativeResult.warnings != null) {
            warnings.addAll(nativeResult.warnings);
        }

        try {
            CustomFont font = importFontJava(fontFile, charset, strokeThickness);
            if (font == null) {
                warnings.add("Failed to parse font file");
                return new ImportResult(null, warnings);
            }

            if (font.getDefinedGlyphCount() == 0) {
                warnings.add("No displayable glyphs found in font");
            }

            if (nativeResult != null && !warnings.isEmpty()) {
                warnings.add(0, "Native import unavailable; used Java fallback.");
            }

            return new ImportResult(font, warnings);
        } catch (IOException e) {
            warnings.add("IO Error: " + e.getMessage());
            return new ImportResult(null, warnings);
        } catch (Throwable t) {
            warnings.add("Error: " + t.getMessage());
            return new ImportResult(null, warnings);
        }
    }

    private static ImportResult importFontNativeWithResult(File fontFile, String charset, float strokeThickness) {
        List<String> warnings = new ArrayList<>();
        if (fontFile == null || !fontFile.exists()) {
            warnings.add("Font file does not exist: " + fontFile);
            return new ImportResult(null, warnings);
        }
        if (!NativeAccess.isAvailable()) {
            warnings.add("Native importer unavailable.");
            return new ImportResult(null, warnings);
        }

        var lookup = NativeAccess.symbolLookup();
        if (lookup == null) {
            warnings.add("Native importer unavailable.");
            return new ImportResult(null, warnings);
        }

        String chars = (charset != null && !charset.isEmpty()) ? charset : DEFAULT_CHARSET;
        try (NativeFonts fonts = new NativeFonts(lookup)) {
            if (!fonts.hasFontImport()) {
                warnings.add("Native importer unavailable.");
                return new ImportResult(null, warnings);
            }
            NativeFonts.FontImportResult nativeResult = fonts.fontImport(
                fontFile.getAbsolutePath(),
                chars,
                strokeThickness
            );

            if (nativeResult == null || nativeResult.font() == null || nativeResult.font().equals(java.lang.foreign.MemorySegment.NULL)) {
                warnings.add("Native import failed to load font file.");
                return new ImportResult(null, warnings);
            }

            if (nativeResult.error() != 0) {
                warnings.add("Native import error: " + nativeErrorMessage(nativeResult.error()));
                fonts.fontFree(nativeResult.font());
                return new ImportResult(null, warnings);
            }

            int packSize = fonts.fontPack(nativeResult.font(), java.lang.foreign.MemorySegment.NULL, 0);
            if (packSize <= 0) {
                warnings.add("Native import failed to serialize font.");
                fonts.fontFree(nativeResult.font());
                return new ImportResult(null, warnings);
            }

            byte[] data;
            try (var arena = java.lang.foreign.Arena.ofConfined()) {
                var buffer = arena.allocate(packSize);
                int packed = fonts.fontPack(nativeResult.font(), buffer, packSize);
                if (packed <= 0) {
                    warnings.add("Native import failed to serialize font.");
                    fonts.fontFree(nativeResult.font());
                    return new ImportResult(null, warnings);
                }
                data = new byte[packed];
                java.lang.foreign.MemorySegment.copy(buffer, java.lang.foreign.ValueLayout.JAVA_BYTE, 0, data, 0, data.length);
            } finally {
                fonts.fontFree(nativeResult.font());
            }

            CustomFont font = CustomFontStorage.loadFromBytes(data);
            if (nativeResult.skipped() > 0) {
                warnings.add("Native importer skipped " + nativeResult.skipped() + " glyph(s).");
            }
            if (nativeResult.defined() == 0) {
                warnings.add("No displayable glyphs found in font.");
            }
            return new ImportResult(font, warnings);
        } catch (IOException e) {
            warnings.add("IO Error: " + e.getMessage());
            return new ImportResult(null, warnings);
        } catch (Throwable t) {
            warnings.add("Native import error: " + t.getMessage());
            return new ImportResult(null, warnings);
        }
    }

    private static String nativeErrorMessage(int code) {
        return switch (code) {
            case -1 -> "Null input";
            case -2 -> "Invalid font data";
            case -3 -> "Unsupported version";
            case -4 -> "I/O error";
            case -5 -> "Out of memory";
            case -6 -> "Overflow";
            case -7 -> "Invalid data";
            case -8 -> "Not found";
            case -9 -> "Buffer too small";
            default -> "Error code " + code;
        };
    }
}
