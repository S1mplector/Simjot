package main.util;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import main.model.Task;

/**
 * TaskStore handles persistence and management of tasks, following the SettingsStore pattern.
 * Uses JSON-like format for human-readable storage with automatic backup functionality.
 */
public final class TaskStore {
    
    private static final String TASKS_FILE = "tasks.json";
    private static final String BACKUP_SUFFIX = ".backup";
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    
    private final List<Task> tasks = new ArrayList<>();
    private final File tasksFile;
    private final File backupFile;
    
    // Singleton handling
    private static TaskStore instance;
    
    public static synchronized TaskStore get() {
        if (instance == null) {
            instance = new TaskStore();
        }
        return instance;
    }
    
    private TaskStore() {
        File tasksDir = AppDirectories.folder(AppDirectories.Type.TASKS);
        tasksFile = new File(tasksDir, TASKS_FILE);
        backupFile = new File(tasksDir, TASKS_FILE + BACKUP_SUFFIX);
        load();
    }
    
    /**
     * Load tasks from the JSON file
     */
    private void load() {
        if (!tasksFile.exists()) {
            return; // No tasks yet, start with empty list
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(tasksFile))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            
            parseJsonContent(content.toString().trim());
            
        } catch (IOException ex) {
            ex.printStackTrace();
            // Try to load from backup
            tryLoadFromBackup();
        }
    }
    
    /**
     * Parse JSON content and populate tasks list
     */
    private void parseJsonContent(String jsonContent) {
        if (jsonContent.isEmpty() || !jsonContent.startsWith("[")) {
            return;
        }
        
        // Simple JSON parsing for our task format
        jsonContent = jsonContent.substring(1, jsonContent.length() - 1); // Remove [ ]
        if (jsonContent.trim().isEmpty()) {
            return;
        }
        
        String[] taskObjects = jsonContent.split("},\\s*\\{");
        
        for (String taskJson : taskObjects) {
            taskJson = taskJson.trim();
            if (!taskJson.startsWith("{")) taskJson = "{" + taskJson;
            if (!taskJson.endsWith("}")) taskJson = taskJson + "}";
            
            try {
                Task task = parseTaskFromJson(taskJson);
                if (task != null) {
                    tasks.add(task);
                }
            } catch (Exception ex) {
                System.err.println("Error parsing task: " + taskJson);
                ex.printStackTrace();
            }
        }
    }
    
    /**
     * Parse a single task from JSON string
     */
    private Task parseTaskFromJson(String json) {
        try {
            // Simple JSON parsing - extract values between quotes
            String id = extractJsonValue(json, "id");
            String text = extractJsonValue(json, "text");
            boolean completed = Boolean.parseBoolean(extractJsonValue(json, "completed"));
            Task.Priority priority = Task.Priority.valueOf(extractJsonValue(json, "priority"));
            LocalDateTime createdAt = LocalDateTime.parse(extractJsonValue(json, "createdAt"), ISO_FORMATTER);
            
            String completedAtStr = extractJsonValue(json, "completedAt");
            LocalDateTime completedAt = "null".equals(completedAtStr) ? null : 
                LocalDateTime.parse(completedAtStr, ISO_FORMATTER);
            
            String category = extractJsonValue(json, "category");
            
            return new Task(id, text, completed, priority, createdAt, completedAt, category);
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }
    
    /**
     * Extract value from JSON string for a given key
     */
    private String extractJsonValue(String json, String key) {
        String searchPattern = "\"" + key + "\":";
        int startIndex = json.indexOf(searchPattern);
        if (startIndex == -1) return "";
        
        startIndex += searchPattern.length();
        
        // Skip whitespace
        while (startIndex < json.length() && Character.isWhitespace(json.charAt(startIndex))) {
            startIndex++;
        }
        
        if (startIndex >= json.length()) return "";
        
        char firstChar = json.charAt(startIndex);
        if (firstChar == '"') {
            // String value
            startIndex++;
            int endIndex = json.indexOf('"', startIndex);
            return endIndex != -1 ? json.substring(startIndex, endIndex) : "";
        } else if (firstChar == 'n' && json.substring(startIndex).startsWith("null")) {
            return "null";
        } else {
            // Boolean or other value
            int endIndex = json.indexOf(',', startIndex);
            if (endIndex == -1) endIndex = json.indexOf('}', startIndex);
            return endIndex != -1 ? json.substring(startIndex, endIndex).trim() : "";
        }
    }
    
    /**
     * Try to load from backup file if main file fails
     */
    private void tryLoadFromBackup() {
        if (backupFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(backupFile))) {
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                parseJsonContent(content.toString().trim());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
    
    /**
     * Save tasks to JSON file with backup
     */
    public void save() {
        try {
            // Create backup of existing file
            if (tasksFile.exists()) {
                copyFile(tasksFile, backupFile);
            }
            
            // Write new content
            try (PrintWriter writer = new PrintWriter(new FileWriter(tasksFile))) {
                writer.println("[");
                for (int i = 0; i < tasks.size(); i++) {
                    writer.print("  " + tasks.get(i).toJsonString());
                    if (i < tasks.size() - 1) {
                        writer.println(",");
                    } else {
                        writer.println();
                    }
                }
                writer.println("]");
            }
            
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    
    /**
     * Copy file for backup purposes
     */
    private void copyFile(File source, File dest) throws IOException {
        try (FileInputStream fis = new FileInputStream(source);
             FileOutputStream fos = new FileOutputStream(dest)) {
            
            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
        }
    }
    
    // --- Public API methods ---
    
    public List<Task> getAllTasks() {
        return new ArrayList<>(tasks);
    }
    
    public List<Task> getActiveTasks() {
        return tasks.stream()
                   .filter(task -> !task.isCompleted())
                   .collect(Collectors.toList());
    }
    
    public List<Task> getCompletedTasks() {
        return tasks.stream()
                   .filter(Task::isCompleted)
                   .collect(Collectors.toList());
    }
    
    public void addTask(Task task) {
        tasks.add(task);
        save();
    }
    
    public void updateTask(Task task) {
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).getId().equals(task.getId())) {
                tasks.set(i, task);
                save();
                return;
            }
        }
    }
    
    public void removeTask(String taskId) {
        tasks.removeIf(task -> task.getId().equals(taskId));
        save();
    }
    
    public void removeTask(Task task) {
        removeTask(task.getId());
    }
    
    public void clearCompletedTasks() {
        tasks.removeIf(Task::isCompleted);
        save();
    }
    
    public int getTaskCount() {
        return tasks.size();
    }
    
    public int getCompletedCount() {
        return (int) tasks.stream().filter(Task::isCompleted).count();
    }
    
    public int getActiveCount() {
        return (int) tasks.stream().filter(task -> !task.isCompleted()).count();
    }
    
    public double getCompletionPercentage() {
        if (tasks.isEmpty()) return 0.0;
        return (double) getCompletedCount() / tasks.size() * 100.0;
    }
}