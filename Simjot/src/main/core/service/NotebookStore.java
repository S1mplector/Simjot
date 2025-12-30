/*
 * SIMJOT - PROPRIETARY
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 * 
 * This source code is licensed under the Simjot Source-Available Personal Use License.
 * You may view and study this code for personal, non-commercial use.
 * Distribution, commercial use, and derivative works are strictly prohibited.
 * 
 * See LICENSE.md for full terms.
 */

package main.core.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import main.infrastructure.backup.NotebookInfo;
import main.infrastructure.io.AppDirectories;
import main.infrastructure.io.FileIO;
import main.infrastructure.io.IoLog;

/**
 * Persists list of notebooks under Simjot/notebooks.json and provides helpers.
 * It is used to load and save notebooks from/to the file system.
 * @author S1mplector
 */
public final class NotebookStore {
    private static final String FILE_NAME = "notebooks.json";

    private final File jsonFile;
    private final List<NotebookInfo> notebooks = new ArrayList<>();

    public NotebookStore() {
        jsonFile = new File(AppDirectories.getRoot(), FILE_NAME);
        load();
    }

    private void load() {
        notebooks.clear();
        Set<String> seenNames = new HashSet<>();
        if(!jsonFile.exists()) return;
        try {
            String raw = Files.readString(jsonFile.toPath(), StandardCharsets.UTF_8);
            if (raw.trim().startsWith("[")) {
                parseJson(raw, seenNames);
                return;
            }
            // Legacy fallback (line-based :: format)
            legacyLoad(raw, seenNames);
        } catch (IOException ex) {
            logWarn("Failed to read notebook store", ex);
        }
    }

    private void legacyLoad(String raw, Set<String> seenNames) {
        try(BufferedReader br = new BufferedReader(new java.io.StringReader(raw))){
            String line;
            while((line=br.readLine())!=null){
                String[] parts = line.split("::",4);
                if(parts.length>=3){
                    try {
                        NotebookInfo.Type type = NotebookInfo.Type.valueOf(parts[1]);
                        File folder = new File(parts[2]);
                        String iconId = parts.length>=4?parts[3]:"legacy";
                        long created=folder.exists()?folder.lastModified():System.currentTimeMillis();
                        if (isNewName(seenNames, parts[0])) {
                            notebooks.add(new NotebookInfo(parts[0],type,folder,created,iconId));
                        }
                    } catch (IllegalArgumentException e) {
                        // Skip notebooks with unknown types (e.g., REGULAR type that was removed)
                        logWarn("Skipping notebook with unknown type: " + parts[1], null);
                    }
                }
            }
        }catch(IOException ex){
            logWarn("Failed to parse legacy notebook store", ex);
        }
    }

    private void parseJson(String raw, Set<String> seenNames) {
        // Minimal parser tailored to our own serialized format
        Pattern objPattern = Pattern.compile("\\{[^}]*\\}");
        Matcher m = objPattern.matcher(raw);
        while (m.find()) {
            String obj = m.group();
            String name = extractJsonString(obj, "name");
            String typeStr = extractJsonString(obj, "type");
            String folderStr = extractJsonString(obj, "folder");
            String iconId = extractJsonString(obj, "iconId");
            Long created = extractJsonLong(obj, "created");
            // New customization fields
            String description = extractJsonString(obj, "description");
            Long accentColor = extractJsonLong(obj, "accentColor");
            String clusterId = extractJsonString(obj, "clusterId");
            
            if (name == null || typeStr == null || folderStr == null) continue;
            NotebookInfo.Type type;
            try { type = NotebookInfo.Type.valueOf(typeStr); } catch (IllegalArgumentException e) { continue; }
            if (!isNewName(seenNames, name)) continue;
            File folder = new File(folderStr);
            long createdMs = created != null ? created : (folder.exists() ? folder.lastModified() : System.currentTimeMillis());
            int accent = accentColor != null ? accentColor.intValue() : -1;
            notebooks.add(new NotebookInfo(name, type, folder, createdMs, 
                iconId == null ? "legacy" : iconId,
                description == null ? "" : description,
                accent,
                clusterId));
        }
    }

    private void save() {
        try {
            String json = toJson(notebooks);
            File parent = jsonFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            FileIO.ensureSpace(jsonFile.toPath(), json.getBytes(StandardCharsets.UTF_8).length + 4096L, "notebook store save");
            FileIO.atomicWrite(jsonFile.toPath(), json, StandardCharsets.UTF_8, true, true);
        } catch(IOException ex){
            logWarn("Failed to save notebook store", ex);
        }
    }

    public List<NotebookInfo> list(){ return Collections.unmodifiableList(notebooks); }

    public NotebookInfo create(String name, NotebookInfo.Type type, String iconId){
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Notebook name cannot be empty");
        }
        for (NotebookInfo nb : notebooks) {
            if (nb.getName().equalsIgnoreCase(name.trim())) {
                throw new IllegalArgumentException("Notebook with the same name already exists");
            }
        }
        File folder = new File(AppDirectories.getRoot(), "notebooks"+File.separator+name);
        folder.mkdirs();
        NotebookInfo nb=new NotebookInfo(name,type,folder,System.currentTimeMillis(),iconId);
        notebooks.add(nb); save();
        return nb;
    }

    // Backward compatibility
    public NotebookInfo create(String name, NotebookInfo.Type type){
        return create(name,type,"legacy");
    }
    
    /** Create notebook with full customization options */
    public NotebookInfo create(String name, NotebookInfo.Type type, String iconId, String description, int accentColor) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Notebook name cannot be empty");
        }
        for (NotebookInfo nb : notebooks) {
            if (nb.getName().equalsIgnoreCase(name.trim())) {
                throw new IllegalArgumentException("Notebook with the same name already exists");
            }
        }
        File folder = new File(AppDirectories.getRoot(), "notebooks" + File.separator + name);
        folder.mkdirs();
        NotebookInfo nb = new NotebookInfo(name, type, folder, System.currentTimeMillis(), iconId, description, accentColor, null);
        notebooks.add(nb);
        save();
        return nb;
    }
    
    /** Update notebook customization (description, accent color) */
    public boolean updateCustomization(NotebookInfo nb, String description, int accentColor) {
        return updateCustomization(nb, description, accentColor, null);
    }
    
    /** Update notebook customization (description, accent color, custom icon path) */
    public boolean updateCustomization(NotebookInfo nb, String description, int accentColor, String customIconPath) {
        if (nb == null) return false;
        int idx = -1;
        for (int i = 0; i < notebooks.size(); i++) {
            if (notebooks.get(i).getName().equalsIgnoreCase(nb.getName())) {
                idx = i;
                break;
            }
        }
        if (idx < 0) return false;
        NotebookInfo updated = notebooks.get(idx).withCustomization(description, accentColor, customIconPath);
        notebooks.set(idx, updated);
        save();
        return true;
    }
    
    // --- Cluster Management --- //
    
    /** Assign a notebook to a cluster */
    public boolean assignToCluster(NotebookInfo nb, String clusterId) {
        if (nb == null) return false;
        int idx = -1;
        for (int i = 0; i < notebooks.size(); i++) {
            if (notebooks.get(i).getName().equalsIgnoreCase(nb.getName())) {
                idx = i;
                break;
            }
        }
        if (idx < 0) return false;
        NotebookInfo updated = notebooks.get(idx).withCluster(clusterId);
        notebooks.set(idx, updated);
        save();
        return true;
    }
    
    /** Remove a notebook from its cluster */
    public boolean removeFromCluster(NotebookInfo nb) {
        return assignToCluster(nb, null);
    }
    
    /** Get all unique cluster IDs */
    public List<String> getClusterIds() {
        Set<String> ids = new java.util.LinkedHashSet<>();
        for (NotebookInfo nb : notebooks) {
            if (nb.isClustered()) {
                ids.add(nb.getClusterId());
            }
        }
        return new ArrayList<>(ids);
    }
    
    /** Get notebooks in a specific cluster */
    public List<NotebookInfo> getNotebooksInCluster(String clusterId) {
        List<NotebookInfo> result = new ArrayList<>();
        if (clusterId == null) return result;
        for (NotebookInfo nb : notebooks) {
            if (clusterId.equals(nb.getClusterId())) {
                result.add(nb);
            }
        }
        return result;
    }
    
    /** Get unclustered notebooks */
    public List<NotebookInfo> getUnclusteredNotebooks() {
        List<NotebookInfo> result = new ArrayList<>();
        for (NotebookInfo nb : notebooks) {
            if (!nb.isClustered()) {
                result.add(nb);
            }
        }
        return result;
    }
    
    /** Disband a cluster (removes all notebooks from it) */
    public void disbandCluster(String clusterId) {
        if (clusterId == null) return;
        for (int i = 0; i < notebooks.size(); i++) {
            NotebookInfo nb = notebooks.get(i);
            if (clusterId.equals(nb.getClusterId())) {
                notebooks.set(i, nb.withCluster(null));
            }
        }
        save();
    }

    /**
     * Removes the notebook from the store and deletes its folder on disk.
     * Also cleans up associated mood data.
     */
    public void delete(NotebookInfo nb){
        if(nb==null) return;
        String targetName = nb.getName();
        notebooks.removeIf(n -> n.getName().equalsIgnoreCase(targetName));
        save();
        
        // Clean up mood data associated with this notebook's entries
        cleanupMoodData(nb);
        
        // delete files on disk
        deleteRec(nb.getFolder());
    }
    
    /**
     * Removes mood data entries that correspond to journal entries in the deleted notebook.
     * This matches mood entries by timestamp with the journal entry file timestamps.
     */
    private void cleanupMoodData(NotebookInfo nb) {
        if (nb.getType() != NotebookInfo.Type.JOURNAL) {
            return; // Only journal notebooks have mood data
        }
        
        File moodFile = new File(AppDirectories.folder(AppDirectories.Type.MOOD_DATA), "mood_log.txt");
        if (!moodFile.exists()) {
            return;
        }
        
        // Collect timestamps of all journal entries in this notebook
        Set<String> entryTimestamps = new HashSet<>();
        File[] entries = nb.getFolder().listFiles((dir, name) -> name.toLowerCase().endsWith(".txt"));
        if (entries != null) {
            for (File entry : entries) {
                String fileName = entry.getName();
                if (fileName.matches("\\d{8}_\\d{6}\\.txt")) {
                    // Extract timestamp from filename (yyyyMMdd_HHmmss.txt)
                    String timestamp = fileName.substring(0, fileName.lastIndexOf('.'));
                    entryTimestamps.add(timestamp);
                }
            }
        }
        
        if (entryTimestamps.isEmpty()) {
            return;
        }
        
        // Read existing mood data and filter out entries matching deleted notebook
        List<String> remainingMoodEntries = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(moodFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    String moodTimestamp = parts[0].trim();
                    // Check if this mood entry matches any of the deleted notebook's entries
                    if (!entryTimestamps.contains(moodTimestamp)) {
                        remainingMoodEntries.add(line);
                    }
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }
        
        // Write back the filtered mood data
        try (PrintWriter pw = new PrintWriter(new FileWriter(moodFile))) {
            for (String entry : remainingMoodEntries) {
                pw.println(entry);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Renames the notebook, moving its folder and updating persistence.
     */
    public boolean rename(NotebookInfo nb, String newName){
        if(nb==null || newName==null || newName.trim().isEmpty()) return false;
        newName = newName.trim();
        // Check duplicate names
        for(NotebookInfo n: notebooks){ if(n!=nb && n.getName().equalsIgnoreCase(newName)) return false; }
        File newFolder = new File(nb.getFolder().getParentFile(), newName);
        try {
            try {
                Files.move(nb.getFolder().toPath(), newFolder.toPath(), StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFail) {
                Files.move(nb.getFolder().toPath(), newFolder.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            NotebookInfo updated = new NotebookInfo(newName, nb.getType(), newFolder, nb.getCreatedMillis(), nb.getIconId());
            notebooks.remove(nb);
            notebooks.add(updated);
            save();
            return true;
        } catch (IOException e) {
            logWarn("Failed to rename notebook folder", e);
            return false;
        }
    }

    private static void deleteRec(File f){
        if(f==null || !f.exists()) return;
        if(f.isDirectory()){
            File[] children = f.listFiles();
            if (children != null) {
                for(File ch: children){
                    deleteRec(ch);
                }
            }
        }
        f.delete();
    }

    /** Reload notebooks from file system */
    public void reload(){ load(); }

    // --- JSON helpers (minimal, controlled format only) --- //
    private static String toJson(List<NotebookInfo> notebooks){
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < notebooks.size(); i++) {
            NotebookInfo nb = notebooks.get(i);
            if (i > 0) sb.append(",");
            sb.append("\n  {");
            sb.append("\"name\":\"").append(jsonEscape(nb.getName())).append("\",");
            sb.append("\"type\":\"").append(jsonEscape(nb.getType().name())).append("\",");
            sb.append("\"folder\":\"").append(jsonEscape(nb.getFolder().getAbsolutePath())).append("\",");
            sb.append("\"created\":").append(nb.getCreatedMillis()).append(",");
            sb.append("\"iconId\":\"").append(jsonEscape(nb.getIconId())).append("\",");
            sb.append("\"description\":\"").append(jsonEscape(nb.getDescription())).append("\",");
            sb.append("\"accentColor\":").append(nb.getAccentColorRaw());
            if (nb.getClusterId() != null) {
                sb.append(",\"clusterId\":\"").append(jsonEscape(nb.getClusterId())).append("\"");
            }
            sb.append("}");
        }
        if (!notebooks.isEmpty()) sb.append("\n");
        sb.append("]");
        return sb.toString();
    }

    private static String jsonEscape(String s){
        if (s == null) return "";
        StringBuilder out = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static String extractJsonString(String obj, String key){
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        Matcher m = p.matcher(obj);
        if (!m.find()) return null;
        return jsonUnescape(m.group(1));
    }

    private static Long extractJsonLong(String obj, String key){
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*([0-9]+)");
        Matcher m = p.matcher(obj);
        if (!m.find()) return null;
        try { return Long.parseLong(m.group(1)); } catch (NumberFormatException e) { return null; }
    }

    private static String jsonUnescape(String s){
        StringBuilder out = new StringBuilder();
        boolean esc = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (esc) {
                switch (c) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    default -> out.append(c);
                }
                esc = false;
            } else if (c == '\\') {
                esc = true;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static void logWarn(String msg, Throwable t){
        IoLog.warn("notebook-store", msg, t);
    }

    private static boolean isNewName(Set<String> seenNames, String name){
        if (name == null) return false;
        String key = name.trim().toLowerCase();
        if (seenNames.contains(key)) return false;
        seenNames.add(key);
        return true;
    }
}
