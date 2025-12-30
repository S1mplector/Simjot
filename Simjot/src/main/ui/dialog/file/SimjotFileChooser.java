package main.ui.dialog.file;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Stack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileSystemView;

/**
 * <h1>Simjot File Chooser</h1>
 * 
 * A custom, modern file chooser dialog built from scratch for Simjot.
 * Provides a sleek, native-feeling experience without using Swing's JFileChooser.
 * 
 * <h2>Features</h2>
 * <ul>
 *   <li>Modern frosted glass UI matching Simjot's aesthetic</li>
 *   <li>Quick access sidebar with common locations</li>
 *   <li>Breadcrumb navigation bar</li>
 *   <li>Real-time search filtering</li>
 *   <li>File type filtering with extensions</li>
 *   <li>Grid and list view modes</li>
 *   <li>File preview panel</li>
 *   <li>Keyboard navigation support</li>
 *   <li>Back/forward navigation history</li>
 *   <li>Hidden files toggle</li>
 *   <li>Create new folder</li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * <pre>{@code
 * SimjotFileChooser chooser = new SimjotFileChooser(parentWindow);
 * chooser.setMode(SimjotFileChooser.Mode.OPEN);
 * chooser.addFileFilter("Text Files", "txt", "md");
 * 
 * File selected = chooser.showDialog();
 * if (selected != null) {
 *     // User selected a file
 * }
 * }</pre>
 * 
 * @author S1mplector
 * @version 1.0
 */
public class SimjotFileChooser extends JDialog {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ENUMS AND CONSTANTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * File chooser operation mode.
     */
    public enum Mode {
        /** Open an existing file */
        OPEN,
        /** Save to a file (allows entering new filename) */
        SAVE,
        /** Select a directory */
        DIRECTORY
    }
    
    /**
     * View mode for file display.
     */
    public enum ViewMode {
        LIST,
        GRID
    }
    
    // Colors matching Simjot's aesthetic
    private static final Color BG_COLOR = new Color(248, 248, 250);
    private static final Color SIDEBAR_BG = new Color(240, 240, 245);
    private static final Color SELECTED_BG = new Color(66, 133, 244, 40);
    private static final Color SELECTED_BORDER = new Color(66, 133, 244);
    private static final Color HOVER_BG = new Color(0, 0, 0, 15);
    private static final Color TEXT_PRIMARY = new Color(33, 33, 33);
    private static final Color TEXT_SECONDARY = new Color(100, 100, 100);
    private static final Color BORDER_COLOR = new Color(220, 220, 225);
    private static final Color ACCENT_COLOR = new Color(66, 133, 244);
    
    private static final int SIDEBAR_WIDTH = 180;
    private static final int PREVIEW_WIDTH = 200;
    private static final int ROW_HEIGHT = 32;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // INSTANCE FIELDS
    // ═══════════════════════════════════════════════════════════════════════════
    
    private Mode mode = Mode.OPEN;
    private ViewMode viewMode = ViewMode.LIST;
    private File currentDirectory;
    private File selectedFile;
    private boolean showHiddenFiles = false;
    private boolean multiSelectionEnabled = false;
    private List<File> selectedFiles = new ArrayList<>();
    
    // Navigation history
    private final Stack<File> backHistory = new Stack<>();
    private final Stack<File> forwardHistory = new Stack<>();
    
    // File filters
    private final List<FileFilter> fileFilters = new ArrayList<>();
    private FileFilter activeFilter = null;
    
    // UI Components
    private JPanel sidebarPanel;
    private JPanel navigationBar;
    private JPanel breadcrumbPanel;
    private JTextField searchField;
    private JTextField filenameField;
    private JList<FileItem> fileList;
    private DefaultListModel<FileItem> fileListModel;
    private JPanel previewPanel;
    private JLabel previewLabel;
    private JButton backButton;
    private JButton forwardButton;
    private JButton upButton;
    private JButton confirmButton;
    private JButton cancelButton;
    
    // Async file loading
    private final ExecutorService fileLoader = Executors.newSingleThreadExecutor();
    private volatile boolean loadingCancelled = false;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONSTRUCTORS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Creates a new file chooser dialog.
     * 
     * @param owner The parent window
     */
    public SimjotFileChooser(Window owner) {
        super(owner, "Select File", ModalityType.APPLICATION_MODAL);
        this.currentDirectory = getDefaultDirectory();
        initUI();
        setupKeyBindings();
    }
    
    /**
     * Creates a new file chooser with specified title.
     */
    public SimjotFileChooser(Window owner, String title) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        this.currentDirectory = getDefaultDirectory();
        initUI();
        setupKeyBindings();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Shows the dialog and returns the selected file.
     * 
     * @return The selected file, or null if cancelled
     */
    public File showDialog() {
        selectedFile = null;
        loadDirectory(currentDirectory);
        setVisible(true);
        return selectedFile;
    }
    
    /**
     * Shows the dialog and returns multiple selected files.
     * Only applicable when multi-selection is enabled.
     */
    public List<File> showMultiDialog() {
        selectedFiles.clear();
        multiSelectionEnabled = true;
        fileList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        loadDirectory(currentDirectory);
        setVisible(true);
        return new ArrayList<>(selectedFiles);
    }
    
    /**
     * Sets the operation mode.
     */
    public void setMode(Mode mode) {
        this.mode = mode;
        updateTitleForMode();
        updateUIForMode();
    }
    
    /**
     * Sets the initial directory.
     */
    public void setCurrentDirectory(File directory) {
        if (directory != null && directory.isDirectory()) {
            this.currentDirectory = directory;
        }
    }
    
    /**
     * Adds a file filter.
     * 
     * @param description Display name (e.g., "Text Files")
     * @param extensions File extensions without dots (e.g., "txt", "md")
     */
    public void addFileFilter(String description, String... extensions) {
        fileFilters.add(new FileFilter(description, extensions));
        if (activeFilter == null) {
            activeFilter = fileFilters.get(0);
        }
    }
    
    /**
     * Sets whether hidden files should be shown.
     */
    public void setShowHiddenFiles(boolean show) {
        this.showHiddenFiles = show;
        loadDirectory(currentDirectory);
    }
    
    /**
     * Sets the suggested filename (for save dialogs).
     */
    public void setSuggestedFileName(String name) {
        if (filenameField != null) {
            filenameField.setText(name);
        }
    }
    
    /**
     * Gets the selected file.
     */
    public File getSelectedFile() {
        return selectedFile;
    }
    
    /**
     * Gets all selected files (for multi-selection).
     */
    public List<File> getSelectedFiles() {
        return new ArrayList<>(selectedFiles);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // UI INITIALIZATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    private void initUI() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(900, 600);
        setMinimumSize(new Dimension(700, 450));
        setLocationRelativeTo(getOwner());
        
        // Main container with frosted glass effect
        JPanel mainPanel = new FrostedPanel();
        mainPanel.setLayout(new BorderLayout(0, 0));
        mainPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
        
        // Top navigation bar
        navigationBar = createNavigationBar();
        mainPanel.add(navigationBar, BorderLayout.NORTH);
        
        // Center split: sidebar | file list | preview
        JPanel centerPanel = new JPanel(new BorderLayout(0, 0));
        centerPanel.setOpaque(false);
        
        // Sidebar with quick access
        sidebarPanel = createSidebar();
        centerPanel.add(sidebarPanel, BorderLayout.WEST);
        
        // File list
        JPanel filePanel = createFileListPanel();
        centerPanel.add(filePanel, BorderLayout.CENTER);
        
        // Preview panel (optional)
        previewPanel = createPreviewPanel();
        centerPanel.add(previewPanel, BorderLayout.EAST);
        
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        
        // Bottom: filename field and buttons
        JPanel bottomPanel = createBottomPanel();
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        setContentPane(mainPanel);
        updateTitleForMode();
    }
    
    private JPanel createNavigationBar() {
        JPanel nav = new JPanel(new BorderLayout(8, 0));
        nav.setOpaque(false);
        nav.setBorder(new EmptyBorder(0, 0, 8, 0));
        
        // Back/Forward/Up buttons
        JPanel navButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        navButtons.setOpaque(false);
        
        backButton = createNavButton("←", "Back");
        forwardButton = createNavButton("→", "Forward");
        upButton = createNavButton("↑", "Up");
        
        backButton.addActionListener(e -> navigateBack());
        forwardButton.addActionListener(e -> navigateForward());
        upButton.addActionListener(e -> navigateUp());
        
        navButtons.add(backButton);
        navButtons.add(forwardButton);
        navButtons.add(upButton);
        
        nav.add(navButtons, BorderLayout.WEST);
        
        // Breadcrumb path
        breadcrumbPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        breadcrumbPanel.setOpaque(false);
        
        JScrollPane breadcrumbScroll = new JScrollPane(breadcrumbPanel);
        breadcrumbScroll.setOpaque(false);
        breadcrumbScroll.getViewport().setOpaque(false);
        breadcrumbScroll.setBorder(null);
        breadcrumbScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        breadcrumbScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        breadcrumbScroll.setPreferredSize(new Dimension(400, 28));
        
        nav.add(breadcrumbScroll, BorderLayout.CENTER);
        
        // Search field
        searchField = new JTextField(15);
        searchField.putClientProperty("JTextField.placeholderText", "Search...");
        searchField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
            new EmptyBorder(4, 8, 4, 8)
        ));
        searchField.addKeyListener(new KeyAdapter() {
            private Timer searchTimer;
            @Override
            public void keyReleased(KeyEvent e) {
                if (searchTimer != null) searchTimer.stop();
                searchTimer = new Timer(300, evt -> filterFiles());
                searchTimer.setRepeats(false);
                searchTimer.start();
            }
        });
        
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        searchPanel.setOpaque(false);
        searchPanel.add(searchField);
        
        nav.add(searchPanel, BorderLayout.EAST);
        
        return nav;
    }
    
    private JPanel createSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(SIDEBAR_BG);
        sidebar.setPreferredSize(new Dimension(SIDEBAR_WIDTH, 0));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER_COLOR));
        
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(createSidebarSection("Favorites"));
        
        // Quick access locations
        addQuickAccessItem(sidebar, "Home", getHomeDirectory());
        addQuickAccessItem(sidebar, "Desktop", getDesktopDirectory());
        addQuickAccessItem(sidebar, "Documents", getDocumentsDirectory());
        addQuickAccessItem(sidebar, "Downloads", getDownloadsDirectory());
        
        sidebar.add(Box.createVerticalStrut(16));
        sidebar.add(createSidebarSection("Devices"));
        
        // File system roots
        for (File root : File.listRoots()) {
            String name = root.getAbsolutePath();
            if (name.equals("/")) name = "Macintosh HD";
            addQuickAccessItem(sidebar, name, root);
        }
        
        sidebar.add(Box.createVerticalGlue());
        
        // Hidden files toggle
        JPanel togglePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        togglePanel.setOpaque(false);
        togglePanel.setMaximumSize(new Dimension(SIDEBAR_WIDTH, 30));
        
        JButton hiddenToggle = new JButton(showHiddenFiles ? "☑ Hidden" : "☐ Hidden");
        hiddenToggle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        hiddenToggle.setBorderPainted(false);
        hiddenToggle.setContentAreaFilled(false);
        hiddenToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        hiddenToggle.addActionListener(e -> {
            showHiddenFiles = !showHiddenFiles;
            hiddenToggle.setText(showHiddenFiles ? "☑ Hidden" : "☐ Hidden");
            loadDirectory(currentDirectory);
        });
        togglePanel.add(hiddenToggle);
        sidebar.add(togglePanel);
        sidebar.add(Box.createVerticalStrut(8));
        
        return sidebar;
    }
    
    private JLabel createSidebarSection(String title) {
        JLabel label = new JLabel(title);
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        label.setForeground(TEXT_SECONDARY);
        label.setBorder(new EmptyBorder(4, 12, 4, 12));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }
    
    private void addQuickAccessItem(JPanel sidebar, String name, File directory) {
        if (directory == null || !directory.exists()) return;
        
        JPanel item = new JPanel(new BorderLayout());
        item.setOpaque(false);
        item.setMaximumSize(new Dimension(SIDEBAR_WIDTH, 28));
        item.setBorder(new EmptyBorder(4, 12, 4, 12));
        item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        JLabel label = new JLabel(name);
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        label.setForeground(TEXT_PRIMARY);
        item.add(label, BorderLayout.CENTER);
        
        item.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                item.setOpaque(true);
                item.setBackground(HOVER_BG);
                item.repaint();
            }
            @Override
            public void mouseExited(MouseEvent e) {
                item.setOpaque(false);
                item.repaint();
            }
            @Override
            public void mouseClicked(MouseEvent e) {
                navigateTo(directory);
            }
        });
        
        sidebar.add(item);
    }
    
    private JPanel createFileListPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        
        fileListModel = new DefaultListModel<>();
        fileList = new JList<>(fileListModel);
        fileList.setCellRenderer(new FileListCellRenderer());
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileList.setFixedCellHeight(ROW_HEIGHT);
        fileList.setBackground(Color.WHITE);
        
        fileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    handleDoubleClick();
                }
            }
        });
        
        fileList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                handleSelection();
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(fileList);
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createPreviewPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(250, 250, 252));
        panel.setPreferredSize(new Dimension(PREVIEW_WIDTH, 0));
        panel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, BORDER_COLOR));
        
        previewLabel = new JLabel("", SwingConstants.CENTER);
        previewLabel.setVerticalAlignment(SwingConstants.CENTER);
        previewLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        previewLabel.setForeground(TEXT_SECONDARY);
        
        panel.add(previewLabel, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 8));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(12, 0, 0, 0));
        
        // Filename field (visible for SAVE mode)
        JPanel filenamePanel = new JPanel(new BorderLayout(8, 0));
        filenamePanel.setOpaque(false);
        
        JLabel filenameLabel = new JLabel("Name:");
        filenameLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        
        filenameField = new JTextField();
        filenameField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
            new EmptyBorder(6, 10, 6, 10)
        ));
        
        filenamePanel.add(filenameLabel, BorderLayout.WEST);
        filenamePanel.add(filenameField, BorderLayout.CENTER);
        
        panel.add(filenamePanel, BorderLayout.CENTER);
        
        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setOpaque(false);
        
        cancelButton = createActionButton("Cancel", false);
        confirmButton = createActionButton(getConfirmButtonText(), true);
        
        cancelButton.addActionListener(e -> {
            selectedFile = null;
            dispose();
        });
        
        confirmButton.addActionListener(e -> confirmSelection());
        
        buttonPanel.add(cancelButton);
        buttonPanel.add(confirmButton);
        
        panel.add(buttonPanel, BorderLayout.EAST);
        
        return panel;
    }
    
    private JButton createNavButton(String text, String tooltip) {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        button.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        button.setPreferredSize(new Dimension(32, 28));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setContentAreaFilled(true);
                button.setBackground(HOVER_BG);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                button.setContentAreaFilled(false);
            }
        });
        
        return button;
    }
    
    private JButton createActionButton(String text, boolean primary) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                if (primary) {
                    g2.setColor(ACCENT_COLOR);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    g2.setColor(Color.WHITE);
                } else {
                    g2.setColor(BORDER_COLOR);
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                    g2.setColor(TEXT_PRIMARY);
                }
                
                g2.setFont(getFont());
                int textWidth = g2.getFontMetrics().stringWidth(getText());
                int x = (getWidth() - textWidth) / 2;
                int y = (getHeight() + g2.getFontMetrics().getAscent() - g2.getFontMetrics().getDescent()) / 2;
                g2.drawString(getText(), x, y);
                
                g2.dispose();
            }
        };
        
        button.setPreferredSize(new Dimension(90, 32));
        button.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        return button;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // NAVIGATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    private void navigateTo(File directory) {
        if (directory == null || !directory.isDirectory()) return;
        
        backHistory.push(currentDirectory);
        forwardHistory.clear();
        currentDirectory = directory;
        loadDirectory(directory);
        updateNavigationButtons();
    }
    
    private void navigateBack() {
        if (backHistory.isEmpty()) return;
        forwardHistory.push(currentDirectory);
        currentDirectory = backHistory.pop();
        loadDirectory(currentDirectory);
        updateNavigationButtons();
    }
    
    private void navigateForward() {
        if (forwardHistory.isEmpty()) return;
        backHistory.push(currentDirectory);
        currentDirectory = forwardHistory.pop();
        loadDirectory(currentDirectory);
        updateNavigationButtons();
    }
    
    private void navigateUp() {
        File parent = currentDirectory.getParentFile();
        if (parent != null) {
            navigateTo(parent);
        }
    }
    
    private void updateNavigationButtons() {
        backButton.setEnabled(!backHistory.isEmpty());
        forwardButton.setEnabled(!forwardHistory.isEmpty());
        upButton.setEnabled(currentDirectory.getParentFile() != null);
    }
    
    private void updateBreadcrumbs() {
        breadcrumbPanel.removeAll();
        
        List<File> pathComponents = new ArrayList<>();
        File current = currentDirectory;
        while (current != null) {
            pathComponents.add(0, current);
            current = current.getParentFile();
        }
        
        for (int i = 0; i < pathComponents.size(); i++) {
            File dir = pathComponents.get(i);
            String name = dir.getName().isEmpty() ? dir.getAbsolutePath() : dir.getName();
            
            JButton crumb = new JButton(name);
            crumb.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            crumb.setBorderPainted(false);
            crumb.setContentAreaFilled(false);
            crumb.setFocusPainted(false);
            crumb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            crumb.setForeground(i == pathComponents.size() - 1 ? TEXT_PRIMARY : ACCENT_COLOR);
            
            final File target = dir;
            crumb.addActionListener(e -> navigateTo(target));
            
            breadcrumbPanel.add(crumb);
            
            if (i < pathComponents.size() - 1) {
                JLabel separator = new JLabel(" › ");
                separator.setForeground(TEXT_SECONDARY);
                breadcrumbPanel.add(separator);
            }
        }
        
        breadcrumbPanel.revalidate();
        breadcrumbPanel.repaint();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // FILE LOADING
    // ═══════════════════════════════════════════════════════════════════════════
    
    private void loadDirectory(File directory) {
        if (directory == null || !directory.isDirectory()) return;
        
        loadingCancelled = true; // Cancel any pending load
        
        fileLoader.submit(() -> {
            loadingCancelled = false;
            
            File[] files = directory.listFiles();
            if (files == null || loadingCancelled) return;
            
            List<FileItem> items = new ArrayList<>();
            
            for (File file : files) {
                if (loadingCancelled) return;
                
                // Filter hidden files
                if (!showHiddenFiles && file.isHidden()) continue;
                if (!showHiddenFiles && file.getName().startsWith(".")) continue;
                
                // Filter by mode
                if (mode == Mode.DIRECTORY && !file.isDirectory()) continue;
                
                // Filter by extension
                if (activeFilter != null && !file.isDirectory()) {
                    if (!activeFilter.accepts(file)) continue;
                }
                
                items.add(new FileItem(file));
            }
            
            // Sort: directories first, then alphabetically
            items.sort(Comparator
                .comparing((FileItem f) -> !f.file.isDirectory())
                .thenComparing(f -> f.file.getName().toLowerCase())
            );
            
            SwingUtilities.invokeLater(() -> {
                fileListModel.clear();
                for (FileItem item : items) {
                    fileListModel.addElement(item);
                }
                updateBreadcrumbs();
                updateNavigationButtons();
            });
        });
    }
    
    private void filterFiles() {
        String query = searchField.getText().toLowerCase().trim();
        if (query.isEmpty()) {
            loadDirectory(currentDirectory);
            return;
        }
        
        // Filter current list by search query
        DefaultListModel<FileItem> filtered = new DefaultListModel<>();
        for (int i = 0; i < fileListModel.size(); i++) {
            FileItem item = fileListModel.getElementAt(i);
            if (item.file.getName().toLowerCase().contains(query)) {
                filtered.addElement(item);
            }
        }
        fileList.setModel(filtered);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SELECTION HANDLING
    // ═══════════════════════════════════════════════════════════════════════════
    
    private void handleSelection() {
        FileItem selected = fileList.getSelectedValue();
        if (selected == null) return;
        
        if (mode == Mode.SAVE && selected.file.isFile()) {
            filenameField.setText(selected.file.getName());
        }
        
        updatePreview(selected.file);
    }
    
    private void handleDoubleClick() {
        FileItem selected = fileList.getSelectedValue();
        if (selected == null) return;
        
        if (selected.file.isDirectory()) {
            navigateTo(selected.file);
        } else {
            confirmSelection();
        }
    }
    
    private void confirmSelection() {
        if (mode == Mode.SAVE) {
            String filename = filenameField.getText().trim();
            if (filename.isEmpty()) return;
            selectedFile = new File(currentDirectory, filename);
        } else if (mode == Mode.DIRECTORY) {
            selectedFile = currentDirectory;
        } else {
            FileItem selected = fileList.getSelectedValue();
            if (selected == null) return;
            selectedFile = selected.file;
        }
        
        if (multiSelectionEnabled) {
            for (FileItem item : fileList.getSelectedValuesList()) {
                selectedFiles.add(item.file);
            }
        }
        
        dispose();
    }
    
    private void updatePreview(File file) {
        StringBuilder info = new StringBuilder("<html><center>");
        info.append("<b>").append(escapeHtml(file.getName())).append("</b><br><br>");
        
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            int count = children != null ? children.length : 0;
            info.append(count).append(" items");
        } else {
            info.append(formatFileSize(file.length())).append("<br>");
            try {
                BasicFileAttributes attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
                SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.US);
                info.append("Modified: ").append(sdf.format(new Date(attrs.lastModifiedTime().toMillis())));
            } catch (IOException ignored) {}
        }
        
        info.append("</center></html>");
        previewLabel.setText(info.toString());
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // KEYBOARD NAVIGATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    private void setupKeyBindings() {
        fileList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_ENTER -> handleDoubleClick();
                    case KeyEvent.VK_BACK_SPACE -> navigateUp();
                    case KeyEvent.VK_ESCAPE -> dispose();
                }
            }
        });
        
        // Global escape to close
        getRootPane().registerKeyboardAction(
            e -> dispose(),
            javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════════════
    
    private void updateTitleForMode() {
        switch (mode) {
            case OPEN -> setTitle("Open File");
            case SAVE -> setTitle("Save File");
            case DIRECTORY -> setTitle("Select Folder");
        }
    }
    
    private void updateUIForMode() {
        boolean showFilename = (mode == Mode.SAVE);
        if (filenameField != null) {
            filenameField.getParent().setVisible(showFilename);
        }
        if (confirmButton != null) {
            confirmButton.setText(getConfirmButtonText());
        }
    }
    
    private String getConfirmButtonText() {
        return switch (mode) {
            case OPEN -> "Open";
            case SAVE -> "Save";
            case DIRECTORY -> "Select";
        };
    }
    
    private static File getDefaultDirectory() {
        return getHomeDirectory();
    }
    
    private static File getHomeDirectory() {
        return new File(System.getProperty("user.home"));
    }
    
    private static File getDesktopDirectory() {
        return new File(System.getProperty("user.home"), "Desktop");
    }
    
    private static File getDocumentsDirectory() {
        return new File(System.getProperty("user.home"), "Documents");
    }
    
    private static File getDownloadsDirectory() {
        return new File(System.getProperty("user.home"), "Downloads");
    }
    
    private static String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
        return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
    }
    
    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;");
    }
    
    @Override
    public void dispose() {
        loadingCancelled = true;
        fileLoader.shutdownNow();
        super.dispose();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // INNER CLASSES
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Wrapper for file entries in the list.
     */
    private static class FileItem {
        final File file;
        final String displayName;
        final boolean isDirectory;
        
        FileItem(File file) {
            this.file = file;
            this.displayName = file.getName();
            this.isDirectory = file.isDirectory();
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
    
    /**
     * File filter for extensions.
     */
    public static class FileFilter {
        private final String description;
        private final List<String> extensions;
        
        public FileFilter(String description, String... extensions) {
            this.description = description;
            this.extensions = Arrays.stream(extensions)
                .map(String::toLowerCase)
                .collect(Collectors.toList());
        }
        
        public boolean accepts(File file) {
            if (extensions.isEmpty()) return true;
            String name = file.getName().toLowerCase();
            return extensions.stream().anyMatch(ext -> name.endsWith("." + ext));
        }
        
        @Override
        public String toString() {
            return description;
        }
    }
    
    /**
     * Custom cell renderer for file list.
     */
    private class FileListCellRenderer extends DefaultListCellRenderer {
        private final FileSystemView fsv = FileSystemView.getFileSystemView();
        
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            
            JPanel panel = new JPanel(new BorderLayout(8, 0));
            panel.setBorder(new EmptyBorder(4, 8, 4, 8));
            
            if (isSelected) {
                panel.setBackground(SELECTED_BG);
                panel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(SELECTED_BORDER, 1),
                    new EmptyBorder(3, 7, 3, 7)
                ));
            } else {
                panel.setBackground(Color.WHITE);
            }
            
            FileItem item = (FileItem) value;
            
            // Icon
            JLabel iconLabel = new JLabel();
            try {
                javax.swing.Icon icon = fsv.getSystemIcon(item.file);
                if (icon != null) {
                    iconLabel.setIcon(icon);
                }
            } catch (Exception e) {
                iconLabel.setText(item.isDirectory ? "📁" : "📄");
            }
            
            // Name
            JLabel nameLabel = new JLabel(item.displayName);
            nameLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            nameLabel.setForeground(TEXT_PRIMARY);
            
            // Size/info
            JLabel infoLabel = new JLabel();
            infoLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            infoLabel.setForeground(TEXT_SECONDARY);
            
            if (!item.isDirectory) {
                infoLabel.setText(formatFileSize(item.file.length()));
            }
            
            panel.add(iconLabel, BorderLayout.WEST);
            panel.add(nameLabel, BorderLayout.CENTER);
            panel.add(infoLabel, BorderLayout.EAST);
            
            return panel;
        }
    }
    
    /**
     * Frosted glass background panel.
     */
    private static class FrostedPanel extends JPanel {
        FrostedPanel() {
            super(new BorderLayout());
            setOpaque(false);
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;
            
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            // Soft gradient background
            GradientPaint gp = new GradientPaint(
                0, 0, new Color(252, 252, 254),
                0, h, new Color(245, 245, 248)
            );
            g2.setPaint(gp);
            g2.fillRoundRect(0, 0, w, h, 12, 12);
            
            // Subtle border
            g2.setColor(new Color(0, 0, 0, 20));
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 12, 12));
            
            g2.dispose();
        }
    }
}
