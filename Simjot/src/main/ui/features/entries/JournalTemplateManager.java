package main.ui.features.entries;

import java.io.*;
import java.util.*;
import main.infrastructure.io.AppDirectories;

/**
 * Manages custom and built-in journal entry templates.
 * Persists custom templates to disk.
 */
public class JournalTemplateManager {
    private static JournalTemplateManager instance;
    private final File templatesFile;
    private final List<JournalTemplate> templates;

    public static class JournalTemplate {
        private String id;
        private String name;
        private String description;
        private String[] questions;
        private boolean custom; // true if user-created

        public JournalTemplate(String id, String name, String description, String[] questions, boolean custom) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.questions = questions;
            this.custom = custom;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String[] getQuestions() { return questions; }
        public boolean isCustom() { return custom; }

        public void setName(String name) { this.name = name; }
        public void setDescription(String description) { this.description = description; }
        public void setQuestions(String[] questions) { this.questions = questions; }
    }

    private JournalTemplateManager() {
        templatesFile = new File(AppDirectories.folder(AppDirectories.Type.SETTINGS), "journal_templates.txt");
        templates = new ArrayList<>();
        loadTemplates();
    }

    public static synchronized JournalTemplateManager getInstance() {
        if (instance == null) {
            instance = new JournalTemplateManager();
        }
        return instance;
    }

    private void loadTemplates() {
        templates.clear();
        
        // Add built-in templates
        templates.add(new JournalTemplate("BLANK", "Blank Entry", "Start with a fresh page", new String[0], false));
        templates.add(new JournalTemplate("GRATITUDE", "Gratitude Journal", "What are you grateful for today?", 
            new String[]{
                "What are you grateful for today? (Item 1)",
                "What else are you grateful for? (Item 2)",
                "One more thing you're grateful for (Item 3)"
            }, false));
        templates.add(new JournalTemplate("ANXIETY", "Anxiety Processing", "Work through anxious thoughts",
            new String[]{
                "What's making you anxious?",
                "What's within your control?",
                "What can you let go of?"
            }, false));
        templates.add(new JournalTemplate("DAILY_LOG", "Daily Log", "Recap your day",
            new String[]{
                "How was your morning?",
                "How was your afternoon?",
                "How was your evening?",
                "What were the highlights of today?"
            }, false));
        templates.add(new JournalTemplate("MOOD_TRACKER", "Mood & Energy", "Track how you're feeling",
            new String[]{
                "What's your current mood and energy level?",
                "What influenced your mood today?",
                "What helped you feel better?"
            }, false));
        templates.add(new JournalTemplate("GOAL_PLANNING", "Goal Planning", "Set intentions and plan",
            new String[]{
                "What are your top 3 priorities for today?",
                "What are your goals for this week?",
                "What are your goals for this month?"
            }, false));
        templates.add(new JournalTemplate("REFLECTION", "Evening Reflection", "Reflect on your day",
            new String[]{
                "What went well today?",
                "What could have gone better?",
                "What did you learn today?",
                "What will you do tomorrow?"
            }, false));

        // Load custom templates from file
        if (templatesFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(templatesFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("[TEMPLATE:")) {
                        JournalTemplate template = parseTemplate(line, reader);
                        if (template != null) {
                            templates.add(template);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private JournalTemplate parseTemplate(String headerLine, BufferedReader reader) throws IOException {
        // Parse: [TEMPLATE:id|name|description]
        int endIdx = headerLine.indexOf(']');
        if (endIdx < 0) return null;
        
        String content = headerLine.substring(10, endIdx);
        String[] parts = content.split("\\|", 3);
        if (parts.length < 3) return null;

        String id = parts[0];
        String name = parts[1];
        String description = parts[2];

        List<String> questions = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("[Q:")) {
                int qEndIdx = line.indexOf(']');
                if (qEndIdx > 0) {
                    String question = line.substring(3, qEndIdx);
                    questions.add(question);
                }
            } else if (line.trim().isEmpty() || line.startsWith("[")) {
                break;
            }
        }

        return new JournalTemplate(id, name, description, questions.toArray(new String[0]), true);
    }

    public void saveTemplates() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(templatesFile))) {
            for (JournalTemplate template : templates) {
                if (template.isCustom()) {
                    writer.println("[TEMPLATE:" + template.getId() + "|" + template.getName() + "|" + template.getDescription() + "]");
                    for (String question : template.getQuestions()) {
                        writer.println("[Q:" + question + "]");
                    }
                    writer.println();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<JournalTemplate> getTemplates() {
        return new ArrayList<>(templates);
    }

    public void addTemplate(JournalTemplate template) {
        templates.add(template);
        saveTemplates();
    }

    public void removeTemplate(String id) {
        templates.removeIf(t -> t.isCustom() && t.getId().equals(id));
        saveTemplates();
    }

    public void updateTemplate(JournalTemplate template) {
        saveTemplates();
    }

    public JournalTemplate getTemplateById(String id) {
        return templates.stream()
                .filter(t -> t.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
}
