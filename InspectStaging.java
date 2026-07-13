import java.sql.*;

public class InspectStaging {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/MigrationDB";
        String user = "postgres";
        String password = "root";
        
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("Columns in ccol.docversion_staging:");
            ResultSet rs = conn.getMetaData().getColumns(null, "ccol", "docversion_staging", "%");
            while(rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                if (name.equalsIgnoreCase("migrated_date") || name.equalsIgnoreCase("migration_status") || name.equalsIgnoreCase("object_class_id")) {
                    System.out.println("  " + name + " (" + rs.getString("TYPE_NAME") + ")");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
