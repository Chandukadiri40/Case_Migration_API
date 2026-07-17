package com.migrationreport.util;

import java.security.MessageDigest;
import java.util.Base64;

import java.nio.charset.StandardCharsets;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import com.migrationreport.exception.EncryptionException;

import javax.crypto.spec.GCMParameterSpec;
import java.security.SecureRandom;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class EncryptionUtil {
    
    private EncryptionUtil() {
        // Hide implicit public constructor
    }
    
    /**
     * Hashes a plain text password using SHA-256 and encodes it to Base64.
     * This is a one-way mathematical function.
     */
    public static String hashPassword(String password) {
        if (password == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception ex) {
            throw new EncryptionException("Error hashing password", ex);
        }
    }

    private static final byte[] AES_SECRET_BYTES;

    static {
        byte[] keyBytes = new byte[32];
        try {
            Path keyPath = Paths.get(System.getProperty("user.dir"), ".migration-report.key");
            if (Files.exists(keyPath) && Files.size(keyPath) >= 32) {
                byte[] fileBytes = Files.readAllBytes(keyPath);
                System.arraycopy(fileBytes, 0, keyBytes, 0, 32);
            } else {
                new SecureRandom().nextBytes(keyBytes);
                Files.write(keyPath, keyBytes);
            }
        } catch (Exception e) {
            new SecureRandom().nextBytes(keyBytes);
        }
        AES_SECRET_BYTES = keyBytes;
    }

    /**
     * Two-way AES encryption for database passwords using GCM.
     */
    public static String encrypt(String plainText) {
        if (plainText == null || plainText.trim().isEmpty()) return plainText;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            SecretKeySpec secretKey = new SecretKeySpec(AES_SECRET_BYTES, "AES");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + encryptedBytes.length);
            byteBuffer.put(iv);
            byteBuffer.put(encryptedBytes);
            
            return "ENC(" + Base64.getEncoder().encodeToString(byteBuffer.array()) + ")";
        } catch (Exception e) {
            throw new EncryptionException("Error encrypting text", e);
        }
    }

    /**
     * Two-way AES decryption with GCM and fallback to ECB.
     */
    public static String decrypt(String encryptedText) {
        if (encryptedText == null || !encryptedText.startsWith("ENC(") || !encryptedText.endsWith(")")) {
            return encryptedText; // Not encrypted or null
        }
        try {
            String base64Cipher = encryptedText.substring(4, encryptedText.length() - 1);
            byte[] cipherMessage = Base64.getDecoder().decode(base64Cipher);
            
            try {
                return doDecrypt(cipherMessage, AES_SECRET_BYTES);
            } catch (Exception newKeyEx) {
                throw new EncryptionException("Error decrypting text with current key. The data might have been encrypted with a different key.", newKeyEx);
            }
        } catch (Exception e) {
            if (e instanceof EncryptionException) throw (EncryptionException) e;
            throw new EncryptionException("Error decrypting text", e);
        }
    }

    private static String doDecrypt(byte[] cipherMessage, byte[] keyBytes) throws Exception {
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
        try {
            // Try GCM first
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv = new byte[12];
            System.arraycopy(cipherMessage, 0, iv, 0, 12);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
            byte[] plainBytes = cipher.doFinal(cipherMessage, 12, cipherMessage.length - 12);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception gcmEx) {
            // Fallback to ECB for backward compatibility
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] plainBytes = cipher.doFinal(cipherMessage);
            return new String(plainBytes, StandardCharsets.UTF_8);
        }
    }
}
