package main.ui.features.widgets;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import main.infrastructure.io.AppDirectories;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.components.scrollbar.ModernScrollBarUI;
import main.ui.theme.aero.AeroTheme;
import main.ui.components.icons.ImageIconRenderer;

/**
 * Sticky Notes widget: Manage multiple small notes, change background color,
 * create, edit, delete, and persist them under SETTINGS/stickies/.
 */
public class IdeaStickyWidget implements Widget {
    // Model for a sticky note
    private static class Note {
        String id;
        String text = "";
        Color color = new Color(255, 255, 170);
        File file; // backing file

        @Override public String toString() {
            String t = text == null ? "" : text.trim();
            if (t.isEmpty()) return "Untitled";
            // first non-empty line
            String[] lines = t.split("\r?\n");
            for (String ln : lines) {
                String s = ln.trim();
                if (!s.isEmpty()) return s.length() > 32 ? s.substring(0, 32) + "…" : s;
            }
            return "Untitled";
        }
    }

    private final JFrame owner;
    private JDialog dialog;
    private JPanel textWrap;
    private JEditorPane area;
    private java.util.List<Note> notes = new ArrayList<>();
    private Note current;
    private boolean enabled = false;
    private javax.swing.Timer autosaveTimer;
    private Properties meta = new Properties();
    private File metaFile;
    private boolean resizing = false;
    private Point resizeAnchor;

    public IdeaStickyWidget(JFrame owner) { this.owner = owner; }

    @Override public void start() {
        if (enabled) return;
        enabled = true;
        ensureDialog();
        loadMeta();
        loadNotes();
        if (notes.isEmpty()) {
            createNewNote();
        } else {
            // Restore last opened note if available
            String lastId = meta.getProperty("lastNoteId", "");
            Note found = null;
            if (!lastId.isEmpty()) {
                for (Note n : notes) { if (lastId.equals(n.id)) { found = n; break; } }
            }
            setCurrent(found != null ? found : notes.get(0));
        }
        dialog.setVisible(true);
        dialog.toFront();
    }

    @Override public void stop() {
        enabled = false;
        try { saveCurrent(); } catch (Throwable ignored) {}
        saveMeta();
        if (dialog != null) dialog.setVisible(false);
    }

    @Override public boolean isEnabled() { return enabled; }

    @Override public void setEnabled(boolean enable) { if (enable) start(); else stop(); }

    @Override public String getName() { return "Idea Sticky"; }

    @Override public String getIconId() { return "sticky_widget"; }

    private void ensureDialog() {
        if (dialog != null) return;
        dialog = new JDialog(owner, "Sticky Notes", false);
        dialog.setUndecorated(true);
        dialog.setAlwaysOnTop(true);
        dialog.getRootPane().putClientProperty("apple.awt.draggableWindowBackground", Boolean.TRUE);
        // Make window fully transparent outside the sticky shape
        try { dialog.setBackground(new Color(0,0,0,0)); } catch (Exception ignore) {}
        dialog.setLayout(new BorderLayout());

        JPanel content = new JPanel();
        content.setOpaque(false);
        // No outer padding so the sticky fills the window; avoids visible frame
        content.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        content.setLayout(new BorderLayout(8,8));

        // Rich text (HTML) area in colored wrapper
        area = new JEditorPane();
        area.setContentType("text/html");
        area.setText("<html><body style='font-family:Serif;font-size:14pt;margin:6px;'></body></html>");
        area.setCaretPosition(area.getDocument().getLength());
        // Debounced autosave
        autosaveTimer = new javax.swing.Timer(800, e -> saveCurrent());
        autosaveTimer.setRepeats(false);
        area.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { syncText(); scheduleAutosave(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { syncText(); scheduleAutosave(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { syncText(); scheduleAutosave(); }
        });
        JScrollPane sp = new JScrollPane(area);
        sp.setBorder(BorderFactory.createEmptyBorder());
        try {
            sp.getVerticalScrollBar().setUI(new ModernScrollBarUI());
            sp.getHorizontalScrollBar().setUI(new ModernScrollBarUI());
        } catch (Throwable ignored) {}
        textWrap = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color c = (current != null ? current.color : new Color(255,255,200));
                g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 235));
                g2.fillRoundRect(0,0,getWidth(),getHeight(),10,10);
                // No outline stroke to avoid visible frame
                g2.dispose();
                super.paintComponent(g);
            }
        };
        textWrap.setOpaque(false);
        // Slight inner padding for rounded look
        textWrap.setBorder(BorderFactory.createEmptyBorder(6,6,6,6));
        // Tiny control bar inside the sticky (top-right)
        JPanel topRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 4));
        topRight.setOpaque(false);
        JButton btnList  = makeHeaderButton(iconFor("list", 16), "Notes");
        JButton btnColor = makeHeaderButton(iconFor("settings", 16), "Color");
        JButton btnSave  = makeHeaderButton(iconFor("save", 16), "Save");
        JButton btnNew   = makeHeaderButton(iconFor("plus", 16), "New note");
        JButton btnPin   = makeHeaderButton(iconFor("pin", 16), "Pin to main menu");
        JButton btnClose = makeHeaderButton(iconFor("close", 16), "Close");
        Dimension tiny = new Dimension(22, 22);
        for (JButton b : new JButton[]{btnList, btnColor, btnSave, btnNew, btnPin, btnClose}) {
            b.setPreferredSize(tiny);
            b.setMinimumSize(tiny);
            b.setMaximumSize(tiny);
            b.setFont(b.getFont().deriveFont(Font.BOLD, 11f));
        }
        btnList.addActionListener(e -> showNotesMenu(btnList));
        btnColor.addActionListener(e -> showColorPalette(btnColor));
        btnSave.addActionListener(e -> saveCurrent());
        btnNew.addActionListener(e -> createNewNote());
        btnPin.addActionListener(e -> togglePinCurrent());
        btnClose.addActionListener(e -> stop());
        topRight.add(btnList);
        topRight.add(btnColor);
        topRight.add(btnSave);
        topRight.add(btnNew);
        topRight.add(btnPin);
        topRight.add(btnClose);
        textWrap.add(topRight, BorderLayout.NORTH);
        // Formatting toolbar (B / I / U)
        JToolBar fmt = new JToolBar();
        fmt.setFloatable(false);
        fmt.setOpaque(false);
        JButton bBold = new JButton("B"); bBold.setFont(new Font("SansSerif", Font.BOLD, 12));
        JButton bItalic = new JButton("I"); bItalic.setFont(new Font("SansSerif", Font.ITALIC, 12));
        JButton bUnder = new JButton("U"); bUnder.setFont(new Font("SansSerif", Font.PLAIN, 12));
        bUnder.setBorderPainted(true);
        // Use Swing built-in StyledEditorKit actions (works with HTMLEditorKit)
        bBold.addActionListener(new javax.swing.text.StyledEditorKit.BoldAction());
        bItalic.addActionListener(new javax.swing.text.StyledEditorKit.ItalicAction());
        bUnder.addActionListener(new javax.swing.text.StyledEditorKit.UnderlineAction());
        fmt.add(bBold); fmt.add(bItalic); fmt.add(bUnder);
        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(fmt, BorderLayout.NORTH);
        center.add(sp, BorderLayout.CENTER);
        textWrap.add(center, BorderLayout.CENTER);
        content.add(textWrap, BorderLayout.CENTER);

        // Drag to move
        MouseAdapter drag = new MouseAdapter() {
            Point offset;
            @Override public void mousePressed(MouseEvent e) {
                offset = e.getPoint();
                // bottom-right 16x16 resize zone
                if (e.getX() >= dialog.getWidth() - 18 && e.getY() >= dialog.getHeight() - 18) {
                    resizing = true; resizeAnchor = e.getLocationOnScreen();
                } else {
                    resizing = false;
                }
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (resizing) {
                    Point now = e.getLocationOnScreen();
                    int dx = now.x - resizeAnchor.x;
                    int dy = now.y - resizeAnchor.y;
                    int nw = Math.max(240, dialog.getWidth() + dx);
                    int nh = Math.max(200, dialog.getHeight() + dy);
                    dialog.setSize(nw, nh);
                    resizeAnchor = now;
                    saveMeta();
                } else {
                    Point p = e.getLocationOnScreen();
                    dialog.setLocation(p.x - offset.x, p.y - offset.y);
                    saveMeta();
                }
            }
        };
        content.addMouseListener(drag);
        content.addMouseMotionListener(drag);
        textWrap.addMouseListener(drag);
        textWrap.addMouseMotionListener(drag);

        dialog.setContentPane(content);
        // Restore bounds from meta or place near app window
        int w = parseInt(meta.getProperty("w"), 300);
        int h = parseInt(meta.getProperty("h"), 260);
        int x = parseInt(meta.getProperty("x"), Integer.MIN_VALUE);
        int y = parseInt(meta.getProperty("y"), Integer.MIN_VALUE);
        dialog.setSize(w, h);
        if (x != Integer.MIN_VALUE && y != Integer.MIN_VALUE) {
            dialog.setLocation(x, y);
        } else {
            Point loc = owner != null ? owner.getLocationOnScreen() : new Point(100, 100);
            Dimension ownerSize = owner != null ? owner.getSize() : Toolkit.getDefaultToolkit().getScreenSize();
            dialog.setLocation(loc.x + ownerSize.width - dialog.getWidth() - 24, loc.y + 340);
        }

        // Keyboard shortcuts
        InputMap im = content.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap am = content.getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "save");
        am.put("save", new AbstractAction(){ @Override public void actionPerformed(ActionEvent e){ saveCurrent(); }});
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_N, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "new");
        am.put("new", new AbstractAction(){ @Override public void actionPerformed(ActionEvent e){ createNewNote(); }});
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "close");
        am.put("close", new AbstractAction(){ @Override public void actionPerformed(ActionEvent e){ stop(); }});
    }

    private JButton makeHeaderButton(Icon icon, String tip) {
        JButton b = new JButton(icon);
        b.setOpaque(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setToolTipText(tip);
        return b;
    }

    // Icon factory for mini buttons: prefer themed icon assets, fallback to vector/primitive
    private Icon iconFor(String name, int size) {
        String id = switch (name) {
            case "close" -> "close";
            case "settings", "gear" -> "general_settings";
            case "+", "plus", "new" -> "new";
            case "save" -> "save";
            case "list" -> "list";
            case "delete" -> "delete_entry";
            case "pin" -> null; // custom vector below
            default -> null;
        };
        if (id != null) {
            String res = ImageIconRenderer.mapIdToResource(id);
            if (res != null) {
                Icon icon = ImageIconRenderer.icon(res, size, false);
                if (icon != null) return icon;
            }
        }
        // fallback to simple drawn shapes
        return new Icon() {
            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int s = size;
                g2.setColor(AeroTheme.TEXT_PRIMARY);
                g2.setStroke(new BasicStroke(Math.max(1.6f, s/10f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int m = 2;
                int w = s, h = s;
                int cx = x + w/2, cy = y + h/2;
                switch (name) {
                    case "plus" -> { g2.drawLine(x + m, cy, x + w - m, cy); g2.drawLine(cx, y + m, cx, y + h - m); }
                    case "close" -> { g2.drawLine(x + m, y + m, x + w - m, y + h - m); g2.drawLine(x + m, y + h - m, x + w - m, y + m); }
                    case "save" -> { g2.drawRoundRect(x + m, y + m, w - 2*m, h - 2*m, 3, 3); int header = y + m + (h - 2*m)/3; g2.drawLine(x + m, header, x + w - m, header); int lw = (w - 2*m)/3; g2.drawRect(x + m + 2, y + m + 2, lw, lw); }
                    case "list" -> { int left = x + m, right = x + w - m; int y1 = y + m + 2; int y2 = cy; int y3 = y + h - m - 2; g2.drawLine(left, y1, right, y1); g2.drawLine(left, y2, right, y2); g2.drawLine(left, y3, right, y3); }
                    case "delete" -> { try { main.ui.components.icons.VectorIconPainter.paint(g2, "delete", x, y, s); } catch (Throwable ignored) {} }
                    case "pin" -> {
                        int r = Math.max(10, s-6);
                        int hx = cx, hy = cy-2;
                        Paint head = new RadialGradientPaint(new Point(hx-2, hy-2), r/2f,
                                new float[]{0f, 1f}, new Color[]{new Color(200,200,200), new Color(130,130,130)});
                        g2.setPaint(head);
                        g2.fillOval(hx - r/2, hy - r/2, r, r);
                        g2.setColor(new Color(255,255,255,150));
                        g2.drawOval(hx - r/2, hy - r/2, r, r);
                        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2.setColor(new Color(100,100,100));
                        g2.drawLine(hx, hy + r/2 - 2, hx, hy + r/2 + 6);
                    }
                }
                g2.dispose();
            }
        };
    }

    // Popup menu to list, load, and delete notes
    private void showNotesMenu(Component invoker) {
        loadNotes(); // refresh from disk in case external changes
        // Use a borderless transparent window (no OS shadow/frame)
        JWindow menu = new JWindow(owner);
        menu.setAlwaysOnTop(true);
        try { menu.setBackground(new Color(0,0,0,0)); } catch (Throwable ignore) {}
        ((JComponent) menu.getContentPane()).setOpaque(false);
        DefaultListModel<Note> model = new DefaultListModel<>();
        for (Note n : notes) model.addElement(n);
        JList<Note> list = new JList<>(model);
        list.setVisibleRowCount(8);
        list.setOpaque(false);
        list.setBackground(new Color(0,0,0,0));
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object val, int idx, boolean sel, boolean foc) {
                JLabel lbl = (JLabel) super.getListCellRendererComponent(l, val, idx, sel, foc);
                Note n = (Note) val;
                boolean pinned = main.core.service.SettingsStore.get().isStickyPinned(n.id);
                lbl.setText(n.toString());
                if (pinned) {
                    String res = ImageIconRenderer.mapIdToResource("sticky_widget");
                    lbl.setIcon(res != null ? ImageIconRenderer.icon(res, 14, false) : null);
                } else {
                    lbl.setIcon(null);
                }
                lbl.setOpaque(false);
                lbl.setForeground(Color.DARK_GRAY);
                lbl.setBorder(BorderFactory.createEmptyBorder(4,8,4,8));
                return lbl;
            }
        });
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int i = list.locationToIndex(e.getPoint());
                    if (i >= 0) { setCurrent(model.get(i)); menu.setVisible(false); }
                }
            }
        });
        JScrollPane scroller = new JScrollPane(list);
        scroller.setPreferredSize(new Dimension(220, 180));
        scroller.setOpaque(false);
        scroller.getViewport().setOpaque(false);
        scroller.setBorder(BorderFactory.createEmptyBorder());
        scroller.setViewportBorder(null);
        try {
            scroller.getVerticalScrollBar().setUI(new ModernScrollBarUI());
            scroller.getHorizontalScrollBar().setUI(new ModernScrollBarUI());
        } catch (Throwable ignore) {}

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        controls.setOpaque(false);
        RoundedButton renameBtn = new RoundedButton("Rename");
        RoundedButton loadBtn = new RoundedButton("Load");
        RoundedButton delBtn = new RoundedButton("Delete");
        RoundedButton pinBtn = new RoundedButton("Pin/Unpin");
        loadBtn.addActionListener(e -> {
            Note sel = list.getSelectedValue();
            if (sel != null) { setCurrent(sel); menu.setVisible(false); }
        });
        renameBtn.addActionListener(e -> {
            java.util.List<Note> sel = list.getSelectedValuesList();
            if (sel == null || sel.size() != 1) return;
            Note n = sel.get(0);
            String currentTitle = n.toString();
            String name = JOptionPane.showInputDialog(dialog, "Rename note:", currentTitle);
            if (name != null) {
                applyTitle(n, name.trim());
                saveNote(n);
                list.repaint();
            }
        });
        delBtn.addActionListener(e -> {
            java.util.List<Note> sel = list.getSelectedValuesList();
            if (sel != null && !sel.isEmpty()) {
                int res = JOptionPane.showConfirmDialog(dialog,
                        "Delete " + sel.size() + " selected note(s)?",
                        "Confirm delete",
                        JOptionPane.YES_NO_OPTION);
                if (res != JOptionPane.YES_OPTION) return;
                // Delete all selected notes
                for (Note n : sel) {
                    current = n;
                    deleteCurrent();
                    model.removeElement(n);
                }
                if (!notes.isEmpty()) setCurrent(notes.get(0));
            }
        });
        pinBtn.addActionListener(e -> {
            java.util.List<Note> sel = list.getSelectedValuesList();
            if (sel == null || sel.isEmpty()) return;
            boolean anyPinned = false; for (Note n : sel) if (main.core.service.SettingsStore.get().isStickyPinned(n.id)) { anyPinned = true; break; }
            for (Note n : sel) main.core.service.SettingsStore.get().pinSticky(n.id, !anyPinned);
            main.core.service.SettingsStore.get().save();
            list.repaint();
        });
        // Delete key to remove selected
        list.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("DELETE"), "delNotes");
        list.getActionMap().put("delNotes", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { delBtn.doClick(); }
        });
        // Enable/disable buttons based on selection
        list.addListSelectionListener(e -> {
            int count = list.getSelectedIndices().length;
            loadBtn.setEnabled(count == 1);
            renameBtn.setEnabled(count == 1);
            delBtn.setEnabled(count >= 1);
        });
        loadBtn.setEnabled(false);
        renameBtn.setEnabled(false);
        delBtn.setEnabled(false);
        controls.add(renameBtn);
        controls.add(loadBtn);
        controls.add(pinBtn);
        controls.add(delBtn);

        // Header with title and close button inside a rounded panel
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Notes");
        title.setBorder(BorderFactory.createEmptyBorder(6,10,6,6));
        JButton close = new JButton(iconFor("close", 12));
        close.setOpaque(false);
        close.setContentAreaFilled(false);
        close.setBorderPainted(false);
        close.setFocusPainted(false);
        close.addActionListener(e -> menu.dispose());
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        right.setOpaque(false);
        right.add(close);
        header.add(title, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);

        FrostedGlassPanel panel = new FrostedGlassPanel(new BorderLayout(), 12);
        panel.setBorder(BorderFactory.createEmptyBorder(4,4,4,4));
        panel.add(header, BorderLayout.NORTH);
        panel.add(scroller, BorderLayout.CENTER);
        panel.add(controls, BorderLayout.SOUTH);
        menu.getContentPane().setLayout(new BorderLayout());
        menu.getContentPane().add(panel, BorderLayout.CENTER);
        menu.pack();
        // Position next to invoker (below it)
        try {
            Point onScreen = invoker.getLocationOnScreen();
            menu.setLocation(onScreen.x, onScreen.y + invoker.getHeight());
        } catch (IllegalComponentStateException ex) {
            menu.setLocationRelativeTo(owner);
        }
        // Close when focus is lost (click outside)
        menu.addWindowFocusListener(new WindowAdapter() {
            @Override public void windowLostFocus(WindowEvent e) { menu.dispose(); }
        });
        menu.setVisible(true);
    }

    // Update the note's display title by replacing the first non-empty line of text
    private void applyTitle(Note n, String title) {
        if (n == null) return;
        String text = n.text == null ? "" : n.text;
        String[] lines = text.split("\r?\n", -1);
        int firstIdx = -1;
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].trim().isEmpty()) { firstIdx = i; break; }
        }
        java.util.List<String> out = new ArrayList<>();
        if (firstIdx == -1) {
            // No content yet; set title as the first line
            out.add(title);
            // keep rest if any (all empty)
            for (String s : lines) out.add(s);
        } else {
            for (int i = 0; i < lines.length; i++) {
                if (i == firstIdx) out.add(title);
                else out.add(lines[i]);
            }
        }
        // Reconstruct preserving trailing newline behavior
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < out.size(); i++) {
            sb.append(out.get(i));
            if (i < out.size() - 1) sb.append('\n');
        }
        n.text = sb.toString();
        if (current == n) area.setText(n.text);
    }

    // -------- Notes persistence --------
    private File notesDir() {
        File dir = new File(AppDirectories.folder(AppDirectories.Type.SETTINGS), "stickies");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private void loadNotes() {
        notes.clear();
        File dir = notesDir();
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".txt"));
        if (files != null) {
            Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
            for (File f : files) {
                Note n = readNoteFile(f);
                if (n != null) notes.add(n);
            }
        }
    }

    private Note readNoteFile(File f) {
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String first = r.readLine();
            Color col = new Color(255,255,170);
            StringBuilder sb = new StringBuilder();
            if (first != null && first.startsWith("COLOR ")) {
                String[] parts = first.substring(6).split(",");
                if (parts.length == 3) {
                    int rr = Integer.parseInt(parts[0].trim());
                    int gg = Integer.parseInt(parts[1].trim());
                    int bb = Integer.parseInt(parts[2].trim());
                    col = new Color(rr, gg, bb);
                }
            } else if (first != null) {
                sb.append(first).append('\n');
            }
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            Note n = new Note();
            n.id = idFromName(f.getName());
            n.file = f;
            n.color = col;
            String body = sb.toString();
            if (body.trim().isEmpty()) {
                n.text = "<html><body style='font-family:Serif;font-size:14pt;margin:6px;'></body></html>";
            } else if (!body.toLowerCase(java.util.Locale.ROOT).contains("<html")) {
                // migrate plain text -> html
                String esc = body
                        .replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\n", "<br>");
                n.text = "<html><body style='font-family:Serif;font-size:14pt;margin:6px;'>" + esc + "</body></html>";
            } else {
                n.text = body;
            }
            return n;
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private void saveCurrent() {
        if (current == null) return;
        saveNote(current);
    }

    private void saveNote(Note n) {
        try {
            if (n.file == null) {
                n.id = (n.id == null) ? java.util.UUID.randomUUID().toString() : n.id;
                n.file = new File(notesDir(), n.id + ".txt");
            }
            try (PrintWriter w = new PrintWriter(new FileWriter(n.file))) {
                w.println("COLOR " + n.color.getRed() + "," + n.color.getGreen() + "," + n.color.getBlue());
                if (n.text != null && !n.text.isEmpty()) w.print(n.text);
            }
            // remember last
            if (current == n) { meta.setProperty("lastNoteId", n.id == null ? "" : n.id); saveMeta(); }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void deleteCurrent() {
        if (current == null) return;
        Note toRemove = current;
        int idx = notes.indexOf(toRemove);
        if (toRemove.file != null && toRemove.file.exists()) {
            if (!toRemove.file.delete()) {
                System.err.println("Warning: failed to delete sticky file: " + toRemove.file);
            }
        }
        notes.remove(toRemove);
        if (!notes.isEmpty()) {
            int next = Math.max(0, Math.min(idx, notes.size()-1));
            setCurrent(notes.get(next));
        } else {
            createNewNote();
        }
    }

    private void createNewNote() {
        Note n = new Note();
        n.id = java.util.UUID.randomUUID().toString();
        n.color = new Color(255, 255, 170);
        n.text = "";
        notes.add(0, n);
        setCurrent(n);
        saveNote(n); // create file immediately
    }

    private void setCurrent(Note n) {
        current = n;
        area.setText(n.text != null ? n.text : "<html><body style='font-family:Serif;font-size:14pt;margin:6px;'></body></html>");
        textWrap.repaint();
        if (n != null && n.id != null) { meta.setProperty("lastNoteId", n.id); saveMeta(); }
    }

    private void syncText() {
        if (current != null) current.text = area.getText();
    }

    private void chooseColor() {
        if (current == null) return;
        Color c = JColorChooser.showDialog(dialog, "Sticky Color", current.color);
        if (c != null) {
            current.color = c;
            textWrap.repaint();
        }
    }

    private void togglePinCurrent(){
        if (current == null || current.id == null) return;
        boolean nowPinned = !main.core.service.SettingsStore.get().isStickyPinned(current.id);
        main.core.service.SettingsStore.get().pinSticky(current.id, nowPinned);
        main.core.service.SettingsStore.get().save();
    }

    // Public API for MainMenu to open a specific sticky
    public void openAndFocus(String noteId){
        ensureDialog();
        loadNotes();
        Note found = null;
        for (Note n : notes) if (n.id != null && n.id.equals(noteId)) { found = n; break; }
        if (found != null) setCurrent(found);
        if (!enabled) start();
        if (dialog != null) { dialog.setVisible(true); dialog.toFront(); }
    }

    // Small quick-pick palette window with common sticky colors
    private void showColorPalette(Component invoker) {
        JWindow pal = new JWindow(owner);
        pal.setAlwaysOnTop(true);
        FrostedGlassPanel row = new FrostedGlassPanel(new FlowLayout(FlowLayout.LEFT, 6, 6), 12);
        row.setBorder(BorderFactory.createEmptyBorder(6,6,6,6));
        Color[] colors = new Color[]{
                new Color(255,255,170), // yellow
                new Color(196,255,196), // mint
                new Color(196,220,255), // blue
                new Color(255,205,228), // pink
                new Color(235,210,255)  // violet
        };
        for (Color c : colors) {
            JButton sw = new JButton();
            sw.setPreferredSize(new Dimension(22, 22));
            sw.setBorder(BorderFactory.createLineBorder(new Color(180,180,180)));
            sw.setBackground(c);
            sw.addActionListener(e -> { if (current != null){ current.color = c; textWrap.repaint(); saveCurrent(); } pal.dispose(); });
            row.add(sw);
        }
        JButton more = new JButton(iconFor("settings", 14));
        more.setToolTipText("More colors…");
        more.addActionListener(e -> { pal.dispose(); chooseColor(); });
        row.add(more);
        pal.getContentPane().add(row);
        pal.pack();
        try {
            Point p = invoker.getLocationOnScreen();
            pal.setLocation(p.x, p.y + invoker.getHeight());
        } catch (IllegalComponentStateException ex) { pal.setLocationRelativeTo(owner); }
        pal.setVisible(true);
    }

    private void scheduleAutosave(){
        try { autosaveTimer.restart(); } catch (Throwable ignored) {}
    }

    private void loadMeta(){
        try {
            metaFile = new File(notesDir(), "meta.properties");
            if (metaFile.exists()) try (FileInputStream in = new FileInputStream(metaFile)) { meta.load(in); }
        } catch (Exception ignored) {}
    }

    private void saveMeta(){
        try {
            if (dialog != null) {
                meta.setProperty("x", String.valueOf(dialog.getX()));
                meta.setProperty("y", String.valueOf(dialog.getY()));
                meta.setProperty("w", String.valueOf(dialog.getWidth()));
                meta.setProperty("h", String.valueOf(dialog.getHeight()));
            }
            if (metaFile == null) metaFile = new File(notesDir(), "meta.properties");
            try (FileOutputStream out = new FileOutputStream(metaFile)) { meta.store(out, "Sticky widget"); }
        } catch (Exception ignored) {}
    }

    private static int parseInt(String s, int def){
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    // Utility: derive ID from file name like "<id>.txt"
    private static String idFromName(String name) {
        if (name == null) return null;
        int dot = name.lastIndexOf('.');
        return (dot > 0) ? name.substring(0, dot) : name;
    }
}
