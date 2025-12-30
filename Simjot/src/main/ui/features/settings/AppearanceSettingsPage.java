package main.ui.features.settings;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;

import main.core.service.SettingsStore;
import main.ui.components.buttons.IconMenuButton;
import main.ui.components.calendars.CircularCalendar;
import main.ui.components.calendars.DotMatrixCalendar;
import main.ui.components.calendars.GlassCalendar;
import main.ui.components.calendars.MinimalistCalendar;
import main.ui.components.calendars.NeonCalendar;
import main.ui.components.calendars.PostItCalendar;
import main.ui.components.calendars.RetroCalendar;
import main.ui.components.calendars.StampCalendar;
import main.ui.components.calendars.TornPageCalendar;
import main.ui.components.calendars.VerticalCalendar;
import main.ui.components.checkbox.ModernCheckBoxUI;
import main.ui.components.clocks.BinaryClock;
import main.ui.components.clocks.FlipClock;
import main.ui.components.clocks.MinimalistClock;
import main.ui.components.clocks.NeonClock;
import main.ui.components.clocks.OrbitalClock;
import main.ui.components.clocks.PolarClock;
import main.ui.components.clocks.RadarClock;
import main.ui.components.clocks.SegmentClock;
import main.ui.components.clocks.SunburstClock;
import main.ui.components.clocks.SwissRailwayClock;
import main.ui.components.clocks.WordClock;
import main.ui.components.combobox.ModernComboBoxUI;
import main.ui.features.gallery.WallpaperGalleryPanel;
import main.ui.features.home.AnalogClockPanel;
import main.ui.features.home.TodayCalendarPanel;

class AppearanceSettingsPage extends JPanel implements SettingsPage {
    private final IconMenuButton backgroundOptionsBtn;
    private final JComboBox<String> densityBox;
    private final JComboBox<AccentOption> accentBox;
    private final JCheckBox disableAnimationsChk;
    private final JCheckBox disableMainMenuAnimationsChk;
    // Clock and Calendar style selection
    private String selectedClockStyle;
    private String selectedCalendarStyle;
    private static final String[] CLOCK_STYLES = {"Classic", "Minimalist", "Neon", "Swiss", "Sunburst", "Segment", "Polar", "Binary", "Flip", "Orbital", "Radar", "Word"};
    private static final String[] CALENDAR_STYLES = {"Classic", "Minimalist", "TornPage", "Circular", "PostIt", "Glass", "Vertical", "DotMatrix", "Stamp", "Retro", "Neon"};

    AppearanceSettingsPage() {
        setLayout(new GridBagLayout());
        setOpaque(true);
        setBackground(Color.WHITE);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5, 5, 5, 5);
        gc.fill = GridBagConstraints.HORIZONTAL;

        // Header
        gc.gridx = 0; gc.gridy = 0; gc.gridwidth = 2; add(SettingsUi.header("Appearance", "Theme and visuals"), gc);
        gc.gridwidth = 1;

        SettingsStore store = SettingsStore.get();
        densityBox = new JComboBox<>(new String[]{"Minimal", "Balanced", "Information-dense"});
        densityBox.setUI(new ModernComboBoxUI());
        densityBox.setRenderer(new ModernComboBoxUI.ModernComboBoxRenderer());
        {
            String saved = store.getLayoutDensity();
            String sel = "Balanced";
            if (saved != null) {
                String s = saved.trim();
                if (s.equalsIgnoreCase("Minimal")) sel = "Minimal";
                else if (s.toLowerCase().startsWith("dense") || s.toLowerCase().startsWith("information")) sel = "Information-dense";
            }
            densityBox.setSelectedItem(sel);
        }

        accentBox = new JComboBox<>(AccentOption.presets());
        accentBox.setUI(new ModernComboBoxUI());
        accentBox.setRenderer(new AccentRenderer());
        {
            int saved = store.getWidgetAccentRGB();
            if (saved != Integer.MIN_VALUE) {
                Color c = new Color(saved, false);
                accentBox.setSelectedItem(AccentOption.fromCustom(c));
            }
        }

        disableAnimationsChk = new JCheckBox("Disable transition animations", store.isAnimationsDisabled());
        disableAnimationsChk.setUI(new ModernCheckBoxUI());
        disableAnimationsChk.setBackground(new Color(0, 0, 0, 0));

        disableMainMenuAnimationsChk = new JCheckBox("Disable main menu animations", store.isMainMenuAnimationsDisabled());
        disableMainMenuAnimationsChk.setUI(new ModernCheckBoxUI());
        disableMainMenuAnimationsChk.setBackground(new Color(0, 0, 0, 0));

        backgroundOptionsBtn = new IconMenuButton("Set BG", "backgroundoptions");
        backgroundOptionsBtn.setToolTipText("Background Options");
        backgroundOptionsBtn.addActionListener(e -> openBackgroundOptions());

        gc.gridx = 0; gc.gridy = 1; add(SettingsUi.label("Background:"), gc);
        gc.gridx = 1;
        gc.fill = GridBagConstraints.NONE;
        gc.anchor = GridBagConstraints.WEST;
        add(backgroundOptionsBtn, gc);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.CENTER;
        gc.gridx = 0; gc.gridy = 2; add(SettingsUi.label("Layout density:"), gc);
        gc.gridx = 1; add(densityBox, gc);
        gc.gridx = 0; gc.gridy = 3; add(SettingsUi.label("Widget accent:"), gc);
        gc.gridx = 1; add(accentBox, gc);
        gc.gridx = 0; gc.gridy = 4; gc.gridwidth = 2; add(disableAnimationsChk, gc);
        gc.gridx = 0; gc.gridy = 5; gc.gridwidth = 2; add(disableMainMenuAnimationsChk, gc);

        // Clock & Calendar Style section
        gc.gridx = 0; gc.gridy = 6; gc.gridwidth = 2;
        gc.insets = new Insets(20, 5, 5, 5);
        add(SettingsUi.header("Clock & Calendar", "Style for main menu widgets"), gc);
        gc.insets = new Insets(5, 5, 5, 5);

        // Clock style selection
        gc.gridx = 0; gc.gridy = 7; gc.gridwidth = 2;
        add(SettingsUi.label("Clock style:"), gc);

        selectedClockStyle = store.getClockStyle();
        StyleCycler clockCycler = new StyleCycler(CLOCK_STYLES, selectedClockStyle, true);
        clockCycler.setOnChange(style -> selectedClockStyle = style);
        gc.gridx = 0; gc.gridy = 8; gc.gridwidth = 2;
        gc.fill = GridBagConstraints.HORIZONTAL;
        add(clockCycler, gc);

        // Calendar style selection
        gc.gridx = 0; gc.gridy = 9; gc.gridwidth = 2;
        gc.insets = new Insets(10, 5, 5, 5);
        add(SettingsUi.label("Calendar style:"), gc);
        gc.insets = new Insets(5, 5, 5, 5);

        selectedCalendarStyle = store.getCalendarStyle();
        StyleCycler calendarCycler = new StyleCycler(CALENDAR_STYLES, selectedCalendarStyle, false);
        calendarCycler.setOnChange(style -> selectedCalendarStyle = style);
        gc.gridx = 0; gc.gridy = 10; gc.gridwidth = 2;
        gc.fill = GridBagConstraints.HORIZONTAL;
        add(calendarCycler, gc);
        gc.gridwidth = 1;
    }

    private static JPanel createClockPreview(String style) {
        return switch (style) {
            case "Minimalist" -> new MinimalistClock();
            case "Neon" -> new NeonClock();
            case "Swiss" -> new SwissRailwayClock();
            case "Sunburst" -> new SunburstClock();
            case "Segment" -> new SegmentClock();
            case "Polar" -> new PolarClock();
            case "Binary" -> new BinaryClock();
            case "Flip" -> new FlipClock();
            case "Orbital" -> new OrbitalClock();
            case "Radar" -> new RadarClock();
            case "Word" -> new WordClock();
            default -> new AnalogClockPanel();
        };
    }

    private static JPanel createCalendarPreview(String style) {
        return switch (style) {
            case "Minimalist" -> new MinimalistCalendar();
            case "TornPage" -> new TornPageCalendar();
            case "Circular" -> new CircularCalendar();
            case "PostIt" -> new PostItCalendar();
            case "Glass" -> new GlassCalendar();
            case "Vertical" -> new VerticalCalendar();
            case "DotMatrix" -> new DotMatrixCalendar();
            case "Stamp" -> new StampCalendar();
            case "Retro" -> new RetroCalendar();
            case "Neon" -> new NeonCalendar();
            default -> new TodayCalendarPanel();
        };
    }


    @Override public JComponent getComponent() { return this; }

    @Override public void apply() {
        SettingsStore store = SettingsStore.get();
        String density = (String) densityBox.getSelectedItem();
        store.setLayoutDensity(density == null ? "Balanced" : density);

        AccentOption ao = (AccentOption) accentBox.getSelectedItem();
        if (ao != null && ao.color != null) {
            store.setWidgetAccentRGB(ao.color.getRGB());
            store.setMainMenuAccentRGB(ao.color.getRGB());
        } else {
            store.clearWidgetAccent();
            store.clearMainMenuAccent();
        }

        store.setAnimationsDisabled(disableAnimationsChk.isSelected());

        store.setMainMenuAnimationsDisabled(disableMainMenuAnimationsChk.isSelected());

        // Clock and Calendar styles
        if (selectedClockStyle != null) store.setClockStyle(selectedClockStyle);
        if (selectedCalendarStyle != null) store.setCalendarStyle(selectedCalendarStyle);
    }

    private void openBackgroundOptions() {
        WallpaperGalleryPanel.showWallpaperGallery(this);
    }

    private record AccentOption(String name, Color color) {
        @Override public String toString() { return name; }

        static AccentOption[] presets() {
            return new AccentOption[]{
                new AccentOption("Theme default", null),
                new AccentOption("Default Blue", new Color(0, 120, 215)),
                new AccentOption("Mint", new Color(46, 204, 113)),
                new AccentOption("Amber", new Color(236, 151, 31)),
                new AccentOption("Rose", new Color(230, 93, 129)),
                new AccentOption("Grape", new Color(121, 86, 190))
            };
        }

        static AccentOption fromCustom(Color c) {
            return new AccentOption("Custom", c);
        }
    }

    private static class AccentRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof AccentOption ao && c instanceof JLabel lbl) {
                lbl.setText(ao.name());
                lbl.setIcon(buildSwatch(ao.color));
            }
            return c;
        }

        private static ImageIcon buildSwatch(Color c) {
            if (c == null) return null;
            int s = 14;
            BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0,0,0,40));
            g.fillRoundRect(1, 3, s-2, s-2, 6, 6);
            g.setColor(c);
            g.fillRoundRect(0, 0, s-2, s-2, 6, 6);
            g.dispose();
            return new ImageIcon(img);
        }
    }

    private class StyleCycler extends JPanel {
        private final String[] styles;
        private final boolean isClock;
        private int currentIndex;
        private JPanel previewContainer;
        private JLabel nameLabel;
        private java.util.function.Consumer<String> onChange;

        StyleCycler(String[] styles, String initialStyle, boolean isClock) {
            this.styles = styles;
            this.isClock = isClock;
            this.currentIndex = findIndex(initialStyle);
            setOpaque(false);
            setLayout(new BorderLayout(8, 0));
            buildUI();
        }

        private int findIndex(String style) {
            for (int i = 0; i < styles.length; i++) {
                if (styles[i].equals(style)) return i;
            }
            return 0;
        }

        void setOnChange(java.util.function.Consumer<String> callback) {
            this.onChange = callback;
        }

        private void buildUI() {
            // Left arrow
            JButton prevBtn = createArrowButton("back");
            prevBtn.addActionListener(e -> cycle(-1));
            add(prevBtn, BorderLayout.WEST);

            // Center: preview + name
            JPanel center = new JPanel();
            center.setOpaque(false);
            center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

            previewContainer = new JPanel(new BorderLayout());
            previewContainer.setOpaque(false);
            previewContainer.setPreferredSize(isClock ? new Dimension(160, 160) : new Dimension(140, 160));
            previewContainer.setAlignmentX(Component.CENTER_ALIGNMENT);
            center.add(previewContainer);

            nameLabel = new JLabel();
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 13f));
            nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            nameLabel.setHorizontalAlignment(JLabel.CENTER);
            center.add(javax.swing.Box.createRigidArea(new Dimension(0, 6)));
            center.add(nameLabel);

            // Index indicator
            JLabel indexLabel = new JLabel() {
                @Override
                public String getText() {
                    return (currentIndex + 1) + " / " + styles.length;
                }
            };
            indexLabel.setFont(indexLabel.getFont().deriveFont(Font.PLAIN, 11f));
            indexLabel.setForeground(new Color(120, 120, 120));
            indexLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            indexLabel.setHorizontalAlignment(JLabel.CENTER);
            center.add(indexLabel);

            add(center, BorderLayout.CENTER);

            updatePreview();
        }

        private JButton createArrowButton(String iconId) {
            String res = main.ui.components.icons.ImageIconRenderer.mapIdToResource(iconId);
            javax.swing.Icon icon = res != null ? main.ui.components.icons.ImageIconRenderer.icon(res, 20, false) : null;
            JButton btn = new JButton(icon) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    if (getModel().isPressed()) {
                        g2.setColor(new Color(0, 0, 0, 25));
                    } else if (getModel().isRollover()) {
                        g2.setColor(new Color(0, 0, 0, 15));
                    } else {
                        g2.setColor(new Color(0, 0, 0, 0));
                    }
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            btn.setOpaque(false);
            btn.setContentAreaFilled(false);
            btn.setBorderPainted(false);
            btn.setFocusPainted(false);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btn.setPreferredSize(new Dimension(36, 36));
            return btn;
        }

        private void cycle(int delta) {
            currentIndex = (currentIndex + delta + styles.length) % styles.length;
            updatePreview();
            if (onChange != null) {
                onChange.accept(styles[currentIndex]);
            }
        }

        private void updatePreview() {
            previewContainer.removeAll();
            String style = styles[currentIndex];
            JPanel preview = isClock ? createClockPreview(style) : createCalendarPreview(style);
            preview.setPreferredSize(isClock ? new Dimension(150, 150) : new Dimension(130, 150));
            previewContainer.add(preview, BorderLayout.CENTER);
            nameLabel.setText(style);
            previewContainer.revalidate();
            previewContainer.repaint();
            repaint();
        }
    }
}
