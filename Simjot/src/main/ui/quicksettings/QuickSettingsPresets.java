package main.ui.quicksettings;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class QuickSettingsPresets {

    public static List<QuickSettingsCategory> defaultCategories(QuickSettingsController.HostApi host) {
        List<QuickSettingsCategory> list = new ArrayList<>();

        // Five categories with simple glyph icons
        list.add(new QuickSettingsCategory("General", new GlyphIcon("⚙", 18, new Color(255,255,255)), () -> buildGeneralPanel(host)));
        list.add(new QuickSettingsCategory("Widgets", new GlyphIcon("🧩", 18, new Color(255,255,255)), () -> buildWidgetsPanel(host)));
        list.add(new QuickSettingsCategory("Display", new GlyphIcon("🖥", 18, new Color(255,255,255)), () -> buildDisplayPanel())) ;
        list.add(new QuickSettingsCategory("Audio", new GlyphIcon("🔊", 18, new Color(255,255,255)), () -> buildAudioPanel()));
        list.add(new QuickSettingsCategory("Shortcuts", new GlyphIcon("⌨", 18, new Color(255,255,255)), () -> buildShortcutsPanel()));

        return list;
    }

    private static JComponent buildGeneralPanel(QuickSettingsController.HostApi host) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("General Settings");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(title);
        p.add(Box.createRigidArea(new Dimension(0, 8)));

        JLabel hint = new JLabel("Middle-click anywhere to open quick settings.");
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(hint);

        return p;
    }

    private static JComponent buildWidgetsPanel(QuickSettingsController.HostApi host) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Widgets Utility");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(title);
        p.add(Box.createRigidArea(new Dimension(0, 6)));

        JCheckBox toggle = new JCheckBox("Show widgets panel", host.isWidgetsPanelVisible());
        toggle.setOpaque(false);
        toggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        toggle.addActionListener(e -> host.setWidgetsPanelVisible(toggle.isSelected()));
        p.add(toggle);

        return p;
    }

    private static JComponent buildDisplayPanel() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Display");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(title);
        p.add(Box.createRigidArea(new Dimension(0, 6)));

        JCheckBox dark = new JCheckBox("Dark mode");
        dark.setOpaque(false);
        dark.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(dark);

        JCheckBox animations = new JCheckBox("Enable UI animations", true);
        animations.setOpaque(false);
        animations.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(animations);

        return p;
    }

    private static JComponent buildAudioPanel() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Audio");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(title);
        p.add(Box.createRigidArea(new Dimension(0, 6)));

        JLabel volLabel = new JLabel("Volume");
        volLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(volLabel);
        JSlider volume = new JSlider(0, 100, 70);
        volume.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(volume);

        JCheckBox mute = new JCheckBox("Mute");
        mute.setOpaque(false);
        mute.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(mute);

        return p;
    }

    private static JComponent buildShortcutsPanel() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Shortcuts");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(title);
        p.add(Box.createRigidArea(new Dimension(0, 6)));

        JLabel help = new JLabel("Tip: Press ESC to close quick settings.");
        help.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(help);

        return p;
    }
}
