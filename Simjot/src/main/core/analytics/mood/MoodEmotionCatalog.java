/*
 * SIMJOT - MIT License
 *
 * Copyright (c) 2024-2025 Ilgaz Mehmetoglu.
 *
 * See LICENSE.md for full terms.
 */

package main.core.analytics.mood;

/**
 * Canonical emotion metadata used by mood analytics engines.
 */
public final class MoodEmotionCatalog {

    private static final String[] NAMES = {
            "Joy", "Calm", "Gratitude", "Energy",
            "Sadness", "Anger", "Anxiety", "Stress"
    };

    private static final boolean[] RESTORATIVE = {
            true, true, true, true,
            false, false, false, false
    };

    public static final int EMOTION_COUNT = NAMES.length;
    public static final int NEUTRAL_VALUE = 50;

    private MoodEmotionCatalog() {
    }

    public static int count() {
        return EMOTION_COUNT;
    }

    public static boolean isValidIndex(int index) {
        return index >= 0 && index < EMOTION_COUNT;
    }

    public static String emotionName(int index) {
        if (!isValidIndex(index)) {
            return "Emotion";
        }
        return NAMES[index];
    }

    public static boolean isRestorative(int index) {
        return isValidIndex(index) && RESTORATIVE[index];
    }

    public static boolean isChallenging(int index) {
        return isValidIndex(index) && !RESTORATIVE[index];
    }

    public static String[] namesCopy() {
        return NAMES.clone();
    }
}
