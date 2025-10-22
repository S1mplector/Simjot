package main.ui.theme;

import main.core.service.SettingsStore;

public final class Theme {
    private Theme() {}

    public static boolean isPlainWhite() {
        try {
            String t = SettingsStore.get().getTheme();
            if (t == null) return false;
            t = t.trim();
            if (t.equalsIgnoreCase("Plain White") || t.equalsIgnoreCase("Plain") || t.equalsIgnoreCase("White")) {
                return true;
            }
            // Backwards compatibility: treat legacy values as Aero
            // (Light/Dark -> Aero)
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isAero() {
        return !isPlainWhite();
    }
}
