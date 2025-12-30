/**
 * <h1>Simjot Cryptographic Engine v1.0</h1>
 * 
 * <p>This package provides a sophisticated, proprietary encryption system designed
 * exclusively for Simjot. It implements military-grade encryption for protecting
 * journal entries, poems, and backup archives.</p>
 * 
 * <h2>Architecture Overview</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │                         SIMJOT CRYPTO ARCHITECTURE                       │
 * ├─────────────────────────────────────────────────────────────────────────┤
 * │                                                                          │
 * │  ┌──────────────────┐     ┌──────────────────┐     ┌─────────────────┐  │
 * │  │   SimjotCrypto   │────▶│   CryptoConfig   │     │   ContentType   │  │
 * │  │  (Main Engine)   │     │ (Configuration)  │     │     (Enum)      │  │
 * │  └────────┬─────────┘     └──────────────────┘     └─────────────────┘  │
 * │           │                                                              │
 * │           │ uses                                                         │
 * │           ▼                                                              │
 * │  ┌──────────────────┐     ┌──────────────────┐                          │
 * │  │   CryptoHeader   │     │  CryptoException │                          │
 * │  │ (File Format)    │     │   (Errors)       │                          │
 * │  └──────────────────┘     └──────────────────┘                          │
 * │                                                                          │
 * └─────────────────────────────────────────────────────────────────────────┘
 * </pre>
 * 
 * <h2>Cryptographic Specifications</h2>
 * <table border="1">
 *   <tr><th>Component</th><th>Algorithm/Specification</th></tr>
 *   <tr><td>Symmetric Encryption</td><td>AES-256-GCM (Authenticated Encryption)</td></tr>
 *   <tr><td>Key Derivation</td><td>PBKDF2-HMAC-SHA256 (100K-500K iterations)</td></tr>
 *   <tr><td>Salt Size</td><td>256 bits (32 bytes)</td></tr>
 *   <tr><td>IV/Nonce Size</td><td>96 bits (12 bytes) - GCM recommended</td></tr>
 *   <tr><td>Auth Tag Size</td><td>128 bits (16 bytes)</td></tr>
 *   <tr><td>Header Signature</td><td>HMAC-SHA256 (256 bits)</td></tr>
 *   <tr><td>Compression</td><td>DEFLATE (optional)</td></tr>
 * </table>
 * 
 * <h2>Proprietary File Format (.sjcrypt)</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                     SIMJOT ENCRYPTED FILE                       │
 * ├─────────────────────────────────────────────────────────────────┤
 * │ HEADER                                                          │
 * │ ├─ Magic Bytes: "SJOT" (4 bytes)                               │
 * │ ├─ Version: Major.Minor (2 bytes)                              │
 * │ ├─ Content Type (1 byte)                                       │
 * │ ├─ Security Level (1 byte)                                     │
 * │ ├─ Flags (1 byte)                                              │
 * │ ├─ Header Length (4 bytes)                                     │
 * │ ├─ Salt (32 bytes)                                             │
 * │ ├─ IV/Nonce (12 bytes)                                         │
 * │ ├─ Original Size (8 bytes)                                     │
 * │ ├─ Encrypted Size (8 bytes)                                    │
 * │ ├─ Timestamp (8 bytes)                                         │
 * │ ├─ Identifier Length (2 bytes)                                 │
 * │ ├─ Identifier (variable)                                       │
 * │ └─ Header HMAC-SHA256 (32 bytes)                               │
 * ├─────────────────────────────────────────────────────────────────┤
 * │ ENCRYPTED PAYLOAD                                               │
 * │ └─ AES-256-GCM ciphertext + 16-byte auth tag                   │
 * └─────────────────────────────────────────────────────────────────┘
 * </pre>
 * 
 * <h2>Quick Start Examples</h2>
 * 
 * <h3>Basic String Encryption</h3>
 * <pre>{@code
 * SimjotCrypto crypto = new SimjotCrypto();
 * 
 * // Encrypt a journal entry
 * String entry = "Dear diary, today was a wonderful day...";
 * byte[] encrypted = crypto.encrypt(entry, "mySecurePassword", ContentType.ENTRY);
 * 
 * // Decrypt
 * String decrypted = crypto.decryptToString(encrypted, "mySecurePassword");
 * }</pre>
 * 
 * <h3>File Encryption with Custom Security</h3>
 * <pre>{@code
 * SimjotCrypto crypto = new SimjotCrypto();
 * 
 * // Configure high security for sensitive content
 * CryptoConfig config = new CryptoConfig()
 *     .withSecurityLevel(CryptoConfig.SecurityLevel.HIGH)
 *     .withCompression(true)
 *     .withMetadata(true)
 *     .withIdentifier("backup-2024-01");
 * 
 * // Encrypt file
 * crypto.encryptFile(
 *     new File("journal.txt"),
 *     new File("journal.sjcrypt"),
 *     "password",
 *     ContentType.ENTRY,
 *     config
 * );
 * 
 * // Decrypt file
 * crypto.decryptFile(
 *     new File("journal.sjcrypt"),
 *     new File("journal-restored.txt"),
 *     "password"
 * );
 * }</pre>
 * 
 * <h3>Using Preset Configurations</h3>
 * <pre>{@code
 * // Preset for journal entries (standard security)
 * CryptoConfig entryConfig = CryptoConfig.forEntries();
 * 
 * // Preset for poems (standard security)
 * CryptoConfig poemConfig = CryptoConfig.forPoems();
 * 
 * // Preset for backups (high security)
 * CryptoConfig backupConfig = CryptoConfig.forBackups();
 * 
 * // Preset for maximum security (no compression, no metadata)
 * CryptoConfig maxSecConfig = CryptoConfig.forMaximumSecurity();
 * }</pre>
 * 
 * <h3>Header Inspection</h3>
 * <pre>{@code
 * SimjotCrypto crypto = new SimjotCrypto();
 * 
 * // Check if data is Simjot encrypted
 * if (crypto.isSimjotEncrypted(data)) {
 *     // Read header without decrypting
 *     CryptoHeader header = crypto.readHeader(data);
 *     
 *     System.out.println("Content Type: " + header.getContentType().getDisplayName());
 *     System.out.println("Security Level: " + header.getSecurityLevel().getDisplayName());
 *     System.out.println("Encrypted: " + new Date(header.getTimestamp()));
 *     System.out.println("Original Size: " + header.getOriginalSize() + " bytes");
 * }
 * }</pre>
 * 
 * <h3>Password Validation</h3>
 * <pre>{@code
 * SimjotCrypto crypto = new SimjotCrypto();
 * 
 * // Validate password without full decryption
 * boolean valid = crypto.validatePassword(encryptedData, "testPassword");
 * if (valid) {
 *     byte[] decrypted = crypto.decrypt(encryptedData, "testPassword");
 * }
 * }</pre>
 * 
 * <h3>Error Handling</h3>
 * <pre>{@code
 * SimjotCrypto crypto = new SimjotCrypto();
 * 
 * try {
 *     byte[] decrypted = crypto.decrypt(encryptedData, password);
 * } catch (CryptoException e) {
 *     if (e.isAuthenticationFailure()) {
 *         // Wrong password
 *         showError("Incorrect password. Please try again.");
 *     } else if (e.isDataCorrupted()) {
 *         // Data corruption
 *         showError("The file appears to be corrupted.");
 *     } else {
 *         // Other errors
 *         showError(e.getUserMessage());
 *     }
 * }
 * }</pre>
 * 
 * <h3>Streaming API for Large Files</h3>
 * <pre>{@code
 * SimjotCrypto crypto = new SimjotCrypto();
 * CryptoConfig config = CryptoConfig.forBackups();
 * 
 * // Encrypt large file using streams
 * try (FileOutputStream fos = new FileOutputStream("large-backup.sjcrypt");
 *      OutputStream encStream = crypto.createEncryptingStream(
 *          fos, "password", ContentType.BACKUP, config);
 *      FileInputStream fis = new FileInputStream("large-backup.zip")) {
 *     
 *     byte[] buffer = new byte[8192];
 *     int read;
 *     while ((read = fis.read(buffer)) != -1) {
 *         encStream.write(buffer, 0, read);
 *     }
 * }
 * }</pre>
 * 
 * <h2>Security Considerations</h2>
 * <ul>
 *   <li><b>Password Strength:</b> Use strong passwords (12+ characters, mixed case, 
 *       numbers, symbols). The security of encryption depends on password strength.</li>
 *   <li><b>Key Derivation:</b> PBKDF2 iterations provide protection against brute-force
 *       attacks. Higher security levels use more iterations but are slower.</li>
 *   <li><b>Authenticated Encryption:</b> AES-GCM provides both confidentiality and
 *       integrity. Tampering with ciphertext will be detected.</li>
 *   <li><b>Random Salt/IV:</b> Each encryption uses cryptographically random salt and
 *       IV, preventing rainbow table attacks and ensuring semantic security.</li>
 *   <li><b>Header Protection:</b> The header is signed with HMAC to detect tampering.</li>
 *   <li><b>Compression:</b> While compression reduces size, it may leak information
 *       about content patterns. Disable for maximum security.</li>
 * </ul>
 * 
 * <h2>Integration Points</h2>
 * <p>This crypto engine is designed to integrate with:</p>
 * <ul>
 *   <li><b>EntryPanel / EditEntryPanel:</b> Encrypt/decrypt journal entries</li>
 *   <li><b>PoemPanel / EditPoemPanel:</b> Encrypt/decrypt poems</li>
 *   <li><b>BackupManager / BackupService:</b> Encrypt backup archives</li>
 *   <li><b>FileIO:</b> Transparent encryption layer for file operations</li>
 * </ul>
 * 
 * @since 1.0
 * @see SimjotCrypto
 * @see CryptoConfig
 * @see CryptoHeader
 * @see ContentType
 * @see CryptoException
 */
package main.core.security.crypto;
