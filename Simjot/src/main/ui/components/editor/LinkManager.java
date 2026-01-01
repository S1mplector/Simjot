/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.components.editor;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.Element;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * LinkManager - Detects and styles URLs in JTextPane editors.
 * 
 * Features:
 * - Auto-detects URLs when text is pasted
 * - Applies blue color and underline styling to links
 * - Clickable links that open in default browser (Ctrl/Cmd+Click)
 * - Hover cursor changes to hand over links
 */
public final class LinkManager {

    private LinkManager() {}

    // URL detection pattern - matches http, https, ftp, and www URLs
    private static final Pattern URL_PATTERN = Pattern.compile(
        "(https?://|ftp://|www\\.)[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+"
    );

    // Custom attribute key to mark link text
    public static final String LINK_ATTRIBUTE = "link_url";

    // Link styling color
    private static final Color LINK_COLOR = new Color(30, 90, 180);

    /**
     * Installs link detection and styling on a JTextPane.
     * 
     * @param editor The JTextPane to install link management on
     */
    public static void install(JTextPane editor) {
        if (editor == null) return;
        Object installed = editor.getClientProperty("linkManagerInstalled");
        if (Boolean.TRUE.equals(installed)) return;
        editor.putClientProperty("linkManagerInstalled", Boolean.TRUE);

        try {
            // Wrap default paste action to detect and style links
            Action defaultPaste = editor.getActionMap().get(DefaultEditorKit.pasteAction);
            Action imageAwarePaste = editor.getActionMap().get("imageAwarePaste");

            // Use imageAwarePaste if available (from ImagePasteManager), otherwise default
            Action basePaste = imageAwarePaste != null ? imageAwarePaste : defaultPaste;

            editor.getActionMap().put("linkAwarePaste", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    int startOffset = editor.getCaretPosition();

                    // First, let the base paste action run
                    if (basePaste != null) {
                        basePaste.actionPerformed(e);
                    }

                    // Then scan and style any links in the pasted content
                    SwingUtilities.invokeLater(() -> {
                        try {
                            int endOffset = editor.getCaretPosition();
                            if (endOffset > startOffset) {
                                String pastedText = editor.getDocument().getText(startOffset, endOffset - startOffset);
                                styleLinkRanges(editor, startOffset, pastedText);
                            }
                        } catch (BadLocationException ignored) {}
                    });
                }
            });

            int mask = getMenuShortcutMask();
            if (mask != 0) {
                editor.getInputMap(JComponent.WHEN_FOCUSED)
                        .put(KeyStroke.getKeyStroke('V', mask), "linkAwarePaste");
            }

            // Add mouse listener for link clicks (Ctrl/Cmd+Click to open)
            editor.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if ((e.getModifiersEx() & mask) != 0 || e.getClickCount() == 2) {
                        String url = getLinkAtPoint(editor, e.getPoint());
                        if (url != null) {
                            openLink(url);
                            e.consume();
                        }
                    }
                }
            });

            // Change cursor to hand when hovering over links
            editor.addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    String url = getLinkAtPoint(editor, e.getPoint());
                    if (url != null) {
                        editor.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    } else {
                        editor.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
                    }
                }
            });
        } catch (Throwable ignored) {
            editor.putClientProperty("linkManagerInstalled", Boolean.FALSE);
        }
    }

    /**
     * Defers installation until the editor is displayable, to avoid wiring before the UI is ready.
     */
    public static void installWhenReady(JTextPane editor) {
        if (editor == null) return;
        if (editor.isDisplayable()) {
            SwingUtilities.invokeLater(() -> install(editor));
            return;
        }
        Object pending = editor.getClientProperty("linkManagerInstallScheduled");
        if (Boolean.TRUE.equals(pending)) return;
        editor.putClientProperty("linkManagerInstallScheduled", Boolean.TRUE);
        editor.addHierarchyListener(new HierarchyListener() {
            @Override
            public void hierarchyChanged(HierarchyEvent e) {
                if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0 && editor.isDisplayable()) {
                    editor.removeHierarchyListener(this);
                    editor.putClientProperty("linkManagerInstallScheduled", Boolean.FALSE);
                    SwingUtilities.invokeLater(() -> install(editor));
                }
            }
        });
    }

    private static int getMenuShortcutMask() {
        try {
            int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
            return mask != 0 ? mask : InputEvent.CTRL_DOWN_MASK;
        } catch (Throwable ignored) {
            return InputEvent.CTRL_DOWN_MASK;
        }
    }

    /**
     * Scans text for URLs and applies link styling.
     */
    private static void styleLinkRanges(JTextPane editor, int baseOffset, String text) {
        StyledDocument doc = editor.getStyledDocument();
        List<int[]> linkRanges = findLinkRanges(text);

        for (int[] range : linkRanges) {
            int start = baseOffset + range[0];
            int length = range[1] - range[0];
            String url = text.substring(range[0], range[1]);

            // Normalize URL (add http:// if starts with www.)
            if (url.startsWith("www.")) {
                url = "https://" + url;
            }

            MutableAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setForeground(attrs, LINK_COLOR);
            StyleConstants.setUnderline(attrs, true);
            attrs.addAttribute(LINK_ATTRIBUTE, url);

            doc.setCharacterAttributes(start, length, attrs, false);
        }
    }

    /**
     * Finds all URL ranges in the given text.
     * Uses native C++ implementation when available for better performance.
     * 
     * @return List of [start, end] offset pairs
     */
    private static List<int[]> findLinkRanges(String text) {
        // Try native implementation first (faster)
        if (NativeAccess.hasLinkSupport()) {
            int[][] nativeRanges = NativeAccess.linkFindRanges(text);
            if (nativeRanges != null && nativeRanges.length > 0) {
                List<int[]> ranges = new ArrayList<>(nativeRanges.length);
                for (int[] range : nativeRanges) {
                    ranges.add(range);
                }
                return ranges;
            }
        }
        
        // Java fallback using regex
        List<int[]> ranges = new ArrayList<>();
        Matcher matcher = URL_PATTERN.matcher(text);
        while (matcher.find()) {
            ranges.add(new int[]{matcher.start(), matcher.end()});
        }
        return ranges;
    }

    /**
     * Gets the link URL at the given point in the editor, if any.
     */
    private static String getLinkAtPoint(JTextPane editor, java.awt.Point point) {
        try {
            int pos = editor.viewToModel2D(point);
            if (pos < 0) return null;

            StyledDocument doc = editor.getStyledDocument();
            Element elem = doc.getCharacterElement(pos);
            AttributeSet attrs = elem.getAttributes();
            Object url = attrs.getAttribute(LINK_ATTRIBUTE);
            return url != null ? url.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Opens a URL in the default browser.
     */
    private static void openLink(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception e) {
            // Silently fail if browser can't be opened
        }
    }

    /**
     * Scans the entire document and styles any links found.
     * Useful for styling links when loading existing content.
     */
    public static void styleAllLinks(JTextPane editor) {
        try {
            StyledDocument doc = editor.getStyledDocument();
            String text = doc.getText(0, doc.getLength());
            styleLinkRanges(editor, 0, text);
        } catch (BadLocationException ignored) {}
    }

    /**
     * Checks if the given text contains any URLs.
     * Uses native C++ implementation when available.
     */
    public static boolean containsLinks(String text) {
        if (text == null || text.isEmpty()) return false;
        
        // Try native first
        if (NativeAccess.hasLinkSupport()) {
            return NativeAccess.linkContains(text);
        }
        
        // Java fallback
        return URL_PATTERN.matcher(text).find();
    }

    /**
     * Extracts all URLs from the given text.
     * Uses native C++ implementation when available.
     */
    public static List<String> extractLinks(String text) {
        List<String> links = new ArrayList<>();
        if (text == null || text.isEmpty()) return links;
        
        // Try native first
        if (NativeAccess.hasLinkSupport()) {
            List<String> nativeLinks = NativeAccess.linkExtractAll(text);
            if (nativeLinks != null && !nativeLinks.isEmpty()) {
                return nativeLinks;
            }
        }
        
        // Java fallback
        Matcher matcher = URL_PATTERN.matcher(text);
        while (matcher.find()) {
            String url = matcher.group();
            if (url.startsWith("www.")) {
                url = "https://" + url;
            }
            links.add(url);
        }
        return links;
    }
}
