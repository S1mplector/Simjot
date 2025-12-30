package main.core.security.crypto;

/**
 * Configuration options for the Simjot encryption engine.
 * Provides tunable parameters for security/performance tradeoffs.
 * 
 * <h2>Security Levels</h2>
 * <ul>
 *   <li><b>STANDARD</b> - Good security, fast performance (100,000 PBKDF2 iterations)</li>
 *   <li><b>HIGH</b> - Enhanced security, moderate performance (250,000 iterations)</li>
 *   <li><b>MAXIMUM</b> - Maximum security, slower performance (500,000 iterations)</li>
 * </ul>
 * 
 * @since 1.0
 */
public final class CryptoConfig {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SIMJOT PROPRIETARY FORMAT CONSTANTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Magic bytes identifying a Simjot encrypted file.
     * ASCII: "SJOT" + version nibble + format nibble
     */
    public static final byte[] MAGIC_BYTES = { 
        (byte) 0x53,  // 'S'
        (byte) 0x4A,  // 'J'
        (byte) 0x4F,  // 'O'
        (byte) 0x54,  // 'T'
        (byte) 0x01,  // Format version (major)
        (byte) 0x00   // Format version (minor)
    };
    
    /**
     * Current format version for compatibility checking.
     */
    public static final int FORMAT_VERSION_MAJOR = 1;
    public static final int FORMAT_VERSION_MINOR = 0;
    
    /**
     * File extension for Simjot encrypted files.
     */
    public static final String ENCRYPTED_EXTENSION = ".sjcrypt";
    
    /**
     * File extension for encrypted backup archives.
     */
    public static final String ENCRYPTED_BACKUP_EXTENSION = ".sjbackup";
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CRYPTOGRAPHIC PARAMETERS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Encryption algorithm: AES in GCM mode for authenticated encryption.
     * GCM provides both confidentiality and integrity in a single pass.
     */
    public static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    
    /**
     * Key derivation algorithm: PBKDF2 with HMAC-SHA256.
     */
    public static final String KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256";
    
    /**
     * AES key size in bits (256-bit for maximum security).
     */
    public static final int KEY_SIZE_BITS = 256;
    
    /**
     * GCM authentication tag length in bits.
     */
    public static final int GCM_TAG_LENGTH_BITS = 128;
    
    /**
     * Initialization vector (nonce) size for GCM mode in bytes.
     * 12 bytes (96 bits) is the recommended size for GCM.
     */
    public static final int IV_SIZE_BYTES = 12;
    
    /**
     * Salt size for key derivation in bytes.
     * 32 bytes provides 256 bits of entropy.
     */
    public static final int SALT_SIZE_BYTES = 32;
    
    /**
     * Header signature for integrity verification.
     * HMAC-SHA256 of the header contents.
     */
    public static final int HEADER_SIGNATURE_BYTES = 32;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SECURITY LEVELS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Security level enumeration with associated PBKDF2 iteration counts.
     */
    public enum SecurityLevel {
        /**
         * Standard security: 100,000 iterations.
         * Suitable for general use with good performance.
         */
        STANDARD(100_000, "Standard"),
        
        /**
         * High security: 250,000 iterations.
         * Recommended for sensitive content.
         */
        HIGH(250_000, "High"),
        
        /**
         * Maximum security: 500,000 iterations.
         * For highly sensitive data, slower key derivation.
         */
        MAXIMUM(500_000, "Maximum");
        
        private final int iterations;
        private final String displayName;
        
        SecurityLevel(int iterations, String displayName) {
            this.iterations = iterations;
            this.displayName = displayName;
        }
        
        public int getIterations() {
            return iterations;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        /**
         * Gets the byte marker for serialization.
         */
        public byte getMarker() {
            return (byte) ordinal();
        }
        
        /**
         * Resolves a SecurityLevel from its byte marker.
         */
        public static SecurityLevel fromMarker(byte marker) {
            int index = marker & 0xFF;
            SecurityLevel[] values = values();
            return index < values.length ? values[index] : STANDARD;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // INSTANCE CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    private SecurityLevel securityLevel;
    private boolean compressBeforeEncrypt;
    private boolean includeMetadata;
    private String customIdentifier;
    
    /**
     * Creates a new configuration with default settings.
     * Default: STANDARD security, compression enabled, metadata included.
     */
    public CryptoConfig() {
        this.securityLevel = SecurityLevel.STANDARD;
        this.compressBeforeEncrypt = true;
        this.includeMetadata = true;
        this.customIdentifier = null;
    }
    
    /**
     * Creates a configuration with specified security level.
     */
    public CryptoConfig(SecurityLevel level) {
        this();
        this.securityLevel = level;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // BUILDER METHODS (Fluent API)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Sets the security level.
     * @return this config for chaining
     */
    public CryptoConfig withSecurityLevel(SecurityLevel level) {
        this.securityLevel = level;
        return this;
    }
    
    /**
     * Enables or disables compression before encryption.
     * Compression can reduce file size but may leak information about content patterns.
     * @return this config for chaining
     */
    public CryptoConfig withCompression(boolean compress) {
        this.compressBeforeEncrypt = compress;
        return this;
    }
    
    /**
     * Enables or disables metadata inclusion in the encrypted header.
     * Metadata includes timestamps and content type hints.
     * @return this config for chaining
     */
    public CryptoConfig withMetadata(boolean include) {
        this.includeMetadata = include;
        return this;
    }
    
    /**
     * Sets a custom identifier embedded in the encrypted file.
     * Can be used for organizational purposes or content identification.
     * @return this config for chaining
     */
    public CryptoConfig withIdentifier(String identifier) {
        this.customIdentifier = identifier;
        return this;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════════════════
    
    public SecurityLevel getSecurityLevel() {
        return securityLevel;
    }
    
    public int getIterations() {
        return securityLevel.getIterations();
    }
    
    public boolean isCompressBeforeEncrypt() {
        return compressBeforeEncrypt;
    }
    
    public boolean isIncludeMetadata() {
        return includeMetadata;
    }
    
    public String getCustomIdentifier() {
        return customIdentifier;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // PRESET CONFIGURATIONS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Configuration preset for journal entries.
     * Standard security with compression and metadata.
     */
    public static CryptoConfig forEntries() {
        return new CryptoConfig(SecurityLevel.STANDARD)
                .withCompression(true)
                .withMetadata(true);
    }
    
    /**
     * Configuration preset for poems.
     * Standard security with compression and metadata.
     */
    public static CryptoConfig forPoems() {
        return new CryptoConfig(SecurityLevel.STANDARD)
                .withCompression(true)
                .withMetadata(true);
    }
    
    /**
     * Configuration preset for backups.
     * High security with compression, as backups contain aggregated data.
     */
    public static CryptoConfig forBackups() {
        return new CryptoConfig(SecurityLevel.HIGH)
                .withCompression(true)
                .withMetadata(true);
    }
    
    /**
     * Configuration preset for maximum security.
     * Disables metadata to minimize information leakage.
     */
    public static CryptoConfig forMaximumSecurity() {
        return new CryptoConfig(SecurityLevel.MAXIMUM)
                .withCompression(false)  // Compression can leak patterns
                .withMetadata(false);    // Minimize metadata
    }
}
