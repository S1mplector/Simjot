package main.ui.buttons;

import java.awt.*;
import javax.swing.*;

public class RoundedButton extends JButton {
    public RoundedButton(String text){
        super(text);
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setForeground(Color.DARK_GRAY);
        setFont(new Font("SansSerif", Font.BOLD, 12));
        setPreferredSize(new Dimension(140,32));
    }

    @Override
    protected void paintComponent(Graphics g){
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if(getModel().isPressed())       g2.setColor(new Color(200,200,200));
        else if(getModel().isRollover()) g2.setColor(new Color(220,220,220));
        else                             g2.setColor(new Color(240,240,240));
        g2.fillRoundRect(0,0,getWidth(),getHeight(),10,10);
        g2.setColor(Color.LIGHT_GRAY);
        g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,10,10);

        g2.setColor(getForeground());
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(getText(), (getWidth()-fm.stringWidth(getText()))/2, ((getHeight()-fm.getHeight())/2)+fm.getAscent());
        g2.dispose();
    }
} 