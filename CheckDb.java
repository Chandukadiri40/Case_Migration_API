import java.sql.*;

public class CheckDb {
    public static void main(String[] args) {
        String searchId = "AE4848F6E84530C1898A5792F7D3B64C9";
        try (Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/P8Migration", "postgres", "postgres")) {
            
            System.out.println("Searching for: " + searchId);
            
            String[] tables = {"docversion_source", "docversion_staging", "docversion_target"};
            for (String t : tables) {
                try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM ccol." + t + " WHERE LOWER(object_id) = LOWER(?)")) {
                    stmt.setString(1, searchId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            System.out.println("Table: " + t + " -> Found: " + rs.getInt(1));
                        }
                    }
                }
            }
            
            System.out.println("\nChecking classdef for this object:");
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT cd.symbolic_name FROM ccol.docversion_source dv " + 
                "JOIN ccol.classdef cd ON dv.object_class_id = cd.object_id " + 
                "WHERE LOWER(dv.object_id) = LOWER(?)")) {
                stmt.setString(1, searchId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        System.out.println("Found class: " + rs.getString("symbolic_name"));
                    } else {
                        System.out.println("No matching classdef found for this object_id in source table.");
                    }
                }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
