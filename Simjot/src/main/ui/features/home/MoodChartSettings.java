/*
 * SIMJOT - PROPRIETARY
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 * 
 * This source code is licensed under the Simjot Source-Available Personal Use License.
 * You may view and study this code for personal, non-commercial use.
 * Distribution, commercial use, and derivative works are strictly prohibited.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.home;

final class MoodChartSettings {
    private boolean showFill = true;
    private boolean showTrend = true;
    private boolean showEntryTicks = true;
    private int trendWindow = 7;

    boolean isShowFill() { return showFill; }
    void setShowFill(boolean v) { showFill = v; }

    boolean isShowTrend() { return showTrend; }
    void setShowTrend(boolean v) { showTrend = v; }

    boolean isShowEntryTicks() { return showEntryTicks; }
    void setShowEntryTicks(boolean v) { showEntryTicks = v; }

    int getTrendWindow() { return trendWindow; }
    void setTrendWindow(int w) { trendWindow = Math.max(1, w); }
}
