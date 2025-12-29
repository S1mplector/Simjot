package main.ui.components.toolbars;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import main.infrastructure.backup.NotebookInfo;
import main.ui.app.JournalApp;
import main.ui.components.buttons.RoundedToggleButton;
import main.ui.components.buttons.ToolbarIconButton;
import main.ui.components.fields.ModernTextField;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.util.EditorUIUtils;

/**
 * Reusable poetry-style editor toolbar:
 * - Left: Back button, Title label, Title field
 * - Center: B / I / U / S formatting buttons
 * - Right: caller-provided controls (e.g., stats/rhymes/export/fullscreen/settings)
 *
 * Font settings are now in Appearance settings.
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
            Consumer<Boolean> onBold,
            Consumer<Boolean> onItalic,
            Consumer<Boolean> onUnderline,
            Consumer<Boolean> onStrike,
            Consumer<String> onFontFamily,
            Consumer<Integer> onFontSize,
            Consumer<String> onLineSpacing,
            JComponent rightToolbarControls
    ) {
        super(new BorderLayout(0, 5));
        setOpaque(false);

        container = new FrostedGlassPanel(new BorderLayout(0, 5), 16);
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

        // Assemble (font settings moved to Appearance settings)
        container.add(topToolbar, BorderLayout.CENTER);
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
