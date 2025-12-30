/*
 * SIMJOT CRYPTOGRAPHIC ENGINE - PROPRIETARY
 * 
 * Copyright (c) 2024 Simjot / S1mplector. All Rights Reserved.
 * 
 * This source code is licensed under the Simjot Cryptographic Engine License.
 * You may inspect this code for educational and security research purposes only.
 * Use, modification, or incorporation into other projects is strictly prohibited.
 * 
 * See LICENSE file in this package for full terms.
 */
package main.core.security.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Represents the header of a Simjot encrypted file.
 * 
 * <h2>Simjot Encrypted File Format (SJCRYPT v1.0)</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                     HEADER (Variable Length)                    │
 * ├─────────────────────────────────────────────────────────────────┤
 * │ Bytes 0-5:    Magic bytes "SJOT" + version (6 bytes)           │
 * │ Byte 6:       Content type marker (1 byte)                      │
 * │ Byte 7:       Security level marker (1 byte)                    │
 * │ Byte 8:       Flags byte (compression, metadata, etc.)          │
 * │ Bytes 9-12:   Header length (4 bytes, big-endian)               │
 * │ Bytes 13-44:  Salt (32 bytes)                                   │
 * │ Bytes 45-56:  IV/Nonce (12 bytes)                               │
 * │ Bytes 57-64:  Original size (8 bytes, big-endian)               │
 * │ Bytes 65-72:  Encrypted size (8 bytes, big-endian)              │
 * │ Bytes 73-80:  Timestamp (8 bytes, epoch millis)                 │
 * │ Bytes 81-82:  Identifier length (2 bytes, big-endian)           │
 * │ Bytes 83-N:   Identifier (variable, UTF-8)                      │
 * │ Last 32:      Header HMAC-SHA256 signature                      │
 * ├─────────────────────────────────────────────────────────────────┤
 * │                     ENCRYPTED PAYLOAD                           │
 * │              (AES-256-GCM encrypted content)                    │
 * │         Includes 16-byte GCM authentication tag                 │
 * └─────────────────────────────────────────────────────────────────┘
 * </pre>
 * 
 * <h2>Flags Byte Layout</h2>
 * <pre>
 * Bit 0: Compression enabled (1 = compressed before encryption)
 * Bit 1: Metadata included
 * Bit 2: Reserved
 * Bit 3: Reserved
 * Bit 4-7: Reserved for future use
 * </pre>
 * 
 * @since 1.0
 */
public final class CryptoHeader {
    
    // Flag bit positions
    private static final int FLAG_COMPRESSED = 0;
    private static final int FLAG_HAS_METADATA = 1;
    
    // Minimum header size (without variable-length identifier)
    private static final int MIN_HEADER_SIZE = 83 + CryptoConfig.HEADER_SIGNATURE_BYTES;
    
    // Header fields
    private final int versionMajor;
    private final int versionMinor;
    private final ContentType contentType;
    private final CryptoConfig.SecurityLevel securityLevel;
    private final byte flags;
    private final byte[] salt;
    private final byte[] iv;
    private final long originalSize;
    private final long encryptedSize;
    private final long timestamp;
    private final String identifier;
    private final byte[] headerSignature;
    
    /**
     * Private constructor - use Builder to create instances.
     */
    private CryptoHeader(Builder builder) {
        this.versionMajor = builder.versionMajor;
        this.versionMinor = builder.versionMinor;
        this.contentType = builder.contentType;
        this.securityLevel = builder.securityLevel;
        this.flags = builder.flags;
        this.salt = builder.salt;
        this.iv = builder.iv;
        this.originalSize = builder.originalSize;
        this.encryptedSize = builder.encryptedSize;
        this.timestamp = builder.timestamp;
        this.identifier = builder.identifier;
        this.headerSignature = builder.headerSignature;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SERIALIZATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Serializes this header to a byte array.
     * Note: The header signature should be computed after serialization
     * of the header content (before signature field).
     * 
     * @return The serialized header bytes
     * @throws IOException if serialization fails
     */
    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        
        // Magic bytes
        dos.write(CryptoConfig.MAGIC_BYTES);
        
        // Content type and security level
        dos.writeByte(contentType.getMarker());
        dos.writeByte(securityLevel.getMarker());
        
        // Flags
        dos.writeByte(flags);
        
        // Calculate and write header length
        byte[] identifierBytes = identifier != null ? 
                identifier.getBytes(StandardCharsets.UTF_8) : new byte[0];
        int headerLength = MIN_HEADER_SIZE + identifierBytes.length;
        dos.writeInt(headerLength);
        
        // Salt and IV
        dos.write(salt);
        dos.write(iv);
        
        // Sizes
        dos.writeLong(originalSize);
        dos.writeLong(encryptedSize);
        
        // Timestamp
        dos.writeLong(timestamp);
        
        // Identifier
        dos.writeShort(identifierBytes.length);
        if (identifierBytes.length > 0) {
            dos.write(identifierBytes);
        }
        
        // Header signature (should be computed externally and set via Builder)
        if (headerSignature != null && headerSignature.length == CryptoConfig.HEADER_SIGNATURE_BYTES) {
            dos.write(headerSignature);
        } else {
            dos.write(new byte[CryptoConfig.HEADER_SIGNATURE_BYTES]);
        }
        
        dos.flush();
        return baos.toByteArray();
    }
    
    /**
     * Serializes the header content (excluding signature) for HMAC computation.
     * 
     * @return Header bytes without the signature field
     * @throws IOException if serialization fails
     */
    public byte[] toBytesForSigning() throws IOException {
        byte[] fullHeader = toBytes();
        return Arrays.copyOf(fullHeader, fullHeader.length - CryptoConfig.HEADER_SIGNATURE_BYTES);
    }
    
    /**
     * Parses a CryptoHeader from a byte array.
     * 
     * @param data The byte array containing the header
     * @return The parsed header
     * @throws IOException if parsing fails or format is invalid
     */
    public static CryptoHeader fromBytes(byte[] data) throws IOException {
        if (data == null || data.length < MIN_HEADER_SIZE) {
            throw new IOException("Invalid header: insufficient data");
        }
        
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
        
        // Verify magic bytes
        byte[] magic = new byte[CryptoConfig.MAGIC_BYTES.length];
        dis.readFully(magic);
        if (!Arrays.equals(magic, CryptoConfig.MAGIC_BYTES)) {
            throw new IOException("Invalid header: magic bytes mismatch - not a Simjot encrypted file");
        }
        
        // Read content type and security level
        ContentType contentType = ContentType.fromMarker(dis.readByte());
        CryptoConfig.SecurityLevel securityLevel = CryptoConfig.SecurityLevel.fromMarker(dis.readByte());
        
        // Flags
        byte flags = dis.readByte();
        
        // Header length
        int headerLength = dis.readInt();
        if (data.length < headerLength) {
            throw new IOException("Invalid header: declared length exceeds data");
        }
        
        // Salt
        byte[] salt = new byte[CryptoConfig.SALT_SIZE_BYTES];
        dis.readFully(salt);
        
        // IV
        byte[] iv = new byte[CryptoConfig.IV_SIZE_BYTES];
        dis.readFully(iv);
        
        // Sizes
        long originalSize = dis.readLong();
        long encryptedSize = dis.readLong();
        
        // Timestamp
        long timestamp = dis.readLong();
        
        // Identifier
        int identifierLength = dis.readUnsignedShort();
        String identifier = null;
        if (identifierLength > 0) {
            byte[] identifierBytes = new byte[identifierLength];
            dis.readFully(identifierBytes);
            identifier = new String(identifierBytes, StandardCharsets.UTF_8);
        }
        
        // Header signature
        byte[] headerSignature = new byte[CryptoConfig.HEADER_SIGNATURE_BYTES];
        dis.readFully(headerSignature);
        
        return new Builder()
                .versionMajor(magic[4] & 0xFF)
                .versionMinor(magic[5] & 0xFF)
                .contentType(contentType)
                .securityLevel(securityLevel)
                .flags(flags)
                .salt(salt)
                .iv(iv)
                .originalSize(originalSize)
                .encryptedSize(encryptedSize)
                .timestamp(timestamp)
                .identifier(identifier)
                .headerSignature(headerSignature)
                .build();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════════════════
    
    public int getVersionMajor() { return versionMajor; }
    public int getVersionMinor() { return versionMinor; }
    public String getVersionString() { return versionMajor + "." + versionMinor; }
    public ContentType getContentType() { return contentType; }
    public CryptoConfig.SecurityLevel getSecurityLevel() { return securityLevel; }
    public byte[] getSalt() { return salt.clone(); }
    public byte[] getIv() { return iv.clone(); }
    public long getOriginalSize() { return originalSize; }
    public long getEncryptedSize() { return encryptedSize; }
    public long getTimestamp() { return timestamp; }
    public String getIdentifier() { return identifier; }
    public byte[] getHeaderSignature() { return headerSignature != null ? headerSignature.clone() : null; }
    
    public boolean isCompressed() {
        return (flags & (1 << FLAG_COMPRESSED)) != 0;
    }
    
    public boolean hasMetadata() {
        return (flags & (1 << FLAG_HAS_METADATA)) != 0;
    }
    
    /**
     * Gets the total header size in bytes.
     */
    public int getHeaderSize() {
        int identifierLength = identifier != null ? 
                identifier.getBytes(StandardCharsets.UTF_8).length : 0;
        return MIN_HEADER_SIZE + identifierLength;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // BUILDER
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Builder for creating CryptoHeader instances.
     */
    public static class Builder {
        private int versionMajor = CryptoConfig.FORMAT_VERSION_MAJOR;
        private int versionMinor = CryptoConfig.FORMAT_VERSION_MINOR;
        private ContentType contentType = ContentType.BINARY;
        private CryptoConfig.SecurityLevel securityLevel = CryptoConfig.SecurityLevel.STANDARD;
        private byte flags = 0;
        private byte[] salt = new byte[CryptoConfig.SALT_SIZE_BYTES];
        private byte[] iv = new byte[CryptoConfig.IV_SIZE_BYTES];
        private long originalSize = 0;
        private long encryptedSize = 0;
        private long timestamp = System.currentTimeMillis();
        private String identifier = null;
        private byte[] headerSignature = null;
        
        public Builder() {}
        
        public Builder versionMajor(int major) {
            this.versionMajor = major;
            return this;
        }
        
        public Builder versionMinor(int minor) {
            this.versionMinor = minor;
            return this;
        }
        
        public Builder contentType(ContentType type) {
            this.contentType = type;
            return this;
        }
        
        public Builder securityLevel(CryptoConfig.SecurityLevel level) {
            this.securityLevel = level;
            return this;
        }
        
        public Builder flags(byte flags) {
            this.flags = flags;
            return this;
        }
        
        public Builder compressed(boolean compressed) {
            if (compressed) {
                this.flags |= (1 << FLAG_COMPRESSED);
            } else {
                this.flags &= ~(1 << FLAG_COMPRESSED);
            }
            return this;
        }
        
        public Builder hasMetadata(boolean hasMetadata) {
            if (hasMetadata) {
                this.flags |= (1 << FLAG_HAS_METADATA);
            } else {
                this.flags &= ~(1 << FLAG_HAS_METADATA);
            }
            return this;
        }
        
        public Builder salt(byte[] salt) {
            if (salt != null && salt.length == CryptoConfig.SALT_SIZE_BYTES) {
                this.salt = salt.clone();
            }
            return this;
        }
        
        public Builder iv(byte[] iv) {
            if (iv != null && iv.length == CryptoConfig.IV_SIZE_BYTES) {
                this.iv = iv.clone();
            }
            return this;
        }
        
        public Builder originalSize(long size) {
            this.originalSize = size;
            return this;
        }
        
        public Builder encryptedSize(long size) {
            this.encryptedSize = size;
            return this;
        }
        
        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder identifier(String identifier) {
            this.identifier = identifier;
            return this;
        }
        
        public Builder headerSignature(byte[] signature) {
            if (signature != null && signature.length == CryptoConfig.HEADER_SIGNATURE_BYTES) {
                this.headerSignature = signature.clone();
            }
            return this;
        }
        
        /**
         * Applies configuration from a CryptoConfig instance.
         */
        public Builder fromConfig(CryptoConfig config) {
            this.securityLevel = config.getSecurityLevel();
            this.compressed(config.isCompressBeforeEncrypt());
            this.hasMetadata(config.isIncludeMetadata());
            this.identifier = config.getCustomIdentifier();
            return this;
        }
        
        public CryptoHeader build() {
            return new CryptoHeader(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format(
            "CryptoHeader[version=%d.%d, type=%s, security=%s, compressed=%b, " +
            "originalSize=%d, encryptedSize=%d, identifier=%s]",
            versionMajor, versionMinor, contentType.getDisplayName(),
            securityLevel.getDisplayName(), isCompressed(),
            originalSize, encryptedSize, identifier
        );
    }
}
