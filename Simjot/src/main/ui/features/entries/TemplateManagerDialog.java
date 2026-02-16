/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.entries;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;

import main.infrastructure.backup.NotebookInfo;
import main.infrastructure.ffi.NativeAccess;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.containers.AeroPanel;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.input.AeroTextField;
import main.ui.dialog.confirmation.CustomChoiceDialog;
import main.ui.dialog.confirmation.CustomConfirmDialog;
import main.ui.dialog.input.CustomInputDialog;
import main.ui.dialog.message.UIMessage;

/**
 * Dialog for managing journal entry templates: add, edit, remove.
 */
public class TemplateManagerDialog extends JDialog {
    private DefaultListModel<JournalTemplateManager.JournalTemplate> listModel;
    private JList<JournalTemplateManager.JournalTemplate> templateList;
    private final NotebookInfo notebook; // null => global management
    private TemplateEditorPanel editorPanel;
    private AeroTextField searchField;
    private List<JournalTemplateManager.JournalTemplate> allTemplates;

    public TemplateManagerDialog(Frame parent) { this(parent, null); }

    public TemplateManagerDialog(Frame parent, NotebookInfo notebook) {
        super(parent, "Manage Templates", true);
        this.notebook = notebook;
        initializeUI(parent);
    }

    public TemplateManagerDialog(Dialog parent, NotebookInfo notebook) {
        super(parent, "Manage Templates", true);
        this.notebook = notebook;
        initializeUI(parent);
    }

    private void initializeUI(Component parent) {
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        setLayout(new BorderLayout());
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        FrostedGlassPanel mainPanel = new FrostedGlassPanel(new BorderLayout(12, 12), 18);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Title
        JLabel titleLabel = new JLabel("Journal Templates" + (notebook!=null? " — "+notebook.getName():""));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
        titleLabel.setForeground(new Color(40, 40, 40));
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        // Left column: search + list + actions
        JPanel left = new JPanel(new BorderLayout(8, 8));
        left.setOpaque(false);

        AeroPanel searchRow = new AeroPanel(16);
        searchRow.setLayout(new BorderLayout());
        searchRow.setBorder(BorderFactory.createEmptyBorder(6,6,6,6));
        searchField = new AeroTextField(22);
        searchField.setToolTipText("Search templates…");
        searchField.putClientProperty("JTextField.placeholderText", "Search templates…");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){
            public void insertUpdate(javax.swing.event.DocumentEvent e){ filterAsync(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e){ filterAsync(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e){ filterAsync(); }
            private void filterAsync(){ SwingUtilities.invokeLater(() -> refreshList()); }
        });
        left.add(searchRow, BorderLayout.NORTH);
        searchRow.add(searchField, BorderLayout.CENTER);

        listModel = new DefaultListModel<>();
        templateList = new JList<>(listModel);
        templateList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        templateList.setCellRenderer(new TemplateListRenderer());
        templateList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) loadSelectedIntoEditor();
        });
        JScrollPane scroll = new JScrollPane(templateList);
        scroll.setPreferredSize(new Dimension(320, 360));
        try {
            JScrollBar vbar = scroll.getVerticalScrollBar();
            vbar.setUI(new main.ui.components.scrollbar.ModernScrollBarUI());
            vbar.setPreferredSize(new Dimension(10, Integer.MAX_VALUE));
            vbar.setOpaque(false);
        } catch (Throwable ignored) {}
        left.add(scroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        RoundedButton addBtn = new RoundedButton("Add New");
        addBtn.setPreferredSize(new Dimension(110, 34));
        addBtn.addActionListener(e -> addTemplate());
        RoundedButton dupBtn = new RoundedButton("Duplicate");
        dupBtn.setPreferredSize(new Dimension(110, 34));
        dupBtn.addActionListener(e -> duplicateSelected());
        RoundedButton deleteBtn = new RoundedButton("Delete/Hide");
        deleteBtn.setPreferredSize(new Dimension(120, 34));
        deleteBtn.addActionListener(e -> deleteTemplate());
        RoundedButton closeBtn = new RoundedButton("Close");
        closeBtn.setPreferredSize(new Dimension(100, 34));
        closeBtn.addActionListener(e -> dispose());
        actions.add(addBtn);
        actions.add(dupBtn);
        actions.add(deleteBtn);
        actions.add(closeBtn);
        left.add(actions, BorderLayout.SOUTH);

        // Right column: editor
        editorPanel = new TemplateEditorPanel();
        editorPanel.setOnSave(tmpl -> {
            // Save updates to the correct scope
            if (tmpl.getScope() == JournalTemplateManager.Scope.NOTEBOOK_CUSTOM && notebook != null) {
                JournalTemplateManager.getInstance().updateTemplate(tmpl);
            } else if (tmpl.getScope() == JournalTemplateManager.Scope.GLOBAL_CUSTOM || tmpl.isCustom()) {
                JournalTemplateManager.getInstance().updateTemplate(tmpl);
            }
            refreshList();
            selectTemplate(tmpl);
        });
        // Ensure Cancel closes the manager dialog
        editorPanel.setOnCancel(() -> TemplateManagerDialog.this.dispose());

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, editorPanel);
        split.setResizeWeight(0.35);
        split.setBorder(BorderFactory.createEmptyBorder());
        mainPanel.add(split, BorderLayout.CENTER);

        add(mainPanel);
        setSize(860, 560);
        setLocationRelativeTo(parent);

        // ESC to close
        try {
            getRootPane().registerKeyboardAction(e -> dispose(),
                    KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                    JComponent.WHEN_IN_FOCUSED_WINDOW);
        } catch (Throwable ignored) {}

        // Initial load
        refreshList();
        if (!listModel.isEmpty()) templateList.setSelectedIndex(0);
    }

    private void refreshList() {
        listModel.clear();
        allTemplates = (notebook!=null)
                ? JournalTemplateManager.getInstance().getTemplates(notebook)
                : JournalTemplateManager.getInstance().getTemplates();
        String q = searchField != null ? searchField.getText().trim().toLowerCase() : "";
        for (JournalTemplateManager.JournalTemplate template : allTemplates) {
            String name = template.getName().toLowerCase();
            String desc = template.getDescription().toLowerCase();
            if (q.isEmpty() || NativeAccess.searchContains(name, q) || NativeAccess.searchContains(desc, q)) {
                listModel.addElement(template);
            }
        }
    }

    private void addTemplate() {
        editorPanel.load(null);
        editorPanel.setReadOnly(false);
        editorPanel.setOnSave(tmpl -> {
            if (notebook != null) {
                tmpl.setScope(JournalTemplateManager.Scope.NOTEBOOK_CUSTOM);
                tmpl.setNotebookName(notebook.getName());
                JournalTemplateManager.getInstance().addTemplateForNotebook(notebook, tmpl);
            } else {
                tmpl.setScope(JournalTemplateManager.Scope.GLOBAL_CUSTOM);
                JournalTemplateManager.getInstance().addTemplate(tmpl);
            }
            refreshList();
            selectTemplate(tmpl);
        });
    }

    private void loadSelectedIntoEditor() {
        JournalTemplateManager.JournalTemplate selected = templateList.getSelectedValue();
        if (selected == null) return;
        editorPanel.load(selected);
        editorPanel.setReadOnly(!selected.isCustom());
        editorPanel.setOnSave(tmpl -> {
            JournalTemplateManager.getInstance().updateTemplate(tmpl);
            refreshList();
            selectTemplate(tmpl);
        });
    }

    private void selectTemplate(JournalTemplateManager.JournalTemplate t){
        for (int i=0;i<listModel.size();i++){
            if (listModel.get(i).getId().equals(t.getId())){ templateList.setSelectedIndex(i); break; }
        }
    }

    private void duplicateSelected(){
        JournalTemplateManager.JournalTemplate sel = templateList.getSelectedValue();
        if (sel == null) return;
        JournalTemplateManager.JournalTemplate copy = new JournalTemplateManager.JournalTemplate(
                "CUSTOM_" + System.currentTimeMillis(),
                sel.getName() + " (Copy)",
                sel.getDescription(),
                sel.getQuestions(),
                true
        );
        if (notebook != null) {
            copy.setScope(JournalTemplateManager.Scope.NOTEBOOK_CUSTOM);
            copy.setNotebookName(notebook.getName());
            JournalTemplateManager.getInstance().addTemplateForNotebook(notebook, copy);
        } else {
            copy.setScope(JournalTemplateManager.Scope.GLOBAL_CUSTOM);
            JournalTemplateManager.getInstance().addTemplate(copy);
        }
        refreshList();
        selectTemplate(copy);
        editorPanel.load(copy);
        editorPanel.setReadOnly(false);
    }

    private void deleteTemplate() {
        JournalTemplateManager.JournalTemplate selected = templateList.getSelectedValue();
        if (selected == null) {
            UIMessage.warn(this,
                    "No Template Selected",
                    "You haven't selected a template to delete.",
                    "Click a template in the list, then press Delete.");
            return;
        }
        if (!selected.isCustom()) {
            // Built-in: offer to hide per-notebook or globally (custom UI)
            String[] options = (notebook!=null)
                    ? new String[]{"Hide in this notebook","Hide globally","Cancel"}
                    : new String[]{"Hide globally","Cancel"};
            int choice = CustomChoiceDialog.choose(this,
                    "Hide Built-in Template",
                    "Built-in templates can't be deleted, but you can hide them.",
                    options);
            if (choice == 0 && notebook!=null) {
                JournalTemplateManager.getInstance().hideBuiltInForNotebook(notebook, selected.getId());
                refreshList();
            } else if ((choice == 0 && notebook==null) || (choice == 1 && notebook!=null)) {
                JournalTemplateManager.getInstance().hideBuiltInGlobally(selected.getId());
                refreshList();
            }
            return;
        }

        boolean confirm = CustomConfirmDialog.confirm(this,
                "Confirm Delete",
                "Delete template '" + selected.getName() + "'?");

        if (confirm) {
            if (selected.getScope() == JournalTemplateManager.Scope.NOTEBOOK_CUSTOM && notebook!=null) {
                JournalTemplateManager.getInstance().removeTemplate(notebook, selected.getId());
            } else {
                JournalTemplateManager.getInstance().removeTemplate(selected.getId());
            }
            refreshList();
        }
    }

    private static class TemplateListRenderer extends JLabel implements ListCellRenderer<JournalTemplateManager.JournalTemplate> {
        TemplateListRenderer() {
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends JournalTemplateManager.JournalTemplate> list,
                JournalTemplateManager.JournalTemplate template,
                int index,
                boolean isSelected,
                boolean cellHasFocus) {
            String text = "<html>" + template.getName() +
                    (template.isCustom() ? "<span style='color:#6A6;'>  • Custom</span>"
                            : "<span style='color:#66a;'>  • Built-in</span>") +
                    "<br><span style='font-size:10px;color:#888;'>" + template.getDescription() + "</span></html>";
            setText(text);

            if (isSelected) {
                setBackground(new Color(240, 245, 255));
                setForeground(new Color(30, 30, 30));
            } else {
                setBackground(Color.WHITE);
                setForeground(new Color(60, 60, 60));
            }

            return this;
        }
    }

    /**
     * Embedded editor panel replacing the old modal editor dialog.
     */
    private static class TemplateEditorPanel extends JPanel {
        private JournalTemplateManager.JournalTemplate template;
        private final AeroTextField nameField;
        private final AeroTextField descField;
        private final DefaultListModel<String> questionModel;
        private final JList<String> questionList;
        private java.util.function.Consumer<JournalTemplateManager.JournalTemplate> onSave;
        private Runnable onCancel;
        private final AeroTextField quickAddField;
        private final JLabel roLabel;

        TemplateEditorPanel() {
            setLayout(new BorderLayout(12, 12));
            setOpaque(false);

            AeroPanel header = new AeroPanel(14);
            header.setLayout(new BorderLayout());
            header.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
            JLabel hdr = new JLabel("Template Editor");
            hdr.setFont(hdr.getFont().deriveFont(Font.BOLD, 17f));
            hdr.setForeground(new Color(40,40,40));
            header.add(hdr, BorderLayout.WEST);
            add(header, BorderLayout.NORTH);

            JPanel formPanel = new JPanel(new GridBagLayout());
            formPanel.setOpaque(false);
            GridBagConstraints gc = new GridBagConstraints();
            gc.insets = new Insets(6, 8, 6, 8);
            gc.fill = GridBagConstraints.HORIZONTAL;

            gc.gridx = 0; gc.gridy = 0; gc.weightx = 0;
            JLabel nameLbl = new JLabel("Name");
            nameLbl.setFont(nameLbl.getFont().deriveFont(Font.BOLD));
            formPanel.add(nameLbl, gc);
            gc.gridx = 1; gc.weightx = 1;
            nameField = new AeroTextField(30);
            nameField.setFont(nameField.getFont().deriveFont(14f));
            formPanel.add(nameField, gc);

            gc.gridx = 0; gc.gridy = 1; gc.weightx = 0;
            JLabel descLbl = new JLabel("Description");
            descLbl.setFont(descLbl.getFont().deriveFont(Font.BOLD));
            formPanel.add(descLbl, gc);
            gc.gridx = 1; gc.weightx = 1;
            descField = new AeroTextField(30);
            descField.setFont(descField.getFont().deriveFont(14f));
            formPanel.add(descField, gc);

            gc.gridx = 0; gc.gridy = 2; gc.gridwidth = 2;
            JLabel qLbl = new JLabel("Questions");
            qLbl.setFont(qLbl.getFont().deriveFont(Font.BOLD));
            formPanel.add(qLbl, gc);

            questionModel = new DefaultListModel<>();
            questionList = new JList<>(questionModel);
            questionList.setFont(questionList.getFont().deriveFont(14f));
            questionList.setCellRenderer(new QuestionCellRenderer());
            questionList.setFixedCellHeight(36);
            
            // Double-click to edit
            questionList.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (e.getClickCount() == 2 && questionList.isEnabled()) editQuestion();
                }
            });
            
            // Delete key to remove
            questionList.addKeyListener(new java.awt.event.KeyAdapter() {
                @Override public void keyPressed(java.awt.event.KeyEvent e) {
                    if (e.getKeyCode() == java.awt.event.KeyEvent.VK_DELETE && questionList.isEnabled()) {
                        deleteQuestion();
                    } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER && questionList.isEnabled()) {
                        editQuestion();
                    }
                }
            });
            
            JScrollPane qScroll = new JScrollPane(questionList);
            qScroll.setBorder(BorderFactory.createLineBorder(new Color(200,210,225)));
            qScroll.setOpaque(false);
            qScroll.getViewport().setOpaque(false);
            questionList.setOpaque(true);
            questionList.setBackground(new Color(252, 253, 255));
            qScroll.setPreferredSize(new Dimension(480, 200));
            try {
                JScrollBar vbar = qScroll.getVerticalScrollBar();
                vbar.setUI(new main.ui.components.scrollbar.ModernScrollBarUI());
                vbar.setPreferredSize(new Dimension(10, Integer.MAX_VALUE));
                vbar.setOpaque(false);
            } catch (Throwable ignored) {}
            gc.gridy = 3;
            formPanel.add(qScroll, gc);

            JPanel qButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            qButtons.setOpaque(false);
            RoundedButton addQBtn = new RoundedButton("Add");
            addQBtn.setPreferredSize(new Dimension(90, 32));
            addQBtn.addActionListener(e -> addQuestion());
            RoundedButton editQBtn = new RoundedButton("Edit");
            editQBtn.setPreferredSize(new Dimension(90, 32));
            editQBtn.addActionListener(e -> editQuestion());
            RoundedButton deleteQBtn = new RoundedButton("Delete");
            deleteQBtn.setPreferredSize(new Dimension(90, 32));
            deleteQBtn.addActionListener(e -> deleteQuestion());
            RoundedButton upBtn = new RoundedButton("Move Up");
            upBtn.setPreferredSize(new Dimension(100, 32));
            upBtn.addActionListener(e -> moveQuestion(-1));
            RoundedButton downBtn = new RoundedButton("Move Down");
            downBtn.setPreferredSize(new Dimension(110, 32));
            downBtn.addActionListener(e -> moveQuestion(1));
            qButtons.add(addQBtn);
            qButtons.add(editQBtn);
            qButtons.add(deleteQBtn);
            qButtons.add(upBtn);
            qButtons.add(downBtn);
            gc.gridy = 4;
            formPanel.add(qButtons, gc);

            // Quick-add field below buttons
            gc.gridy = 5; gc.gridwidth = 2;
            AeroPanel quickRow = new AeroPanel(12);
            quickRow.setLayout(new BorderLayout(6, 0));
            quickRow.setBorder(BorderFactory.createEmptyBorder(6,8,6,8));
            quickAddField = new AeroTextField(20);
            quickAddField.setToolTipText("Type a question and press Enter to add");
            quickAddField.putClientProperty("JTextField.placeholderText", "Type a new question and press Enter…");
            quickAddField.addActionListener(e -> { 
                String t = quickAddField.getText().trim(); 
                if (!t.isEmpty()) { 
                    questionModel.addElement(t); 
                    quickAddField.setText(""); 
                    questionList.setSelectedIndex(questionModel.size() - 1);
                    questionList.ensureIndexIsVisible(questionModel.size() - 1);
                } 
            });
            quickRow.add(quickAddField, BorderLayout.CENTER);
            
            // Quick hint label
            JLabel quickHint = new JLabel("Press Enter to add");
            quickHint.setFont(quickHint.getFont().deriveFont(Font.PLAIN, 11f));
            quickHint.setForeground(new Color(140, 150, 165));
            quickRow.add(quickHint, BorderLayout.EAST);
            formPanel.add(quickRow, gc);

            // Read-only hint label
            roLabel = new JLabel("ℹ Built-in template (read-only). Duplicate to customize.");
            roLabel.setForeground(new Color(100, 120, 160));
            roLabel.setFont(roLabel.getFont().deriveFont(Font.ITALIC, 12f));
            roLabel.setVisible(false);
            gc.gridy = 6; gc.gridwidth = 2;
            formPanel.add(roLabel, gc);
            
            // Hint for keyboard shortcuts
            JLabel shortcutHint = new JLabel("Tip: Double-click to edit • Delete key to remove • Drag to reorder");
            shortcutHint.setFont(shortcutHint.getFont().deriveFont(Font.PLAIN, 11f));
            shortcutHint.setForeground(new Color(130, 140, 155));
            gc.gridy = 7;
            formPanel.add(shortcutHint, gc);

            // Enable drag-reorder of questions
            installReorderDnD(questionList, questionModel);

            FrostedGlassPanel body = new FrostedGlassPanel(new BorderLayout(12, 12), 16);
            body.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            body.add(formPanel, BorderLayout.CENTER);

            JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            bottomPanel.setOpaque(false);
            RoundedButton saveBtn = new RoundedButton("Save");
            saveBtn.setPreferredSize(new Dimension(120, 36));
            saveBtn.addActionListener(e -> save());
            RoundedButton cancelBtn = new RoundedButton("Cancel");
            cancelBtn.setPreferredSize(new Dimension(120, 36));
            cancelBtn.addActionListener(e -> { if (onCancel != null) onCancel.run(); });
            bottomPanel.add(saveBtn);
            bottomPanel.add(cancelBtn);
            body.add(bottomPanel, BorderLayout.SOUTH);
            add(body, BorderLayout.CENTER);
        }

        void setOnSave(java.util.function.Consumer<JournalTemplateManager.JournalTemplate> cb) { this.onSave = cb; }
        void setOnCancel(Runnable cb) { this.onCancel = cb; }
        void setReadOnly(boolean ro){
            nameField.setEnabled(!ro);
            descField.setEnabled(!ro);
            questionList.setEnabled(!ro);
            quickAddField.setEnabled(!ro);
            roLabel.setVisible(ro);
        }

        void load(JournalTemplateManager.JournalTemplate existing) {
            this.template = existing;
            String name = existing != null ? existing.getName() : "";
            String desc = existing != null ? existing.getDescription() : "";
            nameField.setText(name);
            descField.setText(desc);
            questionModel.clear();
            if (existing != null && existing.getQuestions()!=null) {
                for (String q : existing.getQuestions()) questionModel.addElement(q);
            }
        }

        private void addQuestion() {
            String t = quickAddField.getText().trim();
            if (!t.isEmpty()) {
                questionModel.addElement(t);
                quickAddField.setText("");
                quickAddField.requestFocusInWindow();
            } else {
                quickAddField.requestFocusInWindow();
            }
        }
        private void editQuestion() {
            int index = questionList.getSelectedIndex();
            if (index < 0) return;
            String current = questionModel.get(index);
            String edited = CustomInputDialog.prompt(this, "Edit Question", "Edit question:", current);
            if (edited != null && !edited.trim().isEmpty()) questionModel.set(index, edited.trim());
        }
        private void deleteQuestion() {
            int index = questionList.getSelectedIndex();
            if (index >= 0) questionModel.remove(index);
        }
        private void moveQuestion(int delta) {
            int index = questionList.getSelectedIndex();
            if (index < 0) return;
            int newIndex = index + delta;
            if (newIndex < 0 || newIndex >= questionModel.size()) return;
            String item = questionModel.get(index);
            questionModel.remove(index);
            questionModel.add(newIndex, item);
            questionList.setSelectedIndex(newIndex);
            questionList.ensureIndexIsVisible(newIndex);
        }

        private void save() {
            String name = nameField.getText().trim();
            String desc = descField.getText().trim();
            if (name.isEmpty()) {
                UIMessage.warn(this,
                        "Template Name Required",
                        "The template needs a name.",
                        "Type a descriptive name (e.g., 'Daily Reflection'), then click Save.");
                return;
            }
            List<String> questions = new ArrayList<>();
            for (int i = 0; i < questionModel.size(); i++) questions.add(questionModel.get(i));
            if (template != null) {
                template.setName(name);
                template.setDescription(desc);
                template.setQuestions(questions.toArray(new String[0]));
                if (onSave != null) onSave.accept(template);
            } else {
                String id = "CUSTOM_" + System.currentTimeMillis();
                JournalTemplateManager.JournalTemplate newTemplate =
                        new JournalTemplateManager.JournalTemplate(id, name, desc, questions.toArray(new String[0]), true);
                if (onSave != null) onSave.accept(newTemplate);
            }
        }

        /** Custom renderer showing question numbers */
        private class QuestionCellRenderer extends JPanel implements ListCellRenderer<String> {
            private final JLabel numLabel = new JLabel();
            private final JLabel textLabel = new JLabel();
            
            QuestionCellRenderer() {
                setLayout(new BorderLayout(8, 0));
                setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
                setOpaque(true);
                
                numLabel.setFont(numLabel.getFont().deriveFont(Font.BOLD, 12f));
                numLabel.setForeground(new Color(100, 130, 180));
                numLabel.setPreferredSize(new Dimension(28, 24));
                
                textLabel.setFont(textLabel.getFont().deriveFont(14f));
                
                add(numLabel, BorderLayout.WEST);
                add(textLabel, BorderLayout.CENTER);
            }
            
            @Override
            public Component getListCellRendererComponent(JList<? extends String> list, String value, 
                    int index, boolean isSelected, boolean cellHasFocus) {
                numLabel.setText((index + 1) + ".");
                textLabel.setText(value);
                
                if (isSelected) {
                    setBackground(new Color(225, 235, 250));
                    textLabel.setForeground(new Color(30, 50, 80));
                    setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 3, 0, 0, new Color(90, 140, 220)),
                        BorderFactory.createEmptyBorder(6, 7, 6, 10)
                    ));
                } else {
                    setBackground(index % 2 == 0 ? new Color(252, 253, 255) : new Color(248, 250, 254));
                    textLabel.setForeground(new Color(50, 55, 65));
                    setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
                }
                
                return this;
            }
        }
        
        private static void installReorderDnD(JList<String> list, DefaultListModel<String> model){
            list.setDragEnabled(true);
            list.setDropMode(DropMode.INSERT);
            list.setTransferHandler(new TransferHandler(){
                private int[] indices = null;
                private int addIndex = -1; // Location where items were added
                private int addCount = 0;   // Number of items added.
                @Override public int getSourceActions(JComponent c){ return MOVE; }
                @Override protected java.awt.datatransfer.Transferable createTransferable(JComponent c){
                    indices = list.getSelectedIndices();
                    List<String> values = new ArrayList<>();
                    for (int i : indices) values.add(model.get(i));
                    return new java.awt.datatransfer.StringSelection(String.join("\n", values));
                }
                @Override public boolean canImport(TransferSupport info){ return info.isDrop(); }
                @Override public boolean importData(TransferSupport info){
                    if (!info.isDrop()) return false;
                    JList.DropLocation dl = (JList.DropLocation) info.getDropLocation();
                    int index = dl.getIndex();
                    addIndex = index;
                    addCount = 0;
                    try {
                        String data = (String) info.getTransferable().getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor);
                        String[] items = data.split("\n");
                        for (String s : items){ model.add(index++, s); addCount++; }
                        return true;
                    } catch (Exception ex){ return false; }
                }
                @Override protected void exportDone(JComponent c, java.awt.datatransfer.Transferable t, int action){
                    if (action == MOVE && indices != null){
                        // If we moved items to a later index, adjust for earlier removals
                        if (addIndex > indices[0]){
                            for (int i = indices.length - 1; i >= 0; i--) model.remove(indices[i]);
                        } else {
                            for (int i = 0; i < indices.length; i++) model.remove(indices[i]);
                        }
                        indices = null; addCount = 0; addIndex = -1;
                    }
                }
            });
        }
    }
}
