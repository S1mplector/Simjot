package main.ui.features.entries;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.containers.RoundedPanel;
import main.ui.dialog.message.UIMessage;

/**
 * Dialog for managing journal entry templates: add, edit, remove.
 */
public class TemplateManagerDialog extends JDialog {
    private final DefaultListModel<JournalTemplateManager.JournalTemplate> listModel;
    private final JList<JournalTemplateManager.JournalTemplate> templateList;

    public TemplateManagerDialog(Frame parent) {
        super(parent, "Manage Templates", true);
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        setLayout(new BorderLayout());

        RoundedPanel mainPanel = new RoundedPanel();
        mainPanel.setArc(18);
        mainPanel.setLayout(new BorderLayout(12, 12));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        mainPanel.setBackground(Color.WHITE);

        // Title
        JLabel titleLabel = new JLabel("Journal Templates");
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
        List<JournalTemplateManager.JournalTemplate> templates = JournalTemplateManager.getInstance().getTemplates();
        for (JournalTemplateManager.JournalTemplate template : templates) {
            listModel.addElement(template);
        }
    }

    private void addTemplate() {
        TemplateEditorDialog dialog = new TemplateEditorDialog((Frame) SwingUtilities.getWindowAncestor(this), null);
        dialog.setVisible(true);
        if (dialog.isAccepted()) {
            JournalTemplateManager.JournalTemplate template = dialog.getTemplate();
            JournalTemplateManager.getInstance().addTemplate(template);
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
            UIMessage.warn(this,
                    "Deletion Not Allowed",
                    "Built-in templates can't be deleted.",
                    "Select a custom template you created to delete it.");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this, 
            "Delete template '" + selected.getName() + "'?", 
            "Confirm Delete", 
            JOptionPane.YES_NO_OPTION);
        
        if (confirm == JOptionPane.YES_OPTION) {
            JournalTemplateManager.getInstance().removeTemplate(selected.getId());
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
            mainPanel.setArc(16);
            mainPanel.setLayout(new BorderLayout(12, 12));
            mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            mainPanel.setBackground(Color.WHITE);

            // Form
            JPanel formPanel = new JPanel(new GridBagLayout());
            formPanel.setOpaque(false);
            GridBagConstraints gc = new GridBagConstraints();
            gc.insets = new Insets(4, 4, 4, 4);
            gc.fill = GridBagConstraints.HORIZONTAL;

            gc.gridx = 0; gc.gridy = 0; gc.weightx = 0;
            formPanel.add(new JLabel("Name:"), gc);
            gc.gridx = 1; gc.weightx = 1;
            nameField = new JTextField(existing != null ? existing.getName() : "", 30);
            formPanel.add(nameField, gc);

            gc.gridx = 0; gc.gridy = 1; gc.weightx = 0;
            formPanel.add(new JLabel("Description:"), gc);
            gc.gridx = 1; gc.weightx = 1;
            descField = new JTextField(existing != null ? existing.getDescription() : "", 30);
            formPanel.add(descField, gc);

            gc.gridx = 0; gc.gridy = 2; gc.gridwidth = 2;
            formPanel.add(new JLabel("Questions:"), gc);

            questionModel = new DefaultListModel<>();
            if (existing != null) {
                for (String q : existing.getQuestions()) {
                    questionModel.addElement(q);
                }
            }
            questionList = new JList<>(questionModel);
            JScrollPane qScroll = new JScrollPane(questionList);
            qScroll.setPreferredSize(new Dimension(400, 150));
            gc.gridy = 3;
            formPanel.add(qScroll, gc);

            JPanel qButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
            qButtons.setOpaque(false);
            RoundedButton addQBtn = new RoundedButton("Add Question");
            addQBtn.addActionListener(e -> addQuestion());
            RoundedButton editQBtn = new RoundedButton("Edit");
            editQBtn.addActionListener(e -> editQuestion());
            RoundedButton deleteQBtn = new RoundedButton("Delete");
            deleteQBtn.addActionListener(e -> deleteQuestion());
            qButtons.add(addQBtn);
            qButtons.add(editQBtn);
            qButtons.add(deleteQBtn);
            gc.gridy = 4;
            formPanel.add(qButtons, gc);

            mainPanel.add(formPanel, BorderLayout.CENTER);

            // Bottom buttons
            JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            bottomPanel.setOpaque(false);

            RoundedButton saveBtn = new RoundedButton("Save");
            saveBtn.setPreferredSize(new Dimension(100, 36));
            saveBtn.addActionListener(e -> save());

            RoundedButton cancelBtn = new RoundedButton("Cancel");
            cancelBtn.setPreferredSize(new Dimension(100, 36));
            cancelBtn.addActionListener(e -> dispose());

            bottomPanel.add(saveBtn);
            bottomPanel.add(cancelBtn);
            mainPanel.add(bottomPanel, BorderLayout.SOUTH);

            add(mainPanel);
            pack();
            setLocationRelativeTo(parent);
        }

        private void addQuestion() {
            String q = JOptionPane.showInputDialog(this, "Enter question:", "Add Question", JOptionPane.PLAIN_MESSAGE);
            if (q != null && !q.trim().isEmpty()) {
                questionModel.addElement(q.trim());
            }
        }

        private void editQuestion() {
            int index = questionList.getSelectedIndex();
            if (index < 0) return;
            String current = questionModel.get(index);
            String edited = (String) JOptionPane.showInputDialog(this, "Edit question:", "Edit Question", 
                JOptionPane.PLAIN_MESSAGE, null, null, current);
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
