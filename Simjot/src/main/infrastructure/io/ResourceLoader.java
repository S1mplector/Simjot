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
        return ResourceLoader.class.getResourceAsStream("/" + path);
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
