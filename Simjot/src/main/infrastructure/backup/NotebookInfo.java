package main.infrastructure.backup;

import java.io.File;

/**
 * Simple value object describing a user notebook.
 */
public class NotebookInfo {
    public enum Type { JOURNAL, POETRY }

    private final String name;
    private final Type type;
    private final File folder;
    private final long createdMillis;
    private final String iconId;

    public NotebookInfo(String name, Type type, File folder, long createdMillis, String iconId) {
        this.name = name;
        this.type = type;
        this.folder = folder;
        this.createdMillis = createdMillis;
        this.iconId = iconId==null?"legacy":iconId;
    }

    public String getName() { return name; }
    public Type getType()   { return type; }
    public File getFolder() { return folder; }
    public long getCreatedMillis() { return createdMillis; }
    public String getIconId(){ return iconId; }

    @Override public String toString(){ return name; }
} 