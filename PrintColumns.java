import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;

public class PrintColumns {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/P8Migration";
        String user = "postgres";
        String password = "postgres";
        
        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {
            
            ResultSet rs = stmt.executeQuery("SELECT * FROM ccol.docversion_source LIMIT 1");
            ResultSetMetaData rsmd = rs.getMetaData();
            int columnCount = rsmd.getColumnCount();
            
            System.out.println("Columns in ccol.docversion_source:");
            for (int i = 1; i <= columnCount; i++) {
                System.out.println(rsmd.getColumnName(i) + " (" + rsmd.getColumnTypeName(i) + ")");
            }
        }
    }
}
