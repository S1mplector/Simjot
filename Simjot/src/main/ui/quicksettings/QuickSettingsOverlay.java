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
    private final int radius = 56; // orbit radius
    private final float angularSpeed = (float) Math.toRadians(120); // radians/sec clockwise
    private final int spawnIntervalMs = 220; // ms between orb spawns
    private final int orbDiameter = 26;

    // Menu panel for the currently selected category
    private JComponent menuPanel;
    private int selectedIndex = 0;
    private boolean menuShown = false;

    private Rectangle menuBoundsCache = new Rectangle(0,0,0,0);

    public QuickSettingsOverlay(List<QuickSettingsCategory> categories, Point spawnPoint, Runnable onClose) {
        this.categories = categories;
        this.spawnPoint = new Point(spawnPoint);
        this.onClose = onClose;

        setOpaque(false);
        setFocusable(true);

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

        // Spawn next orb if time
        while (nextSpawnIndex < categories.size() && elapsed >= nextSpawnIndex * (long) spawnIntervalMs) {
            orbs.add(new Orb(nextSpawnIndex));
            nextSpawnIndex++;
        }

        // Update orb animations
        float dt = 16f / 1000f;
        for (Orb o : orbs) {
            o.update(dt);
        }

        // Show menu after all spawned (small delay for polish)
        if (!menuShown && nextSpawnIndex >= categories.size() && elapsed > categories.size() * (long) spawnIntervalMs + 120) {
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
    }

    private Point getOrbitCenter() {
        return new Point(spawnPoint.x + radius, spawnPoint.y);
    }

    private class Orb {
        private float angle = (float) Math.PI; // start at spawn point (leftmost on the circle)
        private float alpha = 0f; // fade in

        private final int categoryIndex;

        Orb(int categoryIndex) {
            this.categoryIndex = categoryIndex;
        }

        void update(float dt) {
            // Fade to 1
            alpha += dt * 5f; // ~200ms
            if (alpha > 1f) alpha = 1f;
            // Rotate clockwise => decreasing angle
            angle -= angularSpeed * dt;
        }

        Rectangle getBounds() {
            Point p = position();
            int d = orbDiameter;
            return new Rectangle(p.x - d / 2, p.y - d / 2, d, d);
        }

        Point position() {
            Point c = getOrbitCenter();
            int x = Math.round(c.x + (float) Math.cos(angle) * radius);
            int y = Math.round(c.y + (float) Math.sin(angle) * radius);
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
            g2.drawOval(x, y, d, d);

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
