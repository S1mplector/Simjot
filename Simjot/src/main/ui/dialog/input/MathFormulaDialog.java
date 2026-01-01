/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.dialog.input;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import main.ui.components.containers.FrostedGlassPanel;

public class MathFormulaDialog extends JDialog {
    public static class Result {
        public final BufferedImage image;
        public final String latex;
        public final int size;
        public Result(BufferedImage image, String latex, int size) {
            this.image = image; this.latex = latex; this.size = size;
        }
    }

    private boolean detectJlmAvailable() {
        try { Class.forName("org.scilab.forge.jlatexmath.TeXFormula"); return true; } catch (Throwable ignored) {}
        ensureJlmLoader();
        if (jlmLoader != null) {
            try { Class.forName("org.scilab.forge.jlatexmath.TeXFormula", true, jlmLoader); return true; } catch (Throwable ignored) {}
        }
        return false;
    }

    private void ensureJlmLoader() {
        if (jlmLoader != null) return;
        try {
            File jar = findJlmJar();
            if (jar != null) {
                URL url = jar.toURI().toURL();
                jlmLoader = new URLClassLoader(new URL[]{url}, getClass().getClassLoader());
            }
        } catch (Throwable ignored) {}
    }

    private File findJlmJar() {
        try {
            // 1) Working dir
            File found = scanLibsDir(new File("libs"));
            if (found != null) return found;
            // 2) Walk up to 5 parents looking for a libs directory
            File cur = new File(System.getProperty("user.dir", ".")).getAbsoluteFile();
            for (int i=0; i<5 && cur != null; i++) {
                File candidate = new File(cur, "libs");
                found = scanLibsDir(candidate);
                if (found != null) return found;
                cur = cur.getParentFile();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static File scanLibsDir(File dir){
        if (dir != null && dir.isDirectory()) {
            File[] c = dir.listFiles(f -> f.isFile() && f.getName().toLowerCase().startsWith("jlatexmath") && f.getName().toLowerCase().endsWith(".jar"));
            if (c != null && c.length > 0) return c[0];
        }
        return null;
    }

    private final JTextArea input;
    private final JSpinner sizeSpinner;
    private final JLabel previewLabel;
    private final JCheckBox transparentBg;
    private final JCheckBox showLatex;
    private final JPanel latexPanel;
    private final JPanel fieldsPanel;
    private java.util.List<JTextField> currentFields = new java.util.ArrayList<>();
    private int segCounter = 0;
    private BufferedImage currentImage;
    private final boolean hasJlm;
    private ClassLoader jlmLoader;

    private MathFormulaDialog(Frame owner) {
        super(owner, "Insert Math Formula", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        FrostedGlassPanel root = new FrostedGlassPanel(new BorderLayout(10, 10), 16);
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        input = new JTextArea(4, 40);
        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        input.setFont(new Font("Monospaced", Font.PLAIN, 13));
        JScrollPane inputScroll = new JScrollPane(input);

        sizeSpinner = new JSpinner(new SpinnerNumberModel(18, 8, 72, 1));
        transparentBg = new JCheckBox("Transparent background", true);
        showLatex = new JCheckBox("Show LaTeX", false);

        JPanel opts = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        opts.setOpaque(false);
        opts.add(new JLabel("Size:"));
        opts.add(sizeSpinner);
        opts.add(transparentBg);
        opts.add(showLatex);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Common", buildCommonTab());
        tabs.addTab("Relations", buildRelationsTab());
        tabs.addTab("Brackets", buildBracketsTab());
        tabs.addTab("Greek", buildGreekTab());
        tabs.addTab("Operators", buildOperatorsTab());
        tabs.addTab("Calculus", buildCalculusTab());

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.add(tabs, BorderLayout.CENTER);
        topBar.add(opts, BorderLayout.SOUTH);

        hasJlm = detectJlmAvailable();
        if (!hasJlm) {
            JLabel hint = new JLabel("Install JLaTeXMath to render formulas (currently showing plain text)");
            hint.setForeground(new Color(110,110,110));
            hint.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
            topBar.add(hint, BorderLayout.EAST);
        }

        previewLabel = new JLabel();
        previewLabel.setHorizontalAlignment(SwingConstants.CENTER);
        previewLabel.setVerticalAlignment(SwingConstants.CENTER);
        JScrollPane prevScroll = new JScrollPane(previewLabel);
        prevScroll.setPreferredSize(new Dimension(600, 280));

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.setOpaque(false);
        JPanel northStack = new JPanel(new BorderLayout());
        northStack.setOpaque(false);
        northStack.add(topBar, BorderLayout.NORTH);
        fieldsPanel = new JPanel();
        fieldsPanel.setOpaque(false);
        fieldsPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 6));
        fieldsPanel.setBorder(BorderFactory.createTitledBorder("Fields"));
        northStack.add(fieldsPanel, BorderLayout.CENTER);
        center.add(northStack, BorderLayout.NORTH);
        center.add(prevScroll, BorderLayout.CENTER);

        latexPanel = new JPanel(new BorderLayout(6, 6));
        latexPanel.add(new JLabel("LaTeX:"), BorderLayout.NORTH);
        latexPanel.add(inputScroll, BorderLayout.CENTER);
        latexPanel.setVisible(false);
        center.add(latexPanel, BorderLayout.SOUTH);

        root.add(center, BorderLayout.CENTER);

        JButton insertBtn = new JButton("Insert");
        JButton cancelBtn = new JButton("Cancel");
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setOpaque(false);
        bottom.add(cancelBtn);
        bottom.add(insertBtn);
        root.add(bottom, BorderLayout.SOUTH);
        setContentPane(root);

        Runnable refresh = this::renderPreview;
        input.addKeyListener(new KeyAdapter(){
            @Override public void keyReleased(KeyEvent e){ refresh.run(); }
        });
        sizeSpinner.addChangeListener(e -> refresh.run());
        transparentBg.addActionListener(e -> refresh.run());
        showLatex.addActionListener(e -> {
            latexPanel.setVisible(showLatex.isSelected());
            pack();
        });

        cancelBtn.addActionListener(e -> { currentImage = null; dispose(); });
        insertBtn.addActionListener(e -> { renderPreview(); dispose(); });

        // no overlay interactions; all editing happens in the Fields panel

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

    private void insertText(String s) {
        int start;
        int end;
        if (!latexPanel.isVisible()) {
            start = end = input.getDocument().getLength();
        } else {
            start = input.getSelectionStart();
            end = input.getSelectionEnd();
        }
        try {
            input.getDocument().remove(start, end - start);
            input.getDocument().insertString(start, s, null);
            input.setCaretPosition(start + s.length());
        } catch (Exception ignored) {}
        renderPreview();
    }

    private void insertTemplate(java.util.function.Function<String, String> template) {
        String sel = input.getSelectedText();
        if (sel == null) sel = "";
        String ins = template.apply(sel);
        insertText(ins);
    }

    private void insertTemplatedSegment(String key, String[] paramLabels, java.util.function.Function<String, String> builder) {
        String id = newSegmentId();
        String sel = input.getSelectedText(); if (sel == null) sel = "";
        String body = builder.apply(sel);
        String seg = "%[[SEG "+id+"]]" + body + "%[[/SEG "+id+"]]";
        insertText(seg);
        buildFieldsForSegment(id, paramLabels);
        focusFirstField();
    }

    private String newSegmentId(){ return "T" + (System.currentTimeMillis() & 0xffff) + "_" + (segCounter++); }

    private void buildFieldsForSegment(String id, String[] labels){
        fieldsPanel.removeAll();
        currentFields.clear();
        if (labels != null && labels.length > 0) {
            for (int i=0;i<labels.length;i++){
                int idx = i;
                JTextField tf = new JTextField(12);
                tf.putClientProperty("segId", id);
                tf.putClientProperty("slot", idx);
                tf.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){
                    private void upd(){ updateSegmentPlaceholder(id, idx, tf.getText()); }
                    public void insertUpdate(javax.swing.event.DocumentEvent e){ upd(); }
                    public void removeUpdate(javax.swing.event.DocumentEvent e){ upd(); }
                    public void changedUpdate(javax.swing.event.DocumentEvent e){ upd(); }
                });
                JPanel pair = new JPanel(new BorderLayout(4,0));
                pair.setOpaque(false);
                pair.add(new JLabel(labels[i]+":"), BorderLayout.WEST);
                pair.add(tf, BorderLayout.CENTER);
                fieldsPanel.add(pair);
                currentFields.add(tf);
            }
        }
        fieldsPanel.revalidate(); fieldsPanel.repaint();
    }

    private void focusFirstField(){
        if (!currentFields.isEmpty()) {
            SwingUtilities.invokeLater(() -> currentFields.get(0).requestFocusInWindow());
        }
    }

    private void updateSegmentPlaceholder(String id, int slotIndex, String value){
        try {
            String all = input.getText();
            String begin = "%[[SEG "+id+"]]";
            String end = "%[[/SEG "+id+"]]";
            int b = all.indexOf(begin);
            int e = (b>=0) ? all.indexOf(end, b+begin.length()) : -1;
            if (b < 0 || e < 0) return;
            int segStart = b + begin.length();
            String seg = all.substring(segStart, e);
            // Replace the Nth occurrence of \\square
            String needle = "\\\\square"; // literal "\\square"
            int from = 0; int pos=-1;
            for (int i=0;i<=slotIndex;i++){
                pos = seg.indexOf(needle, from);
                if (pos < 0) return; // not enough placeholders
                from = pos + needle.length();
            }
            String replacement = wrapBracesIfNeeded(value==null?"":value);
            String newSeg = seg.substring(0,pos) + replacement + seg.substring(pos+needle.length());
            String updated = all.substring(0, segStart) + newSeg + all.substring(e);
            input.setText(updated);
            renderPreview();
        } catch (Throwable ignored) {}
    }

    private String wrapBracesIfNeeded(String s) {
        if (s.isEmpty()) return "\\square";
        if (s.startsWith("{") && s.endsWith("}")) return s;
        return "{" + s + "}";
    }

    private JPanel buildCommonTab() {
        JPanel p = grid(6, 4);
        addBtn(p, "a/b", () -> insertTemplatedSegment("frac", new String[]{"numerator","denominator"}, sel -> "\\frac{" + (sel.isEmpty()?"\\square":wrapBracesIfNeeded(sel)) + "}{\\square}"));
        addBtn(p, "x^n", () -> insertTemplatedSegment("pow", new String[]{"base","exponent"}, sel -> (sel.isEmpty()?"\\square":"("+sel+")") + "^{\\square}"));
        addBtn(p, "x_n", () -> insertTemplatedSegment("sub", new String[]{"base","subscript"}, sel -> (sel.isEmpty()?"\\square":"("+sel+")") + "_{\\square}"));
        addBtn(p, "√", () -> insertTemplatedSegment("sqrt", new String[]{"radicand"}, sel -> "\\sqrt{" + (sel.isEmpty()?"\\square":wrapBracesIfNeeded(sel)) + "}"));
        addBtn(p, "ⁿ√", () -> insertTemplatedSegment("nroot", new String[]{"index","radicand"}, sel -> "\\sqrt[\\square]{" + (sel.isEmpty()?"\\square":wrapBracesIfNeeded(sel)) + "}"));
        addBtn(p, "()", () -> insertTemplate(sel -> "\\left(" + (sel.isEmpty()?"\\square":sel) + "\\right)"));
        addBtn(p, "[]", () -> insertTemplate(sel -> "\\left[" + (sel.isEmpty()?"\\square":sel) + "\\right]"));
        addBtn(p, "{}", () -> insertTemplate(sel -> "\\left{" + (sel.isEmpty()?"\\square":sel) + "\\right}"));
        addSym(p, "∞", "\\infty");
        addSym(p, "π", "\\pi");
        addSym(p, "θ", "\\theta");
        addSym(p, "λ", "\\lambda");
        addSym(p, "·", "\\cdot");
        addSym(p, "±", "\\pm");
        addSym(p, "×", "\\times");
        addSym(p, "÷", "\\div");
        return p;
    }

    private JPanel buildRelationsTab() {
        JPanel p = grid(6, 3);
        addSym(p, "=", "=");
        addSym(p, "≠", "\\neq");
        addSym(p, "<", "<");
        addSym(p, ">", ">");
        addSym(p, "≤", "\\leq");
        addSym(p, "≥", "\\geq");
        addSym(p, "≈", "\\approx");
        addSym(p, "∝", "\\propto");
        addSym(p, "→", "\\to");
        addSym(p, "⇒", "\\Rightarrow");
        return p;
    }

    private JPanel buildBracketsTab() {
        JPanel p = grid(6, 3);
        addBtn(p, "( )", () -> insertTemplate(sel -> "\\left(" + (sel.isEmpty()?"\\square":sel) + "\\right)"));
        addBtn(p, "[ ]", () -> insertTemplate(sel -> "\\left[" + (sel.isEmpty()?"\\square":sel) + "\\right]"));
        addBtn(p, "{ }", () -> insertTemplate(sel -> "\\left{" + (sel.isEmpty()?"\\square":sel) + "\\right}"));
        addBtn(p, "⟨ ⟩", () -> insertTemplate(sel -> "\\left\\langle" + (sel.isEmpty()?"\\square":sel) + "\\right\\rangle"));
        addBtn(p, "| |", () -> insertTemplate(sel -> "\\left|" + (sel.isEmpty()?"\\square":sel) + "\\right|"));
        return p;
    }

    private JPanel buildGreekTab() {
        JPanel p = grid(8, 3);
        addSym(p, "α", "\\alpha");
        addSym(p, "β", "\\beta");
        addSym(p, "γ", "\\gamma");
        addSym(p, "δ", "\\delta");
        addSym(p, "ε", "\\epsilon");
        addSym(p, "μ", "\\mu");
        addSym(p, "σ", "\\sigma");
        addSym(p, "ω", "\\omega");
        addSym(p, "Δ", "\\Delta");
        addSym(p, "Σ", "\\Sigma");
        addSym(p, "Ω", "\\Omega");
        addSym(p, "Ψ", "\\Psi");
        return p;
    }

    private JPanel buildOperatorsTab() {
        JPanel p = grid(8, 3);
        addSym(p, "+", "+");
        addSym(p, "−", "-");
        addSym(p, "×", "\\times");
        addSym(p, "÷", "\\div");
        addSym(p, "·", "\\cdot");
        addSym(p, "∓", "\\mp");
        addSym(p, "∪", "\\cup");
        addSym(p, "∩", "\\cap");
        addSym(p, "⊂", "\\subset");
        addSym(p, "⊃", "\\supset");
        addSym(p, "∈", "\\in");
        addSym(p, "∉", "\\notin");
        return p;
    }

    private JPanel buildCalculusTab() {
        JPanel p = grid(6, 3);
        addBtn(p, "∫", () -> insertTemplatedSegment("int", new String[]{"integrand"}, sel -> "\\int " + (sel.isEmpty()?"\\square":sel) + " \\mathrm{d}x"));
        addBtn(p, "∫ab", () -> insertTemplatedSegment("int2", new String[]{"lower","upper","integrand"}, sel -> "\\int_{\\square}^{\\square} " + (sel.isEmpty()?"\\square":sel) + " \\mathrm{d}x"));
        addBtn(p, "∑", () -> insertTemplate(sel -> "\\sum " + (sel.isEmpty()?"\\square":sel)));
        addBtn(p, "∑ i=a..b", () -> insertTemplate(sel -> "\\sum_{i=\\square}^{\\square} " + (sel.isEmpty()?"\\square":sel)));
        addBtn(p, "∏", () -> insertTemplate(sel -> "\\prod " + (sel.isEmpty()?"\\square":sel)));
        addBtn(p, "d/dx", () -> insertTemplatedSegment("ddx", new String[]{"expression"}, sel -> "\\frac{d}{dx} " + (sel.isEmpty()?"\\square":wrapBracesIfNeeded(sel))));
        return p;
    }

    private JPanel grid(int cols, int rows) {
        JPanel p = new JPanel(new GridLayout(rows, cols, 6, 6));
        p.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        return p;
    }

    private void addSym(JPanel p, String label, String latex) {
        JButton b = new JButton(label);
        b.setMargin(new Insets(2, 8, 2, 8));
        b.setFont(b.getFont().deriveFont(Font.PLAIN, 16f));
        b.addActionListener(e -> insertText(latex));
        p.add(b);
    }

    private void addBtn(JPanel p, String label, Runnable onClick) {
        JButton b = new JButton(label);
        b.setMargin(new Insets(2, 8, 2, 8));
        b.setFont(b.getFont().deriveFont(Font.PLAIN, 14f));
        b.addActionListener(e -> onClick.run());
        p.add(b);
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
        String rendered = stripSegmentMarkers(latex);
        BufferedImage img = renderLatexToImage(rendered, size, transparent);
        currentImage = img;
        if (img != null) {
            previewLabel.setIcon(new ImageIcon(img));
        } else {
            previewLabel.setIcon(null);
        }
    }

    private String stripSegmentMarkers(String latex){
        // Remove both open and close markers anywhere in the string
        return latex.replaceAll("(?m)%\\[\\[(?:/)?SEG [^]]*\\]\\]", "");
    }

    private BufferedImage renderLatexToImage(String latex, int size, boolean transparent) {
        try {
            Class<?> texFormulaClz;
            Class<?> texConstantsClz;
            try {
                texFormulaClz = Class.forName("org.scilab.forge.jlatexmath.TeXFormula");
                texConstantsClz = Class.forName("org.scilab.forge.jlatexmath.TeXConstants");
            } catch (Throwable missing) {
                ensureJlmLoader();
                if (jlmLoader == null) throw missing;
                texFormulaClz = Class.forName("org.scilab.forge.jlatexmath.TeXFormula", true, jlmLoader);
                texConstantsClz = Class.forName("org.scilab.forge.jlatexmath.TeXConstants", true, jlmLoader);
            }
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
            icon.getClass().getMethod("paintIcon", Component.class, Graphics.class, int.class, int.class)
                    .invoke(icon, null, g2, 0, 0);
            g2.dispose();
            return img;
        } catch (Throwable t) {
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
