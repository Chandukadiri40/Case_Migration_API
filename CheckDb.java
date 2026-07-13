import java.sql.*;
public class CheckDb {
    public static void main(String[] args) throws Exception {
        Connection c = DriverManager.getConnection("jdbc:postgresql://localhost:5432/P8Migration", "postgres", "postgres");
        ResultSet rs = c.getMetaData().getColumns(null, "ccol", "docversion_staging", null);
        while(rs.next()) {
            System.out.println(rs.getString("COLUMN_NAME"));
        }
        c.close();
    }
}
