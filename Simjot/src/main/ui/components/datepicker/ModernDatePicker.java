package main.ui.components.datepicker;

import java.awt.*;
import java.awt.event.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import main.ui.dialog.message.UIMessage;

/**
 * Modern Date Picker component following Simjot's design patterns.
 * Features: rounded corners, quick preset buttons, calendar popup, modern styling
 */
public class ModernDatePicker extends JPanel {
    
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    private static final DateTimeFormatter INPUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    private LocalDate selectedDate;
    private JTextField dateField;
    private JButton calendarButton;
    private JPopupMenu calendarPopup;
    private CalendarPanel calendarPanel;
    
    // Quick preset buttons
    private JButton todayButton;
    private JButton tomorrowButton;
    private JButton nextWeekButton;
    private JButton clearButton;
    
    // Event handling
    private ActionListener dateChangeListener;
    
    public ModernDatePicker() {
        this(null);
    }
    
    public ModernDatePicker(LocalDate initialDate) {
        this.selectedDate = initialDate;
        setLayout(new BorderLayout(3, 3));
        setOpaque(false);
        setPreferredSize(new Dimension(200, 60)); // Better proportions
        setMinimumSize(new Dimension(180, 60));
        setMaximumSize(new Dimension(250, 60));
        
        initComponents();
        updateDisplay();
    }
    
    private void initComponents() {
        // Main date input panel
        JPanel inputPanel = new JPanel(new BorderLayout(3, 0));
        inputPanel.setOpaque(false);
        
        // Date text field - more compact
        dateField = new ModernTextField(8); // Reduced from 12
        dateField.setEditable(true);
        dateField.setPreferredSize(new Dimension(120, 28)); // Fixed width
        dateField.addActionListener(e -> parseDateFromField());
        dateField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                parseDateFromField();
            }
        });
        inputPanel.add(dateField, BorderLayout.CENTER);
        
        // Calendar button
        calendarButton = new ModernCalendarButton();
        calendarButton.addActionListener(e -> showCalendarPopup());
        inputPanel.add(calendarButton, BorderLayout.EAST);
        
        add(inputPanel, BorderLayout.CENTER);
        
        // Quick preset buttons panel with improved spacing
        JPanel presetsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        presetsPanel.setOpaque(false);
        presetsPanel.setPreferredSize(new Dimension(250, 10)); // Slightly larger to accommodate better spacing
        
        todayButton = new PresetButton("Today");
        todayButton.addActionListener(e -> setSelectedDate(LocalDate.now()));
        presetsPanel.add(todayButton);
        
        tomorrowButton = new PresetButton("Tomorrow");
        tomorrowButton.addActionListener(e -> setSelectedDate(LocalDate.now().plusDays(1)));
        presetsPanel.add(tomorrowButton);
        
        nextWeekButton = new PresetButton("Next Week");
        nextWeekButton.addActionListener(e -> setSelectedDate(LocalDate.now().plusWeeks(1)));
        presetsPanel.add(nextWeekButton);
        
        clearButton = new PresetButton("Clear");
        clearButton.addActionListener(e -> setSelectedDate(null));
        presetsPanel.add(clearButton);
        
        add(presetsPanel, BorderLayout.SOUTH);
        
        // Initialize calendar popup
        initCalendarPopup();
    }
    
    private void initCalendarPopup() {
        calendarPopup = new JPopupMenu();
        calendarPopup.setBorder(BorderFactory.createEmptyBorder());
        
        calendarPanel = new CalendarPanel();
        calendarPanel.setDateSelectionListener(e -> {
            if (e.getSource() instanceof CalendarPanel.DayButton) {
                CalendarPanel.DayButton dayButton = (CalendarPanel.DayButton) e.getSource();
                setSelectedDate(dayButton.getDate());
            }
            calendarPopup.setVisible(false);
        });
        
        calendarPopup.add(calendarPanel);
    }
    
    private void showCalendarPopup() {
        if (selectedDate != null) {
            calendarPanel.setDisplayedDate(selectedDate);
        } else {
            calendarPanel.setDisplayedDate(LocalDate.now());
        }
        
        calendarPopup.show(calendarButton, 0, calendarButton.getHeight());
    }
    
    private void parseDateFromField() {
        String text = dateField.getText().trim();
        if (text.isEmpty()) {
            setSelectedDate(null);
            return;
        }
        
        try {
            // Try parsing as ISO format first (yyyy-MM-dd)
            LocalDate date = LocalDate.parse(text, INPUT_FORMAT);
            setSelectedDate(date);
        } catch (DateTimeParseException e1) {
            try {
                // Try parsing as display format (MMM dd, yyyy)
                LocalDate date = LocalDate.parse(text, DATE_FORMAT);
                setSelectedDate(date);
            } catch (DateTimeParseException e2) {
                // Invalid format - revert to previous value
                updateDisplay();
                UIMessage.warn(this,
                        "Invalid Date",
                        "The date you entered isn't recognized.",
                        "Use formats like 'Jan 31, 2025' or '2025-01-31', then press Enter.");
            }
        }
    }
    
    private void updateDisplay() {
        if (selectedDate != null) {
            dateField.setText(selectedDate.format(DATE_FORMAT));
            dateField.setForeground(Color.DARK_GRAY);
        } else {
            dateField.setText("No due date");
            dateField.setForeground(Color.GRAY);
        }
    }
    
    // --- Public API ---
    
    public LocalDate getSelectedDate() {
        return selectedDate;
    }
    
    public void setSelectedDate(LocalDate date) {
        LocalDate oldDate = this.selectedDate;
        this.selectedDate = date;
        updateDisplay();
        
        if (dateChangeListener != null && 
            (oldDate == null && date != null) || 
            (oldDate != null && !oldDate.equals(date)) ||
            (oldDate != null && date == null)) {
            dateChangeListener.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "dateChanged"));
        }
    }
    
    public void setDateChangeListener(ActionListener listener) {
        this.dateChangeListener = listener;
    }
    
    public boolean hasDate() {
        return selectedDate != null;
    }
    
    public void setEditable(boolean editable) {
        dateField.setEditable(editable);
        calendarButton.setEnabled(editable);
        todayButton.setEnabled(editable);
        tomorrowButton.setEnabled(editable);
        nextWeekButton.setEnabled(editable);
        clearButton.setEnabled(editable);
    }
    
    // --- Custom Components ---
    
    private static class ModernTextField extends JTextField {
        public ModernTextField(int cols) {
            super(cols);
            setOpaque(false);
            setBorder(new EmptyBorder(4, 8, 4, 8)); // Reduced padding
            setFont(new Font("SansSerif", Font.PLAIN, 12)); // Smaller font
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            super.paintComponent(g2);
            g2.dispose();
        }
        
        @Override
        protected void paintBorder(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(hasFocus() ? new Color(0, 120, 215) : Color.LIGHT_GRAY);
            g2.setStroke(new BasicStroke(hasFocus() ? 2f : 1f));
            g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 8, 8);
            g2.dispose();
        }
    }
    
    private static class ModernCalendarButton extends JButton {
        public ModernCalendarButton() {
            super("📅");
            setFont(new Font("SansSerif", Font.BOLD, 12)); // Match RoundedButton font
            setPreferredSize(new Dimension(35, 35));
            setBorder(new EmptyBorder(5, 5, 5, 5));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setForeground(Color.DARK_GRAY); // Match RoundedButton foreground
            setToolTipText("Open calendar");
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            // Use exact RoundedButton styling
            if(getModel().isPressed())       g2.setColor(new Color(200,200,200));
            else if(getModel().isRollover()) g2.setColor(new Color(220,220,220));
            else                             g2.setColor(new Color(240,240,240));
            
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10); // Match RoundedButton radius
            g2.setColor(Color.LIGHT_GRAY); // Match RoundedButton border
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            
            g2.dispose();
            super.paintComponent(g);
        }
    }
    
    private static class PresetButton extends JButton {
        public PresetButton(String text) {
            super(text);
            setFont(new Font("SansSerif", Font.BOLD, 12)); // Match RoundedButton font
            
            // Compact sizing based on text length but with better proportions
            int width = Math.max(60, text.length() * 8 + 16);
            setPreferredSize(new Dimension(width, 32)); // Match RoundedButton height
            setMinimumSize(new Dimension(width, 32));
            setMaximumSize(new Dimension(width, 32));
            
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setForeground(Color.DARK_GRAY); // Match RoundedButton foreground
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            // Use exact RoundedButton styling
            if(getModel().isPressed())       g2.setColor(new Color(200,200,200));
            else if(getModel().isRollover()) g2.setColor(new Color(220,220,220));
            else                             g2.setColor(new Color(240,240,240));
            
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10); // Match RoundedButton radius
            g2.setColor(Color.LIGHT_GRAY); // Match RoundedButton border
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);

            // Draw text manually to match RoundedButton style
            g2.setColor(getForeground());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(getText(), (getWidth()-fm.stringWidth(getText()))/2, ((getHeight()-fm.getHeight())/2)+fm.getAscent());
            
            g2.dispose();
        }
    }
    
    // --- Calendar Panel ---
    
    private static class CalendarPanel extends JPanel {
        private LocalDate displayedDate;
        private LocalDate selectedDate;
        private ActionListener dateSelectionListener;
        
        private JLabel monthLabel;
        private JButton prevButton, nextButton;
        private JPanel daysPanel;
        
        public CalendarPanel() {
            this.displayedDate = LocalDate.now();
            setLayout(new BorderLayout());
            setPreferredSize(new Dimension(280, 220));
            setBorder(new EmptyBorder(10, 10, 10, 10));
            setBackground(Color.WHITE);
            
            initCalendarComponents();
            updateCalendar();
        }
        
        private void initCalendarComponents() {
            // Header with month navigation
            JPanel headerPanel = new JPanel(new BorderLayout());
            headerPanel.setOpaque(false);
            
            prevButton = new ModernNavigationButton("<");
            prevButton.addActionListener(e -> {
                displayedDate = displayedDate.minusMonths(1);
                updateCalendar();
            });
            
            nextButton = new ModernNavigationButton(">");
            nextButton.addActionListener(e -> {
                displayedDate = displayedDate.plusMonths(1);
                updateCalendar();
            });
            
            monthLabel = new JLabel();
            monthLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
            monthLabel.setHorizontalAlignment(JLabel.CENTER);
            
            headerPanel.add(prevButton, BorderLayout.WEST);
            headerPanel.add(monthLabel, BorderLayout.CENTER);
            headerPanel.add(nextButton, BorderLayout.EAST);
            
            add(headerPanel, BorderLayout.NORTH);
            
            // Days grid
            daysPanel = new JPanel(new GridLayout(7, 7, 2, 2));
            daysPanel.setOpaque(false);
            add(daysPanel, BorderLayout.CENTER);
        }
        
        private void updateCalendar() {
            monthLabel.setText(displayedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy")));
            
            daysPanel.removeAll();
            
            // Add day headers
            String[] dayHeaders = {"Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"};
            for (String header : dayHeaders) {
                JLabel headerLabel = new JLabel(header);
                headerLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
                headerLabel.setHorizontalAlignment(JLabel.CENTER);
                headerLabel.setForeground(Color.GRAY);
                daysPanel.add(headerLabel);
            }
            
            // Calculate first day of month and number of days
            LocalDate firstDay = displayedDate.withDayOfMonth(1);
            int startDayOfWeek = firstDay.getDayOfWeek().getValue() % 7; // Convert to Sunday = 0
            int daysInMonth = displayedDate.lengthOfMonth();
            
            // Add empty cells for days before first day of month
            for (int i = 0; i < startDayOfWeek; i++) {
                daysPanel.add(new JLabel());
            }
            
            // Add day buttons
            for (int day = 1; day <= daysInMonth; day++) {
                LocalDate date = displayedDate.withDayOfMonth(day);
                DayButton dayButton = new DayButton(day, date);
                
                if (selectedDate != null && date.equals(selectedDate)) {
                    dayButton.setSelected(true);
                }
                
                daysPanel.add(dayButton);
            }
            
            revalidate();
            repaint();
        }
        
        public void setDisplayedDate(LocalDate date) {
            this.displayedDate = date;
            updateCalendar();
        }
        
        public void setSelectedDate(LocalDate date) {
            this.selectedDate = date;
            updateCalendar();
        }
        
        public void setDateSelectionListener(ActionListener listener) {
            this.dateSelectionListener = listener;
        }
        
        private class DayButton extends JButton {
            private final LocalDate date;
            private boolean selected = false;
            
            public DayButton(int day, LocalDate date) {
                super(String.valueOf(day));
                this.date = date;
                
                setFont(new Font("SansSerif", Font.PLAIN, 11));
                setBorder(new EmptyBorder(5, 5, 5, 5));
                setFocusPainted(false);
                setOpaque(false);
                
                addActionListener(e -> {
                    if (dateSelectionListener != null) {
                        selectedDate = date;
                        dateSelectionListener.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "dateSelected"));
                    }
                });
                
                // Highlight today
                if (date.equals(LocalDate.now())) {
                    setForeground(new Color(0, 120, 215));
                    setFont(getFont().deriveFont(Font.BOLD));
                }
            }
            
            public LocalDate getDate() {
                return date;
            }
            
            public void setSelected(boolean selected) {
                this.selected = selected;
                repaint();
            }
            
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                if (selected) {
                    g2.setColor(new Color(0, 120, 215));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                    setForeground(Color.WHITE);
                } else if (getModel().isRollover()) {
                    g2.setColor(new Color(230, 230, 230));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                }
                
                g2.dispose();
                super.paintComponent(g);
            }
        }
        
        // --- Modern Navigation Button for Calendar ---
        private static class ModernNavigationButton extends JButton {
            public ModernNavigationButton(String text) {
                super(text);
                setFont(new Font("SansSerif", Font.BOLD, 12)); // Match RoundedButton font
                setPreferredSize(new Dimension(35, 32)); // Match RoundedButton height
                setFocusPainted(false);
                setBorderPainted(false);
                setContentAreaFilled(false);
                setForeground(Color.DARK_GRAY); // Match RoundedButton foreground
            }
            
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Use exact RoundedButton styling
                if(getModel().isPressed())       g2.setColor(new Color(200,200,200));
                else if(getModel().isRollover()) g2.setColor(new Color(220,220,220));
                else                             g2.setColor(new Color(240,240,240));
                
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10); // Match RoundedButton radius
                g2.setColor(Color.LIGHT_GRAY); // Match RoundedButton border
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
    
                // Draw text manually to match RoundedButton style
                g2.setColor(getForeground());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(), (getWidth()-fm.stringWidth(getText()))/2, ((getHeight()-fm.getHeight())/2)+fm.getAscent());
                
                g2.dispose();
            }
        }
    }
}