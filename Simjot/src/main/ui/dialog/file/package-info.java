/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * <h1>Simjot File Chooser Package</h1>
 * 
 * <p>Custom file selection dialogs built from scratch for Simjot, providing
 * a modern, native-feeling experience without relying on Swing's JFileChooser.</p>
 * 
 * <h2>Components</h2>
 * <ul>
 *   <li><b>SimjotFileChooser</b> - Main file/folder selection dialog</li>
 *   <li><b>FileIconProvider</b> - System and custom file icons with caching</li>
 *   <li><b>FileTypeDetector</b> - MIME type detection via magic bytes and extensions</li>
 *   <li><b>QuickAccessManager</b> - Bookmarks and quick access locations</li>
 *   <li><b>FilePreviewProvider</b> - Async thumbnail and text preview generation</li>
 *   <li><b>RecentFilesManager</b> - Recent files/directories history with persistence</li>
 * </ul>
 * 
 * <h2>Features</h2>
 * <ul>
 *   <li>Modern frosted glass UI matching Simjot's aesthetic</li>
 *   <li>Quick access sidebar (Home, Desktop, Documents, Downloads)</li>
 *   <li>Breadcrumb navigation with clickable path components</li>
 *   <li>Back/forward/up navigation with history</li>
 *   <li>Real-time search filtering</li>
 *   <li>File type filtering by extension</li>
 *   <li>Hidden files toggle</li>
 *   <li>File preview panel with metadata</li>
 *   <li>Full keyboard navigation (Enter, Backspace, Escape)</li>
 *   <li>Multi-selection support</li>
 *   <li>Async file loading for responsive UI</li>
 * </ul>
 * 
 * <h2>Usage Examples</h2>
 * 
 * <h3>Open a File</h3>
 * <pre>{@code
 * SimjotFileChooser chooser = new SimjotFileChooser(parentWindow);
 * chooser.setMode(SimjotFileChooser.Mode.OPEN);
 * chooser.addFileFilter("Text Files", "txt", "md", "rtf");
 * chooser.addFileFilter("Images", "jpg", "jpeg", "png", "gif");
 * 
 * File selected = chooser.showDialog();
 * if (selected != null) {
 *     // User selected a file
 *     openFile(selected);
 * }
 * }</pre>
 * 
 * <h3>Save a File</h3>
 * <pre>{@code
 * SimjotFileChooser chooser = new SimjotFileChooser(parentWindow, "Save Entry");
 * chooser.setMode(SimjotFileChooser.Mode.SAVE);
 * chooser.setSuggestedFileName("my-entry.txt");
 * chooser.setCurrentDirectory(defaultSaveLocation);
 * 
 * File destination = chooser.showDialog();
 * if (destination != null) {
 *     saveToFile(destination);
 * }
 * }</pre>
 * 
 * <h3>Select a Folder</h3>
 * <pre>{@code
 * SimjotFileChooser chooser = new SimjotFileChooser(parentWindow, "Select Backup Folder");
 * chooser.setMode(SimjotFileChooser.Mode.DIRECTORY);
 * 
 * File folder = chooser.showDialog();
 * if (folder != null) {
 *     setBackupDirectory(folder);
 * }
 * }</pre>
 * 
 * <h3>Multi-Selection</h3>
 * <pre>{@code
 * SimjotFileChooser chooser = new SimjotFileChooser(parentWindow, "Select Files to Import");
 * chooser.setMode(SimjotFileChooser.Mode.OPEN);
 * chooser.addFileFilter("Journal Entries", "sjentry", "txt");
 * 
 * List<File> files = chooser.showMultiDialog();
 * for (File file : files) {
 *     importEntry(file);
 * }
 * }</pre>
 * 
 * <h2>Keyboard Shortcuts</h2>
 * <table border="1">
 *   <tr><th>Key</th><th>Action</th></tr>
 *   <tr><td>Enter</td><td>Open selected file/folder or confirm</td></tr>
 *   <tr><td>Backspace</td><td>Navigate to parent directory</td></tr>
 *   <tr><td>Escape</td><td>Cancel and close dialog</td></tr>
 *   <tr><td>Up/Down</td><td>Navigate file list</td></tr>
 * </table>
 * 
 * <h2>Comparison with JFileChooser</h2>
 * <table border="1">
 *   <tr><th>Feature</th><th>SimjotFileChooser</th><th>JFileChooser</th></tr>
 *   <tr><td>Visual Style</td><td>Modern, matches Simjot</td><td>System L&F dependent</td></tr>
 *   <tr><td>Breadcrumb Nav</td><td>Yes</td><td>No</td></tr>
 *   <tr><td>Quick Access</td><td>Built-in sidebar</td><td>Limited</td></tr>
 *   <tr><td>Search</td><td>Real-time filtering</td><td>No</td></tr>
 *   <tr><td>Preview</td><td>File metadata panel</td><td>Accessory panel API</td></tr>
 *   <tr><td>Async Loading</td><td>Yes</td><td>No</td></tr>
 *   <tr><td>Hidden Files</td><td>Toggle button</td><td>Hidden by default</td></tr>
 * </table>
 * 
 * @since 1.0
 * @see SimjotFileChooser
 */
package main.ui.dialog.file;
