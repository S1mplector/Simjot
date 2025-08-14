package main.ui.components.checkbox;

import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.basic.BasicCheckBoxUI;

public class ModernCheckBoxUI extends BasicCheckBoxUI {

    private static final Color CHECK_COLOR = new Color(0, 120, 215);
    private static final Color BOX_COLOR = new Color(220, 220, 220);
    private static final Color TEXT_COLOR = Color.DARK_GRAY;
    private static final Icon DUMMY_ICON = new Icon() {
        @Override public void paintIcon(Component c, Graphics g, int x, int y) { /* No-op */ }
        @Override public int getIconWidth() { return 18; } // Reserve space for the box
        @Override public int getIconHeight() { return 18; }
    };
    
    @Override
    public void installUI(JComponent c) {
        super.installUI(c);
        // Ensure layout and preferred size reserve space for the checkbox box
        if (c instanceof AbstractButton) {
            AbstractButton b = (AbstractButton) c;
            if (b.getIcon() == null) {
                b.setIcon(DUMMY_ICON);
            }
            if (b.getIconTextGap() < 6) {
                b.setIconTextGap(6);
            }
            b.setFocusPainted(false);
            // Avoid background fill artifacts; let parent paint background
            b.setOpaque(false);
        }
    }

    @Override
    public synchronized void paint(Graphics g, JComponent c) {
        AbstractButton b = (AbstractButton) c;
        ButtonModel model = b.getModel();
        Font f = c.getFont();
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();

        Rectangle viewRect = new Rectangle();
        Rectangle iconRect = new Rectangle();
        Rectangle textRect = new Rectangle();

        Insets i = c.getInsets();
        viewRect.x = i.left;
        viewRect.y = i.top;
        viewRect.width = b.getWidth() - (i.left + i.right);
        viewRect.height = b.getHeight() - (i.top + i.bottom);

        String text = SwingUtilities.layoutCompoundLabel(c,
            fm, b.getText(), DUMMY_ICON, // Use dummy icon for layout
            b.getVerticalAlignment(), b.getHorizontalAlignment(),
            b.getVerticalTextPosition(), b.getHorizontalTextPosition(),
            viewRect, iconRect, textRect, b.getIconTextGap());

        // Paint the check box
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setClip(0, 0, c.getWidth(), c.getHeight());
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Clear background to avoid artifacts when component/background is translucent
        Color bg = (c.getParent() != null ? c.getParent().getBackground() : c.getBackground());
        g2.setComposite(AlphaComposite.SrcOver);
        g2.setColor(bg);
        g2.fillRect(0, 0, c.getWidth(), c.getHeight());

        int boxSize = 16;
        int x = iconRect.x + (iconRect.width - boxSize) / 2;
        int y = iconRect.y + (iconRect.height - boxSize) / 2;

        g2.setColor(model.isSelected() ? CHECK_COLOR : BOX_COLOR);
        g2.fillRoundRect(x, y, boxSize, boxSize, 5, 5);

        if (model.isSelected()) {
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine(x + 4, y + 8, x + 7, y + 11);
            g2.drawLine(x + 7, y + 11, x + 12, y + 6);
        }

        // Paint the text
        if (text != null) {
            g2.setColor(TEXT_COLOR);
            g2.drawString(text, textRect.x, textRect.y + fm.getAscent());
        }
        g2.dispose();
    }
}
