/*
 * SIMJOT - PROPRIETARY
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 * 
 * This source code is licensed under the Simjot Source-Available Personal Use License.
 * You may view and study this code for personal, non-commercial use.
 * Distribution, commercial use, and derivative works are strictly prohibited.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.util;

import main.infrastructure.backup.NotebookInfo;
import main.ui.app.JournalApp;
import main.ui.components.buttons.ToolbarIconButton;
import main.ui.components.buttons.ToolbarMenuIconButton;

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
        ToolbarMenuIconButton back = new ToolbarMenuIconButton("", "back");
        back.setToolTipText("Back to Main Menu");
        back.addActionListener(e -> app.switchCard(JournalApp.MAIN_MENU));
        return back;
    }

    public static ToolbarIconButton createSaveButton(String tooltip, Runnable onSave) {
        ToolbarMenuIconButton save = new ToolbarMenuIconButton("", "save");
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
        ToolbarMenuIconButton back = new ToolbarMenuIconButton("", "back");
        back.setToolTipText("Back to Entries");
        back.addActionListener(e -> app.openNotebookEntries(nb));
        return back;
    }
}
