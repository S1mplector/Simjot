package main.ui.features.settings;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import javax.swing.*;
import main.core.service.SettingsStore;
import main.ui.app.JournalApp;
import main.ui.components.buttons.ToolbarIconButton;
import main.ui.components.scrollbar.AeroScrollBarUI;
import main.ui.dialog.message.CustomMessageDialog;
import main.ui.theme.aero.AeroPainters;
import main.ui.theme.aero.AeroTheme;
import main.ui.theme.aero.AeroLookAndFeel;
import main.ui.features.splash.AeroSplashScreen;

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
        sectionModel.addElement("Sim");
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

        // Appearance (theme, glow, background)
        AppearanceSettingsPage appearance = new AppearanceSettingsPage();
        addPage("Appearance", appearance);

        // Drawing page implemented but intentionally hidden from settings UI

        StorageSettingsPage storagePage = new StorageSettingsPage();
        addPage("Storage", storagePage);

        // Security (lock & password)
        SecuritySettingsPage security = new SecuritySettingsPage();
        addPage("Security", security);

        // Sim (AI companion)
        SimSettingsPage simPage = new SimSettingsPage();
        addPage("Sim", simPage);

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
        // Icon buttons for Cancel/Save using centralized PNG renderer
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
        // Add page directly; main area remains plain white
        cardsPanel.add(page.getComponent(), name);
    }

    private void saveAll(){
        final AeroSplashScreen splash = new AeroSplashScreen();
        splash.setStatus("Saving settings…");
        splash.setVisible(true);

        final boolean wasSimEnabled;
        try { wasSimEnabled = main.core.sim.prefs.SimSettings.get().isEnabled(); } catch (Throwable t) { throw new RuntimeException(t); }

        new javax.swing.SwingWorker<Void, String>() {
            private boolean nowSimEnabled = false;
            @Override protected Void doInBackground() {
                publish("Saving preferences…");
                try { pages.values().forEach(SettingsPage::apply); } catch (Throwable ignored) {}
                try { SettingsStore.get().save(); } catch (Throwable ignored) {}

                publish("Updating look & feel…");
                try {
                    javax.swing.SwingUtilities.invokeAndWait(() -> {
                        try { AeroLookAndFeel.apply(); } catch (Throwable ignored) {}
                        try { javax.swing.SwingUtilities.updateComponentTreeUI(app); } catch (Throwable ignored) {}
                    });
                } catch (Throwable ignored) {}

                publish("Applying features…");
                try { nowSimEnabled = main.core.sim.prefs.SimSettings.get().isEnabled(); } catch (Throwable ignored) {}
                if (wasSimEnabled != nowSimEnabled) {
                    try {
                        javax.swing.SwingUtilities.invokeAndWait(() -> {
                            try { if (nowSimEnabled) app.enableSimFeatures(); else app.disableSimFeatures(); } catch (Throwable ignored) {}
                        });
                    } catch (Throwable ignored) {}
                }

                publish("Refreshing UI…");
                try {
                    javax.swing.SwingUtilities.invokeAndWait(() -> {
                        try { app.recreateMainMenuPanel(); } catch (Throwable ignored) {}
                        try { app.updateWidgetPanelVisibility(); } catch (Throwable ignored) {}
                    });
                } catch (Throwable ignored) {}
                return null;
            }
            @Override protected void process(java.util.List<String> chunks) {
                if (!chunks.isEmpty()) splash.setStatus(chunks.get(chunks.size()-1));
            }
            @Override protected void done() {
                splash.fadeOutAndDispose(() -> {
                    try { CustomMessageDialog.display(SettingsPanel.this, "Success", "Settings saved.", false); } catch (Throwable ignored) {}
                });
            }
        }.execute();
    }

    // ---------- Page types ---------- //

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
            // No category icons; render text-only
            lbl.setIcon(null);
            lbl.setIconTextGap(0);
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

}
