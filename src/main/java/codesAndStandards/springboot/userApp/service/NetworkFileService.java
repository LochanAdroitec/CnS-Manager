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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
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



    public byte[] readFileFromNetworkShare(String filePath) throws Exception {
        logger.info("Attempting to read file from network share: {}", filePath);

        // Convert Windows UNC path to SMB URL
        // \\172.16.20.241\DEV-FileServer\USERDATA\Abhay\file.pdf
        // becomes smb://172.16.20.241/DEV-FileServer/USERDATA/Abhay/file.pdf
        String smbPath = convertToSmbUrl(filePath);
        logger.info("Converted SMB path: {}", smbPath);

        try {
            // Configure JCIFS properties for SMB2/SMB3
            Properties prop = new Properties();
            prop.setProperty("jcifs.smb.client.minVersion", "SMB210");
            prop.setProperty("jcifs.smb.client.maxVersion", "SMB311");
            prop.setProperty("jcifs.resolveOrder", "DNS");

            PropertyConfiguration config = new PropertyConfiguration(prop);
            CIFSContext baseContext = new BaseContext(config);

            // Create authentication
            NtlmPasswordAuthenticator auth = new NtlmPasswordAuthenticator(
                    domain, username, password
            );

            CIFSContext authContext = baseContext.withCredentials(auth);

            // Access the file
            SmbFile smbFile = new SmbFile(smbPath, authContext);

            if (!smbFile.exists()) {
                throw new Exception("File does not exist on network share: " + smbPath);
            }

            if (!smbFile.canRead()) {
                throw new Exception("Cannot read file from network share: " + smbPath);
            }

            logger.info("File found and readable, size: {} bytes", smbFile.length());

            // Read file content
            try (InputStream is = smbFile.getInputStream();
                 ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {

                byte[] data = new byte[16384]; // 16KB buffer
                int nRead;
                while ((nRead = is.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }

                byte[] result = buffer.toByteArray();
                logger.info("Successfully read {} bytes from network share", result.length);
                return result;
            }

        } catch (Exception e) {
            logger.error("Error reading file from network share: {}", e.getMessage(), e);
            throw new Exception("Failed to read file from network share: " + e.getMessage(), e);
        }
    }

    private String convertToSmbUrl(String uncPath) {
        // Convert \\172.16.20.241\DEV-FileServer\path\file.pdf
        // to smb://172.16.20.241/DEV-FileServer/path/file.pdf

        String smbPath = uncPath;

        // Remove leading backslashes
        if (smbPath.startsWith("\\\\")) {
            smbPath = smbPath.substring(2);
        }

        // Replace backslashes with forward slashes
        smbPath = smbPath.replace("\\", "/");

        // Add smb:// prefix
        if (!smbPath.startsWith("smb://")) {
            smbPath = "smb://" + smbPath;
        }

        return smbPath;
    }
}