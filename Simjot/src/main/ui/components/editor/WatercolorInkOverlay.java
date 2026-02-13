/*
 * SIMJOT - MIT License
 *
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 *
 * See LICENSE.md for full terms.
 */

package main.ui.components.editor;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.event.ChangeListener;

/**
 * Lightweight overlay that paints organic watercolor-like strokes over an editor viewport.
 * Coordinates are stored in document space so strokes stay aligned while scrolling.
 */
public final class WatercolorInkOverlay extends JComponent {
    private static final int MAGIC = 0x57434C52; // WCLR
    private static final int VERSION = 1;
    private static final int MAX_STROKES = 4096;
    private static final float MIN_SAMPLE_DIST = 0.45f;
    private static final int MIN_SAMPLE_DT_MS = 3;

    private final JViewport viewport;
    private final ChangeListener viewportListener = e -> repaint();
    private boolean viewportListenerInstalled = false;

    private final List<WatercolorStroke> strokes = new ArrayList<>();
    private WatercolorStroke activeStroke;

    private boolean drawingEnabled = false;
    private Color strokeColor = new Color(24, 24, 24, 148);
    private float brushSize = 26f;
    private long strokeSeedCounter = 1L;

    public WatercolorInkOverlay(JScrollPane hostScrollPane) {
        if (hostScrollPane == null) throw new IllegalArgumentException("hostScrollPane is required");
        this.viewport = hostScrollPane.getViewport();
        setOpaque(false);
        setDoubleBuffered(true);
        installPointerHandlers();
        installViewportListener();
    }

    private void installPointerHandlers() {
        MouseAdapter pointer = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!drawingEnabled || e.getButton() != MouseEvent.BUTTON1) return;
                startStroke(e);
                e.consume();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!drawingEnabled || activeStroke == null) return;
                appendStrokePoint(e);
                e.consume();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) return;
                if (drawingEnabled && activeStroke != null) {
                    appendStrokePoint(e);
                }
                finishActiveStroke();
                e.consume();
            }
        };
        addMouseListener(pointer);
        addMouseMotionListener(pointer);
    }

    @Override
    public boolean contains(int x, int y) {
        // Let underlying editor handle pointer input while brush mode is off.
        return drawingEnabled && super.contains(x, y);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        installViewportListener();
    }

    @Override
    public void removeNotify() {
        uninstallViewportListener();
        super.removeNotify();
    }

    private void installViewportListener() {
        if (viewport == null || viewportListenerInstalled) return;
        viewport.addChangeListener(viewportListener);
        viewportListenerInstalled = true;
    }

    private void uninstallViewportListener() {
        if (viewport == null || !viewportListenerInstalled) return;
        try { viewport.removeChangeListener(viewportListener); } catch (Throwable ignored) {}
        viewportListenerInstalled = false;
    }

    public void setDrawingEnabled(boolean enabled) {
        if (this.drawingEnabled == enabled) return;
        this.drawingEnabled = enabled;
        if (!enabled) {
            finishActiveStroke();
        }
        setCursor(enabled ? Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR) : Cursor.getDefaultCursor());
        repaint();
    }

    public boolean isDrawingEnabled() {
        return drawingEnabled;
    }

    public void setStrokeColor(Color strokeColor) {
        if (strokeColor == null) return;
        this.strokeColor = strokeColor;
    }

    public void setBrushSize(float brushSize) {
        this.brushSize = Math.max(8f, Math.min(80f, brushSize));
    }

    public boolean hasStrokes() {
        return !strokes.isEmpty();
    }

    public void clearStrokes() {
        activeStroke = null;
        strokes.clear();
        repaint();
    }

    public byte[] serialize() throws IOException {
        finishActiveStroke();
        ByteArrayOutputStream baos = new ByteArrayOutputStream(16 * 1024);
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(baos))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(strokes.size());
            for (WatercolorStroke stroke : strokes) {
                out.writeInt(stroke.color.getRGB());
                out.writeFloat(stroke.baseBrushSize);
                out.writeLong(stroke.seed);
                out.writeInt(stroke.points.size());
                for (InkPoint point : stroke.points) {
                    out.writeFloat(point.x);
                    out.writeFloat(point.y);
                    out.writeFloat(point.flow);
                    out.writeLong(point.timeMs);
                }
            }
            out.flush();
        }
        return baos.toByteArray();
    }

    public void deserialize(byte[] data) {
        activeStroke = null;
        strokes.clear();
        if (data == null || data.length == 0) {
            repaint();
            return;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new ByteArrayInputStream(data)))) {
            int magic = in.readInt();
            int version = in.readInt();
            if (magic != MAGIC || version != VERSION) {
                repaint();
                return;
            }
            int strokeCount = Math.max(0, Math.min(MAX_STROKES, in.readInt()));
            for (int i = 0; i < strokeCount; i++) {
                Color color = new Color(in.readInt(), true);
                float baseSize = Math.max(8f, Math.min(80f, in.readFloat()));
                long seed = in.readLong();
                int pointCount = Math.max(0, Math.min(200_000, in.readInt()));
                WatercolorStroke stroke = new WatercolorStroke(color, baseSize, seed);
                for (int p = 0; p < pointCount; p++) {
                    float x = in.readFloat();
                    float y = in.readFloat();
                    float flow = clamp(in.readFloat(), 0.3f, 1.6f);
                    long timeMs = in.readLong();
                    stroke.addPointRaw(x, y, timeMs, flow);
                }
                if (!stroke.points.isEmpty()) {
                    stroke.invalidateCache();
                    strokes.add(stroke);
                }
            }
        } catch (IOException ignored) {
            strokes.clear();
        }
        repaint();
    }

    private void startStroke(MouseEvent e) {
        finishActiveStroke();
        activeStroke = new WatercolorStroke(strokeColor, brushSize, strokeSeedCounter++);
        appendStrokePoint(e);
    }

    private void appendStrokePoint(MouseEvent e) {
        if (activeStroke == null) return;
        Point viewPos = viewport != null ? viewport.getViewPosition() : new Point();
        float docX = e.getX() + viewPos.x;
        float docY = e.getY() + viewPos.y;
        long nowMs = System.nanoTime() / 1_000_000L;
        Rectangle dirtyDoc = activeStroke.addInterpolatedPoint(docX, docY, nowMs);
        if (dirtyDoc != null) {
            repaintDocumentRect(dirtyDoc, viewPos);
        } else {
            repaint();
        }
    }

    private void finishActiveStroke() {
        if (activeStroke == null) return;
        if (!activeStroke.points.isEmpty()) {
            if (strokes.size() >= MAX_STROKES) {
                strokes.remove(0);
            }
            activeStroke.invalidateCache();
            strokes.add(activeStroke);
        }
        activeStroke = null;
        repaint();
    }

    private void repaintDocumentRect(Rectangle docRect, Point viewPos) {
        if (docRect == null) return;
        int x = docRect.x - viewPos.x - 2;
        int y = docRect.y - viewPos.y - 2;
        int w = docRect.width + 4;
        int h = docRect.height + 4;
        repaint(x, y, w, h);
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (strokes.isEmpty() && activeStroke == null) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        Point viewPos = viewport != null ? viewport.getViewPosition() : new Point();
        Rectangle clip = g2.getClipBounds();
        for (WatercolorStroke stroke : strokes) {
            stroke.paint(g2, viewPos.x, viewPos.y, clip, false);
        }
        if (activeStroke != null) {
            activeStroke.paint(g2, viewPos.x, viewPos.y, clip, true);
        }
        g2.dispose();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float lerp(float a, float b, float t) {
        return a + ((b - a) * t);
    }

    private static float pseudoRandom01(long seed) {
        long x = seed;
        x ^= (x >>> 33);
        x *= 0xff51afd7ed558ccdL;
        x ^= (x >>> 33);
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= (x >>> 33);
        long bits = x & 0x00FFFFFFL;
        return bits / (float) 0x01000000L;
    }

    private static final class InkPoint {
        private final float x;
        private final float y;
        private final long timeMs;
        private final float flow;

        private InkPoint(float x, float y, long timeMs, float flow) {
            this.x = x;
            this.y = y;
            this.timeMs = timeMs;
            this.flow = flow;
        }
    }

    private static final class WatercolorStroke {
        private final Color color;
        private final float baseBrushSize;
        private final long seed;
        private final List<InkPoint> points = new ArrayList<>(256);

        private BufferedImage cacheImage;
        private Rectangle cacheDocBounds;
        private boolean cacheDirty = true;

        private float minX = Float.MAX_VALUE;
        private float minY = Float.MAX_VALUE;
        private float maxX = Float.MIN_VALUE;
        private float maxY = Float.MIN_VALUE;

        private WatercolorStroke(Color color, float baseBrushSize, long seed) {
            this.color = color == null ? new Color(24, 24, 24, 148) : color;
            this.baseBrushSize = baseBrushSize;
            this.seed = seed;
        }

        private void addPointRaw(float x, float y, long timeMs, float flow) {
            points.add(new InkPoint(x, y, timeMs, clamp(flow, 0.3f, 1.6f)));
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            cacheDirty = true;
        }

        private Rectangle addInterpolatedPoint(float x, float y, long timeMs) {
            if (points.isEmpty()) {
                addPointRaw(x, y, timeMs, 1.05f);
                return pointDirtyRect(x, y, 1.05f);
            }

            InkPoint prev = points.get(points.size() - 1);
            float dx = x - prev.x;
            float dy = y - prev.y;
            float dist = (float) Math.hypot(dx, dy);
            long dt = Math.max(1L, timeMs - prev.timeMs);

            if (dist < MIN_SAMPLE_DIST && dt < MIN_SAMPLE_DT_MS) {
                return null;
            }

            int steps = Math.max(1, (int) Math.ceil(dist / Math.max(0.85f, baseBrushSize * 0.12f)));
            float velocity = dist / Math.max(1f, (float) dt);
            float targetFlow = clamp(1.3f - velocity * 0.24f, 0.56f, 1.35f);

            Rectangle dirty = null;
            for (int i = 1; i <= steps; i++) {
                float t = i / (float) steps;
                float px = lerp(prev.x, x, t);
                float py = lerp(prev.y, y, t);
                long tm = prev.timeMs + Math.round((timeMs - prev.timeMs) * t);
                float flow = lerp(prev.flow, targetFlow, t);
                addPointRaw(px, py, tm, flow);
                Rectangle pointRect = pointDirtyRect(px, py, flow);
                dirty = (dirty == null) ? pointRect : dirty.union(pointRect);
            }
            return dirty;
        }

        private Rectangle pointDirtyRect(float x, float y, float flow) {
            float radius = Math.max(2f, baseBrushSize * 0.58f * flow);
            int pad = Math.max(8, Math.round(radius * 2.5f));
            int left = Math.round(x) - pad;
            int top = Math.round(y) - pad;
            int side = pad * 2;
            return new Rectangle(left, top, side, side);
        }

        private Rectangle getBoundsDoc() {
            if (points.isEmpty()) return new Rectangle();
            int pad = Math.max(16, Math.round(baseBrushSize * 2.4f));
            int left = Math.round(minX) - pad;
            int top = Math.round(minY) - pad;
            int right = Math.round(maxX) + pad;
            int bottom = Math.round(maxY) + pad;
            return new Rectangle(left, top, Math.max(1, right - left), Math.max(1, bottom - top));
        }

        private void invalidateCache() {
            cacheDirty = true;
        }

        private void ensureCache() {
            if (!cacheDirty && cacheImage != null && cacheDocBounds != null) return;
            Rectangle bounds = getBoundsDoc();
            if (bounds.width <= 0 || bounds.height <= 0) {
                cacheImage = null;
                cacheDocBounds = null;
                cacheDirty = false;
                return;
            }
            BufferedImage image = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            paintInternal(g2, -bounds.x, -bounds.y);
            g2.dispose();
            cacheImage = image;
            cacheDocBounds = bounds;
            cacheDirty = false;
        }

        private void paint(Graphics2D g2, int viewX, int viewY, Rectangle clip, boolean forceLive) {
            if (points.isEmpty()) return;
            if (forceLive) {
                paintInternal(g2, -viewX, -viewY);
                return;
            }
            ensureCache();
            if (cacheImage == null || cacheDocBounds == null) return;
            int dx = cacheDocBounds.x - viewX;
            int dy = cacheDocBounds.y - viewY;
            if (clip != null && !clip.intersects(dx, dy, cacheImage.getWidth(), cacheImage.getHeight())) {
                return;
            }
            g2.drawImage(cacheImage, dx, dy, null);
        }

        private void paintInternal(Graphics2D g2, int offsetX, int offsetY) {
            if (points.isEmpty()) return;
            if (points.size() == 1) {
                InkPoint p = points.get(0);
                paintDaub(g2, p.x + offsetX, p.y + offsetY, p.flow, 0);
                return;
            }

            int stampIndex = 0;
            for (int i = 1; i < points.size(); i++) {
                InkPoint a = points.get(i - 1);
                InkPoint b = points.get(i);
                float dx = b.x - a.x;
                float dy = b.y - a.y;
                float dist = (float) Math.hypot(dx, dy);
                int steps = Math.max(1, (int) Math.ceil(dist / Math.max(0.9f, baseBrushSize * 0.10f)));
                for (int s = 0; s <= steps; s++) {
                    float t = s / (float) steps;
                    float x = lerp(a.x, b.x, t) + offsetX;
                    float y = lerp(a.y, b.y, t) + offsetY;
                    float flow = lerp(a.flow, b.flow, t);
                    paintDaub(g2, x, y, flow, stampIndex++);
                }
            }
        }

        private void paintDaub(Graphics2D g2, float x, float y, float flow, int stampIndex) {
            float radius = Math.max(2.5f, baseBrushSize * 0.56f * flow);
            float baseAlpha = clamp(color.getAlpha() / 255f, 0.1f, 1f);
            int r = color.getRed();
            int g = color.getGreen();
            int b = color.getBlue();

            final float[] scales = {1.60f, 1.08f, 0.68f};
            final float[] alphaMul = {0.18f, 0.26f, 0.33f};

            for (int layer = 0; layer < scales.length; layer++) {
                long layerSeed = seed + (long) stampIndex * 131 + (layer * 977L);
                float jitterX = (pseudoRandom01(layerSeed) - 0.5f) * radius * 0.60f;
                float jitterY = (pseudoRandom01(layerSeed + 37L) - 0.5f) * radius * 0.60f;
                float scaleJitter = 0.84f + (pseudoRandom01(layerSeed + 73L) * 0.34f);
                float rr = radius * scales[layer] * scaleJitter;

                float alpha = clamp(baseAlpha * alphaMul[layer], 0.015f, 0.40f);
                g2.setComposite(AlphaComposite.SrcOver.derive(alpha));
                g2.setColor(new Color(r, g, b));
                int left = Math.round(x + jitterX - rr);
                int top = Math.round(y + jitterY - rr);
                int size = Math.max(1, Math.round(rr * 2f));
                g2.fillOval(left, top, size, size);
            }
            g2.setComposite(AlphaComposite.SrcOver);
        }
    }
}
