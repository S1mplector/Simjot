/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.core.model;

/**
 * Simple data structure representing a section with a title and associated tasks.
 * 
 * <p>This class is used to organize content within journal entries or other
 * structured documents. Each section contains a title and a list of task
 * strings that can be displayed or processed by the UI.</p>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * SectionData section = new SectionData("Daily Goals");
 * section.tasks.add("Morning meditation");
 * section.tasks.add("Complete project proposal");
 * }</pre>
 * 
 * @author S1mplector
 * @since 1.0.0
 */
class SectionData {
    
    /**
     * The title or heading of this section.
     * This is displayed as the section header in the UI.
     */
    String sectionTitle;
    
    /**
     * List of task strings belonging to this section.
     * Each string represents a single task or checklist item.
     * The list is initialized as empty to avoid NullPointerException.
     */
    java.util.List<String> tasks = new java.util.ArrayList<>();
    
    /**
     * Creates a new SectionData with the specified title.
     * 
     * @param sectionTitle The title for this section. Cannot be null.
     * @throws IllegalArgumentException if sectionTitle is null
     */
    public SectionData(String sectionTitle) {
        this.sectionTitle = sectionTitle;
    }
}
