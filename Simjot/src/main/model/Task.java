package main.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Modern Task data model with completion status, priorities, due dates, and reminders.
 * Follows the established patterns in Simjot for data persistence and UI integration.
 */
public class Task {
    
    public enum Priority {
        LOW("Low", "#28a745"),     // Green
        MEDIUM("Medium", "#ffc107"), // Yellow
        HIGH("High", "#dc3545");   // Red
        
        private final String displayName;
        private final String colorHex;
        
        Priority(String displayName, String colorHex) {
            this.displayName = displayName;
            this.colorHex = colorHex;
        }
        
        public String getDisplayName() { return displayName; }
        public String getColorHex() { return colorHex; }
        
        public java.awt.Color getColor() {
            return java.awt.Color.decode(colorHex);
        }
    }
    
    public enum ReminderSettings {
        NONE("No reminders"),
        ON_DUE_DATE("On due date"),
        ONE_DAY_BEFORE("1 day before"),
        TWO_DAYS_BEFORE("2 days before"),
        ONE_WEEK_BEFORE("1 week before");
        
        private final String displayName;
        
        ReminderSettings(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() { return displayName; }
        
        public long getDaysBeforeReminder() {
            return switch (this) {
                case ON_DUE_DATE -> 0;
                case ONE_DAY_BEFORE -> 1;
                case TWO_DAYS_BEFORE -> 2;
                case ONE_WEEK_BEFORE -> 7;
                default -> -1; // No reminder
            };
        }
    }
    
    public enum UrgencyLevel {
        OVERDUE("Overdue", "#dc3545", 1),      // Red - past due date
        DUE_TODAY("Due Today", "#fd7e14", 2),  // Orange - due today
        DUE_THIS_WEEK("Due This Week", "#ffc107", 3), // Yellow - due within 7 days
        FUTURE("Future", "#28a745", 4),        // Green - due in future
        NO_DUE_DATE("No Due Date", "#6c757d", 5); // Gray - no due date set
        
        private final String displayName;
        private final String colorHex;
        private final int sortOrder; // Lower number = higher urgency
        
        UrgencyLevel(String displayName, String colorHex, int sortOrder) {
            this.displayName = displayName;
            this.colorHex = colorHex;
            this.sortOrder = sortOrder;
        }
        
        public String getDisplayName() { return displayName; }
        public String getColorHex() { return colorHex; }
        public int getSortOrder() { return sortOrder; }
        
        public java.awt.Color getColor() {
            return java.awt.Color.decode(colorHex);
        }
    }
    
    private String id;
    private String text;
    private boolean completed;
    private Priority priority;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String category; // Optional categorization
    
    // New due date and reminder fields
    private LocalDate dueDate; // Optional due date
    private ReminderSettings reminderSettings;
    private UrgencyLevel urgencyLevel; // Calculated field, not persisted
    
    // Constructor for new tasks
    public Task(String text) {
        this(java.util.UUID.randomUUID().toString(), text, false, Priority.MEDIUM,
             LocalDateTime.now(), null, "General", null, ReminderSettings.NONE);
    }
    
    // Backward-compatible constructor for existing persistence
    public Task(String id, String text, boolean completed, Priority priority,
                LocalDateTime createdAt, LocalDateTime completedAt, String category) {
        this(id, text, completed, priority, createdAt, completedAt, category, null, ReminderSettings.NONE);
    }
    
    // Full constructor for persistence
    public Task(String id, String text, boolean completed, Priority priority,
                LocalDateTime createdAt, LocalDateTime completedAt, String category,
                LocalDate dueDate, ReminderSettings reminderSettings) {
        this.id = id;
        this.text = text;
        this.completed = completed;
        this.priority = priority;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
        this.category = category;
        this.dueDate = dueDate;
        this.reminderSettings = reminderSettings != null ? reminderSettings : ReminderSettings.NONE;
        this.urgencyLevel = calculateUrgency();
    }
    
    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { 
        this.completed = completed;
        if (completed && completedAt == null) {
            this.completedAt = LocalDateTime.now();
        } else if (!completed) {
            this.completedAt = null;
        }
    }
    
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    
    // New due date and reminder getters/setters
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
        this.urgencyLevel = calculateUrgency(); // Recalculate urgency when due date changes
    }
    
    public ReminderSettings getReminderSettings() { return reminderSettings; }
    public void setReminderSettings(ReminderSettings reminderSettings) {
        this.reminderSettings = reminderSettings != null ? reminderSettings : ReminderSettings.NONE;
    }
    
    public UrgencyLevel getUrgencyLevel() { return urgencyLevel; }
    
    // Calculate urgency based on due date
    public UrgencyLevel calculateUrgency() {
        if (dueDate == null) {
            return UrgencyLevel.NO_DUE_DATE;
        }
        
        LocalDate today = LocalDate.now();
        long daysUntilDue = ChronoUnit.DAYS.between(today, dueDate);
        
        if (daysUntilDue < 0) {
            return UrgencyLevel.OVERDUE;
        } else if (daysUntilDue == 0) {
            return UrgencyLevel.DUE_TODAY;
        } else if (daysUntilDue <= 7) {
            return UrgencyLevel.DUE_THIS_WEEK;
        } else {
            return UrgencyLevel.FUTURE;
        }
    }
    
    // Utility methods for UI display
    public String getFormattedCreatedDate() {
        return createdAt.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"));
    }
    
    public String getFormattedCreatedTime() {
        return createdAt.format(DateTimeFormatter.ofPattern("HH:mm"));
    }
    
    public String getFormattedCompletedDate() {
        if (completedAt == null) return "";
        return completedAt.format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm"));
    }
    
    // New due date utility methods
    public String getFormattedDueDate() {
        if (dueDate == null) return "";
        return dueDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"));
    }
    
    public String getDueDateCountdown() {
        if (dueDate == null) return "";
        
        LocalDate today = LocalDate.now();
        long daysUntilDue = ChronoUnit.DAYS.between(today, dueDate);
        
        if (daysUntilDue < 0) {
            long daysOverdue = Math.abs(daysUntilDue);
            return "Overdue by " + daysOverdue + (daysOverdue == 1 ? " day" : " days");
        } else if (daysUntilDue == 0) {
            return "Due today";
        } else if (daysUntilDue == 1) {
            return "Due tomorrow";
        } else if (daysUntilDue <= 7) {
            return "Due in " + daysUntilDue + " days";
        } else {
            return "Due in " + daysUntilDue + " days";
        }
    }
    
    public boolean hasDueDate() {
        return dueDate != null;
    }
    
    public boolean isOverdue() {
        return hasDueDate() && !completed && dueDate.isBefore(LocalDate.now());
    }
    
    public boolean isDueToday() {
        return hasDueDate() && !completed && dueDate.equals(LocalDate.now());
    }
    
    public boolean isDueThisWeek() {
        if (!hasDueDate() || completed) return false;
        LocalDate today = LocalDate.now();
        return dueDate.isAfter(today) && ChronoUnit.DAYS.between(today, dueDate) <= 7;
    }
    
    public boolean needsReminder() {
        if (!hasDueDate() || completed || reminderSettings == ReminderSettings.NONE) {
            return false;
        }
        
        LocalDate today = LocalDate.now();
        LocalDate reminderDate = dueDate.minusDays(reminderSettings.getDaysBeforeReminder());
        
        return today.equals(reminderDate) || (today.isAfter(reminderDate) && !isOverdue());
    }
    
    public boolean isRecentlyCompleted() {
        // Completed within the last 24 hours
        return completed && completedAt != null && 
               completedAt.isAfter(LocalDateTime.now().minusDays(1));
    }
    
    // For JSON serialization compatibility
    public String toJsonString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(id).append("\",");
        sb.append("\"text\":\"").append(text.replace("\"", "\\\"")).append("\",");
        sb.append("\"completed\":").append(completed).append(",");
        sb.append("\"priority\":\"").append(priority.name()).append("\",");
        sb.append("\"createdAt\":\"").append(createdAt.toString()).append("\",");
        sb.append("\"completedAt\":").append(completedAt != null ? "\"" + completedAt.toString() + "\"" : "null").append(",");
        sb.append("\"category\":\"").append(category).append("\",");
        sb.append("\"dueDate\":").append(dueDate != null ? "\"" + dueDate.toString() + "\"" : "null").append(",");
        sb.append("\"reminderSettings\":\"").append(reminderSettings.name()).append("\"");
        sb.append("}");
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return text + (completed ? " ✓" : "") + " [" + priority.getDisplayName() + "]";
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Task task = (Task) obj;
        return id.equals(task.id);
    }
    
    @Override
    public int hashCode() {
        return id.hashCode();
    }
}