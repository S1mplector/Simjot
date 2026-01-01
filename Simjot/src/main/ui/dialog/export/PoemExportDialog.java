/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.dialog.export;

import main.core.export.PoemExporter;
import main.ui.components.combobox.ModernComboBoxUI;
import main.ui.components.checkbox.ModernCheckBoxUI;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.fields.ModernTextField;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.dialog.file.SimjotFileChooser;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class PoemExportDialog extends JDialog {
    private boolean confirmed = false;
    private final JComboBox<PoemExporter.Format> formatBox = new JComboBox<>(PoemExporter.Format.values());
    private final JCheckBox includeTitle = new JCheckBox("Include title", true);
    private final JCheckBox includeMetadata = new JCheckBox("Include metadata (timestamp)", true);
    private final JCheckBox includeStats = new JCheckBox("Include basic stats", false);
    private final JCheckBox lineNumbers = new JCheckBox("Line numbers", false);
    private final JComboBox<String> htmlTheme = new JComboBox<>(new String[]{"light", "dark"});
    private final ModernTextField pathField = new ModernTextField(28);
    private File initialDir;
    private String suggestedBaseName;

    public PoemExportDialog(Window owner) {
        super(owner, "Export Poem", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        buildUI();
        pack();
        setLocationRelativeTo(owner);
    }

    public PoemExportDialog(Window owner, File initialDir, String suggestedBaseName) {
        this(owner);
        this.initialDir = initialDir;
        this.suggestedBaseName = suggestedBaseName;
    }

    private void buildUI() {
        FrostedGlassPanel root = new FrostedGlassPanel(new BorderLayout(10, 10), 16);
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel opts = new JPanel();
        opts.setLayout(new BoxLayout(opts, BoxLayout.Y_AXIS));
        opts.setOpaque(false);

        // Save location row
        JPanel rowSave = new JPanel(new FlowLayout(FlowLayout.LEFT));
        rowSave.setOpaque(false);
        rowSave.add(new JLabel("Save to:"));
        pathField.setPlaceholder("Choose export location...");
        rowSave.add(pathField);
        JButton browseBtn = new RoundedButton("Browse");
        browseBtn.addActionListener(ignored -> openFileChooser());
        rowSave.add(browseBtn);
        opts.add(rowSave);

        JPanel rowFmt = new JPanel(new FlowLayout(FlowLayout.LEFT));
        rowFmt.setOpaque(false);
        rowFmt.add(new JLabel("Format:"));
        formatBox.setUI(new ModernComboBoxUI());
        formatBox.setRenderer(new ModernComboBoxUI.ModernComboBoxRenderer());
        rowFmt.add(formatBox);
        opts.add(rowFmt);

        // Apply modern checkbox UI styling
        for (JCheckBox cb : new JCheckBox[]{includeTitle, includeMetadata, includeStats, lineNumbers}) {
            cb.setUI(new ModernCheckBoxUI());
            cb.setOpaque(false);
            opts.add(cb);
        }

        JPanel rowTheme = new JPanel(new FlowLayout(FlowLayout.LEFT));
        rowTheme.setOpaque(false);
        rowTheme.add(new JLabel("HTML Theme:"));
        htmlTheme.setUI(new ModernComboBoxUI());
        htmlTheme.setRenderer(new ModernComboBoxUI.ModernComboBoxRenderer());
        rowTheme.add(htmlTheme);
        opts.add(rowTheme);

        // Enable/disable HTML theme based on format selection
        formatBox.addActionListener(ignored -> updateControls());
        updateControls();

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setOpaque(false);
        JButton cancel = new RoundedButton("Cancel");
        JButton export = new RoundedButton("Export");
        cancel.addActionListener(ignored -> { confirmed = false; dispose(); });
        export.addActionListener(ignored -> { confirmed = true; dispose(); });
        buttons.add(cancel);
        buttons.add(export);

        root.add(opts, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private void updateControls() {
        boolean isHtml = getSelectedFormat() == PoemExporter.Format.HTML;
        htmlTheme.setEnabled(isHtml);
    }

    public boolean isConfirmed() { return confirmed; }
    public PoemExporter.Format getSelectedFormat() { return (PoemExporter.Format) formatBox.getSelectedItem(); }

    private void openFileChooser() {
        SimjotFileChooser chooser = new SimjotFileChooser(SwingUtilities.getWindowAncestor(this), "Choose export location");
        chooser.setMode(SimjotFileChooser.Mode.SAVE);
        if (initialDir != null && initialDir.isDirectory()) {
            chooser.setCurrentDirectory(initialDir);
        }
        chooser.setSuggestedFileName(getSuggestedFileNameForCurrentFormat());
        File f = chooser.showDialog();
        if (f != null) {
            pathField.setText(f.getAbsolutePath());
        }
    }

    private String getSuggestedFileNameForCurrentFormat() {
        PoemExporter.Format fmt = getSelectedFormat();
        String base = (suggestedBaseName == null || suggestedBaseName.isBlank()) ? "poem_export" : suggestedBaseName;
        String ext = switch (fmt) {
            case MARKDOWN -> ".md";
            case HTML -> ".html";
            case TXT -> ".txt";
            case PNG -> ".png";
        };
        return base + ext;
    }

    public File getOutputFile(File defaultOut, PoemExporter.Format fmt) {
        String txt = pathField.getText();
        if (txt == null || txt.isBlank()) return defaultOut;
        File f = new File(txt);
        // Append extension if missing
        String name = f.getName();
        if (!name.contains(".")) {
            String ext = switch (fmt) {
                case MARKDOWN -> ".md";
                case HTML -> ".html";
                case TXT -> ".txt";
                case PNG -> ".png";
            };
            f = new File(f.getParentFile() != null ? f.getParentFile() : initialDir, name + ext);
        }
        return f;
    }

    public PoemExporter.Options getOptions() {
        PoemExporter.Options o = new PoemExporter.Options();
        o.includeTitle = includeTitle.isSelected();
        o.includeMetadata = includeMetadata.isSelected();
        o.includeStats = includeStats.isSelected();
        o.lineNumbers = lineNumbers.isSelected();
        Object theme = htmlTheme.getSelectedItem();
        o.htmlTheme = theme == null ? "light" : theme.toString();
        return o;
    }
}
