package main.ui.components.icons;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.ImageIcon;

import main.core.service.SettingsStore;
import main.infrastructure.io.ResourceLoader;
import main.ui.features.gallery.GeneratedWallpapers;
import main.ui.util.AccentColorUtil;

/**
 * Central PNG icon loader/renderer with high-quality scaling and in-memory caching.
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
    private static volatile Color sAccentTint = null;
    public static void setAccentTint(Color c) {
        int before = tintSignature();
        sAccentTint = c;
        int after = tintSignature();
        if (before != after) {
            CACHE.clear();
        }
    }

    public static void clearCaches() {
        sAccentTint = null;
        CACHE.clear();
    }
    private static int tintSignature() { Color c = sAccentTint; return c == null ? 0 : c.getRGB(); }

    public static BufferedImage get(String resourcePath, int size, boolean shadow){
        if (resourcePath == null) return null;
        // Normalize: allow callers to pass with or without leading "Simjot/"
        if (resourcePath.startsWith("Simjot/")) {
            resourcePath = resourcePath.substring("Simjot/".length());
        }
        ensureAccentTintInitialized();
        Key key = new Key(resourcePath, size, shadow, tintSignature());
        return CACHE.computeIfAbsent(key, k -> build(k.path, k.size, k.shadow));
    }

    public static void draw(Graphics2D g, String resourcePath, int x, int y, int size, Component obs, boolean shadow){
        BufferedImage img = get(resourcePath, size, shadow);
        if (img != null) {
            g.drawImage(img, x, y, obs);
        }
    }

    private static BufferedImage build(String path, int size, boolean withShadow){
        Image imgRaw = ResourceLoader.createImage("Simjot/" + path);
        if (imgRaw == null) return null;

        int target = Math.max(1, size);
        // Realize source into a BufferedImage
        ImageIcon icon = new ImageIcon(imgRaw);
        int srcW = icon.getIconWidth();
        int srcH = icon.getIconHeight();
        if (srcW <= 0 || srcH <= 0) return null;
        BufferedImage src = new BufferedImage(srcW, srcH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gsrc = src.createGraphics();
        gsrc.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        gsrc.drawImage(imgRaw, 0, 0, null);
        gsrc.dispose();

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

        // Final precise resize to exact target with bicubic
        BufferedImage scaled = new BufferedImage(target, target, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g.drawImage(current, 0, 0, target, target, null);
        g.dispose();
        if (current != src) current.flush();

        // Optional accent recolor (blue-hue -> accent hue)
        BufferedImage tinted = recolorToAccentIfEnabled(scaled);

        if (!withShadow) return tinted;
        BufferedImage shadow = new BufferedImage(target, target, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gSh = shadow.createGraphics();
        gSh.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        gSh.drawImage(tinted, 0, 0, null);
        gSh.setComposite(AlphaComposite.SrcAtop);
        gSh.setColor(new Color(0,0,0,80));
        gSh.fillRect(0, 0, target, target);
        gSh.dispose();

        BufferedImage out = new BufferedImage(target, target, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gOut = out.createGraphics();
        gOut.drawImage(shadow, 1, 1, null);
        gOut.drawImage(tinted, 0, 0, null);
        gOut.dispose();
        return out;
    }

    // Recolor by remapping pixel luminance to the accent hue (monochrome colorize),
    // preserving near-black strokes and near-white highlights.
    private static BufferedImage recolorToAccentIfEnabled(BufferedImage src){
        Color target = sAccentTint;
        if (target == null) return src;
        float[] tgtHSB = Color.RGBtoHSB(target.getRed(), target.getGreen(), target.getBlue(), null);
        float tgtHue = tgtHSB[0];
        float tgtSat = Math.max(0.35f, tgtHSB[1]); // ensure enough color presence

        int w = src.getWidth(), height = src.getHeight();
        BufferedImage out = new BufferedImage(w, height, BufferedImage.TYPE_INT_ARGB);
        for (int y=0; y<height; y++){
            for (int x=0; x<w; x++){
                int argb = src.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a == 0) { out.setRGB(x, y, 0); continue; }
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = (argb) & 0xFF;
                // Relative luminance (sRGB) to preserve shading
                float lum = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f;
                // Preserve near-black strokes and near-white highlights (often borders/gloss)
                if (lum < 0.06f || lum > 0.97f) { out.setRGB(x, y, argb); continue; }
                float v = clamp01(lum * 1.0f); // direct luminance -> value
                int rgb = Color.HSBtoRGB(tgtHue, tgtSat, v);
                out.setRGB(x, y, (a << 24) | (rgb & 0x00FFFFFF));
            }
        }
        return out;
    }

    private static float clamp01(float v){ return (v < 0f ? 0f : (v > 1f ? 1f : v)); }

    private static void ensureAccentTintInitialized(){
        if (sAccentTint != null) return;
        try {
            Color accent = AccentColorUtil.defaultAccent();
            try {
                int saved = SettingsStore.get().getWidgetAccentRGB();
                if (saved != Integer.MIN_VALUE) {
                    accent = new Color(saved, false);
                }
            } catch (Throwable ignored) {}
            String bgPath = SettingsStore.get().getBackgroundImage();
            if (bgPath != null && !bgPath.isEmpty()) {
                Image img = null;
                if (bgPath.startsWith("gen:")) {
                    img = GeneratedWallpapers.render(bgPath, 1280, 720);
                } else if (bgPath.startsWith("res:")) {
                    img = ResourceLoader.createImage("Simjot/" + bgPath.substring(4));
                } else {
                    img = new ImageIcon(bgPath).getImage();
                }
                if (img != null) accent = AccentColorUtil.extractAccent(img);
            }
            setAccentTint(accent);
        } catch (Throwable ignored) {}
    }

    // Convenience mapping for common IDs used across the app
    public static String mapIdToResource(String id){
        if (id == null) return null;
        return switch (id.toLowerCase()) {
            case "notebook" -> "img/icons/notebooks_mainmenu.png";
            case "smile" -> "img/icons/moodchart_mainmenu.png";
            case "chart", "stats", "analysis" -> "img/icons/list.png";
            case "wrench", "settings", "options" -> "img/icons/settings.png";
            case "trash" -> "img/icons/trash.png";
            case "new", "write", "plus" -> "img/icons/write.png";
            case "brush" -> "img/icons/brush.png";
            case "delete", "delete_entry" -> "img/icons/delete_entry.png";
            case "back" -> "img/icons/back.png";
            case "list" -> "img/icons/list.png";
            case "close" -> "img/icons/close.png";
            case "save" -> "img/icons/save.png";
            case "load" -> "img/icons/load.png";
            // View / window controls
            case "fullscreen", "enter_fullscreen", "toggle_fullscreen" -> "img/icons/fullscreen.png";
            case "export", "share", "download" -> "img/icons/export.png";
            case "rhyme", "rhymes", "rhyme_dock" -> "img/icons/rhyme.png";
            // Storage actions
            case "explorer", "open_in_explorer" -> "img/icons/explorer.png";
            case "refreshsizes", "refresh_sizes" -> "img/icons/refreshsizes.png";
            case "revealselected", "reveal_selected" -> "img/icons/revealselected.png";
            // Sim guidance
            case "sim_guidance", "simguidance", "guidance" -> "img/icons/simguidance.png";
            // Settings sidebar categories
            case "general_settings", "settings_general" -> "img/icons/general_settings.png";
            case "appearance_settings", "settings_appearance" -> "img/icons/appearance_settings.png";
            case "storage_settings", "settings_storage" -> "img/icons/storage_settings.png";
            case "sim_settings", "settings_sim" -> "img/icons/sim_settings.png";
            case "about_settings", "settings_about" -> "img/icons/about_settings.png";
            // Widget buttons
            case "breathing_widget", "widget_breathing" -> "img/icons/breathing_widget.png";
            case "pomodoro_widget", "widget_pomodoro" -> "img/icons/pomodoro_widget.png";
            case "sticky_widget", "widget_sticky" -> "img/icons/sticky_widget.png";
            default -> null;
        };
    }
}
