package main.ui.panels;

import java.awt.*;
import java.io.*;
import javax.swing.*;
import main.ui.JournalApp;

public class EditPoemPanel extends PoemPanel {
    private File poemFile;

    public EditPoemPanel(JournalApp app, File poemFile, File journalFolder, CardLayout cardLayout, JPanel cardPanel) {
        super(app, journalFolder, cardLayout, cardPanel);
        this.poemFile = poemFile;
        loadPoemFile();
    }

    private void loadPoemFile() {
        try (BufferedReader reader = new BufferedReader(new FileReader(poemFile))) {
            String title = reader.readLine();
            if (title == null) title = "";
            poemTitleField.setText(title);
            reader.readLine(); // skip blank line

            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            poemTextArea.setText(content.toString());
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading poem.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    protected void savePoem() {
        String title = poemTitleField.getText().trim();
        String content = poemTextArea.getText().trim();
        if (title.isEmpty() && content.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a title or some content for your poem.", 
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try (PrintWriter writer = new PrintWriter(new FileWriter(poemFile))) {
            writer.println(title);
            writer.println();
            writer.println(content);
            JOptionPane.showMessageDialog(this, "Poem updated successfully!");
            app.switchCard(JournalApp.VIEW_ENTRIES);
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error saving poem.", 
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
