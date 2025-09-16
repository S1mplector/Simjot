package main.ui.components.editor;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * ImagePasteManager adds crisp, reusable support for pasting and dropping images into a JTextPane.
 *
 * Features:
 * - Ctrl/Cmd+V: if clipboard has an image, it is saved under attachmentsDir and inserted at caret.
 * - Paste image file path or URL (http/https) – downloads and inserts.
 * - Drag & drop image files directly into the editor.
 * - Optional maxWidth scaling to keep images neat in the flow.
 *
 * Persistence:
 * - Images are saved to disk as PNGs under the supplied attachments directory.
 * - The editor receives an ImageIcon inserted as a component; with RTFEditorKit-based saving,
 *   icons are serialized into the RTF. The source image is still kept on disk for reuse/exports.
 */
public final class ImagePasteManager {

    private ImagePasteManager() {}

    public static void install(JTextPane editor, Supplier<File> attachmentsDirSupplier, int maxWidthPx) {
        Objects.requireNonNull(editor, "editor");
        Objects.requireNonNull(attachmentsDirSupplier, "attachmentsDirSupplier");

        // Wrap default paste action to handle images
        Action defaultPaste = editor.getActionMap().get(DefaultEditorKit.pasteAction);
        editor.getActionMap().put("imageAwarePaste", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (!tryPasteFromClipboard(editor, attachmentsDirSupplier, maxWidthPx)) {
                    // Fallback to default behavior
                    if (defaultPaste != null) defaultPaste.actionPerformed(e);
                }
            }
        });
        // Bind Ctrl/Cmd+V to our action
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        editor.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke('V', mask), "imageAwarePaste");

        // Install a transfer handler for drag & drop
        editor.setTransferHandler(new ImageFileTransferHandler(editor, attachmentsDirSupplier, maxWidthPx, editor.getTransferHandler()));

        // Click-to-adjust overlay for images inside the editor
        editor.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) {
                try {
                    int pos = editor.viewToModel2D(e.getPoint());
                    if (pos < 0) pos = 0;
                    StyledDocument doc = editor.getStyledDocument();
                    int len = doc.getLength();
                    // Search a small window around the click for an icon element
                    javax.swing.text.Element found = null;
                    ImageIcon foundIcon = null;
                    File srcFile = null;
                    Rectangle bounds = null;
                    for (int d = 0; d <= 10 && found == null; d++) {
                        for (int sign : new int[]{-1, 1, 0}) {
                            int idx = Math.max(0, Math.min(len, pos + sign * d));
                            javax.swing.text.Element el = doc.getCharacterElement(idx);
                            if (el == null) continue;
                            javax.swing.text.AttributeSet as = el.getAttributes();
                            Object ico = StyleConstants.getIcon(as);
                            if (ico instanceof ImageIcon) {
                                try {
                                    java.awt.geom.Rectangle2D r2 = editor.modelToView2D(el.getStartOffset());
                                    bounds = (r2 != null) ? r2.getBounds() : null;
                                } catch (Throwable ignored2) {}
                                found = el;
                                foundIcon = (ImageIcon) ico;
                                Object src = as.getAttribute("imageSourceFile");
                                if (src instanceof File) srcFile = (File) src;
                                break;
                            }
                        }
                    }
                    if (found != null) {
                        if (bounds == null) bounds = new Rectangle(e.getX(), e.getY(), foundIcon.getIconWidth(), foundIcon.getIconHeight());
                        showResizeOverlay(editor, found.getStartOffset(), srcFile, foundIcon, bounds, attachmentsDirSupplier, maxWidthPx);
                    }
                } catch (Throwable ignored) {}
            }
        });
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

    // ---- Overlay UI for resizing ----
    private static void showResizeOverlay(JTextPane editor,
                                          int startOffset,
                                          File sourceFile,
                                          ImageIcon currentIcon,
                                          Rectangle imageBounds,
                                          Supplier<File> attachmentsDirSupplier,
                                          int defaultMaxWidth) {
        int currentW = currentIcon.getIconWidth();
        int minW = 120;
        int maxW = Math.max(defaultMaxWidth * 2, Math.max(currentW, editor.getWidth()));

        JSlider slider = new JSlider(JSlider.HORIZONTAL, minW, maxW, currentW);
        slider.setMajorTickSpacing(Math.max(100, (maxW - minW) / 4));
        slider.setPaintTicks(true);

        JButton fitBtn = new JButton("Fit to editor");
        JButton minusBtn = new JButton("-100");
        JButton plusBtn = new JButton("+100");
        JButton removeBtn = new JButton("Remove");

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        panel.add(new JLabel("Width:"));
        panel.add(slider);
        panel.add(minusBtn);
        panel.add(plusBtn);
        panel.add(fitBtn);
        panel.add(removeBtn);

        JPopupMenu popup = new JPopupMenu();
        popup.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        popup.add(panel);

        // Mutable holder for the source file so lambdas can update it
        final File[] srcRef = new File[]{ sourceFile };

        Runnable applyResize = () -> resizeImageAt(editor, startOffset, srcRef, currentIcon, Math.max(minW, Math.min(maxW, slider.getValue())), attachmentsDirSupplier);

        minusBtn.addActionListener(e -> { slider.setValue(Math.max(minW, slider.getValue() - 100)); applyResize.run(); });
        plusBtn.addActionListener(e -> { slider.setValue(Math.min(maxW, slider.getValue() + 100)); applyResize.run(); });
        fitBtn.addActionListener(e -> {
            int inset = 64; // some padding inside the paper card
            int fitW = Math.max(minW, editor.getVisibleRect().width - inset);
            slider.setValue(fitW);
            applyResize.run();
        });
        slider.addChangeListener(e -> applyResize.run());
        removeBtn.addActionListener(e -> {
            try {
                StyledDocument doc = editor.getStyledDocument();
                doc.remove(startOffset, 1);
                popup.setVisible(false);
            } catch (BadLocationException ignored) {}
        });

        int px = (imageBounds != null) ? imageBounds.x : 10;
        int py = (imageBounds != null) ? imageBounds.y + imageBounds.height : 10;
        Point p = new Point(px, py);
        SwingUtilities.convertPointToScreen(p, editor);
        popup.show(editor, px, py);

        // Add draggable top-right handle for innovative resizing
        addResizeHandle(editor, startOffset, imageBounds, (newW) -> resizeImageAt(editor, startOffset, srcRef, currentIcon, Math.max(minW, Math.min(maxW, newW)), attachmentsDirSupplier));
    }

    private static Rectangle resizeImageAt(JTextPane editor,
                                      int startOffset,
                                      File[] srcRef,
                                      ImageIcon currentIcon,
                                      int targetW,
                                      Supplier<File> attachmentsDirSupplier) {
        try {
            File ensuredSource = srcRef[0];
            if (ensuredSource == null) {
                File dir = attachmentsDirSupplier != null ? attachmentsDirSupplier.get() : null;
                if (dir != null && !dir.exists()) dir.mkdirs();
                ensuredSource = new File(dir != null ? dir : new File("."), timestampName()+".png");
                Image img = currentIcon.getImage();
                BufferedImage buf = toBufferedImage(img);
                try { ImageIO.write(buf, "PNG", ensuredSource); } catch (IOException ignored) {}
                srcRef[0] = ensuredSource;
            }
            BufferedImage orig = ImageIO.read(ensuredSource);
            if (orig == null) return null;
            BufferedImage scaled = scaleToMaxWidth(orig, targetW);
            ImageIcon icon = new ImageIcon(scaled);
            StyledDocument doc = editor.getStyledDocument();
            javax.swing.text.Element el = doc.getCharacterElement(startOffset);
            if (el == null) return null;
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setIcon(attrs, icon);
            if (srcRef[0] != null) attrs.addAttribute("imageSourceFile", srcRef[0]);
            try {
                doc.remove(startOffset, 1);
                doc.insertString(startOffset, " ", attrs);
            } catch (BadLocationException ignored) {}
            editor.revalidate(); editor.repaint();
            try {
                java.awt.geom.Rectangle2D r2 = editor.modelToView2D(startOffset);
                if (r2 != null) {
                    Rectangle r = r2.getBounds();
                    r.width = icon.getIconWidth();
                    r.height = icon.getIconHeight();
                    return r;
                }
            } catch (Throwable ignored) {}
        } catch (IOException ignored) {}
        return null;
    }

    private static void addResizeHandle(JTextPane editor, int startOffset, Rectangle imageBounds, java.util.function.IntFunction<Rectangle> onResize) {
        if (imageBounds == null) return;
        JRootPane root = SwingUtilities.getRootPane(editor);
        if (root == null) return;
        JLayeredPane lp = root.getLayeredPane();

        // Small square handle
        JPanel handle = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(255,255,255,230));
                g2.fillRoundRect(0,0,getWidth()-1,getHeight()-1,6,6);
                g2.setColor(new Color(0,0,0,160));
                g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,6,6);
                g2.dispose();
            }
        };
        handle.setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
        handle.setSize(16,16);
        // Position at TOP-RIGHT relative to layered pane coordinates
        Point tr = new Point(imageBounds.x + imageBounds.width - 8, imageBounds.y - 8);
        SwingUtilities.convertPointToScreen(tr, editor);
        SwingUtilities.convertPointFromScreen(tr, lp);
        handle.setLocation(tr);
        lp.add(handle, JLayeredPane.POPUP_LAYER);
        lp.repaint();

        final int[] startX = new int[1];
        final int[] startWidth = new int[1];
        handle.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) {
                startX[0] = e.getXOnScreen();
                startWidth[0] = imageBounds.width;
            }
            @Override public void mouseReleased(java.awt.event.MouseEvent e) {
                lp.remove(handle);
                lp.repaint();
            }
        });
        handle.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override public void mouseDragged(java.awt.event.MouseEvent e) {
                int dx = e.getXOnScreen() - startX[0];
                int newW = Math.max(60, startWidth[0] + dx);
                Rectangle updated = onResize.apply(newW);
                // Reposition handle to stay attached to the image's TOP-RIGHT corner
                if (updated != null) {
                    Point nbr = new Point(updated.x + updated.width - 8, updated.y - 8);
                    SwingUtilities.convertPointToScreen(nbr, editor);
                    SwingUtilities.convertPointFromScreen(nbr, lp);
                    handle.setLocation(nbr);
                } else {
                    // Fallback: move relatively
                    Point p = handle.getLocation();
                    handle.setLocation(p.x + dx, p.y);
                }
                lp.repaint();
            }
        });
    }
}
