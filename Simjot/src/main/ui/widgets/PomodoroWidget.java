package main.ui.widgets;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.time.LocalTime;
import main.ui.buttons.RoundedButton;
import main.ui.theme.aero.AeroTheme;

/**
 * A lightweight Pomodoro timer widget. When enabled, it shows a small always-on-top dialog
 * with a 25-minute focus timer and 5-minute break cycles. Provides Start/Pause, Skip, and Reset.
 */
public class PomodoroWidget implements Widget {
    private static final int DEFAULT_FOCUS_MIN = 25;
    private static final int DEFAULT_BREAK_MIN = 5;

    private enum Phase { FOCUS, BREAK }

    private final JFrame owner;
    private JDialog dialog;
    private Timer tick;
    private Timer analogTimer;
    private Phase phase = Phase.FOCUS;
    private boolean running = false;

    private int remainingSeconds;
    private int focusMinutes = DEFAULT_FOCUS_MIN;
    private int breakMinutes = DEFAULT_BREAK_MIN;

    private JLabel timeLabel;
    private JLabel phaseLabel;
    private JButton startPauseBtn;
    private JButton skipBtn;
    private JButton resetBtn;
    private JPanel analogPanel;

    private boolean enabled = false;

    public PomodoroWidget(JFrame owner) {
        this.owner = owner;
    }

    @Override
    public void start() {
        if (enabled) return;
        enabled = true;
        ensureDialog();
        dialog.setVisible(true);
        dialog.toFront();
        if (analogTimer != null && !analogTimer.isRunning()) analogTimer.start();
    }

    @Override
    public void stop() {
        enabled = false;
        running = false;
        if (tick != null) tick.stop();
        if (analogTimer != null) analogTimer.stop();
        if (dialog != null) dialog.setVisible(false);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getName() { return "Pomodoro"; }

    @Override
    public String getIconId() { return "clock"; }

    private void ensureDialog() {
        if (dialog != null) return;
        dialog = new JDialog(owner, "Pomodoro", false);
        dialog.setUndecorated(true);
        dialog.setAlwaysOnTop(true);
        dialog.getRootPane().putClientProperty("apple.awt.draggableWindowBackground", Boolean.TRUE);
        dialog.setLayout(new BorderLayout());
        // Make window fully transparent so only our rounded panel is visible (no sharp rectangular frame)
        dialog.setBackground(new Color(0,0,0,0));

        JPanel content = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();

                // Aero silvery panel gradient
                Paint bg = new LinearGradientPaint(0, 0, 0, h,
                        new float[]{0f, 0.5f, 1f},
                        new Color[]{new Color(252,252,252,200), new Color(236,236,236,200), new Color(222,222,222,200)});
                g2.setPaint(bg);
                g2.fillRoundRect(0, 0, w, h, 16, 16);

                // Glass highlight
                g2.setPaint(new GradientPaint(0, 0, new Color(255,255,255,170), 0, h/2f, new Color(255,255,255,0)));
                g2.fillRoundRect(1, 1, w-2, Math.max(1, h/2), 14, 14);

                // Subtle border
                g2.setColor(new Color(170,170,170));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, 16, 16);
                g2.dispose();
            }
        };
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        content.setLayout(new BorderLayout(8, 8));

        // Header with settings
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        phaseLabel = new JLabel("Focus", SwingConstants.CENTER);
        phaseLabel.setForeground(AeroTheme.TEXT_PRIMARY);
        phaseLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        // Close button (×)
        JButton closeBtn = new JButton("\u00D7");
        closeBtn.setOpaque(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setFocusPainted(false);
        closeBtn.setForeground(AeroTheme.TEXT_PRIMARY);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.setToolTipText("Close Pomodoro");
        closeBtn.addActionListener(e -> stop());
        JButton settingsBtn = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                int s = Math.min(w, h) - 6; // padding
                int cx = w/2, cy = h/2;
                int rOuter = s/2;
                int rInner = Math.max(2, rOuter - 4);
                // Teeth
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(AeroTheme.TEXT_PRIMARY);
                for (int i=0;i<8;i++) {
                    double a = Math.toRadians(i * 45);
                    int tx1 = cx + (int)((rInner+1) * Math.cos(a));
                    int ty1 = cy + (int)((rInner+1) * Math.sin(a));
                    int tx2 = cx + (int)((rOuter) * Math.cos(a));
                    int ty2 = cy + (int)((rOuter) * Math.sin(a));
                    g2.drawLine(tx1, ty1, tx2, ty2);
                }
                // Outer ring
                g2.drawOval(cx - rInner, cy - rInner, rInner*2, rInner*2);
                // Hub
                g2.fillOval(cx - 2, cy - 2, 4, 4);
                g2.dispose();
            }
        };
        settingsBtn.setPreferredSize(new Dimension(22, 22));
        settingsBtn.setOpaque(false);
        settingsBtn.setContentAreaFilled(false);
        settingsBtn.setBorderPainted(false);
        settingsBtn.setFocusPainted(false);
        settingsBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        settingsBtn.setToolTipText("Pomodoro Settings");
        settingsBtn.addActionListener(e -> showSettingsDialog());
        header.add(phaseLabel, BorderLayout.CENTER);
        header.add(settingsBtn, BorderLayout.EAST);
        header.add(closeBtn, BorderLayout.WEST);
        content.add(header, BorderLayout.NORTH);

        // Time display
        timeLabel = new JLabel("25:00", SwingConstants.CENTER);
        timeLabel.setForeground(AeroTheme.TEXT_PRIMARY);
        timeLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        timeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Controls
        JPanel controls = new JPanel();
        controls.setOpaque(false);
        controls.setLayout(new GridLayout(1, 3, 8, 0));
        startPauseBtn = makeButton("Start");
        skipBtn = makeButton("Skip");
        resetBtn = makeButton("Reset");
        controls.add(startPauseBtn);
        controls.add(skipBtn);
        controls.add(resetBtn);
        content.add(controls, BorderLayout.SOUTH);

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

        // Center content stack: digital then analog
        JPanel centerStack = new JPanel();
        centerStack.setOpaque(false);
        centerStack.setLayout(new BoxLayout(centerStack, BoxLayout.Y_AXIS));
        centerStack.add(timeLabel);
        analogPanel = new AnalogClockPanel();
        analogPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerStack.add(Box.createVerticalStrut(6));
        centerStack.add(analogPanel);

        content.add(centerStack, BorderLayout.CENTER);

        dialog.setContentPane(content);
        dialog.setSize(220, 260);
        // Position near top-right of the owner or screen
        Point loc = owner != null ? owner.getLocationOnScreen() : new Point(100, 100);
        Dimension ownerSize = owner != null ? owner.getSize() : Toolkit.getDefaultToolkit().getScreenSize();
        dialog.setLocation(loc.x + ownerSize.width - dialog.getWidth() - 24, loc.y + 60);

        // Init state
        resetTimer();

        // Timer
        tick = new Timer(1000, e -> onTick());
        analogTimer = new Timer(250, e -> {
            if (analogPanel != null && analogPanel.isShowing()) analogPanel.repaint();
        });

        // Actions
        startPauseBtn.addActionListener(e -> toggleStartPause());
        skipBtn.addActionListener(e -> skipPhase());
        resetBtn.addActionListener(e -> resetTimer());
    }

    private JButton makeButton(String text) {
        RoundedButton b = new RoundedButton(text);
        // Slightly smaller to fit our compact dialog
        b.setPreferredSize(new Dimension(90, 28));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private void resetTimer() {
        running = false;
        if (tick != null) tick.stop();
        phase = Phase.FOCUS;
        remainingSeconds = focusMinutes * 60;
        updateLabels();
        startPauseBtn.setText("Start");
    }

    private void toggleStartPause() {
        running = !running;
        if (running) {
            if (!tick.isRunning()) tick.start();
            startPauseBtn.setText("Pause");
        } else {
            tick.stop();
            startPauseBtn.setText("Resume");
        }
    }

    private void skipPhase() {
        switchPhase();
    }

    private void onTick() {
        if (!running) return;
        if (remainingSeconds > 0) {
            remainingSeconds--;
            updateLabels();
        } else {
            // Phase complete
            Toolkit.getDefaultToolkit().beep();
            switchPhase();
        }
    }

    private void switchPhase() {
        running = false;
        if (tick != null) tick.stop();
        if (phase == Phase.FOCUS) {
            phase = Phase.BREAK;
            remainingSeconds = breakMinutes * 60;
        } else {
            phase = Phase.FOCUS;
            remainingSeconds = focusMinutes * 60;
        }
        updateLabels();
        startPauseBtn.setText("Start");
    }

    private void updateLabels() {
        int m = remainingSeconds / 60;
        int s = remainingSeconds % 60;
        timeLabel.setText(String.format("%02d:%02d", m, s));
        phaseLabel.setText(phase == Phase.FOCUS ? "Focus" : "Break");
    }

    // --- Custom analog clock panel ---
    private class AnalogClockPanel extends JPanel {
        AnalogClockPanel() { setPreferredSize(new Dimension(150, 150)); setMinimumSize(new Dimension(120,120)); setOpaque(false); }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int size = Math.min(w, h) - 10;
            int x = (w - size) / 2;
            int y = (h - size) / 2;

            // Dial outline only (no fill)
            g2.setColor(new Color(0,0,0,110));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawOval(x, y, size, size);

            // Ticks (60 minor + 12 major)
            int cx = x + size/2;
            int cy = y + size/2;
            g2.setColor(new Color(0,0,0,90));
            for (int i=0;i<60;i++){
                double a = Math.toRadians(i*6 - 90);
                int r1 = size/2 - 4;
                int r2 = size/2 - (i%5==0 ? 1 : 2);
                int x1 = cx + (int)(r1*Math.cos(a));
                int y1 = cy + (int)(r1*Math.sin(a));
                int x2 = cx + (int)(r2*Math.cos(a));
                int y2 = cy + (int)(r2*Math.sin(a));
                g2.drawLine(x1,y1,x2,y2);
            }
            g2.setColor(new Color(0,0,0,160));
            for (int i=0;i<12;i++){
                double a = Math.toRadians(i*30 - 90);
                int r1 = size/2 - 6;
                int r2 = size/2 - 1;
                int x1 = cx + (int)(r1*Math.cos(a));
                int y1 = cy + (int)(r1*Math.sin(a));
                int x2 = cx + (int)(r2*Math.cos(a));
                int y2 = cy + (int)(r2*Math.sin(a));
                g2.drawLine(x1,y1,x2,y2);
            }

            // Numerals at 12/3/6/9
            g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
            g2.setColor(new Color(0,0,0,190));
            drawCentered(g2, "12", cx, y + 16);
            drawCentered(g2, "3",  x + size - 16, cy + 5);
            drawCentered(g2, "6",  cx, y + size - 8);
            drawCentered(g2, "9",  x + 16, cy + 5);

            // Highlight arc for upcoming session
            int durationMin = (phase == Phase.FOCUS ? focusMinutes : breakMinutes);
            Color arcColor = (phase == Phase.FOCUS) ? new Color(220,60,60) : new Color(60,180,90);

            long nowMs = System.currentTimeMillis();
            LocalTime t = LocalTime.now();
            int totalMin = (t.getHour()%12)*60 + t.getMinute();
            double nowDegCW = (totalMin + (t.getSecond() + (nowMs%1000)/1000.0)/60.0) * 6.0; // clockwise from 12
            int startAng = (int)Math.round(90 - nowDegCW); // Graphics start
            int extent = (int)Math.round(- durationMin * 6.0); // clockwise extent
            int pad = 6;
            int ox=x+pad, oy=y+pad, os=size-2*pad;
            g2.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(arcColor);
            g2.drawArc(ox, oy, os, os, startAng, extent);
            // simple tail fades near start and end
            int tail = Math.min(14, Math.abs(extent)/3);
            for (int i=1;i<=3;i++){
                float alpha = (4-i)/12f; // subtle
                g2.setComposite(AlphaComposite.SrcOver.derive(alpha));
                g2.drawArc(ox, oy, os, os, startAng, -i*tail);
                g2.drawArc(ox, oy, os, os, startAng+extent+i*tail, -i*tail);
            }
            g2.setComposite(AlphaComposite.SrcOver);

            // Hands
            double secA = Math.toRadians(((t.getSecond() + (nowMs%1000)/1000.0))*6 - 90);
            double minA = Math.toRadians((t.getMinute() + (t.getSecond() + (nowMs%1000)/1000.0)/60.0)*6 - 90);
            double hourA = Math.toRadians(((t.getHour()%12) + t.getMinute()/60.0)*30 - 90);

            g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(AeroTheme.TEXT_PRIMARY);
            drawHand(g2, cx, cy, hourA, size*0.25);
            g2.setStroke(new BasicStroke(3f));
            drawHand(g2, cx, cy, minA, size*0.35);
            g2.setColor(new Color(255,80,80));
            g2.setStroke(new BasicStroke(1.5f));
            drawHand(g2, cx, cy, secA, size*0.40);
            g2.fillOval(cx-2, cy-2, 4, 4);

            g2.dispose();
        }
        private void drawHand(Graphics2D g2, int cx, int cy, double angle, double len){
            int x2 = cx + (int)(len * Math.cos(angle));
            int y2 = cy + (int)(len * Math.sin(angle));
            g2.drawLine(cx, cy, x2, y2);
        }
        private void drawCentered(Graphics2D g2, String s, int cx, int cy){
            FontMetrics fm = g2.getFontMetrics();
            int x = cx - fm.stringWidth(s)/2;
            int y = cy - fm.getAscent()/2 + fm.getAscent();
            g2.drawString(s, x, y);
        }
    }

    private void showSettingsDialog() {
        JDialog d = new JDialog(owner, "Pomodoro Settings", true);
        d.setLayout(new BorderLayout(10, 10));
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4,4,4,4);
        gbc.anchor = GridBagConstraints.WEST;

        JLabel focusLbl = new JLabel("Focus (min):");
        JSpinner focusSpin = new JSpinner(new SpinnerNumberModel(focusMinutes, 5, 120, 1));
        JLabel breakLbl = new JLabel("Break (min):");
        JSpinner breakSpin = new JSpinner(new SpinnerNumberModel(breakMinutes, 1, 60, 1));

        gbc.gridx=0; gbc.gridy=0; form.add(focusLbl, gbc);
        gbc.gridx=1; gbc.gridy=0; form.add(focusSpin, gbc);
        gbc.gridx=0; gbc.gridy=1; form.add(breakLbl, gbc);
        gbc.gridx=1; gbc.gridy=1; form.add(breakSpin, gbc);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        RoundedButton ok = new RoundedButton("Save");
        RoundedButton cancel = new RoundedButton("Cancel");
        ok.addActionListener(e -> {
            focusMinutes = (Integer) focusSpin.getValue();
            breakMinutes = (Integer) breakSpin.getValue();
            // Reset to apply new durations
            resetTimer();
            d.dispose();
        });
        cancel.addActionListener(e -> d.dispose());
        buttons.add(ok);
        buttons.add(cancel);

        d.add(form, BorderLayout.CENTER);
        d.add(buttons, BorderLayout.SOUTH);
        d.pack();
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
    }
}
