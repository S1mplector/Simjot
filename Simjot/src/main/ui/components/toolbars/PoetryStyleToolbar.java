package main.ui.components.toolbars;

import java.awt.*;
import java.util.function.Consumer;
import javax.swing.*;
import main.ui.app.JournalApp;
import main.ui.components.combobox.ModernComboBoxUI;
import main.ui.components.fields.ModernTextField;
import main.ui.components.buttons.RoundedToggleButton;
import main.ui.components.buttons.ToolbarIconButton;
import main.ui.components.util.EditorUIUtils;
import main.infrastructure.backup.NotebookInfo;

/**
 * Reusable two-row poetry-style editor toolbar:
 * - Left: Back button, Title label, Title field
 * - Center: B / I / U formatting buttons
 * - Bottom row: Font family, Size, Spacing selectors
 * - Right: caller-provided controls (e.g., stats/rhymes/export/fullscreen/settings)
 *
 * Behavior is provided via callbacks so callers (EntryPanel/PoemPanel) can wire to their editors.
 */
public class PoetryStyleToolbar extends JPanel {
    private final JPanel container;
    private final ModernTextField titleField;
    private RoundedToggleButton boldBtn;
    private RoundedToggleButton italicBtn;
    private RoundedToggleButton underlineBtn;
    private RoundedToggleButton strikeBtn;

    public PoetryStyleToolbar(
            JournalApp app,
            NotebookInfo nbInfo,
            String titleLabelText,
            String titlePlaceholder,
            java.util.function.Consumer<Boolean> onBold,
            java.util.function.Consumer<Boolean> onItalic,
            java.util.function.Consumer<Boolean> onUnderline,
            java.util.function.Consumer<Boolean> onStrike,
            Consumer<String> onFontFamily,
            Consumer<Integer> onFontSize,
            Consumer<String> onLineSpacing,
            JComponent rightToolbarControls
    ) {
        super(new BorderLayout(0, 5));
        setOpaque(false);

        container = new JPanel(new BorderLayout(0, 5));
        container.setOpaque(true);
        container.setBackground(new Color(0xE7, 0xE7, 0xE7));
        container.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top row (back + title + B/I/U)
        JPanel topToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topToolbar.setOpaque(false);

        ToolbarIconButton backButton = EditorUIUtils.createBackToEntriesButton(app, nbInfo);
        topToolbar.add(backButton);

        JLabel titleLabel = new JLabel(titleLabelText);
        titleLabel.setForeground(Color.DARK_GRAY);
        titleLabel.setFont(new Font("Serif", Font.BOLD, 16));
        topToolbar.add(Box.createHorizontalStrut(6));
        topToolbar.add(titleLabel);

        titleField = new ModernTextField(24);
        titleField.setFont(new Font("Serif", Font.BOLD, 16));
        // Directly set placeholder (avoid pattern matching instanceof for broader compatibility)
        titleField.setPlaceholder(titlePlaceholder);
        topToolbar.add(titleField);

        // Formatting buttons (RoundedToggleButton styling with selected highlight)
        Dimension btnSize = new Dimension(48, 28);
        boldBtn = new RoundedToggleButton("B");
        boldBtn.setPreferredSize(btnSize);
        boldBtn.setFocusPainted(false);
        boldBtn.addActionListener(e -> { if (onBold != null) onBold.accept(boldBtn.isSelected()); });
        italicBtn = new RoundedToggleButton("I");
        italicBtn.setPreferredSize(btnSize);
        italicBtn.setFocusPainted(false);
        italicBtn.addActionListener(e -> { if (onItalic != null) onItalic.accept(italicBtn.isSelected()); });
        underlineBtn = new RoundedToggleButton("U");
        underlineBtn.setPreferredSize(btnSize);
        underlineBtn.setFocusPainted(false);
        underlineBtn.addActionListener(e -> { if (onUnderline != null) onUnderline.accept(underlineBtn.isSelected()); });
        strikeBtn = new RoundedToggleButton("S");
        strikeBtn.setPreferredSize(btnSize);
        strikeBtn.setFocusPainted(false);
        strikeBtn.setToolTipText("Strikethrough");
        strikeBtn.addActionListener(e -> { if (onStrike != null) onStrike.accept(strikeBtn.isSelected()); });
        topToolbar.add(Box.createHorizontalStrut(6));
        topToolbar.add(boldBtn);
        topToolbar.add(italicBtn);
        topToolbar.add(underlineBtn);
        topToolbar.add(strikeBtn);

        // Bottom row (font/size/spacing)
        JPanel bottomToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottomToolbar.setOpaque(false);

        JLabel fontLabel = new JLabel("Font:");
        fontLabel.setForeground(Color.DARK_GRAY);
        fontLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        bottomToolbar.add(fontLabel);

        // Include a few handwriting-style faces so poetry can feel more personal.
        String[] fonts = {
                "Serif", "Georgia", "Garamond", "Baskerville",
                "Lucida Handwriting", "Segoe Script", "Comic Sans MS", "Bradley Hand", "Apple Chancery", "Cursive"
        };
        JComboBox<String> fontSelector = new JComboBox<>(fonts);
        fontSelector.setUI(new ModernComboBoxUI());
        fontSelector.setRenderer(new ModernComboBoxUI.ModernComboBoxRenderer());
        fontSelector.setSelectedItem("Serif");
        fontSelector.addActionListener(e -> {
            if (onFontFamily != null) {
                onFontFamily.accept((String) fontSelector.getSelectedItem());
            }
        });
        bottomToolbar.add(fontSelector);

        bottomToolbar.add(new JLabel(" Size:"));
        Integer[] sizes = {12, 14, 16, 18, 20, 22, 24, 28};
        JComboBox<Integer> sizeSelector = new JComboBox<>(sizes);
        sizeSelector.setUI(new ModernComboBoxUI());
        sizeSelector.setRenderer(new ModernComboBoxUI.ModernComboBoxRenderer());
        sizeSelector.setSelectedItem(16);
        sizeSelector.addActionListener(e -> {
            if (onFontSize != null) {
                Integer sz = (Integer) sizeSelector.getSelectedItem();
                if (sz != null) onFontSize.accept(sz);
            }
        });
        bottomToolbar.add(sizeSelector);

        bottomToolbar.add(new JLabel(" Spacing:"));
        JComboBox<String> spacing = new JComboBox<>(new String[]{"1.0", "1.2", "1.5"});
        spacing.setUI(new ModernComboBoxUI());
        spacing.setRenderer(new ModernComboBoxUI.ModernComboBoxRenderer());
        spacing.setSelectedIndex(0);
        spacing.addActionListener(e -> {
            if (onLineSpacing != null) onLineSpacing.accept((String) spacing.getSelectedItem());
        });
        bottomToolbar.add(spacing);

        // Assemble
        container.add(topToolbar, BorderLayout.NORTH);
        container.add(bottomToolbar, BorderLayout.CENTER);
        if (rightToolbarControls != null) {
            container.add(rightToolbarControls, BorderLayout.EAST);
        }
        add(container, BorderLayout.CENTER);
    }

    public JPanel getContainer() { return container; }
    public ModernTextField getTitleField() { return titleField; }

    public void setToggleStates(boolean bold, boolean italic, boolean underline, boolean strike) {
        if (boldBtn != null) boldBtn.setSelected(bold);
        if (italicBtn != null) italicBtn.setSelected(italic);
        if (underlineBtn != null) underlineBtn.setSelected(underline);
        if (strikeBtn != null) strikeBtn.setSelected(strike);
    }
}
