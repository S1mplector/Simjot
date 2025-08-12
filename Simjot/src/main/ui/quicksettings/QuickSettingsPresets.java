package main.ui.quicksettings;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class QuickSettingsPresets {

    public static List<QuickSettingsCategory> defaultCategories(QuickSettingsController.HostApi host) {
        List<QuickSettingsCategory> list = new ArrayList<>();

        list.add(new QuickSettingsCategory("General", null, () -> buildGeneralPanel(host)));
        list.add(new QuickSettingsCategory("Widgets", null, () -> buildWidgetsPanel(host)));
        // Add more categories here later

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
}
