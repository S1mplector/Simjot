package main.ui.panels;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import javax.imageio.ImageIO;
import javax.swing.*;
import main.transitions.FadingButton;
import main.ui.JournalApp;

@SuppressWarnings("serial")
public class TodoPanel extends JPanel {
    private CardLayout cardLayout;
    private JPanel cardPanel;
    private JournalApp app;
    
    private int currentDayIndex = 0;
    private final int totalDays = 7;
    private DayPanel[] dayPanels;
    
    // Center panel that will display the current DayPanel
    private JPanel centerPanel;
    // We'll use a custom animation when switching days.
    
    // Background image (wood texture)
    private BufferedImage backgroundImage;
    
    public TodoPanel(JournalApp app, File journalFolder, CardLayout cardLayout, JPanel cardPanel) {
        this.app = app;
        this.cardLayout = cardLayout;
        this.cardPanel = cardPanel;
        setLayout(new BorderLayout());
        setBackground(Color.BLACK);
        loadBackground();
        initUI();
    }
    
    // Load the wood background image
    private void loadBackground() {
        try {
            backgroundImage = ImageIO.read(new File("Simjournal/img/wood.png"));
        } catch (IOException ex) {
            ex.printStackTrace();
            backgroundImage = null;
        }
    }
    
    // Override paintComponent to draw the background image scaled to fill the panel.
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if(backgroundImage != null){
            g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
        }
    }
    
    private void initUI() {
        // Top panel with Back button (returns to main menu)
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setOpaque(false);
        FadingButton backButton = new FadingButton("Back");
        backButton.setBackground(Color.DARK_GRAY);
        backButton.setForeground(Color.WHITE);
        backButton.addActionListener(e -> app.switchCard(JournalApp.MAIN_MENU));
        topPanel.add(backButton);
        add(topPanel, BorderLayout.NORTH);
        
        // Create DayPanels for 7 days (today and next 6 days)
        dayPanels = new DayPanel[totalDays];
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM d");
        for (int i = 0; i < totalDays; i++) {
            String tabTitle = sdf.format(cal.getTime());
            dayPanels[i] = new DayPanel(tabTitle);
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        
        // Create the center panel (we'll use null layout for animation)
        centerPanel = new JPanel(null);
        centerPanel.setOpaque(false);
        add(centerPanel, BorderLayout.CENTER);
        
        // Initially show day 0 without animation.
        DayPanel initialPanel = dayPanels[0];
        initialPanel.setBounds(0, 0, 600, 300); // fixed size for animation (or use centerPanel.getSize())
        centerPanel.add(initialPanel);
        currentDayIndex = 0;
        
        // Navigation buttons: left and right arrows
        JPanel navPanel = new JPanel(new BorderLayout());
        navPanel.setOpaque(false);
        FadingButton leftButton = new FadingButton("<");
        leftButton.setPreferredSize(new Dimension(60, 40));
        leftButton.setBackground(new Color(64, 64, 64, 140));
        leftButton.setForeground(Color.WHITE);
        leftButton.addActionListener(e -> {
            if (currentDayIndex > 0) {
                animateDaySwitch(currentDayIndex - 1);
            }
        });
        navPanel.add(leftButton, BorderLayout.WEST);
        
        FadingButton rightButton = new FadingButton(">");
        rightButton.setPreferredSize(new Dimension(60, 40));
        rightButton.setBackground(new Color(64, 64, 64, 140));
        rightButton.setForeground(Color.WHITE);
        rightButton.addActionListener(e -> {
            if (currentDayIndex < totalDays - 1) {
                animateDaySwitch(currentDayIndex + 1);
            }
        });
        navPanel.add(rightButton, BorderLayout.EAST);
        
        add(navPanel, BorderLayout.SOUTH);
    }
    
    // Animate sliding to a new day panel.
    private void animateDaySwitch(int newIndex) {
        if (newIndex < 0 || newIndex >= totalDays) return;

        if (main.util.SettingsStore.get().isAnimationsDisabled()) {
            centerPanel.removeAll();
            centerPanel.setLayout(new BorderLayout());
            centerPanel.add(dayPanels[newIndex], BorderLayout.CENTER);
            centerPanel.revalidate();
            centerPanel.repaint();
            currentDayIndex = newIndex;
            return;
        }
        
        // Get current and new panels
        Component oldPanel = dayPanels[currentDayIndex];
        DayPanel newPanel = dayPanels[newIndex];
        int w = centerPanel.getWidth();
        int h = centerPanel.getHeight();
        
        // Remove all from centerPanel and add both panels with absolute positions.
        centerPanel.removeAll();
        
        // Determine direction: if newIndex > current, slide from right; else from left.
        int direction = (newIndex > currentDayIndex) ? 1 : -1;
        // Position the new panel off-screen.
        newPanel.setBounds(direction * w, 0, w, h);
        centerPanel.add(newPanel);
        // Also add the old panel at (0,0)
        oldPanel.setBounds(0, 0, w, h);
        centerPanel.add(oldPanel);
        centerPanel.revalidate();
        centerPanel.repaint();
        
        // Animate the slide transition.
        int animationDuration = 300; // milliseconds
        int animationDelay = 20;      // milliseconds per step
        int steps = animationDuration / animationDelay;
        int delta = w / steps;
        
        Timer timer = new Timer(animationDelay, null);
        timer.addActionListener(new ActionListener(){
            int currentStep = 0;
            public void actionPerformed(ActionEvent e) {
                currentStep++;
                int shift = delta * currentStep * direction;
                oldPanel.setLocation(shift, 0);
                newPanel.setLocation(shift + (direction * w), 0);
                centerPanel.repaint();
                if (currentStep >= steps) {
                    timer.stop();
                    // Finally, show newPanel only in a BorderLayout.
                    centerPanel.removeAll();
                    centerPanel.setLayout(new BorderLayout());
                    centerPanel.add(newPanel, BorderLayout.CENTER);
                    centerPanel.revalidate();
                    centerPanel.repaint();
                    currentDayIndex = newIndex;
                }
            }
        });
        timer.start();
    }
    
    // Inner class representing a single day's todo list.
    private class DayPanel extends JPanel {
        private String dayLabel;
        private RoundedPanel tasksPanel;
        private JTextField newTaskField;
        
        public DayPanel(String dayLabel) {
            this.dayLabel = dayLabel;
            setOpaque(false);
            setLayout(new BorderLayout(10, 10));
            initDayPanel();
        }
        
        private void initDayPanel() {
            // Header label for the day.
            JLabel label = new JLabel(dayLabel, SwingConstants.CENTER);
            label.setForeground(Color.WHITE);
            label.setFont(new Font("SansSerif", Font.BOLD, 16));
            add(label, BorderLayout.NORTH);
            
            // The tasksPanel with rounded corners.
            tasksPanel = new RoundedPanel();
            tasksPanel.setBackground(new Color(0, 0, 0, 100)); // semi-transparent
            tasksPanel.setLayout(new BoxLayout(tasksPanel, BoxLayout.Y_AXIS));
            JScrollPane scrollPane = new JScrollPane(tasksPanel);
            scrollPane.setOpaque(false);
            scrollPane.getViewport().setOpaque(false);
            add(scrollPane, BorderLayout.CENTER);
            
            // Bottom panel: text field and Add button.
            JPanel addPanel = new JPanel(new BorderLayout());
            addPanel.setOpaque(false);
            newTaskField = new JTextField();
            newTaskField.setBackground(Color.DARK_GRAY);
            newTaskField.setForeground(Color.WHITE);
            newTaskField.addActionListener(e -> addTask());
            FadingButton addButton = new FadingButton("Add");
            addButton.setBackground(Color.DARK_GRAY);
            addButton.setForeground(Color.WHITE);
            addButton.addActionListener(e -> addTask());
            addPanel.add(newTaskField, BorderLayout.CENTER);
            addPanel.add(addButton, BorderLayout.EAST);
            add(addPanel, BorderLayout.SOUTH);
        }
        
        // Add a new task to tasksPanel.
        private void addTask() {
            String taskText = newTaskField.getText().trim();
            if (taskText.isEmpty())
                return;
            TaskItem task = new TaskItem(taskText);
            tasksPanel.add(task);
            tasksPanel.revalidate();
            tasksPanel.repaint();
            newTaskField.setText("");
        }
    }
    
    // A custom panel with a rounded rectangle background.
    private class RoundPanel extends JPanel {
        public RoundPanel() {
            setOpaque(false);
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int arc = 20;
            Shape round = new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc);
            g2.setColor(getBackground());
            g2.fill(round);
            g2.dispose();
            super.paintComponent(g);
        }
    }
    
    // A task item panel with a checkbox, task text, and a delete button.
    private class TaskItem extends JPanel {
        public TaskItem(String taskText) {
            setLayout(new BorderLayout(5, 5));
            setOpaque(false);
            
            JCheckBox checkBox = new JCheckBox();
            checkBox.setOpaque(false);
            checkBox.setForeground(Color.WHITE);
            add(checkBox, BorderLayout.WEST);
            
            JLabel taskLabel = new JLabel(taskText);
            taskLabel.setForeground(Color.WHITE);
            add(taskLabel, BorderLayout.CENTER);
            
            FadingButton deleteButton = new FadingButton("X");
            deleteButton.setBackground(Color.DARK_GRAY);
            deleteButton.setForeground(Color.WHITE);
            deleteButton.setMargin(new Insets(2, 2, 2, 2));
            deleteButton.addActionListener(e -> {
                Container parent = getParent();
                parent.remove(TaskItem.this);
                parent.revalidate();
                parent.repaint();
            });
            add(deleteButton, BorderLayout.EAST);
        }
    }
    
    /*
    public static void main(String[] args) {
        // For standalone testing of TodoPanel:
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("TodoPanel Test");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            CardLayout cl = new CardLayout();
            JPanel cp = new JPanel(cl);
            cp.add(new TodoPanel(new File("."), cl, cp), "todo");
            frame.getContentPane().add(cp);
            frame.setSize(800, 600);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            cl.show(cp, "todo");
        });
    } 
    */
}
