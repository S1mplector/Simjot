package main.core.security.crypto;

/**
 * Exception thrown when cryptographic operations fail.
 * 
 * <p>This exception wraps various underlying exceptions that can occur during
 * encryption and decryption operations, providing a unified exception type
 * for the Simjot crypto API.</p>
 * 
 * <h2>Common Causes</h2>
 * <ul>
 *   <li>Invalid password (authentication tag verification fails)</li>
 *   <li>Corrupted encrypted data</li>
 *   <li>Invalid file format (not a Simjot encrypted file)</li>
 *   <li>Unsupported format version</li>
 *   <li>I/O errors during file operations</li>
 * </ul>
 * 
 * @since 1.0
 */
public class CryptoException extends Exception {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Error codes for categorizing cryptographic failures.
     */
    public enum ErrorCode {
        /** Generic/unknown error */
        UNKNOWN,
        /** Invalid or empty input data */
        INVALID_INPUT,
        /** Invalid password (authentication failed) */
        INVALID_PASSWORD,
        /** File format is not recognized as Simjot encrypted */
        INVALID_FORMAT,
        /** Encrypted data is corrupted */
        DATA_CORRUPTED,
        /** Header signature verification failed */
        HEADER_TAMPERED,
        /** Unsupported format version */
        UNSUPPORTED_VERSION,
        /** Key derivation failed */
        KEY_DERIVATION_FAILED,
        /** Encryption algorithm not available */
        ALGORITHM_NOT_AVAILABLE,
        /** File I/O error */
        IO_ERROR,
        /** Compression/decompression error */
        COMPRESSION_ERROR
    }
    
    private final ErrorCode errorCode;
    
    /**
     * Constructs a new CryptoException with the specified message.
     * 
     * @param message The detail message
     */
    public CryptoException(String message) {
        super(message);
        this.errorCode = ErrorCode.UNKNOWN;
    }
    
    /**
     * Constructs a new CryptoException with the specified message and cause.
     * 
     * @param message The detail message
     * @param cause The underlying cause
     */
    public CryptoException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = inferErrorCode(cause);
    }
    
    /**
     * Constructs a new CryptoException with a specific error code.
     * 
     * @param message The detail message
     * @param errorCode The error code categorizing the failure
     */
    public CryptoException(String message, ErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
    
    /**
     * Constructs a new CryptoException with a specific error code and cause.
     * 
     * @param message The detail message
     * @param cause The underlying cause
     * @param errorCode The error code categorizing the failure
     */
    public CryptoException(String message, Throwable cause, ErrorCode errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    /**
     * Gets the error code categorizing this exception.
     * 
     * @return The error code
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
    
    /**
     * Checks if this exception indicates an authentication failure
     * (likely wrong password).
     * 
     * @return true if the error suggests wrong password
     */
    public boolean isAuthenticationFailure() {
        return errorCode == ErrorCode.INVALID_PASSWORD || 
               errorCode == ErrorCode.HEADER_TAMPERED;
    }
    
    /**
     * Checks if this exception indicates corrupted data.
     * 
     * @return true if the data appears corrupted
     */
    public boolean isDataCorrupted() {
        return errorCode == ErrorCode.DATA_CORRUPTED ||
               errorCode == ErrorCode.INVALID_FORMAT;
    }
    
    /**
     * Gets a user-friendly error message suitable for display.
     * 
     * @return A user-friendly message
     */
    public String getUserMessage() {
        return switch (errorCode) {
            case INVALID_PASSWORD, HEADER_TAMPERED -> 
                "The password is incorrect or the data has been tampered with.";
            case INVALID_FORMAT -> 
                "This file is not a valid Simjot encrypted file.";
            case DATA_CORRUPTED -> 
                "The encrypted data is corrupted and cannot be decrypted.";
            case UNSUPPORTED_VERSION -> 
                "This file was encrypted with a newer version of Simjot.";
            case INVALID_INPUT -> 
                "Invalid input provided for encryption/decryption.";
            case IO_ERROR -> 
                "A file error occurred. Please check file permissions.";
            case COMPRESSION_ERROR -> 
                "Failed to compress or decompress data.";
            case ALGORITHM_NOT_AVAILABLE -> 
                "Required encryption algorithm is not available on this system.";
            case KEY_DERIVATION_FAILED -> 
                "Failed to derive encryption key. This may indicate a system issue.";
            default -> 
                "An encryption error occurred: " + getMessage();
        };
    }
    
    /**
     * Infers an error code from the cause exception.
     */
    private ErrorCode inferErrorCode(Throwable cause) {
        if (cause == null) {
            return ErrorCode.UNKNOWN;
        }
        
        String causeName = cause.getClass().getSimpleName();
        String message = cause.getMessage();
        
        // Check for GCM authentication tag failure (wrong password)
        if (causeName.contains("AEADBadTagException") || 
            (message != null && message.contains("Tag mismatch"))) {
            return ErrorCode.INVALID_PASSWORD;
        }
        
        // Check for I/O errors
        if (cause instanceof java.io.IOException) {
            if (message != null && message.contains("compress")) {
                return ErrorCode.COMPRESSION_ERROR;
            }
            return ErrorCode.IO_ERROR;
        }
        
        // Check for security/crypto errors
        if (cause instanceof java.security.GeneralSecurityException) {
            if (causeName.contains("NoSuchAlgorithm") || 
                causeName.contains("NoSuchProvider")) {
                return ErrorCode.ALGORITHM_NOT_AVAILABLE;
            }
            if (causeName.contains("InvalidKey")) {
                return ErrorCode.KEY_DERIVATION_FAILED;
            }
        }
        
        return ErrorCode.UNKNOWN;
    }
    
    @Override
    public String toString() {
        return String.format("CryptoException[%s]: %s", errorCode, getMessage());
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // FACTORY METHODS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Creates an exception for invalid password.
     */
    public static CryptoException invalidPassword() {
        return new CryptoException(
            "Decryption failed - incorrect password or corrupted data",
            ErrorCode.INVALID_PASSWORD
        );
    }
    
    /**
     * Creates an exception for invalid format.
     */
    public static CryptoException invalidFormat(String details) {
        return new CryptoException(
            "Invalid file format: " + details,
            ErrorCode.INVALID_FORMAT
        );
    }
    
    /**
     * Creates an exception for corrupted data.
     */
    public static CryptoException dataCorrupted(String details) {
        return new CryptoException(
            "Data corrupted: " + details,
            ErrorCode.DATA_CORRUPTED
        );
    }
    
    /**
     * Creates an exception for unsupported version.
     */
    public static CryptoException unsupportedVersion(int major, int minor) {
        return new CryptoException(
            String.format("Unsupported format version: %d.%d", major, minor),
            ErrorCode.UNSUPPORTED_VERSION
        );
    }
    
    /**
     * Creates an exception for invalid input.
     */
    public static CryptoException invalidInput(String details) {
        return new CryptoException(
            "Invalid input: " + details,
            ErrorCode.INVALID_INPUT
        );
    }
}
