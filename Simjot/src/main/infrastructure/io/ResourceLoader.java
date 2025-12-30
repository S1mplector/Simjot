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

package main.infrastructure.io;

import java.awt.Image;
import java.io.InputStream;
import java.net.URL;
import javax.swing.ImageIcon;

public class ResourceLoader {

    public static URL getResource(String path) {
        // The leading slash is important, it signifies that the path is absolute from the root of the classpath.
        // We will strip any leading "Simjot/" from the path.
        if (path.startsWith("Simjot/")) {
            path = path.substring("Simjot/".length());
        }
        URL url = ResourceLoader.class.getResource("/" + path);
        if (url != null) {
            return url;
        }
        url = ResourceLoader.class.getResource("/resources/" + path);
        if (url != null) {
            return url;
        }
        url = ResourceLoader.class.getResource("/main/resources/" + path);
        if (url != null) {
            return url;
        }

        // Fallbacks for development environment where resources aren't copied to output:
        // 1) relative to working directory
        java.io.File file = new java.io.File(path);
        // 2) under project root: Simjot/<path>
        if (!file.exists()) file = new java.io.File("Simjot/" + path);
        // 3) typical Maven-style resources dir
        if (!file.exists()) file = new java.io.File("src/main/resources/" + path);
        // 4) typical within Simjot module
        if (!file.exists()) file = new java.io.File("Simjot/src/main/resources/" + path);
        try {
            return file.exists() ? file.toURI().toURL() : null;
        } catch (java.net.MalformedURLException e) {
            return null;
        }
    }

    public static InputStream getResourceAsStream(String path) {
        if (path.startsWith("Simjot/")) {
            path = path.substring("Simjot/".length());
        }
        // 1) Try classpath first
        InputStream in = ResourceLoader.class.getResourceAsStream("/" + path);
        if (in != null) return in;
        in = ResourceLoader.class.getResourceAsStream("/resources/" + path);
        if (in != null) return in;
        in = ResourceLoader.class.getResourceAsStream("/main/resources/" + path);
        if (in != null) return in;

        // 2) Fallbacks for development environment where resources aren't copied to output
        //    Try common filesystem locations mirroring getResource()
        java.io.File file = new java.io.File(path);
        if (!file.exists()) file = new java.io.File("Simjot/" + path);
        if (!file.exists()) file = new java.io.File("src/main/resources/" + path);
        if (!file.exists()) file = new java.io.File("Simjot/src/main/resources/" + path);
        try {
            return file.exists() ? new java.io.FileInputStream(file) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static ImageIcon createImageIcon(String path) {
        URL imgURL = getResource(path);
        if (imgURL != null) {
            return new ImageIcon(imgURL);
        } else {
            System.err.println("Couldn't find file: " + path);
            return null;
        }
    }
    
    public static Image createImage(String path) {
        ImageIcon icon = createImageIcon(path);
        if (icon != null) {
            return icon.getImage();
        }
        return null;
    }
}
