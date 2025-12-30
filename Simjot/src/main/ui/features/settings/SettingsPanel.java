package main.ui.features.settings;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.HashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;

import main.core.service.SettingsStore;
import main.ui.app.JournalApp;
import main.ui.components.buttons.ToolbarIconButton;
import main.ui.components.icons.ImageIconRenderer;
import main.ui.components.scrollbar.AeroScrollBarUI;
import main.ui.features.splash.SettingsSaveSplash;
import main.ui.scaling.UIScalingManager;
import main.ui.theme.Theme;
import main.ui.theme.ThemePalette;
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
        sectionModel.addElement("Storage");
        sectionModel.addElement("Security");
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
        GeneralSettingsPage general = new GeneralSettingsPage();
        addPage("General", general);

        // Appearance (background, density, accent, widgets)
        AppearanceSettingsPage appearance = new AppearanceSettingsPage();
        addPage("Appearance", appearance);

        // Drawing page implemented but intentionally hidden from settings UI

        StorageSettingsPage storagePage = new StorageSettingsPage();
        addPage("Storage", storagePage);

        // Security (lock & password)
        SecuritySettingsPage security = new SecuritySettingsPage();
        addPage("Security", security);

        // About (comprehensive information page)
        AboutSettingsPage aboutPage = new AboutSettingsPage();
        addPage("About", aboutPage);

        cardsPanel.setOpaque(true);
        cardsPanel.setBackground(Color.WHITE);
        add(cardsPanel, BorderLayout.CENTER);
        cardLayout.show(cardsPanel, "General");
    }

    private JPanel buildSouthBar(){
        JPanel chrome = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        chrome.setOpaque(true);
        chrome.setBackground(Color.WHITE);
        // Icon buttons for Cancel/Save using centralized icon renderer
        ToolbarIconButton cancel = new ToolbarIconButton("back");
        cancel.setToolTipText("Cancel");
        cancel.addActionListener(e-> app.switchCard(JournalApp.MAIN_MENU));
        ToolbarIconButton save = new ToolbarIconButton("save");
        save.setToolTipText("Save Settings");
        save.addActionListener(e-> saveAll());
        chrome.add(cancel);
        chrome.add(save);
        return chrome;
    }

    private void addPage(String name, SettingsPage page){
        pages.put(name, page);
        // Wrap page in a scroll pane so content can scroll if it exceeds visible area
        JScrollPane scrollPane = new JScrollPane(page.getComponent());
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUI(new AeroScrollBarUI());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        cardsPanel.add(scrollPane, name);
    }

    private void saveAll(){
        final SettingsSaveSplash splash = new SettingsSaveSplash();
        splash.setVisible(true);

        new javax.swing.SwingWorker<Void, String>() {
            @Override protected Void doInBackground() {
                publish("Applying settings…");
                try { pages.values().forEach(SettingsPage::apply); } catch (Throwable ignored) {}

                publish("Writing preferences to disk…");
                // Robust save: attempt multiple times if needed
                SettingsStore store = SettingsStore.get();
                boolean saved = false;
                for (int attempt = 1; attempt <= 3 && !saved; attempt++) {
                    try {
                        store.save();
                        // Verify the save by forcing a sync
                        Thread.sleep(50);
                        saved = true;
                    } catch (Throwable ex) {
                        if (attempt == 3) {
                            System.err.println("Failed to save settings after 3 attempts: " + ex.getMessage());
                        }
                        try { Thread.sleep(100); } catch (InterruptedException ie) { break; }
                    }
                }

                publish("Updating look & feel…");
                try {
                    javax.swing.SwingUtilities.invokeAndWait(() -> {
                        try { ThemePalette.refresh(); } catch (Throwable ignored) {}
                        try { UIScalingManager.updateScale(Theme.densityToScale(store.getLayoutDensity())); } catch (Throwable ignored) {}
                        try { javax.swing.SwingUtilities.updateComponentTreeUI(app); } catch (Throwable ignored) {}
                    });
                } catch (Throwable ignored) {}

                publish("Refreshing UI…");
                try {
                    javax.swing.SwingUtilities.invokeAndWait(() -> {
                        try { app.recreateMainMenuPanel(); } catch (Throwable ignored) {}
                        try { app.updateWidgetPanelVisibility(); } catch (Throwable ignored) {}
                    });
                } catch (Throwable ignored) {}

                publish("Finalizing…");
                // Final sync to ensure all writes are flushed
                try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                return null;
            }
            @Override protected void process(java.util.List<String> chunks) {
                if (!chunks.isEmpty()) splash.setStatus(chunks.get(chunks.size()-1));
            }
            @Override protected void done() {
                splash.fadeOutAndDispose(() -> app.restartAfterSettingsChange());
            }
        }.execute();
    }

    // ---------- Page types ---------- //

    // --- Modern sidebar renderer with icons ----
    private static class SidebarCellRenderer extends JPanel implements ListCellRenderer<String>{
        private final JLabel lbl = new JLabel();
        private boolean selected;
        private static final Map<String, Icon> SECTION_ICONS = new HashMap<>();
        
        static {
            // Load icons for each settings section
            SECTION_ICONS.put("General", loadSectionIcon("general_settings"));
            SECTION_ICONS.put("Appearance", loadSectionIcon("appearance_settings"));
            SECTION_ICONS.put("Storage", loadSectionIcon("storage_settings"));
            SECTION_ICONS.put("Security", loadSectionIcon("security"));
            SECTION_ICONS.put("About", loadSectionIcon("about_settings"));
        }

        private static Icon loadSectionIcon(String id) {
            String res = ImageIconRenderer.mapIdToResource(id);
            return res != null ? ImageIconRenderer.icon(res, 18, false) : null;
        }
        
        SidebarCellRenderer(){
            setLayout(new FlowLayout(FlowLayout.LEFT,8,8));
            setOpaque(false);
            lbl.setFont(AeroTheme.defaultFont().deriveFont(14f));
            lbl.setIconTextGap(8);
            add(lbl);
        }
        @Override public Component getListCellRendererComponent(JList<? extends String> list, String value,int idx,boolean sel,boolean focus){
            this.selected = sel;
            lbl.setText(value);
            // Set icon for each category
            Icon icon = SECTION_ICONS.get(value);
            lbl.setIcon(icon);
            lbl.setForeground(AeroTheme.TEXT_PRIMARY);
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

}
