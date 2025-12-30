package main.ui.dialog.setup;

import java.awt.*;
import java.io.File;
import javax.swing.*;
import main.core.service.SettingsStore;
import main.infrastructure.io.AppDirectories;
import main.infrastructure.io.ResourceLoader;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.spinner.ModernSpinner;
import main.ui.dialog.confirmation.CustomConfirmDialog;

/**
 * Simple first-launch wizard that guides the user through choosing where the
 * Simjot root folder should live.  After the user picks a location we create
 * the standard sub-folders and expose the chosen root via {@link #getRootFolder()}.
 */
public class SetupWizardDialog extends JDialog {
    private File rootFolder;

    public SetupWizardDialog(JFrame owner) {
        super(owner, "Simjot – First-time setup", true);
        initUI();
    }

    public File getRootFolder() {
        return rootFolder;
    }

    private void initUI() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10,10));
        FrostedGlassPanel content = new FrostedGlassPanel(16);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

        JLabel title = new JLabel("Welcome to Simjot!", JLabel.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel desc = new JLabel("Choose where you would like Simjot to create its root folder.");
        desc.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Explain what the chosen root folder is used for and how to back it up
        String overview = "<html><div style='width:420px'>" +
                "<div style='margin-top:4px;margin-bottom:6px'>" +
                "Simjot will create a <b>Simjot</b> folder at the location you pick. " +
                "Everything you create is stored inside this folder, so it's easy to move or back up." +
                "</div>" +
                "<ul style='margin-top:0'>" +
                "<li>Inside it we'll create sub‑folders for your data: <i>entries</i>, <i>poems</i>, <i>mood data</i>, <i>settings</i>, and <i>images</i>.</li>" +
                "<li>You can keep it safe like any normal folder:</li>" +
                "<ul>" +
                "<li>Place it inside a cloud‑sync location (e.g. iCloud Drive, Dropbox, OneDrive) to sync across devices.</li>" +
                "<li>Or include it in system backups (e.g. Time Machine) or copy it to an external drive.</li>" +
                "</ul>" +
                "<li>If a <b>Simjot</b> folder already exists there, we'll reuse it.</li>" +
                "</ul>" +
                "</div></html>";
        JLabel featuresLabel = new JLabel(overview);
        featuresLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        RoundedButton chooseBtn = new RoundedButton("Choose Location…");
        chooseBtn.setPreferredSize(new Dimension(160, 36));

        RoundedButton docsBtn = new RoundedButton("Use Documents Folder");
        docsBtn.setPreferredSize(new Dimension(180, 36));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttonPanel.add(docsBtn);
        buttonPanel.add(chooseBtn);
        buttonPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        ModernSpinner spinner = new ModernSpinner(36, new Color(0,120,215));
        spinner.setAlignmentX(Component.CENTER_ALIGNMENT);
        spinner.setVisible(false);

        chooseBtn.addActionListener(e -> {
            spinner.setVisible(true);
            DirectoryChooserDialog dirDlg = new DirectoryChooserDialog((JFrame) SwingUtilities.getWindowAncestor(SetupWizardDialog.this));
            dirDlg.setVisible(true);
            File chosen = dirDlg.getSelectedDirectory();
            if (chosen != null) {
                rootFolder = new File(chosen, "Simjot");
                SwingUtilities.invokeLater(() -> performInitialSetup(spinner));
            } else {
                spinner.setVisible(false);
            }
        });

        docsBtn.addActionListener(e -> {
            spinner.setVisible(true);
            String documentsPath = javax.swing.filechooser.FileSystemView.getFileSystemView().getDefaultDirectory().getPath();
            rootFolder = new File(documentsPath, "Simjot");
            SwingUtilities.invokeLater(() -> performInitialSetup(spinner));
        });

        content.add(Box.createVerticalStrut(10));
        content.add(title);
        content.add(Box.createVerticalStrut(8));
        content.add(desc);
        content.add(Box.createVerticalStrut(12));
        content.add(featuresLabel);
        content.add(Box.createVerticalStrut(12));
        content.add(buttonPanel);
        content.add(Box.createVerticalStrut(12));
        content.add(spinner);
        content.add(Box.createVerticalStrut(10));

        add(content, BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(getOwner());
    }

    private void performInitialSetup(ModernSpinner spinner) {
        // Ensure directories exist
        if (!rootFolder.exists()) rootFolder.mkdirs();
        AppDirectories.setRoot(rootFolder);
        for (AppDirectories.Type t : AppDirectories.Type.values()) {
            AppDirectories.folder(t);
        }

        // Optionally create desktop shortcut (Windows-only)
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            boolean yes = CustomConfirmDialog.confirm(this, "Create Shortcut", "Would you like a Simjot shortcut on your Desktop?");
            if(yes){
                try{
                    createDesktopShortcut();
                }catch(Exception ex){ 
                    System.err.println("Failed to create desktop shortcut: " + ex.getMessage());
                }
            }
        }

        // Default to no wallpaper on first setup. The user can choose one later
        // from the appearance settings. No prompts shown here to streamline setup.
        SettingsStore.get().setBackgroundImage("");
        SettingsStore.get().save();

        spinner.setVisible(false);
        dispose(); // close dialog; caller will read rootFolder
    }

    // Creates a simple .url Internet Shortcut which launches the installed exe/jar
    private void createDesktopShortcut() throws Exception {
        // Determine exe or jar path
        String execPath;
        java.net.URI uri = SetupWizardDialog.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        execPath = new java.io.File(uri).getAbsolutePath();

        // If we are running from a jpackage installation "Simjot.exe" might be in same dir.
        // Otherwise point to the jar.
        String desktopPath = System.getProperty("user.home") + File.separator + "Desktop";
        File shortcut = new File(desktopPath, "Simjot.url");
        try(java.io.PrintWriter pw = new java.io.PrintWriter(shortcut)){
            pw.println("[InternetShortcut]");
            pw.println("URL=file:///" + execPath.replace("\\","/"));
            pw.println("IconFile=" + execPath.replace("\\","/"));
            pw.println("IconIndex=0");
        }
    }

    /**
     * Wallpaper chooser dialog that allows the user to select from a set of
     * predefined wallpapers or choose no wallpaper at all.
     * This dialog displays a grid of buttons
     * with thumbnail images of available wallpapers.
     * The user can click a button to select a wallpaper,
     * or click "No wallpaper" to clear the background.
     */
    private static class WallpaperChooser extends JDialog {
        private static final String[] BUILTIN = {
                "img/wallpapers/bg1.jpg",
                "img/wallpapers/bg2.jpg",
                "img/wallpapers/bg3.jpg"
        };

        WallpaperChooser(Dialog owner){
            super(owner, "Choose a wallpaper", true);
            setLayout(new BorderLayout(10,10));

            JPanel grid = new JPanel(new FlowLayout(FlowLayout.CENTER,10,10));
            for(String res : BUILTIN){
                Image img = ResourceLoader.createImage("Simjot/"+res);
                if(img==null) continue;
                Image thumb = img.getScaledInstance(200,112, Image.SCALE_SMOOTH);
                JButton btn = new JButton(new ImageIcon(thumb));
                btn.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY,2));
                btn.addActionListener(e->{
                    SettingsStore.get().setBackgroundImage("res:"+res);
                    dispose();
                });
                grid.add(btn);
            }

            RoundedButton none = new RoundedButton("No wallpaper");
            none.addActionListener(e->{
                SettingsStore.get().setBackgroundImage("");
                dispose();
            });
            JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER));
            south.setOpaque(false);
            south.add(none);

            add(new JScrollPane(grid), BorderLayout.CENTER);
            add(south, BorderLayout.SOUTH);

            pack();
            setLocationRelativeTo(owner);
        }
    }
}
