package main.ui.components.fields;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

/**
 * Minimal text field that is meant to look like a refined divider, transparent fill with a thin underline
 * and optional placeholder, suitable for toolbar title inputs on frosted backgrounds.
 * It is a child class of JTextField, so it can be used as a JTextField. 
 * I initially thought of using a JComponent, but I decided to use a JTextField, because it is
 * more flexible and can be used as an input field as well.
 * 
 * The rendering is kept lightweight using simple Swing operations like Graphics2D and GradientPaint.
 * 
 * This component will be used in the entry and poetry toolbars. 
 * 
 * Why does everyone hate on Java Swing, like come on, it's pretty awesome.
 * Just my opinion anyway. 
 * 
 * @author S1mplector
 */
public class TitleDividerField extends JTextField {
    private String placeholder;

    public TitleDividerField(int columns) {
        super(columns);
        setOpaque(false);
        setBorder(new EmptyBorder(6, 0, 6, 0));
        setForeground(new Color(40, 40, 40));
        setCaretColor(new Color(50, 50, 50));
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
        repaint();
    }

    public String getPlaceholder() {
        return placeholder;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        super.paintComponent(g2);

        // Placeholder
        if ((getText() == null || getText().isEmpty()) && !isFocusOwner() && placeholder != null) {
            Font font = getFont();
            g2.setFont(font);
            g2.setColor(new Color(120, 120, 120));
            FontMetrics fm = g2.getFontMetrics();
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(placeholder, 2, y);
        }
        g2.dispose();
    }

    @Override
    protected void paintBorder(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int y = getHeight() - 2;
        g2.setColor(new Color(60, 60, 60, 150));
        g2.drawLine(0, y, getWidth(), y);
        g2.setColor(new Color(255, 255, 255, 130));
        g2.drawLine(0, y - 1, getWidth(), y - 1);
        g2.dispose();
    }
}
