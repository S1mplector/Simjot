package main.dialog;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import main.ui.buttons.RoundedButton;
import main.ui.panels.RoundedPanel;

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
        RoundedPanel container = new RoundedPanel(15);
        container.setBackground(new Color(250,250,250));
        container.setLayout(new BorderLayout(10,10));
        container.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        // build pages – refreshed for the latest main-menu layout
        addPage("Welcome", "Welcome to Simjot!\nLet's walk through the essentials.");
        addPage("Notebooks", "Organise life with multiple notebooks.\nCreate, rename or delete notebooks and watch them pop into the shelf.");
        addPage("Journal", "Your daily journal is here.\nWrite entries, add photos, and track your moods with the mood slider.");
        addPage("Poetry", "Express yourself with poetry.\nCreate, edit and delete poems, and re-visit them whenever you like");
        addPage("Canvas", "Tap <b>Canvas</b> to sketch ideas on an infinite sheet.\nPens, eraser and colour picker give you creative freedom.");
        addPage("Mood Chart", "All those slider moods converge here.\nSee how your feelings trend over the last 7/30 days or overall.");
        addPage("Settings & Themes", "Personalise Simjot: change wallpapers, tweak UI sizes and more in <b>Settings</b>.");
        addPage("That's it!", "Enjoy Simjot and happy writing.");

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
        setSize(380, 260);
        setAlwaysOnTop(true);
        setLocationRelativeTo(getOwner());
    }

    private void addPage(String title, String body) {
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
        page.add(Box.createVerticalGlue());

        String name = "page" + cardNames.size();
        cardNames.add(name);
        cardPanel.add(page, name);
    }
} 