package main.ui.panels;

import java.awt.*;
import java.io.*;
import javax.swing.*;
import main.dialog.CustomMessageDialog;
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
            new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this), "Error", "Error loading poem.", true).showDialog();
        }
    }

    protected void savePoem() {
        String title = poemTitleField.getText().trim();
        String content = poemTextArea.getText().trim();
        if (title.isEmpty() && content.isEmpty()) {
            new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this), "Error", "Please enter a title or some content for your poem.", true).showDialog();
            return;
        }
        try (PrintWriter writer = new PrintWriter(new FileWriter(poemFile))) {
            writer.println(title);
            writer.println();
            writer.println(content);
            new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this), "Success", "Poem updated successfully!", false).showDialog();
            // Stay in the current panel - don't navigate away
        } catch (IOException ex) {
            ex.printStackTrace();
            new CustomMessageDialog((Frame) SwingUtilities.getWindowAncestor(this), "Error", "Error saving poem.", true).showDialog();
        }
    }
}
