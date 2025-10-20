package main.ui.features.notebooks;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.swing.*;

import main.core.service.NotebookStore;
import main.infrastructure.backup.NotebookInfo;
import main.ui.animations.transitions.FadingButton;
import main.ui.app.JournalApp;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.buttons.ToolbarIconButton;
import main.ui.components.combobox.ModernComboBoxUI;
import main.ui.components.containers.RoundedPanel;
import main.ui.theme.aero.AeroPainters;
import main.ui.theme.aero.AeroTheme;

public class NotebookManagerPanel extends JPanel {
    private final NotebookStore store = new NotebookStore();
    private final JPanel gallery = new JPanel(new FlowLayout(FlowLayout.LEFT,20,20));
    private final JournalApp app;

    public NotebookManagerPanel(JournalApp app){
        this.app = app;
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        // --- Top toolbar matching other panels ---
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topBar.setBackground(new Color(230,230,230));
        ToolbarIconButton backBtn = new ToolbarIconButton("back");
        backBtn.setToolTipText("Back to Main Menu");
        backBtn.addActionListener(e-> app.switchCard(JournalApp.MAIN_MENU));
        topBar.add(backBtn);

        add(topBar, BorderLayout.NORTH);

        gallery.setOpaque(false);
        JScrollPane scroll = new JScrollPane(gallery);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll,BorderLayout.CENTER);

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
            @Override public void mouseClicked(MouseEvent e){ if(e.getClickCount()==1) openNotebook(nb); }
        });
        return tile;
    }

    private static BufferedImage createIcon(NotebookInfo nb){
        final int S = 100; // tile icon canvas
        final int ICON_SIZE = 72;
        BufferedImage canvas = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = canvas.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        // Use centralized PNG renderer (with caching + subtle shadow)
        String id = "notebook"; // could be extended to read from nb metadata
        String res = main.ui.components.icons.ImageIconRenderer.mapIdToResource(id);
        java.awt.image.BufferedImage scaled = (res != null)
                ? main.ui.components.icons.ImageIconRenderer.get(res, ICON_SIZE, true)
                : null;

        int x = (S - ICON_SIZE) / 2;
        int y = (S - ICON_SIZE) / 2;
        if (scaled != null) {
            g2.drawImage(scaled, x, y, null);
        } else {
            // Fallback: simple vector placeholder
            g2.setColor(new Color(230,230,230));
            g2.fillRoundRect(x, y, ICON_SIZE, ICON_SIZE, 10, 10);
            g2.setColor(new Color(180,180,180));
            g2.drawRoundRect(x, y, ICON_SIZE, ICON_SIZE, 10, 10);
        }
        g2.dispose();
        return canvas;
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
        private float selProgress = 0f; // 0..1 animation for selection
        private javax.swing.Timer selTimer;
        void setSelected(boolean s){
            if(this.selected == s) return;
            this.selected = s;
            // animate towards target (1 for selected, 0 for not)
            if(selTimer!=null && selTimer.isRunning()) selTimer.stop();
            final float target = s ? 1f : 0f;
            selTimer = new javax.swing.Timer(16, null);
            selTimer.addActionListener(e->{
                // simple easing towards target
                selProgress += (target - selProgress) * 0.25f;
                if(Math.abs(target - selProgress) < 0.02f){ selProgress = target; selTimer.stop(); }
                repaint();
            });
            selTimer.start();
            repaint();
        }
        @Override protected void paintComponent(Graphics g){
            super.paintComponent(g);
            Graphics2D g2=(Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arc = 16;

            // Subtle hover background
            if(hover && selProgress < 0.5f){
                g2.setColor(new Color(255,255,255,90));
                g2.fillRoundRect(0,0,w-1,h-1,arc,arc);
                g2.setColor(new Color(0,0,0,40));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(1,1,w-3,h-3,arc,arc);
            }

            g2.dispose();
        }
        @Override public void mouseEntered(MouseEvent e){ hover=true; repaint(); }
        @Override public void mouseExited(MouseEvent e){ hover=false; repaint(); }
        @Override public void mouseClicked(MouseEvent e){
            if(SwingUtilities.isLeftMouseButton(e)){
                if(e.getClickCount()==1){ openNotebook(nb); }
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
            }
        }
    }

    private void openNotebook(NotebookInfo nb){
        app.openNotebookEntries(nb);
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
                // Center PNG icon for creating a new notebook
                // Use centralized renderer with caching; resource lives under Simjot/src/main/resources/img/icons/newnotebook.png
                int iconSize = 42; // visually balanced within 70x70 tile
                java.awt.image.BufferedImage img = main.ui.components.icons.ImageIconRenderer.get("img/icons/newnotebook.png", iconSize, true);
                if (img != null) {
                    int x = (getWidth() - iconSize) / 2;
                    int y = (getHeight() - iconSize) / 2;
                    g2.drawImage(img, x, y, null);
                }
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
}
