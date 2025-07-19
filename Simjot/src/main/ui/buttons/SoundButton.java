package main.ui.buttons;

import javax.swing.*;

import main.ui.components.SoundEffect;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class SoundButton extends JButton {
    public SoundButton(String text) {
        super(text);
        init();
    }
    
    private void init() {
        // Add an ActionListener that plays the sound effect every time the button is clicked.
        addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                SoundEffect.playSound("audio/fx.wav");
            }
        });
    }
}
