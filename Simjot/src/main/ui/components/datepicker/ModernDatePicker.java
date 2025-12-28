package main.ui.components.datepicker;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

import main.ui.theme.aero.AeroTheme;

/**
 * Modern Calendar Picker with sleek design matching Simjot's Aero theme.
 * Features: smooth animations, elegant styling, month/year dropdowns, today indicator
 */
public class ModernDatePicker extends JPanel {
    
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    private static final DateTimeFormatter INPUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Color ACCENT_COLOR = new Color(0, 120, 215);
    private static final Color ACCENT_LIGHT = new Color(0, 120, 215, 40);
    private static final Color HOVER_BG = new Color(245, 248, 252);
    private static final Color TODAY_RING = new Color(0, 120, 215, 100);
    
    private LocalDate selectedDate;
    private JTextField dateField;
    private JButton calendarButton;
    private JPopupMenu calendarPopup;
    private ModernCalendarPanel calendarPanel;
    private ActionListener dateChangeListener;
    
    public ModernDatePicker() {
        this(null);
    }
    
    public ModernDatePicker(LocalDate initialDate) {
        this.selectedDate = initialDate;
        setLayout(new BorderLayout(4, 0));
        setOpaque(false);
        initComponents();
        updateDisplay();
    }
    
    private void initComponents() {
        // Date text field with modern styling
        dateField = new StyledTextField();
        dateField.setEditable(true);
        dateField.addActionListener(e -> parseDateFromField());
        dateField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { parseDateFromField(); }
        });
        add(dateField, BorderLayout.CENTER);
        
        // Calendar toggle button
        calendarButton = new CalendarToggleButton();
        calendarButton.addActionListener(e -> showCalendarPopup());
        add(calendarButton, BorderLayout.EAST);
        
        initCalendarPopup();
    }
    
    private void initCalendarPopup() {
        calendarPopup = new JPopupMenu() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16));
                g2.dispose();
            }
        };
        calendarPopup.setBorder(BorderFactory.createCompoundBorder(
            new ShadowBorder(8, new Color(0, 0, 0, 30)),
            BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));
        calendarPopup.setOpaque(false);
        
        calendarPanel = new ModernCalendarPanel(date -> {
            setSelectedDate(date);
            calendarPopup.setVisible(false);
        });
        calendarPopup.add(calendarPanel);
    }
    
    private void showCalendarPopup() {
        calendarPanel.setDisplayedMonth(selectedDate != null ? selectedDate : LocalDate.now());
        calendarPanel.setSelectedDate(selectedDate);
        
        // Position popup below the field
        Point loc = dateField.getLocationOnScreen();
        calendarPopup.setLocation(loc.x, loc.y + dateField.getHeight() + 4);
        calendarPopup.setVisible(true);
    }
    
    private void parseDateFromField() {
        String text = dateField.getText().trim();
        if (text.isEmpty() || text.equalsIgnoreCase("Select date...")) {
            setSelectedDate(null);
            return;
        }
        try {
            setSelectedDate(LocalDate.parse(text, INPUT_FORMAT));
        } catch (DateTimeParseException e1) {
            try {
                setSelectedDate(LocalDate.parse(text, DATE_FORMAT));
            } catch (DateTimeParseException e2) {
                updateDisplay(); // Revert to previous
            }
        }
    }
    
    private void updateDisplay() {
        if (selectedDate != null) {
            dateField.setText(selectedDate.format(DATE_FORMAT));
            dateField.setForeground(AeroTheme.TEXT_PRIMARY);
        } else {
            dateField.setText("Select date...");
            dateField.setForeground(new Color(160, 160, 160));
        }
    }
    
    // --- Public API ---
    
    public LocalDate getSelectedDate() { return selectedDate; }
    
    public void setSelectedDate(LocalDate date) {
        LocalDate oldDate = this.selectedDate;
        this.selectedDate = date;
        updateDisplay();
        if (dateChangeListener != null && 
            ((oldDate == null) != (date == null) || (oldDate != null && !oldDate.equals(date)))) {
            dateChangeListener.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "dateChanged"));
        }
    }
    
    public void setDateChangeListener(ActionListener listener) {
        this.dateChangeListener = listener;
    }
    
    public boolean hasDate() { return selectedDate != null; }
    
    /** Get just the calendar panel for standalone use */
    public static ModernCalendarPanel createStandaloneCalendar(java.util.function.Consumer<LocalDate> onSelect) {
        return new ModernCalendarPanel(onSelect);
    }
    
    // ========================= STYLED TEXT FIELD =========================
    
    private static class StyledTextField extends JTextField {
        StyledTextField() {
            setOpaque(false);
            setBorder(new EmptyBorder(8, 12, 8, 12));
            setFont(AeroTheme.defaultFont().deriveFont(13f));
            setPreferredSize(new Dimension(140, 36));
        }
        
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            // Background
            g2.setColor(Color.WHITE);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
            
            // Border
            g2.setColor(hasFocus() ? ACCENT_COLOR : new Color(200, 200, 200));
            g2.setStroke(new BasicStroke(hasFocus() ? 1.5f : 1f));
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth()-1, getHeight()-1, 10, 10));
            
            g2.dispose();
            super.paintComponent(g);
        }
    }
    
    // ========================= CALENDAR TOGGLE BUTTON =========================
    
    private static class CalendarToggleButton extends JButton {
        CalendarToggleButton() {
            setPreferredSize(new Dimension(36, 36));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setToolTipText("Open calendar");
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            int w = getWidth(), h = getHeight();
            
            // Background
            if (getModel().isPressed()) g2.setColor(new Color(220, 230, 240));
            else if (getModel().isRollover()) g2.setColor(HOVER_BG);
            else g2.setColor(new Color(248, 248, 248));
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, 10, 10));
            
            // Border
            g2.setColor(new Color(200, 200, 200));
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w-1, h-1, 10, 10));
            
            // Draw calendar icon
            int iconSize = 18;
            int x = (w - iconSize) / 2;
            int y = (h - iconSize) / 2;
            drawCalendarIcon(g2, x, y, iconSize);
            
            g2.dispose();
        }
        
        private void drawCalendarIcon(Graphics2D g2, int x, int y, int size) {
            g2.setColor(new Color(80, 80, 80));
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            
            // Calendar outline
            int pad = 2;
            g2.drawRoundRect(x + pad, y + pad + 3, size - pad*2, size - pad*2 - 3, 3, 3);
            
            // Top binding rings
            int ringY = y + pad + 1;
            g2.drawLine(x + 5, ringY, x + 5, ringY + 4);
            g2.drawLine(x + size - 5, ringY, x + size - 5, ringY + 4);
            
            // Header bar
            g2.setColor(ACCENT_COLOR);
            g2.fillRect(x + pad + 1, y + pad + 4, size - pad*2 - 1, 4);
            
            // Grid dots
            g2.setColor(new Color(120, 120, 120));
            for (int row = 0; row < 2; row++) {
                for (int col = 0; col < 3; col++) {
                    int dx = x + 5 + col * 4;
                    int dy = y + 12 + row * 3;
                    g2.fillRect(dx, dy, 2, 2);
                }
            }
        }
    }
    
    // ========================= MODERN CALENDAR PANEL =========================
    
    public static class ModernCalendarPanel extends JPanel {
        private LocalDate displayedMonth;
        private LocalDate selectedDate;
        private final java.util.function.Consumer<LocalDate> onDateSelected;
        
        private JComboBox<String> monthCombo;
        private JComboBox<Integer> yearCombo;
        private JPanel daysGrid;
        private JButton todayButton;
        private JLabel monthYearLabel;
        private JLabel fullDateLabel;
        
        public ModernCalendarPanel(java.util.function.Consumer<LocalDate> onDateSelected) {
            this.onDateSelected = onDateSelected;
            this.displayedMonth = LocalDate.now();
            
            setLayout(new BorderLayout(0, 8));
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(300, 320));
            setBorder(new EmptyBorder(12, 12, 12, 12));
            
            initHeader();
            initDaysGrid();
            initFooter();
            updateCalendar();
        }
        
        private void initHeader() {
            // Box container for full-date label and header controls
            JPanel northBox = new JPanel();
            northBox.setOpaque(false);
            northBox.setLayout(new BoxLayout(northBox, BoxLayout.Y_AXIS));

            // Top line: full date like "Friday, February 10, 2012"
            fullDateLabel = new JLabel(" ", SwingConstants.CENTER);
            fullDateLabel.setForeground(new Color(0, 102, 204));
            fullDateLabel.setFont(AeroTheme.defaultBoldFont(13f));
            fullDateLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            northBox.add(fullDateLabel);
            northBox.add(new JLabel(" ") {{ setPreferredSize(new Dimension(1,4)); setOpaque(false); }});

            // Header with arrows + centered Month Year label
            JPanel header = new JPanel(new BorderLayout(8, 0));
            header.setOpaque(false);

            JButton prevMonth = createNavButton("◀", -1);
            JButton nextMonth = createNavButton("▶", 1);

            monthYearLabel = new JLabel(" ", SwingConstants.CENTER);
            monthYearLabel.setFont(AeroTheme.defaultBoldFont(14f));
            monthYearLabel.setForeground(AeroTheme.TEXT_PRIMARY);
            monthYearLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            monthYearLabel.addMouseListener(new MouseAdapter(){
                @Override public void mouseClicked(MouseEvent e){ showMonthYearPopup(monthYearLabel); }
            });

            header.add(prevMonth, BorderLayout.WEST);
            header.add(monthYearLabel, BorderLayout.CENTER);
            header.add(nextMonth, BorderLayout.EAST);

            // Hidden combos used for popup selection (kept for convenience)
            monthCombo = new JComboBox<>();
            for (Month m : Month.values()) monthCombo.addItem(m.getDisplayName(TextStyle.FULL, Locale.getDefault()));
            yearCombo = new JComboBox<>();
            int currentYear = Year.now().getValue();
            for (int y = currentYear - 50; y <= currentYear + 20; y++) yearCombo.addItem(y);

            northBox.add(header);
            add(northBox, BorderLayout.NORTH);

            // Mouse wheel month navigation on the whole panel
            addMouseWheelListener(e -> { displayedMonth = displayedMonth.plusMonths(e.getWheelRotation() > 0 ? 1 : -1); updateCalendar(); });
        }
        
        private JButton createNavButton(String text, int monthDelta) {
            JButton btn = new JButton(text) {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    
                    if (getModel().isPressed()) g2.setColor(new Color(220, 230, 240));
                    else if (getModel().isRollover()) g2.setColor(HOVER_BG);
                    else g2.setColor(Color.WHITE);
                    
                    g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                    
                    g2.setColor(AeroTheme.TEXT_PRIMARY);
                    g2.setFont(getFont());
                    FontMetrics fm = g2.getFontMetrics();
                    int tx = (getWidth() - fm.stringWidth(getText())) / 2;
                    int ty = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                    g2.drawString(getText(), tx, ty);
                    g2.dispose();
                }
            };
            btn.setFont(AeroTheme.defaultFont().deriveFont(11f));
            btn.setPreferredSize(new Dimension(32, 28));
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setContentAreaFilled(false);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btn.addActionListener(e -> {
                displayedMonth = displayedMonth.plusMonths(monthDelta);
                updateCalendar();
            });
            return btn;
        }
        
        private void initDaysGrid() {
            daysGrid = new JPanel(new GridLayout(7, 7, 4, 4));
            daysGrid.setOpaque(false);
            add(daysGrid, BorderLayout.CENTER);
        }
        
        private void initFooter() {
            JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
            footer.setOpaque(false);
            
            todayButton = createFooterButton("Today", () -> {
                selectedDate = LocalDate.now();
                if (onDateSelected != null) onDateSelected.accept(selectedDate);
            });
            
            JButton clearButton = createFooterButton("Clear", () -> {
                selectedDate = null;
                if (onDateSelected != null) onDateSelected.accept(null);
            });
            
            footer.add(todayButton);
            footer.add(clearButton);
            add(footer, BorderLayout.SOUTH);
        }
        
        private void showMonthYearPopup(Component invoker) {
            JPopupMenu pop = new JPopupMenu();
            pop.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
            JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
            p.setOpaque(false);
            p.add(new JLabel("Month:"));
            p.add(monthCombo);
            p.add(new JLabel("Year:"));
            p.add(yearCombo);
            JButton apply = new JButton("Apply");
            apply.addActionListener(e->{
                int m = Math.max(0, monthCombo.getSelectedIndex()) + 1;
                Integer y = (Integer) yearCombo.getSelectedItem();
                if (y != null) displayedMonth = displayedMonth.withYear(y).withMonth(m);
                updateCalendar();
                pop.setVisible(false);
            });
            p.add(apply);
            pop.add(p);
            pop.show(invoker, 0, invoker.getHeight()+4);
        }
        
        private JButton createFooterButton(String text, Runnable action) {
            JButton btn = new JButton(text) {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    
                    if (getModel().isPressed()) g2.setColor(new Color(0, 100, 180));
                    else if (getModel().isRollover()) g2.setColor(ACCENT_COLOR);
                    else g2.setColor(new Color(240, 245, 250));
                    
                    g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                    
                    boolean isHover = getModel().isRollover() || getModel().isPressed();
                    g2.setColor(isHover ? Color.WHITE : ACCENT_COLOR);
                    g2.setFont(getFont());
                    FontMetrics fm = g2.getFontMetrics();
                    int tx = (getWidth() - fm.stringWidth(getText())) / 2;
                    int ty = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                    g2.drawString(getText(), tx, ty);
                    g2.dispose();
                }
            };
            btn.setFont(AeroTheme.defaultBoldFont(11f));
            btn.setPreferredSize(new Dimension(70, 30));
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setContentAreaFilled(false);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btn.addActionListener(e -> action.run());
            return btn;
        }
        
        private void updateCalendar() {
            // Sync hidden combos (guard if no listeners yet)
            try {
                if (monthCombo.getActionListeners().length > 0)
                    monthCombo.removeActionListener(monthCombo.getActionListeners()[0]);
                if (yearCombo.getActionListeners().length > 0)
                    yearCombo.removeActionListener(yearCombo.getActionListeners()[0]);
            } catch (Throwable ignored) {}
            monthCombo.setSelectedIndex(Math.max(0, displayedMonth.getMonthValue() - 1));
            yearCombo.setSelectedItem(displayedMonth.getYear());
            monthCombo.addActionListener(e -> { if (monthCombo.getSelectedIndex() >= 0) { displayedMonth = displayedMonth.withMonth(monthCombo.getSelectedIndex() + 1); updateCalendar(); }});
            yearCombo.addActionListener(e -> { if (yearCombo.getSelectedItem() != null) { displayedMonth = displayedMonth.withYear((Integer) yearCombo.getSelectedItem()); updateCalendar(); }});
            
            // Update header labels like Windows 7
            java.time.format.DateTimeFormatter mfmt = java.time.format.DateTimeFormatter.ofPattern("MMMM, yyyy");
            monthYearLabel.setText(displayedMonth.format(mfmt));
            LocalDate ref = (selectedDate != null) ? selectedDate : LocalDate.now();
            java.time.format.DateTimeFormatter dfmt = java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy");
            fullDateLabel.setText(ref.format(dfmt));
            
            daysGrid.removeAll();
            
            // Day headers
            String[] headers = {"Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"};
            for (String h : headers) {
                JLabel lbl = new JLabel(h, SwingConstants.CENTER);
                lbl.setFont(AeroTheme.defaultBoldFont(11f));
                lbl.setForeground(new Color(130, 130, 130));
                daysGrid.add(lbl);
            }
            
            // Calculate grid
            LocalDate firstOfMonth = displayedMonth.withDayOfMonth(1);
            int startDay = firstOfMonth.getDayOfWeek().getValue() % 7;
            int daysInMonth = displayedMonth.lengthOfMonth();
            LocalDate today = LocalDate.now();
            
            // Empty cells before first day
            for (int i = 0; i < startDay; i++) {
                daysGrid.add(new JLabel());
            }
            
            // Day cells
            for (int day = 1; day <= daysInMonth; day++) {
                LocalDate date = displayedMonth.withDayOfMonth(day);
                boolean isToday = date.equals(today);
                boolean isSelected = date.equals(selectedDate);
                boolean isWeekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || 
                                   date.getDayOfWeek() == DayOfWeek.SUNDAY;
                
                DayCell cell = new DayCell(day, isToday, isSelected, isWeekend);
                final LocalDate clickDate = date;
                cell.addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) {
                        selectedDate = clickDate;
                        if (onDateSelected != null) onDateSelected.accept(clickDate);
                    }
                });
                daysGrid.add(cell);
            }
            
            daysGrid.revalidate();
            daysGrid.repaint();
        }
        
        public void setDisplayedMonth(LocalDate date) {
            this.displayedMonth = date.withDayOfMonth(1);
            updateCalendar();
        }
        
        public void setSelectedDate(LocalDate date) {
            this.selectedDate = date;
            updateCalendar();
        }
        
        public LocalDate getSelectedDate() { return selectedDate; }
    }
    
    // ========================= DAY CELL =========================
    
    private static class DayCell extends JPanel {
        private final int day;
        private final boolean isToday;
        private final boolean isSelected;
        private final boolean isWeekend;
        private boolean hover = false;
        
        DayCell(int day, boolean isToday, boolean isSelected, boolean isWeekend) {
            this.day = day;
            this.isToday = isToday;
            this.isSelected = isSelected;
            this.isWeekend = isWeekend;
            
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(34, 34));
            
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }
        
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            
            int w = getWidth(), h = getHeight();
            int inset = 3;
            
            // Background
            if (isSelected) {
                // Win7 selection: light fill + strong border
                Color selFill = new Color(199, 224, 255);
                Color selBorder = new Color(51, 153, 255);
                g2.setColor(selFill);
                g2.fill(new RoundRectangle2D.Float(inset, inset, w - inset*2, h - inset*2, 6, 6));
                g2.setStroke(new BasicStroke(1.6f));
                g2.setColor(selBorder);
                g2.draw(new RoundRectangle2D.Float(inset + 0.5f, inset + 0.5f, w - inset*2 - 1f, h - inset*2 - 1f, 6, 6));
            } else if (hover) {
                Color hov = new Color(222, 235, 255);
                g2.setColor(hov);
                g2.fill(new RoundRectangle2D.Float(inset, inset, w - inset*2, h - inset*2, 6, 6));
            }
            
            // Today indicator (ring)
            if (isToday && !isSelected) {
                g2.setColor(TODAY_RING);
                g2.setStroke(new BasicStroke(2f));
                g2.draw(new RoundRectangle2D.Float(inset + 1, inset + 1, w - inset*2 - 2, h - inset*2 - 2, 8, 8));
            }
            
            // Text
            g2.setFont(AeroTheme.defaultFont().deriveFont(isToday ? Font.BOLD : Font.PLAIN, 12f));
            FontMetrics fm = g2.getFontMetrics();
            String text = String.valueOf(day);
            int tx = (w - fm.stringWidth(text)) / 2;
            int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
            
            if (isSelected) {
                g2.setColor(Color.WHITE);
            } else if (isWeekend) {
                g2.setColor(new Color(180, 100, 100));
            } else {
                g2.setColor(AeroTheme.TEXT_PRIMARY);
            }
            g2.drawString(text, tx, ty);
            
            g2.dispose();
        }
    }
    
    // ========================= SHADOW BORDER =========================
    
    private static class ShadowBorder extends javax.swing.border.AbstractBorder {
        private final int shadowSize;
        private final Color shadowColor;
        
        ShadowBorder(int size, Color color) {
            this.shadowSize = size;
            this.shadowColor = color;
        }
        
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            // Multi-layer shadow for softness
            for (int i = 0; i < shadowSize; i++) {
                float alpha = (float)(shadowSize - i) / (shadowSize * 3);
                g2.setColor(new Color(shadowColor.getRed(), shadowColor.getGreen(), shadowColor.getBlue(), 
                    (int)(shadowColor.getAlpha() * alpha)));
                g2.draw(new RoundRectangle2D.Float(x + i, y + i, width - i*2 - 1, height - i*2 - 1, 16 - i, 16 - i));
            }
            g2.dispose();
        }
        
        @Override public Insets getBorderInsets(Component c) {
            return new Insets(shadowSize, shadowSize, shadowSize, shadowSize);
        }
    }
}