package main.ui.widgets;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import main.util.AppDirectories;

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
    private JTextArea area;
    private java.util.List<Note> notes = new ArrayList<>();
    private Note current;
    private boolean enabled = false;

    public IdeaStickyWidget(JFrame owner) { this.owner = owner; }

    @Override public void start() {
        if (enabled) return;
        enabled = true;
        ensureDialog();
        loadNotes();
        if (notes.isEmpty()) {
            createNewNote();
        } else {
            setCurrent(notes.get(0));
        }
        dialog.setVisible(true);
        dialog.toFront();
    }

    @Override public void stop() {
        enabled = false;
        if (dialog != null) dialog.setVisible(false);
    }

    @Override public boolean isEnabled() { return enabled; }

    @Override public void setEnabled(boolean enable) { if (enable) start(); else stop(); }

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

        // Text area in colored wrapper
        area = new JTextArea(8, 26);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font("Serif", Font.PLAIN, 14));
        area.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { syncText(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { syncText(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { syncText(); }
        });
        JScrollPane sp = new JScrollPane(area);
        sp.setBorder(BorderFactory.createEmptyBorder());
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
        JButton btnColor = makeHeaderButton(iconFor("gear", 16), "Color");
        JButton btnSave  = makeHeaderButton(iconFor("save", 16), "Save");
        JButton btnNew   = makeHeaderButton(iconFor("plus", 16), "New note");
        JButton btnClose = makeHeaderButton(iconFor("close", 16), "Close");
        Dimension tiny = new Dimension(22, 22);
        for (JButton b : new JButton[]{btnColor, btnSave, btnNew, btnClose}) {
            b.setPreferredSize(tiny);
            b.setMinimumSize(tiny);
            b.setMaximumSize(tiny);
            b.setFont(b.getFont().deriveFont(Font.BOLD, 11f));
        }
        btnColor.addActionListener(e -> chooseColor());
        btnSave.addActionListener(e -> saveCurrent());
        btnNew.addActionListener(e -> createNewNote());
        btnClose.addActionListener(e -> stop());
        topRight.add(btnColor);
        topRight.add(btnSave);
        topRight.add(btnNew);
        topRight.add(btnClose);
        textWrap.add(topRight, BorderLayout.NORTH);
        textWrap.add(sp, BorderLayout.CENTER);
        content.add(textWrap, BorderLayout.CENTER);

        // Drag to move
        MouseAdapter drag = new MouseAdapter() {
            Point offset;
            @Override public void mousePressed(MouseEvent e) { offset = e.getPoint(); }
            @Override public void mouseDragged(MouseEvent e) {
                Point p = e.getLocationOnScreen();
                dialog.setLocation(p.x - offset.x, p.y - offset.y);
            }
        };
        content.addMouseListener(drag);
        content.addMouseMotionListener(drag);
        textWrap.addMouseListener(drag);
        textWrap.addMouseMotionListener(drag);

        dialog.setContentPane(content);
        dialog.setSize(300, 260);
        Point loc = owner != null ? owner.getLocationOnScreen() : new Point(100, 100);
        Dimension ownerSize = owner != null ? owner.getSize() : Toolkit.getDefaultToolkit().getScreenSize();
        dialog.setLocation(loc.x + ownerSize.width - dialog.getWidth() - 24, loc.y + 340);
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

    // Vector icon factory for mini buttons
    private Icon iconFor(String name, int size) {
        final int s = Math.max(8, size);
        return new Icon() {
            @Override public int getIconWidth() { return s; }
            @Override public int getIconHeight() { return s; }
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.BLACK);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int m = 2;
                int w = s, h = s;
                int cx = x + w/2, cy = y + h/2;
                switch (name) {
                    case "plus": {
                        g2.drawLine(x + m, cy, x + w - m, cy);
                        g2.drawLine(cx, y + m, cx, y + h - m);
                        break;
                    }
                    case "close": {
                        g2.drawLine(x + m, y + m, x + w - m, y + h - m);
                        g2.drawLine(x + m, y + h - m, x + w - m, y + m);
                        break;
                    }
                    case "save": {
                        // Floppy: outer square + notch + label line
                        g2.drawRoundRect(x + m, y + m, w - 2*m, h - 2*m, 3, 3);
                        // header notch
                        int header = y + m + (h - 2*m)/3;
                        g2.drawLine(x + m, header, x + w - m, header);
                        // inner label rectangle
                        int lw = (w - 2*m)/3;
                        g2.drawRect(x + m + 2, y + m + 2, lw, lw);
                        break;
                    }
                    case "gear": {
                        int r = Math.min(w, h)/2 - 4;
                        g2.drawOval(cx - r, cy - r, 2*r, 2*r);
                        int r2 = Math.max(1, r - 4);
                        g2.drawOval(cx - r2, cy - r2, 2*r2, 2*r2);
                        for (int i = 0; i < 8; i++) {
                            double ang = i * Math.PI / 4.0;
                            int x1 = (int) (cx + Math.cos(ang) * (r + 1));
                            int y1 = (int) (cy + Math.sin(ang) * (r + 1));
                            int x2 = (int) (cx + Math.cos(ang) * (r + 3));
                            int y2 = (int) (cy + Math.sin(ang) * (r + 3));
                            g2.drawLine(x1, y1, x2, y2);
                        }
                        break;
                    }
                }
                g2.dispose();
            }
        };
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
            n.text = sb.toString();
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
        area.setText(n.text != null ? n.text : "");
        textWrap.repaint();
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

    // Utility: derive ID from file name like "<id>.txt"
    private static String idFromName(String name) {
        if (name == null) return null;
        int dot = name.lastIndexOf('.');
        return (dot > 0) ? name.substring(0, dot) : name;
    }
}
