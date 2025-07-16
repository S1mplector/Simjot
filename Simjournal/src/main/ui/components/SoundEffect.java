package main.ui.components;

import java.io.File;
import java.io.IOException;
import javax.sound.sampled.*;

public class SoundEffect {
    public static void playSound(String path) {
        try {
            File soundFile = new File(path);  // Adjust the path if necessary.
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(soundFile);
            Clip clip = AudioSystem.getClip();
            clip.open(audioStream);
            clip.start();
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException ex) {
            ex.printStackTrace();
        }
    }
}
