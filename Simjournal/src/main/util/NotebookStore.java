package main.util;

import java.io.*;
import java.util.*;

/**
 * Persists list of notebooks under Simnote/notebooks.json and provides helpers.
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
        if(!jsonFile.exists()) return;
        try(BufferedReader br = new BufferedReader(new FileReader(jsonFile))){
            String line;
            while((line=br.readLine())!=null){
                String[] parts = line.split("::",4);
                if(parts.length>=3){
                    try {
                        NotebookInfo.Type type = NotebookInfo.Type.valueOf(parts[1]);
                        File folder = new File(parts[2]);
                        String iconId = parts.length>=4?parts[3]:"legacy";
                        long created=folder.exists()?folder.lastModified():System.currentTimeMillis();
                        notebooks.add(new NotebookInfo(parts[0],type,folder,created,iconId));
                    } catch (IllegalArgumentException e) {
                        // Skip notebooks with unknown types (e.g., REGULAR type that was removed)
                        System.out.println("Skipping notebook with unknown type: " + parts[1]);
                    }
                }
            }
        }catch(IOException ignored){}
    }

    private void save() {
        try(PrintWriter pw = new PrintWriter(new FileWriter(jsonFile))){
            for(NotebookInfo nb: notebooks){
                pw.println(nb.getName()+"::"+nb.getType().name()+"::"+nb.getFolder().getAbsolutePath()+"::"+nb.getIconId());
            }
        }catch(IOException ignored){}
    }

    public List<NotebookInfo> list(){ return Collections.unmodifiableList(notebooks); }

    public NotebookInfo create(String name, NotebookInfo.Type type, String iconId){
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

    /**
     * Removes the notebook from the store and deletes its folder on disk.
     */
    public void delete(NotebookInfo nb){
        if(nb==null) return;
        String targetName = nb.getName();
        notebooks.removeIf(n -> n.getName().equalsIgnoreCase(targetName));
        save();
        // delete files on disk
        deleteRec(nb.getFolder());
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
        if(nb.getFolder().renameTo(newFolder)){
            NotebookInfo updated = new NotebookInfo(newName, nb.getType(), newFolder, nb.getCreatedMillis(), nb.getIconId());
            notebooks.remove(nb);
            notebooks.add(updated);
            save();
            return true;
        }
        return false;
    }

    private static void deleteRec(File f){
        if(f==null || !f.exists()) return;
        if(f.isDirectory()){
            for(File ch: Objects.requireNonNull(f.listFiles())){
                deleteRec(ch);
            }
        }
        f.delete();
    }

    /** Reload notebooks from file system */
    public void reload(){ load(); }
} 