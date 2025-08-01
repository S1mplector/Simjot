package main.ui.panels;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import javax.swing.*;
import main.dialog.CustomMessageDialog;
import main.transitions.FadingButton;
import main.ui.JournalApp;
import main.ui.buttons.RoundedButton;
import main.ui.components.ModernCheckBoxUI;
import main.ui.components.ModernComboBoxUI;
import main.ui.components.ModernSpinnerUI;
import main.util.AppDirectories;
import main.util.SettingsStore;

public class SettingsPanel extends JPanel {

    private final JournalApp app;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardsPanel = new JPanel(cardLayout);
    private final DefaultListModel<String> sectionModel = new DefaultListModel<>();
    private final JList<String> sectionList = new JList<>(sectionModel);
    private final Map<String, SettingsPage> pages = new HashMap<>();

    public SettingsPanel(JournalApp app, CardLayout parentLayout, JPanel parentCardPanel) {
        this.app = app;
        setLayout(new BorderLayout());
        buildSidebar();
        buildPages();
        add(buildSouthBar(), BorderLayout.SOUTH);
        setPreferredSize(new Dimension(600,400));
    }

    private void buildSidebar(){
        sectionModel.addElement("General");
        sectionModel.addElement("Appearance");
        sectionModel.addElement("Drawing");
        sectionModel.addElement("Storage");
        sectionModel.addElement("About");

        sectionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sectionList.setSelectedIndex(0);
        sectionList.setFixedCellWidth(160);
        sectionList.setBackground(new Color(245,245,245));
        sectionList.setCellRenderer(new SidebarCellRenderer());
        sectionList.setBorder(BorderFactory.createEmptyBorder());
        sectionList.addListSelectionListener(e->{
            if(!e.getValueIsAdjusting()){
                String key = sectionList.getSelectedValue();
                cardLayout.show(cardsPanel, key);
            }
        });
        add(new JScrollPane(sectionList), BorderLayout.WEST);
    }

    private void buildPages(){
        // General
        GeneralPage general = new GeneralPage();
        addPage("General", general);

        // Appearance (theme, glow, background)
        AppearancePage appearance = new AppearancePage();
        addPage("Appearance", appearance);

        // Drawing page fully implemented
        DrawingPage drawPage = new DrawingPage();
        addPage("Drawing", drawPage);

        StoragePage storagePage = new StoragePage();
        addPage("Storage", storagePage);

        addPage("About", new PlaceholderPage("Simjot v1.0\nCreated by Ilgaz with ❤️"));

        add(cardsPanel, BorderLayout.CENTER);
        cardLayout.show(cardsPanel, "General");
    }

    private JPanel buildSouthBar(){
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        RoundedButton cancel = new RoundedButton("Cancel");
        cancel.addActionListener(e-> app.switchCard(JournalApp.MAIN_MENU));
        RoundedButton save = new RoundedButton("Save");
        save.addActionListener(e-> saveAll());
        south.add(cancel);
        south.add(save);
        return south;
    }

    private void addPage(String name, SettingsPage page){
        pages.put(name, page);
        cardsPanel.add(page.getComponent(), name);
    }

    private void saveAll(){
        pages.values().forEach(SettingsPage::apply);
        SettingsStore.get().save();

        // Refresh main menu so background / theme update instantly
        app.recreateMainMenuPanel();

        CustomMessageDialog.display(this, "Success", "Settings saved.", false);
    }

    // ---------- Page types ---------- //
    private interface SettingsPage{
        JComponent getComponent();
        void apply();
    }

    private static class PlaceholderPage extends JPanel implements SettingsPage{
        public PlaceholderPage(String msg){
            setLayout(new BorderLayout());
            setBackground(Color.WHITE);
            JLabel lab=new JLabel("<html><div style='text-align:center;'>"+msg+"</div></html>",SwingConstants.CENTER);
            add(lab, BorderLayout.CENTER);
        }
        public JComponent getComponent(){ return this; }
        public void apply(){}
    }

    // ---- General ----
    private class GeneralPage extends JPanel implements SettingsPage{
        private final JSpinner journalFont;
        private final JSpinner poemFont;
        private final JSpinner autosaveSpin;
        GeneralPage(){
            setLayout(new GridBagLayout());
            setBackground(Color.WHITE);
            GridBagConstraints gc = new GridBagConstraints();
            gc.insets=new Insets(5,5,5,5);
            gc.fill=GridBagConstraints.HORIZONTAL;

            SettingsStore store = SettingsStore.get();
            journalFont = new JSpinner(new SpinnerNumberModel(store.getJournalFontSize(), 8, 72, 1));
            journalFont.setUI(new ModernSpinnerUI());

            poemFont = new JSpinner(new SpinnerNumberModel(store.getPoemFontSize(), 8, 72, 1));
            poemFont.setUI(new ModernSpinnerUI());

            gc.gridx=0; gc.gridy=0; add(new JLabel("Journal font size:"), gc);
            gc.gridx=1; add(journalFont, gc);

            gc.gridx=0; gc.gridy=1; add(new JLabel("Poem font size:"), gc);
            gc.gridx=1; add(poemFont, gc);

            autosaveSpin = new JSpinner(new SpinnerNumberModel(SettingsStore.get().getAutosaveMinutes(),0,120,5));
            autosaveSpin.setUI(new ModernSpinnerUI());
            ((JSpinner.DefaultEditor)autosaveSpin.getEditor()).getTextField().setColumns(3);
            gc.gridx=0; gc.gridy=2; add(new JLabel("Autosave interval (min):"),gc);
            gc.gridx=1; add(autosaveSpin,gc);
        }
        public JComponent getComponent(){ return this; }
        public void apply(){
            SettingsStore store = SettingsStore.get();
            int jf = (Integer) journalFont.getValue();
            store.setJournalFontSize(jf);
            JournalApp.globalJournalFontSize = jf;
            
            int pf = (Integer) poemFont.getValue();
            store.setPoemFontSize(pf);
            
            SettingsStore.get().setAutosaveMinutes((Integer)autosaveSpin.getValue());
        }
    }

    // ---- Appearance ----
    private class AppearancePage extends JPanel implements SettingsPage{
        private final RoundedButton backgroundOptionsBtn;
        private final JComboBox<String> themeBox;
        private final JCheckBox glowChk;
        private final JCheckBox disableAnimationsChk;

        AppearancePage(){
            setLayout(new GridBagLayout());
            setBackground(Color.WHITE);
            GridBagConstraints gc = new GridBagConstraints();
            gc.insets=new Insets(5,5,5,5);
            gc.fill=GridBagConstraints.HORIZONTAL;

            SettingsStore store = SettingsStore.get();
            String[] themes = {"Light","Dark"};
            themeBox = new JComboBox<>(themes);
            themeBox.setUI(new ModernComboBoxUI());
            themeBox.setRenderer(new ModernComboBoxUI.ModernComboBoxRenderer());
            themeBox.setSelectedItem(store.getTheme());

            glowChk = new JCheckBox("Enable button glow", store.isGlowEnabled());
            glowChk.setUI(new ModernCheckBoxUI());
            glowChk.setBackground(Color.WHITE);

            disableAnimationsChk = new JCheckBox("Disable UI animations", store.isAnimationsDisabled());
            disableAnimationsChk.setUI(new ModernCheckBoxUI());
            disableAnimationsChk.setBackground(Color.WHITE);

            // Single background options button
            backgroundOptionsBtn = new RoundedButton("Background Options");
            backgroundOptionsBtn.addActionListener(e->openBackgroundOptions());
            
            gc.gridx=0; gc.gridy=0; add(new JLabel("Background:"), gc);
            gc.gridx=1; add(backgroundOptionsBtn, gc);
            gc.gridx=0; gc.gridy=1; add(new JLabel("Theme:"), gc);
            gc.gridx=1; add(themeBox, gc);
            gc.gridx=0; gc.gridy=2; gc.gridwidth=2; add(glowChk, gc);
            gc.gridx=0; gc.gridy=3; gc.gridwidth=2; add(disableAnimationsChk, gc);
        }
        public JComponent getComponent(){ return this; }
        public void apply(){
            SettingsStore store = SettingsStore.get();
            String theme = (String) themeBox.getSelectedItem();
            store.setTheme(theme);
            // TODO: apply theme live

            boolean glow = glowChk.isSelected();
            store.setGlowEnabled(glow);
            FadingButton.setGlowEnabled(glow);
            main.ui.buttons.ToolbarIconButton.setGlowEnabled(glow);

            store.setAnimationsDisabled(disableAnimationsChk.isSelected());
        }

        private void openBackgroundOptions(){
            WallpaperGalleryPanel.showWallpaperGallery(this);
        }
    }

    // ---- Drawing Page ----
    private class DrawingPage extends JPanel implements SettingsPage{
        private final JSpinner brushSize;
        private final JCheckBox smoothing;
        private final JCheckBox thumbnails;
        DrawingPage(){
            setLayout(new GridBagLayout());
            setBackground(Color.WHITE);
            GridBagConstraints gc = new GridBagConstraints();
            gc.insets=new Insets(5,5,5,5);
            gc.fill=GridBagConstraints.HORIZONTAL;

            SettingsStore st = SettingsStore.get();
            brushSize = new JSpinner(new SpinnerNumberModel(st.getDefaultBrushSize(),1,50,1));
            brushSize.setUI(new ModernSpinnerUI());
            smoothing = new JCheckBox("Enable stroke smoothing", st.isSmoothingEnabled());
            smoothing.setUI(new ModernCheckBoxUI());
            thumbnails= new JCheckBox("Generate thumbnails on save", st.isThumbnailGeneration());
            thumbnails.setUI(new ModernCheckBoxUI());
            smoothing.setBackground(Color.WHITE);
            thumbnails.setBackground(Color.WHITE);

            gc.gridx=0;gc.gridy=0;add(new JLabel("Default brush size:"),gc);
            gc.gridx=1;add(brushSize,gc);
            gc.gridx=0;gc.gridy=1;gc.gridwidth=2;add(smoothing,gc);
            gc.gridy=2;add(thumbnails,gc);
        }
        public JComponent getComponent(){return this;}
        public void apply(){
            SettingsStore st=SettingsStore.get();
            st.setDefaultBrushSize((Integer)brushSize.getValue());
            st.setSmoothingEnabled(smoothing.isSelected());
            st.setThumbnailGeneration(thumbnails.isSelected());
        }
    }

    // ---- Storage Page ----
    private class StoragePage extends JPanel implements SettingsPage{
        private final JLabel pathLbl;
        private final RoundedButton clearThumbsBtn;
        StoragePage(){
            setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
            setBackground(Color.WHITE);
            add(new JLabel("Simjot root folder:"));
            pathLbl=new JLabel(AppDirectories.getRoot().getAbsolutePath());
            pathLbl.setFont(new Font("Monospaced",Font.PLAIN,12));
            add(pathLbl);

            RoundedButton openBtn = new RoundedButton("Open in Explorer");
            openBtn.addActionListener(e->{
                try{
                    java.awt.Desktop.getDesktop().open(AppDirectories.getRoot());
                }catch(Exception ignored){}
            });
            add(openBtn);

            clearThumbsBtn = new RoundedButton("Clear thumbnails cache");
            clearThumbsBtn.addActionListener(e->clearThumbs());
            add(clearThumbsBtn);
        }
        public JComponent getComponent(){return this;}
        public void apply(){}

        private void clearThumbs(){
            java.io.File dir = AppDirectories.folder(AppDirectories.Type.DRAWINGS);
            int deleted=0;
            for(java.io.File f: dir.listFiles((d,n)->n.toLowerCase().endsWith(".png"))){
                if(f.delete()) deleted++; }
            CustomMessageDialog.display(this, "Cleanup", deleted+" thumbnails deleted.", false);
        }
    }

    // --- Modern sidebar renderer ----
    private static class SidebarCellRenderer extends JPanel implements ListCellRenderer<String>{
        private final JLabel lbl = new JLabel();
        SidebarCellRenderer(){ setLayout(new FlowLayout(FlowLayout.LEFT,8,8)); setOpaque(true); add(lbl); }
        @Override public Component getListCellRendererComponent(JList<? extends String> list, String value,int idx,boolean sel,boolean focus){
            lbl.setText(value);
            lbl.setFont(new Font("SansSerif", Font.PLAIN, 14));
            lbl.setForeground(sel?Color.WHITE:Color.DARK_GRAY);
            setBackground(sel?new Color(0,120,215):new Color(245,245,245));
            setPreferredSize(new Dimension(list.getFixedCellWidth(),40));
            return this;
        }
    }
    

}
