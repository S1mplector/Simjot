package main.ui.panels;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.swing.*;
import main.dialog.CustomConfirmDialog;
import main.transitions.FadingButton;
import main.ui.JournalApp;
import main.ui.buttons.RoundedButton;
import main.ui.buttons.ToolbarIconButton;
import main.ui.components.ModernComboBoxUI;
import main.util.NotebookInfo;
import main.util.NotebookStore;

public class NotebookManagerPanel extends JPanel {
    private final NotebookStore store = new NotebookStore();
    private final JPanel gallery = new JPanel(new FlowLayout(FlowLayout.LEFT,20,20));
    private final JournalApp app;
    private ToolbarIconButton deleteBtn;
    private NotebookTile selectedTile;

    public NotebookManagerPanel(JournalApp app){
        this.app = app;
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        // --- Top toolbar matching other panels ---
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topBar.setBackground(new Color(230,230,230));
        RoundedButton backBtn = new RoundedButton("Back");
        backBtn.addActionListener(e-> app.switchCard(JournalApp.MAIN_MENU));
        topBar.add(backBtn);

        deleteBtn = new ToolbarIconButton("trash");
        deleteBtn.setEnabled(false);
        deleteBtn.addActionListener(e-> deleteSelectedNotebook());
        topBar.add(deleteBtn);

        add(topBar, BorderLayout.NORTH);

        gallery.setOpaque(false);
        JScrollPane scroll = new JScrollPane(gallery);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll,BorderLayout.CENTER);

        selectedTile = null;
        deleteBtn.setEnabled(false);

        refresh();
    }

    public void refresh(){
        store.reload();
        gallery.removeAll();
        java.util.List<NotebookInfo> list = store.list();
        for(NotebookInfo nb: list){
            gallery.add(createTile(nb));
        }
        gallery.add(createAddTile()); // always present at the end
        revalidate(); repaint();
    }

    private JPanel createTile(NotebookInfo nb){
        NotebookTile tile = new NotebookTile(nb);
        tile.setPreferredSize(new Dimension(120,120));
        tile.addMouseListener(new MouseAdapter(){
            @Override public void mouseClicked(MouseEvent e){ if(e.getClickCount()==2) openNotebook(nb); }
        });
        return tile;
    }

    private static BufferedImage createIcon(NotebookInfo nb){
        NotebookInfo.Type type = nb.getType();
        String iconId = nb.getIconId();
        final int S = 100;
        BufferedImage img = new BufferedImage(S,S,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);

        // Smooth background with subtle gradient
        Color base = switch(type){
            case JOURNAL -> new Color(0xFFC46B);
            case POETRY  -> new Color(0xC48BDF);
        };
        Color lighter = base.brighter();
        g.setPaint(new GradientPaint(0,0,lighter,0,S,base));
        g.fillRoundRect(0,0,S,S,20,20);

        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(Color.WHITE);

        switch(type){
            case JOURNAL -> {
                int margin = 18;
                // margin line
                g.drawLine(margin+8, margin, margin+8, S-margin);
                // horizontal rules
                for(int y=margin+12; y<S-margin; y+=12){ g.drawLine(margin, y, S-margin, y); }
            }
            case POETRY -> {
                // stylised quill
                int cx = S/2; int cy = S/2;
                Path2D quill = new Path2D.Double();
                quill.moveTo(cx+20, cy-20);
                quill.curveTo(cx-5, cy-35, cx-35, cy+5, cx-10, cy+25);
                quill.curveTo(cx, cy+35, cx+15, cy+10, cx+20, cy-20);
                g.draw(quill);
                // shaft
                g.drawLine(cx-5, cy+15, cx+25, cy-25);
            }
        }
        if(!"legacy".equals(iconId)){
            // override gradient by iconId themed colours
            Color base2 = switch(iconId){
                case "lightbulb" -> new Color(0xFFE27C);
                case "rocket"    -> new Color(0xFF8A65);
                case "camera"    -> new Color(0x90CAF9);
                case "music"     -> new Color(0xCE93D8);
                case "code"      -> new Color(0xA5D6A7);
                default           -> new Color(0xB0BEC5);
            };
            Color lighter2 = base2.brighter();
            g.setPaint(new GradientPaint(0,0,lighter2,0,S,base2));
            g.fillRoundRect(0,0,S,S,20,20);

            g.setStroke(new BasicStroke(4f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            g.setColor(Color.WHITE);
            int cx=S/2, cy=S/2;
            switch(iconId){
                case "lightbulb" -> {
                    g.drawOval(cx-18,cy-18,36,36);
                    g.drawLine(cx-8,cy+18,cx+8,cy+18);
                }
                case "rocket" -> {
                    g.drawLine(cx,cy-20,cx,cy+10);
                    g.drawLine(cx,cy-20,cx-10,cy-5);
                    g.drawLine(cx,cy-20,cx+10,cy-5);
                }
                case "camera" -> {
                    g.drawRoundRect(cx-20,cy-12,40,24,6,6);
                    g.drawOval(cx-8,cy-4,16,16);
                }
                case "music" -> {
                    g.drawLine(cx-8,cy-15,cx-8,cy+15);
                    g.drawLine(cx-8,cy-15,cx+12,cy-20);
                    g.drawLine(cx+12,cy-20,cx+12,cy+10);
                    g.drawOval(cx-12,cy+12,8,8);
                    g.drawOval(cx+6,cy+12,8,8);
                }
                case "code" -> {
                    g.drawLine(cx-12,cy-10,cx-22,cy);
                    g.drawLine(cx-22,cy,cx-12,cy+10);
                    g.drawLine(cx+12,cy-10,cx+22,cy);
                    g.drawLine(cx+22,cy,cx+12,cy+10);
                }
            }
            g.dispose();
            return img;
        }
        g.dispose();
        return img;
    }

    private static int countEntries(NotebookInfo nb){
        if(nb==null) return 0;
        File folder = nb.getFolder();
        if(folder==null || !folder.exists()) return 0;
        File[] files = folder.listFiles((d,name)->{
            String s=name.toLowerCase();
            return s.endsWith(".txt")||s.endsWith(".md")||s.endsWith(".rtf")||s.endsWith(".note")||s.endsWith(".poem");
        });
        return files==null?0:files.length;
    }

    private class NotebookTile extends JPanel implements MouseListener{
        private final NotebookInfo nb;
        NotebookTile(NotebookInfo nb){
            this.nb = nb;
            setLayout(new BorderLayout());
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(2,2,2,2));
            addMouseListener(this);

            JLabel icon = new JLabel(new ImageIcon(createIcon(nb)));
            icon.setHorizontalAlignment(SwingConstants.CENTER);
            // Forward hover and click events to the tile
            MouseAdapter forward = new MouseAdapter(){
                @Override public void mouseEntered(MouseEvent e){ NotebookTile.this.mouseEntered(e); }
                @Override public void mouseExited(MouseEvent e){ NotebookTile.this.mouseExited(e); }
                @Override public void mouseClicked(MouseEvent e){ NotebookTile.this.mouseClicked(e); }
            };
            icon.addMouseListener(forward);
            add(icon, BorderLayout.CENTER);

            // name label under tile
            JLabel nameLbl = new JLabel(nb.getName(),SwingConstants.CENTER);
            nameLbl.setForeground(Color.DARK_GRAY);
            NotebookTile.this.add(nameLbl, BorderLayout.SOUTH);
        }
        private boolean hover=false;
        private boolean selected=false;
        void setSelected(boolean s){ this.selected=s; repaint(); }
        @Override protected void paintComponent(Graphics g){
            super.paintComponent(g);
            if(hover || selected){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(255,255,255,80));
                g2.fillRoundRect(0,0,getWidth()-1,getHeight()-1,16,16);
                g2.setColor(new Color(0,0,0,60));
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1,1,getWidth()-3,getHeight()-3,16,16);
                g2.dispose();
            }
        }
        @Override public void mouseEntered(MouseEvent e){ hover=true; repaint(); }
        @Override public void mouseExited(MouseEvent e){ hover=false; repaint(); }
        @Override public void mouseClicked(MouseEvent e){
            if(SwingUtilities.isLeftMouseButton(e)){
                selectTile(this);
                if(e.getClickCount()==2){ openNotebook(nb); }
            }
        }
        @Override public void mousePressed(MouseEvent e){}
        @Override public void mouseReleased(MouseEvent e){}
    }

    private void promptNew(){
        CreateNotebookDialog dlg = new CreateNotebookDialog((Frame) SwingUtilities.getWindowAncestor(this));
        dlg.setVisible(true);
        if(dlg.isAccepted()){
            String name = dlg.getNotebookName();
            NotebookInfo.Type type = dlg.getNotebookType();
            String icon = dlg.getSelectedIcon();
            if(name!=null && !name.isEmpty()){
                NotebookInfo nb = store.create(name,type,icon);
                animateNewNotebook(nb);
            }
        }
    }

    private void openNotebook(NotebookInfo nb){
        app.openNotebookEntries(nb);
    }

    private void openOptions(NotebookInfo nb){
        NotebookOptionsDialog dlg = new NotebookOptionsDialog((Frame) SwingUtilities.getWindowAncestor(this), nb);
        dlg.setVisible(true);
        if(dlg.isDeleted()){
            store.delete(nb);
            refresh();
        } else if(dlg.isRenamed()){
            store.rename(nb, dlg.getNewName());
            refresh();
        }
    }

    /* ----------------- New Notebook Dialog ----------------- */
    private static class CreateNotebookDialog extends JDialog{
        private boolean accepted=false;
        private ModernTextField nameField=new ModernTextField(15);
        private JComboBox<NotebookInfo.Type> typeBox=new JComboBox<>(NotebookInfo.Type.values());
        private String selectedIcon="legacy";

        CreateNotebookDialog(Frame parent){
            super(parent,"New Notebook",true);
            setUndecorated(true);
            setBackground(new Color(0,0,0,0));
            setLayout(new BorderLayout());

            RoundedPanel panel = new RoundedPanel();
            panel.setArc(20);
            panel.setBackground(new Color(255,255,255,240));
            panel.setLayout(new BorderLayout(10,10));
            panel.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));

            JPanel fields = new JPanel(new GridLayout(0,1,5,5));
            fields.setOpaque(false);
            JLabel nlab=new JLabel("Name: "); nlab.setForeground(Color.DARK_GRAY);
            fields.add(nlab); fields.add(nameField);

            // styled combo
            typeBox.setUI(new ModernComboBoxUI());
            typeBox.setRenderer(new ModernComboBoxUI.ModernComboBoxRenderer());
            typeBox.setFont(new Font("SansSerif", Font.PLAIN, 14));
            typeBox.setForeground(Color.DARK_GRAY);

            JLabel tlab=new JLabel("Type: "); tlab.setForeground(Color.DARK_GRAY);
            fields.add(tlab); fields.add(typeBox);

            // Icon selector with arrows
            String[] icons={"legacy","lightbulb","rocket","camera","music","code"};
            selectedIcon = icons[0];

            JPanel iconSelector = new JPanel(); iconSelector.setOpaque(false); iconSelector.setLayout(new BoxLayout(iconSelector, BoxLayout.X_AXIS));
            iconSelector.add(Box.createHorizontalGlue());
            
            // arrows and preview will be added later

            JLabel iconPreview = new JLabel(); iconPreview.setHorizontalAlignment(SwingConstants.CENTER);
            iconPreview.setPreferredSize(new Dimension(80,80));

            java.util.function.Consumer<String> updatePreview = id->{
                NotebookInfo tmp=new NotebookInfo("t", NotebookInfo.Type.JOURNAL,new java.io.File("."),0,id);
                Image img=createIcon(tmp).getScaledInstance(70,70,Image.SCALE_SMOOTH);
                iconPreview.setIcon(new ImageIcon(img));
            };
            updatePreview.accept(selectedIcon);

            JButton leftBtn = new ArrowButton(false);
            JButton rightBtn = new ArrowButton(true);

            leftBtn.addActionListener(e->{
                int idx=java.util.Arrays.asList(icons).indexOf(selectedIcon);
                idx=(idx-1+icons.length)%icons.length;
                selectedIcon=icons[idx]; updatePreview.accept(selectedIcon);
            });
            rightBtn.addActionListener(e->{
                int idx=java.util.Arrays.asList(icons).indexOf(selectedIcon);
                idx=(idx+1)%icons.length;
                selectedIcon=icons[idx]; updatePreview.accept(selectedIcon);
            });

            iconSelector.add(leftBtn);
            iconSelector.add(Box.createHorizontalStrut(12));
            iconSelector.add(iconPreview);
            iconSelector.add(Box.createHorizontalStrut(12));
            iconSelector.add(rightBtn);
            iconSelector.add(Box.createHorizontalGlue());

            JPanel iconPanel = new JPanel(new BorderLayout()); iconPanel.setOpaque(false);
            iconPanel.add(new JLabel("Icon:"){{setForeground(Color.DARK_GRAY);}}, BorderLayout.NORTH);
            iconPanel.add(iconSelector, BorderLayout.CENTER);

            fields.setBorder(BorderFactory.createEmptyBorder(10,0,10,0));
            panel.add(fields, BorderLayout.NORTH);
            panel.add(iconPanel, BorderLayout.CENTER);

            JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER,20,0)); btns.setOpaque(false);
            FadingButton okBtn = new FadingButton("Create");
            okBtn.setBackground(new Color(76,175,80)); okBtn.setForeground(Color.WHITE); okBtn.setPreferredSize(new Dimension(100,38));
            okBtn.addActionListener(e->{ accepted=true; setVisible(false); dispose(); });

            FadingButton cancel = new FadingButton("Cancel");
            cancel.setBackground(new Color(200,200,200)); cancel.setForeground(Color.DARK_GRAY); cancel.setPreferredSize(new Dimension(100,38));
            cancel.addActionListener(e->{ accepted=false; setVisible(false); dispose(); });
            btns.add(okBtn); btns.add(cancel);
            panel.add(btns, BorderLayout.SOUTH);

            add(panel);
            pack();
            setSize(420,380);
            setLocationRelativeTo(parent);
        }

        boolean isAccepted(){ return accepted; }
        String getNotebookName(){ return nameField.getText().trim(); }
        NotebookInfo.Type getNotebookType(){ return (NotebookInfo.Type)typeBox.getSelectedItem(); }
        String getSelectedIcon(){ return selectedIcon; }

        class ArrowButton extends JButton{
            private final boolean right;
            ArrowButton(boolean right){ this.right=right; Dimension d=new Dimension(28,28); setPreferredSize(d); setMinimumSize(d); setMaximumSize(d); setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false); }
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(240,240,240));
                g2.fillOval(0,0,getWidth(),getHeight());
                g2.setColor(new Color(120,120,120));
                int cx=getWidth()/2, cy=getHeight()/2;
                int s=6;
                if(right){
                    g2.drawLine(cx-s/2,cy-s, cx+s/2,cy);
                    g2.drawLine(cx-s/2,cy+s, cx+s/2,cy);
                } else {
                    g2.drawLine(cx+s/2,cy-s, cx-s/2,cy);
                    g2.drawLine(cx+s/2,cy+s, cx-s/2,cy);
                }
                g2.dispose();
            }
        }
    }

    /* Options dialog */
    private static class NotebookOptionsDialog extends JDialog{
        private boolean renamed=false, deleted=false; private String newName=null;
        NotebookOptionsDialog(Frame parent, NotebookInfo nb){
            super(parent, nb.getName(), true);
            setUndecorated(true);
            setBackground(new Color(0,0,0,0));
            setLayout(new BorderLayout());

            RoundedPanel panel = new RoundedPanel(); panel.setArc(20);
            panel.setBackground(new Color(45,45,45,230)); panel.setLayout(new BorderLayout(10,10)); panel.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));

            JLabel info = new JLabel("<html><center>Created "+((System.currentTimeMillis()-nb.getCreatedMillis())/86400000L)+" days ago<br>Entries: "+countEntries(nb)+"</center></html>", SwingConstants.CENTER);
            info.setForeground(Color.WHITE);
            panel.add(info, BorderLayout.NORTH);

            JTextField nameField = new JTextField(nb.getName());
            nameField.setBorder(BorderFactory.createEmptyBorder(6,8,6,8));
            panel.add(nameField, BorderLayout.CENTER);

            JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER,12,0)); btns.setOpaque(false);
            FadingButton save = new FadingButton("Save"); save.setBackground(new Color(80,130,180)); save.setForeground(Color.WHITE);
            FadingButton del  = new FadingButton("Delete"); del.setBackground(new Color(200,30,30)); del.setForeground(Color.WHITE);
            FadingButton close  = new FadingButton("Close"); close.setBackground(new Color(120,120,120)); close.setForeground(Color.WHITE);

            save.addActionListener(e->{ renamed=true; newName=nameField.getText().trim(); setVisible(false); dispose(); });
            del.addActionListener(e->{ deleted=true; setVisible(false); dispose(); });
            close.addActionListener(e->{ setVisible(false); dispose(); });

            btns.add(save); btns.add(del); btns.add(close);
            panel.add(btns, BorderLayout.SOUTH);

            add(panel);
            pack(); setLocationRelativeTo(parent);
        }
        boolean isDeleted(){ return deleted; }
        boolean isRenamed(){ return renamed; }
        String getNewName(){ return newName; }
    }

    // Create the permanent tile for adding a new notebook
    private JPanel createAddTile(){
        JPanel tile = new JPanel(){
            { setOpaque(false); }
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                float[] dash={4f,4f};
                g2.setStroke(new BasicStroke(2f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND,1f,dash,0f));
                g2.setColor(new Color(180,180,180));
                g2.drawRoundRect(1,1,getWidth()-2,getHeight()-2,12,12);
                // plus icon
                int cx=getWidth()/2, cy=getHeight()/2, s=12;
                g2.setStroke(new BasicStroke(3f));
                g2.drawLine(cx-s/2,cy, cx+s/2,cy);
                g2.drawLine(cx,cy-s/2, cx,cy+s/2);
                g2.dispose();
            }
        };
        tile.setPreferredSize(new Dimension(70,70));
        tile.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        tile.addMouseListener(new MouseAdapter(){
            @Override public void mouseClicked(MouseEvent e){ promptNew(); }
        });
        return tile;
    }

    // reusable rounded text field class
    private static class ModernTextField extends JTextField{
        ModernTextField(int cols){ super(cols); setOpaque(false); setBorder(BorderFactory.createEmptyBorder(6,10,6,10)); }
        @Override protected void paintComponent(Graphics g){
            Graphics2D g2=(Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(240,240,240));
            g2.fillRoundRect(0,0,getWidth(),getHeight(),10,10);
            super.paintComponent(g2);
            g2.dispose();
        }
        @Override protected void paintBorder(Graphics g){
            Graphics2D g2=(Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.LIGHT_GRAY);
            g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,10,10);
            g2.dispose();
        }
    }

    private void selectTile(NotebookTile tile){
        if(selectedTile!=null){ selectedTile.setSelected(false); }
        selectedTile = tile;
        if(selectedTile!=null){ selectedTile.setSelected(true); }
        deleteBtn.setEnabled(selectedTile!=null);
    }

    private void deleteSelectedNotebook(){
        if(selectedTile==null) return;
        NotebookInfo nb = selectedTile.nb;
        boolean ok = CustomConfirmDialog.confirm(this, "Delete Notebook", "Delete notebook '"+nb.getName()+"'?" );
        if(ok){
            store.delete(nb);
            selectedTile = null;
            deleteBtn.setEnabled(false);
            refresh();
        }
    }

    /** Adds the given notebook tile with a simple grow animation */
    private void animateNewNotebook(NotebookInfo nb){
        // Remove the '+' add tile (assumed last component)
        Component addTile = null;
        if(gallery.getComponentCount()>0){
            addTile = gallery.getComponent(gallery.getComponentCount()-1);
            gallery.remove(addTile);
        }

        NotebookTile newTile = new NotebookTile(nb);
        newTile.setPreferredSize(new Dimension(0,120));
        gallery.add(newTile);
        if(addTile!=null) gallery.add(addTile); // keep plus tile at end

        gallery.revalidate(); gallery.repaint();

        Timer anim = new Timer(15,null);
        anim.addActionListener(e->{
            Dimension d = newTile.getPreferredSize();
            int w = d.width + 12; // speed
            if(w >= 120){
                w = 120; anim.stop();
            }
            newTile.setPreferredSize(new Dimension(w,120));
            gallery.revalidate(); gallery.repaint();
        });
        anim.start();
    }
} 
