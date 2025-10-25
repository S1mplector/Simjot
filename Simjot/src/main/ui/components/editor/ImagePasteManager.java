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
import main.ui.components.containers.AeroPanel;
import main.ui.components.buttons.RoundedButton;

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
        JLabel widthLbl = new JLabel(currentW + " px");

        JButton fitBtn = new RoundedButton("Fit");
        JButton minusBtn = new RoundedButton("-100");
        JButton plusBtn = new RoundedButton("+100");
        JButton rotateLBtn = new RoundedButton("⟲");
        JButton rotateRBtn = new RoundedButton("⟳");
        JButton alignLeftBtn = new RoundedButton("L");
        JButton alignCenterBtn = new RoundedButton("C");
        JButton alignRightBtn = new RoundedButton("R");
        JButton captionBtn = new RoundedButton("Caption");
        JButton replaceBtn = new RoundedButton("Replace…");
        JButton openBtn = new RoundedButton("Open");
        JButton copyBtn = new RoundedButton("Copy");
        JButton removeBtn = new RoundedButton("Remove");

        // Compact margins
        for (JButton b : new JButton[]{fitBtn, minusBtn, plusBtn, rotateLBtn, rotateRBtn, alignLeftBtn, alignCenterBtn, alignRightBtn, captionBtn, replaceBtn, openBtn, copyBtn, removeBtn}) {
            b.setMargin(new Insets(2,6,2,6));
        }

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4,4,2,4);
        gc.gridy = 0; gc.gridx = 0; gc.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel("Width:"), gc);
        gc.gridx = 1; gc.weightx = 1; gc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(slider, gc);
        gc.gridx = 2; gc.weightx = 0; gc.fill = GridBagConstraints.NONE;
        panel.add(widthLbl, gc);
        gc.gridx = 3; panel.add(minusBtn, gc);
        gc.gridx = 4; panel.add(plusBtn, gc);
        gc.gridx = 5; panel.add(fitBtn, gc);

        // Row 2: actions
        gc.insets = new Insets(2,4,4,4);
        gc.gridy = 1; gc.gridx = 0; gc.gridwidth = 1;
        panel.add(rotateLBtn, gc);
        gc.gridx = 1; panel.add(rotateRBtn, gc);
        gc.gridx = 2; panel.add(alignLeftBtn, gc);
        gc.gridx = 3; panel.add(alignCenterBtn, gc);
        gc.gridx = 4; panel.add(alignRightBtn, gc);
        gc.gridx = 5; panel.add(captionBtn, gc);
        gc.gridx = 6; panel.add(replaceBtn, gc);
        gc.gridx = 7; panel.add(copyBtn, gc);
        gc.gridx = 8; panel.add(openBtn, gc);
        gc.gridx = 9; panel.add(removeBtn, gc);

        JPopupMenu popup = new JPopupMenu();
        popup.setBorder(BorderFactory.createEmptyBorder());
        AeroPanel aero = new AeroPanel(14);
        aero.setLayout(new BorderLayout());
        aero.setBorder(BorderFactory.createEmptyBorder(6,8,6,8));
        aero.add(panel, BorderLayout.CENTER);
        popup.add(aero);

        // Mutable holder for the source file so lambdas can update it
        final File[] srcRef = new File[]{ sourceFile };

        Runnable applyResize = () -> {
            int tw = Math.max(minW, Math.min(maxW, slider.getValue()));
            resizeImageAt(editor, startOffset, srcRef, currentIcon, tw, attachmentsDirSupplier);
            try {
                int fitW = Math.max(minW, editor.getVisibleRect().width - 64);
                int pct = Math.max(1, Math.round((tw * 100f) / Math.max(1, fitW)));
                widthLbl.setText(tw + " px  " + pct + "% of editor");
            } catch (Throwable ignored3) { widthLbl.setText(tw + " px"); }
        };

        minusBtn.addActionListener(e -> { slider.setValue(Math.max(minW, slider.getValue() - 100)); applyResize.run(); });
        plusBtn.addActionListener(e -> { slider.setValue(Math.min(maxW, slider.getValue() + 100)); applyResize.run(); });
        fitBtn.addActionListener(e -> { int fitW = fitWidth(editor, minW); slider.setValue(fitW); applyResize.run(); });
        slider.addChangeListener(e -> applyResize.run());
        removeBtn.addActionListener(e -> {
            try {
                StyledDocument doc = editor.getStyledDocument();
                doc.remove(startOffset, 1);
                popup.setVisible(false);
            } catch (BadLocationException ignored) {}
        });

        // Rotate (rewrites the source image and updates the icon keeping current width)
        rotateLBtn.addActionListener(e -> rotateImageAt(editor, startOffset, srcRef, currentIcon, -90, attachmentsDirSupplier));
        rotateRBtn.addActionListener(e -> rotateImageAt(editor, startOffset, srcRef, currentIcon, 90, attachmentsDirSupplier));

        // Alignment for the paragraph hosting the icon
        alignLeftBtn.addActionListener(e -> alignParagraphAt(editor, startOffset, StyleConstants.ALIGN_LEFT));
        alignCenterBtn.addActionListener(e -> alignParagraphAt(editor, startOffset, StyleConstants.ALIGN_CENTER));
        alignRightBtn.addActionListener(e -> alignParagraphAt(editor, startOffset, StyleConstants.ALIGN_RIGHT));

        // Add caption line under the image (italic)
        captionBtn.addActionListener(e -> addCaptionUnder(editor, startOffset));

        // Replace with another image; maintain current slider width
        replaceBtn.addActionListener(e -> replaceImageFromChooser(editor, startOffset, srcRef, slider.getValue(), attachmentsDirSupplier));

        // Utilities: open file and copy to clipboard
        openBtn.addActionListener(e -> { if (srcRef[0] != null) try { java.awt.Desktop.getDesktop().open(srcRef[0]); } catch (Throwable ignored) {} });
        copyBtn.addActionListener(e -> copyImageToClipboard(currentIcon.getImage()));

        int px = (imageBounds != null) ? imageBounds.x : 10;
        int py = (imageBounds != null) ? imageBounds.y + imageBounds.height : 10;
        // Clamp popup location within the editor visible viewport
        Dimension pref = popup.getPreferredSize();
        Rectangle vr = editor.getVisibleRect();
        int xClamped = Math.max(vr.x + 6, Math.min(px, vr.x + vr.width - pref.width - 6));
        int yBelow = py;
        int yAbove = (imageBounds != null) ? imageBounds.y - pref.height - 8 : py - pref.height - 8;
        int yClamped = (yBelow + pref.height <= vr.y + vr.height - 4) ? yBelow : Math.max(vr.y + 4, yAbove);
        popup.show(editor, xClamped, yClamped);

        // Add a robust overlay that follows the image and supports drag resize
        addResizeHandle(editor, startOffset, imageBounds, (newW) -> resizeImageAt(editor, startOffset, srcRef, currentIcon, Math.max(minW, Math.min(maxW, newW)), attachmentsDirSupplier));
    }

    private static int fitWidth(JTextPane editor, int minW) {
        int inset = 64;
        return Math.max(minW, editor.getVisibleRect().width - inset);
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

    private static void rotateImageAt(JTextPane editor,
                                      int startOffset,
                                      File[] srcRef,
                                      ImageIcon currentIcon,
                                      int degrees,
                                      Supplier<File> attachmentsDirSupplier) {
        try {
            if (srcRef[0] == null) return;
            BufferedImage orig = ImageIO.read(srcRef[0]);
            if (orig == null) return;
            BufferedImage rotated = rotate(orig, degrees);
            if (rotated == null) return;
            int targetW = currentIcon.getIconWidth();
            BufferedImage scaled = scaleToMaxWidth(rotated, targetW);
            try { ImageIO.write(scaled, "PNG", srcRef[0]); } catch (IOException ignored) {}
            // Replace icon in document with the rotated one
            ImageIcon newIcon = new ImageIcon(scaled);
            StyledDocument doc = editor.getStyledDocument();
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setIcon(attrs, newIcon);
            attrs.addAttribute("imageSourceFile", srcRef[0]);
            try {
                doc.remove(startOffset, 1);
                doc.insertString(startOffset, " ", attrs);
            } catch (BadLocationException ignored) {}
            editor.revalidate(); editor.repaint();
        } catch (IOException ignored) {}
    }

    private static BufferedImage rotate(BufferedImage src, int degrees) {
        int d = ((degrees % 360) + 360) % 360;
        if (d == 0) return src;
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage dst = (d == 90 || d == 270)
                ? new BufferedImage(h, w, BufferedImage.TYPE_INT_ARGB)
                : new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = dst.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        java.awt.geom.AffineTransform at = new java.awt.geom.AffineTransform();
        switch (d) {
            case 90 -> { at.translate(h, 0); at.rotate(Math.toRadians(90)); }
            case 180 -> { at.translate(w, h); at.rotate(Math.toRadians(180)); }
            case 270 -> { at.translate(0, w); at.rotate(Math.toRadians(270)); }
        }
        g2.drawImage(src, at, null);
        g2.dispose();
        return dst;
    }

    private static void alignParagraphAt(JTextPane editor, int startOffset, int alignment) {
        try {
            StyledDocument doc = editor.getStyledDocument();
            javax.swing.text.Element para = doc.getParagraphElement(startOffset);
            if (para != null) {
                SimpleAttributeSet attrs = new SimpleAttributeSet();
                StyleConstants.setAlignment(attrs, alignment);
                doc.setParagraphAttributes(para.getStartOffset(), para.getEndOffset() - para.getStartOffset(), attrs, false);
            }
        } catch (Throwable ignored) {}
    }

    private static void addCaptionUnder(JTextPane editor, int startOffset) {
        try {
            StyledDocument doc = editor.getStyledDocument();
            // Insert right after the icon + newline that follows it
            int pos = Math.min(doc.getLength(), startOffset + 2);
            SimpleAttributeSet italic = new SimpleAttributeSet();
            StyleConstants.setItalic(italic, true);
            StyleConstants.setForeground(italic, new java.awt.Color(90, 90, 90));
            doc.insertString(pos, "Caption...\n", italic);
            editor.requestFocusInWindow();
            editor.setCaretPosition(Math.min(doc.getLength(), pos + 8));
        } catch (BadLocationException ignored) {}
    }

    private static void replaceImageFromChooser(JTextPane editor,
                                                int startOffset,
                                                File[] srcRef,
                                                int targetW,
                                                Supplier<File> attachmentsDirSupplier) {
        try {
            javax.swing.JFileChooser ch = new javax.swing.JFileChooser();
            int res = ch.showOpenDialog(editor);
            if (res == javax.swing.JFileChooser.APPROVE_OPTION) {
                File sel = ch.getSelectedFile();
                BufferedImage bi = ImageIO.read(sel);
                if (bi != null) {
                    if (srcRef[0] == null) {
                        File dir = attachmentsDirSupplier != null ? attachmentsDirSupplier.get() : null;
                        if (dir != null && !dir.exists()) dir.mkdirs();
                        srcRef[0] = new File(dir != null ? dir : new File("."), timestampName()+".png");
                    }
                    BufferedImage scaled = scaleToMaxWidth(bi, targetW);
                    try { ImageIO.write(scaled, "PNG", srcRef[0]); } catch (IOException ignored) {}
                    ImageIcon icon = new ImageIcon(scaled);
                    StyledDocument doc = editor.getStyledDocument();
                    SimpleAttributeSet attrs = new SimpleAttributeSet();
                    StyleConstants.setIcon(attrs, icon);
                    attrs.addAttribute("imageSourceFile", srcRef[0]);
                    try { doc.remove(startOffset, 1); doc.insertString(startOffset, " ", attrs); } catch (BadLocationException ignored) {}
                    editor.revalidate(); editor.repaint();
                }
            }
        } catch (IOException ignored) {}
    }

    private static void copyImageToClipboard(Image img) {
        try {
            if (img == null) return;
            java.awt.image.BufferedImage bi = toBufferedImage(img);
            java.awt.datatransfer.Transferable t = new java.awt.datatransfer.Transferable() {
                @Override public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() { return new java.awt.datatransfer.DataFlavor[]{ java.awt.datatransfer.DataFlavor.imageFlavor }; }
                @Override public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor flavor) { return java.awt.datatransfer.DataFlavor.imageFlavor.equals(flavor); }
                @Override public Object getTransferData(java.awt.datatransfer.DataFlavor flavor) { return bi; }
            };
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(t, null);
        } catch (Throwable ignored) {}
    }

    private static void addResizeHandle(JTextPane editor, int startOffset, Rectangle imageBounds, java.util.function.IntFunction<Rectangle> onResize) {
        if (imageBounds == null) return;
        JRootPane root = SwingUtilities.getRootPane(editor);
        if (root == null) return;
        JLayeredPane lp = root.getLayeredPane();

        final int HANDLE = 12;
        final int BORDER = 2;

        JComponent overlay = new JComponent(){
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w=getWidth(), h=getHeight();
                // Border
                g2.setColor(new Color(0,120,215,160));
                g2.setStroke(new BasicStroke(BORDER));
                g2.drawRect(0,0,w-1,h-1);
                // Corner handle (bottom-right)
                g2.setColor(new Color(255,255,255,230));
                g2.fillRoundRect(w-HANDLE, h-HANDLE, HANDLE-2, HANDLE-2, 6, 6);
                g2.setColor(new Color(0,0,0,160));
                g2.drawRoundRect(w-HANDLE, h-HANDLE, HANDLE-2, HANDLE-2, 6, 6);
                // Right edge indicator
                g2.setColor(new Color(0,120,215,120));
                g2.fillRect(w-3, 2, 2, h-4);
                g2.dispose();
            }
        };
        overlay.setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));

        // Initial bounds
        Point tl = new Point(imageBounds.x, imageBounds.y);
        SwingUtilities.convertPointToScreen(tl, editor);
        SwingUtilities.convertPointFromScreen(tl, lp);
        overlay.setBounds(tl.x, tl.y, imageBounds.width, imageBounds.height);
        lp.add(overlay, JLayeredPane.POPUP_LAYER);
        lp.repaint();

        final int[] startX = new int[1];
        final int[] startWidth = new int[1];
        final boolean[] dragging = new boolean[1];

        java.awt.event.MouseAdapter ma = new java.awt.event.MouseAdapter(){
            @Override public void mousePressed(java.awt.event.MouseEvent e){
                int w = overlay.getWidth(), h = overlay.getHeight();
                int x=e.getX(), y=e.getY();
                boolean inCorner = (x >= w-HANDLE && y >= h-HANDLE);
                boolean onRightEdge = (x >= w-8);
                if (inCorner || onRightEdge) {
                    dragging[0]=true;
                    startX[0]=e.getXOnScreen();
                    startWidth[0]=w;
                }
            }
            @Override public void mouseReleased(java.awt.event.MouseEvent e){
                dragging[0]=false;
                lp.remove(overlay);
                lp.repaint();
            }
            @Override public void mouseDragged(java.awt.event.MouseEvent e){
                if(!dragging[0]) return;
                int dx = e.getXOnScreen() - startX[0];
                int newW = Math.max(60, startWidth[0] + dx);
                Rectangle updated = onResize.apply(newW);
                if (updated != null) {
                    Point tl2 = new Point(updated.x, updated.y);
                    SwingUtilities.convertPointToScreen(tl2, editor);
                    SwingUtilities.convertPointFromScreen(tl2, lp);
                    overlay.setBounds(tl2.x, tl2.y, updated.width, updated.height);
                } else {
                    overlay.setSize(newW, overlay.getHeight());
                }
                lp.repaint();
            }
        };
        overlay.addMouseListener(ma);
        overlay.addMouseMotionListener(ma);

        // Keep overlay following the image as the view scrolls/resizes
        javax.swing.Timer follow = new javax.swing.Timer(120, ev -> {
            try {
                java.awt.geom.Rectangle2D r2 = editor.modelToView2D(startOffset);
                if (r2 != null) {
                    Rectangle r = r2.getBounds();
                    Point tl3 = new Point(r.x, r.y);
                    SwingUtilities.convertPointToScreen(tl3, editor);
                    SwingUtilities.convertPointFromScreen(tl3, lp);
                    overlay.setBounds(tl3.x, tl3.y, r.width, r.height);
                    overlay.revalidate(); overlay.repaint();
                }
            } catch (Throwable ignored) {}
        });
        follow.setRepeats(true); follow.start();

        overlay.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.DISPLAYABILITY_CHANGED) != 0 && !overlay.isDisplayable()) {
                follow.stop();
            }
        });
    }
}
