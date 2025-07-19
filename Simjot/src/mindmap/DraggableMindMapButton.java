package mindmap;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;

public class DraggableMindMapButton extends JButton {
    private final MindMap map;
    private final File mapFile;
    private Point initialClick;
    private final MindMapPanel parent;

    public DraggableMindMapButton(MindMap map, File mapFile, MindMapPanel parent) {
        super(map.getTitle());
        this.map = map;
        this.mapFile = mapFile;
        this.parent = parent;

        setBackground(Color.LIGHT_GRAY);
        setForeground(Color.DARK_GRAY);
        setOpaque(true);
        setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
        setPreferredSize(new Dimension(150, 50));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                initialClick = e.getPoint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    JPopupMenu popup = new JPopupMenu();
                    JMenuItem colorItem = new JMenuItem("Color");
                    colorItem.addActionListener(ev -> {
                        Color chosen = JColorChooser.showDialog(
                            DraggableMindMapButton.this, 
                            "Choose Color", 
                            getBackground()
                        );
                        if (chosen != null) {
                            setBackground(chosen);
                        }
                    });
                    popup.add(colorItem);
                    popup.show(DraggableMindMapButton.this, e.getX(), e.getY());
                } else if (e.getClickCount() == 2) {
                    // Double-click to open editing
                    parent.showEditingPanel(map);
                }
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                int thisX = getLocation().x;
                int thisY = getLocation().y;
                int xMoved = e.getX() - initialClick.x;
                int yMoved = e.getY() - initialClick.y;
                setLocation(thisX + xMoved, thisY + yMoved);
                getParent().revalidate();
                getParent().repaint();
            }
        });
    }
}
