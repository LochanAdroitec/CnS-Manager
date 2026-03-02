package codesAndStandards.springboot.userApp.controller;

import codesAndStandards.springboot.userApp.dto.ApplicationSettingsDto;
import codesAndStandards.springboot.userApp.entity.User;
import codesAndStandards.springboot.userApp.repository.UserRepository;
import codesAndStandards.springboot.userApp.service.ActivityLogService;
import codesAndStandards.springboot.userApp.service.ApplicationSettingsService;
import codesAndStandards.springboot.userApp.service.LicenseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/settings")
@PreAuthorize("hasAuthority('Admin')")
public class ApplicationSettingsController {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationSettingsController.class);

    @Autowired
    private LicenseService licenseService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApplicationSettingsService applicationSettingsService;

    @Autowired
    private ActivityLogService activityLogService;

    // ==================== GET ALL SETTINGS ====================

    @GetMapping
    public ResponseEntity<?> getAllSettings() {
        try {
            if (!licenseService.isLicenseValid())
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Invalid or expired license"));
            ApplicationSettingsDto settings = applicationSettingsService.getAllSettings();
            return ResponseEntity.ok(settings);
        } catch (Exception e) {
            logger.error("❌ Error fetching settings: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch settings: " + e.getMessage()));
        }
    }

    // ==================== REPOSITORY SETTINGS ====================

    @PutMapping("/repository")
    public ResponseEntity<?> updateRepositorySettings(@RequestBody Map<String, Object> payload, Authentication authentication) {
        String username = authentication.getName();
        User currentUser = userRepository.findByUsername(username);
        try {
            if (!licenseService.isLicenseValid())
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Invalid or expired license"));

            Integer maxFileSizeMb = (Integer) payload.get("maxFileSizeMb");
            String allowedFiles = (String) payload.get("allowedFiles");

            if (maxFileSizeMb == null)
                return ResponseEntity.badRequest().body(Map.of("error", "Max file size is required"));
            if (allowedFiles == null || allowedFiles.trim().isEmpty())
                return ResponseEntity.badRequest().body(Map.of("error", "At least one file format must be selected"));

            try {
                applicationSettingsService.updateRepositorySettings(maxFileSizeMb, allowedFiles, username, false);

                // ✅ LOG SUCCESS
                activityLogService.log(currentUser, ActivityLogService.SETTINGS_REPOSITORY_UPDATE,
                        "Max file size set to " + maxFileSizeMb + "MB, allowed formats: " + allowedFiles);

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Repository settings updated successfully");
                response.put("maxFileSizeMb", maxFileSizeMb);
                response.put("allowedFiles", allowedFiles);
                return ResponseEntity.ok(response);

            } catch (IllegalStateException e) {
                Integer currentMaxSize = applicationSettingsService.getMaxFileSizeMB();
                int affectedCount = applicationSettingsService.countFilesExceedingSize(maxFileSizeMb);
                Map<String, Object> response = new HashMap<>();
                response.put("needsConfirmation", true);
                response.put("affectedFilesCount", affectedCount);
                response.put("currentMaxSize", currentMaxSize);
                response.put("newMaxSize", maxFileSizeMb);
                response.put("message", e.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            // ✅ LOG FAILURE
            activityLogService.log(currentUser, ActivityLogService.SETTINGS_REPOSITORY_UPDATE_FAIL,
                    "Failed to update repository settings: " + e.getMessage());
            logger.error("❌ Error updating repository settings: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update settings: " + e.getMessage()));
        }
    }

    @PostMapping("/repository/confirm")
    public ResponseEntity<?> confirmRepositoryUpdate(@RequestBody Map<String, Object> payload, Authentication authentication) {
        String username = authentication.getName();
        User currentUser = userRepository.findByUsername(username);
        try {
            if (!licenseService.isLicenseValid())
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Invalid or expired license"));

            Integer maxFileSizeMb = (Integer) payload.get("maxFileSizeMb");
            String allowedFiles = (String) payload.get("allowedFiles");
            applicationSettingsService.updateRepositorySettings(maxFileSizeMb, allowedFiles, username, true);

            // ✅ LOG SUCCESS (confirmed override)
            activityLogService.log(currentUser, ActivityLogService.SETTINGS_REPOSITORY_UPDATE,
                    "Repository settings confirmed - Max file size: " + maxFileSizeMb + "MB, formats: " + allowedFiles);

            return ResponseEntity.ok(Map.of("success", true, "message", "Repository settings updated successfully"));
        } catch (Exception e) {
            // ✅ LOG FAILURE
            activityLogService.log(currentUser, ActivityLogService.SETTINGS_REPOSITORY_UPDATE_FAIL,
                    "Failed to confirm repository settings update: " + e.getMessage());
            logger.error("❌ Error confirming settings update: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update settings: " + e.getMessage()));
        }
    }

    // ==================== METADATA SCHEMA ====================

    @GetMapping("/metadata")
    public ResponseEntity<?> getMetadataSchema() {
        try {
            if (!licenseService.isLicenseValid())
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Invalid or expired license"));
            return ResponseEntity.ok(applicationSettingsService.getMetadataSchema());
        } catch (Exception e) {
            logger.error("❌ Error fetching metadata schema: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch metadata schema: " + e.getMessage()));
        }
    }

    @PutMapping("/metadata")
    public ResponseEntity<?> updateMetadataSchema(@RequestBody Map<String, Boolean> metadataSettings, Authentication authentication) {
        String username = authentication.getName();
        User currentUser = userRepository.findByUsername(username);
        try {
            if (!licenseService.isLicenseValid())
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Invalid or expired license"));

            applicationSettingsService.updateMetadataSchema(metadataSettings, username);

            // ✅ LOG SUCCESS — build a readable summary of what fields are required
            List<String> enabledFields = metadataSettings.entrySet().stream()
                    .filter(Map.Entry::getValue)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            String fieldSummary = enabledFields.isEmpty() ? "none required" : String.join(", ", enabledFields);
            activityLogService.log(currentUser, ActivityLogService.SETTINGS_METADATA_UPDATE,
                    "Metadata required fields updated: " + fieldSummary);

            return ResponseEntity.ok(Map.of("success", true, "message", "Metadata schema updated successfully"));
        } catch (Exception e) {
            // ✅ LOG FAILURE
            activityLogService.log(currentUser, ActivityLogService.SETTINGS_METADATA_UPDATE_FAIL,
                    "Failed to update metadata schema: " + e.getMessage());
            logger.error("❌ Error updating metadata schema: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update metadata schema: " + e.getMessage()));
        }
    }

    // ==================== TAG POLICIES ====================

    @GetMapping("/tag-policies")
    public ResponseEntity<?> getTagPolicies() {
        try {
            if (!licenseService.isLicenseValid())
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Invalid or expired license"));
            return ResponseEntity.ok(applicationSettingsService.getTagPolicies());
        } catch (Exception e) {
            logger.error("❌ Error fetching tag policies: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch tag policies: " + e.getMessage()));
        }
    }

    @PutMapping("/tag-policies")
    public ResponseEntity<?> updateTagPolicies(@RequestBody Map<String, Object> payload, Authentication authentication) {
        String username = authentication.getName();
        User currentUser = userRepository.findByUsername(username);
        try {
            if (!licenseService.isLicenseValid())
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Invalid or expired license"));

            Integer maxTagsPerDocument = (Integer) payload.get("maxTagsPerDocument");
            if (maxTagsPerDocument == null)
                return ResponseEntity.badRequest().body(Map.of("error", "Maximum tags per document is required"));

            applicationSettingsService.updateTagPolicies(maxTagsPerDocument, username);

            // ✅ LOG SUCCESS
            activityLogService.log(currentUser, ActivityLogService.SETTINGS_TAG_POLICY_UPDATE,
                    "Max tags per document set to " + maxTagsPerDocument);

            return ResponseEntity.ok(Map.of("success", true, "message", "Tag policies updated successfully",
                    "maxTagsPerDocument", maxTagsPerDocument));
        } catch (IllegalArgumentException e) {
            // ✅ LOG FAILURE
            activityLogService.log(currentUser, ActivityLogService.SETTINGS_TAG_POLICY_UPDATE_FAIL,
                    "Invalid tag policy value: " + e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            // ✅ LOG FAILURE
            activityLogService.log(currentUser, ActivityLogService.SETTINGS_TAG_POLICY_UPDATE_FAIL,
                    "Failed to update tag policies: " + e.getMessage());
            logger.error("❌ Error updating tag policies: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update tag policies: " + e.getMessage()));
        }
    }

    // ==================== WATERMARK SETTINGS ====================

    @GetMapping("/watermark")
    public ResponseEntity<?> getWatermarkSettings() {
        try {
            if (!licenseService.isLicenseValid())
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Invalid or expired license"));
            return ResponseEntity.ok(applicationSettingsService.getWatermarkSettings());
        } catch (Exception e) {
            logger.error("❌ Error fetching watermark settings: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch watermark settings: " + e.getMessage()));
        }
    }

    @PutMapping("/watermark")
    public ResponseEntity<?> updateWatermarkSettings(@RequestBody Map<String, Object> payload, Authentication authentication) {
        String username = authentication.getName();
        User currentUser = userRepository.findByUsername(username);
        try {
            if (!licenseService.isLicenseValid())
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Invalid or expired license"));

            Boolean watermarkEnabled = (Boolean) payload.get("watermarkEnabled");
            Integer watermarkOpacity = (Integer) payload.get("watermarkOpacity");
            String watermarkPosition = (String) payload.get("watermarkPosition");
            Integer watermarkFontSize = (Integer) payload.get("watermarkFontSize");

            if (watermarkEnabled == null)
                return ResponseEntity.badRequest().body(Map.of("error", "Watermark enabled status is required"));
            if (watermarkOpacity == null)
                return ResponseEntity.badRequest().body(Map.of("error", "Watermark opacity is required"));
            if (watermarkPosition == null || watermarkPosition.trim().isEmpty())
                return ResponseEntity.badRequest().body(Map.of("error", "Watermark position is required"));
            if (watermarkFontSize == null)
                return ResponseEntity.badRequest().body(Map.of("error", "Watermark font size is required"));

            applicationSettingsService.updateWatermarkSettings(watermarkEnabled, watermarkOpacity,
                    watermarkPosition, watermarkFontSize, username);

            // ✅ LOG SUCCESS
            activityLogService.log(currentUser, ActivityLogService.SETTINGS_WATERMARK_UPDATE,
                    "Watermark updated - Enabled: " + watermarkEnabled
                            + ", Opacity: " + watermarkOpacity + "%"
                            + ", Position: " + watermarkPosition
                            + ", Font size: " + watermarkFontSize + "pt");

            return ResponseEntity.ok(Map.of("success", true, "message", "Watermark settings updated successfully"));
        } catch (IllegalArgumentException e) {
            // ✅ LOG FAILURE
            activityLogService.log(currentUser, ActivityLogService.SETTINGS_WATERMARK_UPDATE_FAIL,
                    "Invalid watermark value: " + e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            // ✅ LOG FAILURE
            activityLogService.log(currentUser, ActivityLogService.SETTINGS_WATERMARK_UPDATE_FAIL,
                    "Failed to update watermark settings: " + e.getMessage());
            logger.error("❌ Error updating watermark settings: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update watermark settings: " + e.getMessage()));
        }
    }

    // ==================== SECURITY & ACCESS ====================

    @GetMapping("/security")
    public ResponseEntity<?> getSecuritySettings() {
        try {
            if (!licenseService.isLicenseValid())
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Invalid or expired license"));
            return ResponseEntity.ok(applicationSettingsService.getSecuritySettings());
        } catch (Exception e) {
            logger.error("❌ Error fetching security settings: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch security settings: " + e.getMessage()));
        }
    }

    @PutMapping("/security")
    public ResponseEntity<?> updateSecuritySettings(@RequestBody Map<String, Object> payload, Authentication authentication) {
        String username = authentication.getName();
        User currentUser = userRepository.findByUsername(username);
        try {
            if (!licenseService.isLicenseValid())
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Invalid or expired license"));

            Integer sessionTimeoutHours  = (Integer) payload.get("sessionTimeoutHours");
            Boolean enforcePasswordPolicy = (Boolean) payload.get("enforcePasswordPolicy");
            Integer minPasswordLength     = (Integer) payload.get("minPasswordLength");
            Boolean requireUppercase      = (Boolean) payload.get("requireUppercase");
            Boolean requireLowercase      = (Boolean) payload.get("requireLowercase");
            Boolean requireNumber         = (Boolean) payload.get("requireNumber");
            Boolean requireSpecialChar    = (Boolean) payload.get("requireSpecialChar");

            applicationSettingsService.updateSecuritySettings(sessionTimeoutHours, enforcePasswordPolicy,
                    minPasswordLength, requireUppercase, requireLowercase, requireNumber, requireSpecialChar, username);

            // ✅ LOG SUCCESS
            activityLogService.log(currentUser, ActivityLogService.SETTINGS_SECURITY_UPDATE,
                    "Security updated - Session timeout: " + sessionTimeoutHours + "h"
                            + ", Password policy enforced: " + enforcePasswordPolicy
                            + (Boolean.TRUE.equals(enforcePasswordPolicy)
                            ? ", Min length: " + minPasswordLength
                            + ", Uppercase: " + requireUppercase
                            + ", Lowercase: " + requireLowercase
                            + ", Number: " + requireNumber
                            + ", Special char: " + requireSpecialChar
                            : ""));

            return ResponseEntity.ok(Map.of("success", true, "message", "Security settings updated successfully"));
        } catch (IllegalArgumentException e) {
            // ✅ LOG FAILURE
            activityLogService.log(currentUser, ActivityLogService.SETTINGS_SECURITY_UPDATE_FAIL,
                    "Invalid security setting value: " + e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            // ✅ LOG FAILURE
            activityLogService.log(currentUser, ActivityLogService.SETTINGS_SECURITY_UPDATE_FAIL,
                    "Failed to update security settings: " + e.getMessage());
            logger.error("❌ Error updating security settings: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update security settings: " + e.getMessage()));
        }
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/password-policy")
    public ResponseEntity<Map<String, Object>> getPasswordPolicy() {
        try {
            Map<String, Object> policy = applicationSettingsService.getPasswordPolicy();
            return ResponseEntity.ok(policy);
        } catch (Exception e) {
            logger.error("Error fetching password policy: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PreAuthorize("hasAnyAuthority('Admin', 'Manager')")
    @GetMapping("/max-tags-per-document")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getMaxTagsPerDocument() {
        try {
            Integer maxTags = applicationSettingsService.getMaxTagsPerDocument();
            Map<String, Object> response = new HashMap<>();
            response.put("maxTagsPerDocument", maxTags);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error fetching max tags setting: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ==================== ACTIVITY LOGGING ====================

    @GetMapping("/logging")
    public ResponseEntity<?> getActivityLoggingSettings() {
        try {
            if (!licenseService.isLicenseValid())
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Invalid or expired license"));
            return ResponseEntity.ok(applicationSettingsService.getActivityLoggingSettings());
        } catch (Exception e) {
            logger.error("❌ Error fetching logging settings: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch logging settings: " + e.getMessage()));
        }
    }

    @PutMapping("/logging")
    public ResponseEntity<?> updateActivityLoggingSettings(@RequestBody Map<String, Object> payload, Authentication authentication) {
        String username = authentication.getName();
        User currentUser = userRepository.findByUsername(username);
        try {
            if (!licenseService.isLicenseValid())
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Invalid or expired license"));

            Boolean activityLoggingEnabled = (Boolean) payload.get("activityLoggingEnabled");
            Integer logRetentionDays = payload.get("logRetentionDays") != null
                    ? Integer.valueOf(payload.get("logRetentionDays").toString()) : null;

            if (activityLoggingEnabled == null)
                return ResponseEntity.badRequest().body(Map.of("error", "Activity logging enabled status is required"));

            applicationSettingsService.updateActivityLoggingSettings(activityLoggingEnabled, logRetentionDays, username);

            // ✅ LOG SUCCESS
            String details = "Activity logging: " + (activityLoggingEnabled ? "Enabled" : "Disabled");
            if (Boolean.TRUE.equals(activityLoggingEnabled) && logRetentionDays != null) {
                details += ", Retention period: " + logRetentionDays + " days";
            }
            activityLogService.log(currentUser, ActivityLogService.SETTINGS_LOGGING_UPDATE, details);

            return ResponseEntity.ok(Map.of("success", true, "message", "Activity logging settings updated successfully"));
        } catch (IllegalArgumentException e) {
            // ✅ LOG FAILURE
            activityLogService.log(currentUser, ActivityLogService.SETTINGS_LOGGING_UPDATE_FAIL,
                    "Invalid logging setting value: " + e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            // ✅ LOG FAILURE
            activityLogService.log(currentUser, ActivityLogService.SETTINGS_LOGGING_UPDATE_FAIL,
                    "Failed to update logging settings: " + e.getMessage());
            logger.error("❌ Error updating logging settings: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update logging settings: " + e.getMessage()));
        }
    }

    // ==================== BULK DELETE ====================

    @PostMapping("/bulk-delete/verify-password")
    public ResponseEntity<?> verifyAdminPassword(@RequestBody Map<String, String> payload, Authentication authentication) {
        try {
            String username = authentication.getName();
            String password = payload.get("password");
            if (password == null || password.trim().isEmpty())
                return ResponseEntity.badRequest().body(Map.of("error", "Password is required"));
            boolean isValid = applicationSettingsService.verifyAdminPassword(username, password);
            if (isValid) {
                logger.info("✅ Admin password verified for bulk delete by: {}", username);
                return ResponseEntity.ok(Map.of("success", true, "message", "Password verified"));
            } else {
                logger.warn("❌ Incorrect password attempt for bulk delete by: {}", username);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Incorrect password"));
            }
        } catch (Exception e) {
            logger.error("❌ Error verifying password: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to verify password"));
        }
    }

    @GetMapping("/bulk-delete/users-list")
    public ResponseEntity<?> getAllUsersForBulkDelete(Authentication authentication) {
        try {
            String currentUsername = authentication.getName();
            List<Map<String, Object>> users = applicationSettingsService.getAllUsersForBulkDelete(currentUsername);
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            logger.error("❌ Error fetching users list: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to fetch users"));
        }
    }

    @GetMapping("/bulk-delete/documents-list")
    public ResponseEntity<?> getAllDocumentsForBulkDelete() {
        try {
            List<Map<String, Object>> documents = applicationSettingsService.getAllDocumentsForBulkDelete();
            return ResponseEntity.ok(documents);
        } catch (Exception e) {
            logger.error("❌ Error fetching documents list: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to fetch documents"));
        }
    }

    @DeleteMapping("/bulk-delete/users")
    public ResponseEntity<?> bulkDeleteUsers(@RequestBody Map<String, Object> payload, Authentication authentication) {
        String username = authentication.getName();
        User currentUser = userRepository.findByUsername(username);
        try {
            List<Long> userIds = ((List<?>) payload.get("userIds"))
                    .stream().map(id -> Long.valueOf(id.toString())).collect(Collectors.toList());
            if (userIds == null || userIds.isEmpty())
                return ResponseEntity.badRequest().body(Map.of("error", "No users selected"));

            // Prevent self-deletion
            if (currentUser != null) userIds.removeIf(id -> id.equals(currentUser.getId()));

            int deleted = applicationSettingsService.bulkDeleteUsers(userIds, username);

            // ✅ LOG SUCCESS
            activityLogService.log(currentUser, ActivityLogService.SETTINGS_BULK_USER_DELETE,
                    "Bulk deleted " + deleted + " user(s). IDs: " + userIds);

            logger.info("✅ Bulk deleted {} users by: {}", deleted, username);
            return ResponseEntity.ok(Map.of("success", true, "deletedCount", deleted,
                    "message", deleted + " user(s) deleted successfully"));
        } catch (Exception e) {
            // ✅ LOG FAILURE
            activityLogService.log(currentUser, ActivityLogService.SETTINGS_BULK_USER_DELETE_FAIL,
                    "Failed to bulk delete users: " + e.getMessage());
            logger.error("❌ Error bulk deleting users: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete users: " + e.getMessage()));
        }
    }

    @DeleteMapping("/bulk-delete/documents")
    public ResponseEntity<?> bulkDeleteDocuments(@RequestBody Map<String, Object> payload, Authentication authentication) {
        String username = authentication.getName();
        User currentUser = userRepository.findByUsername(username);
        try {
            List<Long> documentIds = ((List<?>) payload.get("documentIds"))
                    .stream().map(id -> Long.valueOf(id.toString())).collect(Collectors.toList());
            if (documentIds == null || documentIds.isEmpty())
                return ResponseEntity.badRequest().body(Map.of("error", "No documents selected"));

            int deleted = applicationSettingsService.bulkDeleteDocuments(documentIds, username);

            // ✅ LOG SUCCESS
            activityLogService.log(currentUser, ActivityLogService.SETTINGS_BULK_DOC_DELETE,
                    "Bulk deleted " + deleted + " document(s). IDs: " + documentIds);

            logger.info("✅ Bulk deleted {} documents by: {}", deleted, username);
            return ResponseEntity.ok(Map.of("success", true, "deletedCount", deleted,
                    "message", deleted + " document(s) deleted successfully"));
        } catch (Exception e) {
            // ✅ LOG FAILURE
            activityLogService.log(currentUser, ActivityLogService.SETTINGS_BULK_DOC_DELETE_FAIL,
                    "Failed to bulk delete documents: " + e.getMessage());
            logger.error("❌ Error bulk deleting documents: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete documents: " + e.getMessage()));
        }
    }
}