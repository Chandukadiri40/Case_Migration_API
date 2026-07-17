import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;

public class PasswordEncryptor {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: java PasswordEncryptor <passwordToEncrypt>");
            System.out.println("Example: java PasswordEncryptor mySuperSecretPassword");
            return;
        }
        
        String plainText = args[0];
        
        byte[] keyBytes = new byte[32];
        Path keyPath = Paths.get(System.getProperty("user.dir"), ".migration-report.key");
        
        if (Files.exists(keyPath) && Files.size(keyPath) >= 32) {
            byte[] fileBytes = Files.readAllBytes(keyPath);
            System.arraycopy(fileBytes, 0, keyBytes, 0, 32);
            System.out.println("Loaded encryption key from: " + keyPath.toAbsolutePath());
        } else {
            System.out.println("ERROR: Could not find a valid .migration-report.key at " + keyPath.toAbsolutePath());
            System.out.println("Please make sure you run this script from the directory containing the key file (e.g. MigrationReportTool/Backend/MigrationReport)");
            return;
        }
        
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
        
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
        byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        
        ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + encryptedBytes.length);
        byteBuffer.put(iv);
        byteBuffer.put(encryptedBytes);
        
        String encryptedResult = "ENC(" + Base64.getEncoder().encodeToString(byteBuffer.array()) + ")";
        
        System.out.println("\n--------------------------------------------------");
        System.out.println("Your encrypted password is:");
        System.out.println(encryptedResult);
        System.out.println("--------------------------------------------------\n");
        System.out.println("You can safely copy and paste this entire ENC(...) string into your db-config.json file.");
    }
}
