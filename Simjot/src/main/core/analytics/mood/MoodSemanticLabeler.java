/*
 * SIMJOT - MIT License
 *
 * Copyright (c) 2024-2025 Ilgaz Mehmetoglu.
 *
 * See LICENSE.md for full terms.
 */

package main.core.analytics.mood;

/**
 * Converts raw mood/emotion values into human-readable semantic labels.
 */
public final class MoodSemanticLabeler {

    public String labelComposite(double mood) {
        if (mood < 20) return "Very low";
        if (mood < 40) return "Low";
        if (mood < 60) return "Neutral";
        if (mood < 80) return "Good";
        return "Excellent";
    }

    public String labelEmotion(int emotionIndex, int value) {
        int v = clamp(value);
        int band = intensityBand(v);

        return switch (emotionIndex) {
            case 0 -> switch (band) {
                case 0 -> "Flat";
                case 1 -> "Muted";
                case 2 -> "Steady";
                case 3 -> "Bright";
                default -> "Radiant";
            };
            case 1 -> switch (band) {
                case 0 -> "Restless";
                case 1 -> "Uneasy";
                case 2 -> "Balanced";
                case 3 -> "Grounded";
                default -> "Serene";
            };
            case 2 -> switch (band) {
                case 0 -> "Numb";
                case 1 -> "Distant";
                case 2 -> "Aware";
                case 3 -> "Thankful";
                default -> "Deeply thankful";
            };
            case 3 -> switch (band) {
                case 0 -> "Drained";
                case 1 -> "Low";
                case 2 -> "Even";
                case 3 -> "Active";
                default -> "Energized";
            };
            case 4 -> switch (band) {
                case 0 -> "Light";
                case 1 -> "Tender";
                case 2 -> "Heavy";
                case 3 -> "Downcast";
                default -> "Deeply low";
            };
            case 5 -> switch (band) {
                case 0 -> "Composed";
                case 1 -> "Irritated";
                case 2 -> "Frustrated";
                case 3 -> "Intense";
                default -> "Fuming";
            };
            case 6 -> switch (band) {
                case 0 -> "At ease";
                case 1 -> "Alert";
                case 2 -> "Uneasy";
                case 3 -> "Elevated";
                default -> "Overwhelmed";
            };
            case 7 -> switch (band) {
                case 0 -> "Clear";
                case 1 -> "Loaded";
                case 2 -> "Pressured";
                case 3 -> "Tense";
                default -> "Overclocked";
            };
            default -> "Steady";
        };
    }

    public String labelWithName(int emotionIndex, int value) {
        return MoodEmotionCatalog.emotionName(emotionIndex) + " · " + labelEmotion(emotionIndex, value);
    }

    public int intensityPercent(int value) {
        return (int) Math.round(Math.max(0d, Math.min(100d, Math.abs(clamp(value) - 50d) * 2d)));
    }

    private int intensityBand(int value) {
        if (value < 20) return 0;
        if (value < 40) return 1;
        if (value < 60) return 2;
        if (value < 80) return 3;
        return 4;
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
