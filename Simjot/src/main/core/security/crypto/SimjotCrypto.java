/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

package main.core.security.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import main.infrastructure.ffi.NativeAccess;

/**
 * <h1>Simjot Cryptographic Engine</h1>
 * 
 * An encryption engine implementing a proprietary file format
 * exclusively for Simjot. Provides military-grade encryption for journal entries,
 * poems, and backup archives.
 * 
 * <h2>Security Features</h2>
 * <ul>
 *   <li><b>AES-256-GCM</b>: Authenticated encryption providing confidentiality and integrity</li>
 *   <li><b>PBKDF2-HMAC-SHA256</b>: Secure key derivation with configurable iterations</li>
 *   <li><b>Random Salt</b>: 256-bit salt per encryption prevents rainbow table attacks</li>
 *   <li><b>Random IV/Nonce</b>: 96-bit nonce per encryption ensures semantic security</li>
 *   <li><b>Header HMAC</b>: Cryptographic signature protects header integrity</li>
 *   <li><b>Optional Compression</b>: DEFLATE compression before encryption</li>
 * </ul>
 * 
 * <h2>Proprietary Format</h2>
 * Files encrypted with SimjotCrypto use the {@code .sjcrypt} extension and contain
 * a structured header followed by the encrypted payload. The format is designed
 * to be:
 * <ul>
 *   <li>Self-describing with magic bytes and version information</li>
 *   <li>Extensible for future cryptographic upgrades</li>
 *   <li>Resistant to tampering through authenticated encryption and header signatures</li>
 * </ul>
 * 
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * // Simple string encryption
 * SimjotCrypto crypto = new SimjotCrypto();
 * byte[] encrypted = crypto.encrypt("My secret journal entry", "password123", ContentType.ENTRY);
 * String decrypted = crypto.decryptToString(encrypted, "password123");
 * 
 * // File encryption with custom config
 * CryptoConfig config = CryptoConfig.forBackups();
 * crypto.encryptFile(new File("backup.zip"), new File("backup.sjcrypt"), "password", config);
 * 
 * // Decryption with header inspection
 * CryptoHeader header = crypto.readHeader(encryptedData);
 * System.out.println("Content type: " + header.getContentType());
 * byte[] decrypted = crypto.decrypt(encryptedData, "password");
 * }</pre>
 * 
 * <h2>Thread Safety</h2>
 * Instances of SimjotCrypto are thread-safe. Each encryption/decryption operation
 * uses independent cryptographic contexts.
 * 
 * @author S1mplector
 * @version 1.0
 * @since 1.0
 */
public final class SimjotCrypto {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int BUFFER_SIZE = 8192;
    
    // Cryptographically secure random number generator
    private final SecureRandom secureRandom;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONSTRUCTORS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Creates a new SimjotCrypto instance with a fresh SecureRandom.
     */
    public SimjotCrypto() {
        this.secureRandom = new SecureRandom();
    }
    
    /**
     * Creates a new SimjotCrypto instance with a provided SecureRandom.
     * Useful for testing or when a specific entropy source is required.
     * 
     * @param secureRandom The SecureRandom instance to use
     */
    public SimjotCrypto(SecureRandom secureRandom) {
        this.secureRandom = secureRandom != null ? secureRandom : new SecureRandom();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ENCRYPTION METHODS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Encrypts a string using the default configuration.
     * 
     * @param plaintext The string to encrypt
     * @param password The encryption password
     * @param contentType The type of content being encrypted
     * @return The encrypted data including header
     * @throws CryptoException if encryption fails
     */
    public byte[] encrypt(String plaintext, String password, ContentType contentType) 
            throws CryptoException {
        return encrypt(plaintext.getBytes(StandardCharsets.UTF_8), password, 
                contentType, new CryptoConfig());
    }
    
    /**
     * Encrypts a string with custom configuration.
     * 
     * @param plaintext The string to encrypt
     * @param password The encryption password
     * @param contentType The type of content being encrypted
     * @param config The encryption configuration
     * @return The encrypted data including header
     * @throws CryptoException if encryption fails
     */
    public byte[] encrypt(String plaintext, String password, ContentType contentType,
            CryptoConfig config) throws CryptoException {
        return encrypt(plaintext.getBytes(StandardCharsets.UTF_8), password, contentType, config);
    }
    
    /**
     * Encrypts binary data using the default configuration.
     * 
     * @param data The data to encrypt
     * @param password The encryption password
     * @param contentType The type of content being encrypted
     * @return The encrypted data including header
     * @throws CryptoException if encryption fails
     */
    public byte[] encrypt(byte[] data, String password, ContentType contentType) 
            throws CryptoException {
        return encrypt(data, password, contentType, new CryptoConfig());
    }
    
    /**
     * Encrypts binary data with custom configuration.
     * 
     * <p>This is the primary encryption method. It performs the following steps:</p>
     * <ol>
     *   <li>Generate random salt and IV</li>
     *   <li>Derive encryption key from password using PBKDF2</li>
     *   <li>Optionally compress the data</li>
     *   <li>Encrypt using AES-256-GCM</li>
     *   <li>Build and sign the header</li>
     *   <li>Concatenate header and ciphertext</li>
     * </ol>
     * 
     * @param data The data to encrypt
     * @param password The encryption password
     * @param contentType The type of content being encrypted
     * @param config The encryption configuration
     * @return The encrypted data including header
     * @throws CryptoException if encryption fails
     */
    public byte[] encrypt(byte[] data, String password, ContentType contentType,
            CryptoConfig config) throws CryptoException {
        
        validateInputs(data, password);
        
        try {
            // Generate cryptographic parameters
            byte[] salt = generateSalt();
            byte[] iv = generateIV();
            
            // Derive the encryption key
            SecretKey key = deriveKey(password, salt, config.getIterations());
            
            // Optionally compress the data
            byte[] processedData = data;
            if (config.isCompressBeforeEncrypt()) {
                processedData = compress(data);
            }
            
            // Encrypt using AES-GCM
            byte[] ciphertext = encryptAesGcm(processedData, key, iv);
            
            // Build the header
            CryptoHeader.Builder headerBuilder = new CryptoHeader.Builder()
                    .contentType(contentType)
                    .fromConfig(config)
                    .salt(salt)
                    .iv(iv)
                    .originalSize(data.length)
                    .encryptedSize(ciphertext.length)
                    .timestamp(System.currentTimeMillis());
            
            // Create header without signature first
            CryptoHeader unsignedHeader = headerBuilder.build();
            byte[] headerBytesForSigning = unsignedHeader.toBytesForSigning();
            
            // Compute header HMAC signature
            byte[] headerSignature = computeHmac(headerBytesForSigning, key);
            
            // Build final header with signature
            CryptoHeader signedHeader = headerBuilder
                    .headerSignature(headerSignature)
                    .build();
            
            byte[] headerBytes = signedHeader.toBytes();
            
            // Concatenate header and ciphertext
            byte[] result = new byte[headerBytes.length + ciphertext.length];
            System.arraycopy(headerBytes, 0, result, 0, headerBytes.length);
            System.arraycopy(ciphertext, 0, result, headerBytes.length, ciphertext.length);
            
            return result;
            
        } catch (Exception e) {
            throw new CryptoException("Encryption failed", e);
        }
    }
    
    /**
     * Encrypts a file to another file.
     * 
     * @param inputFile The file to encrypt
     * @param outputFile The destination for encrypted data
     * @param password The encryption password
     * @param contentType The type of content
     * @throws CryptoException if encryption fails
     * @throws IOException if file I/O fails
     */
    public void encryptFile(File inputFile, File outputFile, String password, 
            ContentType contentType) throws CryptoException, IOException {
        encryptFile(inputFile, outputFile, password, contentType, new CryptoConfig());
    }
    
    /**
     * Encrypts a file to another file with custom configuration.
     * 
     * @param inputFile The file to encrypt
     * @param outputFile The destination for encrypted data
     * @param password The encryption password
     * @param contentType The type of content
     * @param config The encryption configuration
     * @throws CryptoException if encryption fails
     * @throws IOException if file I/O fails
     */
    public void encryptFile(File inputFile, File outputFile, String password,
            ContentType contentType, CryptoConfig config) throws CryptoException, IOException {
        
        byte[] data = readFile(inputFile);
        byte[] encrypted = encrypt(data, password, contentType, config);
        writeFile(outputFile, encrypted);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // DECRYPTION METHODS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Decrypts data and returns as a UTF-8 string.
     * 
     * @param encryptedData The encrypted data (header + ciphertext)
     * @param password The decryption password
     * @return The decrypted string
     * @throws CryptoException if decryption fails
     */
    public String decryptToString(byte[] encryptedData, String password) throws CryptoException {
        byte[] decrypted = decrypt(encryptedData, password);
        return new String(decrypted, StandardCharsets.UTF_8);
    }
    
    /**
     * Decrypts data and returns the raw bytes.
     * 
     * <p>This is the primary decryption method. It performs the following steps:</p>
     * <ol>
     *   <li>Parse and validate the header</li>
     *   <li>Derive the decryption key from password</li>
     *   <li>Verify header HMAC signature</li>
     *   <li>Decrypt using AES-256-GCM (includes authentication)</li>
     *   <li>Optionally decompress the data</li>
     * </ol>
     * 
     * @param encryptedData The encrypted data (header + ciphertext)
     * @param password The decryption password
     * @return The decrypted data
     * @throws CryptoException if decryption fails or authentication fails
     */
    public byte[] decrypt(byte[] encryptedData, String password) throws CryptoException {
        
        if (encryptedData == null || encryptedData.length < 100) {
            throw new CryptoException("Invalid encrypted data: too short");
        }
        if (password == null || password.isEmpty()) {
            throw new CryptoException("Password cannot be null or empty");
        }
        
        try {
            // Parse the header
            CryptoHeader header = CryptoHeader.fromBytes(encryptedData);
            int headerSize = header.getHeaderSize();
            
            // Extract ciphertext
            byte[] ciphertext = Arrays.copyOfRange(encryptedData, headerSize, encryptedData.length);
            
            // Derive the decryption key
            SecretKey key = deriveKey(password, header.getSalt(), 
                    header.getSecurityLevel().getIterations());
            
            // Verify header signature
            byte[] headerBytesForSigning = Arrays.copyOf(encryptedData, 
                    headerSize - CryptoConfig.HEADER_SIGNATURE_BYTES);
            byte[] expectedSignature = computeHmac(headerBytesForSigning, key);
            byte[] actualSignature = header.getHeaderSignature();
            
            if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
                throw new CryptoException("Header signature verification failed - " +
                        "data may be corrupted or password is incorrect");
            }
            
            // Decrypt using AES-GCM
            byte[] decrypted = decryptAesGcm(ciphertext, key, header.getIv());
            
            // Decompress if necessary
            if (header.isCompressed()) {
                decrypted = decompress(decrypted);
            }
            
            return decrypted;
            
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("Decryption failed", e);
        }
    }
    
    /**
     * Decrypts a file to another file.
     * 
     * @param inputFile The encrypted file
     * @param outputFile The destination for decrypted data
     * @param password The decryption password
     * @throws CryptoException if decryption fails
     * @throws IOException if file I/O fails
     */
    public void decryptFile(File inputFile, File outputFile, String password) 
            throws CryptoException, IOException {
        
        byte[] encrypted = readFile(inputFile);
        byte[] decrypted = decrypt(encrypted, password);
        writeFile(outputFile, decrypted);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // HEADER INSPECTION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Reads and parses the header from encrypted data without decrypting.
     * Useful for inspecting content type, timestamps, or verifying format.
     * 
     * @param encryptedData The encrypted data
     * @return The parsed header
     * @throws CryptoException if the header is invalid
     */
    public CryptoHeader readHeader(byte[] encryptedData) throws CryptoException {
        try {
            return CryptoHeader.fromBytes(encryptedData);
        } catch (IOException e) {
            throw new CryptoException("Failed to read header", e);
        }
    }
    
    /**
     * Checks if the given data appears to be a valid Simjot encrypted file.
     * Only checks the magic bytes, does not verify cryptographic integrity.
     * 
     * @param data The data to check
     * @return true if the data starts with Simjot magic bytes
     */
    public boolean isSimjotEncrypted(byte[] data) {
        if (data == null || data.length < CryptoConfig.MAGIC_BYTES.length) {
            return false;
        }
        for (int i = 0; i < CryptoConfig.MAGIC_BYTES.length; i++) {
            if (data[i] != CryptoConfig.MAGIC_BYTES[i]) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Checks if a file appears to be a Simjot encrypted file.
     * 
     * @param file The file to check
     * @return true if the file starts with Simjot magic bytes
     */
    public boolean isSimjotEncrypted(File file) {
        if (file == null || !file.exists() || !file.canRead()) {
            return false;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] magic = new byte[CryptoConfig.MAGIC_BYTES.length];
            int read = fis.read(magic);
            return read == magic.length && isSimjotEncrypted(magic);
        } catch (IOException e) {
            return false;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // PASSWORD VALIDATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Validates a password against encrypted data without full decryption.
     * Uses header signature verification for fast password checking.
     * 
     * @param encryptedData The encrypted data
     * @param password The password to validate
     * @return true if the password is correct
     */
    public boolean validatePassword(byte[] encryptedData, String password) {
        try {
            CryptoHeader header = CryptoHeader.fromBytes(encryptedData);
            int headerSize = header.getHeaderSize();
            
            SecretKey key = deriveKey(password, header.getSalt(),
                    header.getSecurityLevel().getIterations());
            
            byte[] headerBytesForSigning = Arrays.copyOf(encryptedData,
                    headerSize - CryptoConfig.HEADER_SIGNATURE_BYTES);
            byte[] expectedSignature = computeHmac(headerBytesForSigning, key);
            byte[] actualSignature = header.getHeaderSignature();
            
            return MessageDigest.isEqual(expectedSignature, actualSignature);
            
        } catch (Exception e) {
            return false;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CRYPTOGRAPHIC PRIMITIVES
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Generates a cryptographically secure random salt.
     */
    private byte[] generateSalt() {
        byte[] salt = new byte[CryptoConfig.SALT_SIZE_BYTES];
        secureRandom.nextBytes(salt);
        return salt;
    }
    
    /**
     * Generates a cryptographically secure random IV/nonce.
     */
    private byte[] generateIV() {
        byte[] iv = new byte[CryptoConfig.IV_SIZE_BYTES];
        secureRandom.nextBytes(iv);
        return iv;
    }
    
    /**
     * Derives an AES-256 key from a password using PBKDF2-HMAC-SHA256.
     * 
     * @param password The password
     * @param salt The salt
     * @param iterations The number of PBKDF2 iterations
     * @return The derived secret key
     */
    private SecretKey deriveKey(String password, byte[] salt, int iterations) 
            throws GeneralSecurityException {
        
        // Try native PBKDF2 first (much faster)
        byte[] nativeKey = NativeAccess.pbkdf2HmacSha256(password, salt, iterations, 32);
        if (nativeKey != null) {
            return new SecretKeySpec(nativeKey, "AES");
        }
        
        // Fallback to Java implementation
        PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(),
                salt,
                iterations,
                CryptoConfig.KEY_SIZE_BITS
        );
        
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(
                    CryptoConfig.KEY_DERIVATION_ALGORITHM);
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");
        } finally {
            spec.clearPassword();
        }
    }
    
    /**
     * Encrypts data using AES-256-GCM.
     */
    private byte[] encryptAesGcm(byte[] data, SecretKey key, byte[] iv) 
            throws GeneralSecurityException {
        
        Cipher cipher = Cipher.getInstance(CryptoConfig.CIPHER_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(CryptoConfig.GCM_TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);
        return cipher.doFinal(data);
    }
    
    /**
     * Decrypts data using AES-256-GCM.
     * GCM mode automatically verifies the authentication tag.
     */
    private byte[] decryptAesGcm(byte[] ciphertext, SecretKey key, byte[] iv) 
            throws GeneralSecurityException {
        
        Cipher cipher = Cipher.getInstance(CryptoConfig.CIPHER_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(CryptoConfig.GCM_TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);
        return cipher.doFinal(ciphertext);
    }
    
    /**
     * Computes HMAC-SHA256 of data using the given key.
     */
    private byte[] computeHmac(byte[] data, SecretKey key) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(key);
        return mac.doFinal(data);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // COMPRESSION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Compresses data using DEFLATE.
     */
    private byte[] compress(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DeflaterOutputStream dos = new DeflaterOutputStream(baos, 
                new Deflater(Deflater.BEST_COMPRESSION))) {
            dos.write(data);
        }
        return baos.toByteArray();
    }
    
    /**
     * Decompresses DEFLATE-compressed data.
     */
    private byte[] decompress(byte[] data) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InflaterInputStream iis = new InflaterInputStream(bais, new Inflater())) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = iis.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
        }
        return baos.toByteArray();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // FILE I/O
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Reads entire file into byte array.
     */
    private byte[] readFile(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            return baos.toByteArray();
        }
    }
    
    /**
     * Writes byte array to file.
     */
    private void writeFile(File file, byte[] data) throws IOException {
        // Ensure parent directories exist
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // VALIDATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Validates encryption inputs.
     */
    private void validateInputs(byte[] data, String password) throws CryptoException {
        if (data == null || data.length == 0) {
            throw new CryptoException("Data cannot be null or empty");
        }
        if (password == null || password.isEmpty()) {
            throw new CryptoException("Password cannot be null or empty");
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // STREAMING API (for large files)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Creates an encrypting output stream.
     * Data written to this stream will be encrypted.
     * 
     * <p><b>Note:</b> The stream must be closed properly to finalize encryption
     * and write the authentication tag.</p>
     * 
     * @param outputStream The underlying output stream
     * @param password The encryption password
     * @param contentType The content type
     * @param config The encryption configuration
     * @return An OutputStream that encrypts data
     * @throws CryptoException if stream creation fails
     */
    public OutputStream createEncryptingStream(OutputStream outputStream, String password,
            ContentType contentType, CryptoConfig config) throws CryptoException {
        
        try {
            byte[] salt = generateSalt();
            byte[] iv = generateIV();
            SecretKey key = deriveKey(password, salt, config.getIterations());
            
            // Build and write header (sizes will be 0 for streaming)
            CryptoHeader.Builder headerBuilder = new CryptoHeader.Builder()
                    .contentType(contentType)
                    .fromConfig(config)
                    .salt(salt)
                    .iv(iv)
                    .originalSize(0)  // Unknown for streaming
                    .encryptedSize(0) // Unknown for streaming
                    .timestamp(System.currentTimeMillis());
            
            CryptoHeader unsignedHeader = headerBuilder.build();
            byte[] headerBytesForSigning = unsignedHeader.toBytesForSigning();
            byte[] headerSignature = computeHmac(headerBytesForSigning, key);
            
            CryptoHeader signedHeader = headerBuilder
                    .headerSignature(headerSignature)
                    .build();
            
            byte[] headerBytes = signedHeader.toBytes();
            outputStream.write(headerBytes);
            outputStream.flush(); // Ensure header is written before cipher stream starts
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(CryptoConfig.CIPHER_ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(CryptoConfig.GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);
            
            return new javax.crypto.CipherOutputStream(outputStream, cipher);
            
        } catch (Exception e) {
            throw new CryptoException("Failed to create encrypting stream", e);
        }
    }
    
    /**
     * Creates a decrypting input stream.
     * Data read from this stream will be decrypted.
     * 
     * @param inputStream The underlying input stream containing encrypted data
     * @param password The decryption password
     * @return An InputStream that decrypts data
     * @throws CryptoException if stream creation fails
     */
    public InputStream createDecryptingStream(InputStream inputStream, String password) 
            throws CryptoException {
        
        try {
            // Read header bytes first
            // We need to read enough for the minimum header
            byte[] headerBuffer = new byte[256];  // More than enough for header
            int totalRead = 0;
            int read;
            while (totalRead < headerBuffer.length && 
                   (read = inputStream.read(headerBuffer, totalRead, 
                           headerBuffer.length - totalRead)) != -1) {
                totalRead += read;
                // Try to parse header to get actual size
                try {
                    CryptoHeader header = CryptoHeader.fromBytes(
                            Arrays.copyOf(headerBuffer, totalRead));
                    int headerSize = header.getHeaderSize();
                    if (totalRead >= headerSize) {
                        // We have the complete header
                        SecretKey key = deriveKey(password, header.getSalt(),
                                header.getSecurityLevel().getIterations());
                        
                        // Verify header signature
                        byte[] headerBytesForSigning = Arrays.copyOf(headerBuffer,
                                headerSize - CryptoConfig.HEADER_SIGNATURE_BYTES);
                        byte[] expectedSignature = computeHmac(headerBytesForSigning, key);
                        byte[] actualSignature = header.getHeaderSignature();
                        
                        if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
                            throw new CryptoException("Header signature verification failed");
                        }
                        
                        // Initialize cipher
                        Cipher cipher = Cipher.getInstance(CryptoConfig.CIPHER_ALGORITHM);
                        GCMParameterSpec gcmSpec = new GCMParameterSpec(
                                CryptoConfig.GCM_TAG_LENGTH_BITS, header.getIv());
                        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);
                        
                        // Create a combined stream: any leftover header bytes + rest of input
                        byte[] leftover = Arrays.copyOfRange(headerBuffer, headerSize, totalRead);
                        InputStream combinedStream = new java.io.SequenceInputStream(
                                new ByteArrayInputStream(leftover), inputStream);
                        
                        InputStream cipherStream = new javax.crypto.CipherInputStream(
                                combinedStream, cipher);
                        
                        // Wrap in decompression stream if needed
                        if (header.isCompressed()) {
                            return new InflaterInputStream(cipherStream);
                        }
                        return cipherStream;
                    }
                } catch (IOException e) {
                    // Not enough data yet, continue reading
                }
            }
            throw new CryptoException("Failed to read complete header from stream");
            
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("Failed to create decrypting stream", e);
        }
    }
}
