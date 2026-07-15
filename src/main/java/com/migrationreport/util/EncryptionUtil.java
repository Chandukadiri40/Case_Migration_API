package com.migrationreport.util;

import java.security.MessageDigest;
import java.util.Base64;

public class EncryptionUtil {
    
    /**
     * Hashes a plain text password using SHA-256 and encodes it to Base64.
     * This is a one-way mathematical function.
     */
    public static String hashPassword(String password) {
        if (password == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception ex) {
            throw new RuntimeException("Error hashing password", ex);
        }
    }
}
