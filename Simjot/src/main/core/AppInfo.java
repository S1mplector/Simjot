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
package main.core;

/**
 * Centralized application information.
 * Single source of truth for version, name, and other app metadata.
 */
public final class AppInfo {
    private AppInfo() {}

    public static final String NAME = "Simjot";
    public static final String VERSION = "0.1.0";
    public static final String AUTHOR = "Ilgaz Mehmetoglu";
    public static final String LICENSE = "Source-Available Personal Use License";
    
    /**
     * Returns formatted version string (e.g., "v1.0.0")
     */
    public static String versionString() {
        return "v" + VERSION;
    }
    
    /**
     * Returns full application title with version (e.g., "Simjot v1.0.0")
     */
    public static String fullTitle() {
        return NAME + " " + versionString();
    }
}
