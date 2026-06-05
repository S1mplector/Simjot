/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.core.security.crypto;

/**
 * Defines the type of content being encrypted.
 * Each content type may have different handling characteristics
 * and is embedded in the encrypted file header for proper decryption.
 * 
 * @since 1.0
 */
public enum ContentType {
    
    /**
     * Journal entry content (rich text, metadata, attachments reference)
     */
    ENTRY((byte) 0x01, "Journal Entry"),
    
    /**
     * Poem content (poetry text, styling, metadata)
     */
    POEM((byte) 0x02, "Poem"),

    /**
     * Notebook metadata and structure
     */
    NOTEBOOK((byte) 0x08, "Notebook"),
    
    /**
     * Full backup archive (compressed, multi-file)
     */
    BACKUP((byte) 0x03, "Backup Archive"),
    
    /**
     * Settings and preferences data
     */
    SETTINGS((byte) 0x04, "Settings"),
    
    /**
     * Generic binary data
     */
    BINARY((byte) 0x05, "Binary Data"),
    
    /**
     * Plain text content
     */
    TEXT((byte) 0x06, "Plain Text"),
    
    /**
     * Attachment file (image, document, etc.)
     */
    ATTACHMENT((byte) 0x07, "Attachment");
    
    private final byte marker;
    private final String displayName;
    
    ContentType(byte marker, String displayName) {
        this.marker = marker;
        this.displayName = displayName;
    }
    
    /**
     * Gets the single-byte marker for this content type.
     * Used in the encrypted file header.
     */
    public byte getMarker() {
        return marker;
    }
    
    /**
     * Gets a human-readable name for this content type.
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Resolves a ContentType from its byte marker.
     * 
     * @param marker The byte marker to look up
     * @return The matching ContentType, or BINARY if not found
     */
    public static ContentType fromMarker(byte marker) {
        for (ContentType type : values()) {
            if (type.marker == marker) {
                return type;
            }
        }
        return BINARY;
    }
}
