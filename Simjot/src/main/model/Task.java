package main.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Modern Task data model with completion status, priorities, and timestamps.
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
    
    private String id;
    private String text;
    private boolean completed;
    private Priority priority;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String category; // Optional categorization
    
    // Constructor for new tasks
    public Task(String text) {
        this(java.util.UUID.randomUUID().toString(), text, false, Priority.MEDIUM, 
             LocalDateTime.now(), null, "General");
    }
    
    // Full constructor for persistence
    public Task(String id, String text, boolean completed, Priority priority, 
                LocalDateTime createdAt, LocalDateTime completedAt, String category) {
        this.id = id;
        this.text = text;
        this.completed = completed;
        this.priority = priority;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
        this.category = category;
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
    
    public boolean isOverdue() {
        // Simple logic - tasks older than 7 days and not completed
        return !completed && createdAt.isBefore(LocalDateTime.now().minusDays(7));
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
        sb.append("\"category\":\"").append(category).append("\"");
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