package main.ui.components.util;

import main.ui.app.JournalApp;
import main.ui.components.buttons.ToolbarIconButton;
import main.ui.components.buttons.ToolbarMenuIconButton;
import main.infrastructure.backup.NotebookInfo;

/**
 * Utility methods for creating common editor toolbar buttons
 * to ensure consistent look & behavior across panels.
 * Used in editor panels.
 * 
 * @author S1mplector
 */
public final class EditorUIUtils {
    private EditorUIUtils() {}

    public static ToolbarIconButton createBackButton(JournalApp app) {
        ToolbarMenuIconButton back = new ToolbarMenuIconButton("Back", "back");
        back.setToolTipText("Back to Main Menu");
        back.addActionListener(e -> app.switchCard(JournalApp.MAIN_MENU));
        return back;
    }

    public static ToolbarIconButton createSaveButton(String tooltip, Runnable onSave) {
        ToolbarMenuIconButton save = new ToolbarMenuIconButton("Save", "save");
        if (tooltip != null && !tooltip.isEmpty()) {
            save.setToolTipText(tooltip);
        }
        save.addActionListener(e -> onSave.run());
        return save;
    }

    /**
     * Back button that returns to the entries manager for the given notebook.
     */
    public static ToolbarIconButton createBackToEntriesButton(JournalApp app, NotebookInfo nb) {
        ToolbarMenuIconButton back = new ToolbarMenuIconButton("Back", "back");
        back.setToolTipText("Back to Entries");
        back.addActionListener(e -> app.openNotebookEntries(nb));
        return back;
    }
}
