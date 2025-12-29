package main.ui.features.settings;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.image.BufferedImage;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import main.core.service.SettingsStore;
import main.infrastructure.monitoring.AppPerf;
import main.ui.animations.transitions.FadingButton;
import main.ui.components.buttons.IconMenuButton;
import main.ui.components.checkbox.ModernCheckBoxUI;
import main.ui.components.combobox.ModernComboBoxUI;
import main.ui.features.gallery.WallpaperGalleryPanel;

class AppearanceSettingsPage extends JPanel implements SettingsPage {
    private final IconMenuButton backgroundOptionsBtn;
    private final JComboBox<String> themeBox;
    private final JComboBox<String> densityBox;
    private final JComboBox<AccentOption> accentBox;
    private final JCheckBox glowChk;
    private final JCheckBox disableAnimationsChk;
    private final JCheckBox disableMainMenuAnimationsChk;
    private final JCheckBox lowPowerChk;
    // Editor Font settings
    private final JComboBox<String> fontFamilyBox;
    private final JComboBox<Integer> fontSizeBox;
    private final JComboBox<String> lineSpacingBox;
    private final JTextPane fontPreview;

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
        String[] themes = {"Aero", "Light", "Sepia"};
        themeBox = new JComboBox<>(themes);
        themeBox.setUI(new ModernComboBoxUI());
        themeBox.setRenderer(new ModernComboBoxUI.ModernComboBoxRenderer());
        {
            String saved = store.getTheme();
            String sel = "Aero";
            if (saved != null) {
                String s = saved.trim();
                if (s.equalsIgnoreCase("Plain White") || s.equalsIgnoreCase("Plain") || s.equalsIgnoreCase("White") || s.equalsIgnoreCase("Light")) sel = "Light";
                else if (s.equalsIgnoreCase("Sepia")) sel = "Sepia";
                else if (s.equalsIgnoreCase("Dark")) sel = "Light"; // legacy mapping
                else if (s.equalsIgnoreCase("Aero")) sel = "Aero";
                else sel = "Aero";
            }
            themeBox.setSelectedItem(sel);
        }

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

        glowChk = new JCheckBox("Enable button glow", store.isGlowEnabled());
        glowChk.setUI(new ModernCheckBoxUI());
        glowChk.setBackground(new Color(0, 0, 0, 0));

        disableAnimationsChk = new JCheckBox("Disable transition animations", store.isAnimationsDisabled());
        disableAnimationsChk.setUI(new ModernCheckBoxUI());
        disableAnimationsChk.setBackground(new Color(0, 0, 0, 0));

        disableMainMenuAnimationsChk = new JCheckBox("Disable main menu animations", store.isMainMenuAnimationsDisabled());
        disableMainMenuAnimationsChk.setUI(new ModernCheckBoxUI());
        disableMainMenuAnimationsChk.setBackground(new Color(0, 0, 0, 0));

        lowPowerChk = new JCheckBox("Low Power Mode (battery saver)", store.isLowPowerMode());
        lowPowerChk.setUI(new ModernCheckBoxUI());
        lowPowerChk.setBackground(new Color(0, 0, 0, 0));

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
        gc.gridx = 0; gc.gridy = 2; add(SettingsUi.label("Theme:"), gc);
        gc.gridx = 1; add(themeBox, gc);
        gc.gridx = 0; gc.gridy = 3; add(SettingsUi.label("Layout density:"), gc);
        gc.gridx = 1; add(densityBox, gc);
        gc.gridx = 0; gc.gridy = 4; add(SettingsUi.label("Widget accent:"), gc);
        gc.gridx = 1; add(accentBox, gc);
        gc.gridx = 0; gc.gridy = 5; gc.gridwidth = 2; add(glowChk, gc);
        gc.gridx = 0; gc.gridy = 6; gc.gridwidth = 2; add(disableAnimationsChk, gc);
        gc.gridx = 0; gc.gridy = 7; gc.gridwidth = 2; add(disableMainMenuAnimationsChk, gc);
        gc.gridx = 0; gc.gridy = 8; gc.gridwidth = 2; add(lowPowerChk, gc);

        // Editor Font section
        gc.gridx = 0; gc.gridy = 9; gc.gridwidth = 2;
        gc.insets = new Insets(20, 5, 5, 5);
        add(SettingsUi.header("Editor Font", "Font style for journals and poems"), gc);
        gc.insets = new Insets(5, 5, 5, 5);
        gc.gridwidth = 1;

        String[] fonts = {"Serif", "Georgia", "Garamond", "Baskerville",
                "Lucida Handwriting", "Segoe Script", "Comic Sans MS", "Bradley Hand",
                "Segoe Print", "Marker Felt", "Noteworthy", "Chalkboard", "Chalkboard SE",
                "Apple Chancery", "Snell Roundhand", "Zapfino", "Brush Script MT",
                "Lucida Calligraphy", "Papyrus", "Cursive"};
        fontFamilyBox = new JComboBox<>(fonts);
        fontFamilyBox.setUI(new ModernComboBoxUI());
        fontFamilyBox.setRenderer(new ModernComboBoxUI.ModernComboBoxRenderer());
        fontFamilyBox.setSelectedItem(store.getEditorFontFamily());
        fontFamilyBox.addActionListener(e -> updateFontPreview());

        Integer[] sizes = {12, 14, 16, 18, 20, 22, 24, 28};
        fontSizeBox = new JComboBox<>(sizes);
        fontSizeBox.setUI(new ModernComboBoxUI());
        fontSizeBox.setRenderer(new ModernComboBoxUI.ModernComboBoxRenderer());
        fontSizeBox.setSelectedItem(store.getJournalFontSize());
        fontSizeBox.addActionListener(e -> updateFontPreview());

        lineSpacingBox = new JComboBox<>(new String[]{"1.0", "1.2", "1.5"});
        lineSpacingBox.setUI(new ModernComboBoxUI());
        lineSpacingBox.setRenderer(new ModernComboBoxUI.ModernComboBoxRenderer());
        lineSpacingBox.setSelectedItem(store.getEditorLineSpacing());
        lineSpacingBox.addActionListener(e -> updateFontPreview());

        gc.gridx = 0; gc.gridy = 10; add(SettingsUi.label("Font:"), gc);
        gc.gridx = 1; add(fontFamilyBox, gc);
        gc.gridx = 0; gc.gridy = 11; add(SettingsUi.label("Size:"), gc);
        gc.gridx = 1; add(fontSizeBox, gc);
        gc.gridx = 0; gc.gridy = 12; add(SettingsUi.label("Line spacing:"), gc);
        gc.gridx = 1; add(lineSpacingBox, gc);

        // Live preview panel
        fontPreview = new JTextPane();
        fontPreview.setText("The quick brown fox jumps over the lazy dog.\nHow vexingly quick daft zebras jump!");
        fontPreview.setEditable(false);
        fontPreview.setBackground(new Color(252, 252, 250));
        fontPreview.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        fontPreview.setPreferredSize(new Dimension(300, 80));
        updateFontPreview();

        gc.gridx = 0; gc.gridy = 13; gc.gridwidth = 2;
        gc.fill = GridBagConstraints.BOTH;
        add(fontPreview, gc);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.gridwidth = 1;
    }

    private void updateFontPreview() {
        String family = (String) fontFamilyBox.getSelectedItem();
        Integer size = (Integer) fontSizeBox.getSelectedItem();
        String spacingStr = (String) lineSpacingBox.getSelectedItem();
        if (family == null) family = "Serif";
        if (size == null) size = 16;
        float spacing = switch (spacingStr) { case "1.2" -> 0.2f; case "1.5" -> 0.5f; default -> 0.0f; };

        fontPreview.setFont(new Font(family, Font.PLAIN, size));
        try {
            StyledDocument doc = fontPreview.getStyledDocument();
            MutableAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setLineSpacing(attrs, spacing);
            doc.setParagraphAttributes(0, doc.getLength(), attrs, false);
        } catch (Exception ignored) {}
    }

    @Override public JComponent getComponent() { return this; }

    @Override public void apply() {
        SettingsStore store = SettingsStore.get();
        String theme = (String) themeBox.getSelectedItem();
        store.setTheme(theme);
        // Theme is applied live via SettingsPanel.saveAll() -> AeroLookAndFeel.apply()

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

        boolean glow = glowChk.isSelected();
        store.setGlowEnabled(glow);
        FadingButton.setGlowEnabled(glow);
        main.ui.components.buttons.ToolbarIconButton.setGlowEnabled(glow);

        store.setAnimationsDisabled(disableAnimationsChk.isSelected());

        store.setMainMenuAnimationsDisabled(disableMainMenuAnimationsChk.isSelected());

        boolean lp = lowPowerChk.isSelected();
        store.setLowPowerMode(lp);
        AppPerf.setLowPowerMode(lp);

        // Editor Font settings
        String fontFamily = (String) fontFamilyBox.getSelectedItem();
        Integer fontSize = (Integer) fontSizeBox.getSelectedItem();
        String lineSpacing = (String) lineSpacingBox.getSelectedItem();
        if (fontFamily != null) store.setEditorFontFamily(fontFamily);
        if (fontSize != null) {
            store.setJournalFontSize(fontSize);
            store.setPoemFontSize(fontSize);
        }
        if (lineSpacing != null) store.setEditorLineSpacing(lineSpacing);
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
}
