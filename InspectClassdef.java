import java.sql.*;

public class InspectClassdef {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/MigrationDB";
        String user = "postgres";
        String password = "root";
        
        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {
            
            System.out.println("Columns in ccol.classdef:");
            ResultSet rs = conn.getMetaData().getColumns(null, "ccol", "classdef", "%");
            while(rs.next()) {
                System.out.println("  " + rs.getString("COLUMN_NAME") + " (" + rs.getString("TYPE_NAME") + ")");
            }
            
            System.out.println("Data in ccol.classdef:");
            ResultSet data = stmt.executeQuery("SELECT object_id, symbolic_name FROM ccol.classdef LIMIT 10");
            while(data.next()) {
                System.out.println("  object_id: " + data.getString("object_id") + " | symbolic_name: " + data.getString("symbolic_name"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
