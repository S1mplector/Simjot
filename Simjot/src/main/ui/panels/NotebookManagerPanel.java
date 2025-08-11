package main.ui.panels;

import java.awt.*;
import java.awt.event.*;
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
import main.ui.theme.aero.AeroPainters;
import main.ui.theme.aero.AeroTheme;

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
        final int S = 100;
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Base sizing
        int s = 72; // icon content size
        int x = (S - s) / 2;
        int y = (S - s) / 2;
        int arc = Math.max(10, s / 6);

        // --- Soft drop shadow (behind book) ---
        {
            int sh = 6;
            Rectangle shadowRect = new Rectangle(x + 3, y + 5, s - 2, s - 2);
            for (int i = sh; i >= 1; i--) {
                int a = (int) (22 * (i / (float) sh));
                g2.setColor(new Color(0, 0, 0, a));
                g2.setStroke(new BasicStroke(i * 1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new java.awt.geom.RoundRectangle2D.Float(
                        shadowRect.x + 1, shadowRect.y + 1,
                        shadowRect.width - 2, shadowRect.height - 2,
                        arc, arc));
            }
        }

        // --- Page block on the right ---
        int pageW = Math.max(16, s / 4);
        Rectangle pageRect = new Rectangle(x + s - pageW - 6, y + 8, pageW, s - 16);
        // Page fill
        AeroPainters.paintVerticalGradient(g2, pageRect,
                new Color(255, 255, 255), new Color(240, 240, 240), arc - 6);
        // Page lines
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(new Color(0, 0, 0, 45));
        int lines = 4;
        for (int i = 0; i < lines; i++) {
            int yy = pageRect.y + pageRect.height / (lines + 1) * (i + 1);
            g2.drawLine(pageRect.x + 6, yy, pageRect.x + pageRect.width - 6, yy);
        }
        // Page inner stroke
        AeroPainters.paintInnerStroke(g2, pageRect, arc - 6, new Color(0, 0, 0, 35));

        // --- Spine (left) ---
        int spineW = Math.max(12, s / 5);
        Rectangle spine = new Rectangle(x, y, spineW, s);
        AeroPainters.paintVerticalGradient(g2, spine, AeroTheme.AERO_BLUE_DARK, AeroTheme.AERO_BLUE, arc);
        // Spine highlight strip
        g2.setColor(new Color(255, 255, 255, 100));
        g2.fill(new java.awt.geom.RoundRectangle2D.Float(
                spine.x + 2, spine.y + 2, Math.max(3, spineW / 5f), spine.height - 4, arc, arc));

        // --- Cover (center area excluding spine and a bit of page) ---
        int coverX = x + spineW - 1;
        int coverW = s - spineW - (S - (pageRect.x + pageRect.width)) - 4; // leave space to the pages
        Rectangle coverRect = new Rectangle(coverX, y, coverW, s);

        // Outer glow halo
        AeroPainters.paintOuterGlow(g2, coverRect, arc, AeroTheme.AERO_BLUE_LIGHT, 4, 60);
        // Cover gradient
        AeroPainters.paintVerticalGradient(g2, coverRect, AeroTheme.AERO_BLUE_LIGHT, AeroTheme.AERO_BLUE, arc);
        // Glass highlight overlay
        AeroPainters.paintGlassOverlay(g2, coverRect, arc);
        // Border accent
        g2.setColor(new Color(0, 0, 0, 70));
        g2.setStroke(new BasicStroke(1.2f));
        g2.draw(new java.awt.geom.RoundRectangle2D.Float(coverRect.x + 0.5f, coverRect.y + 0.5f,
                coverRect.width - 1f, coverRect.height - 1f, arc, arc));

        // --- Elastic band/bookmark near the right edge of the cover ---
        int bandX = coverRect.x + coverRect.width - Math.max(10, s / 10);
        g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(AeroTheme.AERO_BLUE_DARK.getRed(), AeroTheme.AERO_BLUE_DARK.getGreen(), AeroTheme.AERO_BLUE_DARK.getBlue(), 160));
        g2.drawLine(bandX, coverRect.y + arc / 2, bandX, coverRect.y + coverRect.height - arc / 2);

        // Subtle separator between cover and page block
        g2.setColor(new Color(0, 0, 0, 35));
        g2.drawLine(pageRect.x - 1, pageRect.y + 2, pageRect.x - 1, pageRect.y + pageRect.height - 2);

        g2.dispose();
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
            MouseAdapter forward = new MouseAdapter(){
                @Override public void mouseEntered(MouseEvent e){ NotebookTile.this.mouseEntered(e); }
                @Override public void mouseExited(MouseEvent e){ NotebookTile.this.mouseExited(e); }
                @Override public void mouseClicked(MouseEvent e){ NotebookTile.this.mouseClicked(e); }
            };
            icon.addMouseListener(forward);
            add(icon, BorderLayout.CENTER);

            JLabel nameLbl = new JLabel(nb.getName(),SwingConstants.CENTER);
            nameLbl.setForeground(AeroTheme.TEXT_PRIMARY);
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
            if(name!=null && !name.isEmpty()){
                NotebookInfo nb = store.create(name, type, "notebook");
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

    /* Create dialog */
    private static class CreateNotebookDialog extends JDialog{
        private boolean accepted=false;
        private final ModernTextField nameField = new ModernTextField(20);
        private final JComboBox<NotebookInfo.Type> typeBox = new JComboBox<>(new NotebookInfo.Type[]{ NotebookInfo.Type.JOURNAL, NotebookInfo.Type.POETRY });

        CreateNotebookDialog(Frame parent){
            super(parent, "Create Notebook", true);
            setUndecorated(true);
            setBackground(new Color(0,0,0,0));
            setLayout(new BorderLayout());

            RoundedPanel panel = new RoundedPanel();
            panel.setArc(16);
            panel.setLayout(new BorderLayout(12,12));
            panel.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));

            // Title
            JLabel title = new JLabel("Create Notebook", SwingConstants.LEFT);
            title.setForeground(Color.DARK_GRAY);
            title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
            panel.add(title, BorderLayout.NORTH);

            // Center content
            JPanel center = new JPanel(new GridBagLayout());
            center.setOpaque(false);
            GridBagConstraints gc = new GridBagConstraints();
            gc.gridx=0; gc.gridy=0; gc.anchor=GridBagConstraints.WEST; gc.fill=GridBagConstraints.HORIZONTAL; gc.weightx=1.0; gc.insets=new Insets(4,2,4,2);

            // Name field
            nameField.setToolTipText("Notebook name");
            center.add(nameField, gc);

            // Type selector
            gc.gridy++;
            typeBox.setUI(new ModernComboBoxUI());
            typeBox.setFocusable(false);
            typeBox.setRenderer(new javax.swing.ListCellRenderer<NotebookInfo.Type>(){
                private final JPanel cell = new JPanel(new BorderLayout());
                private final JLabel t = new JLabel();
                private final JLabel sub = new JLabel();
                {
                    cell.setOpaque(true);
                    t.setFont(t.getFont().deriveFont(Font.PLAIN, 14f));
                    sub.setFont(sub.getFont().deriveFont(Font.PLAIN, 11f));
                    sub.setForeground(new Color(120,120,120));
                    cell.add(t, BorderLayout.NORTH);
                    cell.add(sub, BorderLayout.SOUTH);
                    cell.setBorder(BorderFactory.createEmptyBorder(3,8,3,8));
                }
                @Override public Component getListCellRendererComponent(JList<? extends NotebookInfo.Type> list, NotebookInfo.Type value, int index, boolean isSelected, boolean cellHasFocus){
                    String friendly = value==NotebookInfo.Type.JOURNAL?"Journaling":"Poetry";
                    String desc = value==NotebookInfo.Type.JOURNAL?"Daily notes, moods, reflections":"Write and organize poems";
                    t.setText(friendly);
                    sub.setText(index>=0?desc:"");
                    if(isSelected){ cell.setBackground(list.getSelectionBackground()); t.setForeground(list.getSelectionForeground()); sub.setForeground(list.getSelectionForeground()); }
                    else { cell.setBackground(Color.WHITE); t.setForeground(Color.DARK_GRAY); sub.setForeground(new Color(120,120,120)); }
                    return cell;
                }
            });
            center.add(typeBox, gc);

            // Description label under combo (updates on selection)
            gc.gridy++;
            JLabel descLabel = new JLabel("Daily notes, moods, reflections");
            descLabel.setForeground(new Color(120,120,120));
            center.add(descLabel, gc);

            typeBox.addActionListener(e->{
                NotebookInfo.Type t = (NotebookInfo.Type) typeBox.getSelectedItem();
                descLabel.setText(t==NotebookInfo.Type.JOURNAL?"Daily notes, moods, reflections":"Write and organize poems");
            });

            panel.add(center, BorderLayout.CENTER);

            // Buttons
            JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT,10,0));
            btns.setOpaque(false);
            RoundedButton okBtn = new RoundedButton("Create");
            okBtn.setPreferredSize(new Dimension(110,36));
            okBtn.setEnabled(false);
            okBtn.addActionListener(e->{ accepted=true; setVisible(false); dispose(); });
            RoundedButton cancel = new RoundedButton("Cancel");
            cancel.setForeground(Color.DARK_GRAY); cancel.setPreferredSize(new Dimension(110,36));
            cancel.addActionListener(e->{ accepted=false; setVisible(false); dispose(); });
            btns.add(okBtn); btns.add(cancel);
            panel.add(btns, BorderLayout.SOUTH);

            // enable Create only when name entered
            nameField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){
                private void upd(){ okBtn.setEnabled(!nameField.getText().trim().isEmpty()); }
                public void insertUpdate(javax.swing.event.DocumentEvent e){ upd(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e){ upd(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e){ upd(); }
            });

            add(panel);
            pack();
            setSize(420,260);
            setLocationRelativeTo(parent);
        }

        boolean isAccepted(){ return accepted; }
        String getNotebookName(){ return nameField.getText().trim(); }
        NotebookInfo.Type getNotebookType(){ return (NotebookInfo.Type)typeBox.getSelectedItem(); }
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

    // Create the permanent tile for adding a new notebook (Aero-styled)
    private JPanel createAddTile(){
        JPanel tile = new JPanel(){
            private boolean hover=false, pressed=false;
            { setOpaque(false); }
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                // Background gradient depending on state
                Color top = pressed ? AeroTheme.BUTTON_PRESS_TOP : (hover ? AeroTheme.BUTTON_HOVER_TOP : AeroTheme.BUTTON_BG_TOP);
                Color bottom = pressed ? AeroTheme.BUTTON_PRESS_BOTTOM : (hover ? AeroTheme.BUTTON_HOVER_BOTTOM : AeroTheme.BUTTON_BG_BOTTOM);
                Rectangle r = new Rectangle(0,0,getWidth(),getHeight());
                AeroPainters.paintVerticalGradient(g2, r, top, bottom, 12);
                AeroPainters.paintGlassOverlay(g2, r, 12);
                // Soft border
                g2.setColor(new Color(180,180,180));
                g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,12,12);
                // Center plus icon
                int cx=getWidth()/2, cy=getHeight()/2, s=14;
                g2.setColor(new Color(90,90,90));
                g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(cx-s/2,cy, cx+s/2,cy);
                g2.drawLine(cx,cy-s/2, cx,cy+s/2);
                g2.dispose();
            }
            @Override protected void processMouseEvent(MouseEvent e){
                switch(e.getID()){
                    case MouseEvent.MOUSE_ENTERED -> { hover=true; repaint(); }
                    case MouseEvent.MOUSE_EXITED -> { hover=false; repaint(); }
                    case MouseEvent.MOUSE_PRESSED -> { pressed=true; repaint(); }
                    case MouseEvent.MOUSE_RELEASED -> { pressed=false; repaint(); }
                }
                super.processMouseEvent(e);
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
            // Persist deletion first
            store.delete(nb);
            // Animate the tile sliding out/shrinking, then remove from gallery
            NotebookTile toRemove = selectedTile;
            selectedTile = null;
            deleteBtn.setEnabled(false);
            animateDeleteNotebook(toRemove);
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

        Timer anim = new Timer(33,null);
        anim.addActionListener(e->{
            Dimension d = newTile.getPreferredSize();
            int w = d.width + 26; // speed adjusted for 33ms tick (~same duration)
            if(w >= 120){
                w = 120; anim.stop();
            }
            newTile.setPreferredSize(new Dimension(w,120));
            gallery.revalidate(); gallery.repaint();
        });
        anim.start();
    }

    /** Animates removal of the selected notebook by shrinking its tile horizontally, then removes it. */
    private void animateDeleteNotebook(NotebookTile tile){
        if(tile==null) return;
        // Ensure the add ('+') tile remains last; no need to move it for deletion
        // Start from current preferred width (default tiles are 120 wide)
        Dimension start = tile.getPreferredSize();
        if(start == null || start.width <= 0){
            start = new Dimension(120, 120);
        }
        final int targetH = start.height > 0 ? start.height : 120;

        // Disable interactions during animation
        for(MouseListener ml: tile.getMouseListeners()) tile.removeMouseListener(ml);
        tile.setCursor(Cursor.getDefaultCursor());

        final int[] w = new int[]{ start.width };
        Timer anim = new Timer(33, null);
        anim.addActionListener(e -> {
            w[0] -= 26; // speed adjusted for 33ms tick (~same duration)
            if(w[0] <= 0){
                w[0] = 0;
                anim.stop();
                // Remove tile and refresh layout
                gallery.remove(tile);
                gallery.revalidate();
                gallery.repaint();
                return;
            }
            tile.setPreferredSize(new Dimension(w[0], targetH));
            gallery.revalidate();
            gallery.repaint();
        });
        anim.start();
    }
}
