package main.ui.panels;

import java.awt.*;
import java.awt.event.*;
import java.time.LocalDate;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import main.dialog.CustomMessageDialog;
import main.model.Task;
import main.transitions.FadingButton;
import main.ui.JournalApp;
import main.ui.buttons.RoundedButton;
import main.ui.components.ModernCheckBoxUI;
import main.ui.components.ModernComboBoxRenderer;
import main.ui.components.ModernComboBoxUI;
import main.ui.components.ModernDatePicker;
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
    private JComboBox<Task.Priority> prioritySelector;
    private JComboBox<Task.ReminderSettings> reminderSelector;
    private ModernDatePicker dueDatePicker;
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
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 15));
        toolbar.setBackground(new Color(230, 230, 230)); // Matching NewEntryPanel toolbar
        toolbar.setPreferredSize(new Dimension(0, 100)); // Increased height for better spacing
        
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
        
        // Priority selector with modern UI
        prioritySelector = new JComboBox<>(Task.Priority.values());
        prioritySelector.setSelectedItem(Task.Priority.MEDIUM); // Default to medium priority
        prioritySelector.setUI(new ModernComboBoxUI());
        prioritySelector.setRenderer(new PriorityComboBoxRenderer());
        toolbar.add(Box.createHorizontalStrut(5));
        toolbar.add(new JLabel("Priority:"));
        toolbar.add(prioritySelector);
        
        // Reminder selector with modern UI
        reminderSelector = new JComboBox<>(Task.ReminderSettings.values());
        reminderSelector.setSelectedItem(Task.ReminderSettings.NONE); // Default to no reminder
        reminderSelector.setUI(new ModernComboBoxUI());
        reminderSelector.setRenderer(new ReminderComboBoxRenderer());
        toolbar.add(Box.createHorizontalStrut(5));
        toolbar.add(new JLabel("Reminder:"));
        toolbar.add(reminderSelector);
        
        // Due date picker
        dueDatePicker = new ModernDatePicker();
        toolbar.add(Box.createHorizontalStrut(5));
        toolbar.add(new JLabel("Due:"));
        toolbar.add(dueDatePicker);
        
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
        
        Task.Priority selectedPriority = (Task.Priority) prioritySelector.getSelectedItem();
        Task.ReminderSettings selectedReminder = (Task.ReminderSettings) reminderSelector.getSelectedItem();
        LocalDate selectedDueDate = dueDatePicker.getSelectedDate();
        
        Task newTask = new Task(taskText);
        newTask.setPriority(selectedPriority);
        newTask.setReminderSettings(selectedReminder);
        newTask.setDueDate(selectedDueDate);
        
        taskStore.addTask(newTask);
        
        // Reset form to defaults
        newTaskField.setText("");
        prioritySelector.setSelectedItem(Task.Priority.MEDIUM);
        reminderSelector.setSelectedItem(Task.ReminderSettings.NONE);
        dueDatePicker.setSelectedDate(null);
        
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
            // Sort tasks by urgency and priority
            List<Task> activeTasks = taskStore.getActiveTasksSortedByUrgency();
            List<Task> completedTasks = taskStore.getCompletedTasks();
            
            // Sort completed tasks by completion time (most recent first)
            completedTasks.sort((t1, t2) -> {
                if (t1.getCompletedAt() != null && t2.getCompletedAt() != null) {
                    return t2.getCompletedAt().compareTo(t1.getCompletedAt());
                }
                return 0;
            });
            
            if (!activeTasks.isEmpty()) {
                addTaskSection("Active Tasks (Sorted by Urgency)", activeTasks, false);
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
    
    // --- Urgency Helper Methods ---
    private Color getUrgencyBorderColor(Task task) {
        if (task.isCompleted()) {
            return new Color(200, 200, 200); // Light gray for completed
        }
        
        switch (task.getUrgencyLevel()) {
            case OVERDUE:
                return new Color(220, 50, 50); // Red for overdue
            case DUE_TODAY:
                return new Color(255, 140, 0); // Orange for due today
            case DUE_THIS_WEEK:
                return new Color(255, 200, 0); // Yellow for due this week
            case FUTURE:
                return new Color(100, 150, 255); // Blue for future
            case NO_DUE_DATE:
            default:
                return new Color(230, 230, 230); // Default gray
        }
    }
    
    private Color getUrgencyTextColor(Task.UrgencyLevel urgency) {
        switch (urgency) {
            case OVERDUE:
                return new Color(200, 50, 50); // Red
            case DUE_TODAY:
                return new Color(255, 100, 0); // Orange
            case DUE_THIS_WEEK:
                return new Color(200, 150, 0); // Dark yellow
            case FUTURE:
                return new Color(80, 120, 200); // Blue
            case NO_DUE_DATE:
            default:
                return Color.GRAY;
        }
    }
    
    private JLabel createUrgencyIndicator(Task.UrgencyLevel urgency) {
        JLabel indicator = new JLabel();
        indicator.setFont(new Font("SansSerif", Font.BOLD, 12));
        indicator.setForeground(getUrgencyTextColor(urgency));
        
        switch (urgency) {
            case OVERDUE:
                indicator.setText("⚠️");
                indicator.setToolTipText("Overdue");
                break;
            case DUE_TODAY:
                indicator.setText("🔥");
                indicator.setToolTipText("Due Today");
                break;
            case DUE_THIS_WEEK:
                indicator.setText("📅");
                indicator.setToolTipText("Due This Week");
                break;
            case FUTURE:
                indicator.setText("📋");
                indicator.setToolTipText("Future Task");
                break;
            default:
                indicator.setText("");
                break;
        }
        
        return indicator;
    }
    
    // --- Task Item Panel ---
    private class TaskItemPanel extends JPanel {
        private final Task task;
        private JCheckBox completedCheckBox;
        private JLabel taskLabel;
        private JLabel priorityLabel;
        private JLabel dateLabel;
        private FadingButton deleteButton;
        private boolean isDragging = false;
        
        public TaskItemPanel(Task task) {
            this.task = task;
            setLayout(new BorderLayout(8, 0));
            setOpaque(false);
            setBorder(new EmptyBorder(8, 12, 8, 12));
            
            // Set uniform height for all task items
            setPreferredSize(new Dimension(0, 60));
            setMinimumSize(new Dimension(0, 60));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
            
            initTaskItem();
            setupDragAndDrop();
        }
        
        private void setupDragAndDrop() {
            // Drag and drop functionality will be added in a future update
            // For now, focus on core UI improvements
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
            
            // Urgency-based border
            Color borderColor = getUrgencyBorderColor(task);
            int borderWidth = task.isCompleted() ? 1 : 2;
            g2.setColor(borderColor);
            g2.setStroke(new BasicStroke(borderWidth));
            g2.drawRoundRect(borderWidth/2, borderWidth/2, getWidth() - borderWidth,
                           getHeight() - borderWidth, 12, 12);
            
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
            
            // Main content panel - using FlowLayout to center content vertically
            JPanel contentPanel = new JPanel();
            contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
            contentPanel.setOpaque(false);
            
            // Add vertical glue to center content
            contentPanel.add(Box.createVerticalGlue());
            
            // Task text panel with urgency indicator
            JPanel textPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            textPanel.setOpaque(false);
            textPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            // Add urgency indicator for active tasks
            if (!task.isCompleted() && task.getUrgencyLevel() != Task.UrgencyLevel.NO_DUE_DATE) {
                JLabel urgencyIndicator = createUrgencyIndicator(task.getUrgencyLevel());
                textPanel.add(urgencyIndicator);
                textPanel.add(Box.createHorizontalStrut(4));
            }
            
            taskLabel = new JLabel(task.getText());
            taskLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
            if (task.isCompleted()) {
                taskLabel.setForeground(Color.GRAY);
                // Add strikethrough effect
                taskLabel.setText("<html><strike>" + task.getText() + "</strike></html>");
            } else {
                taskLabel.setForeground(Color.DARK_GRAY);
            }
            textPanel.add(taskLabel);
            contentPanel.add(textPanel);
            
            // Details panel (priority, dates, due date info)
            JPanel detailsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            detailsPanel.setOpaque(false);
            detailsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            // Priority indicator
            priorityLabel = new JLabel("●");
            priorityLabel.setForeground(task.getPriority().getColor());
            priorityLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
            priorityLabel.setToolTipText(task.getPriority().getDisplayName() + " Priority");
            detailsPanel.add(priorityLabel);
            
            // Due date information (if exists)
            if (task.getDueDate() != null) {
                JLabel dueDateLabel = new JLabel("📅 " + task.getFormattedDueDate());
                dueDateLabel.setForeground(task.isCompleted() ? Color.GRAY : Color.DARK_GRAY);
                dueDateLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
                dueDateLabel.setToolTipText("Due Date");
                detailsPanel.add(dueDateLabel);
                
                // Add countdown for active tasks
                if (!task.isCompleted()) {
                    String countdown = task.getDueDateCountdown();
                    if (!countdown.isEmpty()) {
                        JLabel countdownLabel = new JLabel("(" + countdown + ")");
                        countdownLabel.setForeground(getUrgencyTextColor(task.getUrgencyLevel()));
                        countdownLabel.setFont(new Font("SansSerif", Font.ITALIC, 10));
                        detailsPanel.add(countdownLabel);
                    }
                }
            }
            
            // Creation date (smaller, more subtle for tasks with due dates)
            dateLabel = new JLabel(task.getDueDate() != null ?
                "Created: " + task.getFormattedCreatedDate() :
                task.getFormattedCreatedDate());
            dateLabel.setForeground(Color.LIGHT_GRAY);
            dateLabel.setFont(new Font("SansSerif", Font.PLAIN, 9));
            detailsPanel.add(dateLabel);
            
            // Reminder indicator
            if (task.getReminderSettings() != Task.ReminderSettings.NONE) {
                JLabel reminderLabel = new JLabel("🔔");
                reminderLabel.setForeground(new Color(0, 120, 215));
                reminderLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
                reminderLabel.setToolTipText("Reminder: " + task.getReminderSettings().getDisplayName());
                detailsPanel.add(reminderLabel);
            }
            
            contentPanel.add(detailsPanel);
            
            // Add vertical glue to center content
            contentPanel.add(Box.createVerticalGlue());
            
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
    
    // Note: Drag-and-drop reordering functionality will be implemented in a future update
    // when the module system can properly support the required drag-and-drop imports
    
    // --- Priority ComboBox Renderer extending ModernComboBoxRenderer ---
    private static class PriorityComboBoxRenderer extends ModernComboBoxRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            
            if (value instanceof Task.Priority priority) {
                setText("● " + priority.getDisplayName());
                
                // Use priority color when not selected, white when selected
                if (!isSelected) {
                    setForeground(priority.getColor());
                }
            }
            
            return this;
        }
    }
    
    // --- Reminder Settings ComboBox Renderer extending ModernComboBoxRenderer ---
    private static class ReminderComboBoxRenderer extends ModernComboBoxRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            
            if (value instanceof Task.ReminderSettings reminder) {
                setText("🔔 " + reminder.getDisplayName());
                
                // Use different colors based on reminder setting
                if (!isSelected) {
                    Color reminderColor = reminder == Task.ReminderSettings.NONE ?
                        Color.GRAY : new Color(0, 120, 215);
                    setForeground(reminderColor);
                }
            }
            
            return this;
        }
    }
}
