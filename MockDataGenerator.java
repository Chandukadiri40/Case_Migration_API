import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class MockDataGenerator {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/Migration";
        String user = "postgres";
        String password = "postgres"; 

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {

            System.out.println("Updating Mock Data for BankAccount...");
            String[] tables = {"docversion_source", "docversion_stagging", "docversion_target"};
            
            for (String table : tables) {
                // BankAccount
                stmt.executeUpdate(
                    "UPDATE " + table + " " +
                    "SET ua598_accholdername = 'Test User ' || right(object_id, 4), " +
                    "    u3a18_accno = 'ACC' || right(object_id, 6), " +
                    "    ua3a3_opendate = '2023-01-15T00:00:00Z', " +
                    "    u5058_acctstatus = 'Active', " +
                    "    u46a8_accttype = 'Savings', " +
                    "    uaf94_availbalance = '10500.50', " +
                    "    u7908_bsbnumber = '123-456', " +
                    "    u9ba8_branchname = 'Sydney CBD', " +
                    "    u63a4_currentbalance = '10500.50', " +
                    "    uf8f8_custid = 'CUST' || right(object_id, 4) " +
                    "WHERE object_class_id = '9F60F3B0000013CF8635EC86D1FA7702'"
                );

                // HomeLoanApplication
                stmt.executeUpdate(
                    "UPDATE " + table + " " +
                    "SET u7ec8_applname = 'Home Loan ' || right(object_id, 4), " +
                    "    u90b8_applno = 'HL' || right(object_id, 6), " +
                    "    ue148_applstatus = 'Approved', " +
                    "    u95a4_intrate = '4.25', " +
                    "    u65d4_loanamount = '500000.00', " +
                    "    u1bb6_loanterm = '360', " +
                    "    u9af8_propaddress = '123 Fake Street, Sydney NSW 2000', " +
                    "    u5163_settledate = '2023-03-01T00:00:00Z', " +
                    "    u3018_brokername = 'Aussie Home Loans' " +
                    "WHERE object_class_id = '9F60F610000018C4B4D37ED44915FDE4'"
                );

                // TransactionRecord
                stmt.executeUpdate(
                    "UPDATE " + table + " " +
                    "SET u0cb8_bpayref = 'BPAY' || right(object_id, 6), " +
                    "    u1058_payeename = 'Telstra', " +
                    "    u63b4_runbalance = '10400.50', " +
                    "    u4ed4_txnamount = '100.00', " +
                    "    u8ee3_txndate = '2023-04-10T00:00:00Z', " +
                    "    u9b28_txndesc = 'Internet Bill', " +
                    "    ua0d8_txnid = 'TXN' || right(object_id, 6), " +
                    "    u9c18_txnstatus = 'Completed', " +
                    "    u9528_txntype = 'Debit' " +
                    "WHERE object_class_id = '9F60F6D0000017C78F8D0B116D0C2C79'"
                );
            }
            
            System.out.println("Mock Data Generation Complete!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
