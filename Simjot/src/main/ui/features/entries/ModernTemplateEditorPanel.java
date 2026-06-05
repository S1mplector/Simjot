/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.features.entries;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragSource;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.border.EmptyBorder;

import main.infrastructure.backup.NotebookInfo;
import main.ui.app.JournalApp;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.containers.ShadowedDialogPanel;
import main.ui.components.input.AeroTextField;
import main.ui.dialog.confirmation.CustomConfirmDialog;
import main.ui.dialog.message.UIMessage;

/**
 * Inline template editor panel for creating and editing templates.
 */
public class ModernTemplateEditorPanel extends JPanel {
    
    private static final Color ACCENT = new Color(70, 130, 220);
    private static final Color BORDER = new Color(215, 220, 230);
    private static final Color TEXT_PRIMARY = new Color(35, 40, 50);
    private static final Color TEXT_SECONDARY = new Color(100, 110, 125);
    private static final Color TEXT_MUTED = new Color(140, 150, 165);
    private static final Color DRAG_HIGHLIGHT = new Color(200, 220, 255);
    private static final int PREVIEW_MAX_QUESTIONS = 5;
    
    private final NotebookInfo notebook;
    private final boolean readOnlyMode;
    private JournalTemplateManager.JournalTemplate template;
    private boolean isNew = false;
    private boolean saved = false;
    private boolean dirty = false;
    
    private final AeroTextField nameField;
    private final AeroTextField descField;
    private final JPanel questionsPanel;
    private final List<QuestionRow> questionRows = new ArrayList<>();
    private final AeroTextField quickAddField;
    private final JLabel questionCountLabel;
    private final JLabel previewTitleLabel;
    private final JLabel previewDescLabel;
    private final JPanel previewQuestionsPanel;
    
    private int dragIndex = -1;
    private int dropIndex = -1;
    
    private Runnable onCancel;
    private Consumer<JournalTemplateManager.JournalTemplate> onSave;
    
    public ModernTemplateEditorPanel(NotebookInfo notebook, JournalTemplateManager.JournalTemplate existing) {
        this(notebook, existing, null);
    }

    public ModernTemplateEditorPanel(NotebookInfo notebook, JournalTemplateManager.JournalTemplate existing, JournalApp app) {
        this.notebook = notebook;
        this.template = existing;
        this.isNew = (existing == null);
        this.readOnlyMode = !isNew && existing != null && !existing.isCustom();
        
        setOpaque(false);
        setLayout(new BorderLayout());
        
        ShadowedDialogPanel main = new ShadowedDialogPanel(new BorderLayout(0, 0), 18);
        main.setBorder(new EmptyBorder(12, 12, 12, 12));
        main.setFlat(true);
        main.setFlatColor(Color.WHITE);
        
        // ═══════════════════════════════════════════════════════════════════
        // HEADER
        // ═══════════════════════════════════════════════════════════════════
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(20, 24, 16, 24));
        
        JLabel title = new JLabel(isNew ? "Create New Template" : "Edit Template");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setForeground(TEXT_PRIMARY);
        
        JLabel subtitle = new JLabel(isNew ? "Design a custom journal template" : template.getName());
        subtitle.setFont(subtitle.getFont().deriveFont(12f));
        subtitle.setForeground(TEXT_MUTED);
        
        JPanel titlePanel = new JPanel();
        titlePanel.setOpaque(false);
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        title.setAlignmentX(LEFT_ALIGNMENT);
        subtitle.setAlignmentX(LEFT_ALIGNMENT);
        titlePanel.add(title);
        titlePanel.add(Box.createVerticalStrut(2));
        titlePanel.add(subtitle);
        
        header.add(titlePanel, BorderLayout.WEST);
        main.add(header, BorderLayout.NORTH);
        
        // ═══════════════════════════════════════════════════════════════════
        // FORM
        // ═══════════════════════════════════════════════════════════════════
        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(new EmptyBorder(0, 24, 0, 24));
        
        // Name field
        JPanel nameRow = createFieldRow("Template Name", "e.g., Morning Reflection");
        nameField = (AeroTextField) ((JPanel) nameRow.getComponent(1)).getComponent(0);
        if (!isNew) nameField.setText(template.getName());
        form.add(nameRow);
        form.add(Box.createVerticalStrut(16));
        
        // Description field
        JPanel descRow = createFieldRow("Description", "Brief description of this template");
        descField = (AeroTextField) ((JPanel) descRow.getComponent(1)).getComponent(0);
        if (!isNew) descField.setText(template.getDescription());
        form.add(descRow);
        form.add(Box.createVerticalStrut(20));
        
        // Questions section
        JPanel questionsHeader = new JPanel(new BorderLayout());
        questionsHeader.setOpaque(false);
        questionsHeader.setAlignmentX(LEFT_ALIGNMENT);
        questionsHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        
        JLabel qLabel = new JLabel("Questions");
        qLabel.setFont(qLabel.getFont().deriveFont(Font.BOLD, 14f));
        qLabel.setForeground(TEXT_PRIMARY);
        
        questionCountLabel = new JLabel("0 questions");
        questionCountLabel.setFont(questionCountLabel.getFont().deriveFont(Font.BOLD, 11f));
        questionCountLabel.setForeground(TEXT_SECONDARY);

        JLabel qHint = new JLabel("Drag to reorder • Click to edit • Remove duplicates automatically");
        qHint.setFont(qHint.getFont().deriveFont(11f));
        qHint.setForeground(TEXT_MUTED);

        JPanel qLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        qLeft.setOpaque(false);
        qLeft.add(qLabel);
        qLeft.add(questionCountLabel);

        questionsHeader.add(qLeft, BorderLayout.WEST);
        questionsHeader.add(qHint, BorderLayout.EAST);
        form.add(questionsHeader);
        form.add(Box.createVerticalStrut(10));
        
        // Questions list
        questionsPanel = new JPanel();
        questionsPanel.setOpaque(false);
        questionsPanel.setLayout(new BoxLayout(questionsPanel, BoxLayout.Y_AXIS));
        questionsPanel.setAlignmentX(LEFT_ALIGNMENT);
        
        if (!isNew && template.getQuestions() != null) {
            for (String q : template.getQuestions()) {
                addQuestionRow(q, false);
            }
        }
        
        JScrollPane qScroll = new JScrollPane(questionsPanel);
        qScroll.setBorder(BorderFactory.createLineBorder(BORDER));
        qScroll.setOpaque(false);
        qScroll.getViewport().setOpaque(false);
        qScroll.setPreferredSize(new Dimension(500, 220));
        qScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));
        qScroll.setAlignmentX(LEFT_ALIGNMENT);
        qScroll.getVerticalScrollBar().setUnitIncrement(16);
        
        try {
            qScroll.getVerticalScrollBar().setUI(new main.ui.components.scrollbar.ModernScrollBarUI());
            qScroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, Integer.MAX_VALUE));
        } catch (Throwable ignored) {}
        
        form.add(qScroll);
        form.add(Box.createVerticalStrut(12));
        
        // Quick add field
        JPanel quickAddRow = new JPanel(new BorderLayout(8, 0));
        quickAddRow.setOpaque(false);
        quickAddRow.setAlignmentX(LEFT_ALIGNMENT);
        quickAddRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        
        quickAddField = new AeroTextField(30);
        quickAddField.setFont(quickAddField.getFont().deriveFont(13f));
        quickAddField.putClientProperty("JTextField.placeholderText", "Type a question and press Enter to add...");
        quickAddField.addActionListener(e -> addQuestionFromQuickField());
        
        RoundedButton addBtn = createActionButton("Quick Add", "new");
        addBtn.setPreferredSize(new Dimension(80, 36));
        addBtn.addActionListener(e -> addQuestionFromQuickField());
        
        quickAddRow.add(quickAddField, BorderLayout.CENTER);
        quickAddRow.add(addBtn, BorderLayout.EAST);
        form.add(quickAddRow);

        form.add(Box.createVerticalStrut(14));

        JLabel previewLabel = new JLabel("Live Preview");
        previewLabel.setFont(previewLabel.getFont().deriveFont(Font.BOLD, 13f));
        previewLabel.setForeground(TEXT_PRIMARY);
        previewLabel.setAlignmentX(LEFT_ALIGNMENT);
        form.add(previewLabel);
        form.add(Box.createVerticalStrut(8));

        JPanel previewCard = new JPanel();
        previewCard.setLayout(new BoxLayout(previewCard, BoxLayout.Y_AXIS));
        previewCard.setAlignmentX(LEFT_ALIGNMENT);
        previewCard.setOpaque(true);
        previewCard.setBackground(new Color(250, 252, 255));
        previewCard.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(225, 230, 238)),
            new EmptyBorder(12, 12, 12, 12)
        ));
        previewCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 170));

        previewTitleLabel = new JLabel();
        previewTitleLabel.setFont(previewTitleLabel.getFont().deriveFont(Font.BOLD, 14f));
        previewTitleLabel.setForeground(TEXT_PRIMARY);

        previewDescLabel = new JLabel();
        previewDescLabel.setFont(previewDescLabel.getFont().deriveFont(Font.PLAIN, 12f));
        previewDescLabel.setForeground(TEXT_SECONDARY);

        previewQuestionsPanel = new JPanel();
        previewQuestionsPanel.setOpaque(false);
        previewQuestionsPanel.setLayout(new BoxLayout(previewQuestionsPanel, BoxLayout.Y_AXIS));

        previewCard.add(previewTitleLabel);
        previewCard.add(Box.createVerticalStrut(4));
        previewCard.add(previewDescLabel);
        previewCard.add(Box.createVerticalStrut(10));
        previewCard.add(previewQuestionsPanel);
        form.add(previewCard);
        
        // Read-only notice for built-ins
        if (readOnlyMode) {
            form.add(Box.createVerticalStrut(12));
            JLabel roNotice = new JLabel("Built-in template. Duplicate it to make changes.");
            roNotice.setFont(roNotice.getFont().deriveFont(Font.ITALIC, 12f));
            roNotice.setForeground(new Color(180, 140, 80));
            roNotice.setAlignmentX(LEFT_ALIGNMENT);
            form.add(roNotice);
            
            nameField.setEnabled(false);
            descField.setEnabled(false);
            quickAddField.setEnabled(false);
        }
        
        main.add(form, BorderLayout.CENTER);
        
        // ═══════════════════════════════════════════════════════════════════
        // FOOTER
        // ═══════════════════════════════════════════════════════════════════
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(20, 24, 20, 24));
        
        // Left: Duplicate button (for existing templates)
        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftButtons.setOpaque(false);
        
        if (!isNew) {
            RoundedButton dupBtn = createActionButton("Duplicate", "new");
            dupBtn.setPreferredSize(new Dimension(100, 38));
            dupBtn.addActionListener(e -> duplicateTemplate());
            leftButtons.add(dupBtn);
        }
        
        // Right: Cancel / Save
        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        rightButtons.setOpaque(false);
        
        RoundedButton cancelBtn = createActionButton("Cancel", "exit");
        cancelBtn.setPreferredSize(new Dimension(100, 38));
        cancelBtn.addActionListener(e -> attemptCancel());
        
        RoundedButton saveBtn = createActionButton(isNew ? "Create" : "Save", "save");
        saveBtn.setPreferredSize(new Dimension(100, 38));
        saveBtn.addActionListener(e -> save());
        
        if (readOnlyMode) {
            saveBtn.setEnabled(false);
            saveBtn.setToolTipText("Built-in templates cannot be edited");
        }
        
        rightButtons.add(cancelBtn);
        rightButtons.add(saveBtn);
        
        footer.add(leftButtons, BorderLayout.WEST);
        footer.add(rightButtons, BorderLayout.EAST);
        main.add(footer, BorderLayout.SOUTH);
        
        add(main, BorderLayout.CENTER);

        installDirtyTracking();
        installSaveShortcut();
        refreshQuestionCount();
        updatePreview();
        dirty = false;
        
        SwingUtilities.invokeLater(() -> nameField.requestFocusInWindow());
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
    }
    
    public void setOnCancel(Runnable onCancel) {
        this.onCancel = onCancel;
    }
    
    public void setOnSave(Consumer<JournalTemplateManager.JournalTemplate> onSave) {
        this.onSave = onSave;
    }
    
    public boolean isSaved() { return saved; }
    
    public void attemptCancel() {
        if (!dirty || saved) {
            runCancel();
            return;
        }
        boolean confirm = CustomConfirmDialog.confirm(
            this,
            "Discard Changes?",
            "You have unsaved template edits. Discard them and close?"
        );
        if (confirm) {
            runCancel();
        }
    }

    public JournalTemplateManager.JournalTemplate getTemplate() { return template; }
    
    private RoundedButton createActionButton(String text, String iconId) {
        RoundedButton btn = new RoundedButton(text).withIcon(iconId);
        return btn;
    }

    private void runCancel() {
        if (onCancel != null) {
            onCancel.run();
        }
    }

    private void installDirtyTracking() {
        DocumentListener listener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onModelMutated(); }
            @Override public void removeUpdate(DocumentEvent e) { onModelMutated(); }
            @Override public void changedUpdate(DocumentEvent e) { onModelMutated(); }
        };
        nameField.getDocument().addDocumentListener(listener);
        descField.getDocument().addDocumentListener(listener);
    }

    private void installSaveShortcut() {
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        KeyStroke saveStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, mask);
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(saveStroke, "save-template");
        getActionMap().put("save-template", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                save();
            }
        });
    }

    private void onModelMutated() {
        dirty = true;
        refreshQuestionCount();
        updatePreview();
    }
    
    private JPanel createFieldRow(String label, String placeholder) {
        JPanel row = new JPanel(new BorderLayout(0, 4));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        
        JLabel lbl = new JLabel(label);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 13f));
        lbl.setForeground(TEXT_PRIMARY);
        
        JPanel fieldWrapper = new JPanel(new BorderLayout());
        fieldWrapper.setOpaque(false);
        
        AeroTextField field = new AeroTextField(30);
        field.setFont(field.getFont().deriveFont(14f));
        field.putClientProperty("JTextField.placeholderText", placeholder);
        fieldWrapper.add(field, BorderLayout.CENTER);
        
        row.add(lbl, BorderLayout.NORTH);
        row.add(fieldWrapper, BorderLayout.CENTER);
        
        return row;
    }
    
    private void addQuestionRow(String text) {
        addQuestionRow(text, true);
    }

    private void addQuestionRow(String text, boolean markDirty) {
        QuestionRow row = new QuestionRow(text, questionRows.size());
        if (readOnlyMode) {
            row.setEditableState(false);
        }
        questionRows.add(row);
        rebuildQuestionListUI();
        if (markDirty) {
            onModelMutated();
        }
    }

    private void addQuestionFromQuickField() {
        String text = quickAddField.getText().trim();
        if (text.isEmpty()) {
            addQuestionRow("New question...");
        } else {
            addQuestionRow(text);
        }
        quickAddField.setText("");
    }

    private void removeQuestionRow(QuestionRow row) {
        int idx = questionRows.indexOf(row);
        if (idx >= 0) {
            questionRows.remove(idx);
            rebuildQuestionListUI();
            onModelMutated();
        }
    }
    
    private void moveQuestion(int fromIndex, int toIndex) {
        if (fromIndex == toIndex || fromIndex < 0 || toIndex < 0) return;
        if (fromIndex >= questionRows.size() || toIndex >= questionRows.size()) return;
        
        QuestionRow row = questionRows.remove(fromIndex);
        questionRows.add(toIndex, row);
        
        rebuildQuestionListUI();
        onModelMutated();
    }

    private void rebuildQuestionListUI() {
        questionsPanel.removeAll();
        for (int i = 0; i < questionRows.size(); i++) {
            questionRows.get(i).updateIndex(i);
            questionsPanel.add(questionRows.get(i));
            questionsPanel.add(Box.createVerticalStrut(6));
        }
        questionsPanel.revalidate();
        questionsPanel.repaint();
    }

    private void refreshQuestionCount() {
        int count = questionRows.size();
        questionCountLabel.setText(count + (count == 1 ? " question" : " questions"));
    }

    private void updatePreview() {
        String name = nameField.getText().trim();
        String desc = descField.getText().trim();

        previewTitleLabel.setText(name.isEmpty() ? "Untitled template" : name);
        previewDescLabel.setText(desc.isEmpty() ? "No description yet." : desc);

        previewQuestionsPanel.removeAll();
        List<String> questions = collectQuestions(false).questions;
        if (questions.isEmpty()) {
            JLabel empty = new JLabel("No prompts yet. Add a question to see preview.");
            empty.setFont(empty.getFont().deriveFont(Font.ITALIC, 12f));
            empty.setForeground(TEXT_MUTED);
            previewQuestionsPanel.add(empty);
        } else {
            int show = Math.min(questions.size(), PREVIEW_MAX_QUESTIONS);
            for (int i = 0; i < show; i++) {
                String question = escapeHtml(questions.get(i));
                JLabel line = new JLabel("<html><body style='width: 470px'>" + (i + 1) + ". " + question + "</body></html>");
                line.setFont(line.getFont().deriveFont(12f));
                line.setForeground(TEXT_SECONDARY);
                previewQuestionsPanel.add(line);
                previewQuestionsPanel.add(Box.createVerticalStrut(2));
            }
            if (questions.size() > show) {
                JLabel more = new JLabel("+" + (questions.size() - show) + " more question(s)");
                more.setFont(more.getFont().deriveFont(Font.ITALIC, 11f));
                more.setForeground(TEXT_MUTED);
                previewQuestionsPanel.add(more);
            }
        }
        previewQuestionsPanel.revalidate();
        previewQuestionsPanel.repaint();
    }

    private String escapeHtml(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }

    private SanitizedQuestions collectQuestions(boolean dedupe) {
        List<String> values = new ArrayList<>();
        int blanks = 0;
        int duplicates = 0;
        Map<String, String> unique = new LinkedHashMap<>();
        for (QuestionRow row : questionRows) {
            String question = row.getText();
            if (question != null) question = question.trim();
            if (question == null || question.isEmpty()) {
                blanks++;
                continue;
            }
            values.add(question);
            if (dedupe) {
                String key = question.toLowerCase(Locale.ROOT);
                if (unique.containsKey(key)) {
                    duplicates++;
                    continue;
                }
                unique.put(key, question);
            }
        }
        List<String> finalList = dedupe ? new ArrayList<>(unique.values()) : values;
        return new SanitizedQuestions(finalList, blanks, duplicates);
    }

    private void setRowsFromQuestions(List<String> questions) {
        questionRows.clear();
        for (int i = 0; i < questions.size(); i++) {
            QuestionRow row = new QuestionRow(questions.get(i), i);
            if (readOnlyMode) {
                row.setEditableState(false);
            }
            questionRows.add(row);
        }
        rebuildQuestionListUI();
        refreshQuestionCount();
        updatePreview();
    }
    
    private void save() {
        if (readOnlyMode) {
            UIMessage.warn(this, "Read-only Template",
                "Built-in templates cannot be edited directly.",
                "Use Duplicate to create an editable copy.");
            return;
        }
        String name = nameField.getText().trim();
        String desc = descField.getText().trim();
        
        if (name.isEmpty()) {
            UIMessage.warn(this, "Name Required", "Please enter a template name.", "");
            nameField.requestFocusInWindow();
            return;
        }
        
        SanitizedQuestions sanitized = collectQuestions(true);
        List<String> questions = sanitized.questions;
        if (sanitized.blanksRemoved > 0 || sanitized.duplicatesRemoved > 0) {
            setRowsFromQuestions(questions);
            onModelMutated();
        }
        
        if (isNew) {
            String id = "CUSTOM_" + System.currentTimeMillis();
            template = new JournalTemplateManager.JournalTemplate(
                id, name, desc, questions.toArray(new String[0]), true
            );
            
            if (notebook != null) {
                template.setScope(JournalTemplateManager.Scope.NOTEBOOK_CUSTOM);
                template.setNotebookName(notebook.getName());
                JournalTemplateManager.getInstance().addTemplateForNotebook(notebook, template);
            } else {
                template.setScope(JournalTemplateManager.Scope.GLOBAL_CUSTOM);
                JournalTemplateManager.getInstance().addTemplate(template);
            }
        } else {
            template.setName(name);
            template.setDescription(desc);
            template.setQuestions(questions.toArray(new String[0]));
            JournalTemplateManager.getInstance().updateTemplate(template);
        }
        
        dirty = false;
        saved = true;
        if (onSave != null) {
            onSave.accept(template);
        }
    }
    
    private void duplicateTemplate() {
        String name = nameField.getText().trim();
        String desc = descField.getText().trim();
        
        List<String> questions = collectQuestions(true).questions;
        
        String id = "CUSTOM_" + System.currentTimeMillis();
        JournalTemplateManager.JournalTemplate copy = new JournalTemplateManager.JournalTemplate(
            id, name + " (Copy)", desc, questions.toArray(new String[0]), true
        );
        
        if (notebook != null) {
            copy.setScope(JournalTemplateManager.Scope.NOTEBOOK_CUSTOM);
            copy.setNotebookName(notebook.getName());
            JournalTemplateManager.getInstance().addTemplateForNotebook(notebook, copy);
        } else {
            copy.setScope(JournalTemplateManager.Scope.GLOBAL_CUSTOM);
            JournalTemplateManager.getInstance().addTemplate(copy);
        }
        
        UIMessage.info(this, "Template Duplicated", 
            "Created '" + copy.getName() + "'", 
            "You can now edit this copy.");
    }

    private static class SanitizedQuestions {
        private final List<String> questions;
        private final int blanksRemoved;
        private final int duplicatesRemoved;

        private SanitizedQuestions(List<String> questions, int blanksRemoved, int duplicatesRemoved) {
            this.questions = questions;
            this.blanksRemoved = blanksRemoved;
            this.duplicatesRemoved = duplicatesRemoved;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // QUESTION ROW - Draggable, editable question item
    // ═══════════════════════════════════════════════════════════════════════════
    
    private class QuestionRow extends JPanel {
        private final JTextField textField;
        private final JButton deleteBtn;
        private int index;
        private boolean hovered = false;
        private boolean dropTarget = false;
        
        QuestionRow(String text, int index) {
            this.index = index;
            setOpaque(false);
            setLayout(new BorderLayout(8, 0));
            setBorder(new EmptyBorder(8, 12, 8, 8));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
            setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            
            // Drag handle
            JLabel handle = new JLabel("⋮⋮") {
                @Override
                protected void paintComponent(Graphics g) {
                    g.setColor(hovered ? TEXT_SECONDARY : TEXT_MUTED);
                    super.paintComponent(g);
                }
            };
            handle.setFont(handle.getFont().deriveFont(Font.BOLD, 14f));
            handle.setPreferredSize(new Dimension(20, 20));
            handle.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            
            // Index label
            JLabel indexLabel = new JLabel(String.valueOf(index + 1) + ".") {
                @Override
                protected void paintComponent(Graphics g) {
                    g.setColor(ACCENT);
                    super.paintComponent(g);
                }
            };
            indexLabel.setFont(indexLabel.getFont().deriveFont(Font.BOLD, 12f));
            indexLabel.setPreferredSize(new Dimension(24, 20));
            
            JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            leftPanel.setOpaque(false);
            leftPanel.add(handle);
            leftPanel.add(indexLabel);
            
            // Text field
            textField = new JTextField(text);
            textField.setFont(textField.getFont().deriveFont(13f));
            textField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 225, 235)),
                new EmptyBorder(6, 8, 6, 8)
            ));
            textField.setBackground(Color.WHITE);
            textField.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { onModelMutated(); }
                @Override public void removeUpdate(DocumentEvent e) { onModelMutated(); }
                @Override public void changedUpdate(DocumentEvent e) { onModelMutated(); }
            });
            
            // Delete button
            deleteBtn = new JButton("×") {
                @Override
                protected void paintComponent(Graphics g) {
                    if (getModel().isRollover()) {
                        g.setColor(new Color(255, 230, 230));
                        g.fillOval(2, 2, getWidth()-4, getHeight()-4);
                    }
                    super.paintComponent(g);
                }
            };
            deleteBtn.setFont(deleteBtn.getFont().deriveFont(Font.BOLD, 16f));
            deleteBtn.setForeground(new Color(200, 100, 100));
            deleteBtn.setPreferredSize(new Dimension(28, 28));
            deleteBtn.setBorderPainted(false);
            deleteBtn.setContentAreaFilled(false);
            deleteBtn.setFocusPainted(false);
            deleteBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            deleteBtn.setToolTipText("Remove question");
            deleteBtn.addActionListener(e -> removeQuestionRow(this));
            
            add(leftPanel, BorderLayout.WEST);
            add(textField, BorderLayout.CENTER);
            add(deleteBtn, BorderLayout.EAST);
            
            // Hover effect
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    repaint();
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    repaint();
                }
            });
            
            // Drag and drop
            if (!readOnlyMode) {
                installDragDrop();
            }
        }
        
        void updateIndex(int newIndex) {
            this.index = newIndex;
            // Update the index label
            JPanel leftPanel = (JPanel) getComponent(0);
            JLabel indexLabel = (JLabel) leftPanel.getComponent(1);
            indexLabel.setText(String.valueOf(newIndex + 1) + ".");
        }
        
        String getText() {
            return textField.getText();
        }

        void setEditableState(boolean editable) {
            textField.setEditable(editable);
            textField.setEnabled(editable);
            deleteBtn.setEnabled(editable);
            setCursor(editable
                ? Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
                : Cursor.getDefaultCursor());
        }
        
        void setDropTarget(boolean target) {
            this.dropTarget = target;
            repaint();
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            Color bg = dropTarget ? DRAG_HIGHLIGHT : (hovered ? new Color(248, 250, 255) : Color.WHITE);
            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
            
            if (hovered || dropTarget) {
                g2.setColor(dropTarget ? ACCENT : new Color(200, 210, 225));
                g2.setStroke(new BasicStroke(dropTarget ? 2f : 1f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth()-1, getHeight()-1, 8, 8));
            }
            
            g2.dispose();
            super.paintComponent(g);
        }
        
        private void installDragDrop() {
            DragSource ds = new DragSource();
            ds.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_MOVE, e -> {
                dragIndex = index;
                e.startDrag(DragSource.DefaultMoveDrop, new StringSelection(String.valueOf(index)));
            });
            
            new DropTarget(this, new DropTargetAdapter() {
                @Override
                public void dragEnter(DropTargetDragEvent e) {
                    if (dragIndex != index) {
                        setDropTarget(true);
                        dropIndex = index;
                    }
                }
                
                @Override
                public void dragExit(DropTargetEvent e) {
                    setDropTarget(false);
                    dropIndex = -1;
                }
                
                @Override
                public void drop(DropTargetDropEvent e) {
                    setDropTarget(false);
                    if (dragIndex >= 0 && dropIndex >= 0 && dragIndex != dropIndex) {
                        moveQuestion(dragIndex, dropIndex);
                    }
                    dragIndex = -1;
                    dropIndex = -1;
                }
            });
        }
    }
}
