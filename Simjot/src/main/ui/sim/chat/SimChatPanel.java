/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.ui.sim.chat;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import main.core.sim.api.SimEventBus;
import main.ui.sim.overlay.ChatTranscriptModel;
import main.ui.sim.overlay.ChatViewPanel;

/**
 * Fullscreen companion chat panel for Sim.
 * Keeps a local transcript and streams assistant messages via SimEventBus.
 */
public final class SimChatPanel extends JPanel implements SimEventBus.Listener {
    private final ChatTranscriptModel transcript = new ChatTranscriptModel();
    private final ChatViewPanel chatView = new ChatViewPanel(transcript);
    private final JTextArea inputArea = new JTextArea();
    private final JLabel statusLabel = new JLabel("Sim is ready.");
    private final Runnable onBack;
    private boolean active = false;
    private boolean subscribed = false;

    public SimChatPanel(Runnable onBack) {
        super(new BorderLayout());
        this.onBack = onBack;
        setOpaque(true);
        setBackground(new Color(236, 240, 245));
        add(createHeader(), BorderLayout.NORTH);
        add(createBody(), BorderLayout.CENTER);
        add(createComposer(), BorderLayout.SOUTH);
    }

    public void onPanelShown() {
        if (!subscribed) {
            try {
                SimEventBus.get().addListener(this);
                subscribed = true;
            } catch (Throwable ignored) {}
        }
        active = true;
        statusLabel.setText("Sim is ready.");
        SwingUtilities.invokeLater(() -> inputArea.requestFocusInWindow());
    }

    public void onPanelHidden() {
        if (!active) return;
        active = false;
        statusLabel.setText("Chat paused.");
        try { SimEventBus.get().emitChatEnded(); } catch (Throwable ignored) {}
    }

    public void disposePanel() {
        onPanelHidden();
        if (subscribed) {
            try {
                SimEventBus.get().removeListener(this);
            } catch (Throwable ignored) {}
            subscribed = false;
        }
    }

    private JComponent createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(18, 22, 14, 22));

        JPanel titleWrap = new JPanel(new BorderLayout());
        titleWrap.setOpaque(false);
        JLabel title = new JLabel("Sim Chat");
        title.setFont(new Font("SansSerif", Font.BOLD, 24));
        title.setForeground(new Color(18, 26, 38));
        JLabel subtitle = new JLabel("Ask anything. Sim responds with local context awareness.");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subtitle.setForeground(new Color(58, 70, 86));
        titleWrap.add(title, BorderLayout.NORTH);
        titleWrap.add(subtitle, BorderLayout.SOUTH);

        JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        JButton newChatButton = createHeaderButton("New Chat", this::startNewChat);
        JButton backButton = createHeaderButton("Back", this::goBack);
        actions.add(newChatButton);
        actions.add(backButton);

        header.add(titleWrap, BorderLayout.WEST);
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private JComponent createBody() {
        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 20));

        JPanel transcriptFrame = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(255, 255, 255, 180));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
                g2.setColor(new Color(150, 165, 184, 122));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 24, 24);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        transcriptFrame.setOpaque(false);
        transcriptFrame.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        JScrollPane scroll = (JScrollPane) chatView.getScrollPane();
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(new Color(0, 0, 0, 0));

        transcriptFrame.add(scroll, BorderLayout.CENTER);
        body.add(transcriptFrame, BorderLayout.CENTER);
        return body;
    }

    private JComponent createComposer() {
        JPanel composer = new JPanel(new GridBagLayout());
        composer.setOpaque(false);
        composer.setBorder(BorderFactory.createEmptyBorder(14, 20, 18, 20));

        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setRows(3);
        inputArea.setFont(new Font("SansSerif", Font.PLAIN, 14));
        inputArea.setForeground(new Color(26, 33, 44));
        inputArea.setBackground(new Color(255, 255, 255, 210));
        inputArea.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        inputArea.setCaretColor(new Color(31, 90, 156));
        inputArea.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "send");
        inputArea.getInputMap().put(KeyStroke.getKeyStroke("shift ENTER"), "newline");
        inputArea.getActionMap().put("send", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                sendCurrentMessage();
            }
        });
        inputArea.getActionMap().put("newline", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                inputArea.append("\n");
            }
        });

        JButton sendButton = createHeaderButton("Send", this::sendCurrentMessage);
        sendButton.setPreferredSize(new Dimension(88, 34));

        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLabel.setForeground(new Color(68, 82, 98));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 0, 10);
        composer.add(inputArea, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(0, 0, 0, 0);
        composer.add(sendButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 2, 0, 0);
        composer.add(statusLabel, gbc);

        return composer;
    }

    private JButton createHeaderButton(String text, Runnable action) {
        JButton b = new JButton(text);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(148, 164, 183, 148), 1, true),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));
        b.setBackground(new Color(245, 249, 255));
        b.setForeground(new Color(24, 34, 48));
        b.setFont(new Font("SansSerif", Font.BOLD, 12));
        b.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        b.addActionListener(e -> {
            try { action.run(); } catch (Throwable ignored) {}
        });
        return b;
    }

    private void sendCurrentMessage() {
        if (!active) return;
        String text = inputArea.getText();
        if (text == null) text = "";
        text = text.strip();
        if (text.isEmpty()) return;
        transcript.appendUser(text);
        inputArea.setText("");
        statusLabel.setText("Waiting for Sim...");
        chatView.scrollToBottom();
        try { SimEventBus.get().emitChatMessage(text); } catch (Throwable ignored) {}
    }

    private void startNewChat() {
        transcript.clear();
        statusLabel.setText("New chat started.");
        try { SimEventBus.get().emitChatEnded(); } catch (Throwable ignored) {}
        SwingUtilities.invokeLater(() -> inputArea.requestFocusInWindow());
    }

    private void goBack() {
        if (onBack != null) {
            try { onBack.run(); } catch (Throwable ignored) {}
        }
    }

    @Override
    public void onSpeakStart() {
        if (!active) return;
        SwingUtilities.invokeLater(() -> {
            transcript.beginAssistantTurn();
            statusLabel.setText("Sim is typing...");
        });
    }

    @Override
    public void onSpeak(String message) {
        if (!active) return;
        String t = message == null ? "" : message;
        if (t.isBlank()) return;
        SwingUtilities.invokeLater(() -> {
            transcript.appendAssistantTokens(t);
            chatView.scrollToBottom();
        });
    }

    @Override
    public void onSpeakEnd() {
        if (!active) return;
        SwingUtilities.invokeLater(() -> {
            transcript.endAssistantTurn();
            statusLabel.setText("Sim is ready.");
            chatView.scrollToBottom();
        });
    }
}
