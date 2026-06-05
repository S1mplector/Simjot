/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.features.widgets;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.border.LineBorder;
import main.infrastructure.io.AppDirectories;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.containers.FrostedGlassPanel;
import main.ui.theme.Theme;

/**
 * A lightweight calendar widget to jot down things to look forward to on specific dates.
 */
public class LookingForwardCalendarWidget implements Widget {
    private static final DateTimeFormatter DATE_KEY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter PRETTY_FMT = DateTimeFormatter.ofPattern("EEE, MMM d");
    private final JFrame owner;
    private JDialog dialog;
    private boolean enabled = false;
    private LocalDate monthCursor = LocalDate.now().withDayOfMonth(1);
    private LocalDate selectedDate = LocalDate.now();
    private final Map<LocalDate, List<String>> entries = new HashMap<>();
    private final Map<LocalDate, JButton> dayButtons = new HashMap<>();
    private final DefaultListModel<String> dayListModel = new DefaultListModel<>();
    private JLabel monthLabel;
    private JLabel selectionLabel;
    private JPanel calendarGrid;
    private JTextField inputField;
    private File storeFile;

    public LookingForwardCalendarWidget(JFrame owner) {
        this.owner = owner;
    }

    @Override
    public void start() {
        if (enabled) return;
        enabled = true;
        ensureDialog();
        loadEntries();
        rebuildCalendar();
        selectDate(selectedDate);
        dialog.setVisible(true);
        dialog.toFront();
    }

    @Override
    public void stop() {
        enabled = false;
        saveEntries();
        if (dialog != null) dialog.setVisible(false);
    }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void setEnabled(boolean enable) { if (enable) start(); else stop(); }

    @Override
    public String getName() { return "Looking Forward"; }

    @Override
    public String getIconId() { return "calendar"; }

    private void ensureDialog() {
        if (dialog != null) return;
        storeFile = new File(AppDirectories.folder(AppDirectories.Type.SETTINGS), "looking_forward_calendar.txt");

        dialog = new JDialog(owner, "Looking Forward", false);
        dialog.setUndecorated(true);
        dialog.setAlwaysOnTop(true);
        dialog.getRootPane().putClientProperty("apple.awt.draggableWindowBackground", Boolean.FALSE);
        dialog.setBackground(new Color(0, 0, 0, 0));

        FrostedGlassPanel root = new FrostedGlassPanel(new BorderLayout(10, 10), 16);
        root.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        // Header with month navigation
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BorderLayout(8, 0));
        JButton prev = navButton(true);
        JButton next = navButton(false);
        monthLabel = new JLabel("", SwingConstants.CENTER);
        monthLabel.setFont(monthLabel.getFont().deriveFont(Font.BOLD, 16f));
        monthLabel.setForeground(Theme.isPlainWhite() ? Color.DARK_GRAY : Color.BLACK);
        header.add(prev, BorderLayout.WEST);
        header.add(monthLabel, BorderLayout.CENTER);
        header.add(next, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        // Calendar + list layout
        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.X_AXIS));

        calendarGrid = new JPanel(new GridLayout(0, 7, 4, 4));
        calendarGrid.setOpaque(false);
        calendarGrid.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 12));
        center.add(calendarGrid);

        JPanel side = new JPanel();
        side.setOpaque(false);
        side.setLayout(new BorderLayout(6, 6));
        selectionLabel = new JLabel("", SwingConstants.LEFT);
        selectionLabel.setFont(selectionLabel.getFont().deriveFont(Font.BOLD, 14f));
        side.add(selectionLabel, BorderLayout.NORTH);

        JList<String> dayList = new JList<>(dayListModel);
        dayList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroller = new JScrollPane(dayList);
        scroller.setBorder(new LineBorder(new Color(210, 210, 210)));
        side.add(scroller, BorderLayout.CENTER);

        JPanel addRow = new JPanel();
        addRow.setOpaque(false);
        addRow.setLayout(new BorderLayout(6, 0));
        inputField = new JTextField();
        inputField.addActionListener(e -> addEntry());
        RoundedButton addBtn = new RoundedButton("Add");
        addBtn.addActionListener(e -> addEntry());
        RoundedButton removeBtn = new RoundedButton("Remove");
        removeBtn.addActionListener(e -> {
            int idx = dayList.getSelectedIndex();
            if (idx >= 0) {
                removeEntry(idx);
            }
        });
        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(addBtn);
        buttons.add(Box.createRigidArea(new Dimension(6, 0)));
        buttons.add(removeBtn);
        addRow.add(inputField, BorderLayout.CENTER);
        addRow.add(buttons, BorderLayout.EAST);
        side.add(addRow, BorderLayout.SOUTH);

        center.add(side);
        root.add(center, BorderLayout.CENTER);

        dialog.setContentPane(root);
        dialog.setSize(520, 360);
        Point loc = owner != null ? owner.getLocationOnScreen() : new Point(100, 100);
        Dimension ownerSize = owner != null ? owner.getSize() : java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        dialog.setLocation(loc.x + ownerSize.width - dialog.getWidth() - 24, loc.y + 140);

        // Dragging by header
        MouseAdapter drag = new MouseAdapter() {
            Point offset;
            @Override public void mousePressed(MouseEvent e) { offset = e.getPoint(); }
            @Override public void mouseDragged(MouseEvent e) {
                if (offset == null) return;
                Point p = e.getLocationOnScreen();
                dialog.setLocation(p.x - offset.x, p.y - offset.y);
            }
        };
        header.addMouseListener(drag);
        header.addMouseMotionListener(drag);

        prev.addActionListener(e -> {
            monthCursor = monthCursor.minusMonths(1);
            clampSelectedToMonth();
            rebuildCalendar();
        });
        next.addActionListener(e -> {
            monthCursor = monthCursor.plusMonths(1);
            clampSelectedToMonth();
            rebuildCalendar();
        });
    }

    private void rebuildCalendar() {
        dayButtons.clear();
        calendarGrid.removeAll();

        String monthTitle = monthCursor.getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()) +
                " " + monthCursor.getYear();
        monthLabel.setText(monthTitle);

        String[] days = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        for (String d : days) {
            JLabel lbl = new JLabel(d, SwingConstants.CENTER);
            lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 12f));
            lbl.setForeground(new Color(70, 70, 70));
            calendarGrid.add(lbl);
        }

        YearMonth ym = YearMonth.from(monthCursor);
        int daysInMonth = ym.lengthOfMonth();
        int offset = monthCursor.getDayOfWeek().getValue() - 1; // Monday = 1
        for (int i = 0; i < offset; i++) calendarGrid.add(Box.createGlue());

        Color baseBg = new Color(246, 248, 252);
        Color hoverBg = new Color(230, 238, 250);
        Color accent = new Color(70, 130, 210);

        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = ym.atDay(day);
            JButton b = new JButton(String.valueOf(day)) {
                @Override protected void paintComponent(java.awt.Graphics g) {
                    super.paintComponent(g);
                    boolean hasItems = hasEntries(date);
                    if (hasItems) {
                        java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(accent);
                        int s = 6;
                        g2.fillOval(getWidth() - s - 6, getHeight() - s - 6, s, s);
                        g2.dispose();
                    }
                }
            };
            b.setFocusPainted(false);
            b.setOpaque(true);
            b.setBackground(baseBg);
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { if (!date.equals(selectedDate)) b.setBackground(hoverBg); }
                @Override public void mouseExited(MouseEvent e) { if (!date.equals(selectedDate)) b.setBackground(baseBg); }
            });
            b.addActionListener(e -> selectDate(date));
            calendarGrid.add(b);
            dayButtons.put(date, b);
        }

        calendarGrid.revalidate();
        calendarGrid.repaint();
        updateSelectionHighlight();
    }

    private void selectDate(LocalDate date) {
        if (date == null) return;
        selectedDate = date;
        if (!YearMonth.from(date).equals(YearMonth.from(monthCursor))) {
            monthCursor = date.withDayOfMonth(1);
            rebuildCalendar();
        }
        selectionLabel.setText("Looking forward to · " + PRETTY_FMT.format(selectedDate));
        refreshDayList();
        updateSelectionHighlight();
    }

    private void updateSelectionHighlight() {
        Color selectedBg = new Color(208, 226, 248);
        Color baseBg = new Color(246, 248, 252);
        for (Map.Entry<LocalDate, JButton> e : dayButtons.entrySet()) {
            JButton b = e.getValue();
            if (b == null) continue;
            if (e.getKey().equals(selectedDate)) {
                b.setBackground(selectedBg);
                b.setBorder(new LineBorder(new Color(90, 140, 210)));
            } else {
                b.setBackground(baseBg);
                b.setBorder(new LineBorder(new Color(210, 210, 210)));
            }
        }
    }

    private void refreshDayList() {
        dayListModel.clear();
        List<String> list = entries.get(selectedDate);
        if (list != null) {
            for (String s : list) dayListModel.addElement(s);
        }
    }

    private void addEntry() {
        String text = inputField.getText();
        if (text == null) return;
        text = text.trim();
        if (text.isEmpty()) return;
        entries.computeIfAbsent(selectedDate, k -> new ArrayList<>()).add(text);
        inputField.setText("");
        refreshDayList();
        updateSelectionHighlight();
        saveEntries();
    }

    private void removeEntry(int index) {
        List<String> list = entries.get(selectedDate);
        if (list == null || index < 0 || index >= list.size()) return;
        list.remove(index);
        if (list.isEmpty()) {
            entries.remove(selectedDate);
        }
        refreshDayList();
        updateSelectionHighlight();
        saveEntries();
    }

    private boolean hasEntries(LocalDate date) {
        List<String> list = entries.get(date);
        return list != null && !list.isEmpty();
    }

    private void clampSelectedToMonth() {
        YearMonth ym = YearMonth.from(monthCursor);
        int day = Math.min(selectedDate.getDayOfMonth(), ym.lengthOfMonth());
        selectedDate = ym.atDay(day);
    }

    private void loadEntries() {
        entries.clear();
        if (storeFile == null || !storeFile.exists()) return;
        try (BufferedReader r = new BufferedReader(new FileReader(storeFile))) {
            String line;
            while ((line = r.readLine()) != null) {
                int idx = line.indexOf('|');
                if (idx <= 0) continue;
                String dateStr = line.substring(0, idx).trim();
                String text = line.substring(idx + 1).trim();
                if (text.isEmpty()) continue;
                try {
                    LocalDate d = LocalDate.parse(dateStr, DATE_KEY_FMT);
                    entries.computeIfAbsent(d, k -> new ArrayList<>()).add(text);
                } catch (Exception ignored) {}
            }
        } catch (IOException ignored) {}
    }

    private void saveEntries() {
        if (storeFile == null) return;
        try {
            File parent = storeFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
        } catch (Throwable ignored) {}
        try (PrintWriter out = new PrintWriter(new FileWriter(storeFile))) {
            for (Map.Entry<LocalDate, List<String>> e : entries.entrySet()) {
                LocalDate d = e.getKey();
                List<String> vals = e.getValue();
                if (vals == null || vals.isEmpty()) continue;
                for (String s : vals) {
                    if (s == null || s.trim().isEmpty()) continue;
                    out.println(DATE_KEY_FMT.format(d) + "|" + s.replace('\n', ' ').trim());
                }
            }
        } catch (IOException ignored) {}
    }

    private JButton navButton(boolean isPrev) {
        JButton b = new JButton(isPrev ? "<" : ">");
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setBackground(new Color(245, 248, 252));
        b.setBorder(new LineBorder(new Color(200, 200, 200)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(36, 30));
        b.setFont(b.getFont().deriveFont(Font.BOLD, 14f));
        return b;
    }
}
