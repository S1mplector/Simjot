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
import main.util.AppPerf;
import main.ui.components.AeroScrollBarUI;
import main.ui.theme.aero.AeroPainters;
import main.ui.theme.aero.AeroTheme;

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
        setOpaque(true);
        setBackground(Color.WHITE);
        setFont(AeroTheme.defaultFont());
        buildSidebar();
        buildPages();
        add(buildSouthBar(), BorderLayout.SOUTH);
        setPreferredSize(new Dimension(600,400));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g); // plain white background (no gradients)
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
        sectionList.setOpaque(false);
        sectionList.setCellRenderer(new SidebarCellRenderer());
        sectionList.setBorder(BorderFactory.createEmptyBorder());
        sectionList.addListSelectionListener(e->{
            if(!e.getValueIsAdjusting()){
                String key = sectionList.getSelectedValue();
                cardLayout.show(cardsPanel, key);
            }
        });
        JScrollPane sp = new JScrollPane(sectionList);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        if (sp.getVerticalScrollBar() != null) {
            sp.getVerticalScrollBar().setUI(new AeroScrollBarUI());
        }
        add(sp, BorderLayout.WEST);
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

        cardsPanel.setOpaque(true);
        cardsPanel.setBackground(Color.WHITE);
        add(cardsPanel, BorderLayout.CENTER);
        cardLayout.show(cardsPanel, "General");
    }

    private JPanel buildSouthBar(){
        JPanel chrome = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        chrome.setOpaque(true);
        chrome.setBackground(Color.WHITE);
        RoundedButton cancel = new RoundedButton("Cancel");
        cancel.addActionListener(e-> app.switchCard(JournalApp.MAIN_MENU));
        RoundedButton save = new RoundedButton("Save");
        save.addActionListener(e-> saveAll());
        chrome.add(cancel);
        chrome.add(save);
        return chrome;
    }

    private void addPage(String name, SettingsPage page){
        pages.put(name, page);
        // Add page directly; main area remains plain white
        cardsPanel.add(page.getComponent(), name);
    }

    private void saveAll(){
        pages.values().forEach(SettingsPage::apply);
        SettingsStore.get().save();

        // Refresh main menu so background / theme update instantly
        app.recreateMainMenuPanel();
        
        // Update widget panel visibility immediately
        app.updateWidgetPanelVisibility();

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
            setOpaque(true);
            setBackground(Color.WHITE);
            JLabel lab=new JLabel("<html><div style='text-align:center'>"+msg+"</div></html>",SwingConstants.CENTER);
            lab.setForeground(AeroTheme.TEXT_PRIMARY);
            lab.setFont(AeroTheme.defaultFont());
            add(lab, BorderLayout.CENTER);
        }
        @Override public JComponent getComponent(){ return this; }
        @Override public void apply(){}
    }

    // ---- General ----
    private class GeneralPage extends JPanel implements SettingsPage{
        private final JSpinner journalFont;
        private final JSpinner poemFont;
        private final JSpinner autosaveSpin;
        private final JSpinner uiScaleSpinner;
        GeneralPage(){
            setLayout(new GridBagLayout());
            setOpaque(true);
            setBackground(Color.WHITE);
            GridBagConstraints gc = new GridBagConstraints();
            gc.insets=new Insets(5,5,5,5);
            gc.fill=GridBagConstraints.HORIZONTAL;

            SettingsStore store = SettingsStore.get();
            journalFont = new JSpinner(new SpinnerNumberModel(store.getJournalFontSize(), 8, 72, 1));
            journalFont.setUI(new ModernSpinnerUI());

            poemFont = new JSpinner(new SpinnerNumberModel(store.getPoemFontSize(), 8, 72, 1));
            poemFont.setUI(new ModernSpinnerUI());

            gc.gridx=0; gc.gridy=0; add(label("Journal font size:"), gc);
            gc.gridx=1; add(journalFont, gc);

            gc.gridx=0; gc.gridy=1; add(label("Poem font size:"), gc);
            gc.gridx=1; add(poemFont, gc);

            autosaveSpin = new JSpinner(new SpinnerNumberModel(SettingsStore.get().getAutosaveMinutes(),0,120,5));
            autosaveSpin.setUI(new ModernSpinnerUI());
            ((JSpinner.DefaultEditor)autosaveSpin.getEditor()).getTextField().setColumns(3);
            gc.gridx=0; gc.gridy=2; add(label("Autosave interval (min):"),gc);
            gc.gridx=1; add(autosaveSpin,gc);

            // UI Scale spinner with custom step size
            SpinnerNumberModel uiScaleModel = new SpinnerNumberModel(store.getUIScale(), 0.5, 3.0, 0.25);
            uiScaleSpinner = new JSpinner(uiScaleModel);
            uiScaleSpinner.setUI(new ModernSpinnerUI());
            JSpinner.NumberEditor editor = new JSpinner.NumberEditor(uiScaleSpinner, "0.00");
            uiScaleSpinner.setEditor(editor);
            gc.gridx=0; gc.gridy=3; add(label("UI Scale:"),gc);
            gc.gridx=1; add(uiScaleSpinner,gc);
            
            // Add a detailed note about UI scale changes
            JLabel noteLabel = new JLabel("<html><i>UI scale changes will take effect after closing and reopening Simjot.<br>This setting helps with high-DPI displays (e.g., use 2.0 for 200% scaling).</i></html>");
            noteLabel.setForeground(Color.GRAY);
            gc.gridx=0; gc.gridy=4; gc.gridwidth=2;
            add(noteLabel,gc);
        }
        @Override public JComponent getComponent(){ return this; }
        @Override public void apply(){
            SettingsStore store = SettingsStore.get();
            int jf = (Integer) journalFont.getValue();
            store.setJournalFontSize(jf);
            JournalApp.globalJournalFontSize = jf;
            
            int pf = (Integer) poemFont.getValue();
            store.setPoemFontSize(pf);
            
            SettingsStore.get().setAutosaveMinutes((Integer)autosaveSpin.getValue());
            
            // Save UI scale setting
            float uiScale = ((Number) uiScaleSpinner.getValue()).floatValue();
            store.setUIScale(uiScale);
        }
    }

    // ---- Appearance ----
    private class AppearancePage extends JPanel implements SettingsPage{
        private final RoundedButton backgroundOptionsBtn;
        private final JComboBox<String> themeBox;
        private final JCheckBox glowChk;
        private final JCheckBox disableAnimationsChk;
        private final JCheckBox lowPowerChk;

        AppearancePage(){
            setLayout(new GridBagLayout());
            setOpaque(true);
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
            glowChk.setBackground(new Color(0,0,0,0));

            disableAnimationsChk = new JCheckBox("Disable UI animations", store.isAnimationsDisabled());
            disableAnimationsChk.setUI(new ModernCheckBoxUI());
            disableAnimationsChk.setBackground(new Color(0,0,0,0));

            lowPowerChk = new JCheckBox("Low Power Mode (battery saver)", store.isLowPowerMode());
            lowPowerChk.setUI(new ModernCheckBoxUI());
            lowPowerChk.setBackground(new Color(0,0,0,0));

            // Single background options button
            backgroundOptionsBtn = new RoundedButton("Background Options");
            backgroundOptionsBtn.addActionListener(e->openBackgroundOptions());
            
            gc.gridx=0; gc.gridy=0; add(label("Background:"), gc);
            gc.gridx=1; add(backgroundOptionsBtn, gc);
            gc.gridx=0; gc.gridy=1; add(label("Theme:"), gc);
            gc.gridx=1; add(themeBox, gc);
            gc.gridx=0; gc.gridy=2; gc.gridwidth=2; add(glowChk, gc);
            gc.gridx=0; gc.gridy=3; gc.gridwidth=2; add(disableAnimationsChk, gc);
            gc.gridx=0; gc.gridy=4; gc.gridwidth=2; add(lowPowerChk, gc);
        }
        @Override public JComponent getComponent(){ return this; }
        @Override public void apply(){
            SettingsStore store = SettingsStore.get();
            String theme = (String) themeBox.getSelectedItem();
            store.setTheme(theme);
            // TODO: apply theme live

            boolean glow = glowChk.isSelected();
            store.setGlowEnabled(glow);
            FadingButton.setGlowEnabled(glow);
            main.ui.buttons.ToolbarIconButton.setGlowEnabled(glow);

            store.setAnimationsDisabled(disableAnimationsChk.isSelected());

            boolean lp = lowPowerChk.isSelected();
            store.setLowPowerMode(lp);
            AppPerf.setLowPowerMode(lp);
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
            smoothing.setBackground(new Color(0,0,0,0));
            thumbnails.setBackground(new Color(0,0,0,0));

            gc.gridx=0;gc.gridy=0;add(label("Default brush size:"),gc);
            gc.gridx=1;add(brushSize,gc);
            gc.gridx=0;gc.gridy=1;gc.gridwidth=2;add(smoothing,gc);
            gc.gridy=2;add(thumbnails,gc);
        }
        @Override public JComponent getComponent(){return this;}
        @Override public void apply(){
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
            setOpaque(true);
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
        @Override public JComponent getComponent(){return this;}
        @Override public void apply(){}

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
        private boolean selected;
        SidebarCellRenderer(){
            setLayout(new FlowLayout(FlowLayout.LEFT,8,8));
            setOpaque(false);
            lbl.setFont(AeroTheme.defaultFont().deriveFont(14f));
            add(lbl);
        }
        @Override public Component getListCellRendererComponent(JList<? extends String> list, String value,int idx,boolean sel,boolean focus){
            this.selected = sel;
            lbl.setText(value);
            lbl.setForeground(AeroTheme.TEXT_PRIMARY); // keep text dark even when selected
            setPreferredSize(new Dimension(list.getFixedCellWidth(),40));
            return this;
        }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (selected) {
                Rectangle r = new Rectangle(4, 4, getWidth()-8, getHeight()-8);
                Color top = new Color(240,248,255,230); // light azure
                Color bottom = new Color(221,236,248,230);
                AeroPainters.paintVerticalGradient(g2, r, top, bottom, 10);
                AeroPainters.paintGlassOverlay(g2, r, 10);
                g2.setColor(new Color(0,0,0,40));
                g2.drawRoundRect(r.x, r.y, r.width-1, r.height-1, 10, 10);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // Convenience label styled for Aero theme
    private JLabel label(String text){
        JLabel l = new JLabel(text);
        l.setFont(AeroTheme.defaultFont());
        l.setForeground(AeroTheme.TEXT_PRIMARY);
        return l;
    }
    

}
