/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.icons;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import main.infrastructure.io.ResourceLoader;

/**
 * Central SVG/PNG icon loader/renderer with high-quality scaling and in-memory caching.
 * Supports optional subtle shadow for light backgrounds.
 */
public final class ImageIconRenderer {
    private ImageIconRenderer() {}

    private static final class Key {
        final String path; final int size; final boolean shadow; final int tint;
        Key(String path, int size, boolean shadow, int tint){ this.path=path; this.size=size; this.shadow=shadow; this.tint=tint; }
        @Override public boolean equals(Object o){
            if (!(o instanceof Key k)) return false; return size==k.size && shadow==k.shadow && tint==k.tint && path.equals(k.path);
        }
        @Override public int hashCode(){ int h = path.hashCode()*31 + size*3 + (shadow?1:0); return h*31 + tint; }
    }

    // LRU cache limits to prevent unbounded memory growth
    private static final int MAX_SCALED_CACHE = 256;
    private static final int MAX_SOURCE_CACHE = 64;
    private static final int MAX_ID_CACHE = 512;

    // LRU cache for scaled icons (path+size+shadow -> image)
    private static final Map<Key, BufferedImage> CACHE = new LinkedHashMap<>(128, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, BufferedImage> eldest) {
            if (size() > MAX_SCALED_CACHE) {
                eldest.getValue().flush(); // Release native resources
                return true;
            }
            return false;
        }
    };

    // SoftReference cache for source images - GC can reclaim under memory pressure
    private static final Map<String, SoftReference<BufferedImage>> SOURCE_CACHE = new LinkedHashMap<>(32, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, SoftReference<BufferedImage>> eldest) {
            return size() > MAX_SOURCE_CACHE;
        }
    };

    // Simple LRU for ID->path mappings (strings only, low memory)
    private static final Map<String, String> ID_TO_PATH_CACHE = new LinkedHashMap<>(128, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > MAX_ID_CACHE;
        }
    };
    private static final String ID_CACHE_MISS = "__missing__";
    private static final String CUSTOM_PNG_DIR = "img/icons/original/";
    private static final String STREAMLINE_SVG_DIR = "img/icons/streamline-ultimate-light---free--24x24-SVG/";
    private static final String STREAMLINE_PNG_DIR = "img/icons/streamline-ultimate-light---free--24x24-PNG/";

    public static void setAccentTint(Color c) {
        // Tinting disabled for icon sets, but clear caches on palette changes.
        clearCaches();
    }

    public static void clearCaches() {
        CACHE.clear();
        SOURCE_CACHE.clear();
        ID_TO_PATH_CACHE.clear();
    }
    private static int tintSignature() { return 0; }

    public static BufferedImage get(String resourcePath, int size, boolean shadow){
        if (resourcePath == null) return null;
        // Normalize: allow callers to pass with or without leading "Simjot/"
        if (resourcePath.startsWith("Simjot/")) {
            resourcePath = resourcePath.substring("Simjot/".length());
        }
        Key key = new Key(resourcePath, size, shadow, tintSignature());
        return CACHE.computeIfAbsent(key, k -> build(k.path, k.size, k.shadow));
    }
    
    /**
     * Get an icon with a custom accent color override (not cached, bypasses system accent).
     * Used for notebook tiles with custom accent colors.
     */
    public static BufferedImage getWithCustomAccent(String resourcePath, int size, boolean shadow, Color customAccent){
        if (resourcePath == null) return null;
        if (resourcePath.startsWith("Simjot/")) {
            resourcePath = resourcePath.substring("Simjot/".length());
        }
        return buildWithAccent(resourcePath, size, shadow, customAccent);
    }

    public static boolean draw(Graphics2D g, String resourcePath, int x, int y, int size, Component obs, boolean shadow){
        if (g == null || resourcePath == null || size <= 0) return false;
        double scale = resolveScale(g, obs);
        int scaledSize = Math.max(1, (int)Math.ceil(size * scale));
        BufferedImage img = get(resourcePath, scaledSize, shadow);
        if (img == null) return false;
        if (Math.abs(scale - 1.0) < 0.01) {
            g.drawImage(img, x, y, obs);
        } else {
            g.drawImage(img, x, y, size, size, obs);
        }
        return true;
    }
    
    public static Icon icon(String resourcePath, int size, boolean shadow) {
        if (resourcePath == null || size <= 0) return null;
        int target = Math.max(1, size);
        return new HiDpiIcon(resourcePath, target, shadow);
    }

    private static final class HiDpiIcon implements Icon {
        private final String path;
        private final int size;
        private final boolean shadow;

        private HiDpiIcon(String path, int size, boolean shadow) {
            this.path = path;
            this.size = size;
            this.shadow = shadow;
        }

        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }
        @Override public void paintIcon(Component c, java.awt.Graphics g, int x, int y) {
            if (g instanceof Graphics2D g2) {
                ImageIconRenderer.draw(g2, path, x, y, size, c, shadow);
            }
        }
    }

    private static double resolveScale(Graphics2D g, Component obs) {
        double scale = 1.0;
        try {
            GraphicsConfiguration gc = (obs != null) ? obs.getGraphicsConfiguration() : g.getDeviceConfiguration();
            if (gc != null) {
                AffineTransform tx = gc.getDefaultTransform();
                scale = Math.max(tx.getScaleX(), tx.getScaleY());
            }
        } catch (Throwable ignored) {
        }
        if (scale <= 0.0 || Double.isNaN(scale) || Double.isInfinite(scale)) {
            scale = 1.0;
        }
        if (Math.abs(scale - 1.0) < 0.01) {
            try {
                AffineTransform tx = g.getTransform();
                double sx = Math.abs(tx.getScaleX());
                double sy = Math.abs(tx.getScaleY());
                scale = Math.max(sx, sy);
            } catch (Throwable ignored) {
            }
        }
        if (scale <= 0.0 || Double.isNaN(scale) || Double.isInfinite(scale)) {
            scale = 1.0;
        }
        return scale;
    }

    private static BufferedImage build(String path, int size, boolean withShadow){
        int target = Math.max(1, size);
        BufferedImage scaled = loadAndScale(path, target);
        if (scaled == null) return null;

        // Accent recolor is intentionally disabled for the new icon set.
        BufferedImage tinted = recolorToAccentIfEnabled(scaled);
        return applyShadow(tinted, target, withShadow);
    }

    private static BufferedImage buildWithAccent(String path, int size, boolean withShadow, Color customAccent){
        int target = Math.max(1, size);
        BufferedImage scaled = loadAndScale(path, target);
        if (scaled == null) return null;

        BufferedImage tinted = recolorWithAccent(scaled, customAccent);
        return applyShadow(tinted, target, withShadow);
    }

    private static BufferedImage loadAndScale(String path, int target){
        BufferedImage src = loadSourceImage(path, target);
        if (src == null) return null;
        int srcW = src.getWidth();
        int srcH = src.getHeight();
        if (srcW <= 0 || srcH <= 0) return null;
        if (srcW == target && srcH == target) return src;

        // Try native SIMD-accelerated scaling first
        BufferedImage nativeScaled = tryNativeScale(src, srcW, srcH, target);
        if (nativeScaled != null) return nativeScaled;

        // Java fallback: Progressive downscale for better sharpness when shrinking a lot
        BufferedImage current = src;
        int cw = srcW;
        int ch = srcH;
        while (cw / 2 >= target || ch / 2 >= target) {
            int nw = Math.max(1, cw / 2);
            int nh = Math.max(1, ch / 2);
            BufferedImage tmp = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = tmp.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
            g2.drawImage(current, 0, 0, nw, nh, null);
            g2.dispose();
            if (current != src) current.flush();
            current = tmp; cw = nw; ch = nh;
        }

        BufferedImage scaled = new BufferedImage(target, target, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
        float scale = Math.min((float) target / srcW, (float) target / srcH);
        int drawW = Math.max(1, Math.round(srcW * scale));
        int drawH = Math.max(1, Math.round(srcH * scale));
        int drawX = (target - drawW) / 2;
        int drawY = (target - drawH) / 2;
        g.drawImage(current, drawX, drawY, drawW, drawH, null);
        g.dispose();
        if (current != src) current.flush();
        return scaled;
    }

    private static BufferedImage tryNativeScale(BufferedImage src, int srcW, int srcH, int target) {
        try {
            if (!main.infrastructure.ffi.NativeAccess.imageScaleReady()) return null;
            
            // Calculate target dimensions maintaining aspect ratio
            float scale = Math.min((float) target / srcW, (float) target / srcH);
            int drawW = Math.max(1, Math.round(srcW * scale));
            int drawH = Math.max(1, Math.round(srcH * scale));
            
            // Use native scaling with quality=1 (balanced)
            BufferedImage scaledContent = main.infrastructure.ffi.NativeAccess.imageScale(src, drawW, drawH, 1);
            if (scaledContent == null) return null;
            
            // Center in target-sized image
            BufferedImage result = new BufferedImage(target, target, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = result.createGraphics();
            int drawX = (target - drawW) / 2;
            int drawY = (target - drawH) / 2;
            g.drawImage(scaledContent, drawX, drawY, null);
            g.dispose();
            scaledContent.flush();
            return result;
        } catch (Throwable t) {
            return null;
        }
    }

    private static BufferedImage loadSourceImage(String path, int target){
        if (path == null) return null;
        if (isSvg(path)) {
            BufferedImage svg = renderSvg(path, target);
            if (svg != null) return svg;
            String fallback = toPngFallback(path);
            if (fallback != null) return loadRasterImage(fallback);
            return null;
        }
        return loadRasterImage(path);
    }

    private static BufferedImage loadRasterImage(String path){
        SoftReference<BufferedImage> ref = SOURCE_CACHE.get(path);
        if (ref != null) {
            BufferedImage cached = ref.get();
            if (cached != null) return cached;
        }
        Image imgRaw = ResourceLoader.createImage("Simjot/" + path);
        if (imgRaw == null) return null;

        ImageIcon icon = new ImageIcon(imgRaw);
        int srcW = icon.getIconWidth();
        int srcH = icon.getIconHeight();
        if (srcW <= 0 || srcH <= 0) return null;
        BufferedImage src = new BufferedImage(srcW, srcH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gsrc = src.createGraphics();
        gsrc.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        gsrc.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        gsrc.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        gsrc.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
        gsrc.drawImage(imgRaw, 0, 0, null);
        gsrc.dispose();
        SOURCE_CACHE.put(path, new SoftReference<>(src));
        return src;
    }

    private static BufferedImage renderSvg(String path, int target){
        try (InputStream in = ResourceLoader.getResourceAsStream("Simjot/" + path)) {
            if (in == null) return null;
            Object transcoder = newPngTranscoder();
            if (transcoder == null) return null;
            Class<?> inputClass = Class.forName("org.apache.batik.transcoder.TranscoderInput");
            Class<?> outputClass = Class.forName("org.apache.batik.transcoder.TranscoderOutput");
            Object input = inputClass.getConstructor(InputStream.class).newInstance(in);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Object output = outputClass.getConstructor(OutputStream.class).newInstance(out);
            applySvgSizeHints(transcoder, target);
            Method transcode = transcoder.getClass().getMethod("transcode", inputClass, outputClass);
            transcode.invoke(transcoder, input, output);
            byte[] bytes = out.toByteArray();
            if (bytes.length == 0) return null;
            return javax.imageio.ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static BufferedImage applyShadow(BufferedImage base, int target, boolean withShadow){
        if (!withShadow || base == null) return base;
        BufferedImage shadow = new BufferedImage(target, target, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gSh = shadow.createGraphics();
        gSh.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        gSh.drawImage(base, 0, 0, null);
        gSh.setComposite(AlphaComposite.SrcAtop);
        gSh.setColor(new Color(0,0,0,80));
        gSh.fillRect(0, 0, target, target);
        gSh.dispose();

        BufferedImage out = new BufferedImage(target, target, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gOut = out.createGraphics();
        gOut.drawImage(shadow, 1, 1, null);
        gOut.drawImage(base, 0, 0, null);
        gOut.dispose();
        return out;
    }

    private static boolean isSvg(String path){
        return path.toLowerCase().endsWith(".svg");
    }

    private static String toPngFallback(String svgPath){
        if (!svgPath.startsWith(STREAMLINE_SVG_DIR) || !svgPath.endsWith(".svg")) return null;
        String stem = svgPath.substring(STREAMLINE_SVG_DIR.length(), svgPath.length() - 4);
        return STREAMLINE_PNG_DIR + stem + ".png";
    }

    private static Object newPngTranscoder() {
        try {
            Class<?> transcoderClass = Class.forName("org.apache.batik.transcoder.image.PNGTranscoder");
            return transcoderClass.getConstructor().newInstance();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void applySvgSizeHints(Object transcoder, int target) {
        try {
            Class<?> svgAbstract = Class.forName("org.apache.batik.transcoder.SVGAbstractTranscoder");
            Field keyWidthField = svgAbstract.getField("KEY_WIDTH");
            Field keyHeightField = svgAbstract.getField("KEY_HEIGHT");
            Object keyWidth = keyWidthField.get(null);
            Object keyHeight = keyHeightField.get(null);
            Class<?> keyClass = Class.forName("org.apache.batik.transcoder.TranscodingHints$Key");
            Method addHint = transcoder.getClass().getMethod("addTranscodingHint", keyClass, Object.class);
            addHint.invoke(transcoder, keyWidth, Float.valueOf(target));
            addHint.invoke(transcoder, keyHeight, Float.valueOf(target));
        } catch (Throwable ignored) {
        }
    }

    private static BufferedImage recolorWithAccent(BufferedImage src, Color accentColor){
        return src;
    }

    // Recolor by remapping pixel luminance to the accent hue (monochrome colorize),
    // preserving near-black strokes and near-white highlights.
    private static BufferedImage recolorToAccentIfEnabled(BufferedImage src){
        return src;
    }

    private static String iconSvg(String name){
        return STREAMLINE_SVG_DIR + name + "--Streamline-Ultimate.svg";
    }

    private static String mapIdToStreamlineResource(String id){
        if (id == null) return null;
        return switch (id.toLowerCase()) {
            case "notebook" -> iconSvg("Bookmarks-Document");
            case "smile" -> iconSvg("Graph-Stats-Circle");
            case "chart", "stats", "analysis" -> iconSvg("Graph-Stats-Descend");
            case "wrench", "settings", "options" -> iconSvg("Cog");
            case "trash" -> iconSvg("Bin-1");
            case "new", "plus" -> iconSvg("Add-Circle-Bold");
            case "write" -> iconSvg("Pen-Write");
            case "brush", "pencil" -> iconSvg("Pencil-1");
            case "delete", "delete_entry" -> iconSvg("Delete-2");
            case "back" -> iconSvg("Direction-Button-2");
            case "list", "lines" -> iconSvg("List-Numbers");
            case "close" -> iconSvg("Remove-Bold");
            case "saveandexit", "save_and_exit" -> iconSvg("Login-1");
            case "check", "select", "apply", "tick" -> iconSvg("Check");
            case "save" -> iconSvg("Check-Badge");
            case "load" -> iconSvg("Move-Down-1");
            case "backgroundoptions" -> iconSvg("Settings-Slider-Desktop-Horizontal");
            case "gallery", "gallerypicker" -> iconSvg("Layout-11");
            case "image" -> iconSvg("Cell-Border-Frame");
            case "clock" -> iconSvg("Time-Clock-Circle");
            case "calendar" -> iconSvg("Calendar-3");
            // View / window controls
            case "fullscreen", "enter_fullscreen", "toggle_fullscreen" -> iconSvg("Expand-Full");
            case "export", "share", "download" -> iconSvg("Direction-Button-3");
            case "rhyme", "rhymes", "rhyme_dock" -> iconSvg("Arrange-Letter");
            // Storage actions
            case "explorer", "open_in_explorer" -> iconSvg("Layout-Content");
            case "refreshsizes", "refresh_sizes" -> iconSvg("Button-Loop");
            case "revealselected", "reveal_selected" -> iconSvg("Cursor-Target-1");
            // Sim guidance
            case "sim_guidance", "simguidance", "guidance" -> iconSvg("Help-Question-Network");
            // Settings sidebar categories
            case "general_settings", "settings_general" -> iconSvg("Layout-Dashboard");
            case "appearance_settings", "settings_appearance" -> iconSvg("Brightness");
            case "storage_settings", "settings_storage" -> iconSvg("Layout");
            case "sim_settings", "settings_sim" -> iconSvg("Laptop-Help-Message");
            case "about_settings", "settings_about" -> iconSvg("Information-Circle");
            case "security", "security_settings" -> iconSvg("Lock-5");
            case "debug_settings", "settings_debug" -> iconSvg("Cog-Search-1");
            // Widget buttons
            case "breathing_widget", "widget_breathing", "breath" -> iconSvg("Gauge-Dashboard");
            case "pomodoro_widget", "widget_pomodoro" -> iconSvg("Alarm-Bell-Ring");
            case "sticky_widget", "widget_sticky" -> iconSvg("Paper-Write");
            default -> null;
        };
    }

    private static boolean resourceExists(String path) {
        return ResourceLoader.getResource("Simjot/" + path) != null;
    }

    private static String resolveCustomResource(String id) {
        if (id == null || id.isEmpty()) return null;
        String direct = CUSTOM_PNG_DIR + id + ".png";
        if (resourceExists(direct)) return direct;
        String alias = switch (id) {
            case "smile", "mood", "moodchart" -> "moodchart";
            case "saveandexit", "save_and_exit" -> "exit";
            case "wrench", "settings", "options", "general_settings", "settings_general" -> "settings";
            case "appearance_settings", "settings_appearance" -> "settings_appearance";
            case "storage_settings", "settings_storage" -> "settings_storage";
            case "backgroundoptions" -> "set_background";
            case "notebook_nopen" -> "notebook_nopen";
            case "notebook" -> "notebook";
            case "back" -> "back";
            case "forward" -> "forward";
            case "search" -> "search";
            case "clock" -> "clock";
            case "rescan", "refreshsizes", "refresh_sizes" -> "Rescan";
            case "fullscreen", "enter_fullscreen", "toggle_fullscreen" -> "fullscreen";
            case "save" -> "save";
            case "export", "share", "download" -> "save";
            case "load", "restore" -> "restore";
            case "new", "plus", "new_entry" -> "new_entry";
            case "exit" -> "exit";
            case "analyze", "analysis" -> "analyze";
            case "stats", "chart" -> "poetry_metering";
            case "rhyme", "rhymes", "rhyme_dock" -> "poetry_rhymes";
            case "delete_entry", "delete" -> "delete_entry";
            case "delete_notebook" -> "delete_notebook";
            case "trash" -> "delete_default";
            case "open_folder", "folder", "folder_open" -> "open_folder";
            // Notetaking toolbar tools
            case "select_text", "text_select", "cursor" -> "notebook";
            case "pen_tool" -> "new_entry";
            case "highlighter_tool", "highlighter" -> "analyze";
            case "eraser_tool", "eraser" -> "delete_default";
            default -> null;
        };
        if (alias == null) return null;
        String aliasPath = CUSTOM_PNG_DIR + alias + ".png";
        return resourceExists(aliasPath) ? aliasPath : null;
    }

    // Convenience mapping for common IDs used across the app
    public static String mapIdToResource(String id){
        if (id == null) return null;
        String key = id.toLowerCase();
        String cached = ID_TO_PATH_CACHE.get(key);
        if (cached != null) return ID_CACHE_MISS.equals(cached) ? null : cached;
        String custom = resolveCustomResource(key);
        String resolved = (custom != null) ? custom : mapIdToStreamlineResource(key);
        ID_TO_PATH_CACHE.put(key, resolved != null ? resolved : ID_CACHE_MISS);
        return resolved;
    }
}
