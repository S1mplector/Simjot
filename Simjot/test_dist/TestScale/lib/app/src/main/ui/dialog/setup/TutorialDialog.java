/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.dialog.setup;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import main.infrastructure.io.ResourceLoader;
import main.ui.components.buttons.RoundedButton;
import main.ui.components.containers.FrostedGlassPanel;

/**
 * A simple multi-page modal dialog that walks the user through the core features
 * of Simjot.  Used only on the very first launch (or when the user opts-in
 * again).
 */
public class TutorialDialog extends JDialog {

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardPanel = new JPanel(cardLayout);
    private int currentIndex = 0;
    private final List<String> cardNames = new ArrayList<>();

    public TutorialDialog(JFrame owner) {
        super(owner, "Simjot – Quick Tour", true);
        initUI();
    }

    private void initUI() {
        setUndecorated(true);
        // Outer rounded frame look
        FrostedGlassPanel container = new FrostedGlassPanel(new BorderLayout(10,10), 15);
        container.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        // build pages – refreshed for the latest main-menu layout with visuals
        addPage("Welcome", "Welcome to Simjot!\nLet's walk through the essentials.", null);
        addPage("Notebooks", "Organize life with multiple notebooks.\nCreate, rename or delete notebooks and watch them pop into the shelf.", "docs/tut_notebooks.png");
        addPage("Notebook Manager", "Organize different notebooks via the notebook manager. \n Create and delete notebooks.", "docs/tut_ntbkmanager.png");
        addPage("Notebook Types", "There are two different type of notebooks, <b>Journal</b> and <b>Poetry</b>.\nJournal is for daily journaling and Poetry is for writing poems.", "docs/tut_ntbktypes.png");
        addPage("Journal Entry Manager", "When in a journal notebook, view, edit and delete journal entries.", "docs/tut_journaling.png");
        addPage("Journaling Editor", "Freely write down your thoughts in the editor and use the toolbar tools to structure your writing.", "docs/tut_jrnleditor.png");
        addPage("Poetry Entry Manager", "The poetry manager is the exact same as the journal entry manager.\nCreate, edit and delete poems, and re-visit them whenever you like", "docs/tut_poetry.png");
        addPage("Poetry Editor", "Let your poem come to life in the editor.", "docs/tut_ptryeditor.png");
        addPage("Poem Text Settings", "Customise your poem's text appearance via the toolbar below the title area.", "docs/tut_poetrytext.png");
        addPage("Help With Writing a Poem", "On the rightmost side of the toolbar, we have a few buttons in the Poetry editor. Let's learn what they do.", "docs/tut_ptrybuttonbundle.png");
        addPage("Metering" , "The metering button will help you write a poem with a specific meter.", "docs/tut_ptrymetering.png");
        addPage("Rhymes", "Click the second from the left button to find rhymes for a word. Click on a word to initiate the rhyme search.\nThis helps you write poems with stronger sound patterns.", "docs/tut_ptryrhymes.png");
        addPage("Mood Chart", "All those slider moods converge here.\nSee how your feelings trend over the last 7/30 days or overall.", "docs/breathing_circle.png");
        addPage("Settings & Themes", "Personalise Simjot: change wallpapers, tweak UI sizes and more in <b>Settings</b>.", "docs/settings_interface.png");
        addPage("That's it!", "Enjoy Simjot and happy writing.", null);

        container.add(cardPanel, BorderLayout.CENTER);

        // nav buttons
        JPanel nav = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        nav.setOpaque(false);
        RoundedButton skipBtn = new RoundedButton("Skip");
        RoundedButton nextBtn = new RoundedButton("Next");

        skipBtn.addActionListener(e -> dispose());
        nextBtn.addActionListener(e -> {
            currentIndex++;
            if(currentIndex >= cardNames.size()) {
                dispose();
            } else {
                cardLayout.show(cardPanel, cardNames.get(currentIndex));
                if(currentIndex == cardNames.size()-1) nextBtn.setText("Finish");
            }
        });

        nav.add(skipBtn);
        nav.add(nextBtn);
        container.add(nav, BorderLayout.SOUTH);

        getContentPane().add(container);
        pack();
        setSize(460, 340);
        setAlwaysOnTop(true);
        setLocationRelativeTo(getOwner());
    }

    private void addPage(String title, String body, String imagePath) {
        JPanel page = new JPanel();
        page.setOpaque(false);
        page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));

        JLabel t = new JLabel(title);
        t.setFont(t.getFont().deriveFont(Font.BOLD, 18f));
        t.setAlignmentX(Component.CENTER_ALIGNMENT);

        String html = "<html><div style='text-align:center;'>"+body+"</div></html>";
        JLabel txt = new JLabel(html, SwingConstants.CENTER);
        txt.setFont(new Font("SansSerif", Font.PLAIN, 14));
        txt.setAlignmentX(Component.CENTER_ALIGNMENT);

        page.add(Box.createVerticalStrut(10));
        page.add(t);
        page.add(Box.createVerticalStrut(10));
        page.add(txt);
        
        if (imagePath != null) {
            Image img = ResourceLoader.createImage("Simjot/" + imagePath);
            if (img != null) {
                // scale to fit width with max height constraint
                int maxW = 400;
                int maxH = 180;
                int iw = img.getWidth(null);
                int ih = img.getHeight(null);
                if (iw > 0 && ih > 0) {
                    double scale = Math.min((double)maxW/iw, (double)maxH/ih);
                    int w = (int)Math.round(iw * scale);
                    int h = (int)Math.round(ih * scale);
                    Image scaled = img.getScaledInstance(w, h, Image.SCALE_SMOOTH);
                    JLabel pic = new JLabel(new ImageIcon(scaled));
                    pic.setAlignmentX(Component.CENTER_ALIGNMENT);
                    pic.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
                    page.add(pic);
                }
            }
        }

        page.add(Box.createVerticalGlue());

        String name = "page" + cardNames.size();
        cardNames.add(name);
        cardPanel.add(page, name);
    }
} 
