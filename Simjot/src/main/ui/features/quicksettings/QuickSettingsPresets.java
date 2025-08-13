package main.ui.features.quicksettings;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import main.core.service.SettingsStore;
import main.infrastructure.backup.BackupService;
import main.ui.components.checkbox.ModernCheckBoxUI;
import main.ui.components.combobox.ModernComboBoxUI;
import main.ui.components.spinner.ModernSpinnerUI;

public class QuickSettingsPresets {

    public static List<QuickSettingsCategory> defaultCategories(QuickSettingsController.HostApi host) {
        List<QuickSettingsCategory> list = new ArrayList<>();

        // Four categories per user request
        list.add(new QuickSettingsCategory("Appearance", new GlyphIcon("🎨", 18, Color.WHITE), () -> buildAppearancePanel()));
        list.add(new QuickSettingsCategory("Writing & Editor", new GlyphIcon("✍", 18, Color.WHITE), () -> buildEditorPanel()));
        list.add(new QuickSettingsCategory("Widgets", new GlyphIcon("🧩", 18, Color.WHITE), () -> buildWidgetsPanel(host)));
        list.add(new QuickSettingsCategory("Backup & Data", new GlyphIcon("💾", 18, Color.WHITE), () -> buildBackupPanel()));

        return list;
    }

    private static JComponent buildAppearancePanel() {
        SettingsStore store = SettingsStore.get();
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Appearance");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(title);
        p.add(Box.createRigidArea(new Dimension(0, 6)));

        // Enable / disable UI animations (maps to inverse of disable flag)
        boolean enabled = !store.isAnimationsDisabled();
        JCheckBox animations = new JCheckBox("Enable UI animations", enabled);
        animations.setUI(new ModernCheckBoxUI());
        animations.setOpaque(false);
        animations.setAlignmentX(Component.LEFT_ALIGNMENT);
        animations.addActionListener(e -> {
            store.setAnimationsDisabled(!animations.isSelected());
            store.save();
        });
        p.add(animations);

        JCheckBox lowPower = new JCheckBox("Low Power Mode", store.isLowPowerMode());
        lowPower.setUI(new ModernCheckBoxUI());
        lowPower.setOpaque(false);
        lowPower.setAlignmentX(Component.LEFT_ALIGNMENT);
        lowPower.addActionListener(e -> {
            store.setLowPowerMode(lowPower.isSelected());
            store.save();
        });
        p.add(lowPower);

        return p;
    }

    private static JComponent buildWidgetsPanel(QuickSettingsController.HostApi host) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Widgets");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(title);
        p.add(Box.createRigidArea(new Dimension(0, 6)));

        JCheckBox toggle = new JCheckBox("Show widgets panel", host.isWidgetsPanelVisible());
        toggle.setUI(new ModernCheckBoxUI());
        toggle.setOpaque(false);
        toggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        toggle.addActionListener(e -> host.setWidgetsPanelVisible(toggle.isSelected()));
        p.add(toggle);

        // Note: Per-widget enable/disable controls are temporarily removed.

        return p;
    }

    private static JComponent buildEditorPanel() {
        SettingsStore store = SettingsStore.get();
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Writing & Editor");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(title);
        p.add(Box.createRigidArea(new Dimension(0, 6)));

        // Journal font size
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row1.setOpaque(false);
        row1.add(new JLabel("Journal font size"));
        int jf = store.getJournalFontSize();
        JSpinner js1 = new JSpinner(new SpinnerNumberModel(jf, 8, 48, 1));
        js1.setUI(new ModernSpinnerUI());
        js1.addChangeListener(e -> { store.setJournalFontSize((Integer) js1.getValue()); store.save(); });
        row1.add(js1);
        row1.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(row1);

        // Poem font size
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row2.setOpaque(false);
        row2.add(new JLabel("Poem font size"));
        int pf = store.getPoemFontSize();
        JSpinner js2 = new JSpinner(new SpinnerNumberModel(pf, 8, 72, 1));
        js2.setUI(new ModernSpinnerUI());
        js2.addChangeListener(e -> { store.setPoemFontSize((Integer) js2.getValue()); store.save(); });
        row2.add(js2);
        row2.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(row2);

        // Autosave interval (minutes; 0 = off)
        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row3.setOpaque(false);
        row3.add(new JLabel("Autosave interval (min)"));
        int as = store.getAutosaveMinutes();
        JSpinner js3 = new JSpinner(new SpinnerNumberModel(as, 0, 120, 1));
        js3.setUI(new ModernSpinnerUI());
        js3.addChangeListener(e -> { store.setAutosaveMinutes((Integer) js3.getValue()); store.save(); });
        row3.add(js3);
        row3.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(row3);

        return p;
    }

    private static JComponent buildBackupPanel() {
        SettingsStore store = SettingsStore.get();
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Backup & Data");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(title);
        p.add(Box.createRigidArea(new Dimension(0, 6)));

        // Backup frequency
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row1.setOpaque(false);
        row1.add(new JLabel("Backup frequency"));
        String[] options = {"Off", "Daily", "Weekly", "Monthly"};
        JComboBox<String> freq = new JComboBox<>(options);
        freq.setUI(new ModernComboBoxUI());
        freq.setSelectedItem(store.getBackupFrequency());
        freq.addActionListener(e -> {
            store.setBackupFrequency((String) freq.getSelectedItem());
            store.save();
            // refresh scheduler
            BackupService.get().start();
        });
        row1.add(freq);
        row1.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(row1);

        // Keep last N backups
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row2.setOpaque(false);
        row2.add(new JLabel("Keep last N backups"));
        int keep = store.getBackupKeepCount();
        JSpinner keepSpin = new JSpinner(new SpinnerNumberModel(keep, 1, 60, 1));
        keepSpin.setUI(new ModernSpinnerUI());
        keepSpin.addChangeListener(e -> { store.setBackupKeepCount((Integer) keepSpin.getValue()); store.save(); });
        row2.add(keepSpin);
        row2.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(row2);

        // Backup Now
        JButton backupNow = new JButton("Backup Now");
        backupNow.setAlignmentX(Component.LEFT_ALIGNMENT);
        backupNow.addActionListener(e -> BackupService.get().triggerNow());
        p.add(Box.createRigidArea(new Dimension(0, 6)));
        p.add(backupNow);

        return p;
    }
    // No shortcuts panel in quick settings per latest spec
}
