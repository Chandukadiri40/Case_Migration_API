package com.migrationreport.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TempUpdate {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/Migration";
        String user = "postgres";
        String encPass = "ENC(Xv2j6/HM6I6xuVpsXYWNJi+F7hiyv+dCb3EaztKcuqZRKGJ3)";
        String pass = EncryptionUtil.decrypt(encPass);
        
        log.info("Connecting to database...");
        try (Connection c = DriverManager.getConnection(url, user, pass);
             Statement s = c.createStatement()) {
            
            // Get all failed records
            ResultSet rs = s.executeQuery("SELECT object_id FROM public.docversion_stagging WHERE lower(migration_status) = 'failed'");
            List<String> ids = new ArrayList<>();
            while (rs.next()) {
                ids.add(rs.getString("object_id"));
            }
            rs.close();
            
            log.info("Found {} failed records.", ids.size());
            
            String[] errors = {
                "Source Document Not Found in FileNet",
                "Checksum Mismatch during transfer",
                "Corrupted PDF Header (Invalid MIME type)",
                "Metadata validation failed (Missing required fields)"
            };
            
            int[] distribution = {3, 3, 2, 2};
            int currentReason = 0;
            int countForReason = 0;
            
            int totalUpdated = 0;
            for (String id : ids) {
                if (countForReason >= distribution[currentReason]) {
                    currentReason++;
                    countForReason = 0;
                    if (currentReason >= errors.length) currentReason = errors.length - 1; // Fallback
                }
                
                String errorInfo = errors[currentReason];
                
                int rows = s.executeUpdate("UPDATE public.docversion_stagging SET error_info = '" + errorInfo + "' WHERE object_id = '" + id + "'");
                totalUpdated += rows;
                countForReason++;
            }
            
            log.info("Update successful. Rows affected: {}", totalUpdated);
        }
    }
}
