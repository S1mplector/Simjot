package main.ui.quicksettings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Transparent overlay panel that renders animated category orbs and a
 * contextual settings panel to the right after the spawn sequence completes.
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
    private final int spawnIntervalMs = 220; // ms between orb spawns
    private final int orbDiameter = 34; // larger orbs

    // Menu panel for the currently selected category
    private JComponent menuPanel;
    private int selectedIndex = 0;
    private boolean menuShown = false;
    private Rectangle menuBoundsCache = new Rectangle(0,0,0,0);

    // Final layout
    private Point[] finalPositions;

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

        // ESC closes
        registerKeyboardAction(e -> close(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
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

        // Spawn next orb strictly one-by-one: only after previous has settled
        boolean canSpawn = nextSpawnIndex < categories.size()
                && elapsed >= nextSpawnIndex * (long) spawnIntervalMs
                && (nextSpawnIndex == 0 || (orbs.size() >= nextSpawnIndex && orbs.get(nextSpawnIndex - 1).isSettled()));
        if (canSpawn) {
            orbs.add(new Orb(nextSpawnIndex));
            nextSpawnIndex++;
        }

        // Update orb animations
        float dt = 16f / 1000f;
        for (Orb o : orbs) {
            o.update(dt);
        }

        // After all spawned and each has finished animating to final spot, show menu
        if (!menuShown && nextSpawnIndex >= categories.size() && allOrbsSettled()) {
            showMenuFor(selectedIndex);
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
        revalidate();
        repaint();
    }

    private void layoutMenuPanel() {
        if (menuPanel == null) return;
        // Menu should appear to the right of the orbit on the same horizontal level as spawn point
        Point center = getOrbitCenter();
        int orbitRightX = center.x + radius;
        Dimension pref = menuPanel.getPreferredSize();
        int x = orbitRightX + 12;
        int y = Math.max(8, spawnPoint.y - pref.height / 2);
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

    private void close() {
        timer.stop();
        if (onClose != null) onClose.run();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Optional dim background slightly
        g2.setComposite(AlphaComposite.SrcOver.derive(0.08f));
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setComposite(AlphaComposite.SrcOver);

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

        void paint(Graphics2D g2, boolean selected) {
            Point p = position();
            int d = orbDiameter;
            int x = p.x - d / 2;
            int y = p.y - d / 2;

            Composite old = g2.getComposite();
            g2.setComposite(AlphaComposite.SrcOver.derive(alpha));

            // Soft glow
            g2.setPaint(new RadialGradientPaint(new Point(p.x, p.y), d,
                    new float[]{0f, 1f}, new Color[]{new Color(60, 150, 255, 200), new Color(60, 150, 255, 0)}));
            g2.fillOval(x - d / 2, y - d / 2, d * 2, d * 2);

            // Orb body (Aero blue)
            Color c1 = selected ? new Color(20, 140, 255) : new Color(80, 160, 255);
            Color c2 = selected ? new Color(10, 110, 220) : new Color(50, 120, 220);
            Paint body = new GradientPaint(x, y, c1, x, y + d, c2);
            g2.setPaint(body);
            g2.fillOval(x, y, d, d);

            // Gloss and border
            g2.setPaint(new GradientPaint(x, y, new Color(255,255,255,180), x, y + d/2f, new Color(255,255,255,0)));
            g2.fillOval(x + 3, y + 3, d - 6, d - 10);
            g2.setColor(new Color(0, 70, 160, 200));
            g2.setStroke(new BasicStroke(selected ? 2f : 1f));
            g2.drawOval(x, y, d, d);

            // Icon or letter
            javax.swing.Icon icon = categories.get(categoryIndex).getIcon();
            if (icon != null) {
                // Try to scale ImageIcon, otherwise center as-is
                int pad = 8;
                int avail = d - pad * 2;
                if (icon instanceof ImageIcon) {
                    Image img = ((ImageIcon) icon).getImage().getScaledInstance(avail, avail, Image.SCALE_SMOOTH);
                    ImageIcon scaled = new ImageIcon(img);
                    int ix = x + (d - scaled.getIconWidth()) / 2;
                    int iy = y + (d - scaled.getIconHeight()) / 2;
                    scaled.paintIcon(null, g2, ix, iy);
                } else {
                    int ix = x + (d - icon.getIconWidth()) / 2;
                    int iy = y + (d - icon.getIconHeight()) / 2;
                    icon.paintIcon(null, g2, ix, iy);
                }
            } else {
                // Fallback: first letter
                String letter = categories.get(categoryIndex).getName().substring(0, 1).toUpperCase();
                Font oldF = g2.getFont();
                Font f = oldF.deriveFont(Font.BOLD, d * 0.5f);
                g2.setFont(f);
                FontMetrics fm = g2.getFontMetrics();
                int tw = fm.stringWidth(letter);
                int th = fm.getAscent();
                g2.setColor(new Color(255,255,255,230));
                g2.drawString(letter, x + (d - tw) / 2, y + (d + th) / 2 - 4);
                g2.setFont(oldF);
            }

            g2.setComposite(old);
        }
    }

    private static JComponent wrapPanel(JComponent inner) {
        JPanel p = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();
                // Subtle aero-style card
                Paint bg = new LinearGradientPaint(0, 0, 0, h,
                        new float[]{0f, 0.6f, 1f},
                        new Color[]{new Color(252,252,252,235), new Color(236,236,236,235), new Color(222,222,222,235)});
                g2.setPaint(bg);
                g2.fillRoundRect(0, 0, w, h, 14, 14);
                g2.setColor(new Color(170,170,170));
                g2.drawRoundRect(0, 0, w - 1, h - 1, 14, 14);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        p.add(inner, BorderLayout.CENTER);
        return p;
    }
}
