package main.ui.components.toolbars;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import main.infrastructure.backup.NotebookInfo;
import main.ui.app.JournalApp;
import main.ui.components.buttons.HandStyleToggleButton;
import main.ui.components.buttons.ToolbarIconButton;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.fields.TitleDividerField;
import main.ui.components.util.EditorUIUtils;

/**
 * Reusable poetry-style editor toolbar:
 * - Left: Back button, Title label, Title field
 * - Center: B / I / U / S formatting buttons
 * - Right: caller-provided controls (e.g., stats/rhymes/export/fullscreen/settings)
 *
 * Font settings are now in Appearance settings.
 * 
 * The rendering is kept lightweight using simple Swing operations like Graphics2D and GradientPaint.
 * This toolbar is used in the poetry and entry toolbars.
 */
public class PoetryStyleToolbar extends JPanel {
    private final JPanel container;
    private final TitleDividerField titleField;
    private HandStyleToggleButton boldBtn;
    private HandStyleToggleButton italicBtn;
    private HandStyleToggleButton underlineBtn;
    private HandStyleToggleButton strikeBtn;

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

        // Single-row toolbar (back + title + formatting + caller-provided controls)
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));

        ToolbarIconButton backButton = EditorUIUtils.createBackToEntriesButton(app, nbInfo);
        alignCenter(backButton);
        row.add(backButton);

        JLabel titleLabel = new JLabel(titleLabelText);
        titleLabel.setFont(new Font("Serif", Font.BOLD, 16));
        alignCenter(titleLabel);
        row.add(Box.createHorizontalStrut(8));
        row.add(titleLabel);

        titleField = new TitleDividerField(24);
        titleField.setFont(new Font("Serif", Font.BOLD, 16));
        // Directly set placeholder (avoid pattern matching instanceof for broader compatibility)
        titleField.setPlaceholder(titlePlaceholder);
        titleField.setPreferredSize(new Dimension(360, 32));
        titleField.setMaximumSize(new Dimension(10000, 34));
        alignCenter(titleField);
        row.add(Box.createHorizontalStrut(8));
        row.add(titleField);

        // Formatting buttons (RoundedToggleButton styling with selected highlight)
        boldBtn = new HandStyleToggleButton("B");
        boldBtn.addActionListener(e -> { if (onBold != null) onBold.accept(boldBtn.isSelected()); });
        italicBtn = new HandStyleToggleButton("I");
        italicBtn.addActionListener(e -> { if (onItalic != null) onItalic.accept(italicBtn.isSelected()); });
        underlineBtn = new HandStyleToggleButton("U");
        underlineBtn.addActionListener(e -> { if (onUnderline != null) onUnderline.accept(underlineBtn.isSelected()); });
        strikeBtn = new HandStyleToggleButton("S");
        strikeBtn.setToolTipText("Strikethrough");
        strikeBtn.addActionListener(e -> { if (onStrike != null) onStrike.accept(strikeBtn.isSelected()); });
        for (JComponent btn : new JComponent[]{boldBtn, italicBtn, underlineBtn, strikeBtn}) {
            alignCenter(btn);
        }
        row.add(Box.createHorizontalStrut(12));
        row.add(boldBtn);
        row.add(Box.createHorizontalStrut(6));
        row.add(italicBtn);
        row.add(Box.createHorizontalStrut(6));
        row.add(underlineBtn);
        row.add(Box.createHorizontalStrut(6));
        row.add(strikeBtn);

        row.add(Box.createHorizontalGlue());

        // Assemble (font settings moved to Appearance settings)
        if (rightToolbarControls != null) {
            alignCenter(rightToolbarControls);
            row.add(Box.createHorizontalStrut(10));
            row.add(rightToolbarControls);
        }
        container.add(row, BorderLayout.CENTER);
        add(container, BorderLayout.CENTER);
    }

    public JPanel getContainer() { return container; }
    public TitleDividerField getTitleField() { return titleField; }

    public void setToggleStates(boolean bold, boolean italic, boolean underline, boolean strike) {
        if (boldBtn != null) boldBtn.setSelected(bold);
        if (italicBtn != null) italicBtn.setSelected(italic);
        if (underlineBtn != null) underlineBtn.setSelected(underline);
        if (strikeBtn != null) strikeBtn.setSelected(strike);
    }

    private static void alignCenter(Component c) {
        if (c instanceof JComponent jc) {
            jc.setAlignmentY(Component.CENTER_ALIGNMENT);
        }
    }
}
