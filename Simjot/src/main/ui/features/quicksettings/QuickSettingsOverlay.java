package main.ui.features.quicksettings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Transparent overlay panel that renders animated category orbs and a
 * contextual settings panel to the right after the spawn sequence completes.
 * 
 * Tweaks:
 * - Increased spacing between rightmost orb and panel
 * - Reverse dismissal animation for orbs and menu
 * - Translucent rounded background behind orbs+menu with appear/disappear animations
 * - Settings panel fade in/out
 * - Refactored wrapPanel into a fade-capable panel
 */
public class QuickSettingsOverlay extends JComponent {

    private final List<QuickSettingsCategory> categories;
    private final Point spawnPoint; // where middle click occurred (in layeredPane coordinates)
    private final Runnable onClose;

    // Animation
    private final List<Orb> orbs = new ArrayList<>();
    private int nextSpawnIndex = 0;
    private long startMillis;
    private final Timer timer;

    // Visual layout
    private final int radius = 84; // increased orbit radius for more spacing
    private final int spawnIntervalMs = 180; // ms between orb spawns (slightly faster)
    private final int orbDiameter = 34; // larger orbs

    // Menu panel for the currently selected category
    private FadePanel menuPanel;
    private int selectedIndex = 0;
    private boolean menuShown = false;
    private Rectangle menuBoundsCache = new Rectangle(0,0,0,0);

    // Final layout
    private Point[] finalPositions;

    // Dismissal animation (reverse of spawn)
    private boolean dismissing = false;
    private float closeT = 0f;               // 0..1 progress of dismissal
    private final float closeDuration = 0.26f; // seconds
    private List<Point> closeStartPositions;  // captured when dismissal begins

    // Backdrop animation (rounded translucent background behind orbs+menu)
    private float backdropAlpha = 0f;         // 0..1
    private final float backdropFadeSpeed = 6f; // per second, smoother

    // Hover state
    private int hoverIndex = -1;

    public QuickSettingsOverlay(List<QuickSettingsCategory> categories, Point spawnPoint, Runnable onClose) {
        this.categories = categories;
        this.spawnPoint = new Point(spawnPoint);
        this.onClose = onClose;

        setOpaque(false);
        setFocusable(true);
        computeFinalLayout();

        // Input handling
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    close();
                    return;
                }
                // Click: select orb or click-outside closes
                int idx = orbAt(e.getPoint());
                if (idx >= 0) {
                    selectCategory(idx);
                } else if (menuShown && menuBoundsCache.contains(e.getPoint())) {
                    // clicked inside menu – let components handle
                } else {
                    close();
                }
            }
        });

        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int idx = orbAt(e.getPoint());
                if (idx != hoverIndex) {
                    hoverIndex = idx;
                    repaint();
                }
            }
        });

        // ESC closes
        registerKeyboardAction(e -> close(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        // Left/Right to switch category
        registerKeyboardAction(e -> switchCategory(-1),
                KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        registerKeyboardAction(e -> switchCategory(1),
                KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        timer = new Timer(16, e -> onTick());
    }

    public void start() {
        startMillis = System.currentTimeMillis();
        timer.start();
        requestFocusInWindow();
    }

    private void onTick() {
        long elapsed = System.currentTimeMillis() - startMillis;

        float dt = 16f / 1000f;

        if (!dismissing) {
            // Spawn next orb strictly one-by-one: only after previous has settled
            boolean canSpawn = nextSpawnIndex < categories.size()
                    && elapsed >= nextSpawnIndex * (long) spawnIntervalMs
                    && (nextSpawnIndex == 0 || (orbs.size() >= nextSpawnIndex && orbs.get(nextSpawnIndex - 1).isSettled()));
            if (canSpawn) {
                orbs.add(new Orb(nextSpawnIndex));
                nextSpawnIndex++;
            }

            // Update orb animations
            for (Orb o : orbs) {
                o.update(dt);
            }

            // Backdrop fade in while appearing
            backdropAlpha = Math.min(1f, backdropAlpha + dt * (backdropFadeSpeed * 0.2f));

            // After all spawned and each has finished animating to final spot, show menu
            if (!menuShown && nextSpawnIndex >= categories.size() && allOrbsSettled()) {
                showMenuFor(selectedIndex);
            }
        } else {
            // Dismissing: interpolate each orb position back to spawn and fade out
            closeT += dt / closeDuration;
            if (closeT > 1f) closeT = 1f;
            float k = easeInCubic(closeT);
            // Fade out backdrop and menu during dismissal
            backdropAlpha = Math.max(0f, backdropAlpha - dt * (backdropFadeSpeed * 0.4f));
            if (menuPanel != null) menuPanel.setAlpha(1f - k);

            // Orbs fade out and move to spawn
            for (int i = 0; i < orbs.size(); i++) {
                Orb o = orbs.get(i);
                Point s = closeStartPositions.get(i);
                Point p = lerpPoint(s, spawnPoint, k);
                o.setExternalPositionAndAlpha(p, 1f - k);
            }

            if (closeT >= 1f) {
                timer.stop();
                if (onClose != null) onClose.run();
                return;
            }
        }

        repaint();
    }

    private void showMenuFor(int idx) {
        if (idx < 0 || idx >= categories.size()) return;
        selectedIndex = idx;
        menuShown = true;

        if (menuPanel != null) remove(menuPanel);
        menuPanel = wrapPanel(categories.get(idx).createPanel());
        add(menuPanel);
        layoutMenuPanel();
        // Animate menu fade-in
        if (menuPanel != null) menuPanel.setAlpha(0f);
        revalidate();
        repaint();
        // Drive a quick fade-in using the main timer
        new Thread(() -> {
            // simple fade-in over ~225ms
            try {
                for (int i = 0; i <= 15; i++) {
                    final float a = Math.min(1f, i / 15f);
                    SwingUtilities.invokeLater(() -> { if (menuPanel != null) menuPanel.setAlpha(a); });
                    Thread.sleep(15);
                }
            } catch (InterruptedException ignored) {}
        }, "menu-fade-in").start();
    }

    private void layoutMenuPanel() {
        if (menuPanel == null) return;
        // Menu should appear to the right of the orbit on the same horizontal level as spawn point
        Point center = getOrbitCenter();
        int orbitRightX = center.x + radius;
        Dimension pref = menuPanel.getPreferredSize();
        int x = orbitRightX + 28; // slightly more spacing from the rightmost orb
        int y = Math.max(8, spawnPoint.y - pref.height / 2);
        // Flip to left side if not enough room on the right
        if (x + pref.width + 8 > getWidth()) {
            x = center.x - radius - 28 - pref.width;
        }
        // Clamp to keep on-screen
        int maxX = getWidth() - pref.width - 8;
        int maxY = getHeight() - pref.height - 8;
        x = Math.min(Math.max(8, x), Math.max(8, maxX));
        y = Math.min(Math.max(8, y), Math.max(8, maxY));
        menuPanel.setBounds(x, y, pref.width, pref.height);
        menuBoundsCache.setBounds(menuPanel.getBounds());
    }

    private int orbAt(Point p) {
        for (int i = 0; i < orbs.size(); i++) {
            Orb o = orbs.get(i);
            Rectangle r = o.getBounds();
            if (r.contains(p)) return i;
        }
        return -1;
    }

    private void selectCategory(int idx) {
        if (idx == selectedIndex && menuShown) return;
        showMenuFor(idx);
    }

    private void switchCategory(int delta) {
        if (categories == null || categories.isEmpty()) return;
        int n = categories.size();
        int next = (selectedIndex + delta) % n;
        if (next < 0) next += n;
        selectCategory(next);
    }

    private void close() {
        if (dismissing) return;
        dismissing = true;
        closeT = 0f;
        // Capture current positions for smooth reverse animation
        closeStartPositions = new ArrayList<>(orbs.size());
        for (Orb o : orbs) {
            closeStartPositions.add(o.position());
        }
        // Start fading out the menu (if showing)
        if (menuPanel != null) menuPanel.setAlpha(1f);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Soft global dim
        g2.setComposite(AlphaComposite.SrcOver.derive(0.08f));
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setComposite(AlphaComposite.SrcOver);

        // Translucent rounded background behind orbs + menu
        Rectangle clusterBounds = computeClusterBounds();
        if (clusterBounds != null && backdropAlpha > 0f) {
            int arc = 20;
            // Soft outer shadow
            g2.setComposite(AlphaComposite.SrcOver.derive(0.20f * backdropAlpha));
            g2.setColor(new Color(0, 0, 0, 180));
            g2.fillRoundRect(clusterBounds.x - 3, clusterBounds.y - 3, clusterBounds.width + 6, clusterBounds.height + 6, arc + 6, arc + 6);
            // Main glass background
            g2.setComposite(AlphaComposite.SrcOver.derive(Math.min(0.85f, 0.32f * backdropAlpha)));
            g2.setColor(new Color(22, 22, 26, 220));
            g2.fillRoundRect(clusterBounds.x, clusterBounds.y, clusterBounds.width, clusterBounds.height, arc, arc);
            g2.setComposite(AlphaComposite.SrcOver);
        }

        // Draw orbs
        for (int i = 0; i < orbs.size(); i++) {
            Orb o = orbs.get(i);
            o.paint(g2, i == selectedIndex);
        }

        g2.dispose();
    }

    @Override
    public void doLayout() {
        super.doLayout();
        layoutMenuPanel();
        // Recompute final positions if needed (defensive)
        computeFinalLayout();
    }

    private Point getOrbitCenter() {
        return new Point(spawnPoint.x + radius, spawnPoint.y);
    }

    private void computeFinalLayout() {
        int n = Math.max(1, categories.size());
        finalPositions = new Point[n];
        double step = Math.PI * 2.0 / n;
        Point c = getOrbitCenter();
        for (int i = 0; i < n; i++) {
            double ang = Math.PI - i * step;
            int x = Math.round(c.x + (float) Math.cos(ang) * radius);
            int y = Math.round(c.y + (float) Math.sin(ang) * radius);
            finalPositions[i] = new Point(x, y);
        }
    }

    private boolean allOrbsSettled() {
        if (orbs.isEmpty()) return false;
        for (Orb o : orbs) {
            if (!o.isSettled()) return false;
        }
        return true;
    }

    // removed legacy freeze method (no longer needed with direct-to-target animation)

    private class Orb {
        private float alpha = 0f; // fade in
        private float t = 0f;     // 0..1 animation progress to final position
        private final int categoryIndex;
        private final Point startPoint;  // spawn origin (spawnPoint)
        private final Point targetPoint; // final spaced position for this index
        private final float moveDuration = 0.26f; // seconds per orb move

        Orb(int categoryIndex) {
            this.categoryIndex = categoryIndex;
            this.startPoint = new Point(spawnPoint);
            this.targetPoint = finalPositions[categoryIndex];
        }

        void update(float dt) {
            // Fade to 1
            alpha += dt * 5f; // ~200ms
            if (alpha > 1f) alpha = 1f;
            // Progress movement to target
            if (t < 1f) {
                t += dt / moveDuration;
                if (t > 1f) t = 1f;
            }
        }

        boolean isSettled() { return t >= 1f; }

        Rectangle getBounds() {
            Point p = position();
            int d = orbDiameter;
            return new Rectangle(p.x - d / 2, p.y - d / 2, d, d);
        }

        private float easeOutCubic(float x) {
            float inv = 1f - x;
            return 1f - inv * inv * inv;
        }

        Point position() {
            float k = easeOutCubic(t);
            int x = Math.round(startPoint.x + (targetPoint.x - startPoint.x) * k);
            int y = Math.round(startPoint.y + (targetPoint.y - startPoint.y) * k);
            return new Point(x, y);
        }

        void setExternalPositionAndAlpha(Point p, float a) {
            // Used during dismissal to override position/alpha
            this.alpha = Math.max(0f, Math.min(1f, a));
            this.externalPos = new Point(p);
            this.useExternal = true;
        }

        private Point externalPos = null;
        private boolean useExternal = false;

        void paint(Graphics2D g2, boolean selected) {
            Point p = useExternal && externalPos != null ? externalPos : position();
            int d = orbDiameter;
            boolean hovered = (hoverIndex == categoryIndex);
            float scale = hovered ? 1.08f : (selected ? 1.04f : 1.0f);
            int sd = Math.round(d * scale);
            int sx = p.x - sd / 2;
            int sy = p.y - sd / 2;

            Composite old = g2.getComposite();
            g2.setComposite(AlphaComposite.SrcOver.derive(alpha));

            // Soft glow (reduced)
            int glowR = Math.round(sd * 1.3f);
            g2.setPaint(new RadialGradientPaint(new Point(p.x, p.y), glowR,
                    new float[]{0f, 1f}, new Color[]{
                            new Color(80, 170, 255, selected ? 200 : (hovered ? 170 : 140)),
                            new Color(60, 150, 255, 0)}));
            g2.fillOval(p.x - glowR, p.y - glowR, glowR * 2, glowR * 2);

            // Orb body (Aero blue)
            Color c1 = selected ? new Color(20, 140, 255) : new Color(80, 160, 255);
            Color c2 = selected ? new Color(10, 110, 220) : new Color(50, 120, 220);
            Paint body = new GradientPaint(sx, sy, c1, sx, sy + sd, c2);
            g2.setPaint(body);
            g2.fillOval(sx, sy, sd, sd);

            // Gloss and border
            g2.setPaint(new GradientPaint(sx, sy, new Color(255,255,255,180), sx, sy + sd/2f, new Color(255,255,255,0)));
            g2.fillOval(sx + 3, sy + 3, sd - 6, sd - Math.max(10, Math.round(sd * 0.28f)));
            g2.setColor(new Color(0, 70, 160, 200));
            g2.setStroke(new BasicStroke(selected ? 3f : (hovered ? 2f : 1f)));
            g2.drawOval(sx, sy, sd, sd);

            // Icon or letter
            javax.swing.Icon icon = categories.get(categoryIndex).getIcon();
            if (icon != null) {
                // Try to scale ImageIcon, otherwise center as-is
                int pad = 8;
                int avail = sd - pad * 2;
                if (icon instanceof ImageIcon) {
                    Image img = ((ImageIcon) icon).getImage().getScaledInstance(avail, avail, Image.SCALE_SMOOTH);
                    ImageIcon scaled = new ImageIcon(img);
                    int ix = sx + (sd - scaled.getIconWidth()) / 2;
                    int iy = sy + (sd - scaled.getIconHeight()) / 2;
                    scaled.paintIcon(null, g2, ix, iy);
                } else {
                    int ix = sx + (sd - icon.getIconWidth()) / 2;
                    int iy = sy + (sd - icon.getIconHeight()) / 2;
                    icon.paintIcon(null, g2, ix, iy);
                }
            } else {
                // Fallback: first letter
                String letter = categories.get(categoryIndex).getName().substring(0, 1).toUpperCase();
                Font oldF = g2.getFont();
                Font f = oldF.deriveFont(Font.BOLD, sd * 0.5f);
                g2.setFont(f);
                FontMetrics fm = g2.getFontMetrics();
                int tw = fm.stringWidth(letter);
                int th = fm.getAscent();
                g2.setColor(new Color(255,255,255,230));
                g2.drawString(letter, sx + (sd - tw) / 2, sy + (sd + th) / 2 - 4);
                g2.setFont(oldF);
            }

            g2.setComposite(old);
        }
    }

    private static FadePanel wrapPanel(JComponent inner) {
        FadePanel p = new FadePanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        p.add(inner, BorderLayout.CENTER);
        return p;
    }

    private Rectangle computeClusterBounds() {
        // Union of orbit circle and menu bounds (if present), with padding
        Point c = getOrbitCenter();
        int orbitR = radius + orbDiameter / 2 + 10;
        Rectangle orbitRect = new Rectangle(c.x - orbitR, c.y - orbitR, orbitR * 2, orbitR * 2);
        Rectangle r = new Rectangle(orbitRect);
        if (menuShown && menuBoundsCache.width > 0 && menuBoundsCache.height > 0) {
            r = r.union(menuBoundsCache);
        }
        int pad = 12;
        r.x = Math.max(0, r.x - pad);
        r.y = Math.max(0, r.y - pad);
        r.width = Math.min(getWidth() - r.x, r.width + pad * 2);
        r.height = Math.min(getHeight() - r.y, r.height + pad * 2);
        return r;
    }

    private static Point lerpPoint(Point a, Point b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int x = Math.round(a.x + (b.x - a.x) * t);
        int y = Math.round(a.y + (b.y - a.y) * t);
        return new Point(x, y);
    }

    private static float easeInCubic(float x) {
        return x * x * x;
    }

    // Panel that can fade its content in/out
    private static class FadePanel extends JPanel {
        private float alpha = 1f;

        FadePanel(LayoutManager mgr) { super(mgr); }

        void setAlpha(float a) {
            this.alpha = Math.max(0f, Math.min(1f, a));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setComposite(AlphaComposite.SrcOver.derive(alpha));

            int w = getWidth();
            int h = getHeight();
            // White-themed card (restored)
            Paint bg = new LinearGradientPaint(0, 0, 0, h,
                    new float[]{0f, 0.6f, 1f},
                    new Color[]{new Color(252,252,252,235), new Color(236,236,236,235), new Color(222,222,222,235)});
            g2.setPaint(bg);
            g2.fillRoundRect(0, 0, w, h, 14, 14);
            g2.setColor(new Color(170,170,170));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 14, 14);
            g2.dispose();
        }

        @Override
        protected void paintChildren(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setComposite(AlphaComposite.SrcOver.derive(alpha));
            super.paintChildren(g2);
            g2.dispose();
        }
    }
}
