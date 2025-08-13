package main.ui.dialog.setup;

import java.awt.*;
import java.io.File;
import javax.swing.*;
import main.core.service.SettingsStore;
import main.infrastructure.io.AppDirectories;
import main.infrastructure.io.ResourceLoader;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.icons.ModernFileIcons;
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
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Welcome to Simjot!", JLabel.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel desc = new JLabel("Choose where you would like to store your Simjot files.");
        desc.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Brief feature overview (HTML bullet list for nice formatting)
        String overview = "<html><div style='width:300px'>" +
                "<ul style='margin-top:0'>" +
                "<li>Rich journal & note editor (bold, images, colours)</li>" +
                "<li>Poetry mode with custom backgrounds</li>" +
                "<li>Drawing studio with layers & calligraphy brush</li>" +
                "<li>Mood tracking charts</li>" +
                "<li>Gallery for your saved artwork</li>" +
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
            // --- Modernize JFileChooser appearance and bottom section ---
            UIManager.put("Button.background", new Color(245, 245, 245));
            UIManager.put("Button.font", new Font("Segoe UI", Font.PLAIN, 15));
            UIManager.put("Button.border", BorderFactory.createLineBorder(new Color(220,220,220), 1, true));
            UIManager.put("Button.focus", new Color(0,120,215,60));
            UIManager.put("FileChooser.cancelButtonText", "Cancel");
            UIManager.put("FileChooser.openButtonText", "Select");
            UIManager.put("FileChooser.saveButtonText", "Save");
            UIManager.put("FileChooser.updateButtonText", "Update");
            UIManager.put("FileChooser.font", new Font("Segoe UI", Font.PLAIN, 15));
            UIManager.put("FileChooser.background", Color.WHITE);
            UIManager.put("Panel.background", Color.WHITE);

            // Modernize JFileChooser dropdown (combobox) UI
            UIManager.put("ComboBox.background", Color.WHITE);
            UIManager.put("ComboBox.foreground", Color.DARK_GRAY);
            UIManager.put("ComboBox.selectionBackground", new Color(230, 240, 255));
            UIManager.put("ComboBox.selectionForeground", Color.BLACK);
            UIManager.put("ComboBox.border", BorderFactory.createLineBorder(new Color(220,220,220), 1, true));
            UIManager.put("ComboBox.font", new Font("Segoe UI", Font.PLAIN, 15));
            UIManager.put("ComboBox.buttonBackground", new Color(245, 245, 245));
            UIManager.put("ComboBox.buttonHighlight", new Color(230, 240, 255));
            UIManager.put("ComboBox.buttonDarkShadow", new Color(200, 200, 200));
            UIManager.put("ComboBox.buttonShadow", new Color(220, 220, 220));
            UIManager.put("ComboBox.buttonForeground", new Color(120, 120, 120));

            // Modernize JFileChooser scrollbar UI
            UIManager.put("ScrollBar.thumb", new Color(210, 225, 245));
            UIManager.put("ScrollBar.thumbHighlight", new Color(180, 200, 230));
            UIManager.put("ScrollBar.thumbDarkShadow", new Color(180, 200, 230));
            UIManager.put("ScrollBar.thumbShadow", new Color(180, 200, 230));
            UIManager.put("ScrollBar.track", new Color(245, 245, 245));
            UIManager.put("ScrollBar.trackHighlight", new Color(230, 240, 255));
            UIManager.put("ScrollBar.background", Color.WHITE);
            UIManager.put("ScrollBar.foreground", new Color(120, 120, 120));
            UIManager.put("ScrollBar.width", 12);
            UIManager.put("ScrollBar.thumbHeight", 32);
            UIManager.put("ScrollBar.thumbBorder", BorderFactory.createLineBorder(new Color(180, 200, 230), 1, true));

            // Custom icons for JFileChooser
            UIManager.put("FileView.directoryIcon", new javax.swing.ImageIcon(ModernFileIcons.createFolderIcon()));
            UIManager.put("FileView.fileIcon", new javax.swing.ImageIcon(ModernFileIcons.createFileIcon()));
            UIManager.put("FileChooser.upFolderIcon", new javax.swing.ImageIcon(ModernFileIcons.createUpIcon()));
            UIManager.put("FileChooser.homeFolderIcon", new javax.swing.ImageIcon(ModernFileIcons.createHomeIcon()));
            UIManager.put("FileChooser.newFolderIcon", new javax.swing.ImageIcon(ModernFileIcons.createNewFolderIcon()));

            // --- Custom ScrollBarUI for modern, round, smooth scrollbars ---
            UIManager.put("ScrollBarUI", "main.ui.components.scrollbar.ModernScrollBarUI");

            // --- Modernize bottom section (folder name, file type) ---
            UIManager.put("FileChooser.textFieldBackground", Color.WHITE);
            UIManager.put("FileChooser.textFieldForeground", Color.DARK_GRAY);
            UIManager.put("FileChooser.textFieldBorder", BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(220,220,220), 1, true),
                    BorderFactory.createEmptyBorder(6, 10, 6, 10)));
            UIManager.put("FileChooser.textFieldFont", new Font("Segoe UI", Font.PLAIN, 15));
            UIManager.put("FileChooser.labelForeground", new Color(80, 80, 80));
            UIManager.put("FileChooser.labelFont", new Font("Segoe UI", Font.PLAIN, 14));
            UIManager.put("FileChooser.usesSingleFilePane", Boolean.TRUE); // Use single pane layout for more control

            // --- Custom rendering for bottom section (folder name, file type) ---
            // This is a hack: after dialog is shown, walk the component tree and adjust spacing/padding
            DirectoryChooserDialog dirDlg = new DirectoryChooserDialog((JFrame) SwingUtilities.getWindowAncestor(SetupWizardDialog.this)) {
                @Override
                public void setVisible(boolean b) {
                    super.setVisible(b);
                    if (b) {
                        SetupWizardDialog.updateFileChooserBottomSection(this);
                    }
                }
            };
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

    /**
     * Recursively update the bottom section of a JFileChooser/DirectoryChooserDialog for modern look.
     * Applies padding, font, and border to text fields, combo boxes, and labels.
     */
    public static void updateFileChooserBottomSection(Container chooser) {
        Font modernFont = new Font("Segoe UI", Font.PLAIN, 15);
        for (Component c : chooser.getComponents()) {
            if (c instanceof JTextField tf) {
                tf.setFont(modernFont);
                tf.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(220,220,220), 1, true),
                        BorderFactory.createEmptyBorder(6, 8, 6, 8)));
            } else if (c instanceof JComboBox<?> cb) {
                cb.setFont(modernFont);
                cb.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(220,220,220), 1, true),
                        BorderFactory.createEmptyBorder(2, 8, 2, 8)));
            } else if (c instanceof JLabel lbl) {
                lbl.setFont(modernFont);
            } else if (c instanceof Container cont) {
                updateFileChooserBottomSection(cont);
            }
        }
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