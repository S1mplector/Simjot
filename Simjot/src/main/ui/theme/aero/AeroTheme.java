package main.ui.theme.aero;

import java.awt.Color;
import java.awt.Font;

public final class AeroTheme {
    private AeroTheme() {}

    // Core palette inspired by Windows 7 Aero
    public static Color AERO_BLUE = new Color(0, 120, 215);
    public static Color AERO_BLUE_LIGHT = new Color(153, 209, 255);
    public static Color AERO_BLUE_DARK = new Color(0, 84, 153);

    public static Color GLASS_TOP_HIGHLIGHT = new Color(255, 255, 255, 140);
    public static Color GLASS_BOTTOM_SHADE = new Color(0, 0, 0, 40);

    public static Color PANEL_BG_TOP = new Color(255, 255, 255, 180);
    public static Color PANEL_BG_BOTTOM = new Color(230, 230, 230, 180);

    public static Color BUTTON_BG_TOP = new Color(252, 252, 252);
    public static Color BUTTON_BG_BOTTOM = new Color(231, 231, 231);
    public static Color BUTTON_BORDER = new Color(190, 190, 190);

    public static Color BUTTON_HOVER_TOP = new Color(240, 248, 255);
    public static Color BUTTON_HOVER_BOTTOM = new Color(221, 236, 248);

    public static Color BUTTON_PRESS_TOP = new Color(214, 228, 244);
    public static Color BUTTON_PRESS_BOTTOM = new Color(199, 216, 235);
    
    // Primary text color: dark charcoal (not pure black) for better readability
    public static Color TEXT_PRIMARY = new Color(43, 43, 43); // #2B2B2B
    public static Color TEXT_LIGHT = new Color(255, 255, 255);

    public static Font defaultFont() {
        // Java will gracefully fallback if Segoe UI is missing
        return new Font("Segoe UI", Font.PLAIN, 12);
    }

    public static Font defaultBoldFont(float size) {
        return defaultFont().deriveFont(Font.BOLD, size);
    }

    public static void applyVariant(main.ui.theme.Theme.Variant variant) {
        switch (variant) {
            case SEPIA -> {
                AERO_BLUE = new Color(174, 118, 74); // warm accent
                AERO_BLUE_LIGHT = new Color(206, 154, 116);
                AERO_BLUE_DARK = new Color(134, 86, 52);
                GLASS_TOP_HIGHLIGHT = new Color(255, 248, 240, 180);
                GLASS_BOTTOM_SHADE = new Color(80, 60, 40, 60);
                PANEL_BG_TOP = new Color(247, 239, 225, 200);
                PANEL_BG_BOTTOM = new Color(233, 221, 203, 200);
                BUTTON_BG_TOP = new Color(250, 243, 230);
                BUTTON_BG_BOTTOM = new Color(232, 217, 197);
                BUTTON_BORDER = new Color(196, 170, 140);
                BUTTON_HOVER_TOP = new Color(244, 229, 206);
                BUTTON_HOVER_BOTTOM = new Color(226, 206, 179);
                BUTTON_PRESS_TOP = new Color(220, 200, 172);
                BUTTON_PRESS_BOTTOM = new Color(205, 184, 156);
                TEXT_PRIMARY = new Color(52, 38, 26);
                TEXT_LIGHT = new Color(255, 255, 255);
            }
            case LIGHT -> {
                AERO_BLUE = new Color(0, 120, 215);
                AERO_BLUE_LIGHT = new Color(153, 209, 255);
                AERO_BLUE_DARK = new Color(0, 84, 153);
                GLASS_TOP_HIGHLIGHT = new Color(255, 255, 255, 140);
                GLASS_BOTTOM_SHADE = new Color(0, 0, 0, 40);
                PANEL_BG_TOP = new Color(255, 255, 255, 200);
                PANEL_BG_BOTTOM = new Color(242, 242, 242, 200);
                BUTTON_BG_TOP = new Color(252, 252, 252);
                BUTTON_BG_BOTTOM = new Color(231, 231, 231);
                BUTTON_BORDER = new Color(190, 190, 190);
                BUTTON_HOVER_TOP = new Color(240, 248, 255);
                BUTTON_HOVER_BOTTOM = new Color(221, 236, 248);
                BUTTON_PRESS_TOP = new Color(214, 228, 244);
                BUTTON_PRESS_BOTTOM = new Color(199, 216, 235);
                TEXT_PRIMARY = new Color(43, 43, 43);
                TEXT_LIGHT = new Color(255, 255, 255);
            }
            default -> {
                // Aero default (Windows 7-like)
                AERO_BLUE = new Color(0, 120, 215);
                AERO_BLUE_LIGHT = new Color(153, 209, 255);
                AERO_BLUE_DARK = new Color(0, 84, 153);
                GLASS_TOP_HIGHLIGHT = new Color(255, 255, 255, 140);
                GLASS_BOTTOM_SHADE = new Color(0, 0, 0, 40);
                PANEL_BG_TOP = new Color(255, 255, 255, 180);
                PANEL_BG_BOTTOM = new Color(230, 230, 230, 180);
                BUTTON_BG_TOP = new Color(252, 252, 252);
                BUTTON_BG_BOTTOM = new Color(231, 231, 231);
                BUTTON_BORDER = new Color(190, 190, 190);
                BUTTON_HOVER_TOP = new Color(240, 248, 255);
                BUTTON_HOVER_BOTTOM = new Color(221, 236, 248);
                BUTTON_PRESS_TOP = new Color(214, 228, 244);
                BUTTON_PRESS_BOTTOM = new Color(199, 216, 235);
                TEXT_PRIMARY = new Color(43, 43, 43);
                TEXT_LIGHT = new Color(255, 255, 255);
            }
        }
    }
}
