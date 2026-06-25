import javax.swing.*;
import java.awt.*;

public class TestTrans {
    public static void main(String[] args) {
        JDialog d = new JDialog();
        d.setUndecorated(true);
        d.setBackground(new Color(0,0,0,0));
        JPanel p = new JPanel() {
            protected void paintComponent(Graphics g) {
                g.setColor(Color.RED);
                g.fillRoundRect(50, 50, 200, 200, 30, 30);
            }
        };
        p.setOpaque(false);
        d.add(p);
        d.setSize(300, 300);
        d.setLocationRelativeTo(null);
        d.setVisible(true);
        
        try { Thread.sleep(2000); } catch (Exception e) {}
        System.exit(0);
    }
}
