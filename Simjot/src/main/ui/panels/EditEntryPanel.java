package main.ui.panels;

import java.awt.*;
import java.io.*;
import javax.swing.*;
import main.ui.JournalApp;

public class EditEntryPanel extends NewEntryPanel {
    private File fileToEdit;
    
    public EditEntryPanel(JournalApp app, File fileToEdit, File journalFolder, CardLayout cardLayout, JPanel cardPanel) {
        super(app, journalFolder, cardLayout, cardPanel);
        this.fileToEdit = fileToEdit;
        loadFile();
    }

    private void loadFile() {
        // We want to read from file and fill the text fields and mood
        try (BufferedReader reader = new BufferedReader(new FileReader(fileToEdit))) {
            String title = reader.readLine();
            if (title == null) title = "";
            titleField.setText(title);
            reader.readLine(); // skip blank line

            // The rest is content
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            contentArea.setText(content.toString());

            // We won't strictly load mood from file for simplicity 
            // (unless you wrote it there yourself).
            // But if you do store mood in your file, parse it here.
            
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading journal entry.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Override save to update existing file instead of creating a new one.
    protected void saveEntry() {
        String title = titleField.getText().trim();
        String content = contentArea.getText();
        if (title.isEmpty() && content.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a title or content.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try (PrintWriter writer = new PrintWriter(new FileWriter(fileToEdit))) {
            writer.println(title);
            writer.println();
            writer.println(content);
            JOptionPane.showMessageDialog(this, "Journal entry updated successfully!");
            app.switchCard(JournalApp.VIEW_ENTRIES);
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error saving journal entry.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
