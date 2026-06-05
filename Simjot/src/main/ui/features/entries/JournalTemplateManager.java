/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.features.entries;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import main.infrastructure.backup.NotebookInfo;
import main.infrastructure.io.AppDirectories;

/**
 * Manages custom and built-in journal entry templates.
 * Supports per-notebook templates and hiding built-ins per notebook or globally.
 */
public class JournalTemplateManager {
    private static JournalTemplateManager instance;

    // Global persistence locations
    private final File globalTemplatesFile;
    private final File hiddenBuiltinsGlobalFile;

    // In-memory cache for built-ins and global custom templates
    private final List<JournalTemplate> builtIns = new ArrayList<>();
    private final List<JournalTemplate> globalCustoms = new ArrayList<>();

    public enum Scope { BUILTIN, GLOBAL_CUSTOM, NOTEBOOK_CUSTOM }

    public static class JournalTemplate {
        private String id;
        private String name;
        private String description;
        private String[] questions;
        private boolean custom; // kept for backward compatibility with UI logic
        private Scope scope;     // where this template is defined
        private String notebookName; // only set when scope == NOTEBOOK_CUSTOM

        public JournalTemplate(String id, String name, String description, String[] questions, boolean custom) {
            this(id, name, description, questions, custom, Scope.BUILTIN, null);
        }

        public JournalTemplate(String id, String name, String description, String[] questions, boolean custom, Scope scope, String notebookName) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.questions = questions;
            this.custom = custom;
            this.scope = scope;
            this.notebookName = notebookName;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String[] getQuestions() { return questions; }
        public boolean isCustom() { return custom; }
        public Scope getScope() { return scope; }
        public String getNotebookName() { return notebookName; }

        public void setName(String name) { this.name = name; }
        public void setDescription(String description) { this.description = description; }
        public void setQuestions(String[] questions) { this.questions = questions; }
        public void setScope(Scope scope) { this.scope = scope; }
        public void setNotebookName(String notebookName) { this.notebookName = notebookName; }
    }

    private JournalTemplateManager() {
        globalTemplatesFile = new File(AppDirectories.folder(AppDirectories.Type.SETTINGS), "journal_templates.txt");
        hiddenBuiltinsGlobalFile = new File(AppDirectories.folder(AppDirectories.Type.SETTINGS), "hidden_builtins.txt");
        loadBuiltIns();
        loadGlobalCustoms();
    }

    public static synchronized JournalTemplateManager getInstance() {
        if (instance == null) {
            instance = new JournalTemplateManager();
        }
        return instance;
    }

    private void loadBuiltIns() {
        builtIns.clear();
        builtIns.add(new JournalTemplate("BLANK", "Blank Entry", "Start with a fresh page", new String[0], false, Scope.BUILTIN, null));
        builtIns.add(new JournalTemplate("GRATITUDE", "Gratitude Journal", "What are you grateful for today?",
            new String[]{
                "What are you grateful for today? (Item 1)",
                "What else are you grateful for? (Item 2)",
                "One more thing you're grateful for (Item 3)"
            }, false, Scope.BUILTIN, null));
        builtIns.add(new JournalTemplate("ANXIETY", "Anxiety Processing", "Work through anxious thoughts",
            new String[]{
                "What's making you anxious?",
                "What's within your control?",
                "What can you let go of?"
            }, false, Scope.BUILTIN, null));
        builtIns.add(new JournalTemplate("DAILY_LOG", "Daily Log", "Recap your day",
            new String[]{
                "How was your morning?",
                "How was your afternoon?",
                "How was your evening?",
                "What were the highlights of today?"
            }, false, Scope.BUILTIN, null));
        builtIns.add(new JournalTemplate("MOOD_TRACKER", "Mood & Energy", "Track how you're feeling",
            new String[]{
                "What's your current mood and energy level?",
                "What influenced your mood today?",
                "What helped you feel better?"
            }, false, Scope.BUILTIN, null));
        builtIns.add(new JournalTemplate("GOAL_PLANNING", "Goal Planning", "Set intentions and plan",
            new String[]{
                "What are your top 3 priorities for today?",
                "What are your goals for this week?",
                "What are your goals for this month?"
            }, false, Scope.BUILTIN, null));
        builtIns.add(new JournalTemplate("REFLECTION", "Evening Reflection", "Reflect on your day",
            new String[]{
                "What went well today?",
                "What could have gone better?",
                "What did you learn today?",
                "What will you do tomorrow?"
            }, false, Scope.BUILTIN, null));
    }

    private void loadGlobalCustoms() {
        globalCustoms.clear();
        if (!globalTemplatesFile.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(globalTemplatesFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("[TEMPLATE:")) {
                    JournalTemplate template = parseTemplate(line, reader, Scope.GLOBAL_CUSTOM, null);
                    if (template != null) globalCustoms.add(template);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private JournalTemplate parseTemplate(String headerLine, BufferedReader reader, Scope scope, String notebookName) throws IOException {
        // Parse: [TEMPLATE:id|name|description]
        int endIdx = findClosingBracket(headerLine, 10);
        if (endIdx < 0) return null;

        String content = headerLine.substring(10, endIdx);
        List<String> parts = splitEscaped(content, '|', 3);
        if (parts.size() < 3) return null;

        String id = unescapeField(parts.get(0));
        String name = unescapeField(parts.get(1));
        String description = unescapeField(parts.get(2));

        List<String> questions = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("[Q:")) {
                int qEndIdx = findClosingBracket(line, 3);
                if (qEndIdx > 0) {
                    String question = unescapeField(line.substring(3, qEndIdx));
                    questions.add(question);
                }
            } else if (line.trim().isEmpty() || line.startsWith("[")) {
                break;
            }
        }

        return new JournalTemplate(id, name, description, questions.toArray(new String[0]), true, scope, notebookName);
    }

    private void writeTemplates(File file, List<JournalTemplate> list) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            for (JournalTemplate template : list) {
                if (template.isCustom()) {
                    writer.println("[TEMPLATE:"
                        + escapeField(template.getId())
                        + "|"
                        + escapeField(template.getName())
                        + "|"
                        + escapeField(template.getDescription())
                        + "]");
                    String[] questions = template.getQuestions();
                    if (questions == null) {
                        questions = new String[0];
                    }
                    for (String question : questions) {
                        writer.println("[Q:" + escapeField(question) + "]");
                    }
                    writer.println();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int findClosingBracket(String value, int startIndex) {
        boolean escaped = false;
        for (int i = startIndex; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == ']') {
                return i;
            }
        }
        return -1;
    }

    private List<String> splitEscaped(String value, char delimiter, int limit) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }
            if (c == '\\') {
                current.append(c);
                escaped = true;
                continue;
            }
            if (c == delimiter && (limit <= 0 || parts.size() < limit - 1)) {
                parts.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        parts.add(current.toString());
        return parts;
    }

    private String escapeField(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '|' -> out.append("\\|");
                case ']' -> out.append("\\]");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private String unescapeField(String value) {
        if (value == null || value.isEmpty()) return "";
        StringBuilder out = new StringBuilder(value.length());
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!escaped) {
                if (c == '\\') {
                    escaped = true;
                } else {
                    out.append(c);
                }
                continue;
            }

            switch (c) {
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case '\\' -> out.append('\\');
                case '|' -> out.append('|');
                case ']' -> out.append(']');
                default -> {
                    out.append('\\');
                    out.append(c);
                }
            }
            escaped = false;
        }
        if (escaped) {
            out.append('\\');
        }
        return out.toString();
    }

    private File notebookTemplatesFile(NotebookInfo nb) {
        // Store notebook-specific templates under a hidden meta directory to keep them out of the entry list
        File metaDir = new File(nb.getFolder(), ".simjot");
        if (!metaDir.exists()) metaDir.mkdirs();
        return new File(metaDir, "templates.txt");
    }

    private File notebookHiddenFile(NotebookInfo nb) {
        // Store per-notebook hidden built-ins alongside templates in the hidden meta directory
        File metaDir = new File(nb.getFolder(), ".simjot");
        if (!metaDir.exists()) metaDir.mkdirs();
        return new File(metaDir, "hidden_builtins.txt");
    }

    // Legacy locations (pre-migration)
    private File legacyNotebookTemplatesFile(NotebookInfo nb) {
        return new File(nb.getFolder(), ".journal_templates.txt");
    }

    private File legacyNotebookHiddenFile(NotebookInfo nb) {
        return new File(nb.getFolder(), ".hidden_builtins.txt");
    }

    private Set<String> loadHiddenBuiltinsGlobal() {
        Set<String> set = new HashSet<>();
        if (!hiddenBuiltinsGlobalFile.exists()) return set;
        try (BufferedReader br = new BufferedReader(new FileReader(hiddenBuiltinsGlobalFile))) {
            String line; while ((line = br.readLine()) != null) { String id = line.trim(); if (!id.isEmpty()) set.add(id); }
        } catch (IOException ignored) {}
        return set;
    }

    private Set<String> loadHiddenBuiltinsForNotebook(NotebookInfo nb) {
        Set<String> set = new HashSet<>();
        File f = notebookHiddenFile(nb);
        File legacy = legacyNotebookHiddenFile(nb);
        if (!f.exists() && legacy.exists()) {
            // migrate
            try (BufferedReader br = new BufferedReader(new FileReader(legacy))) {
                String line; while ((line = br.readLine()) != null) { String id = line.trim(); if (!id.isEmpty()) set.add(id); }
            } catch (IOException ignored) {}
            // persist to new location and cleanup legacy
            saveHiddenBuiltinsForNotebook(nb, set);
            try { legacy.delete(); } catch (Throwable ignored) {}
            return set;
        }
        if (!f.exists()) return set;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line; while ((line = br.readLine()) != null) { String id = line.trim(); if (!id.isEmpty()) set.add(id); }
        } catch (IOException ignored) {}
        return set;
    }

    private void saveHiddenBuiltinsGlobal(Set<String> ids) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(hiddenBuiltinsGlobalFile))) {
            for (String id : ids) pw.println(id);
        } catch (IOException ignored) {}
    }

    private void saveHiddenBuiltinsForNotebook(NotebookInfo nb, Set<String> ids) {
        File f = notebookHiddenFile(nb);
        try (PrintWriter pw = new PrintWriter(new FileWriter(f))) {
            for (String id : ids) pw.println(id);
        } catch (IOException ignored) {}
    }

    // -------- Public API --------

    /**
     * Returns templates merged as: (Built-ins - hidden) + Global customs + Notebook customs (if nb != null)
     */
    public List<JournalTemplate> getTemplates() {
        // legacy global view (no per-notebook filtering of built-ins)
        List<JournalTemplate> list = new ArrayList<>();
        // Apply global hidden set to built-ins
        Set<String> hiddenGlobal = loadHiddenBuiltinsGlobal();
        for (JournalTemplate t : builtIns) if (!hiddenGlobal.contains(t.getId())) list.add(t);
        list.addAll(globalCustoms);
        return list;
    }

    public List<JournalTemplate> getTemplates(NotebookInfo nb) {
        List<JournalTemplate> list = new ArrayList<>();
        // Hidden = union of global-hidden and notebook-hidden
        Set<String> hidden = loadHiddenBuiltinsGlobal();
        hidden.addAll(loadHiddenBuiltinsForNotebook(nb));
        for (JournalTemplate t : builtIns) if (!hidden.contains(t.getId())) list.add(t);
        list.addAll(globalCustoms);
        list.addAll(loadNotebookCustoms(nb));
        return list;
    }

    private List<JournalTemplate> loadNotebookCustoms(NotebookInfo nb) {
        List<JournalTemplate> res = new ArrayList<>();
        File f = notebookTemplatesFile(nb);
        File legacy = legacyNotebookTemplatesFile(nb);
        // If legacy exists but new doesn't, migrate
        if (!f.exists() && legacy.exists()) {
            try {
                // Read from legacy
                try (BufferedReader reader = new BufferedReader(new FileReader(legacy))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("[TEMPLATE:")) {
                            JournalTemplate template = parseTemplate(line, reader, Scope.NOTEBOOK_CUSTOM, nb.getName());
                            if (template != null) res.add(template);
                        }
                    }
                }
                // Write into new location and delete legacy
                writeTemplates(f, res);
                // best-effort cleanup
                try { legacy.delete(); } catch (Throwable ignored) {}
            } catch (IOException e) { e.printStackTrace(); }
            return res;
        }
        if (!f.exists()) return res;
        try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("[TEMPLATE:")) {
                    JournalTemplate template = parseTemplate(line, reader, Scope.NOTEBOOK_CUSTOM, nb.getName());
                    if (template != null) res.add(template);
                }
            }
        } catch (IOException e) { e.printStackTrace(); }
        return res;
    }

    public void addTemplate(JournalTemplate template) {
        // Add to appropriate scope
        if (template.getScope() == Scope.NOTEBOOK_CUSTOM && template.getNotebookName() != null) {
            // Write into that notebook file (append to current list)
            // Find notebook folder by name under root notebooks
            // Simpler: caller should save via addTemplateForNotebook
            throw new IllegalStateException("Use addTemplateForNotebook for NOTEBOOK_CUSTOM scope");
        }
        globalCustoms.add(template);
        writeTemplates(globalTemplatesFile, globalCustoms);
    }

    public void addTemplateForNotebook(NotebookInfo nb, JournalTemplate template) {
        List<JournalTemplate> notebookList = loadNotebookCustoms(nb);
        template.setScope(Scope.NOTEBOOK_CUSTOM);
        template.setNotebookName(nb.getName());
        notebookList.add(template);
        writeTemplates(notebookTemplatesFile(nb), notebookList);
    }

    public void removeTemplate(String id) {
        globalCustoms.removeIf(t -> t.isCustom() && t.getScope()==Scope.GLOBAL_CUSTOM && t.getId().equals(id));
        writeTemplates(globalTemplatesFile, globalCustoms);
    }

    public void removeTemplate(NotebookInfo nb, String id) {
        List<JournalTemplate> notebookList = loadNotebookCustoms(nb);
        notebookList.removeIf(t -> t.isCustom() && t.getScope()==Scope.NOTEBOOK_CUSTOM && t.getId().equals(id));
        writeTemplates(notebookTemplatesFile(nb), notebookList);
    }

    public void updateTemplate(JournalTemplate template) {
        if (template.getScope() == Scope.NOTEBOOK_CUSTOM && template.getNotebookName() != null) {
            // Update in its notebook file
            // Find the notebook by name by scanning NotebookStore is out of scope here; we only have name.
            // Assume unique file path based on name under AppDirectories.NOTEBOOKS
            File nbFolder = new File(AppDirectories.folder(AppDirectories.Type.NOTEBOOKS), template.getNotebookName());
            NotebookInfo nb = new NotebookInfo(template.getNotebookName(), NotebookInfo.Type.JOURNAL, nbFolder, 0L, "legacy");
            List<JournalTemplate> list = loadNotebookCustoms(nb);
            for (int i=0;i<list.size();i++) {
                if (list.get(i).getId().equals(template.getId())) { list.set(i, template); break; }
            }
            writeTemplates(notebookTemplatesFile(nb), list);
        } else {
            // Update global
            for (int i=0;i<globalCustoms.size();i++) {
                if (globalCustoms.get(i).getId().equals(template.getId())) { globalCustoms.set(i, template); break; }
            }
            writeTemplates(globalTemplatesFile, globalCustoms);
        }
    }

    public JournalTemplate getTemplateById(String id) {
        for (JournalTemplate t : builtIns) if (t.getId().equals(id)) return t;
        for (JournalTemplate t : globalCustoms) if (t.getId().equals(id)) return t;
        return null;
    }

    public JournalTemplate getTemplateById(NotebookInfo nb, String id) {
        for (JournalTemplate t : builtIns) if (t.getId().equals(id)) return t;
        for (JournalTemplate t : globalCustoms) if (t.getId().equals(id)) return t;
        for (JournalTemplate t : loadNotebookCustoms(nb)) if (t.getId().equals(id)) return t;
        return null;
    }

    // --- Built-in visibility ---
    public void hideBuiltInGlobally(String id) {
        Set<String> hidden = loadHiddenBuiltinsGlobal();
        hidden.add(id);
        saveHiddenBuiltinsGlobal(hidden);
    }

    public void unhideBuiltInGlobally(String id) {
        Set<String> hidden = loadHiddenBuiltinsGlobal();
        hidden.remove(id);
        saveHiddenBuiltinsGlobal(hidden);
    }

    public void hideBuiltInForNotebook(NotebookInfo nb, String id) {
        Set<String> hidden = loadHiddenBuiltinsForNotebook(nb);
        hidden.add(id);
        saveHiddenBuiltinsForNotebook(nb, hidden);
    }

    public void unhideBuiltInForNotebook(NotebookInfo nb, String id) {
        Set<String> hidden = loadHiddenBuiltinsForNotebook(nb);
        hidden.remove(id);
        saveHiddenBuiltinsForNotebook(nb, hidden);
    }
}
