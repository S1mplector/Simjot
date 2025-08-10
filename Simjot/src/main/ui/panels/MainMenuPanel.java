package main.ui.panels;

import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import main.transitions.FadingButton;
import main.ui.JournalApp;
import main.ui.buttons.MainMenuButton;
import main.util.RamMonitor;
import main.util.ResourceLoader;
import main.util.SettingsStore;

public class MainMenuPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    // Toggle to quickly hide the Gallery feature without removing code/resources.
    private static final boolean SHOW_GALLERY = false;

    private final JournalApp app;
    private java.util.Map<String, main.ui.widgets.Widget> widgets = new java.util.LinkedHashMap<>();
    private DraggableWidgetPanel widgetPanel;
    private JLayeredPane layeredPane;

    public MainMenuPanel(JournalApp app) {
        this.app = app;
        buildUI();
    }

    private void buildUI() {
        String bgPath = SettingsStore.get().getBackgroundImage();
        JPanel content;
        if (bgPath != null && !bgPath.isEmpty()) {
            if (bgPath.startsWith("res:")) {
                // Built-in resource (class-path) – strip prefix
                String resPath = bgPath.substring(4);
                Image img = ResourceLoader.createImage("Simjot/" + resPath);
                content = (img != null) ? new BackgroundPanel(img) : new JPanel();
            } else {
                // User-selected file path
                content = new BackgroundPanel(bgPath);
            }
            content.setBackground(Color.BLACK);
        } else {
            // Blank / default – just use a plain white panel
            content = new JPanel();
            content.setBackground(Color.WHITE);
        }
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        // -------- Widgets registration ---------
        // Create a dummy widget just for the menu button
        widgets.put("Breathing", new main.ui.widgets.Widget() {
            private boolean enabled = false;

            @Override
            public void start() {
                enabled = true;
            }

            @Override
            public void stop() {
                enabled = false;
            }

            @Override
            public boolean isEnabled() {
                return enabled;
            }
        });

        // Add header and clock.
        HeaderPanel header = new HeaderPanel();
        header.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(Box.createRigidArea(new Dimension(0, 10)));
        content.add(header);

        // Time info right below header quote
        TimeInfoPanel timePanelTop = new TimeInfoPanel();
        timePanelTop.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(Box.createRigidArea(new Dimension(0, 6)));
        content.add(timePanelTop);

        // ---- Clock and Widgets side-by-side ----
        AnalogClockPanel clockPanel = new AnalogClockPanel();
        clockPanel.setPreferredSize(new Dimension(200, 200));
        clockPanel.setMaximumSize(new Dimension(200, 200));

        JPanel clockRow = new JPanel();
        clockRow.setOpaque(false);
        clockRow.setLayout(new BoxLayout(clockRow, BoxLayout.X_AXIS));
        clockRow.add(Box.createHorizontalGlue());
        clockRow.add(clockPanel);
        clockRow.add(Box.createHorizontalGlue());

        clockRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(Box.createRigidArea(new Dimension(0, 5)));
        content.add(clockRow);

        // Create the button panel with animated fade-in
        JPanel buttonPanel = new JPanel();
        buttonPanel.setOpaque(false);
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
        buttonPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // ---------- WRITING section ----------
        JLabel writingHeader = new JLabel("Writing");
        writingHeader.setAlignmentX(Component.CENTER_ALIGNMENT);
        writingHeader.setForeground(Color.WHITE);
        writingHeader.setFont(writingHeader.getFont().deriveFont(Font.BOLD, 22f));
        buttonPanel.add(writingHeader);
        buttonPanel.add(Box.createRigidArea(new Dimension(0, 6)));

        FadingButton notebooksButton = createMenuButtonWithIcon("Notebooks", JournalApp.NOTEBOOK_MANAGER, "notebook");
        buttonPanel.add(notebooksButton);
        buttonPanel.add(Box.createRigidArea(new Dimension(0, 12)));

        // Planning section has been removed
        // ---------- ARTS section ----------
        JLabel artsHeader = new JLabel("Arts & Gallery");
        artsHeader.setAlignmentX(Component.CENTER_ALIGNMENT);
        artsHeader.setForeground(Color.WHITE);
        artsHeader.setFont(artsHeader.getFont().deriveFont(Font.BOLD, 22f));
        buttonPanel.add(artsHeader);
        buttonPanel.add(Box.createRigidArea(new Dimension(0, 6)));

        FadingButton drawingButton = createMenuButtonWithIcon("Canvas", "Drawing", "pencil");
        drawingButton.setForeground(Color.WHITE);
        drawingButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        FadingButton galleryButton = createMenuButtonWithIcon("Gallery", JournalApp.GALLERY, "image");
        galleryButton.setForeground(Color.WHITE);
        galleryButton.setFont(galleryButton.getFont().deriveFont(Font.BOLD, 20f));
        galleryButton.setBorder(BorderFactory.createEmptyBorder(12, 24, 12, 24));
        galleryButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        galleryButton.addActionListener(e -> app.switchCard(JournalApp.GALLERY));

        List<FadingButton> artsBtns = new ArrayList<>();
        artsBtns.add(drawingButton);
        if (SHOW_GALLERY) {
            artsBtns.add(galleryButton);
        }
        for (FadingButton b : artsBtns) {
            b.setAlpha(1f);
            b.setAlignmentX(Component.CENTER_ALIGNMENT);
            buttonPanel.add(b);
            buttonPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        }
        if (SHOW_GALLERY) {
            buttonPanel.add(Box.createRigidArea(new Dimension(0, 12)));
        }

        // ---------- INSIGHTS section ----------
        JLabel insightsHeader = new JLabel("Insights");
        insightsHeader.setAlignmentX(Component.CENTER_ALIGNMENT);
        insightsHeader.setForeground(Color.WHITE);
        insightsHeader.setFont(insightsHeader.getFont().deriveFont(Font.BOLD, 22f));

        FadingButton moodChartButton = createMenuButtonWithIcon("Mood Chart", JournalApp.MOOD_CHART, "smile");

        buttonPanel.add(insightsHeader);
        buttonPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        moodChartButton.setAlpha(1f);
        moodChartButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        buttonPanel.add(moodChartButton);
        buttonPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        buttonPanel.add(Box.createRigidArea(new Dimension(0, 12)));

        FadingButton settingsButton = createMenuButtonWithIcon("Settings", JournalApp.SETTINGS, "wrench");
        settingsButton.setAlpha(1f);
        settingsButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        buttonPanel.add(settingsButton);
        buttonPanel.add(Box.createRigidArea(new Dimension(0, 6)));

        content.add(Box.createRigidArea(new Dimension(0, 20)));
        content.add(buttonPanel);

        header.startAnimation();
        // No fade-in animation; buttons are visible immediately

        // --------- Container setup
        setLayout(new BorderLayout());
        if (bgPath != null && !bgPath.isEmpty()) {
            setBackground(Color.BLACK);
        } else {
            setBackground(Color.WHITE);
        }

        // Always create the widget panel
        widgetPanel = new DraggableWidgetPanel();
        // Initial on-screen position to ensure visibility before layout sizes are known
        widgetPanel.setBounds(20, 50, 150, 200);

        // Use a layered pane to allow dragging over other components
        layeredPane = new JLayeredPane() {
            @Override
            public Dimension getPreferredSize() {
                return content.getPreferredSize();
            }
        };
        layeredPane.setLayout(null); // Use null layout for absolute positioning

        // Add the main content panel
        content.setBounds(0, 0, content.getPreferredSize().width, content.getPreferredSize().height);
        layeredPane.add(content, Integer.valueOf(JLayeredPane.DEFAULT_LAYER));

        // Add the widget panel
        Dimension widgetSize = widgetPanel.getPreferredSize();
        widgetPanel.setBounds(20, 50, widgetSize.width, widgetSize.height);
        layeredPane.add(widgetPanel, Integer.valueOf(JLayeredPane.PALETTE_LAYER));
        // Ensure it renders above everything and is visible immediately
        layeredPane.setLayer(widgetPanel, JLayeredPane.DRAG_LAYER);
        layeredPane.moveToFront(widgetPanel);
        updateWidgetPanelVisibility();
        System.out.println("[MainMenuPanel] Widget panel added: bounds=" + widgetPanel.getBounds() + 
                ", visible=" + widgetPanel.isVisible() + 
                ", layer=" + JLayeredPane.getLayer(widgetPanel));
        widgetPanel.repaint();
        layeredPane.repaint();

        // Add component listener to resize content and reposition widget panel when window resizes
        layeredPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                // Resize content panel to fill the layered pane
                content.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());

                if (widgetPanel != null) {
                    // Keep widget panel in bounds when window resizes
                    Point location = widgetPanel.getLocation();
                    Dimension widgetSize = widgetPanel.getSize();

                    if (location.x + widgetSize.width > layeredPane.getWidth()) {
                        location.x = layeredPane.getWidth() - widgetSize.width;
                    }
                    if (location.y + widgetSize.height > layeredPane.getHeight()) {
                        location.y = layeredPane.getHeight() - widgetSize.height;
                    }

                    widgetPanel.setBounds(location.x, location.y, widgetSize.width, widgetSize.height);
                }
            }
        });

        // Initialize widget panel position when component becomes visible
        SwingUtilities.invokeLater(() -> {
            if (widgetPanel != null && layeredPane.getWidth() > 0) {
                Dimension wSize = widgetPanel.getPreferredSize();
                int x = Math.max(0, layeredPane.getWidth() - wSize.width - 20);
                widgetPanel.setBounds(x, 50, wSize.width, wSize.height);
                System.out.println("[MainMenuPanel] Widget panel repositioned after layout: bounds=" + widgetPanel.getBounds());
            }
        });

        // South Panel with version label and RAM usage
        JPanel southPanel = new JPanel();
        southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.Y_AXIS));
        southPanel.setOpaque(false);
        southPanel.add(Box.createHorizontalGlue());

        JLabel versionLabel = new JLabel("Version 1.0 - By Ilgaz, with love");
        versionLabel.setForeground(Color.WHITE);
        versionLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        versionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        southPanel.add(versionLabel);

        RamMonitor ramPanel = new RamMonitor();
        ramPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        ramPanel.setOpaque(false);
        southPanel.add(ramPanel);

        add(layeredPane, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);
    }

    /**
     * Draggable and collapsible widget panel that can be minimized to just an
     * icon or expanded to show the full widget list.
     */
    private class DraggableWidgetPanel extends JPanel {

        private boolean isExpanded = true;
        private boolean isDragging = false;
        private Point dragOffset;
        private JPanel expandedContent;
        private JButton toggleButton;

        public DraggableWidgetPanel() {
            setOpaque(false);
            setLayout(new BorderLayout());
            initializeComponents();
            setupDragBehavior();
        }

        private void setupTitleBarDrag(Component component) {
            // Expanded state uses this drag behavior for the title bar
            MouseAdapter mouseHandler = createDragHandler();
            component.addMouseListener(mouseHandler);
            component.addMouseMotionListener(mouseHandler);
        }

        private MouseAdapter createDragHandler() {
            return new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        isDragging = true;
                        dragOffset = e.getPoint();
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (isDragging) {
                        isDragging = false;
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (isDragging && dragOffset != null) {
                        Point currentLocation = getLocation();
                        Point newLocation = new Point(
                                currentLocation.x + e.getX() - dragOffset.x,
                                currentLocation.y + e.getY() - dragOffset.y
                        );

                        // Constrain to parent bounds
                        Container parent = getParent();
                        if (parent != null) {
                            Dimension parentSize = parent.getSize();
                            Dimension thisSize = getSize();

                            newLocation.x = Math.max(0, Math.min(newLocation.x, parentSize.width - thisSize.width));
                            newLocation.y = Math.max(0, Math.min(newLocation.y, parentSize.height - thisSize.height));
                        }

                        setBounds(newLocation.x, newLocation.y, getWidth(), getHeight());
                    }
                }
            };
        }

        private void initializeComponents() {
            // Create toggle button (always visible)
            toggleButton = new JButton("⚙") { // Widget icon
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    // Draw rounded background
                    g2.setColor(new Color(0, 0, 0, 150));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);

                    // Draw border
                    g2.setColor(new Color(255, 255, 255, 100));
                    g2.setStroke(new BasicStroke(1f));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);

                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            toggleButton.setPreferredSize(new Dimension(50, 50));
            toggleButton.setOpaque(false);
            toggleButton.setContentAreaFilled(false);
            toggleButton.setBorderPainted(false);
            toggleButton.setForeground(Color.WHITE);
            toggleButton.setFocusPainted(false);
            toggleButton.setFont(new Font("SansSerif", Font.BOLD, 18));
            toggleButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            toggleButton.addActionListener(e -> toggleExpansion());

            // Create expanded content panel
            expandedContent = new JPanel();
            expandedContent.setOpaque(false);
            expandedContent.setLayout(new BoxLayout(expandedContent, BoxLayout.Y_AXIS));
            expandedContent.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

            // Add title
            JLabel title = new JLabel("Widgets");
            title.setForeground(Color.WHITE);
            title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
            title.setAlignmentX(Component.CENTER_ALIGNMENT);
            expandedContent.add(title);
            expandedContent.add(Box.createRigidArea(new Dimension(0, 6)));

            // Add widget buttons
            for (java.util.Map.Entry<String, main.ui.widgets.Widget> entry : widgets.entrySet()) {
                String name = entry.getKey();
                main.ui.widgets.Widget widget = entry.getValue();
                FadingButton btn = new MainMenuButton(name, "bolt");
                btn.setForeground(Color.WHITE);
                btn.setFont(btn.getFont().deriveFont(Font.PLAIN, 16f));
                btn.setAlpha(1f);
                btn.setAlignmentX(Component.CENTER_ALIGNMENT);
                btn.addActionListener(e -> {
                    if (name.equals("Breathing")) {
                        // First show our custom confirmation dialog
                        boolean startBreathing = main.dialog.CustomConfirmDialog.confirm(
                                MainMenuPanel.this,
                                "Breathing Exercise",
                                "Would you like to start a guided breathing exercise?\n\nThis will display a calming animation overlay."
                        );

                        if (startBreathing) {
                            // Show configuration dialog for breathing widget
                            main.dialog.BreathingConfigDialog dialog
                                    = new main.dialog.BreathingConfigDialog((JFrame) SwingUtilities.getWindowAncestor(MainMenuPanel.this));
                            dialog.setVisible(true);

                            if (dialog.isConfirmed()) {
                                // Open breathing exercise in its own window
                                main.dialog.BreathingExerciseWindow exerciseWindow
                                        = new main.dialog.BreathingExerciseWindow((JFrame) SwingUtilities.getWindowAncestor(MainMenuPanel.this));
                                exerciseWindow.startExercise(
                                        dialog.getInhaleTime(),
                                        dialog.getHold1Time(),
                                        dialog.getExhaleTime(),
                                        dialog.getHold2Time(),
                                        dialog.getOpacityValue(),
                                        dialog.getSizeValue(),
                                        dialog.getColor()
                                );
                            }
                        }
                    } else {
                        // For other widgets, just toggle
                        boolean enable = !widget.isEnabled();
                        widget.setEnabled(enable);
                    }
                });
                expandedContent.add(btn);
                expandedContent.add(Box.createRigidArea(new Dimension(0, 4)));
            }

            updateLayout();
        }

        private void setupDragBehavior() {
            MouseAdapter mouseHandler = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        isDragging = true;
                        dragOffset = e.getPoint();
                        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (isDragging) {
                        isDragging = false;
                        setCursor(Cursor.getDefaultCursor());
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (isDragging && dragOffset != null) {
                        Point currentLocation = getLocation();
                        Point newLocation = new Point(
                                currentLocation.x + e.getX() - dragOffset.x,
                                currentLocation.y + e.getY() - dragOffset.y
                        );

                        // Constrain to parent bounds
                        Container parent = getParent();
                        if (parent != null) {
                            Dimension parentSize = parent.getSize();
                            Dimension thisSize = getSize();

                            newLocation.x = Math.max(0, Math.min(newLocation.x, parentSize.width - thisSize.width));
                            newLocation.y = Math.max(0, Math.min(newLocation.y, parentSize.height - thisSize.height));
                        }

                        setBounds(newLocation.x, newLocation.y, getWidth(), getHeight());
                    }
                }
            };

            addMouseListener(mouseHandler);
            addMouseMotionListener(mouseHandler);
            toggleButton.addMouseListener(mouseHandler);
            toggleButton.addMouseMotionListener(mouseHandler);
        }

        private void toggleExpansion() {
            isExpanded = !isExpanded;
            updateLayout();

            // Animate the transition
            Timer animationTimer = new Timer(10, null);
            animationTimer.addActionListener(e -> {
                revalidate();
                repaint();
                animationTimer.stop();
            });
            animationTimer.start();
        }

        private void updateLayout() {
            removeAll();

            if (isExpanded) {
                // Show full widget panel with semi-transparent background
                JPanel backgroundPanel = new JPanel(new BorderLayout()) {
                    @Override
                    protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                        // Draw main background
                        g2.setColor(new Color(0, 0, 0, 150));
                        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);

                        // Draw border
                        g2.setColor(new Color(255, 255, 255, 80));
                        g2.setStroke(new BasicStroke(1f));
                        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 15, 15);

                        // Draw title bar area
                        g2.setColor(new Color(255, 255, 255, 20));
                        g2.fillRoundRect(1, 1, getWidth() - 2, 50, 14, 14);

                        g2.dispose();
                    }
                };
                backgroundPanel.setOpaque(false);

                // Create title bar with toggle button and drag area
                JPanel titleBar = new JPanel(new BorderLayout());
                titleBar.setOpaque(false);
                titleBar.setPreferredSize(new Dimension(0, 50));

                JLabel dragLabel = new JLabel("≡≡≡ Drag Here ≡≡≡", SwingConstants.CENTER);
                dragLabel.setForeground(new Color(255, 255, 255, 150));
                dragLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
                dragLabel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));

                titleBar.add(dragLabel, BorderLayout.CENTER);
                titleBar.add(toggleButton, BorderLayout.EAST);

                backgroundPanel.add(titleBar, BorderLayout.NORTH);
                backgroundPanel.add(expandedContent, BorderLayout.CENTER);
                add(backgroundPanel, BorderLayout.CENTER);
                toggleButton.setText("×"); // X to close when expanded

                // Add drag behavior to title bar
                setupTitleBarDrag(titleBar);
                setupTitleBarDrag(dragLabel);
            } else {
                // Show only the toggle button
                add(toggleButton, BorderLayout.CENTER);
                toggleButton.setText("⚙"); // Widget icon when collapsed
            }

            // Update bounds when layout changes
            Dimension newSize = getPreferredSize();
            setBounds(getX(), getY(), newSize.width, newSize.height);
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            if (isExpanded) {
                return new Dimension(160, 220);
            } else {
                return new Dimension(50, 50);
            }
        }
    }

    public void updateWidgetPanelVisibility() {
        if (widgetPanel != null) {
            // Always show widgets panel; no longer controlled by a setting
            widgetPanel.setVisible(true);
        }
    }

    // Force widget panel to the top-most layer and visible; useful after the UI is shown
    public void ensureWidgetPanelOnTopAndVisible() {
        if (widgetPanel != null && layeredPane != null) {
            layeredPane.setLayer(widgetPanel, JLayeredPane.DRAG_LAYER);
            layeredPane.moveToFront(widgetPanel);
            widgetPanel.setVisible(true);

            // Clamp position to be on-screen
            Dimension parentSize = layeredPane.getSize();
            Dimension wSize = widgetPanel.getPreferredSize();
            int x = widgetPanel.getX();
            int y = widgetPanel.getY();
            if (parentSize.width <= 0 || parentSize.height <= 0) {
                // Parent not laid out yet: place at a safe default
                x = 20; y = 50;
            } else {
                if (x < 0) x = 20;
                if (y < 0) y = 50;
                if (x + wSize.width > parentSize.width) x = Math.max(0, parentSize.width - wSize.width - 20);
                if (y + wSize.height > parentSize.height) y = Math.max(0, parentSize.height - wSize.height - 20);
            }
            widgetPanel.setBounds(x, y, wSize.width, wSize.height);

            widgetPanel.revalidate();
            widgetPanel.repaint();
            layeredPane.revalidate();
            layeredPane.repaint();
            System.out.println("[MainMenuPanel] ensureWidgetPanelOnTopAndVisible() applied: bounds=" + widgetPanel.getBounds());
        }
    }

    private FadingButton createMenuButtonWithIcon(String text, String cardName, String icon) {
        FadingButton button = new MainMenuButton(text, icon);
        button.setForeground(Color.WHITE);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 20f));
        button.setBorder(BorderFactory.createEmptyBorder(12, 24, 12, 24));
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.addActionListener(e -> app.switchCard(cardName));
        return button;
    }

    // ---------- Time Info Panel ----------
    private static class TimeInfoPanel extends JPanel {

        private final JLabel timeLbl = new JLabel();
        private final JLabel pctLbl = new JLabel();

        TimeInfoPanel() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            timeLbl.setForeground(Color.WHITE);
            pctLbl.setForeground(Color.WHITE);
            Font quoteFont = new Font("SansSerif", Font.ITALIC, 18);
            timeLbl.setFont(quoteFont);
            pctLbl.setFont(quoteFont);
            timeLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
            pctLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
            add(timeLbl);
            add(pctLbl);
            javax.swing.Timer t = new javax.swing.Timer(1000, e -> update());
            t.start();
            update();
        }

        private void update() {
            java.time.LocalTime now = java.time.LocalTime.now();
            timeLbl.setText("It's currently " + now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
            int seconds = now.toSecondOfDay();
            double pct = seconds / 86400.0 * 100.0;
            pctLbl.setText(String.format("%.1f%% of the day has passed.", pct));
        }
    }
}
