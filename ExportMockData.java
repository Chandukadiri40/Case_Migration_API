import java.sql.*;
import java.io.*;

public class ExportMockData {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/Migration";
        String user = "postgres";
        String password = "postgres"; 
        
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            exportToCSV(conn, "BankAccount", "BankAccount_MockData.csv", "SELECT object_id, object_class_id, ua598_accholdername as acc_holder_name, u3a18_accno as acc_no, ua3a3_opendate as open_date, u5058_acctstatus as acct_status, u46a8_accttype as acct_type, uaf94_availbalance as avail_balance, u7908_bsbnumber as bsb_number, u9ba8_branchname as branch_name, u63a4_currentbalance as current_balance, uf8f8_custid as cust_id FROM docversion_source WHERE object_class_id='9F60F3B0000013CF8635EC86D1FA7702'");
            exportToCSV(conn, "HomeLoanApplication", "HomeLoanApplication_MockData.csv", "SELECT object_id, object_class_id, u7ec8_applname as appl_name, u90b8_applno as appl_no, ue148_applstatus as appl_status, u95a4_intrate as int_rate, u65d4_loanamount as loan_amount, u1bb6_loanterm as loan_term, u9af8_propaddress as prop_address, u5163_settledate as settle_date, u3018_brokername as broker_name FROM docversion_source WHERE object_class_id='9F60F610000018C4B4D37ED44915FDE4'");
            exportToCSV(conn, "TransactionRecord", "TransactionRecord_MockData.csv", "SELECT object_id, object_class_id, u0cb8_bpayref as bpay_ref, u1058_payeename as payee_name, u63b4_runbalance as run_balance, u4ed4_txnamount as txn_amount, u8ee3_txndate as txn_date, u9b28_txndesc as txn_desc, ua0d8_txnid as txn_id, u9c18_txnstatus as txn_status, u9528_txntype as txn_type FROM docversion_source WHERE object_class_id='9F60F6D0000017C78F8D0B116D0C2C79'");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void exportToCSV(Connection conn, String className, String filename, String query) throws Exception {
        System.out.println("Exporting " + className + "...");
        try (PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery();
             PrintWriter writer = new PrintWriter(new File("d:/MigrationReportTool/" + filename))) {
            
            ResultSetMetaData meta = rs.getMetaData();
            int numCols = meta.getColumnCount();
            
            // Header
            for (int i = 1; i <= numCols; i++) {
                writer.print(meta.getColumnLabel(i));
                if (i < numCols) writer.print(",");
            }
            writer.println();
            
            // Data rows
            int count = 0;
            while (rs.next()) {
                for (int i = 1; i <= numCols; i++) {
                    String val = rs.getString(i);
                    if (val == null) {
                        writer.print("");
                    } else {
                        // Escape quotes and wrap in quotes if contains comma
                        if (val.contains(",") || val.contains("\"")) {
                            writer.print("\"" + val.replace("\"", "\"\"") + "\"");
                        } else {
                            writer.print(val);
                        }
                    }
                    if (i < numCols) writer.print(",");
                }
                writer.println();
                count++;
            }
            System.out.println("Exported " + count + " records for " + className + " to " + filename);
        }
    }
}
