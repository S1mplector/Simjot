/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.core.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
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
import main.infrastructure.io.MoodFile;
import main.infrastructure.io.FileIO;
import main.infrastructure.io.IoLog;
import main.infrastructure.io.NativeJson;

/**
 * Persists list of notebooks under Simjot/notebooks.json and provides helpers.
 * It is used to load and save notebooks from/to the file system.
 * @author S1mplector
 */
public final class NotebookStore {
    private static final String FILE_NAME = "notebooks.json";

    private final File jsonFile;
    private final List<NotebookInfo> notebooks = new ArrayList<>();
    private long lastLoadedMtime = -1L;

    public NotebookStore() {
        jsonFile = new File(AppDirectories.getRoot(), FILE_NAME);
        load();
    }

    private void load() {
        notebooks.clear();
        boolean[] changed = new boolean[1];
        boolean loadedPrimary = false;
        if (jsonFile.exists()) {
            lastLoadedMtime = jsonFile.lastModified();
            loadedPrimary = mergeFromFile(jsonFile, changed);
        } else {
            lastLoadedMtime = -1L;
        }

        boolean mergedConflicts = mergeConflictCopies(changed);
        if (!loadedPrimary && mergedConflicts) {
            // If only conflict copies were available, persist as primary.
            changed[0] = true;
        }

        mergeDiscoveredFolders(changed);

        if (changed[0]) save();
    }

    private boolean mergeFromFile(File file, boolean[] changed) {
        if (file == null || !file.exists() || !file.isFile()) return false;
        String raw;
        try {
            raw = new String(FileIO.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            logWarn("Failed to read notebook store file: " + file.getAbsolutePath(), ex);
            return false;
        }
        if (raw == null) return false;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return false;
        int before = notebooks.size();
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            parseJson(trimmed, changed);
        } else {
            legacyLoad(trimmed, changed);
        }
        return notebooks.size() > before;
    }

    private boolean mergeConflictCopies(boolean[] changed) {
        File root = jsonFile.getParentFile();
        if (root == null || !root.exists() || !root.isDirectory()) return false;
        File[] conflicts = root.listFiles((dir, name) -> isConflictCopy(name));
        if (conflicts == null || conflicts.length == 0) return false;
        boolean merged = false;
        for (File conflict : conflicts) {
            if (conflict == null || conflict.equals(jsonFile)) continue;
            if (mergeFromFile(conflict, changed)) {
                merged = true;
            }
        }
        if (merged) {
            IoLog.warn("notebook-store", "Merged iCloud conflict copies for notebooks.json", null);
        }
        return merged;
    }

    private static boolean isConflictCopy(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        if (!lower.startsWith("notebooks")) return false;
        if (!lower.endsWith(".json")) return false;
        if (lower.equals(FILE_NAME)) return false;
        return lower.contains("conflict");
    }

    private boolean mergeDiscoveredFolders(boolean[] changed) {
        File notebooksRoot;
        try {
            notebooksRoot = AppDirectories.folder(AppDirectories.Type.NOTEBOOKS);
        } catch (Throwable ignored) {
            return false;
        }
        if (notebooksRoot == null || !notebooksRoot.exists() || !notebooksRoot.isDirectory()) return false;
        File[] folders = notebooksRoot.listFiles(File::isDirectory);
        if (folders == null || folders.length == 0) return false;

        boolean merged = false;
        for (File folder : folders) {
            if (!isNotebookFolderCandidate(folder)) continue;

            String name = folder.getName();
            int existingIdx = findIndexByName(name);
            NotebookInfo before = existingIdx >= 0 ? notebooks.get(existingIdx) : null;

            NotebookInfo candidate = new NotebookInfo(
                    name,
                    inferTypeFromFolder(folder),
                    folder,
                    inferCreatedMillis(folder),
                    "legacy");
            mergeNotebookEntry(candidate);

            NotebookInfo after = null;
            int updatedIdx = findIndexByName(name);
            if (updatedIdx >= 0) {
                after = notebooks.get(updatedIdx);
            }

            if (before == null && after != null) {
                merged = true;
                if (changed != null) changed[0] = true;
                IoLog.info("notebook-discovery", "Recovered notebook folder: " + folder.getAbsolutePath());
            } else if (!sameNotebookRecord(before, after)) {
                merged = true;
                if (changed != null) changed[0] = true;
                IoLog.info("notebook-discovery", "Reconciled notebook folder: " + folder.getAbsolutePath());
            }
        }
        return merged;
    }

    private static boolean isNotebookFolderCandidate(File folder) {
        if (folder == null || !folder.exists() || !folder.isDirectory()) return false;
        String name = folder.getName();
        return name != null && !name.isBlank() && !name.startsWith(".");
    }

    private static NotebookInfo.Type inferTypeFromFolder(File folder) {
        int[] scores = new int[3];
        accumulateTypeScores(folder, scores, 0);
        if (scores[1] > 0 && scores[1] >= scores[2] && scores[1] >= scores[0]) {
            return NotebookInfo.Type.POETRY;
        }
        if (scores[2] > 0 && scores[2] >= scores[0]) {
            return NotebookInfo.Type.NOTETAKING;
        }
        return NotebookInfo.Type.JOURNAL;
    }

    private static void accumulateTypeScores(File file, int[] scores, int depth) {
        if (file == null || scores == null || !file.exists()) return;
        if (file.isDirectory()) {
            if (depth > 4) return;
            File[] children = file.listFiles();
            if (children == null) return;
            for (File child : children) {
                if (child == null) continue;
                if (child.isDirectory() && child.getName() != null && child.getName().startsWith(".")) continue;
                accumulateTypeScores(child, scores, depth + 1);
            }
            return;
        }

        String name = file.getName().toLowerCase();
        if (hasNotebookExtension(name, ".poem")) {
            scores[1]++;
        } else if (hasNotebookExtension(name, ".ntk")) {
            scores[2]++;
        } else if (hasNotebookExtension(name, ".note")
                || hasNotebookExtension(name, ".txt")
                || hasNotebookExtension(name, ".md")
                || hasNotebookExtension(name, ".rtf")
                || hasNotebookExtension(name, ".jrnl")) {
            scores[0]++;
        }
    }

    private static boolean hasNotebookExtension(String fileName, String extension) {
        if (fileName == null || extension == null || extension.isBlank()) return false;
        return fileName.endsWith(extension) || fileName.contains(extension + ".");
    }

    private static long inferCreatedMillis(File folder) {
        long inferred = earliestModified(folder, 0);
        return inferred > 0L ? inferred : System.currentTimeMillis();
    }

    private static long earliestModified(File file, int depth) {
        if (file == null || !file.exists()) return 0L;
        long best = file.lastModified();
        if (file.isDirectory()) {
            if (depth > 4) return best;
            File[] children = file.listFiles();
            if (children == null) return best;
            for (File child : children) {
                if (child == null) continue;
                long childModified = earliestModified(child, depth + 1);
                if (childModified > 0L && (best <= 0L || childModified < best)) {
                    best = childModified;
                }
            }
        }
        return best;
    }

    private static boolean sameNotebookRecord(NotebookInfo a, NotebookInfo b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return sameText(a.getName(), b.getName())
                && a.getType() == b.getType()
                && samePath(a.getFolder(), b.getFolder())
                && a.getCreatedMillis() == b.getCreatedMillis()
                && sameText(a.getIconId(), b.getIconId())
                && sameText(a.getDescription(), b.getDescription())
                && a.getAccentColorRaw() == b.getAccentColorRaw()
                && sameText(a.getClusterId(), b.getClusterId())
                && sameText(a.getCustomIconPath(), b.getCustomIconPath())
                && sameText(a.getBackgroundImagePath(), b.getBackgroundImagePath())
                && sameText(a.getCoverImagePath(), b.getCoverImagePath())
                && sameText(a.getEditorFontFamily(), b.getEditorFontFamily())
                && sameText(a.getEditorStylePreset(), b.getEditorStylePreset());
    }

    private static boolean samePath(File a, File b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.toPath().toAbsolutePath().normalize()
                .equals(b.toPath().toAbsolutePath().normalize());
    }

    private static boolean sameText(String a, String b) {
        return java.util.Objects.equals(a, b);
    }

    private void legacyLoad(String raw, boolean[] changed) {
        try(BufferedReader br = new BufferedReader(new java.io.StringReader(raw))){
            String line;
            while((line=br.readLine())!=null){
                String[] parts = line.split("::",4);
                if(parts.length>=3){
                    try {
                        NotebookInfo.Type type = NotebookInfo.Type.valueOf(parts[1]);
                        File folder = normalizeFolder(parts[2], parts[0], changed);
                        if (folder == null) continue;
                        String iconId = parts.length>=4?parts[3]:"legacy";
                        long created=folder.exists()?folder.lastModified():System.currentTimeMillis();
                        mergeNotebookEntry(new NotebookInfo(parts[0],type,folder,created,iconId));
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

    private void parseJson(String raw, boolean[] changed) {
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
            String customIconPath = extractJsonString(obj, "customIconPath");
            String backgroundImagePath = extractJsonString(obj, "backgroundImagePath");
            String coverImagePath = extractJsonString(obj, "coverImagePath");
            String editorFontFamily = extractJsonString(obj, "editorFontFamily");
            String editorStylePreset = extractJsonString(obj, "editorStylePreset");
            
            if (name == null || typeStr == null || folderStr == null) continue;
            NotebookInfo.Type type;
            try { type = NotebookInfo.Type.valueOf(typeStr); } catch (IllegalArgumentException e) { continue; }
            File folder = normalizeFolder(folderStr, name, changed);
            if (folder == null) continue;
            long createdMs = created != null ? created : (folder.exists() ? folder.lastModified() : System.currentTimeMillis());
            int accent = accentColor != null ? accentColor.intValue() : -1;
            String normalizedIconPath = normalizeStoredPath(customIconPath, changed);
            String normalizedBackgroundPath = normalizeStoredPath(backgroundImagePath, changed);
            String normalizedCoverPath = normalizeStoredPath(coverImagePath, changed);
            mergeNotebookEntry(new NotebookInfo(name, type, folder, createdMs, 
                iconId == null ? "legacy" : iconId,
                description == null ? "" : description,
                accent,
                clusterId,
                normalizedIconPath,
                normalizedBackgroundPath,
                normalizedCoverPath,
                editorFontFamily,
                editorStylePreset));
        }
    }

    private static File normalizeFolder(String folderStr, String name, boolean[] changed) {
        if (folderStr == null || folderStr.isBlank()) return null;
        File root = null;
        try { root = AppDirectories.getRoot(); } catch (Throwable ignored) {}
        File folder = new File(folderStr);
        boolean wasAbsolute = folder.isAbsolute();
        if (root != null) {
            if (!wasAbsolute) {
                folder = new File(root, folderStr);
            }
            if (wasAbsolute && isUnderRoot(folder, root) && changed != null) {
                changed[0] = true;
            }
            if (!isUnderRoot(folder, root) && name != null && !name.isBlank()) {
                File candidate = new File(new File(root, "notebooks"), name);
                if (candidate.exists() || looksLikeNotebookPath(folderStr, name)) {
                    IoLog.warn("notebook-rebase", "Rebasing notebook folder from " + folder.getAbsolutePath() +
                            " to " + candidate.getAbsolutePath(), null);
                    folder = candidate;
                    if (changed != null) changed[0] = true;
                }
            }
        }
        return folder;
    }

    private static boolean looksLikeNotebookPath(String folderStr, String name) {
        if (folderStr == null || name == null || name.isBlank()) return false;
        String cleanName = name.trim();
        String sep = File.separator;
        String probe = sep + "notebooks" + sep + cleanName;
        if (folderStr.contains(probe)) return true;
        String alt = folderStr.replace('\\', '/');
        return alt.contains("/notebooks/" + cleanName);
    }

    private static boolean isUnderRoot(File folder, File root) {
        if (folder == null || root == null) return false;
        try {
            java.nio.file.Path rootPath = root.toPath().toAbsolutePath().normalize();
            java.nio.file.Path folderPath = folder.toPath().toAbsolutePath().normalize();
            return folderPath.startsWith(rootPath);
        } catch (Throwable ignored) {
            String rootPath = root.getAbsolutePath();
            String folderPath = folder.getAbsolutePath();
            if (rootPath == null || folderPath == null) return false;
            String prefix = rootPath.endsWith(File.separator) ? rootPath : rootPath + File.separator;
            return folderPath.startsWith(prefix);
        }
    }

    private void save() {
        try {
            if (jsonFile.exists()) {
                long currentMtime = jsonFile.lastModified();
                if (lastLoadedMtime > 0 && currentMtime > lastLoadedMtime) {
                    boolean[] merged = new boolean[1];
                    mergeFromFile(jsonFile, merged);
                }
            }
            boolean[] mergedConflicts = new boolean[1];
            mergeConflictCopies(mergedConflicts);
            String json = toJson(notebooks);
            File parent = jsonFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            FileIO.ensureSpace(jsonFile.toPath(), json.getBytes(StandardCharsets.UTF_8).length + 4096L, "notebook store save");
            FileIO.atomicWrite(jsonFile.toPath(), json, StandardCharsets.UTF_8, true, true);
            lastLoadedMtime = jsonFile.lastModified();
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
        return updateCustomization(nb, description, accentColor, nb != null ? nb.getCustomIconPath() : null);
    }
    
    /** Update notebook customization (description, accent color, custom icon path) */
    public boolean updateCustomization(NotebookInfo nb, String description, int accentColor, String customIconPath) {
        return updateCustomization(nb, description, accentColor, customIconPath,
                nb != null ? nb.getBackgroundImagePath() : null,
                nb != null ? nb.getEditorFontFamily() : null,
                nb != null ? nb.getEditorStylePreset() : null);
    }

    /** Update notebook customization and writing personalization. */
    public boolean updateCustomization(NotebookInfo nb, String description, int accentColor, String customIconPath,
                                       String backgroundImagePath, String editorFontFamily, String editorStylePreset) {
        return updateCustomization(nb, description, accentColor, customIconPath, backgroundImagePath,
                nb != null ? nb.getCoverImagePath() : null, editorFontFamily, editorStylePreset);
    }

    /** Update notebook customization, writing personalization, and cover preview. */
    public boolean updateCustomization(NotebookInfo nb, String description, int accentColor, String customIconPath,
                                       String backgroundImagePath, String coverImagePath,
                                       String editorFontFamily, String editorStylePreset) {
        if (nb == null) return false;
        int idx = -1;
        for (int i = 0; i < notebooks.size(); i++) {
            if (notebooks.get(i).getName().equalsIgnoreCase(nb.getName())) {
                idx = i;
                break;
            }
        }
        if (idx < 0) return false;
        NotebookInfo current = notebooks.get(idx);
        NotebookInfo updated = current.withCustomization(
                description,
                accentColor,
                customIconPath,
                backgroundImagePath,
                coverImagePath,
                editorFontFamily,
                editorStylePreset);
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
        try {
            MoodFile.removeRecordsByTimestamp(entryTimestamps);
        } catch (Throwable ignored) {}
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
            NotebookInfo updated = new NotebookInfo(newName, nb.getType(), newFolder, nb.getCreatedMillis(), nb.getIconId(),
                nb.getDescription(), nb.getAccentColorRaw(), nb.getClusterId(), nb.getCustomIconPath(),
                nb.getBackgroundImagePath(), nb.getCoverImagePath(), nb.getEditorFontFamily(), nb.getEditorStylePreset());
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

    private void mergeNotebookEntry(NotebookInfo candidate) {
        if (candidate == null || candidate.getName() == null) return;
        int idx = findIndexByName(candidate.getName());
        if (idx < 0) {
            notebooks.add(candidate);
            return;
        }
        NotebookInfo existing = notebooks.get(idx);
        NotebookInfo merged = mergeNotebook(existing, candidate);
        if (merged != existing) {
            notebooks.set(idx, merged);
        }
    }

    private int findIndexByName(String name) {
        if (name == null) return -1;
        String key = name.trim().toLowerCase();
        for (int i = 0; i < notebooks.size(); i++) {
            NotebookInfo nb = notebooks.get(i);
            if (nb.getName() != null && nb.getName().trim().toLowerCase().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private static NotebookInfo mergeNotebook(NotebookInfo existing, NotebookInfo incoming) {
        if (existing == null) return incoming;
        if (incoming == null) return existing;
        File root = null;
        try { root = AppDirectories.getRoot(); } catch (Throwable ignored) {}
        String name = existing.getName() != null ? existing.getName() : incoming.getName();
        NotebookInfo.Type type = existing.getType() != null ? existing.getType() : incoming.getType();
        File folder = chooseFolder(existing.getFolder(), incoming.getFolder(), root);
        long created = chooseCreated(existing.getCreatedMillis(), incoming.getCreatedMillis());
        String iconId = chooseIconId(existing.getIconId(), incoming.getIconId());
        String description = chooseNonEmpty(existing.getDescription(), incoming.getDescription());
        int accent = chooseAccent(existing.getAccentColorRaw(), incoming.getAccentColorRaw());
        String clusterId = chooseNonEmpty(existing.getClusterId(), incoming.getClusterId());
        String customIcon = choosePath(existing.getCustomIconPath(), incoming.getCustomIconPath(), root);
        String backgroundImage = choosePath(existing.getBackgroundImagePath(), incoming.getBackgroundImagePath(), root);
        String coverImage = choosePath(existing.getCoverImagePath(), incoming.getCoverImagePath(), root);
        String fontFamily = chooseNonEmpty(existing.getEditorFontFamily(), incoming.getEditorFontFamily());
        String stylePreset = chooseNonEmpty(existing.getEditorStylePreset(), incoming.getEditorStylePreset());
        return new NotebookInfo(name, type, folder, created, iconId, description, accent, clusterId, customIcon,
                backgroundImage, coverImage, fontFamily, stylePreset);
    }

    private static File chooseFolder(File existing, File incoming, File root) {
        if (existing == null) return incoming;
        if (incoming == null) return existing;
        boolean existingExists = existing.exists();
        boolean incomingExists = incoming.exists();
        if (existingExists && !incomingExists) return existing;
        if (incomingExists && !existingExists) return incoming;
        if (root != null) {
            boolean existingUnder = isUnderRoot(existing, root);
            boolean incomingUnder = isUnderRoot(incoming, root);
            if (existingUnder && !incomingUnder) return existing;
            if (incomingUnder && !existingUnder) return incoming;
        }
        return existing;
    }

    private static long chooseCreated(long existing, long incoming) {
        if (existing <= 0) return incoming;
        if (incoming <= 0) return existing;
        return Math.min(existing, incoming);
    }

    private static int chooseAccent(int existing, int incoming) {
        if (existing != -1) return existing;
        return incoming;
    }

    private static String chooseIconId(String existing, String incoming) {
        String e = existing == null ? "" : existing.trim();
        String i = incoming == null ? "" : incoming.trim();
        if (e.isEmpty()) return i.isEmpty() ? "legacy" : i;
        if ("legacy".equalsIgnoreCase(e) && !i.isEmpty() && !"legacy".equalsIgnoreCase(i)) return i;
        return e;
    }

    private static String chooseNonEmpty(String existing, String incoming) {
        String e = existing == null ? "" : existing.trim();
        String i = incoming == null ? "" : incoming.trim();
        if (e.isEmpty()) return i.isEmpty() ? "" : i;
        return e;
    }

    private static String choosePath(String existing, String incoming, File root) {
        String e = existing == null ? "" : existing.trim();
        String i = incoming == null ? "" : incoming.trim();
        if (e.isEmpty()) return i.isEmpty() ? "" : i;
        if (i.isEmpty()) return e;
        boolean eFile = looksLikeFilePath(e);
        boolean iFile = looksLikeFilePath(i);
        boolean eExists = eFile && new File(e).exists();
        boolean iExists = iFile && new File(i).exists();
        if (eExists && !iExists) return e;
        if (iExists && !eExists) return i;
        if (root != null) {
            if (eFile && iFile) {
                boolean eUnder = isUnderRoot(new File(e), root);
                boolean iUnder = isUnderRoot(new File(i), root);
                if (eUnder && !iUnder) return e;
                if (iUnder && !eUnder) return i;
            }
        }
        return e;
    }

    private static boolean looksLikeFilePath(String path) {
        if (path == null) return false;
        String p = path.trim().toLowerCase();
        return !(p.startsWith("res:") || p.startsWith("gen:"));
    }

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
            sb.append("\"folder\":\"").append(jsonEscape(encodeFolderPath(nb.getFolder()))).append("\",");
            sb.append("\"created\":").append(nb.getCreatedMillis()).append(",");
            sb.append("\"iconId\":\"").append(jsonEscape(nb.getIconId())).append("\",");
            sb.append("\"description\":\"").append(jsonEscape(nb.getDescription())).append("\",");
            sb.append("\"accentColor\":").append(nb.getAccentColorRaw());
            if (nb.getClusterId() != null) {
                sb.append(",\"clusterId\":\"").append(jsonEscape(nb.getClusterId())).append("\"");
            }
            if (nb.getCustomIconPath() != null && !nb.getCustomIconPath().isBlank()) {
                sb.append(",\"customIconPath\":\"").append(jsonEscape(encodeStoredPath(nb.getCustomIconPath()))).append("\"");
            }
            if (nb.getBackgroundImagePath() != null && !nb.getBackgroundImagePath().isBlank()) {
                sb.append(",\"backgroundImagePath\":\"").append(jsonEscape(encodeStoredPath(nb.getBackgroundImagePath()))).append("\"");
            }
            if (nb.getCoverImagePath() != null && !nb.getCoverImagePath().isBlank()) {
                sb.append(",\"coverImagePath\":\"").append(jsonEscape(encodeStoredPath(nb.getCoverImagePath()))).append("\"");
            }
            if (nb.getEditorFontFamily() != null && !nb.getEditorFontFamily().isBlank()) {
                sb.append(",\"editorFontFamily\":\"").append(jsonEscape(nb.getEditorFontFamily())).append("\"");
            }
            if (nb.getEditorStylePreset() != null && !nb.getEditorStylePreset().isBlank()) {
                sb.append(",\"editorStylePreset\":\"").append(jsonEscape(nb.getEditorStylePreset())).append("\"");
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
        return NativeJson.getString(obj, key);
    }

    private static Long extractJsonLong(String obj, String key){
        return NativeJson.getLong(obj, key);
    }

    private static String normalizeStoredPath(String path, boolean[] changed) {
        if (path == null || path.isBlank()) return null;
        String trimmed = path.trim();
        if (trimmed.startsWith("res:") || trimmed.startsWith("gen:")) return trimmed;
        File root = null;
        try { root = AppDirectories.getRoot(); } catch (Throwable ignored) {}
        if (root == null) return trimmed;
        File f = new File(trimmed);
        if (!f.isAbsolute()) {
            return new File(root, trimmed).getAbsolutePath();
        }
        if (isUnderRoot(f, root)) {
            if (changed != null) changed[0] = true;
            return f.getAbsolutePath();
        }
        String rel = extractRelativeFromSimjotPath(trimmed);
        if (rel != null && !rel.isBlank()) {
            if (changed != null) changed[0] = true;
            return new File(root, rel).getAbsolutePath();
        }
        return trimmed;
    }

    private static String encodeFolderPath(File folder) {
        if (folder == null) return "";
        return encodeStoredPath(folder.getAbsolutePath());
    }

    private static String encodeStoredPath(String path) {
        if (path == null || path.isBlank()) return "";
        String trimmed = path.trim();
        if (trimmed.startsWith("res:") || trimmed.startsWith("gen:")) return trimmed;
        File root = null;
        try { root = AppDirectories.getRoot(); } catch (Throwable ignored) {}
        if (root == null) return trimmed;
        File f = new File(trimmed);
        if (!f.isAbsolute()) return trimmed;
        if (isUnderRoot(f, root)) {
            String rel = relativizeToRoot(root, f);
            if (rel != null && !rel.isBlank()) return rel;
        }
        String rel = extractRelativeFromSimjotPath(trimmed);
        if (rel != null && !rel.isBlank()) return rel;
        return trimmed;
    }

    private static String relativizeToRoot(File root, File target) {
        try {
            java.nio.file.Path rootPath = root.toPath().toAbsolutePath().normalize();
            java.nio.file.Path targetPath = target.toPath().toAbsolutePath().normalize();
            if (!targetPath.startsWith(rootPath)) return null;
            String rel = rootPath.relativize(targetPath).toString();
            return rel.isEmpty() ? null : rel;
        } catch (Throwable ignored) {
            String rootPath = root.getAbsolutePath();
            String targetPath = target.getAbsolutePath();
            if (rootPath == null || targetPath == null) return null;
            String prefix = rootPath.endsWith(File.separator) ? rootPath : rootPath + File.separator;
            if (!targetPath.startsWith(prefix)) return null;
            return targetPath.substring(prefix.length());
        }
    }

    private static String extractRelativeFromSimjotPath(String path) {
        if (path == null) return null;
        String normalized = path.replace('\\', '/');
        String lower = normalized.toLowerCase();
        String token = "/simjot/";
        int idx = lower.lastIndexOf(token);
        if (idx < 0) return null;
        String rel = normalized.substring(idx + token.length());
        if (rel.isEmpty()) return null;
        if (File.separatorChar != '/') {
            rel = rel.replace('/', File.separatorChar);
        }
        return rel;
    }

    private static void logWarn(String msg, Throwable t){
        IoLog.warn("notebook-store", msg, t);
    }
}
