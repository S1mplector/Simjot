package main.ui.features.home;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import javax.swing.*;
import main.core.service.NotebookStore;
import main.infrastructure.backup.NotebookInfo;
 
import main.ui.app.JournalApp;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.combobox.ModernComboBoxUI;

public class MoodChartPanel extends JPanel {
    private final MoodChartModel model = new MoodChartModel();
    private CardLayout cardLayout;
    private JPanel cardPanel;
    private JournalApp app;
    private JComboBox<String> rangeBox;
    private final String[] ranges = {"7 days","30 days","90 days","365 days","All"};
    private javax.swing.Timer hoverOpenTimer;
    private Integer pendingHoverIdx = null;
    private Integer currentHoverIdx = null;
    private final MoodChartSettings settings = new MoodChartSettings();
    private final MoodChartRenderer renderer = new MoodChartRenderer(settings);

    
    
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
        rangeBox.addActionListener(e->{ model.load(rangeBox.getSelectedIndex()); repaint(); });
        top.add(rangeBox);
        JCheckBox cbFill = new JCheckBox("Fill", settings.isShowFill());
        cbFill.setOpaque(false);
        cbFill.addActionListener(e->{ settings.setShowFill(cbFill.isSelected()); repaint(); });
        top.add(cbFill);
        JCheckBox cbTrend = new JCheckBox("Trend", settings.isShowTrend());
        cbTrend.setOpaque(false);
        cbTrend.addActionListener(e->{ settings.setShowTrend(cbTrend.isSelected()); repaint(); });
        top.add(cbTrend);
        JCheckBox cbTicks = new JCheckBox("Ticks", settings.isShowEntryTicks());
        cbTicks.setOpaque(false);
        cbTicks.addActionListener(e->{ settings.setShowEntryTicks(cbTicks.isSelected()); repaint(); });
        top.add(cbTicks);
        JButton exportBtn = new JButton("Export");
        exportBtn.addActionListener(e->{
            try {
                BufferedImage img = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = img.createGraphics();
                paint(g2);
                g2.dispose();
                javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
                fc.setSelectedFile(new File("mood_chart.png"));
                if (fc.showSaveDialog(MoodChartPanel.this) == javax.swing.JFileChooser.APPROVE_OPTION) {
                    File out = fc.getSelectedFile();
                    ImageIO.write(img, "png", out);
                }
            } catch (Throwable ignored) {}
        });
        top.add(exportBtn);
        add(top, BorderLayout.NORTH);

        // Load initial data
        model.load(rangeBox.getSelectedIndex());

        // Refresh data every time the panel becomes visible
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                model.load(rangeBox.getSelectedIndex());
                repaint();
            }
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                renderer.invalidate();
            }
        });
    }
    
    private void loadMoodData() {
        model.load(rangeBox.getSelectedIndex());
        renderer.invalidate();
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

        if(model.getDays().isEmpty() || model.getValues().stream().allMatch(Objects::isNull)){
            g2.setColor(Color.GRAY);
            String msg="No mood data yet.";
            FontMetrics fm=g2.getFontMetrics();
            g2.drawString(msg, (getWidth()-fm.stringWidth(msg))/2, getHeight()/2);
            g2.dispose();
            return;
        }

        renderer.paint(g2, this, model.getDays(), model.getValues(), model.getEntriesByDate(), currentHoverIdx);

        g2.dispose();
    }

    // tooltip support
    public MoodChartPanel(){ super(); }

    { // initializer for mouse tooltip
        setToolTipText("");
        addMouseMotionListener(new MouseMotionAdapter(){
            @Override public void mouseMoved(MouseEvent e){
                if(model.getDays().isEmpty()) return;
                int idx = renderer.indexForX(getWidth(), e.getX(), model.getDays().size());
                if(idx>=0 && idx<model.getDays().size()){
                    currentHoverIdx = idx;
                    Double raw = model.getValues().get(idx);
                    LocalDate d = model.getDays().get(idx);
                    java.util.List<File> files = model.getEntriesByDate().get(d);
                    MoodChartModel.Details det = model.getLatestDetailsFor(d);
                    if (raw == null) {
                        String base = d + (files == null || files.isEmpty() ? " no entry" : " ("+files.size()+") click to open");
                        if (det != null) {
                            String detail = String.format(" · J:%d C:%d G:%d En:%d | Sa:%d Ang:%d Anx:%d St:%d",
                                    det.joy, det.calm, det.gratitude, det.energy,
                                    det.sadness, det.anger, det.anxiety, det.stress);
                            setToolTipText(base + detail);
                        } else {
                            setToolTipText(base);
                        }
                    } else {
                        String base = d+" avg mood: "+(int)Math.round(raw)+"/100";
                        if (det != null) {
                            String detail = String.format(" · J:%d C:%d G:%d En:%d | Sa:%d Ang:%d Anx:%d St:%d",
                                    det.joy, det.calm, det.gratitude, det.energy,
                                    det.sadness, det.anger, det.anxiety, det.stress);
                            base += detail;
                        }
                        if (files == null || files.isEmpty()) setToolTipText(base);
                        else if (files.size()==1) setToolTipText(base+" · "+safeTitle(files.get(0))+" (click)");
                        else setToolTipText(base+" · "+files.size()+" entries (click)");
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
                                        openNearestForDay(model.getDays().get(targetIdx));
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
                        currentHoverIdx = null;
                    }
                }
            }
        });
        addMouseListener(new MouseAdapter(){
            @Override public void mouseClicked(MouseEvent e){
                if(model.getDays().isEmpty()) return;
                int margin=60;
                int w = getWidth()-2*margin;
                int idx = Math.round((float)(e.getX()-margin)/Math.max(1,w)*(model.getDays().size()-1));
                if(idx<0 || idx>=model.getDays().size()) return;
                Double raw = model.getValues().get(idx);
                if (raw == null) return;
                LocalDate d = model.getDays().get(idx);
                java.util.List<File> files = model.getEntriesByDate().get(d);
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
        java.util.List<MoodChartModel.EntryRef> refs = model.getEntryTimesByDate().get(d);
        java.util.List<LocalDateTime> moodTimes = model.getMoodTimesByDate().get(d);
        if (refs == null || refs.isEmpty()) return;
        File target = null;
        if (moodTimes != null && !moodTimes.isEmpty()) {
            LocalDateTime anchor = moodTimes.get(moodTimes.size()-1); // prefer latest mood sample that day
            long best = Long.MAX_VALUE;
            for (MoodChartModel.EntryRef r : refs) {
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
