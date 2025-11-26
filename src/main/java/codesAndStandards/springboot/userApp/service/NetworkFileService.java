package codesAndStandards.springboot.userApp.service;

import jcifs.CIFSContext;
import jcifs.config.PropertyConfiguration;
import jcifs.context.BaseContext;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Service
public class NetworkFileService {

    private static final Logger logger = LoggerFactory.getLogger(NetworkFileService.class);

    @Value("${network.share.username}")
    private String username;

    @Value("${network.share.password}")
    private String password;

    @Value("${network.share.domain}")
    private String domain;

    @Value("${network.share.host}")
    private String host;

    @Value("${network.share.share}")
    private String shareName;

    @Value("${network.share.folder:#{null}}")
    private String folder;

    // ================= READ FILE =================
    public byte[] readFileFromNetworkShare(String filePath) throws Exception {
        logger.info("Reading file from network: {}", filePath);
        String smbPath = convertToSmbUrl(filePath);

        try (InputStream is = new SmbFile(smbPath, getAuthContext()).getInputStream()) {
            byte[] data = is.readAllBytes();
            logger.info("Successfully read {} bytes from {}", data.length, smbPath);
            return data;
        } catch (Exception e) {
            logger.error("Error reading SMB file: {}", e.getMessage(), e);
            throw new Exception("Failed to read SMB file: " + e.getMessage(), e);
        }
    }

    // ================= STORE FILE =================
    public String storeFile(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new Exception("File is empty or null");
        }

        // Clean filename
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new Exception("Original filename is null");
        }

        originalFilename = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");

        String extension = "";
        String nameWithoutExt = originalFilename;

        if (originalFilename.contains(".")) {
            int lastDot = originalFilename.lastIndexOf(".");
            extension = originalFilename.substring(lastDot);
            nameWithoutExt = originalFilename.substring(0, lastDot);
        }

        String uniqueFileName = nameWithoutExt + "_" + System.currentTimeMillis() + extension;

        logger.info("Attempting to store file: {}", uniqueFileName);
        logger.info("File size: {} bytes", file.getSize());

        // Try Method 1: Direct SMB write
        try {
            return storeFileDirectSMB(file, uniqueFileName);
        } catch (Exception e) {
            logger.warn("Direct SMB write failed: {}. Trying alternative method...", e.getMessage());
        }

        // Try Method 2: Write to temp file first, then copy
        try {
            return storeFileViaTempFile(file, uniqueFileName);
        } catch (Exception e) {
            logger.error("Both storage methods failed", e);
            throw new Exception("Failed to upload file: " + e.getMessage(), e);
        }
    }

    // Method 1: Direct SMB Write (Fixed to always use USERDATA/Abhay)
    private String storeFileDirectSMB(MultipartFile file, String uniqueFileName) throws Exception {
        String smbDirPath = String.format("smb://%s/%s/USERDATA/Abhay/", host, shareName);
        String smbFilePath = smbDirPath + uniqueFileName;

        logger.info("Method 1 - Direct SMB write to: {}", smbFilePath);

        CIFSContext authContext = getAuthContext();

        SmbFile dir = new SmbFile(smbDirPath, authContext);
        if (!dir.exists()) {
            logger.info("Creating target directory: {}", smbDirPath);
            dir.mkdirs();
        }

        if (!dir.canWrite()) {
            throw new Exception("No write permission to " + smbDirPath);
        }

        SmbFile smbFile = new SmbFile(smbFilePath, authContext);
        try (OutputStream os = smbFile.getOutputStream()) {
            os.write(file.getBytes());
            os.flush();
        }

        logger.info("✅ File written successfully to {}", smbFilePath);
        return uniqueFileName;
    }


    // Method 2: Temp File Copy (Fixed path too)
    private String storeFileViaTempFile(MultipartFile file, String uniqueFileName) throws Exception {
        logger.info("Method 2 - Writing via temp file");

        java.io.File tempFile = java.io.File.createTempFile("upload_", "_" + uniqueFileName);
        file.transferTo(tempFile);

        String smbDirPath = String.format("smb://%s/%s/USERDATA/Abhay/", host, shareName);
        String smbFilePath = smbDirPath + uniqueFileName;

        CIFSContext authContext = getAuthContext();
        SmbFile smbDir = new SmbFile(smbDirPath, authContext);
        if (!smbDir.exists()) smbDir.mkdirs();

        SmbFile smbFile = new SmbFile(smbFilePath, authContext);
        try (java.io.FileInputStream fis = new java.io.FileInputStream(tempFile);
             OutputStream os = smbFile.getOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.flush();
        } finally {
            tempFile.delete();
        }

        logger.info("✅ File successfully copied to {}", smbFilePath);
        return uniqueFileName;
    }


    // ================= DELETE FILE =================
    public void deleteFile(String filePath) throws Exception {
        if (filePath == null || filePath.isEmpty()) {
            logger.warn("Delete requested with empty file path");
            return;
        }

        try {
            String smbPath = convertToSmbUrl(filePath);
            logger.info("Attempting to delete file: {}", smbPath);

            SmbFile smbFile = new SmbFile(smbPath, getAuthContext());

            if (smbFile.exists()) {
                if (!smbFile.canWrite()) {
                    logger.warn("No write permission for file: {}", smbPath);
                    throw new Exception("No permission to delete file");
                }

                smbFile.delete();
                logger.info("Successfully deleted file from network share: {}", smbPath);
            } else {
                logger.warn("File not found (may already be deleted): {}", smbPath);
            }

        } catch (jcifs.smb.SmbAuthException e) {
            logger.error("Authentication failed while deleting: {}", e.getMessage(), e);
            throw new Exception("Access denied while deleting file.", e);
        } catch (jcifs.smb.SmbException e) {
            logger.error("SMB error deleting file: {}", e.getMessage(), e);
            throw new Exception("Network share error while deleting: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Error deleting file: {}", e.getMessage(), e);
            throw new Exception("Failed to delete file: " + e.getMessage(), e);
        }
    }

    // ================= GET FULL FILE PATH =================
//    public String getFullFilePath(String fileName) {
//        if (folder != null && !folder.isEmpty()) {
//            return String.format("\\\\%s\\%s\\%s\\%s", host, shareName, folder, fileName);
//        }
//        return String.format("\\\\%s\\%s\\%s", host, shareName, fileName);
//    }

    public String getFullFilePath(String fileName) {
        return String.format("\\\\%s\\%s\\USERDATA\\Abhay\\%s", host, shareName, fileName);
    }


    // ================= CHECK FILE EXISTS =================
    public boolean fileExists(String filePath) {
        try {
            String smbPath = convertToSmbUrl(filePath);
            SmbFile smbFile = new SmbFile(smbPath, getAuthContext());
            return smbFile.exists();
        } catch (Exception e) {
            logger.error("Error checking file existence: {}", e.getMessage());
            return false;
        }
    }

    // ================= TEST CONNECTION =================
    public boolean testConnection() {
        try {
            String testPath;
            if (folder != null && !folder.isEmpty()) {
                testPath = String.format("smb://%s/%s/%s/", host, shareName, folder);
            } else {
                testPath = String.format("smb://%s/%s/", host, shareName);
            }

            SmbFile smbFile = new SmbFile(testPath, getAuthContext());
            boolean exists = smbFile.exists();
            boolean canRead = smbFile.canRead();
            boolean canWrite = smbFile.canWrite();

            logger.info("Connection test - Exists: {}, CanRead: {}, CanWrite: {}", exists, canRead, canWrite);
            return exists && canRead && canWrite;
        } catch (Exception e) {
            logger.error("Connection test failed: {}", e.getMessage(), e);
            return false;
        }
    }

    // Add getters for diagnostic purposes
    public String getHost() {
        return host;
    }

    public String getShareName() {
        return shareName;
    }

    public String getFolder() {
        return folder;
    }

    // ================= CHECK PERMISSIONS =================
    public Map<String, Object> checkPermissions() {
        Map<String, Object> result = new HashMap<>();
        try {
            String testPath;
            if (folder != null && !folder.isEmpty()) {
                testPath = String.format("smb://%s/%s/%s/", host, shareName, folder);
            } else {
                testPath = String.format("smb://%s/%s/", host, shareName);
            }

            logger.info("Checking permissions for: {}", testPath);

            CIFSContext authContext = getAuthContext();
            SmbFile smbFile = new SmbFile(testPath, authContext);

            result.put("path", testPath);
            result.put("exists", smbFile.exists());
            result.put("isDirectory", smbFile.isDirectory());
            result.put("canRead", smbFile.canRead());
            result.put("canWrite", smbFile.canWrite());
            result.put("type", smbFile.getType());

            // Try to list files
            try {
                String[] files = smbFile.list();
                result.put("fileCount", files != null ? files.length : 0);
                if (files != null && files.length > 0) {
                    result.put("sampleFiles", Arrays.copyOf(files, Math.min(5, files.length)));
                }
            } catch (Exception e) {
                result.put("listError", e.getMessage());
            }

            logger.info("Permission check results: {}", result);
            return result;

        } catch (Exception e) {
            logger.error("Error checking permissions: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
            return result;
        }
    }

    // ================= INTERNAL HELPERS =================
    private CIFSContext getAuthContext() throws Exception {
        try {
            Properties prop = new Properties();
            prop.setProperty("jcifs.smb.client.minVersion", "SMB202");
            prop.setProperty("jcifs.smb.client.maxVersion", "SMB311");
            prop.setProperty("jcifs.resolveOrder", "DNS");
            prop.setProperty("jcifs.smb.client.responseTimeout", "30000");
            prop.setProperty("jcifs.smb.client.connTimeout", "30000");
            prop.setProperty("jcifs.smb.client.soTimeout", "30000");
            prop.setProperty("jcifs.smb.client.dfs.disabled", "false");

            PropertyConfiguration config = new PropertyConfiguration(prop);
            CIFSContext baseContext = new BaseContext(config);

            NtlmPasswordAuthenticator auth;

            if (domain != null && !domain.isEmpty() && !"WORKGROUP".equalsIgnoreCase(domain)) {
                auth = new NtlmPasswordAuthenticator(domain, username, password);
                logger.debug("Using domain authentication: {}\\{}", domain, username);
            } else {
                auth = new NtlmPasswordAuthenticator(null, username, password);
                logger.debug("Using local authentication: {}", username);
            }

            return baseContext.withCredentials(auth);
        } catch (Exception e) {
            logger.error("Failed to create auth context: {}", e.getMessage(), e);
            throw new Exception("Failed to authenticate with network share", e);
        }
    }

    private String convertToSmbUrl(String uncPath) {
        if (uncPath == null || uncPath.isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty");
        }

        String smbPath = uncPath;
        if (smbPath.startsWith("\\\\")) {
            smbPath = smbPath.substring(2);
        }
        smbPath = smbPath.replace("\\", "/");

        if (!smbPath.startsWith("smb://")) {
            smbPath = "smb://" + smbPath;
        }

        logger.debug("Converted UNC path '{}' to SMB URL '{}'", uncPath, smbPath);
        return smbPath;
    }
}