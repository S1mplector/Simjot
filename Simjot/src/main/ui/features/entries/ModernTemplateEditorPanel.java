/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
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
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragSource;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import main.infrastructure.backup.NotebookInfo;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.input.AeroTextField;
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
    
    private final NotebookInfo notebook;
    private JournalTemplateManager.JournalTemplate template;
    private boolean isNew = false;
    private boolean saved = false;
    
    private final AeroTextField nameField;
    private final AeroTextField descField;
    private final JPanel questionsPanel;
    private final List<QuestionRow> questionRows = new ArrayList<>();
    private final AeroTextField quickAddField;
    
    private int dragIndex = -1;
    private int dropIndex = -1;
    
    private Runnable onCancel;
    private Consumer<JournalTemplateManager.JournalTemplate> onSave;
    
    public ModernTemplateEditorPanel(NotebookInfo notebook, JournalTemplateManager.JournalTemplate existing) {
        this.notebook = notebook;
        this.template = existing;
        this.isNew = (existing == null);
        
        setOpaque(false);
        setLayout(new BorderLayout());
        
        FrostedGlassPanel main = new FrostedGlassPanel(new BorderLayout(0, 0), 18);
        main.setBorder(new EmptyBorder(0, 0, 0, 0));
        
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
        
        JLabel qHint = new JLabel("Drag to reorder • Click to edit • Press Delete to remove");
        qHint.setFont(qHint.getFont().deriveFont(11f));
        qHint.setForeground(TEXT_MUTED);
        
        questionsHeader.add(qLabel, BorderLayout.WEST);
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
                addQuestionRow(q);
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
        quickAddField.addActionListener(e -> {
            String text = quickAddField.getText().trim();
            if (!text.isEmpty()) {
                addQuestionRow(text);
                quickAddField.setText("");
                questionsPanel.revalidate();
                questionsPanel.repaint();
            }
        });
        
        RoundedButton addBtn = new RoundedButton("+ Add");
        addBtn.setPreferredSize(new Dimension(80, 36));
        addBtn.addActionListener(e -> {
            String text = quickAddField.getText().trim();
            if (!text.isEmpty()) {
                addQuestionRow(text);
                quickAddField.setText("");
            } else {
                addQuestionRow("New question...");
            }
            questionsPanel.revalidate();
            questionsPanel.repaint();
        });
        
        quickAddRow.add(quickAddField, BorderLayout.CENTER);
        quickAddRow.add(addBtn, BorderLayout.EAST);
        form.add(quickAddRow);
        
        // Read-only notice for built-ins
        if (!isNew && !template.isCustom()) {
            form.add(Box.createVerticalStrut(12));
            JLabel roNotice = new JLabel("ℹ This is a built-in template. Duplicate it to make changes.");
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
            RoundedButton dupBtn = new RoundedButton("Duplicate");
            dupBtn.setPreferredSize(new Dimension(100, 38));
            dupBtn.addActionListener(e -> duplicateTemplate());
            leftButtons.add(dupBtn);
        }
        
        // Right: Cancel / Save
        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        rightButtons.setOpaque(false);
        
        RoundedButton cancelBtn = new RoundedButton("Cancel");
        cancelBtn.setPreferredSize(new Dimension(100, 38));
        cancelBtn.addActionListener(e -> {
            if (onCancel != null) {
                onCancel.run();
            }
        });
        
        RoundedButton saveBtn = new RoundedButton(isNew ? "Create" : "Save");
        saveBtn.setPreferredSize(new Dimension(100, 38));
        saveBtn.addActionListener(e -> save());
        
        if (!isNew && !template.isCustom()) {
            saveBtn.setEnabled(false);
            saveBtn.setToolTipText("Built-in templates cannot be edited");
        }
        
        rightButtons.add(cancelBtn);
        rightButtons.add(saveBtn);
        
        footer.add(leftButtons, BorderLayout.WEST);
        footer.add(rightButtons, BorderLayout.EAST);
        main.add(footer, BorderLayout.SOUTH);
        
        add(main, BorderLayout.CENTER);
        
        SwingUtilities.invokeLater(() -> nameField.requestFocusInWindow());
    }
    
    public void setOnCancel(Runnable onCancel) {
        this.onCancel = onCancel;
    }
    
    public void setOnSave(Consumer<JournalTemplateManager.JournalTemplate> onSave) {
        this.onSave = onSave;
    }
    
    public boolean isSaved() { return saved; }
    
    public JournalTemplateManager.JournalTemplate getTemplate() { return template; }
    
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
        QuestionRow row = new QuestionRow(text, questionRows.size());
        questionRows.add(row);
        questionsPanel.add(row);
        questionsPanel.add(Box.createVerticalStrut(6));
    }
    
    private void removeQuestionRow(QuestionRow row) {
        int idx = questionRows.indexOf(row);
        if (idx >= 0) {
            questionRows.remove(idx);
            questionsPanel.remove(row);
            // Remove the spacer too
            if (idx * 2 < questionsPanel.getComponentCount()) {
                questionsPanel.remove(idx * 2);
            }
            // Update indices
            for (int i = 0; i < questionRows.size(); i++) {
                questionRows.get(i).updateIndex(i);
            }
            questionsPanel.revalidate();
            questionsPanel.repaint();
        }
    }
    
    private void moveQuestion(int fromIndex, int toIndex) {
        if (fromIndex == toIndex || fromIndex < 0 || toIndex < 0) return;
        if (fromIndex >= questionRows.size() || toIndex >= questionRows.size()) return;
        
        QuestionRow row = questionRows.remove(fromIndex);
        questionRows.add(toIndex, row);
        
        // Rebuild panel
        questionsPanel.removeAll();
        for (int i = 0; i < questionRows.size(); i++) {
            questionRows.get(i).updateIndex(i);
            questionsPanel.add(questionRows.get(i));
            questionsPanel.add(Box.createVerticalStrut(6));
        }
        questionsPanel.revalidate();
        questionsPanel.repaint();
    }
    
    private void save() {
        String name = nameField.getText().trim();
        String desc = descField.getText().trim();
        
        if (name.isEmpty()) {
            UIMessage.warn(this, "Name Required", "Please enter a template name.", "");
            nameField.requestFocusInWindow();
            return;
        }
        
        List<String> questions = new ArrayList<>();
        for (QuestionRow row : questionRows) {
            String q = row.getText().trim();
            if (!q.isEmpty()) {
                questions.add(q);
            }
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
        
        saved = true;
        if (onSave != null) {
            onSave.accept(template);
        }
    }
    
    private void duplicateTemplate() {
        String name = nameField.getText().trim();
        String desc = descField.getText().trim();
        
        List<String> questions = new ArrayList<>();
        for (QuestionRow row : questionRows) {
            String q = row.getText().trim();
            if (!q.isEmpty()) questions.add(q);
        }
        
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
    
    // ═══════════════════════════════════════════════════════════════════════════
    // QUESTION ROW - Draggable, editable question item
    // ═══════════════════════════════════════════════════════════════════════════
    
    private class QuestionRow extends JPanel {
        private final JTextField textField;
        private int index;
        private boolean hovered = false;
        private boolean dragging = false;
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
            
            // Delete button
            JButton deleteBtn = new JButton("×") {
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
            installDragDrop();
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
                dragging = true;
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
                    dragging = false;
                }
            });
        }
    }
}
