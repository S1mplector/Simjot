package main.ui.features.entries;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import main.infrastructure.backup.NotebookInfo;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.containers.RoundedPanel;
import main.ui.dialog.confirmation.CustomChoiceDialog;
import main.ui.dialog.confirmation.CustomConfirmDialog;
import main.ui.dialog.message.UIMessage;
import main.ui.dialog.input.CustomInputDialog;

/**
 * Dialog for managing journal entry templates: add, edit, remove.
 */
public class TemplateManagerDialog extends JDialog {
    private final DefaultListModel<JournalTemplateManager.JournalTemplate> listModel;
    private final JList<JournalTemplateManager.JournalTemplate> templateList;
    private final NotebookInfo notebook; // null => global management

    public TemplateManagerDialog(Frame parent) { this(parent, null); }

    public TemplateManagerDialog(Frame parent, NotebookInfo notebook) {
        super(parent, "Manage Templates", true);
        this.notebook = notebook;
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        setLayout(new BorderLayout());

        RoundedPanel mainPanel = new RoundedPanel();
        mainPanel.setArc(18);
        mainPanel.setLayout(new BorderLayout(12, 12));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        mainPanel.setBackground(Color.WHITE);

        // Title
        JLabel titleLabel = new JLabel("Journal Templates" + (notebook!=null? " — "+notebook.getName():""));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
        titleLabel.setForeground(new Color(40, 40, 40));
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        // Template list
        listModel = new DefaultListModel<>();
        templateList = new JList<>(listModel);
        templateList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        templateList.setCellRenderer(new TemplateListRenderer());
        refreshList();

        JScrollPane scroll = new JScrollPane(templateList);
        scroll.setPreferredSize(new Dimension(500, 300));
        mainPanel.add(scroll, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setOpaque(false);

        RoundedButton addBtn = new RoundedButton("Add New");
        addBtn.setPreferredSize(new Dimension(110, 36));
        addBtn.addActionListener(e -> addTemplate());

        RoundedButton editBtn = new RoundedButton("Edit");
        editBtn.setPreferredSize(new Dimension(110, 36));
        editBtn.addActionListener(e -> editTemplate());

        RoundedButton deleteBtn = new RoundedButton("Delete");
        deleteBtn.setPreferredSize(new Dimension(110, 36));
        deleteBtn.addActionListener(e -> deleteTemplate());

        RoundedButton closeBtn = new RoundedButton("Close");
        closeBtn.setPreferredSize(new Dimension(110, 36));
        closeBtn.addActionListener(e -> dispose());

        buttonPanel.add(addBtn);
        buttonPanel.add(editBtn);
        buttonPanel.add(deleteBtn);
        buttonPanel.add(closeBtn);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);
        pack();
        setLocationRelativeTo(parent);
    }

    private void refreshList() {
        listModel.clear();
        List<JournalTemplateManager.JournalTemplate> templates =
                (notebook!=null)
                        ? JournalTemplateManager.getInstance().getTemplates(notebook)
                        : JournalTemplateManager.getInstance().getTemplates();
        for (JournalTemplateManager.JournalTemplate template : templates) {
            listModel.addElement(template);
        }
    }

    private void addTemplate() {
        TemplateEditorDialog dialog = new TemplateEditorDialog((Frame) SwingUtilities.getWindowAncestor(this), null);
        dialog.setVisible(true);
        if (dialog.isAccepted()) {
            JournalTemplateManager.JournalTemplate template = dialog.getTemplate();
            if (notebook != null) {
                template.setScope(JournalTemplateManager.Scope.NOTEBOOK_CUSTOM);
                template.setNotebookName(notebook.getName());
                JournalTemplateManager.getInstance().addTemplateForNotebook(notebook, template);
            } else {
                template.setScope(JournalTemplateManager.Scope.GLOBAL_CUSTOM);
                JournalTemplateManager.getInstance().addTemplate(template);
            }
            refreshList();
        }
    }

    private void editTemplate() {
        JournalTemplateManager.JournalTemplate selected = templateList.getSelectedValue();
        if (selected == null) {
            UIMessage.warn(this,
                    "No Template Selected",
                    "You haven't selected a template to edit.",
                    "Click a template in the list, then press Edit.");
            return;
        }
        if (!selected.isCustom()) {
            UIMessage.warn(this,
                    "Editing Not Allowed",
                    "Built-in templates are read-only.",
                    "Choose a custom template or create a new one with Add New.");
            return;
        }

        TemplateEditorDialog dialog = new TemplateEditorDialog((Frame) SwingUtilities.getWindowAncestor(this), selected);
        dialog.setVisible(true);
        if (dialog.isAccepted()) {
            JournalTemplateManager.getInstance().updateTemplate(selected);
            refreshList();
        }
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
            
            String text = template.getName();
            if (!template.isCustom()) {
                text += " (Built-in)";
            } else if (template.getScope()==JournalTemplateManager.Scope.NOTEBOOK_CUSTOM) {
                text += " (Notebook)";
            } else {
                text += " (Custom)";
            }
            setText(text);

            if (isSelected) {
                setBackground(new Color(88, 133, 255, 50));
                setForeground(new Color(40, 40, 40));
            } else {
                setBackground(Color.WHITE);
                setForeground(new Color(60, 60, 60));
            }

            return this;
        }
    }

    /**
     * Dialog for creating/editing a single template
     */
    private static class TemplateEditorDialog extends JDialog {
        private boolean accepted = false;
        private JournalTemplateManager.JournalTemplate template;
        private final JTextField nameField;
        private final JTextField descField;
        private final DefaultListModel<String> questionModel;
        private final JList<String> questionList;

        TemplateEditorDialog(Frame parent, JournalTemplateManager.JournalTemplate existing) {
            super(parent, existing == null ? "New Template" : "Edit Template", true);
            this.template = existing;
            
            setUndecorated(true);
            setBackground(new Color(0, 0, 0, 0));
            setLayout(new BorderLayout());

            RoundedPanel mainPanel = new RoundedPanel();
            mainPanel.setArc(24);
            mainPanel.setFlat(true); // remove gradient/glass
            mainPanel.setLayout(new BorderLayout(16, 16));
            mainPanel.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));
            mainPanel.setBackground(Color.WHITE);

            // Header
            JLabel hdr = new JLabel(existing == null ? "Create Template" : "Edit Template");
            hdr.setFont(hdr.getFont().deriveFont(Font.BOLD, 18f));
            hdr.setForeground(new Color(40,40,40));
            mainPanel.add(hdr, BorderLayout.NORTH);

            // Form
            JPanel formPanel = new JPanel(new GridBagLayout());
            formPanel.setOpaque(false);
            GridBagConstraints gc = new GridBagConstraints();
            gc.insets = new Insets(8, 8, 8, 8);
            gc.fill = GridBagConstraints.HORIZONTAL;

            gc.gridx = 0; gc.gridy = 0; gc.weightx = 0;
            JLabel nameLbl = new JLabel("Name");
            nameLbl.setFont(nameLbl.getFont().deriveFont(Font.BOLD));
            formPanel.add(nameLbl, gc);
            gc.gridx = 1; gc.weightx = 1;
            nameField = new JTextField(existing != null ? existing.getName() : "", 30);
            nameField.setFont(nameField.getFont().deriveFont(14f));
            formPanel.add(nameField, gc);

            gc.gridx = 0; gc.gridy = 1; gc.weightx = 0;
            JLabel descLbl = new JLabel("Description");
            descLbl.setFont(descLbl.getFont().deriveFont(Font.BOLD));
            formPanel.add(descLbl, gc);
            gc.gridx = 1; gc.weightx = 1;
            descField = new JTextField(existing != null ? existing.getDescription() : "", 30);
            descField.setFont(descField.getFont().deriveFont(14f));
            formPanel.add(descField, gc);

            gc.gridx = 0; gc.gridy = 2; gc.gridwidth = 2;
            JLabel qLbl = new JLabel("Questions");
            qLbl.setFont(qLbl.getFont().deriveFont(Font.BOLD));
            formPanel.add(qLbl, gc);

            questionModel = new DefaultListModel<>();
            if (existing != null) {
                for (String q : existing.getQuestions()) {
                    questionModel.addElement(q);
                }
            }
            questionList = new JList<>(questionModel);
            questionList.setFont(questionList.getFont().deriveFont(14f));
            JScrollPane qScroll = new JScrollPane(questionList);
            qScroll.setBorder(BorderFactory.createLineBorder(new Color(210,216,228)));
            qScroll.setPreferredSize(new Dimension(480, 200));
            // modern slim scrollbars
            try {
                JScrollBar vbar = qScroll.getVerticalScrollBar();
                vbar.setUI(new main.ui.components.scrollbar.ModernScrollBarUI());
                vbar.setPreferredSize(new Dimension(10, Integer.MAX_VALUE));
                vbar.setOpaque(false);
                JScrollBar hbar = qScroll.getHorizontalScrollBar();
                hbar.setUI(new main.ui.components.scrollbar.ModernScrollBarUI());
                hbar.setPreferredSize(new Dimension(Integer.MAX_VALUE, 10));
                hbar.setOpaque(false);
            } catch (Throwable ignored) {}
            gc.gridy = 3;
            formPanel.add(qScroll, gc);

            JPanel qButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            qButtons.setOpaque(false);
            RoundedButton addQBtn = new RoundedButton("Add");
            addQBtn.putClientProperty("iconId", "new");
            addQBtn.setPreferredSize(new Dimension(90, 32));
            addQBtn.addActionListener(e -> addQuestion());
            RoundedButton editQBtn = new RoundedButton("Edit");
            editQBtn.putClientProperty("iconId", "write");
            editQBtn.setPreferredSize(new Dimension(90, 32));
            editQBtn.addActionListener(e -> editQuestion());
            RoundedButton deleteQBtn = new RoundedButton("Delete");
            deleteQBtn.putClientProperty("iconId", "delete");
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

            mainPanel.add(formPanel, BorderLayout.CENTER);

            // Bottom buttons
            JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            bottomPanel.setOpaque(false);

            RoundedButton saveBtn = new RoundedButton("Save");
            saveBtn.putClientProperty("iconId", "save");
            saveBtn.setPreferredSize(new Dimension(120, 36));
            saveBtn.addActionListener(e -> save());

            RoundedButton cancelBtn = new RoundedButton("Cancel");
            cancelBtn.putClientProperty("iconId", "close");
            cancelBtn.setPreferredSize(new Dimension(120, 36));
            cancelBtn.addActionListener(e -> dispose());

            bottomPanel.add(saveBtn);
            bottomPanel.add(cancelBtn);
            mainPanel.add(bottomPanel, BorderLayout.SOUTH);

            add(mainPanel);
            pack();
            setLocationRelativeTo(parent);

            // default button + ESC to cancel
            getRootPane().setDefaultButton(saveBtn);
            getRootPane().registerKeyboardAction(e -> dispose(),
                    KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                    JComponent.WHEN_IN_FOCUSED_WINDOW);
        }

        private void addQuestion() {
            String q = CustomInputDialog.prompt(this, "Add Question", "Enter question:", "");
            if (q != null && !q.trim().isEmpty()) {
                questionModel.addElement(q.trim());
            }
        }

        private void editQuestion() {
            int index = questionList.getSelectedIndex();
            if (index < 0) return;
            String current = questionModel.get(index);
            String edited = CustomInputDialog.prompt(this, "Edit Question", "Edit question:", current);
            if (edited != null && !edited.trim().isEmpty()) {
                questionModel.set(index, edited.trim());
            }
        }

        private void deleteQuestion() {
            int index = questionList.getSelectedIndex();
            if (index >= 0) {
                questionModel.remove(index);
            }
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
            for (int i = 0; i < questionModel.size(); i++) {
                questions.add(questionModel.get(i));
            }

            if (template != null) {
                // Edit existing
                template.setName(name);
                template.setDescription(desc);
                template.setQuestions(questions.toArray(new String[0]));
            } else {
                // Create new (will be added by caller)
                String id = "CUSTOM_" + System.currentTimeMillis();
                JournalTemplateManager.JournalTemplate newTemplate = 
                    new JournalTemplateManager.JournalTemplate(id, name, desc, questions.toArray(new String[0]), true);
                this.template = newTemplate;
            }

            accepted = true;
            dispose();
        }

        boolean isAccepted() { return accepted; }
        JournalTemplateManager.JournalTemplate getTemplate() { return template; }
    }
}
