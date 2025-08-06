package main.ui.panels;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.Calendar;
import javax.swing.*;

public class AnalogClockPanel extends JPanel {
    private static final long serialVersionUID = 1L;

	@SuppressWarnings("unused")
	public AnalogClockPanel() {
        setPreferredSize(new Dimension(100, 100));
        setOpaque(false);
        Timer timer = new Timer(1000, e -> repaint());
        timer.start();
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int size = Math.min(getWidth(), getHeight());
        int centerX = getWidth() / 2;
        int centerY = getHeight() / 2;
        int radius = Math.max(1, size / 2 - 6); // Ensure radius is at least 1
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Draw translucent glossy face
        Point2D center = new Point2D.Float(centerX, centerY);
        float[] dist = {0f, 1f};
        Color[] colors = {new Color(255,255,255,180), new Color(200,200,200,120)};
        RadialGradientPaint rg = new RadialGradientPaint(center, radius, dist, colors);
        g2.setPaint(rg);
        g2.fillOval(centerX - radius, centerY - radius, 2 * radius, 2 * radius);
        g2.setColor(new Color(60,60,60,140));
        g2.setStroke(new BasicStroke(2f));
        g2.drawOval(centerX - radius, centerY - radius, 2 * radius, 2 * radius);
        
        // Tick marks
        g2.setStroke(new BasicStroke(2f));
        for(int i=0;i<60;i++){
            double angle = Math.toRadians(i*6 - 90);
            int inner = (i%5==0)? radius-12 : radius-6;
            int outer = radius-2;
            int x1 = centerX + (int)(inner * Math.cos(angle));
            int y1 = centerY + (int)(inner * Math.sin(angle));
            int x2 = centerX + (int)(outer * Math.cos(angle));
            int y2 = centerY + (int)(outer * Math.sin(angle));
            g2.drawLine(x1,y1,x2,y2);
        }
        
        Calendar cal = Calendar.getInstance();
        int hours = cal.get(Calendar.HOUR);
        int minutes = cal.get(Calendar.MINUTE);
        int seconds = cal.get(Calendar.SECOND);
        
        double hourAngle = Math.toRadians((hours + minutes / 60.0) * 30 - 90);
        int hourLength = radius * 50 / 100;
        g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(centerX, centerY, centerX + (int)(hourLength * Math.cos(hourAngle)),
                    centerY + (int)(hourLength * Math.sin(hourAngle)));
        
        double minuteAngle = Math.toRadians((minutes + seconds / 60.0) * 6 - 90);
        int minuteLength = radius * 70 / 100;
        g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(centerX, centerY, centerX + (int)(minuteLength * Math.cos(minuteAngle)),
                    centerY + (int)(minuteLength * Math.sin(minuteAngle)));
        
        double secondAngle = Math.toRadians(seconds * 6 - 90);
        int secondLength = radius * 80 / 100;
        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(Color.RED);
        g2.drawLine(centerX, centerY, centerX + (int)(secondLength * Math.cos(secondAngle)),
                    centerY + (int)(secondLength * Math.sin(secondAngle)));
        
        g2.dispose();
    }
}
