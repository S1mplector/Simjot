/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.app;

import java.awt.CardLayout;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Manages navigation between application panels using CardLayout.
 * Provides centralized card management with optional fade transitions.
 */
public class AppNavigation {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CARD IDENTIFIERS (constants for navigation targets)
    // ═══════════════════════════════════════════════════════════════════════════
    
    public static final String MAIN_MENU = "Main Menu";
    public static final String NEW_ENTRY = "New Entry";
    public static final String MOOD_CHART = "Mood Chart";
    public static final String NEW_POEM = "New Poem";
    public static final String SETTINGS = "Settings";
    public static final String GALLERY = "Gallery";
    public static final String NOTEBOOK_MANAGER = "Notebook Manager";
    
    // ═══════════════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════════════
    
    private final CardLayout cardLayout;
    private final JPanel cardPanel;
    private final Map<String, JPanel> cardMap = new HashMap<>();
    private final Set<String> createdCards = new HashSet<>();
    
    private String currentCard = MAIN_MENU;
    private String previousCard = null;
    private boolean firstSwitchDone = false;
    private boolean animationsEnabled = true;
    
    /** Optional transition panel for fade effects */
    private main.ui.animations.transitions.FadeTransitionPanel transitionPanel;
    
    /** Callback for lazy card creation */
    private CardFactory cardFactory;
    
    /**
     * Interface for lazy card creation.
     */
    @FunctionalInterface
    public interface CardFactory {
        /**
         * Create a panel for the given card name.
         * @param cardName The card identifier
         * @return The created panel, or null if not supported
         */
        JPanel createCard(String cardName);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONSTRUCTION
    // ═══════════════════════════════════════════════════════════════════════════
    
    public AppNavigation(CardLayout cardLayout, JPanel cardPanel) {
        this.cardLayout = cardLayout;
        this.cardPanel = cardPanel;
    }
    
    /**
     * Set the card factory for lazy creation.
     */
    public void setCardFactory(CardFactory factory) {
        this.cardFactory = factory;
    }
    
    /**
     * Set the transition panel for fade effects.
     */
    public void setTransitionPanel(main.ui.animations.transitions.FadeTransitionPanel panel) {
        this.transitionPanel = panel;
    }
    
    /**
     * Enable or disable animations.
     */
    public void setAnimationsEnabled(boolean enabled) {
        this.animationsEnabled = enabled;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CARD MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Register a card panel.
     */
    public void addCard(String name, JPanel panel) {
        if (name == null || panel == null) return;
        cardMap.put(name, panel);
        createdCards.add(name);
        cardPanel.add(panel, name);
    }
    
    /**
     * Check if a card exists.
     */
    public boolean hasCard(String name) {
        return createdCards.contains(name);
    }
    
    /**
     * Get a card panel by name.
     */
    public JPanel getCard(String name) {
        return cardMap.get(name);
    }
    
    /**
     * Remove a card.
     */
    public void removeCard(String name) {
        JPanel panel = cardMap.remove(name);
        if (panel != null) {
            cardPanel.remove(panel);
            createdCards.remove(name);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // NAVIGATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Navigate to a card with optional animation.
     */
    public void showCard(String cardName) {
        showCard(cardName, animationsEnabled);
    }
    
    /**
     * Navigate to a card.
     * 
     * @param cardName The target card
     * @param animate Whether to use fade animation
     */
    public void showCard(String cardName, boolean animate) {
        if (cardName == null) return;
        
        // Lazy create if needed
        ensureCardExists(cardName);
        
        // Track navigation
        previousCard = currentCard;
        currentCard = cardName;
        
        // Perform switch
        if (animate && firstSwitchDone && transitionPanel != null) {
            transitionPanel.startFadeOut(() -> {
                cardLayout.show(cardPanel, cardName);
                transitionPanel.startFadeIn();
            });
        } else {
            cardLayout.show(cardPanel, cardName);
        }
        
        firstSwitchDone = true;
    }
    
    /**
     * Go back to previous card.
     */
    public void goBack() {
        if (previousCard != null) {
            showCard(previousCard);
        } else {
            showCard(MAIN_MENU);
        }
    }
    
    /**
     * Go to main menu.
     */
    public void goHome() {
        showCard(MAIN_MENU);
    }
    
    /**
     * Get current card name.
     */
    public String getCurrentCard() {
        return currentCard;
    }
    
    /**
     * Get previous card name.
     */
    public String getPreviousCard() {
        return previousCard;
    }
    
    /**
     * Check if currently on main menu.
     */
    public boolean isOnMainMenu() {
        return MAIN_MENU.equals(currentCard);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // LAZY CREATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Ensure a card exists, creating it if necessary.
     */
    private void ensureCardExists(String cardName) {
        if (createdCards.contains(cardName)) return;
        
        if (cardFactory != null) {
            JPanel panel = cardFactory.createCard(cardName);
            if (panel != null) {
                addCard(cardName, panel);
            }
        }
    }
    
    /**
     * Pre-create a set of cards in background.
     */
    public void preloadCards(String... cardNames) {
        for (String name : cardNames) {
            if (!createdCards.contains(name)) {
                SwingUtilities.invokeLater(() -> ensureCardExists(name));
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // NOTEBOOK PANELS (special handling for dynamic notebook cards)
    // ═══════════════════════════════════════════════════════════════════════════
    
    private final Map<String, JPanel> notebookPanels = new HashMap<>();
    
    /**
     * Register a notebook-specific panel.
     */
    public void addNotebookPanel(String notebookId, JPanel panel) {
        String cardName = "Notebook:" + notebookId;
        notebookPanels.put(notebookId, panel);
        addCard(cardName, panel);
    }
    
    /**
     * Navigate to a notebook panel.
     */
    public void showNotebook(String notebookId) {
        showCard("Notebook:" + notebookId);
    }
    
    /**
     * Remove a notebook panel.
     */
    public void removeNotebookPanel(String notebookId) {
        String cardName = "Notebook:" + notebookId;
        notebookPanels.remove(notebookId);
        removeCard(cardName);
    }
    
    /**
     * Get all notebook panel IDs.
     */
    public Set<String> getNotebookIds() {
        return new HashSet<>(notebookPanels.keySet());
    }
    
    /**
     * Check if navigating from a notebook.
     */
    public boolean isLeavingNotebook() {
        return previousCard != null && previousCard.startsWith("Notebook:");
    }
    
    /**
     * Get the notebook ID from a card name.
     */
    public static String extractNotebookId(String cardName) {
        if (cardName != null && cardName.startsWith("Notebook:")) {
            return cardName.substring(9);
        }
        return null;
    }
}
