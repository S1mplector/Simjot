package main.ui.components.icons;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    private static final Map<Key, BufferedImage> CACHE = new ConcurrentHashMap<>();
    private static final String STREAMLINE_SVG_DIR = "img/icons/streamline-ultimate-light---free--24x24-SVG/";
    private static final String STREAMLINE_PNG_DIR = "img/icons/streamline-ultimate-light---free--24x24-PNG/";

    public static void setAccentTint(Color c) {
        // Tinting disabled for Streamline icon set.
    }

    public static void clearCaches() {
        CACHE.clear();
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

    public static void draw(Graphics2D g, String resourcePath, int x, int y, int size, Component obs, boolean shadow){
        BufferedImage img = get(resourcePath, size, shadow);
        if (img != null) {
            g.drawImage(img, x, y, obs);
        }
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

        // Progressive downscale for better sharpness when shrinking a lot
        BufferedImage current = src;
        int cw = srcW;
        int ch = srcH;
        while (cw / 2 >= target && ch / 2 >= target) {
            int nw = Math.max(target, cw / 2);
            int nh = Math.max(target, ch / 2);
            BufferedImage tmp = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = tmp.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
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
        g.drawImage(current, 0, 0, target, target, null);
        g.dispose();
        if (current != src) current.flush();
        return scaled;
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
        Image imgRaw = ResourceLoader.createImage("Simjot/" + path);
        if (imgRaw == null) return null;

        ImageIcon icon = new ImageIcon(imgRaw);
        int srcW = icon.getIconWidth();
        int srcH = icon.getIconHeight();
        if (srcW <= 0 || srcH <= 0) return null;
        BufferedImage src = new BufferedImage(srcW, srcH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gsrc = src.createGraphics();
        gsrc.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        gsrc.drawImage(imgRaw, 0, 0, null);
        gsrc.dispose();
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

    // Convenience mapping for common IDs used across the app
    public static String mapIdToResource(String id){
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
            // Widget buttons
            case "breathing_widget", "widget_breathing", "breath" -> iconSvg("Gauge-Dashboard");
            case "pomodoro_widget", "widget_pomodoro" -> iconSvg("Alarm-Bell-Ring");
            case "sticky_widget", "widget_sticky" -> iconSvg("Paper-Write");
            default -> null;
        };
    }
}
