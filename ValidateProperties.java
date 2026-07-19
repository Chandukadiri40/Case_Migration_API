import java.sql.*;

public class ValidateProperties {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/Migration";
        String user = "postgres";
        String password = "postgres"; 
        
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("Validating BankAccount properties:");
            String sql = "SELECT gpd.symbolic_name, pd.inherited_bool, CAST(pd.sys_owned_bool AS VARCHAR) as sys_owned " +
                         "FROM propertydef pd " +
                         "INNER JOIN globalpropertydef gpd ON pd.global_prop_id = gpd.object_id " +
                         "WHERE pd.dbg_class_name = 'BankAccount' " +
                         "ORDER BY gpd.symbolic_name";
            try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(sql)) {
                while(rs.next()) {
                    boolean sysOwned = "1".equals(rs.getString("sys_owned")) || "true".equalsIgnoreCase(rs.getString("sys_owned"));
                    if (!sysOwned) {
                        System.out.println("  - " + rs.getString("symbolic_name") + " (Inherited: " + rs.getString("inherited_bool") + ")");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
