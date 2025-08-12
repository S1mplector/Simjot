package main.ui.features.settings;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Font;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import main.infrastructure.io.AppDirectories;
import main.ui.components.buttons.RoundedButton;
import main.ui.dialog.message.CustomMessageDialog;

class StorageSettingsPage extends JPanel implements SettingsPage {
    private final JLabel pathLbl;
    private final RoundedButton clearThumbsBtn;

    StorageSettingsPage() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(true);
        setBackground(Color.WHITE);
        add(new JLabel("Simjot root folder:"));
        pathLbl = new JLabel(AppDirectories.getRoot().getAbsolutePath());
        pathLbl.setFont(new Font("Monospaced", Font.PLAIN, 12));
        add(pathLbl);

        RoundedButton openBtn = new RoundedButton("Open in Explorer");
        openBtn.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(AppDirectories.getRoot());
            } catch (Exception ignored) {}
        });
        add(openBtn);

        clearThumbsBtn = new RoundedButton("Clear thumbnails cache");
        clearThumbsBtn.addActionListener(e -> clearThumbs());
        add(clearThumbsBtn);
    }

    @Override public JComponent getComponent() { return this; }
    @Override public void apply() {}

    private void clearThumbs() {
        java.io.File dir = AppDirectories.folder(AppDirectories.Type.DRAWINGS);
        int deleted = 0;
        java.io.File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".png"));
        if (files != null) {
            for (java.io.File f : files) {
                if (f.delete()) deleted++;
            }
        }
        CustomMessageDialog.display(this, "Cleanup", deleted + " thumbnails deleted.", false);
    }
}
