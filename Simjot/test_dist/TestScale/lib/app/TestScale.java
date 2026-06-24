import java.awt.GraphicsEnvironment;

public class TestScale {
    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale", "2.0");
        double scale = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getDefaultTransform().getScaleX();
        System.out.println("Scale is: " + scale);
    }
}
