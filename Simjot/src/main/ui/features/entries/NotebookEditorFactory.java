package main.ui.features.entries;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.awt.CardLayout;
import javax.swing.JPanel;
import main.infrastructure.io.AppDirectories;
import main.ui.app.JournalApp;

/**
 * Factory + lightweight DI registry for creating NotebookEditor instances.
 *
 * Usage:
 *   NotebookEditorFactory f = new NotebookEditorFactory(app, journalFolder, layout, cards);
 *   NotebookEditor editor = f.create(NotebookEditorType.ENTRY);
 *   NotebookEditor editor2 = f.createForFile(someFile); // resolves by extension and loads it
 */
public class NotebookEditorFactory {
    private final JournalApp app;
    private final CardLayout cardLayout;
    private final JPanel cardPanel;

    private final Map<String, Supplier<NotebookEditor>> byExtension = new HashMap<>();

    public NotebookEditorFactory(JournalApp app, CardLayout cardLayout, JPanel cardPanel) {
        this.app = Objects.requireNonNull(app);
        this.cardLayout = Objects.requireNonNull(cardLayout);
        this.cardPanel = Objects.requireNonNull(cardPanel);
        registerDefaults();
    }

    private void registerDefaults() {
        // Ensure keys include leading dot and are lowercase
        register(".entry", () -> new EntryPanel(app, AppDirectories.folder(AppDirectories.Type.ENTRIES), cardLayout, cardPanel));
        register(".note", () -> new EntryPanel(app, AppDirectories.folder(AppDirectories.Type.ENTRIES), cardLayout, cardPanel));
        register(".txt", () -> new EntryPanel(app, AppDirectories.folder(AppDirectories.Type.ENTRIES), cardLayout, cardPanel));
        register(".md", () -> new EntryPanel(app, AppDirectories.folder(AppDirectories.Type.ENTRIES), cardLayout, cardPanel));
        register(".rtf", () -> new EntryPanel(app, AppDirectories.folder(AppDirectories.Type.ENTRIES), cardLayout, cardPanel));
        register(".poem", () -> new PoemPanel(app, AppDirectories.folder(AppDirectories.Type.POEMS), cardLayout, cardPanel));
    }

    public void register(String extension, Supplier<NotebookEditor> provider) {
        if (extension == null || extension.isEmpty()) throw new IllegalArgumentException("extension is empty");
        String key = normalizeExt(extension);
        byExtension.put(key, Objects.requireNonNull(provider));
    }

    public Optional<Supplier<NotebookEditor>> getProviderForExtension(String extension) {
        if (extension == null || extension.isEmpty()) return Optional.empty();
        return Optional.ofNullable(byExtension.get(normalizeExt(extension)));
    }

    /**
     * Returns the set of registered file extensions (normalized, lowercase, leading dot).
     */
    public java.util.Set<String> getRegisteredExtensions() {
        return java.util.Collections.unmodifiableSet(byExtension.keySet());
    }

    public NotebookEditor create(NotebookEditorType type) {
        Objects.requireNonNull(type, "type");
        return switch (type) {
            case ENTRY -> new EntryPanel(app, AppDirectories.folder(AppDirectories.Type.ENTRIES), cardLayout, cardPanel);
            case POEM -> new PoemPanel(app, AppDirectories.folder(AppDirectories.Type.POEMS), cardLayout, cardPanel);
        };
    }

    /**
     * Create an editor for a specific target folder. Use this when creating a new entry
     * inside a particular notebook so that first-save writes into that notebook.
     */
    public NotebookEditor createInFolder(NotebookEditorType type, File targetFolder) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(targetFolder, "targetFolder");
        return switch (type) {
            case ENTRY -> new EntryPanel(app, targetFolder, cardLayout, cardPanel);
            case POEM -> new PoemPanel(app, targetFolder, cardLayout, cardPanel);
        };
    }

    /**
     * Create editor for a file based on its extension and load it.
     * Returns a ready-to-use editor with file contents applied.
     */
    public NotebookEditor createForFile(File file) {
        Objects.requireNonNull(file, "file");
        String ext = extractExtension(file.getName()).toLowerCase(Locale.ROOT);
        // IMPORTANT: Use the actual parent folder of the file as the target notebook folder
        // so that the editor's back button returns to the correct entries manager.
        File parentFolder = file.getParentFile();
        if (parentFolder == null) parentFolder = AppDirectories.folder(AppDirectories.Type.ENTRIES);

        NotebookEditor editor;
        if (".poem".equals(ext)) {
            editor = createInFolder(NotebookEditorType.POEM, parentFolder);
        } else {
            // Treat all non-.poem text-like extensions as journal entries
            editor = createInFolder(NotebookEditorType.ENTRY, parentFolder);
        }
        // Load the file contents after constructing the editor in the proper notebook context
        editor.loadFile(file);
        return editor;
    }

    private static String extractExtension(String name) {
        int idx = name.lastIndexOf('.');
        if (idx < 0) return "";
        return name.substring(idx);
    }

    private static String normalizeExt(String ext) {
        String e = ext.startsWith(".") ? ext : "." + ext;
        return e.toLowerCase(Locale.ROOT);
    }
}
