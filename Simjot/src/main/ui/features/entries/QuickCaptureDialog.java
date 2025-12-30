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

package main.ui.features.entries;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.KeyEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import main.core.security.EncryptionManager;
import main.core.security.crypto.ContentType;
import main.core.security.crypto.CryptoConfig;
import main.core.security.crypto.CryptoException;
import main.core.security.crypto.EncryptedMetadata;
import main.core.service.SettingsStore;
import main.infrastructure.backup.EntryHistoryManager;
import main.infrastructure.backup.NotebookInfo;
import main.infrastructure.io.FileIO;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.fields.ModernTextField;
import main.ui.dialog.message.CustomMessageDialog;

/**
 * Small quick-capture window that files a note into the current notebook.
 */
public class QuickCaptureDialog extends JDialog {
    private final NotebookInfo notebook;
    private final Runnable onSaved;
    private final ModernTextField titleField;
    private final JTextArea bodyArea;

    public QuickCaptureDialog(Frame owner, NotebookInfo notebook, Runnable onSaved) {
        super(owner, "Quick Capture", false);
        this.notebook = notebook;
        this.onSaved = onSaved;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        setAlwaysOnTop(true);

        FrostedGlassPanel root = new FrostedGlassPanel(new BorderLayout(10, 10), 16);
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        titleField = new ModernTextField(24);
        titleField.setFont(new Font("Serif", Font.BOLD, 16));
        titleField.setPlaceholder("Quick title");
        root.add(titleField, BorderLayout.NORTH);

        bodyArea = new JTextArea(8, 34);
        bodyArea.setLineWrap(true);
        bodyArea.setWrapStyleWord(true);
        bodyArea.setFont(new Font("Serif", Font.PLAIN, 14));
        JScrollPane scroll = new JScrollPane(bodyArea);
        root.add(scroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton cancel = new RoundedButton("Cancel");
        JButton save = new RoundedButton("Save");
        save.setPreferredSize(new Dimension(80, 28));
        cancel.addActionListener(e -> dispose());
        save.addActionListener(e -> saveAndClose());
        actions.add(cancel);
        actions.add(save);
        root.add(actions, BorderLayout.SOUTH);

        installKeyBindings(root, save);
        setContentPane(root);
        pack();
        setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(() -> titleField.requestFocusInWindow());
    }

    private void installKeyBindings(JComponent root, JButton save) {
        root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "qcClose");
        root.getActionMap().put("qcClose", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { dispose(); }
        });

        int mask = java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, mask), "qcSave");
        root.getActionMap().put("qcSave", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { save.doClick(); }
        });
    }

    private void saveAndClose() {
        if (notebook == null) {
            CustomMessageDialog.display(this, "Quick Capture", "No notebook selected.", true);
            return;
        }
        String title = titleField.getText() == null ? "" : titleField.getText().trim();
        String body = bodyArea.getText() == null ? "" : bodyArea.getText().trim();
        if (title.isBlank()) {
            title = notebook.getType() == NotebookInfo.Type.POETRY ? "Untitled poem" : "Quick note";
        }
        if (body.isBlank()) {
            body = "(empty)";
        }
        try {
            File folder = notebook.getFolder();
            if (folder != null && !folder.exists()) folder.mkdirs();
            String filename = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + extensionFor(notebook);
            File out = new File(folder, filename);

            byte[] data = buildPayload(title, body, notebook);
            if (EncryptionManager.isEncryptionEnabled()) {
                String password = EncryptionManager.getPasswordForUse(this, true);
                if (password == null || password.isBlank()) {
                    CustomMessageDialog.display(this, "Encryption", "Encryption password required to save.", true);
                    return;
                }
                int words = countWords(body);
                CryptoConfig config = (notebook != null && notebook.getType() == NotebookInfo.Type.POETRY)
                        ? CryptoConfig.forPoems().withIdentifier(EncryptedMetadata.encodePoem(title, System.currentTimeMillis(), words))
                        : CryptoConfig.forEntries().withIdentifier(EncryptedMetadata.encodeEntry(title, -1, false, null, System.currentTimeMillis(), words));
                try {
                    ContentType type = (notebook != null && notebook.getType() == NotebookInfo.Type.POETRY)
                            ? ContentType.POEM : ContentType.ENTRY;
                    data = EncryptionManager.encrypt(data, password, type, config);
                } catch (CryptoException ex) {
                    CustomMessageDialog.display(this, "Encryption", ex.getUserMessage(), true);
                    return;
                }
            }
            FileIO.ensureSpace(out.toPath(), data.length + 4096L, "quick capture");
            FileIO.atomicWrite(out.toPath(), data, true, true);

            try {
                int keep = SettingsStore.get().getBackupKeepCount();
                EntryHistoryManager.recordSnapshot(out, keep);
            } catch (Throwable ignored) {}

            try {
                SettingsStore.get().setLastOpenedFilePath(out.getAbsolutePath());
                SettingsStore.get().save();
            } catch (Throwable ignored) {}

            if (onSaved != null) onSaved.run();
            dispose();
        } catch (Exception ex) {
            CustomMessageDialog.display(this, "Quick Capture", "Failed to save quick note.", true);
        }
    }

    private static String extensionFor(NotebookInfo nb) {
        if (nb == null) return ".note";
        return switch (nb.getType()) {
            case POETRY -> ".poem";
            case NOTETAKING -> ".ntk";
            default -> ".note";
        };
    }

    private static byte[] buildPayload(String title, String body, NotebookInfo nb) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8));
        if (nb != null && nb.getType() == NotebookInfo.Type.POETRY) {
            writer.println(title);
            writer.println();
            writer.println(body);
        } else {
            EntryFileFormat.EntryMeta meta = new EntryFileFormat.EntryMeta();
            meta.title = title;
            meta.savedAt = System.currentTimeMillis();
            writer.println(EntryFileFormat.buildHeader(meta));
            writer.println();
            writer.println(body);
        }
        writer.flush();
        return baos.toByteArray();
    }

    private static int countWords(String text) {
        if (text == null) return 0;
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return 0;
        return trimmed.split("\\s+").length;
    }
}
