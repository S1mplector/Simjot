package main.ui.features.drawing;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import javax.swing.*;
import main.ui.components.buttons.RoundedButton;

/**
 * Modal dialog that lists all .mydraw files inside the supplied directory
 * with their generated PNG thumbnails (or filename fallback) so the user
 * can pick one to load into the DrawingPanel.
 */
public class DrawingChooserDialog extends JDialog {
    private final DefaultListModel<File> model = new DefaultListModel<>();
    private final JList<File> list = new JList<>(model);
    private int hoverIndex = -1;
    private File selected;

    private DrawingChooserDialog(Window owner, File drawingsDir){
        super(owner, "Open Drawing", ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout());
        setSize(520, 380);
        setLocationRelativeTo(owner);

        // Thumbnail list setup
        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        list.setVisibleRowCount(-1);
        list.setFixedCellWidth(140);
        list.setFixedCellHeight(140);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(Color.WHITE);
        list.setCellRenderer((JList<? extends File> l, File value, int idx, boolean sel, boolean focus)->{
            JLabel lab = new JLabel(){ @Override public Dimension getPreferredSize(){ return new Dimension(140,140);} };
            lab.setOpaque(true);
            Color base = sel?new Color(0,120,215,60): (idx==hoverIndex?new Color(220,220,220):Color.WHITE);
            lab.setBackground(base);
            lab.setHorizontalAlignment(SwingConstants.CENTER);
            lab.setVerticalAlignment(SwingConstants.CENTER);
            lab.setForeground(Color.DARK_GRAY);
            String pngPath = value.getAbsolutePath().replace(".mydraw", ".png");
            File png = new File(pngPath);
            if(png.exists()){
                ImageIcon icon = new ImageIcon(new ImageIcon(pngPath).getImage().getScaledInstance(120,120,Image.SCALE_SMOOTH));
                lab.setIcon(icon);
            } else {
                lab.setText("<html><body style='width:110px;text-align:center;'>"+value.getName()+"</body></html>");
            }
            // tooltip date
            SimpleDateFormat df = new SimpleDateFormat("dd MMM yyyy HH:mm");
            lab.setToolTipText(df.format(value.lastModified()));
            return lab;
        });
        JScrollPane sc = new JScrollPane(list);
        sc.setOpaque(true);
        sc.setBackground(Color.WHITE);
        sc.getViewport().setOpaque(true);
        sc.getViewport().setBackground(Color.WHITE);
        add(sc, BorderLayout.CENTER);

        // Info label and buttons
        JPanel south = new JPanel(new BorderLayout());
        JLabel info = new JLabel(" ");
        info.setBorder(BorderFactory.createEmptyBorder(2,8,2,8));
        south.add(info, BorderLayout.CENTER);
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        RoundedButton del = new RoundedButton("Delete");
        RoundedButton open = new RoundedButton("Open");
        RoundedButton cancel = new RoundedButton("Cancel");
        btns.add(del); btns.add(open); btns.add(cancel);
        south.add(btns, BorderLayout.EAST);
        add(south, BorderLayout.SOUTH);

        open.addActionListener(e->{
            selected = list.getSelectedValue();
            if(selected!=null) dispose();
        });
        cancel.addActionListener(e->{ selected=null; dispose(); });

        del.addActionListener(e->{
            File f=list.getSelectedValue();
            if(f==null) return;
            boolean ok = main.ui.dialog.confirmation.CustomConfirmDialog.confirm(this, "Delete Drawing", "Delete '"+f.getName()+"'?" );
            if(ok){
                File thumb = new File(f.getAbsolutePath().replace(".mydraw",".png"));
                f.delete(); if(thumb.exists()) thumb.delete();
                model.removeElement(f);
            }
        });

        // hover + double-click support
        list.addMouseMotionListener(new MouseAdapter(){
            @Override public void mouseMoved(MouseEvent e){
                int idx=list.locationToIndex(e.getPoint());
                if(idx!=hoverIndex){ hoverIndex=idx; list.repaint(); }
            }
        });
        list.addMouseListener(new MouseAdapter(){
            @Override public void mouseExited(MouseEvent e){ hoverIndex=-1; list.repaint(); }
            @Override public void mouseClicked(MouseEvent e){ if(e.getClickCount()==2){ open.doClick(); }}
        });

        list.addListSelectionListener(e->{
            File f=list.getSelectedValue();
            if(f==null){ info.setText(" "); return; }
            SimpleDateFormat df=new SimpleDateFormat("dd MMM yyyy HH:mm");
            info.setText("Added: "+df.format(f.lastModified()));
        });

        // populate list
        if(drawingsDir.exists()){
            File[] files = drawingsDir.listFiles((d,f)->f.toLowerCase().endsWith(".mydraw"));
            if(files!=null){
                Arrays.stream(files)
                        .sorted((a,b)->Long.compare(b.lastModified(), a.lastModified()))
                        .forEach(model::addElement);
            }
        }
    }

    /**
     * Shows the chooser and returns the selected file or null.
     */
    public static File chooseDrawing(Component parent, File drawingsDir){
        Window owner = parent instanceof Window ? (Window) parent : SwingUtilities.getWindowAncestor(parent);
        DrawingChooserDialog dlg = new DrawingChooserDialog(owner, drawingsDir);
        dlg.setVisible(true);
        return dlg.selected;
    }
} 