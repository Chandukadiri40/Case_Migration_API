import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class TestDb {
    public static void main(String[] args) {
        try {
            Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/MigrationReports", "postgres", "postgres");
            Statement stmt = conn.createStatement();
            
            ResultSet rs = stmt.executeQuery("SELECT object_class_id FROM docversion_source LIMIT 5");
            while(rs.next()) {
                System.out.println("object_class_id: " + rs.getString(1));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
