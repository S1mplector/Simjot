package main.ui.features.entries;

import main.ui.app.JournalApp;
import main.ui.components.editor.ImagePasteManager;
import main.ui.components.editor.RichTextStyler;

import javax.swing.*;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.io.File;

/**
 * Notetaking editor: extends the standard EntryPanel with
 * enhanced image pasting (wider max width) and richer formatting shortcuts.
 */
public class NotetakingPanel extends EntryPanel {

    public NotetakingPanel(JournalApp app, File journalFolder, CardLayout cardLayout, JPanel cardPanel) {
        super(app, journalFolder, cardLayout, cardPanel);
        // Reinstall image paste support with a larger max width for notes
        try {
            ImagePasteManager.install(
                    contentArea,
                    () -> new File(journalFolder, "attachments"),
                    1200 // larger default width for pasted images
            );
        } catch (Throwable ignored) {}
        // Add richer formatting accelerators on top of base behavior
        installAdvancedFormattingShortcuts();
    }
    
    @Override
    protected boolean supportsMoodControls() {
        return false;
    }

    @Override
    protected boolean supportsClockButton() {
        return false;
    }

    @Override
    protected boolean supportsGuidanceButton() {
        return false;
    }

    private void installAdvancedFormattingShortcuts() {
        int meta = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        javax.swing.InputMap im = contentArea.getInputMap(JComponent.WHEN_FOCUSED);
        javax.swing.ActionMap am = contentArea.getActionMap();

        im.put(KeyStroke.getKeyStroke('B', meta), "ntk-bold");
        am.put("ntk-bold", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (contentArea.getSelectionStart() != contentArea.getSelectionEnd())
                    RichTextStyler.toggleSelectionBold(contentArea);
                else
                    RichTextStyler.setTypingBold(contentArea, !RichTextStyler.getTypingState(contentArea).bold());
            }
        });
        im.put(KeyStroke.getKeyStroke('I', meta), "ntk-italic");
        am.put("ntk-italic", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (contentArea.getSelectionStart() != contentArea.getSelectionEnd())
                    RichTextStyler.toggleSelectionItalic(contentArea);
                else
                    RichTextStyler.setTypingItalic(contentArea, !RichTextStyler.getTypingState(contentArea).italic());
            }
        });
        im.put(KeyStroke.getKeyStroke('U', meta), "ntk-underline");
        am.put("ntk-underline", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (contentArea.getSelectionStart() != contentArea.getSelectionEnd())
                    RichTextStyler.toggleSelectionUnderline(contentArea);
                else
                    RichTextStyler.setTypingUnderline(contentArea, !RichTextStyler.getTypingState(contentArea).underline());
            }
        });
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X, meta | java.awt.event.InputEvent.SHIFT_DOWN_MASK), "ntk-strike");
        am.put("ntk-strike", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (contentArea.getSelectionStart() != contentArea.getSelectionEnd())
                    RichTextStyler.toggleSelectionStrike(contentArea);
                else
                    RichTextStyler.setTypingStrike(contentArea, !RichTextStyler.getTypingState(contentArea).strike());
            }
        });

        // Headings: Cmd/Ctrl+1/2/3 and +0 to reset
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_1, meta), "ntk-h1");
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_2, meta), "ntk-h2");
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_3, meta), "ntk-h3");
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_0, meta), "ntk-p");
        am.put("ntk-h1", new AbstractAction(){ @Override public void actionPerformed(java.awt.event.ActionEvent e){ applyFontSizeToSelection(22f, true); }});
        am.put("ntk-h2", new AbstractAction(){ @Override public void actionPerformed(java.awt.event.ActionEvent e){ applyFontSizeToSelection(18f, true); }});
        am.put("ntk-h3", new AbstractAction(){ @Override public void actionPerformed(java.awt.event.ActionEvent e){ applyFontSizeToSelection(16f, true); }});
        am.put("ntk-p",  new AbstractAction(){ @Override public void actionPerformed(java.awt.event.ActionEvent e){ applyFontSizeToSelection(14f, false); }});
    }

    private void applyFontSizeToSelection(float size, boolean bold) {
        try {
            int start = contentArea.getSelectionStart();
            int end = contentArea.getSelectionEnd();
            if (start == end) {
                // typing attributes
                MutableAttributeSet attrs = new SimpleAttributeSet(((javax.swing.text.StyledEditorKit) contentArea.getEditorKit()).getInputAttributes());
                StyleConstants.setFontSize(attrs, Math.round(size));
                StyleConstants.setBold(attrs, bold);
                contentArea.setCharacterAttributes(attrs, true);
                return;
            }
            StyledDocument doc = contentArea.getStyledDocument();
            MutableAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setFontSize(attrs, Math.round(size));
            StyleConstants.setBold(attrs, bold);
            doc.setCharacterAttributes(start, end - start, attrs, false);
        } catch (Throwable ignored) {}
    }

    @Override
    public String fileExtension() {
        return ".ntk";
    }
}
