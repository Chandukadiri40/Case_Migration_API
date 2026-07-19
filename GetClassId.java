import java.sql.*;

public class GetClassId {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/Migration";
        String user = "postgres";
        String password = "postgres"; 
        
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            try (Statement s = conn.createStatement(); 
                 ResultSet rs = s.executeQuery("SELECT object_id, symbolic_name FROM classdef")) {
                while(rs.next()) {
                    System.out.println(rs.getString(2) + " = " + rs.getString(1));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
