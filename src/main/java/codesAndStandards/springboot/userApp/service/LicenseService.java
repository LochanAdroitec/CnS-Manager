package codesAndStandards.springboot.userApp.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import jakarta.annotation.PostConstruct;
import java.io.*;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Service
public class LicenseService {

    private static final String LICENSE_DIR = "config";
    private static final String LICENSE_FILE = "config/license.lic";
    private static final String LICFILE_PWD = "Kbe@Adr";

    private LicenseInfo cachedLicense = null;

    @PostConstruct
    public void init() {
        new File(LICENSE_DIR).mkdirs();
        loadLicenseFromFile();
    }

    /**
     * Load and decrypt license file
     */
    private void loadLicenseFromFile() {
        try {
            File file = new File(LICENSE_FILE);
            if (file.exists()) {
                System.out.println("📄 Loading license from: " + LICENSE_FILE);

                // Decrypt license file
                byte[] decryptedData = decryptFile(LICENSE_FILE);
                String content = new String(decryptedData, StandardCharsets.UTF_8);

                // Parse license content
                this.cachedLicense = parseLicenseContent(content);

                if (this.cachedLicense != null) {
                    System.out.println("✅ License loaded successfully");
                    System.out.println("  Edition: " + this.cachedLicense.getEdition());
                    System.out.println("  System: " + this.cachedLicense.getSystemName());
                    System.out.println("  Expiry: " + this.cachedLicense.getExpiryDate());
                    System.out.println("  Days Remaining: " + getDaysRemaining());
                    System.out.println("  Bulk Upload Allowed: " + isBulkUploadAllowed());
                }
            } else {
                System.out.println("⚠️ No license file found at: " + LICENSE_FILE);
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to load license: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Parse decrypted license content
     * Format:
     * Line 0: LicenseId:<uuid>
     * Line 1: Edition:<ED1|ED2>
     * Line 2: LastDate:<YYYY-MM-DD>
     * Line 3+: ASCII codes of request code
     */
    private LicenseInfo parseLicenseContent(String content) {
        try {
            String[] lines = content.split("\n");

            if (lines.length < 3) {
                System.err.println("Invalid license format: too few lines");
                return null;
            }

            LicenseInfo license = new LicenseInfo();

            // Line 0: LicenseId
            String licenseId = lines[0].split(":")[1].trim();
            license.setLicenseKey(licenseId);

            // Line 1: Edition (NEW)
            String edition = lines[1].split(":")[1].trim();
            license.setEdition(edition);

            // Line 2: LastDate (Expiry)
            String expiryDateStr = lines[2].split(":")[1].trim();
            LocalDateTime expiryDate = LocalDateTime.parse(expiryDateStr + "T23:59:59");
            license.setExpiryDate(expiryDate);
            license.setIssueDate(LocalDateTime.now());

            // Line 3+: Request code verification
            StringBuilder requestCode = new StringBuilder();
            for (int i = 3; i < lines.length; i++) {
                String line = lines[i].trim();
                if (!line.isEmpty()) {
                    try {
                        int ascii = Integer.parseInt(line);
                        requestCode.append((char) ascii);
                    } catch (NumberFormatException e) {
                        // Skip invalid lines
                    }
                }
            }

            // Verify hardware ID from request code
            String currentHardwareId = generateHardwareId();
            license.setHardwareId(currentHardwareId);

            // Extract hardware ID from request code (format: first8chars + date + last8chars)
            String licenseHardwareId = extractHardwareIdFromRequestCode(requestCode.toString());

            if (!currentHardwareId.equals(licenseHardwareId)) {
                System.err.println("❌ Hardware ID mismatch!");
                System.err.println("  Current: " + currentHardwareId);
                System.err.println("  License: " + licenseHardwareId);
                return null;
            }

            license.setSystemName(getSystemName());
            license.setIsActive(true);

            return license;

        } catch (Exception e) {
            System.err.println("Failed to parse license: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Extract hardware ID from request code
     * Request code format: HardwareID(first8) + Date(8) + HardwareID(last8)
     */
    private String extractHardwareIdFromRequestCode(String requestCode) {
        if (requestCode.length() < 24) {
            return "";
        }
        // First 8 chars + last 8 chars
        return requestCode.substring(0, 8) + requestCode.substring(16, 24);
    }

    /**
     * Decrypt license file using AES-256-CBC
     */
    private byte[] decryptFile(String filePath) throws Exception {
        byte[] keyBytes = createKey(LICFILE_PWD);
        byte[] ivBytes = createIv(LICFILE_PWD);

        SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
        IvParameterSpec iv = new IvParameterSpec(ivBytes);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, key, iv);

        File file = new File(filePath);
        byte[] encryptedData = new byte[(int) file.length()];

        try (FileInputStream fis = new FileInputStream(file)) {
            fis.read(encryptedData);
        }

        return cipher.doFinal(encryptedData);
    }

    /**
     * Create 32-byte AES key from password
     */
    private byte[] createKey(String password) throws Exception {
        byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
        MessageDigest sha = MessageDigest.getInstance("SHA-512");
        byte[] hash = sha.digest(passwordBytes);

        // Take first 32 bytes for AES-256
        byte[] key = new byte[32];
        System.arraycopy(hash, 0, key, 0, 32);
        return key;
    }

    /**
     * Create 16-byte IV from password
     */
    private byte[] createIv(String password) throws Exception {
        byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
        MessageDigest sha = MessageDigest.getInstance("SHA-512");
        byte[] hash = sha.digest(passwordBytes);

        // Take bytes 32-47 for IV
        byte[] iv = new byte[16];
        System.arraycopy(hash, 32, iv, 0, 16);
        return iv;
    }

    /**
     * Generate hardware ID based on system properties
     */
    public String generateHardwareId() {
        try {
            StringBuilder systemInfo = new StringBuilder();
            systemInfo.append(System.getProperty("user.name"));
            systemInfo.append(System.getProperty("os.name"));
            systemInfo.append(System.getProperty("os.version"));

            try {
                systemInfo.append(InetAddress.getLocalHost().getHostName());
            } catch (Exception e) {
                systemInfo.append("unknown-host");
            }

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(systemInfo.toString().getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }

            return hexString.substring(0, 16).toUpperCase();

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate hardware ID", e);
        }
    }

    /**
     * Get system name (hostname)
     */
    public String getSystemName() {
        try {
            return InetAddress.getLocalHost().getHostName().toUpperCase();
        } catch (Exception e) {
            return "UNKNOWN-SYSTEM";
        }
    }

    /**
     * Generate request code for license activation
     * Format: HardwareID(first8) + Date(YYYYMMDD) + HardwareID(last8)
     */
    public String generateRequestCode() {
        String hardwareId = generateHardwareId();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        String part1 = hardwareId.substring(0, 8);
        String part2 = hardwareId.substring(8, 16);

        return part1 + timestamp + part2;
    }

    /**
     * Activate license by uploading .lic file
     */
    public boolean activateLicense(MultipartFile uploadedFile) throws Exception {
        // Save uploaded file temporarily
        File tempFile = File.createTempFile("license_temp", ".lic");
        uploadedFile.transferTo(tempFile);

        try {
            // Decrypt and parse
            byte[] decryptedData = decryptFile(tempFile.getAbsolutePath());
            String content = new String(decryptedData, StandardCharsets.UTF_8);

            LicenseInfo newLicense = parseLicenseContent(content);

            if (newLicense == null) {
                throw new Exception("Invalid license file format");
            }

            // Check hardware ID match
            String currentHardwareId = generateHardwareId();
            if (!currentHardwareId.equals(newLicense.getHardwareId())) {
                throw new Exception("License is not valid for this system. Hardware ID mismatch.\n" +
                        "Expected: " + currentHardwareId + "\n" +
                        "Found: " + newLicense.getHardwareId());
            }

            // Check expiry
            if (newLicense.getExpiryDate().isBefore(LocalDateTime.now())) {
                throw new Exception("License has already expired on " +
                        newLicense.getExpiryDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            }

            // Copy to permanent location
            File licenseFile = new File(LICENSE_FILE);
            licenseFile.getParentFile().mkdirs();

            try (FileInputStream fis = new FileInputStream(tempFile);
                 FileOutputStream fos = new FileOutputStream(licenseFile)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = fis.read(buffer)) > 0) {
                    fos.write(buffer, 0, length);
                }
            }

            // Update cached license
            this.cachedLicense = newLicense;

            System.out.println("✅ License activated successfully!");
            System.out.println("  Edition: " + newLicense.getEdition());
            System.out.println("  Expiry: " + newLicense.getExpiryDate());
            System.out.println("  Bulk Upload Allowed: " + isBulkUploadAllowed());

            return true;

        } finally {
            tempFile.delete();
        }
    }

    /**
     * Get current license info
     */
    public LicenseInfo getCurrentLicense() {
        return this.cachedLicense;
    }

    /**
     * Check if license is valid
     */
    public boolean isLicenseValid() {
        if (cachedLicense == null || !cachedLicense.getIsActive()) {
            return false;
        }

        if (cachedLicense.getExpiryDate().isBefore(LocalDateTime.now())) {
            cachedLicense.setIsActive(false);
            return false;
        }

        return true;
    }

    /**
     * Validate license and throw exception if invalid
     */
    public void validateLicenseOrThrow() throws Exception {
        if (!isLicenseValid()) {
            if (cachedLicense == null) {
                throw new Exception("No license found. Please activate a license.");
            } else {
                throw new Exception("License has expired. Please renew your license.");
            }
        }
    }

    /**
     * Get current edition (ED1 or ED2)
     */
    public String getCurrentEdition() {
        return cachedLicense != null ? cachedLicense.getEdition() : null;
    }

    /**
     * Check if bulk upload is allowed (ED2 only)
     */
    public boolean isBulkUploadAllowed() {
        if (!isLicenseValid()) {
            return false;
        }
        return "ED2".equalsIgnoreCase(getCurrentEdition());
    }

    /**
     * Get days remaining until license expiry
     */
    public long getDaysRemaining() {
        if (cachedLicense == null) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiry = cachedLicense.getExpiryDate();

        return ChronoUnit.DAYS.between(now, expiry);
    }

    /**
     * License Info class
     */
    public static class LicenseInfo {
        private String systemName;
        private String hardwareId;
        private String requestCode;
        private String licenseKey;
        private String edition; // ✅ ED1 or ED2
        private LocalDateTime issueDate;
        private LocalDateTime expiryDate;
        private Boolean isActive = false;

        // Getters and Setters
        public String getSystemName() { return systemName; }
        public void setSystemName(String systemName) { this.systemName = systemName; }

        public String getHardwareId() { return hardwareId; }
        public void setHardwareId(String hardwareId) { this.hardwareId = hardwareId; }

        public String getRequestCode() { return requestCode; }
        public void setRequestCode(String requestCode) { this.requestCode = requestCode; }

        public String getLicenseKey() { return licenseKey; }
        public void setLicenseKey(String licenseKey) { this.licenseKey = licenseKey; }

        public String getEdition() { return edition; }
        public void setEdition(String edition) { this.edition = edition; }

        public LocalDateTime getIssueDate() { return issueDate; }
        public void setIssueDate(LocalDateTime issueDate) { this.issueDate = issueDate; }

        public LocalDateTime getExpiryDate() { return expiryDate; }
        public void setExpiryDate(LocalDateTime expiryDate) { this.expiryDate = expiryDate; }

        public Boolean getIsActive() { return isActive; }
        public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    }
}