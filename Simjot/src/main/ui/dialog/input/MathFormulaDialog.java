package main.ui.dialog.input;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;

/**
 * Lightweight LaTeX math input dialog with live preview.
 * Uses JLaTeXMath if present on classpath; otherwise falls back to plain text render.
 */
public class MathFormulaDialog extends JDialog {
    public static class Result {
        public final BufferedImage image;
        public final String latex;
        public final int size;
        public Result(BufferedImage image, String latex, int size) {
            this.image = image; this.latex = latex; this.size = size;
        }
    }

    private final JTextArea input;
    private final JSpinner sizeSpinner;
    private final JLabel previewLabel;
    private final JCheckBox transparentBg;
    private BufferedImage currentImage;

    private MathFormulaDialog(Frame owner) {
        super(owner, "Insert Math Formula", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        input = new JTextArea(4, 40);
        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        input.setFont(new Font("Monospaced", Font.PLAIN, 13));
        JScrollPane inputScroll = new JScrollPane(input);

        sizeSpinner = new JSpinner(new SpinnerNumberModel(18, 8, 72, 1));
        transparentBg = new JCheckBox("Transparent background", true);

        JPanel top = new JPanel(new BorderLayout(8, 8));
        JPanel opts = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        opts.add(new JLabel("Size:"));
        opts.add(sizeSpinner);
        opts.add(transparentBg);
        top.add(new JLabel("LaTeX:"), BorderLayout.NORTH);
        top.add(inputScroll, BorderLayout.CENTER);
        top.add(opts, BorderLayout.SOUTH);

        previewLabel = new JLabel();
        previewLabel.setHorizontalAlignment(SwingConstants.CENTER);
        previewLabel.setVerticalAlignment(SwingConstants.CENTER);
        JScrollPane prevScroll = new JScrollPane(previewLabel);
        prevScroll.setPreferredSize(new Dimension(420, 240));
        JPanel previewPanel = new JPanel(new BorderLayout());
        previewPanel.add(new JLabel("Preview:"), BorderLayout.NORTH);
        previewPanel.add(prevScroll, BorderLayout.CENTER);

        JPanel center = new JPanel(new GridLayout(1, 2, 8, 8));
        center.add(top);
        center.add(previewPanel);
        add(center, BorderLayout.CENTER);

        JButton insertBtn = new JButton("Insert");
        JButton cancelBtn = new JButton("Cancel");
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(cancelBtn);
        bottom.add(insertBtn);
        add(bottom, BorderLayout.SOUTH);

        Runnable refresh = this::renderPreview;
        input.addKeyListener(new KeyAdapter(){
            @Override public void keyReleased(KeyEvent e){ refresh.run(); }
        });
        sizeSpinner.addChangeListener(e -> refresh.run());
        transparentBg.addActionListener(e -> refresh.run());

        cancelBtn.addActionListener(e -> { currentImage = null; dispose(); });
        insertBtn.addActionListener(e -> { renderPreview(); dispose(); });

        getRootPane().setDefaultButton(insertBtn);
        pack();
        setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(refresh);
    }

    public static Result showDialog(Window owner) {
        Frame f = owner instanceof Frame ? (Frame) owner : null;
        MathFormulaDialog d = new MathFormulaDialog(f);
        d.setVisible(true);
        if (d.currentImage == null) return null;
        int sz = (int) d.sizeSpinner.getValue();
        return new Result(d.currentImage, d.input.getText(), sz);
    }

    private void renderPreview() {
        String latex = input.getText().trim();
        int size = (int) sizeSpinner.getValue();
        boolean transparent = transparentBg.isSelected();
        if (latex.isEmpty()) {
            previewLabel.setIcon(null);
            currentImage = null;
            return;
        }
        BufferedImage img = renderLatexToImage(latex, size, transparent);
        currentImage = img;
        if (img != null) {
            previewLabel.setIcon(new ImageIcon(img));
        } else {
            previewLabel.setIcon(null);
        }
    }

    // Try to render via JLaTeXMath using reflection to avoid hard dependency.
    private BufferedImage renderLatexToImage(String latex, int size, boolean transparent) {
        try {
            Class<?> texFormulaClz = Class.forName("org.scilab.forge.jlatexmath.TeXFormula");
            Class<?> texConstantsClz = Class.forName("org.scilab.forge.jlatexmath.TeXConstants");
            Object formula = texFormulaClz.getConstructor(String.class).newInstance(latex);
            int STYLE_DISPLAY = texConstantsClz.getField("STYLE_DISPLAY").getInt(null);
            java.lang.reflect.Method createIcon = texFormulaClz.getMethod("createTeXIcon", int.class, float.class);
            Object icon = createIcon.invoke(formula, STYLE_DISPLAY, (float) size);

            int w = (int) icon.getClass().getMethod("getIconWidth").invoke(icon);
            int h = (int) icon.getClass().getMethod("getIconHeight").invoke(icon);
            if (w <= 0 || h <= 0) { w = 1; h = size + 8; }
            BufferedImage img = new BufferedImage(w, h, transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = img.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (!transparent) {
                g2.setColor(Color.WHITE);
                g2.fillRect(0, 0, w, h);
            }
            // paintIcon(Component c, Graphics g, int x, int y)
            icon.getClass().getMethod("paintIcon", Component.class, Graphics.class, int.class, int.class)
                    .invoke(icon, null, g2, 0, 0);
            g2.dispose();
            return img;
        } catch (Throwable t) {
            // Fallback: draw plain text so user sees something, not real LaTeX render
            try {
                Font font = new Font("Serif", Font.PLAIN, size);
                BufferedImage tmp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = tmp.createGraphics();
                g2.setFont(font);
                FontMetrics fm = g2.getFontMetrics();
                int w = Math.max(1, fm.stringWidth(latex) + 8);
                int h = Math.max(1, fm.getHeight() + 6);
                g2.dispose();
                BufferedImage img = new BufferedImage(w, h, transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
                g2 = img.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                if (!transparent) { g2.setColor(Color.WHITE); g2.fillRect(0, 0, w, h); }
                g2.setColor(Color.BLACK);
                g2.setFont(font);
                g2.drawString(latex, 4, h - fm.getDescent() - 2);
                g2.dispose();
                return img;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }
}
