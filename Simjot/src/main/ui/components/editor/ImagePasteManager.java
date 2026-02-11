/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.editor;

import java.awt.AWTEvent;
import java.awt.AlphaComposite;
import java.awt.Component;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.JWindow;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import main.infrastructure.ffi.NativeAccess;
import main.infrastructure.io.FileIO;
import main.ui.components.buttons.ToolbarIconButton;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.slider.MoodSlider;
import main.ui.theme.aero.AeroTheme;

/**
 * ImagePasteManager - Overhauled for simplicity and stability.
 * 
 * Features:
 * - Ctrl/Cmd+V: paste images from clipboard
 * - Drag & drop image files
 * - URL image pasting
 * - Clean, minimal adjustment UI on click
 * - Double-buffered rendering to prevent flickering
 */
public final class ImagePasteManager {

    private ImagePasteManager() {}
    
    // Track active overlay to prevent duplicates
    private static JWindow activeOverlay = null;
    private static JWindow imageEditOverlay = null;
    private static JWindow sizePreviewOverlay = null;
    private static MouseAdapter activeMouseListener = null;
    private static java.awt.event.FocusAdapter activeFocusListener = null;
    private static AWTEventListener activeGlobalMouseListener = null;
    private static boolean globalMouseListenerInstalled = false;
    private static JTextPane activeEditor = null;
    
    // Native image cache settings
    private static final int CACHE_MAX_ENTRIES = 48;
    private static final int CACHE_MAX_MEMORY_MB = 96;
    private static final int POPUP_EDGE_MARGIN = 8;
    private static volatile boolean cacheInitialized = false;

    public static void install(JTextPane editor, Supplier<File> attachmentsDirSupplier, int maxWidthPx) {
        Objects.requireNonNull(editor, "editor");
        Objects.requireNonNull(attachmentsDirSupplier, "attachmentsDirSupplier");

        // Initialize native image cache (once, thread-safe)
        initNativeCache();

        // Enable double buffering for flicker-free rendering
        editor.setDoubleBuffered(true);

        // Wrap default paste action to handle images
        Action defaultPaste = editor.getActionMap().get(DefaultEditorKit.pasteAction);
        editor.getActionMap().put("imageAwarePaste", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (!tryPasteFromClipboard(editor, attachmentsDirSupplier, maxWidthPx)) {
                    if (defaultPaste != null) defaultPaste.actionPerformed(e);
                }
            }
        });
        
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        editor.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke('V', mask), "imageAwarePaste");

        // Install transfer handler for drag & drop
        editor.setTransferHandler(new ImageFileTransferHandler(editor, attachmentsDirSupplier, maxWidthPx, editor.getTransferHandler()));

        // Click handler for image adjustment (click-to-show is more reliable than hover)
        editor.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) {
                    handleImageClick(editor, e, attachmentsDirSupplier, maxWidthPx);
                }
            }
        });
    }
    
    private static void handleImageClick(JTextPane editor, MouseEvent e, 
                                         Supplier<File> attachmentsDirSupplier, int maxWidthPx) {
        try {
            int pos = editor.viewToModel2D(e.getPoint());
            if (pos < 0) pos = 0;
            StyledDocument doc = editor.getStyledDocument();
            int len = doc.getLength();
            
            // Search for icon element near click
            for (int d = 0; d <= 5; d++) {
                for (int sign : new int[]{0, -1, 1}) {
                    int idx = Math.max(0, Math.min(len, pos + sign * d));
                    javax.swing.text.Element el = doc.getCharacterElement(idx);
                    if (el == null) continue;
                    
                    javax.swing.text.AttributeSet as = el.getAttributes();
                    Object ico = StyleConstants.getIcon(as);
                    if (ico instanceof ImageIcon icon) {
                        Rectangle bounds = null;
                        try {
                            java.awt.geom.Rectangle2D r2 = editor.modelToView2D(el.getStartOffset());
                            if (r2 != null) {
                                bounds = r2.getBounds();
                                bounds.width = icon.getIconWidth();
                                bounds.height = icon.getIconHeight();
                            }
                        } catch (Throwable ignored) {}
                        
                        if (bounds == null) {
                            bounds = new Rectangle(e.getX(), e.getY(), icon.getIconWidth(), icon.getIconHeight());
                        }

                        // Only treat this as an image click if the pointer is within the image bounds.
                        if (bounds != null && !bounds.contains(e.getPoint())) {
                            continue;
                        }
                        
                        File srcFile = null;
                        Object src = as.getAttribute("imageSourceFile");
                        if (src instanceof File) srcFile = (File) src;
                        
                        showMinimalToolbarWithFade(editor, el.getStartOffset(), srcFile, icon, bounds, e.getPoint(),
                                          attachmentsDirSupplier, maxWidthPx);
                        return;
                    }
                }
            }

            // If the click wasn't on an image, dismiss any active overlay for this editor.
            if (activeEditor == editor && activeOverlay != null && activeOverlay.isVisible()) {
                try {
                    Point screenPoint = e.getLocationOnScreen();
                    if (!isMouseOverAnyOverlay(screenPoint)) {
                        fadeOutAndDismiss(activeOverlay);
                    }
                } catch (Throwable ignored) {
                    fadeOutAndDismiss(activeOverlay);
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void showMinimalToolbarWithFade(JTextPane editor,
                                                   int startOffset,
                                                   File sourceFile,
                                                   ImageIcon currentIcon,
                                                   Rectangle imageBounds,
                                                   Point clickPoint,
                                                   Supplier<File> attachmentsDirSupplier,
                                                   int defaultMaxWidth) {
        // Dismiss any existing overlay
        dismissActiveOverlay();

        int currentW = currentIcon.getIconWidth();
        int minW = 60;
        int maxW = Math.max(defaultMaxWidth * 2, Math.max(currentW * 2, editor.getWidth()));

        final File[] srcRef = new File[]{sourceFile};

        JWindow toolbar = new JWindow(SwingUtilities.getWindowAncestor(editor));
        toolbar.setBackground(Color.WHITE);
        toolbar.setAlwaysOnTop(true);
        activeOverlay = toolbar;

        JPanel content = new JPanel(new java.awt.BorderLayout(10, 0));
        content.setOpaque(true);
        content.setBackground(Color.WHITE);
        content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(202, 208, 216), 1),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));

        MoodSlider sizeSlider = new MoodSlider();
        sizeSlider.setMinimum(minW);
        sizeSlider.setMaximum(maxW);
        sizeSlider.setValue(currentW);
        sizeSlider.setPreferredSize(new Dimension(180, 28));
        sizeSlider.setFocusable(false);

        JLabel sizeLabel = new JLabel(currentW + "px");
        sizeLabel.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 12f));
        sizeLabel.setForeground(new Color(90, 95, 105));
        sizeLabel.setPreferredSize(new Dimension(45, 20));

        ToolbarIconButton deleteBtn = new ToolbarIconButton("delete");
        deleteBtn.setPreferredSize(new Dimension(30, 30));
        deleteBtn.setMinimumSize(new Dimension(30, 30));
        deleteBtn.setMaximumSize(new Dimension(30, 30));
        deleteBtn.setToolTipText("Delete image");

        // Track original dimensions for aspect ratio
        final int originalW = currentIcon.getIconWidth();
        final int originalH = currentIcon.getIconHeight();
        final float aspectRatio = originalH / (float) originalW;

        sizeSlider.addChangeListener(e -> {
            int newW = sizeSlider.getValue();
            int newH = Math.round(newW * aspectRatio);
            sizeLabel.setText(newW + "px");

            if (sizeSlider.getValueIsAdjusting()) {
                // Show live size preview overlay while dragging
                showSizePreviewOverlay(editor, imageBounds, newW, newH);
            } else {
                // User released - apply the resize and hide preview
                hideSizePreviewOverlay();
                resizeImage(editor, startOffset, srcRef, newW, attachmentsDirSupplier);
                repositionToolbar(toolbar, editor, startOffset);
            }
        });

        deleteBtn.addActionListener(e -> {
            try {
                StyledDocument doc = editor.getStyledDocument();
                doc.remove(startOffset, 1);
            } catch (BadLocationException ignored) {}
            fadeOutAndDismiss(toolbar);
        });


        JPanel sliderPanel = new JPanel(new java.awt.BorderLayout(6, 0));
        sliderPanel.setOpaque(false);
        sliderPanel.add(sizeSlider, java.awt.BorderLayout.CENTER);
        sliderPanel.add(sizeLabel, java.awt.BorderLayout.EAST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        actions.add(deleteBtn);
        content.add(sliderPanel, java.awt.BorderLayout.CENTER);
        content.add(actions, java.awt.BorderLayout.EAST);

        toolbar.setContentPane(content);
        toolbar.pack();

        positionToolbar(toolbar, editor, imageBounds, clickPoint);
        toolbar.setVisible(true);

        // Show "being edited" overlay on the image
        showImageEditOverlay(editor, imageBounds);

        // Track editor and remove any previous listeners
        activeEditor = editor;
        removeActiveListeners(editor);
        installGlobalMouseListener();

        // Auto-dismiss when clicking elsewhere.
        activeMouseListener = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                Point screenPoint = e.getLocationOnScreen();
                if (!isMouseOverAnyOverlay(screenPoint)) {
                    fadeOutAndDismiss(toolbar);
                }
            }
        };
        editor.addMouseListener(activeMouseListener);

        activeFocusListener = new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                Timer timer = new Timer(200, ev -> {
                    Window focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
                    if (focused != toolbar && focused != imageEditOverlay && focused != sizePreviewOverlay) {
                        fadeOutAndDismiss(toolbar);
                    }
                });
                timer.setRepeats(false);
                timer.start();
            }
        };
        editor.addFocusListener(activeFocusListener);
    }

    private static void fadeOutAndDismiss(JWindow toolbar) {
        dismissActiveOverlay();
    }

    /**
     * Shows a subtle "being edited" overlay on the image.
     */
    private static void showImageEditOverlay(JTextPane editor, Rectangle imageBounds) {
        hideImageEditOverlay();

        try {
            Rectangle visibleBounds = visibleImageBounds(editor, imageBounds);
            if (visibleBounds == null || visibleBounds.width <= 0 || visibleBounds.height <= 0) {
                return;
            }
            Point editorLoc = editor.getLocationOnScreen();
            int x = editorLoc.x + visibleBounds.x;
            int y = editorLoc.y + visibleBounds.y;

            JWindow overlay = new JWindow(SwingUtilities.getWindowAncestor(editor));
            overlay.setBackground(new Color(0, 0, 0, 0));
            overlay.setAlwaysOnTop(true);

            JPanel content = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    // Subtle blue tint overlay
                    g2.setColor(new Color(59, 130, 246, 35));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    // Border
                    g2.setColor(new Color(59, 130, 246, 120));
                    g2.setStroke(new java.awt.BasicStroke(2f));
                    g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 8, 8);
                    g2.dispose();
                }
            };
            content.setOpaque(false);
            content.setPreferredSize(new Dimension(visibleBounds.width, visibleBounds.height));

            overlay.setContentPane(content);
            overlay.pack();
            overlay.setLocation(x, y);
            overlay.setVisible(true);

            imageEditOverlay = overlay;
        } catch (Throwable ignored) {}
    }

    private static void hideImageEditOverlay() {
        if (imageEditOverlay != null) {
            imageEditOverlay.setVisible(false);
            imageEditOverlay.dispose();
            imageEditOverlay = null;
        }
    }

    /**
     * Shows a size preview overlay centered on the image (like Apple Freeform).
     */
    private static void showSizePreviewOverlay(JTextPane editor, Rectangle imageBounds, int newW, int newH) {
        try {
            Rectangle visibleBounds = visibleImageBounds(editor, imageBounds);
            if (visibleBounds == null || visibleBounds.width <= 0 || visibleBounds.height <= 0) {
                visibleBounds = imageBounds;
            }
            Point editorLoc = editor.getLocationOnScreen();
            int centerX = editorLoc.x + visibleBounds.x + visibleBounds.width / 2;
            int centerY = editorLoc.y + visibleBounds.y + visibleBounds.height / 2;

            String sizeText = newW + " × " + newH;

            if (sizePreviewOverlay == null) {
                JWindow overlay = new JWindow(SwingUtilities.getWindowAncestor(editor));
                overlay.setBackground(new Color(0, 0, 0, 0));
                overlay.setAlwaysOnTop(true);

                JLabel label = new JLabel(sizeText, javax.swing.SwingConstants.CENTER);
                label.setFont(AeroTheme.defaultFont().deriveFont(Font.BOLD, 14f));
                label.setForeground(Color.WHITE);
                label.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

                JPanel content = new JPanel(new java.awt.BorderLayout()) {
                    @Override
                    protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        // Dark pill background
                        g2.setColor(new Color(30, 30, 30, 220));
                        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                        g2.dispose();
                    }
                };
                content.setOpaque(false);
                content.add(label, java.awt.BorderLayout.CENTER);

                overlay.setContentPane(content);
                overlay.pack();

                sizePreviewOverlay = overlay;
            } else {
                // Update existing overlay
                JPanel content = (JPanel) sizePreviewOverlay.getContentPane();
                JLabel label = (JLabel) content.getComponent(0);
                label.setText(sizeText);
                sizePreviewOverlay.pack();
            }

            // Center on image
            int overlayW = sizePreviewOverlay.getWidth();
            int overlayH = sizePreviewOverlay.getHeight();
            int targetX = centerX - overlayW / 2;
            int targetY = centerY - overlayH / 2;
            Point clamped = clampToVisibleRect(editor, targetX, targetY, overlayW, overlayH);
            sizePreviewOverlay.setLocation(clamped.x, clamped.y);
            sizePreviewOverlay.setVisible(true);
        } catch (Throwable ignored) {}
    }

    private static void hideSizePreviewOverlay() {
        if (sizePreviewOverlay != null) {
            sizePreviewOverlay.setVisible(false);
            sizePreviewOverlay.dispose();
            sizePreviewOverlay = null;
        }
    }

    private static void removeActiveListeners(JTextPane editor) {
        if (activeMouseListener != null) {
            editor.removeMouseListener(activeMouseListener);
            activeMouseListener = null;
        }
        if (activeFocusListener != null) {
            editor.removeFocusListener(activeFocusListener);
            activeFocusListener = null;
        }
    }

    private static void installGlobalMouseListener() {
        if (activeGlobalMouseListener == null) {
            activeGlobalMouseListener = event -> {
                if (!(event instanceof MouseEvent me)) return;
                if (me.getID() != MouseEvent.MOUSE_PRESSED) return;
                if (activeOverlay == null || !activeOverlay.isVisible()) return;
                Window srcWindow = null;
                Object src = me.getSource();
                if (src instanceof Component) {
                    try {
                        srcWindow = SwingUtilities.getWindowAncestor((Component) src);
                    } catch (Throwable ignored) {}
                }
                if (srcWindow == activeOverlay || srcWindow == imageEditOverlay || srcWindow == sizePreviewOverlay) {
                    return;
                }
                fadeOutAndDismiss(activeOverlay);
            };
        }
        if (!globalMouseListenerInstalled) {
            Toolkit.getDefaultToolkit().addAWTEventListener(activeGlobalMouseListener, AWTEvent.MOUSE_EVENT_MASK);
            globalMouseListenerInstalled = true;
        }
    }

    private static void removeGlobalMouseListener() {
        if (globalMouseListenerInstalled && activeGlobalMouseListener != null) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(activeGlobalMouseListener);
            globalMouseListenerInstalled = false;
        }
    }

    private static boolean isMouseOverAnyOverlay(Point screenPos) {
        if (screenPos == null) {
            try {
                screenPos = java.awt.MouseInfo.getPointerInfo().getLocation();
            } catch (Throwable t) {
                return false;
            }
        }
        if (activeOverlay != null && activeOverlay.isVisible() && activeOverlay.getBounds().contains(screenPos)) {
            return true;
        }
        if (imageEditOverlay != null && imageEditOverlay.isVisible() && imageEditOverlay.getBounds().contains(screenPos)) {
            return true;
        }
        if (sizePreviewOverlay != null && sizePreviewOverlay.isVisible() && sizePreviewOverlay.getBounds().contains(screenPos)) {
            return true;
        }
        return false;
    }

    private static boolean tryPasteFromClipboard(JTextPane editor, Supplier<File> dirSupplier, int maxWidthPx) {
        try {
            Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable t = cb.getContents(null);
            if (t == null) return false;

            // 1) Direct image
            if (t.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                Image img = (Image) t.getTransferData(DataFlavor.imageFlavor);
                return insertImage(editor, toBufferedImage(img), dirSupplier.get(), maxWidthPx);
            }

            // 2) File list (e.g., pasted from Finder)
            if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                @SuppressWarnings("unchecked")
                List<File> files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
                boolean any = false;
                for (File f : files) {
                    if (isImageFile(f)) {
                        BufferedImage bi = ImageIO.read(f);
                        if (bi != null) any |= insertImage(editor, bi, dirSupplier.get(), maxWidthPx);
                    }
                }
                return any;
            }

            // 3) String (maybe a URL)
            if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                String s = ((String) t.getTransferData(DataFlavor.stringFlavor)).trim();
                if (s.startsWith("http://") || s.startsWith("https://")) {
                    try {
                        BufferedImage bi = ImageIO.read(new URL(s));
                        if (bi != null) return insertImage(editor, bi, dirSupplier.get(), maxWidthPx);
                    } catch (IOException ignored) {}
                }
            }
        } catch (UnsupportedFlavorException | IOException ignored) {
            // fall through to text paste
        }
        return false;
    }

    private static boolean insertImage(JTextPane editor, BufferedImage bi, File attachmentsDir, int maxWidthPx) {
        if (bi == null) return false;
        if (attachmentsDir != null && !attachmentsDir.exists()) attachmentsDir.mkdirs();

        // Scale to fit max width (keeping aspect)
        BufferedImage scaled = scaleToMaxWidth(bi, maxWidthPx);
        BufferedImage softened = softenCornersIfNeeded(scaled);
        // Save to disk
        File out = new File(attachmentsDir, timestampName()+".png");
        try {
            long approxBytes = (long) softened.getWidth() * (long) softened.getHeight() * 4L;
            FileIO.ensureSpace(out.toPath(), approxBytes + 4096L, "image attachment");
        } catch (IOException e) {
            return false;
        }
        try { ImageIO.write(softened, "PNG", out); } catch (IOException e) { /* ignore, still insert */ }

        // Cache in native memory for scroll performance
        cacheImageNative(softened, out);

        // Insert as icon at caret, preserving scroll position to prevent jumping
        ImageIcon icon = new ImageIcon(softened);
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setIcon(attrs, icon);
        // Keep track of the source file so we can rescale later
        attrs.addAttribute("imageSourceFile", out);
        StyledDocument doc = editor.getStyledDocument();
        DefaultCaret caret = (editor.getCaret() instanceof DefaultCaret)
            ? (DefaultCaret) editor.getCaret() : null;
        int oldPolicy = caret != null ? caret.getUpdatePolicy() : -1;
        try {
            // Save current scroll position before insertion
            javax.swing.JViewport viewport = null;
            java.awt.Container parent = editor.getParent();
            if (parent instanceof javax.swing.JViewport) {
                viewport = (javax.swing.JViewport) parent;
            }
            Point savedScrollPos = viewport != null ? viewport.getViewPosition() : null;
            
            int pos = editor.getCaretPosition();
            if (caret != null) {
                caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
            }
            doc.insertString(pos, " ", attrs);
            // Add a trailing newline for spacing
            doc.insertString(pos+1, "\n", null);
            
            // Set caret without triggering scroll by using invokeLater
            // and restore scroll position if user was scrolled away
            final javax.swing.JViewport vp = viewport;
            final Point scrollPos = savedScrollPos;
            final DefaultCaret caretRef = caret;
            final int caretPolicy = oldPolicy;
            SwingUtilities.invokeLater(() -> {
                try {
                    // Move caret
                    editor.setCaretPosition(Math.min(doc.getLength(), pos + 2));
                    // Restore scroll position to prevent jump
                    if (vp != null && scrollPos != null) vp.setViewPosition(scrollPos);
                } catch (Throwable ignored) {}
                if (caretRef != null && caretPolicy >= 0) {
                    caretRef.setUpdatePolicy(caretPolicy);
                }
            });
            
            editor.requestFocusInWindow();
            return true;
        } catch (BadLocationException e) {
            if (caret != null && oldPolicy >= 0) {
                try { caret.setUpdatePolicy(oldPolicy); } catch (Throwable ignored) {}
            }
            return false;
        }
    }

    public static boolean insertImageFromBuffer(JTextPane editor,
                                                BufferedImage bi,
                                                java.util.function.Supplier<File> attachmentsDirSupplier,
                                                int maxWidthPx) {
        File dir = attachmentsDirSupplier != null ? attachmentsDirSupplier.get() : null;
        return insertImage(editor, bi, dir, maxWidthPx);
    }

    private static String timestampName() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
    }
    
    /**
     * Initialize native image cache for scroll performance.
     * Thread-safe, only initializes once.
     */
    private static void initNativeCache() {
        if (cacheInitialized) return;
        synchronized (ImagePasteManager.class) {
            if (cacheInitialized) return;
            if (NativeAccess.hasImageCacheSupport()) {
                cacheInitialized = NativeAccess.imgcacheInit(CACHE_MAX_ENTRIES, CACHE_MAX_MEMORY_MB);
            }
        }
    }
    
    /**
     * Cache an image in native memory for fast scroll rendering.
     * Uses file path hash as stable ID.
     */
    private static void cacheImageNative(BufferedImage img, File sourceFile) {
        if (!cacheInitialized || img == null || sourceFile == null) return;
        try {
            long imageId = sourceFile.getAbsolutePath().hashCode() & 0xFFFFFFFFL;
            int w = img.getWidth();
            int h = img.getHeight();
            int[] pixels = img.getRGB(0, 0, w, h, null, 0, w);
            NativeAccess.imgcachePut(imageId, pixels, w, h);
        } catch (Throwable ignored) {
            // Cache failure is non-fatal, just skip
        }
    }

    private static boolean isImageFile(File f) {
        if (f == null || !f.exists() || !f.isFile()) return false;
        String n = f.getName().toLowerCase();
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".gif") || n.endsWith(".bmp") || n.endsWith(".webp");
    }

    private static BufferedImage toBufferedImage(Image img) {
        if (img instanceof BufferedImage) return (BufferedImage) img;
        BufferedImage b = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = b.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(img, 0, 0, null);
        g2.dispose();
        return b;
    }

    private static BufferedImage scaleToMaxWidth(BufferedImage src, int maxW) {
        if (maxW <= 0 || src.getWidth() <= maxW) return src;
        return resizeToWidth(src, maxW);
    }
    
    /**
     * Resize image to target width, maintaining aspect ratio.
     * Supports both upscaling and downscaling using native C implementation.
     */
    private static BufferedImage resizeToWidth(BufferedImage src, int targetW) {
        if (targetW <= 0) return src;
        if (src.getWidth() == targetW) return src;
        
        // Calculate target dimensions maintaining aspect ratio
        int srcW = src.getWidth();
        int srcH = src.getHeight();
        float scale = targetW / (float) srcW;
        int w = Math.max(1, Math.round(srcW * scale));
        int h = Math.max(1, Math.round(srcH * scale));
        
        // Try native resize first (faster, supports both up and down)
        BufferedImage result = nativeResize(src, w, h);
        if (result != null) return result;
        
        // Java fallback with bicubic interpolation
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(src, 0, 0, w, h, null);
        g2.dispose();
        return out;
    }
    
    /**
     * Native high-performance image resize using C implementation.
     * Returns null if native library unavailable or resize fails.
     */
    private static BufferedImage nativeResize(BufferedImage src, int dstW, int dstH) {
        try {
            int srcW = src.getWidth();
            int srcH = src.getHeight();
            
            // Extract ARGB pixels
            int[] srcPixels = src.getRGB(0, 0, srcW, srcH, null, 0, srcW);
            
            // Call native resize (quality=2 for auto selection)
            int[] dstPixels = NativeAccess.imageResizeArgb(srcPixels, srcW, srcH, dstW, dstH, 2);
            if (dstPixels == null) return null;
            
            // Create result image
            BufferedImage out = new BufferedImage(dstW, dstH, BufferedImage.TYPE_INT_ARGB);
            out.setRGB(0, 0, dstW, dstH, dstPixels, 0, dstW);
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    // ---- DnD handler ----
    private static class ImageFileTransferHandler extends TransferHandler {
        private final JTextPane editor;
        private final Supplier<File> dirSupplier;
        private final int maxWidthPx;
        private final TransferHandler previous;
        ImageFileTransferHandler(JTextPane editor, Supplier<File> dirSupplier, int maxWidthPx, TransferHandler previous){
            this.editor = editor; this.dirSupplier = dirSupplier; this.maxWidthPx = maxWidthPx; this.previous = previous;
        }
        @Override public boolean canImport(TransferSupport support) {
            if (support.isDataFlavorSupported(DataFlavor.imageFlavor)) return true;
            if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return true;
            if (support.isDataFlavorSupported(DataFlavor.stringFlavor)) return true;
            return previous != null && previous.canImport(support);
        }
        @Override public boolean importData(TransferSupport support) {
            try {
                if (support.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                    Image img = (Image) support.getTransferable().getTransferData(DataFlavor.imageFlavor);
                    return insertImage(editor, toBufferedImage(img), dirSupplier.get(), maxWidthPx);
                }
                if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    boolean any = false;
                    for (File f : files) {
                        if (isImageFile(f)) {
                            BufferedImage bi = ImageIO.read(f);
                            if (bi != null) any |= insertImage(editor, bi, dirSupplier.get(), maxWidthPx);
                        }
                    }
                    return any || (previous != null && previous.importData(support));
                }
                if (support.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                    String s = ((String) support.getTransferable().getTransferData(DataFlavor.stringFlavor)).trim();
                    if (s.startsWith("http://") || s.startsWith("https://")) {
                        try {
                            BufferedImage bi = ImageIO.read(new URL(s));
                            if (bi != null) return insertImage(editor, bi, dirSupplier.get(), maxWidthPx);
                        } catch (IOException ignored) {}
                    }
                }
            } catch (UnsupportedFlavorException | IOException ignored) {
                // pass
            }
            return previous != null && previous.importData(support);
        }
    }

    // ---- Minimal Floating Toolbar for Image Adjustment ----
    private static void showMinimalToolbar(JTextPane editor,
                                           int startOffset,
                                           File sourceFile,
                                           ImageIcon currentIcon,
                                           Rectangle imageBounds,
                                           Supplier<File> attachmentsDirSupplier,
                                           int defaultMaxWidth) {
        // Dismiss any existing overlay
        dismissActiveOverlay();
        
        int currentW = currentIcon.getIconWidth();
        int minW = 60;
        int maxW = Math.max(defaultMaxWidth * 2, Math.max(currentW * 2, editor.getWidth()));
        
        // Mutable holder for the source file
        final File[] srcRef = new File[]{ sourceFile };
        
        // Create a sleek floating toolbar
        JWindow toolbar = new JWindow(SwingUtilities.getWindowAncestor(editor));
        toolbar.setBackground(new Color(0, 0, 0, 0));
        toolbar.setAlwaysOnTop(true);
        activeOverlay = toolbar;
        
        FrostedGlassPanel content = new FrostedGlassPanel(new java.awt.BorderLayout(10, 0), 16);
        content.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        
        // Slider for resizing
        MoodSlider sizeSlider = new MoodSlider();
        sizeSlider.setMinimum(minW);
        sizeSlider.setMaximum(maxW);
        sizeSlider.setValue(currentW);
        sizeSlider.setPreferredSize(new Dimension(180, 28));
        sizeSlider.setFocusable(false);
        
        // Size label
        JLabel sizeLabel = new JLabel(currentW + "px");
        sizeLabel.setFont(AeroTheme.defaultFont().deriveFont(Font.PLAIN, 12f));
        sizeLabel.setForeground(new Color(90, 95, 105));
        sizeLabel.setPreferredSize(new Dimension(45, 20));
        
        ToolbarIconButton deleteBtn = new ToolbarIconButton("delete");
        deleteBtn.setPreferredSize(new Dimension(30, 30));
        deleteBtn.setMinimumSize(new Dimension(30, 30));
        deleteBtn.setMaximumSize(new Dimension(30, 30));
        deleteBtn.setToolTipText("Delete image");
        
        // Track original dimensions for aspect ratio
        final int originalW = currentIcon.getIconWidth();
        final int originalH = currentIcon.getIconHeight();
        final float aspectRatio = originalH / (float) originalW;

        // Slider change listener - defer resize until release, show live preview
        sizeSlider.addChangeListener(e -> {
            int newW = sizeSlider.getValue();
            int newH = Math.round(newW * aspectRatio);
            sizeLabel.setText(newW + "px");
            
            if (sizeSlider.getValueIsAdjusting()) {
                // Show live size preview overlay while dragging
                showSizePreviewOverlay(editor, imageBounds, newW, newH);
            } else {
                // User released - apply the resize and hide preview
                hideSizePreviewOverlay();
                resizeImage(editor, startOffset, srcRef, newW, attachmentsDirSupplier);
                repositionToolbar(toolbar, editor, startOffset);
            }
        });
        
        deleteBtn.addActionListener(e -> {
            try {
                StyledDocument doc = editor.getStyledDocument();
                doc.remove(startOffset, 1);
            } catch (BadLocationException ignored) {}
            dismissActiveOverlay();
        });

        
        // Layout: [slider] [size] [delete]
        JPanel sliderPanel = new JPanel(new java.awt.BorderLayout(6, 0));
        sliderPanel.setOpaque(false);
        sliderPanel.add(sizeSlider, java.awt.BorderLayout.CENTER);
        sliderPanel.add(sizeLabel, java.awt.BorderLayout.EAST);
        
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        actions.add(deleteBtn);
        content.add(sliderPanel, java.awt.BorderLayout.CENTER);
        content.add(actions, java.awt.BorderLayout.EAST);
        
        toolbar.setContentPane(content);
        toolbar.pack();
        
        // Position below the image
        positionToolbar(toolbar, editor, imageBounds);
        toolbar.setVisible(true);

        // Show "being edited" overlay on the image
        showImageEditOverlay(editor, imageBounds);

        // Track editor and remove any previous listeners
        activeEditor = editor;
        removeActiveListeners(editor);
        installGlobalMouseListener();
        
        // Auto-dismiss when clicking elsewhere
        activeMouseListener = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                Point screenPoint = e.getLocationOnScreen();
                if (!isMouseOverAnyOverlay(screenPoint)) {
                    dismissActiveOverlay();
                }
            }
        };
        editor.addMouseListener(activeMouseListener);
        
        // Dismiss on focus loss
        activeFocusListener = new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                Timer timer = new Timer(200, ev -> {
                    Window focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
                    if (focused != toolbar && focused != imageEditOverlay && focused != sizePreviewOverlay) {
                        dismissActiveOverlay();
                    }
                });
                timer.setRepeats(false);
                timer.start();
            }
        };
        editor.addFocusListener(activeFocusListener);
    }
    
    private static void positionToolbar(JWindow toolbar, JTextPane editor, Rectangle imageBounds) {
        positionToolbar(toolbar, editor, imageBounds, null);
    }

    private static void positionToolbar(JWindow toolbar, JTextPane editor, Rectangle imageBounds, Point clickPoint) {
        try {
            Rectangle visibleBounds = visibleImageBounds(editor, imageBounds);
            if (visibleBounds == null || visibleBounds.width <= 0 || visibleBounds.height <= 0) {
                visibleBounds = imageBounds;
            }
            Point editorLoc = editor.getLocationOnScreen();
            Rectangle placementBounds = getPopupPlacementBounds(editor);
            if (placementBounds == null || placementBounds.width <= 0 || placementBounds.height <= 0) return;

            Rectangle imageOnScreen = new Rectangle(
                    editorLoc.x + visibleBounds.x,
                    editorLoc.y + visibleBounds.y,
                    Math.max(1, visibleBounds.width),
                    Math.max(1, visibleBounds.height));

            int w = toolbar.getWidth();
            int h = toolbar.getHeight();
            Rectangle usable = new Rectangle(
                    placementBounds.x + POPUP_EDGE_MARGIN,
                    placementBounds.y + POPUP_EDGE_MARGIN,
                    Math.max(1, placementBounds.width - (POPUP_EDGE_MARGIN * 2)),
                    Math.max(1, placementBounds.height - (POPUP_EDGE_MARGIN * 2)));
            int minX = usable.x;
            int minY = usable.y;
            int maxX = usable.x + Math.max(0, usable.width - w);
            int maxY = usable.y + Math.max(0, usable.height - h);

            int anchorCenterX = imageOnScreen.x + (imageOnScreen.width / 2);
            int anchorCenterY = imageOnScreen.y + (imageOnScreen.height / 2);
            if (clickPoint != null) {
                int clickX = editorLoc.x + clickPoint.x;
                int clickY = editorLoc.y + clickPoint.y;
                anchorCenterX = clampInt(clickX, imageOnScreen.x, imageOnScreen.x + Math.max(0, imageOnScreen.width - 1));
                anchorCenterY = clampInt(clickY, imageOnScreen.y, imageOnScreen.y + Math.max(0, imageOnScreen.height - 1));
            }

            final int gap = 12;
            Rectangle[] candidates = new Rectangle[] {
                    // Below
                    new Rectangle(clampInt(anchorCenterX - (w / 2), minX, maxX), imageOnScreen.y + imageOnScreen.height + gap, w, h),
                    // Above
                    new Rectangle(clampInt(anchorCenterX - (w / 2), minX, maxX), imageOnScreen.y - h - gap, w, h),
                    // Right
                    new Rectangle(imageOnScreen.x + imageOnScreen.width + gap, clampInt(anchorCenterY - (h / 2), minY, maxY), w, h),
                    // Left
                    new Rectangle(imageOnScreen.x - w - gap, clampInt(anchorCenterY - (h / 2), minY, maxY), w, h)
            };

            Rectangle usableRect = new Rectangle(usable.x, usable.y, Math.max(1, usable.width), Math.max(1, usable.height));
            Rectangle bestPartial = null;
            long bestVisibleArea = -1L;

            for (Rectangle candidate : candidates) {
                boolean outsideImage = !candidate.intersects(imageOnScreen);
                if (!outsideImage) continue;

                boolean fullyVisible = candidate.x >= minX && candidate.x <= maxX
                        && candidate.y >= minY && candidate.y <= maxY;
                if (fullyVisible) {
                    toolbar.setLocation(candidate.x, candidate.y);
                    return;
                }

                Rectangle intersection = candidate.intersection(usableRect);
                long visibleArea = (long) Math.max(0, intersection.width) * Math.max(0, intersection.height);
                if (visibleArea > bestVisibleArea) {
                    bestVisibleArea = visibleArea;
                    bestPartial = candidate;
                }
            }

            if (bestPartial != null) {
                toolbar.setLocation(bestPartial.x, bestPartial.y);
                return;
            }
        } catch (Throwable ignored) {}
    }

    private static Rectangle visibleImageBounds(JTextPane editor, Rectangle imageBounds) {
        if (editor == null || imageBounds == null) return imageBounds;
        try {
            Rectangle visible = getVisibleRectSafe(editor);
            Rectangle clipped = imageBounds.intersection(visible);
            if (clipped.isEmpty()) return imageBounds;
            return clipped;
        } catch (Throwable ignored) {
            return imageBounds;
        }
    }

    private static Rectangle getVisibleRectSafe(JTextPane editor) {
        Rectangle visible = editor.getVisibleRect();
        if (visible == null || visible.width <= 0 || visible.height <= 0) {
            return new Rectangle(0, 0, editor.getWidth(), editor.getHeight());
        }
        return visible;
    }

    private static Rectangle getPopupPlacementBounds(JTextPane editor) {
        if (editor == null) return new Rectangle(0, 0, 1, 1);
        try {
            Rectangle editorVisibleOnScreen = getEditorVisibleOnScreen(editor);
            Window owner = SwingUtilities.getWindowAncestor(editor);
            Rectangle ownerBoundsOnScreen = null;
            if (owner != null && owner.isShowing()) {
                Point ownerLoc = owner.getLocationOnScreen();
                ownerBoundsOnScreen = new Rectangle(
                        ownerLoc.x,
                        ownerLoc.y,
                        Math.max(1, owner.getWidth()),
                        Math.max(1, owner.getHeight()));
            }

            java.awt.GraphicsConfiguration gc = editor.getGraphicsConfiguration();
            if (gc == null) {
                Window w = owner;
                if (w != null) gc = w.getGraphicsConfiguration();
            }
            Rectangle screenBounds;
            if (gc != null) {
                screenBounds = new Rectangle(gc.getBounds());
                java.awt.Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
                screenBounds.x += insets.left;
                screenBounds.y += insets.top;
                screenBounds.width = Math.max(1, screenBounds.width - insets.left - insets.right);
                screenBounds.height = Math.max(1, screenBounds.height - insets.top - insets.bottom);
            } else {
                Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
                screenBounds = new Rectangle(0, 0, Math.max(1, screen.width), Math.max(1, screen.height));
            }

            Rectangle sourceBounds = ownerBoundsOnScreen != null ? ownerBoundsOnScreen : editorVisibleOnScreen;
            Rectangle clipped = sourceBounds.intersection(screenBounds);
            if (clipped.width > 0 && clipped.height > 0) return clipped;
            // Fallback to editor-visible area if owner bounds were unusable
            clipped = editorVisibleOnScreen.intersection(screenBounds);
            if (clipped.width > 0 && clipped.height > 0) return clipped;
            if (sourceBounds.width > 0 && sourceBounds.height > 0) return sourceBounds;
            return screenBounds;
        } catch (Throwable ignored) {
            return new Rectangle(0, 0, 1, 1);
        }
    }

    private static Rectangle getEditorVisibleOnScreen(JTextPane editor) {
        Point editorLoc = editor.getLocationOnScreen();
        Rectangle visible = getVisibleRectSafe(editor);
        return new Rectangle(
                editorLoc.x + visible.x,
                editorLoc.y + visible.y,
                Math.max(1, visible.width),
                Math.max(1, visible.height));
    }

    private static Point clampToVisibleRect(JTextPane editor, int x, int y, int w, int h) {
        try {
            Rectangle placementBounds = getEditorVisibleOnScreen(editor);
            java.awt.GraphicsConfiguration gc = editor.getGraphicsConfiguration();
            if (gc != null) {
                Rectangle screenBounds = new Rectangle(gc.getBounds());
                java.awt.Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
                screenBounds.x += insets.left;
                screenBounds.y += insets.top;
                screenBounds.width = Math.max(1, screenBounds.width - insets.left - insets.right);
                screenBounds.height = Math.max(1, screenBounds.height - insets.top - insets.bottom);
                Rectangle clipped = placementBounds.intersection(screenBounds);
                if (clipped.width > 0 && clipped.height > 0) {
                    placementBounds = clipped;
                }
            }
            int minX = placementBounds.x + POPUP_EDGE_MARGIN;
            int minY = placementBounds.y + POPUP_EDGE_MARGIN;
            int maxX = placementBounds.x + placementBounds.width - w - POPUP_EDGE_MARGIN;
            int maxY = placementBounds.y + placementBounds.height - h - POPUP_EDGE_MARGIN;
            if (maxX < minX) {
                minX = placementBounds.x;
                maxX = placementBounds.x + Math.max(0, placementBounds.width - w);
            }
            if (maxY < minY) {
                minY = placementBounds.y;
                maxY = placementBounds.y + Math.max(0, placementBounds.height - h);
            }
            int clampedX = clampInt(x, minX, maxX);
            int clampedY = clampInt(y, minY, maxY);
            return new Point(clampedX, clampedY);
        } catch (Throwable ignored) {
            return new Point(x, y);
        }
    }

    private static int clampInt(int value, int min, int max) {
        if (max < min) return min;
        return Math.max(min, Math.min(value, max));
    }
    
    private static void repositionToolbar(JWindow toolbar, JTextPane editor, int startOffset) {
        try {
            java.awt.geom.Rectangle2D r2 = editor.modelToView2D(startOffset);
            if (r2 != null) {
                Rectangle bounds = r2.getBounds();
                // Get current icon dimensions
                StyledDocument doc = editor.getStyledDocument();
                javax.swing.text.Element el = doc.getCharacterElement(startOffset);
                if (el != null) {
                    Object ico = StyleConstants.getIcon(el.getAttributes());
                    if (ico instanceof ImageIcon icon) {
                        bounds.width = icon.getIconWidth();
                        bounds.height = icon.getIconHeight();
                    }
                }
                positionToolbar(toolbar, editor, bounds);
            }
        } catch (Throwable ignored) {}
    }
    
    private static void dismissActiveOverlay() {
        hideImageEditOverlay();
        hideSizePreviewOverlay();
        if (activeEditor != null) {
            removeActiveListeners(activeEditor);
        }
        removeGlobalMouseListener();
        if (activeOverlay != null) {
            activeOverlay.dispose();
            activeOverlay = null;
        }
        activeEditor = null;
    }
    
    private static void resizeImage(JTextPane editor, int startOffset, File[] srcRef, 
                                    int targetW, Supplier<File> attachmentsDirSupplier) {
        try {
            // Save scroll position to prevent jumping
            javax.swing.JViewport viewport = null;
            java.awt.Container parent = editor.getParent();
            if (parent instanceof javax.swing.JViewport) {
                viewport = (javax.swing.JViewport) parent;
            }
            final Point savedScrollPos = viewport != null ? viewport.getViewPosition() : null;
            final javax.swing.JViewport vp = viewport;
            
            File source = srcRef[0];
            if (source == null) {
                // Create source file from current icon
                StyledDocument doc = editor.getStyledDocument();
                javax.swing.text.Element el = doc.getCharacterElement(startOffset);
                if (el == null) return;
                Object ico = StyleConstants.getIcon(el.getAttributes());
                if (!(ico instanceof ImageIcon icon)) return;
                
                File dir = attachmentsDirSupplier != null ? attachmentsDirSupplier.get() : null;
                if (dir != null && !dir.exists()) dir.mkdirs();
                source = new File(dir != null ? dir : new File("."), timestampName() + ".png");
                BufferedImage buf = toBufferedImage(icon.getImage());
                try { ImageIO.write(buf, "PNG", source); } catch (IOException ignored) {}
                srcRef[0] = source;
            }
            
            BufferedImage orig = ImageIO.read(source);
            if (orig == null) return;
            
            // Use resizeToWidth for both upscaling and downscaling
            BufferedImage scaled = resizeToWidth(orig, targetW);
            scaled = softenCornersIfNeeded(scaled);
            ImageIcon newIcon = new ImageIcon(scaled);
            
            StyledDocument doc = editor.getStyledDocument();
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setIcon(attrs, newIcon);
            attrs.addAttribute("imageSourceFile", source);
            
            try {
                doc.remove(startOffset, 1);
                doc.insertString(startOffset, " ", attrs);
                
                // Force layout update so click detection works immediately
                editor.revalidate();
                editor.repaint();
                
                // Restore scroll position after resize to prevent jumping
                if (vp != null && savedScrollPos != null) {
                    SwingUtilities.invokeLater(() -> {
                        try {
                            vp.setViewPosition(savedScrollPos);
                        } catch (Throwable ignored) {}
                    });
                }
            } catch (BadLocationException ignored) {}
            
        } catch (IOException ignored) {}
    }
    
    private static void copyToClipboard(Image img) {
        if (img == null) return;
        BufferedImage bi = toBufferedImage(img);
        Transferable t = new Transferable() {
            @Override public DataFlavor[] getTransferDataFlavors() { 
                return new DataFlavor[]{ DataFlavor.imageFlavor }; 
            }
            @Override public boolean isDataFlavorSupported(DataFlavor flavor) { 
                return DataFlavor.imageFlavor.equals(flavor); 
            }
            @Override public Object getTransferData(DataFlavor flavor) { 
                return bi; 
            }
        };
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(t, null);
        } catch (Throwable ignored) {}
    }

    private static BufferedImage softenCornersIfNeeded(BufferedImage src) {
        if (src == null) return null;
        if (!shouldSoftenCorners(src)) return src;
        int w = src.getWidth();
        int h = src.getHeight();
        int radius = Math.max(4, Math.min(10, Math.round(Math.min(w, h) * 0.02f)));
        if (radius <= 0) return src;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setComposite(AlphaComposite.Src);
        g2.setColor(new Color(0, 0, 0, 0));
        g2.fillRect(0, 0, w, h);
        RoundRectangle2D.Float clip = new RoundRectangle2D.Float(0f, 0f, w, h, radius * 2f, radius * 2f);
        g2.setClip(clip);
        g2.drawImage(src, 0, 0, null);
        g2.dispose();
        return out;
    }

    private static boolean shouldSoftenCorners(BufferedImage src) {
        try {
            int w = src.getWidth();
            int h = src.getHeight();
            if (w <= 4 || h <= 4) return false;
            int[] corners = new int[] {
                src.getRGB(0, 0),
                src.getRGB(w - 1, 0),
                src.getRGB(0, h - 1),
                src.getRGB(w - 1, h - 1)
            };
            int opaque = 0;
            for (int argb : corners) {
                int a = (argb >>> 24) & 0xFF;
                if (a >= 240) opaque++;
            }
            return opaque >= 3;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
