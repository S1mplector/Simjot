package main.ui.features.widgets;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import main.infrastructure.io.AppDirectories;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.icons.ImageIconRenderer;

/**
 * A compact widget to quickly log mood to mood_log.txt without opening an editor.
 * Provides 5 preset buttons and a small slider for custom value.
 */
public class QuickMoodWidget implements Widget {
    private final JFrame owner;
    private JDialog dialog;
    private boolean enabled = false;

    private JSlider slider;
    private JLabel statusLabel;
    private javax.swing.Timer statusClearTimer;

    public QuickMoodWidget(JFrame owner) {
        this.owner = owner;
    }

    @Override
    public void start() {
        if (enabled) return;
        enabled = true;
        ensureDialog();
        dialog.setVisible(true);
        dialog.toFront();
    }

    @Override
    public void stop() {
        enabled = false;
        if (dialog != null) dialog.setVisible(false);
    }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public String getName() { return "Quick Mood Log"; }

    @Override
    public String getIconId() { return "smile"; }

    private void ensureDialog() {
        if (dialog != null) return;
        dialog = new JDialog(owner, "Quick Mood", false);
        dialog.setUndecorated(true);
        dialog.setAlwaysOnTop(true);
        dialog.getRootPane().putClientProperty("apple.awt.draggableWindowBackground", Boolean.TRUE);
        dialog.setBackground(new Color(0,0,0,0));
        dialog.setLayout(new BorderLayout());

        JPanel content = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                Paint bg = new LinearGradientPaint(0, 0, 0, h,
                        new float[]{0f, 0.5f, 1f},
                        new Color[]{new Color(252,252,252,210), new Color(236,236,236,210), new Color(222,222,222,210)});
                g2.setPaint(bg);
                g2.fillRoundRect(0, 0, w, h, 16, 16);
                g2.setColor(new Color(170,170,170));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, 16, 16);
                g2.dispose();
            }
        };
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        content.setLayout(new BorderLayout(8, 8));

        // Header with close button
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Quick Mood", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 14));
        JButton close = new JButton(new ImageIcon(ImageIconRenderer.get(ImageIconRenderer.mapIdToResource("close"), 16, true)));
        close.setOpaque(false);
        close.setContentAreaFilled(false);
        close.setBorderPainted(false);
        close.setFocusPainted(false);
        close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        close.setToolTipText("Close");
        close.addActionListener(e -> stop());
        header.add(close, BorderLayout.WEST);
        header.add(title, BorderLayout.CENTER);
        content.add(header, BorderLayout.NORTH);

        // Center: preset buttons + slider
        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        JPanel presets = new JPanel(new GridLayout(1,5,6,0));
        presets.setOpaque(false);
        presets.add(makePresetButton("Bad", 20, new Color(200,60,60)));
        presets.add(makePresetButton("Meh", 40, new Color(220,140,70)));
        presets.add(makePresetButton("Okay", 60, new Color(180,160,80)));
        presets.add(makePresetButton("Good", 80, new Color(70,160,90)));
        presets.add(makePresetButton("Great", 100, new Color(60,180,120)));
        center.add(presets);
        center.add(Box.createVerticalStrut(8));

        JPanel sliderRow = new JPanel(new BorderLayout(6,0));
        sliderRow.setOpaque(false);
        slider = new JSlider(0, 100, 50);
        slider.setOpaque(false);
        sliderRow.add(slider, BorderLayout.CENTER);
        RoundedButton logBtn = new RoundedButton("Log");
        logBtn.addActionListener(e -> logMood(slider.getValue()));
        sliderRow.add(logBtn, BorderLayout.EAST);
        center.add(sliderRow);

        statusLabel = new JLabel(" ");
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusLabel.setForeground(new Color(0,120,215));
        center.add(Box.createVerticalStrut(6));
        center.add(statusLabel);

        content.add(center, BorderLayout.CENTER);

        // Drag anywhere
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

        dialog.setContentPane(content);
        dialog.setSize(260, 160);
        Point loc = owner != null ? owner.getLocationOnScreen() : new Point(100, 100);
        Dimension ownerSize = owner != null ? owner.getSize() : Toolkit.getDefaultToolkit().getScreenSize();
        dialog.setLocation(loc.x + ownerSize.width - dialog.getWidth() - 24, loc.y + 240);
    }

    private JButton makePresetButton(String text, int value, Color color) {
        RoundedButton b = new RoundedButton(text);
        b.setPreferredSize(new Dimension(80, 30));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setForeground(Color.DARK_GRAY);
        b.addActionListener(e -> logMood(value));
        return b;
    }

    private void logMood(int moodValue) {
        File moodFile = new File(AppDirectories.folder(AppDirectories.Type.MOOD_DATA), "mood_log.txt");
        try (PrintWriter writer = new PrintWriter(new FileWriter(moodFile, true))) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
            String timestamp = sdf.format(new Date());
            writer.println(timestamp + "," + moodValue);
            showStatus("Logged " + moodValue);
        } catch (IOException ex) {
            showStatus("Failed to log");
        }
    }

    private void showStatus(String msg) {
        statusLabel.setText(msg);
        if (statusClearTimer != null && statusClearTimer.isRunning()) statusClearTimer.stop();
        statusClearTimer = new javax.swing.Timer(1200, e -> statusLabel.setText(" "));
        statusClearTimer.setRepeats(false);
        statusClearTimer.start();
    }
}
