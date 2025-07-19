package main.ui.panels;

import java.awt.*;
import java.awt.event.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import main.dialog.CustomMessageDialog;
import main.model.Task;
import main.transitions.FadingButton;
import main.ui.JournalApp;
import main.ui.buttons.RoundedButton;
import main.ui.components.ModernCheckBoxUI;
import main.util.SettingsStore;
import main.util.TaskStore;

/**
 * Modern TodoPanel following current Simjot design patterns.
 * Features: BorderLayout structure, modern UI components, TaskStore integration,
 * background handling, and consistent styling with other panels.
 */
public class TodoPanel extends JPanel {
    
    private final JournalApp app;
    private final CardLayout cardLayout;
    private final JPanel cardPanel;
    
    // UI Components
    private ModernTextField newTaskField;
    private JPanel taskListPanel;
    private JScrollPane taskScrollPane;
    private JLabel statsLabel;
    private RoundedButton addButton;
    private RoundedButton clearCompletedButton;
    
    // Task management
    private final TaskStore taskStore = TaskStore.get();
    
    public TodoPanel(JournalApp app, CardLayout cardLayout, JPanel cardPanel) {
        this.app = app;
        this.cardLayout = cardLayout;
        this.cardPanel = cardPanel;
        
        setLayout(new BorderLayout());
        setOpaque(false);
        setBackground(new Color(250, 250, 245)); // Paper-like color matching other panels
        
        initUI();
        refreshTaskList();
    }
    
    private void initUI() {
        // --- Modern Toolbar (NORTH) ---
        createToolbar();
        
        // --- Task List Container (CENTER) ---
        createTaskListArea();
        
        // --- Statistics and Actions (SOUTH) ---
        createBottomPanel();
    }
    
    private void createToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        toolbar.setBackground(new Color(230, 230, 230)); // Matching NewEntryPanel toolbar
        toolbar.setPreferredSize(new Dimension(0, 50));
        
        // Back button
        RoundedButton backButton = new RoundedButton("Back");
        backButton.addActionListener(e -> app.switchCard(JournalApp.MAIN_MENU));
        toolbar.add(backButton);
        
        // Title label
        JLabel titleLabel = new JLabel("Tasks");
        titleLabel.setForeground(Color.DARK_GRAY);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        toolbar.add(Box.createHorizontalStrut(10));
        toolbar.add(titleLabel);
        
        // Add task input
        newTaskField = new ModernTextField(20);
        newTaskField.setFont(new Font("SansSerif", Font.PLAIN, 14));
        newTaskField.addActionListener(e -> addNewTask());
        newTaskField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    addNewTask();
                }
            }
        });
        toolbar.add(Box.createHorizontalStrut(20));
        toolbar.add(new JLabel("New Task:"));
        toolbar.add(newTaskField);
        
        // Add button
        addButton = new RoundedButton("Add");
        addButton.addActionListener(e -> addNewTask());
        toolbar.add(addButton);
        
        // Clear completed button
        clearCompletedButton = new RoundedButton("Clear Completed");
        clearCompletedButton.addActionListener(e -> clearCompletedTasks());
        toolbar.add(Box.createHorizontalStrut(10));
        toolbar.add(clearCompletedButton);
        
        add(toolbar, BorderLayout.NORTH);
    }
    
    private void createTaskListArea() {
        // Task list panel with BoxLayout for vertical stacking
        taskListPanel = new JPanel();
        taskListPanel.setLayout(new BoxLayout(taskListPanel, BoxLayout.Y_AXIS));
        taskListPanel.setOpaque(false);
        taskListPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // Scroll pane for task list
        taskScrollPane = new JScrollPane(taskListPanel);
        taskScrollPane.setOpaque(false);
        taskScrollPane.getViewport().setOpaque(false);
        taskScrollPane.setBorder(BorderFactory.createEmptyBorder());
        taskScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        taskScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        
        add(taskScrollPane, BorderLayout.CENTER);
    }
    
    private void createBottomPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(new EmptyBorder(5, 15, 10, 15));
        
        // Statistics label
        statsLabel = new JLabel();
        statsLabel.setForeground(Color.GRAY);
        statsLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
        updateStatsLabel();
        bottomPanel.add(statsLabel, BorderLayout.WEST);
        
        // Action buttons on the right
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actionPanel.setOpaque(false);
        
        FadingButton refreshButton = new FadingButton("Refresh");
        refreshButton.setBackground(Color.DARK_GRAY);
        refreshButton.setForeground(Color.WHITE);
        refreshButton.addActionListener(e -> refreshTaskList());
        actionPanel.add(refreshButton);
        
        bottomPanel.add(actionPanel, BorderLayout.EAST);
        
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        // Handle background painting based on user settings
        String bgPath = SettingsStore.get().getBackgroundImage();
        if (bgPath != null && !bgPath.isEmpty()) {
            // If user has a background set, let the parent handle it
            super.paintComponent(g);
        } else {
            // Default paper-like background
            super.paintComponent(g);
        }
    }
    
    private void addNewTask() {
        String taskText = newTaskField.getText().trim();
        if (taskText.isEmpty()) {
            return;
        }
        
        Task newTask = new Task(taskText);
        taskStore.addTask(newTask);
        newTaskField.setText("");
        refreshTaskList();
    }
    
    private void clearCompletedTasks() {
        int completedCount = taskStore.getCompletedCount();
        if (completedCount == 0) {
            CustomMessageDialog.display(this, "No Completed Tasks", "There are no completed tasks to clear.", false);
            return;
        }
        
        boolean confirmed = main.dialog.CustomConfirmDialog.confirm(
            SwingUtilities.getWindowAncestor(this), 
            "Clear Completed Tasks", 
            "Remove " + completedCount + " completed task(s)?"
        );
        
        if (confirmed) {
            taskStore.clearCompletedTasks();
            refreshTaskList();
        }
    }
    
    private void refreshTaskList() {
        taskListPanel.removeAll();
        
        List<Task> allTasks = taskStore.getAllTasks();
        if (allTasks.isEmpty()) {
            // Show empty state
            JLabel emptyLabel = new JLabel("No tasks yet. Add your first task above!");
            emptyLabel.setForeground(Color.GRAY);
            emptyLabel.setFont(new Font("SansSerif", Font.ITALIC, 16));
            emptyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            emptyLabel.setBorder(new EmptyBorder(40, 20, 40, 20));
            taskListPanel.add(emptyLabel);
        } else {
            // Add active tasks first, then completed tasks
            List<Task> activeTasks = taskStore.getActiveTasks();
            List<Task> completedTasks = taskStore.getCompletedTasks();
            
            if (!activeTasks.isEmpty()) {
                addTaskSection("Active Tasks", activeTasks, false);
            }
            
            if (!completedTasks.isEmpty()) {
                addTaskSection("Completed Tasks", completedTasks, true);
            }
        }
        
        updateStatsLabel();
        taskListPanel.revalidate();
        taskListPanel.repaint();
    }
    
    private void addTaskSection(String sectionTitle, List<Task> tasks, boolean isCompleted) {
        // Section header
        if (taskListPanel.getComponentCount() > 0) {
            taskListPanel.add(Box.createRigidArea(new Dimension(0, 15)));
        }
        
        JLabel sectionLabel = new JLabel(sectionTitle);
        sectionLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        sectionLabel.setForeground(isCompleted ? Color.GRAY : Color.DARK_GRAY);
        sectionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        taskListPanel.add(sectionLabel);
        taskListPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        
        // Tasks in this section
        for (Task task : tasks) {
            TaskItemPanel taskItem = new TaskItemPanel(task);
            taskItem.setAlignmentX(Component.LEFT_ALIGNMENT);
            taskListPanel.add(taskItem);
            taskListPanel.add(Box.createRigidArea(new Dimension(0, 3)));
        }
    }
    
    private void updateStatsLabel() {
        int total = taskStore.getTaskCount();
        int completed = taskStore.getCompletedCount();
        int active = taskStore.getActiveCount();
        double percentage = taskStore.getCompletionPercentage();
        
        if (total == 0) {
            statsLabel.setText("No tasks");
        } else {
            statsLabel.setText(String.format(
                "Tasks: %d total, %d active, %d completed (%.1f%%)", 
                total, active, completed, percentage
            ));
        }
    }
    
    // --- Task Item Panel ---
    private class TaskItemPanel extends JPanel {
        private final Task task;
        private JCheckBox completedCheckBox;
        private JLabel taskLabel;
        private JLabel priorityLabel;
        private JLabel dateLabel;
        private FadingButton deleteButton;
        
        public TaskItemPanel(Task task) {
            this.task = task;
            setLayout(new BorderLayout(8, 0));
            setOpaque(false);
            setBorder(new EmptyBorder(8, 12, 8, 12));
            initTaskItem();
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            // Draw rounded background
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            Color bgColor = task.isCompleted() ? 
                new Color(245, 245, 245, 180) :  // Light gray for completed
                new Color(255, 255, 255, 200);   // White for active
                
            g2.setColor(bgColor);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            
            // Subtle border
            g2.setColor(new Color(220, 220, 220));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            
            g2.dispose();
            super.paintComponent(g);
        }
        
        private void initTaskItem() {
            // Completion checkbox
            completedCheckBox = new JCheckBox();
            completedCheckBox.setSelected(task.isCompleted());
            completedCheckBox.setOpaque(false);
            completedCheckBox.setUI(new ModernCheckBoxUI());
            completedCheckBox.addActionListener(e -> toggleTaskCompletion());
            add(completedCheckBox, BorderLayout.WEST);
            
            // Main content panel
            JPanel contentPanel = new JPanel(new BorderLayout(5, 2));
            contentPanel.setOpaque(false);
            
            // Task text
            taskLabel = new JLabel(task.getText());
            taskLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
            if (task.isCompleted()) {
                taskLabel.setForeground(Color.GRAY);
                // Add strikethrough effect
                taskLabel.setText("<html><strike>" + task.getText() + "</strike></html>");
            } else {
                taskLabel.setForeground(Color.DARK_GRAY);
            }
            contentPanel.add(taskLabel, BorderLayout.CENTER);
            
            // Details panel (priority, date)
            JPanel detailsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            detailsPanel.setOpaque(false);
            
            // Priority indicator
            priorityLabel = new JLabel("●");
            priorityLabel.setForeground(task.getPriority().getColor());
            priorityLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
            priorityLabel.setToolTipText(task.getPriority().getDisplayName() + " Priority");
            detailsPanel.add(priorityLabel);
            
            // Creation date
            dateLabel = new JLabel(task.getFormattedCreatedDate());
            dateLabel.setForeground(Color.LIGHT_GRAY);
            dateLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
            detailsPanel.add(dateLabel);
            
            contentPanel.add(detailsPanel, BorderLayout.SOUTH);
            add(contentPanel, BorderLayout.CENTER);
            
            // Delete button
            deleteButton = new FadingButton("×");
            deleteButton.setFont(new Font("SansSerif", Font.BOLD, 14));
            deleteButton.setForeground(Color.RED);
            deleteButton.setBackground(Color.WHITE);
            deleteButton.setPreferredSize(new Dimension(25, 25));
            deleteButton.addActionListener(e -> deleteTask());
            deleteButton.setToolTipText("Delete task");
            add(deleteButton, BorderLayout.EAST);
        }
        
        private void toggleTaskCompletion() {
            task.setCompleted(completedCheckBox.isSelected());
            taskStore.updateTask(task);
            refreshTaskList();
        }
        
        private void deleteTask() {
            boolean confirmed = main.dialog.CustomConfirmDialog.confirm(
                SwingUtilities.getWindowAncestor(this),
                "Delete Task",
                "Are you sure you want to delete this task?\n\"" + task.getText() + "\""
            );
            
            if (confirmed) {
                taskStore.removeTask(task);
                refreshTaskList();
            }
        }
    }
    
    // --- Modern Text Field Component ---
    private static class ModernTextField extends JTextField {
        public ModernTextField(int cols) {
            super(cols);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            super.paintComponent(g2);
            g2.dispose();
        }
        
        @Override
        protected void paintBorder(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(hasFocus() ? new Color(0, 120, 215) : Color.LIGHT_GRAY);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            g2.dispose();
        }
    }
}
