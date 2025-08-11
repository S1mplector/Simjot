package main.ui.theme.aero;

import java.awt.Color;
import java.awt.Font;

public final class AeroTheme {
    private AeroTheme() {}

    // Core palette inspired by Windows 7 Aero
    public static final Color AERO_BLUE = new Color(0, 120, 215);
    public static final Color AERO_BLUE_LIGHT = new Color(153, 209, 255);
    public static final Color AERO_BLUE_DARK = new Color(0, 84, 153);

    public static final Color GLASS_TOP_HIGHLIGHT = new Color(255, 255, 255, 140);
    public static final Color GLASS_BOTTOM_SHADE = new Color(0, 0, 0, 40);

    public static final Color PANEL_BG_TOP = new Color(255, 255, 255, 180);
    public static final Color PANEL_BG_BOTTOM = new Color(230, 230, 230, 180);

    public static final Color BUTTON_BG_TOP = new Color(252, 252, 252);
    public static final Color BUTTON_BG_BOTTOM = new Color(231, 231, 231);
    public static final Color BUTTON_BORDER = new Color(190, 190, 190);

    public static final Color BUTTON_HOVER_TOP = new Color(240, 248, 255);
    public static final Color BUTTON_HOVER_BOTTOM = new Color(221, 236, 248);

    public static final Color BUTTON_PRESS_TOP = new Color(214, 228, 244);
    public static final Color BUTTON_PRESS_BOTTOM = new Color(199, 216, 235);
    
    // Primary text color: dark charcoal (not pure black) for better readability
    public static final Color TEXT_PRIMARY = new Color(43, 43, 43); // #2B2B2B
    public static final Color TEXT_LIGHT = new Color(255, 255, 255);

    public static Font defaultFont() {
        // Java will gracefully fallback if Segoe UI is missing
        return new Font("Segoe UI", Font.PLAIN, 12);
    }

    public static Font defaultBoldFont(float size) {
        return defaultFont().deriveFont(Font.BOLD, size);
    }
}
