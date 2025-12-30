/*
 * SIMJOT - PROPRIETARY
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 * 
 * This source code is licensed under the Simjot Source-Available Personal Use License.
 * You may view and study this code for personal, non-commercial use.
 * Distribution, commercial use, and derivative works are strictly prohibited.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.features.gallery;

import java.awt.*;
import java.io.File;
import java.util.Arrays;
import javax.swing.*;
import main.ui.animations.transitions.FadingButton;
import main.ui.app.JournalApp;
import main.ui.dialog.confirmation.CustomConfirmDialog;
import main.ui.dialog.message.CustomMessageDialog;
import main.ui.features.drawing.DrawingPanel;

public class GalleryPanel extends JPanel {
    private final DefaultListModel<File> model = new DefaultListModel<>();
    private final JList<File> list = new JList<>(model);
    private final File drawingsDir;
    private final DrawingPanel drawingPanel;
    private final JournalApp app;

    public GalleryPanel(File drawingsDir, CardLayout layout, JPanel cardPanel, JournalApp app, DrawingPanel drawingPanel) {
        this.drawingsDir = drawingsDir;
        this.drawingPanel = drawingPanel;
        this.app = app;
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        FadingButton back = new FadingButton("Back");
        back.addActionListener(e->app.switchCard(JournalApp.MAIN_MENU));
        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT));
        north.setOpaque(false);
        north.add(back);
        add(north, BorderLayout.NORTH);

        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        list.setVisibleRowCount(-1);
        list.setFixedCellWidth(140);
        list.setFixedCellHeight(140);
        list.setCellRenderer((JList<? extends File> l, File value, int idx, boolean sel, boolean focus)->{
            JLabel lab = new JLabel();
            lab.setOpaque(true);
            lab.setForeground(Color.DARK_GRAY);
            lab.setHorizontalAlignment(SwingConstants.CENTER);
            lab.setVerticalAlignment(SwingConstants.CENTER);
            String pngPath = value.getAbsolutePath().replace(".mydraw", ".png");
            java.io.File pngFile = new java.io.File(pngPath);
            if(pngFile.exists()){
                ImageIcon icon = new ImageIcon(new ImageIcon(pngPath).getImage().getScaledInstance(120,120,Image.SCALE_SMOOTH));
                lab.setIcon(icon);
            }else{
                lab.setText(value.getName());
            }
            lab.setBackground(sel?new Color(0,120,215,60):Color.WHITE);
            // Tooltip showing file last-modified date
            java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("dd MMM yyyy HH:mm");
            lab.setToolTipText(df.format(new java.util.Date(value.lastModified())));
            return lab;
        });
        list.setBackground(Color.WHITE);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane sc = new JScrollPane(list);
        sc.setOpaque(true);
        sc.setBackground(Color.WHITE);
        add(sc, BorderLayout.CENTER);

        JPanel south=new JPanel(new FlowLayout()); south.setOpaque(true); south.setBackground(Color.WHITE);
        FadingButton open=new FadingButton("Open");
        FadingButton delete=new FadingButton("Delete");
        open.addActionListener(e->{ File f=list.getSelectedValue(); if(f!=null){ CustomMessageDialog.display(this,"Info","Open not implemented yet.",false); }});
        delete.addActionListener(e->{ File f=list.getSelectedValue(); if(f!=null){ boolean ok=CustomConfirmDialog.confirm(this,"Confirm","Delete "+f.getName()+"?"); if(ok){ if(f.delete()){ model.removeElement(f);} } }});
        south.add(open); south.add(delete);
        add(south, BorderLayout.SOUTH);

        // Double-click to open drawing in editor
        list.addMouseListener(new java.awt.event.MouseAdapter(){
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e){
                if(e.getClickCount()==2){
                    File f=list.getSelectedValue();
                    if(f!=null){
                        drawingPanel.loadFromFile(f);
                        app.switchCard("Drawing");
                    }
                }
            }
        });

        updateFileList();
    }

    private void updateFileList(){
        model.clear();
        if(drawingsDir.exists()){
            File[] files = drawingsDir.listFiles((d,f)->f.toLowerCase().endsWith(".mydraw"));
            if(files!=null){
                Arrays.stream(files)
                        .sorted((a,b)->Long.compare(b.lastModified(), a.lastModified()))
                        .forEach(model::addElement);
            }
        }
        // If list is empty show placeholder label
        if(model.isEmpty()){
            if(list.getParent()!=null){ // remove previous placeholder if any
                remove(list.getParent().getParent());
            }
            JLabel empty=new JLabel("No drawings yet. Go create something!",SwingConstants.CENTER);
            empty.setForeground(Color.DARK_GRAY);
            add(empty, BorderLayout.CENTER);
        }else{
            // Ensure scrollpane is present (it may have been removed when empty last time)
            if(list.getParent()==null){
                JScrollPane sc=new JScrollPane(list);
                sc.setOpaque(true); sc.setBackground(Color.WHITE); sc.getViewport().setOpaque(true);
                sc.getViewport().setBackground(Color.WHITE);
                add(sc, BorderLayout.CENTER);
            }
        }
        revalidate(); repaint();
    }

    @Override
    public void setVisible(boolean aFlag){
        if(aFlag){
            updateFileList();
        }
        super.setVisible(aFlag);
    }

    // Allow external caller (e.g., DrawingPanel) to refresh list after new save
    public void refresh(){ updateFileList(); }
} 
