package main.ui.features.widgets;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.icons.VectorIconPainter;
import main.ui.theme.aero.AeroTheme;

/**
 * A lightweight Pomodoro timer widget. When enabled, it shows a small always-on-top dialog
 * with a 25-minute focus timer and 5-minute break cycles. Provides Start/Pause, Skip, and Reset.
 */
public class PomodoroWidget implements Widget {
    private static final int DEFAULT_FOCUS_MIN = 25;
    private static final int DEFAULT_BREAK_MIN = 5;
    private static final int DEFAULT_LONG_BREAK_MIN = 15;
    private static final int DEFAULT_CYCLES_BEFORE_LONG = 4;

    private enum Phase { FOCUS, BREAK, LONG_BREAK }

    private final JFrame owner;
    private JDialog dialog;
    private Timer tick;
    // Removed analog repaint timer; using digital labels only
    private Phase phase = Phase.FOCUS;
    private boolean running = false;

    private int remainingSeconds;
    private int focusMinutes = DEFAULT_FOCUS_MIN;
    private int breakMinutes = DEFAULT_BREAK_MIN;
    private int longBreakMinutes = DEFAULT_LONG_BREAK_MIN;
    private int cyclesBeforeLong = DEFAULT_CYCLES_BEFORE_LONG;
    private int completedFocusCount = 0;

    private JLabel timeLabel;
    private JLabel phaseLabel;
    private JButton startPauseBtn;
    private JButton skipBtn;
    private JButton resetBtn;
    private JLabel elapsedLabel;

    private boolean enabled = false;

    public PomodoroWidget(JFrame owner) {
        this.owner = owner;
    }

    // Create a small header icon button that paints via VectorIconPainter for exact reuse
    private JButton makeHeaderIconButton(String id, String tooltip) {
        JButton b = new JButton(new Icon() {
            private final int s = 18;
            @Override public int getIconWidth() { return s; }
            @Override public int getIconHeight() { return s; }
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                java.awt.image.BufferedImage img = VectorIconPainter.getImage(id, s);
                if (img != null) {
                    g2.drawImage(img, x, y, null);
                } else {
                    // Fallback to vector draw if unknown id
                    VectorIconPainter.paint(g2, id, x, y, s);
                }
                g2.dispose();
            }
        });
        b.setPreferredSize(new Dimension(22, 22));
        b.setOpaque(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setToolTipText(tooltip);
        return b;
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
        running = false;
        if (tick != null) tick.stop();
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
        // Exact same icons as Sticky: delete (close) and settings
        JButton closeBtn = makeHeaderIconButton("delete", "Close Pomodoro");
        closeBtn.addActionListener(e -> stop());
        JButton settingsBtn = makeHeaderIconButton("settings", "Pomodoro Settings");
        settingsBtn.addActionListener(e -> showSettingsDialog());
        header.add(phaseLabel, BorderLayout.CENTER);
        header.add(settingsBtn, BorderLayout.EAST);
        header.add(closeBtn, BorderLayout.WEST);
        content.add(header, BorderLayout.NORTH);

        // Time display (remaining large)
        timeLabel = new JLabel("25:00", SwingConstants.CENTER);
        timeLabel.setForeground(AeroTheme.TEXT_PRIMARY);
        timeLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        timeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        // Elapsed time (smaller)
        elapsedLabel = new JLabel("Elapsed 00:00", SwingConstants.CENTER);
        elapsedLabel.setForeground(AeroTheme.TEXT_PRIMARY);
        elapsedLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        elapsedLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

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

        // Center content stack: digital remaining + digital elapsed
        JPanel centerStack = new JPanel();
        centerStack.setOpaque(false);
        centerStack.setLayout(new BoxLayout(centerStack, BoxLayout.Y_AXIS));
        centerStack.add(timeLabel);
        centerStack.add(Box.createVerticalStrut(4));
        centerStack.add(elapsedLabel);

        content.add(centerStack, BorderLayout.CENTER);

        dialog.setContentPane(content);
        dialog.setSize(220, 160);
        // Position near top-right of the owner or screen
        Point loc = owner != null ? owner.getLocationOnScreen() : new Point(100, 100);
        Dimension ownerSize = owner != null ? owner.getSize() : Toolkit.getDefaultToolkit().getScreenSize();
        dialog.setLocation(loc.x + ownerSize.width - dialog.getWidth() - 24, loc.y + 60);

        // Init state
        resetTimer();

        // Timer
        tick = new Timer(1000, e -> onTick());

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
        completedFocusCount = 0;
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
            // Completed a focus session
            completedFocusCount++;
            boolean takeLong = (completedFocusCount % Math.max(1, cyclesBeforeLong)) == 0;
            if (takeLong) {
                phase = Phase.LONG_BREAK;
                remainingSeconds = longBreakMinutes * 60;
            } else {
                phase = Phase.BREAK;
                remainingSeconds = breakMinutes * 60;
            }
        } else {
            // After any break, return to focus
            phase = Phase.FOCUS;
            remainingSeconds = focusMinutes * 60;
        }
        updateLabels();
        startPauseBtn.setText("Start");
    }

    private int getPhaseTotalSeconds() {
        switch (phase) {
            case FOCUS: return focusMinutes * 60;
            case LONG_BREAK: return longBreakMinutes * 60;
            case BREAK:
            default: return breakMinutes * 60;
        }
    }

    private void updateLabels() {
        int m = remainingSeconds / 60;
        int s = remainingSeconds % 60;
        timeLabel.setText(String.format("%02d:%02d", m, s));

        int elapsed = Math.max(0, getPhaseTotalSeconds() - remainingSeconds);
        int em = elapsed / 60;
        int es = elapsed % 60;
        elapsedLabel.setText(String.format("Elapsed %02d:%02d", em, es));

        // Color code: red for work (Focus), green for rest (Break/Long Break)
        boolean isFocus = (phase == Phase.FOCUS);
        Color workRed = new Color(220, 60, 60);
        Color restGreen = new Color(60, 180, 90);
        Color activeColor = isFocus ? workRed : restGreen;
        timeLabel.setForeground(activeColor);
        elapsedLabel.setForeground(activeColor);

        String phaseText;
        if (phase == Phase.FOCUS) phaseText = "Focus";
        else if (phase == Phase.LONG_BREAK) phaseText = "Long Break";
        else phaseText = "Break";
        phaseLabel.setText(phaseText);
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
        JLabel longBreakLbl = new JLabel("Long Break (min):");
        JSpinner longBreakSpin = new JSpinner(new SpinnerNumberModel(longBreakMinutes, 5, 60, 1));
        JLabel cyclesLbl = new JLabel("Cycles before long break:");
        JSpinner cyclesSpin = new JSpinner(new SpinnerNumberModel(cyclesBeforeLong, 1, 12, 1));

        gbc.gridx=0; gbc.gridy=0; form.add(focusLbl, gbc);
        gbc.gridx=1; gbc.gridy=0; form.add(focusSpin, gbc);
        gbc.gridx=0; gbc.gridy=1; form.add(breakLbl, gbc);
        gbc.gridx=1; gbc.gridy=1; form.add(breakSpin, gbc);
        gbc.gridx=0; gbc.gridy=2; form.add(longBreakLbl, gbc);
        gbc.gridx=1; gbc.gridy=2; form.add(longBreakSpin, gbc);
        gbc.gridx=0; gbc.gridy=3; form.add(cyclesLbl, gbc);
        gbc.gridx=1; gbc.gridy=3; form.add(cyclesSpin, gbc);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        RoundedButton ok = new RoundedButton("Save");
        RoundedButton cancel = new RoundedButton("Cancel");
        ok.addActionListener(e -> {
            focusMinutes = (Integer) focusSpin.getValue();
            breakMinutes = (Integer) breakSpin.getValue();
            longBreakMinutes = (Integer) longBreakSpin.getValue();
            cyclesBeforeLong = (Integer) cyclesSpin.getValue();
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
