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

package main.ui.components.editor;

import java.awt.Color;
import java.awt.Cursor;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
import javax.swing.JButton;
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
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import main.infrastructure.io.FileIO;

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

    public static void install(JTextPane editor, Supplier<File> attachmentsDirSupplier, int maxWidthPx) {
        Objects.requireNonNull(editor, "editor");
        Objects.requireNonNull(attachmentsDirSupplier, "attachmentsDirSupplier");

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

        // Click handler for image adjustment
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
                        
                        File srcFile = null;
                        Object src = as.getAttribute("imageSourceFile");
                        if (src instanceof File) srcFile = (File) src;
                        
                        showMinimalToolbar(editor, el.getStartOffset(), srcFile, icon, bounds, 
                                          attachmentsDirSupplier, maxWidthPx);
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {}
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
        // Save to disk
        File out = new File(attachmentsDir, timestampName()+".png");
        try {
            long approxBytes = (long) scaled.getWidth() * (long) scaled.getHeight() * 4L;
            FileIO.ensureSpace(out.toPath(), approxBytes + 4096L, "image attachment");
        } catch (IOException e) {
            return false;
        }
        try { ImageIO.write(scaled, "PNG", out); } catch (IOException e) { /* ignore, still insert */ }

        // Insert as icon at caret
        ImageIcon icon = new ImageIcon(scaled);
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setIcon(attrs, icon);
        // Keep track of the source file so we can rescale later
        attrs.addAttribute("imageSourceFile", out);
        StyledDocument doc = editor.getStyledDocument();
        try {
            int pos = editor.getCaretPosition();
            doc.insertString(pos, " ", attrs);
            // Add a trailing newline for spacing
            doc.insertString(pos+1, "\n", null);
            editor.requestFocusInWindow();
            editor.setCaretPosition(Math.min(doc.getLength(), pos+2));
            return true;
        } catch (BadLocationException e) {
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
        float scale = maxW / (float) src.getWidth();
        int w = Math.max(1, Math.round(src.getWidth() * scale));
        int h = Math.max(1, Math.round(src.getHeight() * scale));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(src, 0, 0, w, h, null);
        g2.dispose();
        return out;
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
        int minW = 80;
        int maxW = Math.max(defaultMaxWidth * 2, Math.max(currentW, editor.getWidth()));
        
        // Mutable holder for the source file
        final File[] srcRef = new File[]{ sourceFile };
        final int[] currentWidth = new int[]{ currentW };
        
        // Create a sleek floating toolbar
        JWindow toolbar = new JWindow(SwingUtilities.getWindowAncestor(editor));
        toolbar.setAlwaysOnTop(true);
        activeOverlay = toolbar;
        
        JPanel content = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Rounded rectangle background with shadow effect
                g2.setColor(new Color(0, 0, 0, 30));
                g2.fillRoundRect(2, 2, getWidth() - 2, getHeight() - 2, 12, 12);
                g2.setColor(new Color(250, 250, 250, 245));
                g2.fillRoundRect(0, 0, getWidth() - 2, getHeight() - 2, 12, 12);
                g2.setColor(new Color(200, 200, 200));
                g2.drawRoundRect(0, 0, getWidth() - 3, getHeight() - 3, 12, 12);
                g2.dispose();
            }
        };
        content.setOpaque(false);
        content.setLayout(new FlowLayout(FlowLayout.CENTER, 4, 6));
        content.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        
        // Size label
        JLabel sizeLabel = new JLabel(currentW + "px");
        sizeLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        sizeLabel.setForeground(new Color(80, 80, 80));
        
        // Create icon buttons using simple Unicode symbols
        JButton smallerBtn = createToolbarButton("−", "Make smaller");
        JButton largerBtn = createToolbarButton("+", "Make larger");
        JButton fitBtn = createToolbarButton("⤢", "Fit to width");
        JButton copyBtn = createToolbarButton("⎘", "Copy image");
        JButton deleteBtn = createToolbarButton("✕", "Remove image");
        
        // Size adjustment actions
        smallerBtn.addActionListener(e -> {
            int newW = Math.max(minW, currentWidth[0] - 100);
            currentWidth[0] = newW;
            resizeImage(editor, startOffset, srcRef, newW, attachmentsDirSupplier);
            sizeLabel.setText(newW + "px");
            repositionToolbar(toolbar, editor, startOffset);
        });
        
        largerBtn.addActionListener(e -> {
            int newW = Math.min(maxW, currentWidth[0] + 100);
            currentWidth[0] = newW;
            resizeImage(editor, startOffset, srcRef, newW, attachmentsDirSupplier);
            sizeLabel.setText(newW + "px");
            repositionToolbar(toolbar, editor, startOffset);
        });
        
        fitBtn.addActionListener(e -> {
            int fitW = Math.max(minW, editor.getVisibleRect().width - 48);
            currentWidth[0] = fitW;
            resizeImage(editor, startOffset, srcRef, fitW, attachmentsDirSupplier);
            sizeLabel.setText(fitW + "px");
            repositionToolbar(toolbar, editor, startOffset);
        });
        
        copyBtn.addActionListener(e -> {
            copyToClipboard(currentIcon.getImage());
        });
        
        deleteBtn.addActionListener(e -> {
            try {
                StyledDocument doc = editor.getStyledDocument();
                doc.remove(startOffset, 1);
            } catch (BadLocationException ignored) {}
            dismissActiveOverlay();
        });
        
        // Add components
        content.add(smallerBtn);
        content.add(sizeLabel);
        content.add(largerBtn);
        content.add(createSeparator());
        content.add(fitBtn);
        content.add(copyBtn);
        content.add(deleteBtn);
        
        toolbar.setContentPane(content);
        toolbar.pack();
        
        // Position below the image
        positionToolbar(toolbar, editor, imageBounds);
        toolbar.setVisible(true);
        
        // Auto-dismiss when clicking elsewhere
        editor.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                // Check if click is outside toolbar area
                Point screenPoint = e.getLocationOnScreen();
                Rectangle toolbarBounds = toolbar.getBounds();
                if (!toolbarBounds.contains(screenPoint)) {
                    dismissActiveOverlay();
                    editor.removeMouseListener(this);
                }
            }
        });
        
        // Dismiss on focus loss
        editor.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                // Delay to allow toolbar button clicks
                Timer timer = new Timer(200, ev -> {
                    Window focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
                    if (focused != toolbar) {
                        dismissActiveOverlay();
                    }
                });
                timer.setRepeats(false);
                timer.start();
                editor.removeFocusListener(this);
            }
        });
    }
    
    private static JButton createToolbarButton(String text, String tooltip) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isPressed()) {
                    g2.setColor(new Color(220, 220, 220));
                } else if (getModel().isRollover()) {
                    g2.setColor(new Color(235, 235, 235));
                } else {
                    g2.setColor(new Color(245, 245, 245));
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("SansSerif", Font.PLAIN, 14));
        btn.setForeground(new Color(60, 60, 60));
        btn.setPreferredSize(new Dimension(28, 24));
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setToolTipText(tooltip);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }
    
    private static JComponent createSeparator() {
        JPanel sep = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(new Color(200, 200, 200));
                g.fillRect(getWidth() / 2, 2, 1, getHeight() - 4);
            }
        };
        sep.setOpaque(false);
        sep.setPreferredSize(new Dimension(8, 20));
        return sep;
    }
    
    private static void positionToolbar(JWindow toolbar, JTextPane editor, Rectangle imageBounds) {
        try {
            Point editorLoc = editor.getLocationOnScreen();
            int x = editorLoc.x + imageBounds.x + (imageBounds.width - toolbar.getWidth()) / 2;
            int y = editorLoc.y + imageBounds.y + imageBounds.height + 4;
            
            // Keep within screen bounds
            Rectangle screenBounds = editor.getGraphicsConfiguration().getBounds();
            x = Math.max(screenBounds.x, Math.min(x, screenBounds.x + screenBounds.width - toolbar.getWidth()));
            y = Math.max(screenBounds.y, Math.min(y, screenBounds.y + screenBounds.height - toolbar.getHeight()));
            
            toolbar.setLocation(x, y);
        } catch (Throwable ignored) {}
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
        if (activeOverlay != null) {
            activeOverlay.dispose();
            activeOverlay = null;
        }
    }
    
    private static void resizeImage(JTextPane editor, int startOffset, File[] srcRef, 
                                    int targetW, Supplier<File> attachmentsDirSupplier) {
        try {
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
            
            BufferedImage scaled = scaleToMaxWidth(orig, targetW);
            ImageIcon newIcon = new ImageIcon(scaled);
            
            StyledDocument doc = editor.getStyledDocument();
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setIcon(attrs, newIcon);
            attrs.addAttribute("imageSourceFile", source);
            
            try {
                doc.remove(startOffset, 1);
                doc.insertString(startOffset, " ", attrs);
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
}
