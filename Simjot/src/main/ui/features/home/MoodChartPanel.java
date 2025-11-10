package main.ui.features.home;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import javax.swing.*;
import main.core.service.NotebookStore;
import main.infrastructure.backup.NotebookInfo;
import main.infrastructure.io.AppDirectories;
import main.ui.app.JournalApp;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.combobox.ModernComboBoxUI;

public class MoodChartPanel extends JPanel {
    private java.util.List<LocalDate> dayList = new ArrayList<>();
    private java.util.List<Double> avgMoodList = new ArrayList<>();
    private java.util.Map<LocalDate, java.util.List<File>> entriesByDate = new java.util.HashMap<>();
    private final java.util.Map<LocalDate, java.util.List<LocalDateTime>> moodTimesByDate = new java.util.HashMap<>();
    private final java.util.Map<LocalDate, java.util.List<EntryRef>> entryTimesByDate = new java.util.HashMap<>();
    private CardLayout cardLayout;
    private JPanel cardPanel;
    private JournalApp app;
    private JComboBox<String> rangeBox;
    private final String[] ranges = {"7 days","30 days","90 days","365 days","All"};
    private javax.swing.Timer hoverOpenTimer;
    private Integer pendingHoverIdx = null;

    private static class EntryRef {
        final File file; final LocalDateTime ts;
        EntryRef(File f, LocalDateTime ts){ this.file=f; this.ts=ts; }
    }
    
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
        entriesByDate.clear();
        moodTimesByDate.clear();
        entryTimesByDate.clear();
        int daysLimit = switch(rangeBox.getSelectedIndex()){
            case 0 -> 7; case 1 -> 30; case 2 -> 90; case 3 -> 365; default -> Integer.MAX_VALUE; };
        LocalDate today = LocalDate.now();
        java.util.Map<LocalDate, java.util.List<Integer>> map = new java.util.HashMap<>();
        File moodFile = new File(AppDirectories.folder(AppDirectories.Type.MOOD_DATA), "mood_log.txt");
        if (moodFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(moodFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length >= 2) {
                        LocalDate date; LocalDateTime dateTime = null;
                        try {
                            date = LocalDate.parse(parts[0]);
                        } catch (Exception ex) {
                            try {
                                DateTimeFormatter alt = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
                                dateTime = LocalDateTime.parse(parts[0], alt);
                                date = dateTime.toLocalDate();
                            } catch (Exception ex2) {
                                continue;
                            }
                        }
                        String moodStr = parts[1].trim();
                        int pct;
                        try {
                            pct = Math.max(0, Math.min(100, Integer.parseInt(moodStr)));
                        } catch (NumberFormatException nfe) {
                            pct = ":)".equals(moodStr) ? 100 : ":/".equals(moodStr) ? 50 : 0;
                        }
                        map.computeIfAbsent(date, d -> new ArrayList<>()).add(pct);
                        if (dateTime != null) {
                            moodTimesByDate.computeIfAbsent(date, d -> new ArrayList<>()).add(dateTime);
                        }
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        if (map.isEmpty()) return;
        java.util.List<LocalDate> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);
        LocalDate start;
        if (daysLimit == Integer.MAX_VALUE) {
            start = keys.get(0);
        } else {
            start = today.minusDays(Math.max(0, daysLimit - 1));
        }
        LocalDate end = today;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            dayList.add(d);
            java.util.List<Integer> l = map.get(d);
            if (l == null || l.isEmpty()) {
                avgMoodList.add(null);
            } else {
                double avg = l.stream().mapToInt(x -> x).average().orElse(0);
                avgMoodList.add(avg);
            }
        }

        try {
            NotebookStore store = new NotebookStore();
            java.util.List<NotebookInfo> nbs = store.list();
            DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyyMMdd");
            for (NotebookInfo nb : nbs) {
                if (nb == null || nb.getType() != NotebookInfo.Type.JOURNAL) continue;
                File folder = nb.getFolder();
                if (folder == null) continue;
                File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".txt"));
                if (files == null) continue;
                for (File f : files) {
                    String name = f.getName();
                    LocalDate d; LocalDateTime ts;
                    try {
                        if (name.matches("\\d{8}_\\d{6}.*\\.txt")) {
                            String ymd = name.substring(0, 8);
                            String hms = name.substring(9, 15);
                            ts = LocalDateTime.parse(ymd+"_"+hms, DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                            d = ts.toLocalDate();
                        } else {
                            ts = Instant.ofEpochMilli(f.lastModified()).atZone(ZoneId.systemDefault()).toLocalDateTime();
                            d = ts.toLocalDate();
                        }
                    } catch (Throwable ignored) { continue; }
                    entriesByDate.computeIfAbsent(d, k -> new ArrayList<>()).add(f);
                    entryTimesByDate.computeIfAbsent(d, k -> new ArrayList<>()).add(new EntryRef(f, ts));
                }
            }
        } catch (Throwable ignored) {}
    }
    
    private boolean hasAnyJournalEntries() {
        try {
            NotebookStore store = new NotebookStore();
            java.util.List<NotebookInfo> list = store.list();
            if (list == null || list.isEmpty()) return false;
            for (NotebookInfo nb : list) {
                if (nb != null && nb.getType() == NotebookInfo.Type.JOURNAL) {
                    File folder = nb.getFolder();
                    if (folder != null && folder.isDirectory()) {
                        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".txt"));
                        if (files != null && files.length > 0) return true;
                    }
                }
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if(dayList.isEmpty() || avgMoodList.stream().allMatch(Objects::isNull)){
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

        for (int t = 0; t <= 4; t++) {
            int pct = t * 25;
            int y = getHeight()-margin - (int)(pct / 100.0 * h);
            g2.setColor(new Color(200,200,200));
            g2.drawLine(margin, y, getWidth()-margin, y);
            g2.setColor(Color.DARK_GRAY);
            String lab = String.valueOf(pct);
            int tw = g2.getFontMetrics().stringWidth(lab);
            g2.drawString(lab, margin - tw - 8, y + 4);
        }

        java.util.function.Function<Double, Color> colorFor = v -> {
            int p = (int)Math.round(v);
            if (p <= 33) return new Color(200,60,60);
            if (p <= 66) return new Color(230,160,50);
            return new Color(40,160,90);
        };

        java.util.List<Double> ma = new ArrayList<>(avgMoodList.size());
        for (int i = 0; i < avgMoodList.size(); i++) {
            Double a = avgMoodList.get(i);
            Double b = i>0 ? avgMoodList.get(i-1) : null;
            Double c = i+1<avgMoodList.size()? avgMoodList.get(i+1) : null;
            double sum=0; int cnt=0;
            if (b!=null){sum+=b;cnt++;}
            if (a!=null){sum+=a;cnt++;}
            if (c!=null){sum+=c;cnt++;}
            ma.add(cnt==0?null:sum/cnt);
        }

        int n = dayList.size();
        Integer lastX = null; Integer lastY = null;
        for (int i = 0; i < n; i++) {
            int x = margin + (int)(i * (n>1? (double)w/(n-1): w));
            Double v = avgMoodList.get(i);
            if (v != null) {
                int y = getHeight()-margin - (int)(v / 100.0 * h);
                if (lastX != null && lastY != null) {
                    g2.setColor(new Color(0,120,215));
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawLine(lastX, lastY, x, y);
                }
                g2.setColor(colorFor.apply(v));
                g2.fillOval(x-3,y-3,6,6);
                lastX = x; lastY = y;
            } else {
                lastX = null; lastY = null;
            }
        }

        lastX = null; lastY = null;
        g2.setColor(new Color(100,60,180));
        g2.setStroke(new BasicStroke(1.5f));
        for (int i = 0; i < n; i++) {
            int x = margin + (int)(i * (n>1? (double)w/(n-1): w));
            Double v = ma.get(i);
            if (v != null) {
                int y = getHeight()-margin - (int)(v / 100.0 * h);
                if (lastX != null && lastY != null) g2.drawLine(lastX, lastY, x, y);
                lastX = x; lastY = y;
            } else { lastX = null; lastY = null; }
        }

        // date labels
        g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        if (dayList.size() > 0) {
            int labelTarget = 8;
            int step = Math.max(1, dayList.size() / labelTarget);
            for(int i=0;i<dayList.size();i+=step){
                int x = margin + (int)(i*(dayList.size()>1? (double)w/(dayList.size()-1): w));
                String txt = fmt.format(dayList.get(i));
                int tw=g2.getFontMetrics().stringWidth(txt);
                g2.drawString(txt, x-tw/2, getHeight()-margin+15);
            }
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
                int idx = Math.round((float)(e.getX()-margin)/Math.max(1,w)*(dayList.size()-1));
                if(idx>=0 && idx<dayList.size()){
                    Double raw = avgMoodList.get(idx);
                    LocalDate d = dayList.get(idx);
                    java.util.List<File> files = entriesByDate.get(d);
                    if (raw == null) {
                        if (files == null || files.isEmpty()) setToolTipText(d+" no entry");
                        else setToolTipText(d+" ("+files.size()+") click to open");
                    } else {
                        if (files == null || files.isEmpty()) setToolTipText(d+" avg mood: "+(int)Math.round(raw)+"/100");
                        else if (files.size()==1) setToolTipText(d+" · "+safeTitle(files.get(0))+" (click)");
                        else setToolTipText(d+" · "+files.size()+" entries (click)");
                    }

                    // Cmd-hover: open after a short delay if Command is held
                    if (e.isMetaDown() && files != null && !files.isEmpty()) {
                        if (!Integer.valueOf(idx).equals(pendingHoverIdx)) {
                            pendingHoverIdx = idx;
                            if (hoverOpenTimer != null && hoverOpenTimer.isRunning()) hoverOpenTimer.stop();
                            final int targetIdx = idx;
                            hoverOpenTimer = new javax.swing.Timer(250, ae -> {
                                try {
                                    if (pendingHoverIdx != null && pendingHoverIdx == targetIdx) {
                                        openNearestForDay(dayList.get(targetIdx));
                                        pendingHoverIdx = null;
                                    }
                                } catch (Throwable ignored) {}
                            });
                            hoverOpenTimer.setRepeats(false);
                            hoverOpenTimer.start();
                        }
                    } else {
                        pendingHoverIdx = null;
                        if (hoverOpenTimer != null && hoverOpenTimer.isRunning()) hoverOpenTimer.stop();
                    }
                }
            }
        });
        addMouseListener(new MouseAdapter(){
            @Override public void mouseClicked(MouseEvent e){
                if(dayList.isEmpty()) return;
                int margin=60;
                int w = getWidth()-2*margin;
                int idx = Math.round((float)(e.getX()-margin)/Math.max(1,w)*(dayList.size()-1));
                if(idx<0 || idx>=dayList.size()) return;
                Double raw = avgMoodList.get(idx);
                if (raw == null) return;
                LocalDate d = dayList.get(idx);
                java.util.List<File> files = entriesByDate.get(d);
                if (files == null || files.isEmpty()) return;
                if (e.isMetaDown()) {
                    openNearestForDay(d);
                    return;
                }
                if (files.size() == 1) {
                    NotebookInfo nb = findNotebookFor(files.get(0));
                    if (nb != null) app.openExistingEntryEditor(nb, files.get(0));
                } else {
                    JPopupMenu menu = new JPopupMenu();
                    for (File f : files) {
                        JMenuItem it = new JMenuItem(safeTitle(f));
                        it.addActionListener(ev -> {
                            NotebookInfo nb = findNotebookFor(f);
                            if (nb != null) app.openExistingEntryEditor(nb, f);
                        });
                        menu.add(it);
                    }
                    menu.show(MoodChartPanel.this, e.getX(), e.getY());
                }
            }
        });
    }

    private void openNearestForDay(LocalDate d){
        java.util.List<EntryRef> refs = entryTimesByDate.get(d);
        java.util.List<LocalDateTime> moodTimes = moodTimesByDate.get(d);
        if (refs == null || refs.isEmpty()) return;
        File target = null;
        if (moodTimes != null && !moodTimes.isEmpty()) {
            LocalDateTime anchor = moodTimes.get(moodTimes.size()-1); // prefer latest mood sample that day
            long best = Long.MAX_VALUE;
            for (EntryRef r : refs) {
                if (r.ts == null) continue;
                long diff = Math.abs(ChronoUnit.SECONDS.between(anchor, r.ts));
                if (diff < best) { best = diff; target = r.file; }
            }
        }
        if (target == null) {
            // Fallback: open the most recently modified entry that day
            target = refs.stream().max(java.util.Comparator.comparingLong(er -> er.file.lastModified())).map(er -> er.file).orElse(null);
        }
        if (target != null) {
            NotebookInfo nb = findNotebookFor(target);
            if (nb != null) app.openExistingEntryEditor(nb, target);
        }
    }

    private NotebookInfo findNotebookFor(File f){
        try {
            NotebookStore store = new NotebookStore();
            for (NotebookInfo nb : store.list()) {
                File folder = nb.getFolder();
                if (folder != null && f.getAbsolutePath().startsWith(folder.getAbsolutePath()+File.separator)) return nb;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private String safeTitle(File f){
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String first = br.readLine();
            if (first != null && !first.isBlank()) return first.trim();
        } catch (IOException ignored) {}
        String nm = f.getName();
        int dot = nm.lastIndexOf('.');
        return dot>0?nm.substring(0,dot):nm;
    }
}
