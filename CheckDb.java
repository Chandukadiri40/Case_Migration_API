import java.sql.*;

public class CheckDb {
    public static void main(String[] args) {
        String searchId = "AE4848F6E84530C1898A5792F7D3B64C9";
        try (Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/Migration", "postgres", "postgres")) {
            
            System.out.println("Finding Custom Object Classes...");
            
            System.out.println("Checking columns in docversion_source...");
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'docversion_source' AND table_schema = 'public'")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        System.out.println("docversion_source: " + rs.getString("column_name") + " (" + rs.getString("data_type") + ")");
                    }
                }
            }
            
            System.out.println("Checking columns in propertydef...");
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'propertydef' AND table_schema = 'public'")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        System.out.println("propertydef: " + rs.getString("column_name") + " (" + rs.getString("data_type") + ")");
                    }
                }
            }
            
            System.out.println("Checking columns in globalpropertydef...");
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'globalpropertydef' AND table_schema = 'public'")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        System.out.println("globalpropertydef: " + rs.getString("column_name") + " (" + rs.getString("data_type") + ")");
                    }
                }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
