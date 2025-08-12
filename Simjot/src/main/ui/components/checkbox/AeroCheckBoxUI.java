package main.ui.components.checkbox;

import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.basic.BasicCheckBoxUI;

public class AeroCheckBoxUI extends BasicCheckBoxUI {
    private static final Color BOX_BORDER = new Color(160, 160, 160);
    private static final Color BOX_FILL = new Color(245, 245, 245);
    private static final Color BOX_FILL_HOVER = new Color(234, 242, 252);
    private static final Color CHECK_BLUE = new Color(0, 120, 215);

    private static final Icon DUMMY_ICON = new Icon() {
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {}
        @Override public int getIconWidth() { return 18; }
        @Override public int getIconHeight() { return 18; }
    };

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
                fm, b.getText(), DUMMY_ICON,
                b.getVerticalAlignment(), b.getHorizontalAlignment(),
                b.getVerticalTextPosition(), b.getHorizontalTextPosition(),
                viewRect, iconRect, textRect, b.getIconTextGap());

        // Paint box
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int box = 16;
        int x = iconRect.x + (iconRect.width - box) / 2;
        int y = iconRect.y + (iconRect.height - box) / 2;
        g2.setColor(model.isRollover() ? BOX_FILL_HOVER : BOX_FILL);
        g2.fillRoundRect(x, y, box, box, 4, 4);
        g2.setColor(BOX_BORDER);
        g2.drawRoundRect(x, y, box, box, 4, 4);

        if (model.isSelected()) {
            g2.setColor(CHECK_BLUE);
            g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(x + 4, y + 8, x + 7, y + 11);
            g2.drawLine(x + 7, y + 11, x + 12, y + 6);
        }

        // Paint text
        if (text != null) {
            g2.setColor(b.isEnabled() ? new Color(32,32,32) : new Color(120,120,120));
            g2.drawString(text, textRect.x, textRect.y + fm.getAscent());
        }
        g2.dispose();
    }
}
