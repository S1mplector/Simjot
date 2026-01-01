/*
 * SIMJOT - MIT License
 *
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 *
 * See LICENSE.md for full terms.
 */

package main.ui.components.scrollbar;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

import javax.swing.JViewport;

import main.infrastructure.ffi.NativeAccess;

/**
 * A JViewport that uses native C++ offscreen buffering for smooth scrolling.
 * 
 * This viewport maintains a native pixel buffer that caches rendered content.
 * During scrolling, it uses efficient native memmove operations to shift the
 * buffer contents and only repaints the newly exposed areas.
 * 
 * Falls back to standard JViewport behavior if native support is unavailable.
 * 
 * @author S1mplector
 */
public class NativeBufferedViewport extends JViewport {
    
    private long nativeBuffer = 0;
    private int bufferWidth = 0;
    private int bufferHeight = 0;
    private Point lastViewPosition = new Point(0, 0);
    private boolean nativeEnabled;
    private BufferedImage transferImage; // For pixel transfer between Java and native
    private int backgroundColor = 0xFFFFFFFF; // Default white, fully opaque
    
    public NativeBufferedViewport() {
        super();
        this.nativeEnabled = NativeAccess.hasBufferSupport();
        setScrollMode(SIMPLE_SCROLL_MODE); // We handle buffering ourselves
    }
    
    /**
     * Set the background color used to fill exposed areas during scroll.
     * @param argb Color in 0xAARRGGBB format
     */
    public void setBufferBackground(int argb) {
        this.backgroundColor = argb;
    }
    
    /**
     * Check if native buffering is active.
     */
    public boolean isNativeBufferingEnabled() {
        return nativeEnabled && nativeBuffer != 0;
    }
    
    @Override
    public void reshape(int x, int y, int w, int h) {
        super.reshape(x, y, w, h);
        ensureBuffer(w, h);
    }
    
    @Override
    public void setViewPosition(Point p) {
        if (!nativeEnabled || nativeBuffer == 0) {
            super.setViewPosition(p);
            return;
        }
        
        Point oldPos = getViewPosition();
        int dx = p.x - oldPos.x;
        int dy = p.y - oldPos.y;
        
        // Let the parent update the position
        super.setViewPosition(p);
        
        // If we have a valid buffer and scrolled, do native scroll optimization
        if (nativeBuffer != 0 && (dx != 0 || dy != 0)) {
            // Scroll the native buffer
            NativeAccess.bufferScroll(nativeBuffer, dx, dy, backgroundColor);
            
            // Mark only the newly exposed areas as needing repaint
            Rectangle viewRect = getViewRect();
            
            if (dy != 0) {
                // Vertical scroll - repaint top or bottom strip
                int stripHeight = Math.min(Math.abs(dy), viewRect.height);
                if (dy > 0) {
                    // Scrolled down - repaint bottom
                    repaint(0, viewRect.height - stripHeight, viewRect.width, stripHeight);
                } else {
                    // Scrolled up - repaint top
                    repaint(0, 0, viewRect.width, stripHeight);
                }
            }
            
            if (dx != 0) {
                // Horizontal scroll - repaint left or right strip
                int stripWidth = Math.min(Math.abs(dx), viewRect.width);
                if (dx > 0) {
                    // Scrolled right - repaint right
                    repaint(viewRect.width - stripWidth, 0, stripWidth, viewRect.height);
                } else {
                    // Scrolled left - repaint left
                    repaint(0, 0, stripWidth, viewRect.height);
                }
            }
        }
        
        lastViewPosition = p;
    }
    
    @Override
    public void paint(Graphics g) {
        if (!nativeEnabled || nativeBuffer == 0) {
            super.paint(g);
            return;
        }
        
        // Ensure buffer size matches viewport
        int w = getWidth();
        int h = getHeight();
        if (w != bufferWidth || h != bufferHeight) {
            ensureBuffer(w, h);
        }
        
        // Paint the view into our transfer image
        if (transferImage == null || transferImage.getWidth() != w || transferImage.getHeight() != h) {
            transferImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        }
        
        Graphics2D g2 = transferImage.createGraphics();
        try {
            // Clear with background
            g2.setColor(getBackground());
            g2.fillRect(0, 0, w, h);
            
            // Paint the child component
            Component view = getView();
            if (view != null) {
                Point viewPos = getViewPosition();
                g2.translate(-viewPos.x, -viewPos.y);
                view.paint(g2);
            }
        } finally {
            g2.dispose();
        }
        
        // Copy to native buffer
        int[] pixels = ((DataBufferInt) transferImage.getRaster().getDataBuffer()).getData();
        NativeAccess.bufferWrite(nativeBuffer, pixels, w, h, 0, 0);
        
        // Blit from native buffer to screen (through transfer image)
        // In this case we already have the pixels in transferImage, so just draw it
        g.drawImage(transferImage, 0, 0, null);
    }
    
    @Override
    protected void paintChildren(Graphics g) {
        // Children are painted as part of the buffered paint() above
        if (!nativeEnabled || nativeBuffer == 0) {
            super.paintChildren(g);
        }
    }
    
    private void ensureBuffer(int width, int height) {
        if (!nativeEnabled) return;
        if (width <= 0 || height <= 0) return;
        
        if (nativeBuffer == 0) {
            // Create new buffer
            nativeBuffer = NativeAccess.bufferCreate(width, height);
            if (nativeBuffer != 0) {
                bufferWidth = width;
                bufferHeight = height;
                NativeAccess.bufferClear(nativeBuffer, backgroundColor);
            }
        } else if (width != bufferWidth || height != bufferHeight) {
            // Resize existing buffer
            if (NativeAccess.bufferResize(nativeBuffer, width, height)) {
                bufferWidth = width;
                bufferHeight = height;
            } else {
                // Resize failed, recreate
                NativeAccess.bufferDestroy(nativeBuffer);
                nativeBuffer = NativeAccess.bufferCreate(width, height);
                if (nativeBuffer != 0) {
                    bufferWidth = width;
                    bufferHeight = height;
                    NativeAccess.bufferClear(nativeBuffer, backgroundColor);
                }
            }
        }
    }
    
    /**
     * Force a full repaint of the buffer (e.g., after content changes).
     */
    public void invalidateBuffer() {
        if (nativeBuffer != 0) {
            NativeAccess.bufferClear(nativeBuffer, backgroundColor);
        }
        repaint();
    }
    
    /**
     * Release native resources. Called automatically when the viewport is removed.
     */
    public void dispose() {
        if (nativeBuffer != 0) {
            NativeAccess.bufferDestroy(nativeBuffer);
            nativeBuffer = 0;
        }
        transferImage = null;
    }
    
    @Override
    public void removeNotify() {
        dispose();
        super.removeNotify();
    }
    
    /**
     * Create a JScrollPane with this native-buffered viewport.
     */
    public static javax.swing.JScrollPane createScrollPane(Component view) {
        NativeBufferedViewport viewport = new NativeBufferedViewport();
        viewport.setView(view);
        
        javax.swing.JScrollPane scrollPane = new javax.swing.JScrollPane();
        scrollPane.setViewport(viewport);
        return scrollPane;
    }
    
    /**
     * Install a native-buffered viewport on an existing scroll pane.
     * Returns the viewport for further configuration.
     */
    public static NativeBufferedViewport install(javax.swing.JScrollPane scrollPane) {
        NativeBufferedViewport viewport = new NativeBufferedViewport();
        Component oldView = null;
        
        if (scrollPane.getViewport() != null) {
            oldView = scrollPane.getViewport().getView();
        }
        
        scrollPane.setViewport(viewport);
        
        if (oldView != null) {
            viewport.setView(oldView);
        }
        
        return viewport;
    }
}
