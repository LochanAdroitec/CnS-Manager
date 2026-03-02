package codesAndStandards.springboot.userApp.service;

import codesAndStandards.springboot.userApp.dto.ApplicationSettingsDto;
import java.util.List;
import java.util.Map;

public interface ApplicationSettingsService {

    // ==================== GENERAL ====================
    ApplicationSettingsDto getAllSettings();
    Integer getSettingAsInteger(String settingName, Integer defaultValue);
    String getSetting(String settingName);
    String getRepositoryPath();

    // ==================== REPOSITORY ====================
    void updateRepositorySettings(Integer maxFileSizeMb, String allowedFiles, String username, boolean skipFileCheck) throws Exception;
    boolean isFormatAllowed(String format);
    Integer getMaxFileSizeMB();
    List<String> getAllowedFormats();
    int countFilesExceedingSize(int newMaxSizeMb);

    // ==================== METADATA SCHEMA ====================
    Map<String, Boolean> getMetadataSchema();
    void updateMetadataSchema(Map<String, Boolean> metadataSettings, String username) throws Exception;
    boolean isMetadataFieldRequired(String fieldName);

    // ==================== TAG POLICIES ====================
    Map<String, Object> getTagPolicies();
    void updateTagPolicies(Integer maxTagsPerDocument, String username) throws Exception;
    Integer getMaxTagsPerDocument();

    // ==================== WATERMARK ====================
    Map<String, Object> getWatermarkSettings();
    void updateWatermarkSettings(Boolean watermarkEnabled, Integer watermarkOpacity, String watermarkPosition, Integer watermarkFontSize, String username) throws Exception;
    Boolean isWatermarkEnabled();
    Integer getWatermarkOpacity();
    String getWatermarkPosition();
    Integer getWatermarkFontSize(); // ⭐ NEW METHOD

    // ==================== SECURITY & ACCESS ====================
    Map<String, Object> getSecuritySettings();
    void updateSecuritySettings(Integer sessionTimeoutHours, Boolean enforcePasswordPolicy,
                                Integer minPasswordLength, Boolean requireUppercase,
                                Boolean requireLowercase, Boolean requireNumber,
                                Boolean requireSpecialChar, String username) throws Exception;
    Boolean isPasswordPolicyEnforced();
    Map<String, Object> getPasswordPolicy();

    // ==================== ACTIVITY LOGGING ====================
    Map<String, Object> getActivityLoggingSettings();
    void updateActivityLoggingSettings(Boolean activityLoggingEnabled, Integer logRetentionDays, String username) throws Exception;
    Boolean isActivityLoggingEnabled();
    Integer getLogRetentionDays();

    // ==================== BULK DELETE ====================
    boolean verifyAdminPassword(String username, String rawPassword);
    int bulkDeleteUsers(List<Long> userIds, String adminUsername) throws Exception;
    int bulkDeleteDocuments(List<Long> documentIds, String adminUsername) throws Exception;
    List<Map<String, Object>> getAllUsersForBulkDelete(String currentUsername);
    List<Map<String, Object>> getAllDocumentsForBulkDelete();
}