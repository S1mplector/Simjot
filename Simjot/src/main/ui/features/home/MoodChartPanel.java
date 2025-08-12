package main.ui.features.home;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import javax.swing.*;
import main.infrastructure.io.AppDirectories;
import main.ui.app.JournalApp;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.combobox.ModernComboBoxUI;

public class MoodChartPanel extends JPanel {
    private java.util.List<LocalDate> dayList = new ArrayList<>();
    private java.util.List<Double> avgMoodList = new ArrayList<>();
    private CardLayout cardLayout;
    private JPanel cardPanel;
    private JournalApp app;
    private JComboBox<String> rangeBox;
    private final String[] ranges = {"7 days","30 days","All"};
    
    public MoodChartPanel(JournalApp app, CardLayout cardLayout, JPanel cardPanel) {
        this.app = app;
        this.cardLayout = cardLayout;
        this.cardPanel = cardPanel;
        setBackground(Color.WHITE);
        setLayout(new BorderLayout());

        RoundedButton backButton = new RoundedButton("Back to Main Menu");
        backButton.addActionListener(e -> app.switchCard(JournalApp.MAIN_MENU));

        JPanel bottomPanel = new JPanel();
        bottomPanel.add(backButton);
        add(bottomPanel, BorderLayout.SOUTH);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.setBackground(new Color(230,230,230));
        top.add(new JLabel("Range:"));
        rangeBox = new JComboBox<>(ranges);
        rangeBox.setUI(new ModernComboBoxUI());
        rangeBox.setRenderer(new ModernComboBoxUI.ModernComboBoxRenderer());
        rangeBox.setSelectedIndex(1);
        rangeBox.addActionListener(e->{ loadMoodData(); repaint(); });
        top.add(rangeBox);
        add(top, BorderLayout.NORTH);

        // Load initial data
        loadMoodData();

        // Refresh data every time the panel becomes visible
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                loadMoodData();
                repaint();
            }
        });
    }
    
    private void loadMoodData() {
        dayList.clear(); avgMoodList.clear();
        int daysLimit = switch(rangeBox.getSelectedIndex()){
            case 0 -> 7; case 1 -> 30; default -> Integer.MAX_VALUE; };
        LocalDate today = LocalDate.now();
        java.util.Map<LocalDate, java.util.List<Double>> map = new java.util.HashMap<>();
        File moodFile = new File(AppDirectories.folder(AppDirectories.Type.MOOD_DATA), "mood_log.txt");
        if (moodFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(moodFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length >= 2) {
                        LocalDate date;
                        try {
                            date = LocalDate.parse(parts[0]); // ISO format yyyy-MM-dd
                        } catch(java.time.format.DateTimeParseException ex){
                            try {
                                DateTimeFormatter alt = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
                                date = LocalDateTime.parse(parts[0], alt).toLocalDate();
                            } catch(Exception ex2){
                                continue; // skip malformed line
                            }
                        }
                        if(ChronoUnit.DAYS.between(date, today) > daysLimit) continue;
                        String moodStr = parts[1].trim();
                        double val;
                        try {
                            int num = Integer.parseInt(moodStr);
                            // Map 0-100 → 0-2 for consistency with old range
                            val = (num / 100.0) * 2.0;
                        } catch (NumberFormatException nfe) {
                            // Fallback for legacy emoji records
                            val = moodStr.equals(":)") ? 2.0 : moodStr.equals(":/") ? 1.0 : 0.0;
                        }
                        map.computeIfAbsent(date,d->new ArrayList<>()).add(val);
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        java.util.List<LocalDate> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);
        for(LocalDate d: keys){
            java.util.List<Double> l = map.get(d);
            double avg = l.stream().mapToDouble(x->x).average().orElse(0);
            dayList.add(d); avgMoodList.add(avg);
        }
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if(dayList.isEmpty()){
            g2.setColor(Color.GRAY);
            String msg="No mood data yet.";
            FontMetrics fm=g2.getFontMetrics();
            g2.drawString(msg, (getWidth()-fm.stringWidth(msg))/2, getHeight()/2);
            g2.dispose();
            return;
        }

        int margin=60;
        int w = getWidth()-2*margin;
        int h = getHeight()-2*margin;
        // axes
        g2.setColor(Color.DARK_GRAY);
        g2.drawLine(margin, getHeight()-margin, margin, margin);
        g2.drawLine(margin, getHeight()-margin, getWidth()-margin, getHeight()-margin);

        // plot line
        if(dayList.size()>1){
            for(int i=1;i<dayList.size();i++){
                int x1 = margin + (int)((i-1)*w/(dayList.size()-1));
                int x2 = margin + (int)(i*w/(dayList.size()-1));
                int y1 = getHeight()-margin - (int)(avgMoodList.get(i-1)/2*h);
                int y2 = getHeight()-margin - (int)(avgMoodList.get(i)/2*h);
                g2.setColor(new Color(0,120,215));
                g2.setStroke(new BasicStroke(2f));
                g2.drawLine(x1,y1,x2,y2);
                g2.fillOval(x1-3,y1-3,6,6);
                if(i==dayList.size()-1) g2.fillOval(x2-3,y2-3,6,6);
            }
        } else if (dayList.size() == 1) {
            int x = margin + w / 2; // Center the single point
            int y = getHeight() - margin - (int)(avgMoodList.get(0) / 2 * h);
            g2.setColor(new Color(0, 120, 215));
            g2.fillOval(x - 3, y - 3, 6, 6);
        }

        // date labels
        g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        if (dayList.size() > 1) {
            for(int i=0;i<dayList.size();i++){
                int x = margin + (int)(i*w/(dayList.size()-1));
                String txt = fmt.format(dayList.get(i));
                int tw=g2.getFontMetrics().stringWidth(txt);
                g2.drawString(txt, x-tw/2, getHeight()-margin+15);
            }
        } else if (dayList.size() == 1) {
            int x = margin + w / 2; // Center the label
            String txt = fmt.format(dayList.get(0));
            int tw = g2.getFontMetrics().stringWidth(txt);
            g2.drawString(txt, x - tw / 2, getHeight() - margin + 15);
        }

        g2.dispose();
    }

    // tooltip support
    public MoodChartPanel(){ super(); }

    { // initializer for mouse tooltip
        setToolTipText("");
        addMouseMotionListener(new MouseMotionAdapter(){
            @Override public void mouseMoved(MouseEvent e){
                if(dayList.isEmpty()) return;
                int margin=60;
                int w = getWidth()-2*margin;
                int idx = Math.round((float)(e.getX()-margin)/w*(dayList.size()-1));
                if(idx>=0 && idx<dayList.size()){
                    double raw = avgMoodList.get(idx); // 0..2 scale
                    int pct = (int)Math.round(raw / 2.0 * 100);
                    setToolTipText(dayList.get(idx)+" avg mood: "+pct+"/100");
                }
            }
        });
    }
}
