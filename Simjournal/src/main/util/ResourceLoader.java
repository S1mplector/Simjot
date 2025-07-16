package main.util;

import java.awt.Image;
import java.io.InputStream;
import java.net.URL;
import javax.swing.ImageIcon;

public class ResourceLoader {

    public static URL getResource(String path) {
        // The leading slash is important, it signifies that the path is absolute from the root of the classpath.
        // We will strip any leading "Simjournal/" from the path.
        if (path.startsWith("Simjournal/")) {
            path = path.substring("Simjournal/".length());
        }
        URL url = ResourceLoader.class.getResource("/" + path);
        if (url != null) {
            return url;
        }

        // Fallback: look for the file on disk relative to the working directory or in the Simjournal folder
        java.io.File file = new java.io.File(path);
        if (!file.exists()) {
            // Try with "Simjournal/" prefix (useful when running from project root)
            file = new java.io.File("Simjournal/" + path);
        }
        try {
            return file.exists() ? file.toURI().toURL() : null;
        } catch (java.net.MalformedURLException e) {
            return null;
        }
    }

    public static InputStream getResourceAsStream(String path) {
        if (path.startsWith("Simjournal/")) {
            path = path.substring("Simjournal/".length());
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
