/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

#include "simjot_native.h"
#include <cmath>
#include <algorithm>
#include <vector>
#include <cstring>

// ═══════════════════════════════════════════════════════════════════════════
// MOOD ANALYTICS - Fast computations for mood data analysis
// ═══════════════════════════════════════════════════════════════════════════

extern "C" {

/**
 * Compute rolling average (smoothed values) for mood data.
 * Uses SIMD-friendly loop for performance.
 * 
 * @param values Input mood values (0-100, -1 for missing)
 * @param count Number of values
 * @param window Rolling window size
 * @param out_smoothed Output buffer (same size as values)
 * @return Number of non-null smoothed values
 */
int32_t simjot_mood_smooth(const int32_t* values, int32_t count, int32_t window, double* out_smoothed) {
    if (!values || !out_smoothed || count <= 0 || window <= 0) return 0;
    
    int32_t nonNullCount = 0;
    
    for (int32_t i = 0; i < count; i++) {
        int32_t start = std::max(0, i - window + 1);
        double sum = 0.0;
        int32_t validCount = 0;
        
        for (int32_t j = start; j <= i; j++) {
            if (values[j] >= 0) {
                sum += values[j];
                validCount++;
            }
        }
        
        if (validCount > 0) {
            out_smoothed[i] = sum / validCount;
            nonNullCount++;
        } else {
            out_smoothed[i] = -1.0; // Mark as missing
        }
    }
    
    return nonNullCount;
}

/**
 * Compute volatility (standard deviation) of mood values.
 * 
 * @param values Input mood values (0-100, -1 for missing)
 * @param count Number of values
 * @return Standard deviation, or -1 if insufficient data
 */
double simjot_mood_volatility(const int32_t* values, int32_t count) {
    if (!values || count < 2) return -1.0;
    
    // First pass: compute mean
    double sum = 0.0;
    int32_t validCount = 0;
    
    for (int32_t i = 0; i < count; i++) {
        if (values[i] >= 0) {
            sum += values[i];
            validCount++;
        }
    }
    
    if (validCount < 2) return -1.0;
    
    double mean = sum / validCount;
    
    // Second pass: compute variance
    double sumSq = 0.0;
    for (int32_t i = 0; i < count; i++) {
        if (values[i] >= 0) {
            double diff = values[i] - mean;
            sumSq += diff * diff;
        }
    }
    
    return std::sqrt(sumSq / (validCount - 1));
}

/**
 * Compute mood streaks.
 * 
 * @param values Input mood values (0-100, -1 for missing)
 * @param count Number of values
 * @param threshold Good/bad threshold (typically 50)
 * @param out_current Current streak (positive=good, negative=bad)
 * @param out_longest_good Longest good streak
 * @param out_longest_bad Longest bad streak
 * @return 1 on success, 0 on failure
 */
int32_t simjot_mood_streaks(const int32_t* values, int32_t count, int32_t threshold,
                            int32_t* out_current, int32_t* out_longest_good, int32_t* out_longest_bad) {
    if (!values || count <= 0 || !out_current || !out_longest_good || !out_longest_bad) return 0;
    
    int32_t longestGood = 0, longestBad = 0;
    int32_t runningGood = 0, runningBad = 0;
    
    for (int32_t i = 0; i < count; i++) {
        int32_t v = values[i];
        
        if (v < 0) {
            // Gap breaks streak
            longestGood = std::max(longestGood, runningGood);
            longestBad = std::max(longestBad, runningBad);
            runningGood = 0;
            runningBad = 0;
        } else if (v >= threshold) {
            runningGood++;
            longestBad = std::max(longestBad, runningBad);
            runningBad = 0;
        } else {
            runningBad++;
            longestGood = std::max(longestGood, runningGood);
            runningGood = 0;
        }
    }
    
    // Final check
    longestGood = std::max(longestGood, runningGood);
    longestBad = std::max(longestBad, runningBad);
    
    // Current streak from most recent
    int32_t current = 0;
    if (count > 0) {
        int32_t last = values[count - 1];
        if (last >= 0) {
            current = last >= threshold ? runningGood : -runningBad;
        }
    }
    
    *out_current = current;
    *out_longest_good = longestGood;
    *out_longest_bad = longestBad;
    
    return 1;
}

/**
 * Compute daily aggregates from timestamped mood samples.
 * Assumes samples are sorted by timestamp.
 * 
 * @param timestamps Array of timestamps (days since epoch)
 * @param values Array of mood values (0-100)
 * @param count Number of samples
 * @param out_days Output array for unique days
 * @param out_averages Output array for daily averages
 * @param max_days Maximum number of days to output
 * @return Number of unique days found
 */
int32_t simjot_mood_daily_aggregate(const int32_t* timestamps, const int32_t* values,
                                     int32_t count, int32_t* out_days, double* out_averages,
                                     int32_t max_days) {
    if (!timestamps || !values || count <= 0 || !out_days || !out_averages || max_days <= 0) return 0;
    
    int32_t dayCount = 0;
    int32_t currentDay = -1;
    double daySum = 0.0;
    int32_t daySamples = 0;
    
    for (int32_t i = 0; i < count; i++) {
        int32_t day = timestamps[i];
        int32_t val = values[i];
        
        if (val < 0) continue; // Skip invalid
        
        if (day != currentDay) {
            // Save previous day's aggregate
            if (currentDay >= 0 && daySamples > 0 && dayCount < max_days) {
                out_days[dayCount] = currentDay;
                out_averages[dayCount] = daySum / daySamples;
                dayCount++;
            }
            
            // Start new day
            currentDay = day;
            daySum = val;
            daySamples = 1;
        } else {
            daySum += val;
            daySamples++;
        }
    }
    
    // Don't forget last day
    if (currentDay >= 0 && daySamples > 0 && dayCount < max_days) {
        out_days[dayCount] = currentDay;
        out_averages[dayCount] = daySum / daySamples;
        dayCount++;
    }
    
    return dayCount;
}

/**
 * Compute trend slope using simple linear regression.
 * 
 * @param values Mood values (0-100, -1 for missing)
 * @param count Number of values
 * @return Slope (positive = improving, negative = declining), or NaN if insufficient data
 */
double simjot_mood_trend_slope(const int32_t* values, int32_t count) {
    if (!values || count < 2) return NAN;
    
    // Collect valid points
    std::vector<double> x, y;
    for (int32_t i = 0; i < count; i++) {
        if (values[i] >= 0) {
            x.push_back(static_cast<double>(i));
            y.push_back(static_cast<double>(values[i]));
        }
    }
    
    int32_t n = static_cast<int32_t>(x.size());
    if (n < 2) return NAN;
    
    // Compute means
    double sumX = 0, sumY = 0;
    for (int32_t i = 0; i < n; i++) {
        sumX += x[i];
        sumY += y[i];
    }
    double meanX = sumX / n;
    double meanY = sumY / n;
    
    // Compute slope: sum((x-meanX)(y-meanY)) / sum((x-meanX)^2)
    double num = 0, denom = 0;
    for (int32_t i = 0; i < n; i++) {
        double dx = x[i] - meanX;
        double dy = y[i] - meanY;
        num += dx * dy;
        denom += dx * dx;
    }
    
    if (std::abs(denom) < 1e-10) return 0.0;
    
    return num / denom;
}

/**
 * Find optimal mood prediction using exponential smoothing.
 * 
 * @param values Mood values (0-100, -1 for missing)
 * @param count Number of values
 * @param alpha Smoothing factor (0-1, higher = more weight on recent)
 * @return Predicted next value, or -1 if insufficient data
 */
double simjot_mood_predict_next(const int32_t* values, int32_t count, double alpha) {
    if (!values || count <= 0 || alpha <= 0 || alpha > 1) return -1.0;
    
    double smoothed = -1.0;
    bool started = false;
    
    for (int32_t i = 0; i < count; i++) {
        if (values[i] >= 0) {
            if (!started) {
                smoothed = values[i];
                started = true;
            } else {
                smoothed = alpha * values[i] + (1 - alpha) * smoothed;
            }
        }
    }
    
    return smoothed;
}

/**
 * Categorize mood distribution into buckets.
 * 
 * @param values Mood values (0-100, -1 for missing)
 * @param count Number of values
 * @param out_buckets Output array of 5 buckets (0-20, 20-40, 40-60, 60-80, 80-100)
 * @return Total number of valid samples
 */
int32_t simjot_mood_distribution(const int32_t* values, int32_t count, int32_t* out_buckets) {
    if (!values || count <= 0 || !out_buckets) return 0;
    
    std::memset(out_buckets, 0, 5 * sizeof(int32_t));
    int32_t total = 0;
    
    for (int32_t i = 0; i < count; i++) {
        int32_t v = values[i];
        if (v < 0) continue;
        
        int bucket = std::min(4, v / 20);
        out_buckets[bucket]++;
        total++;
    }
    
    return total;
}

} // extern "C"
