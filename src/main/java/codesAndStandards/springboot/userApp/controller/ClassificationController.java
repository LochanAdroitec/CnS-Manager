package codesAndStandards.springboot.userApp.controller;

import codesAndStandards.springboot.userApp.dto.ClassificationDto;
import codesAndStandards.springboot.userApp.entity.User;
import codesAndStandards.springboot.userApp.repository.UserRepository;
import codesAndStandards.springboot.userApp.service.ActivityLogService;
import codesAndStandards.springboot.userApp.service.ClassificationService;
import codesAndStandards.springboot.userApp.service.LicenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/classifications")
@RequiredArgsConstructor
//@CrossOrigin(origins = "http://localhost:8080", allowCredentials = "true")
public class ClassificationController {

    private final ClassificationService classificationService;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;
    private static final Logger logger = LoggerFactory.getLogger(ClassificationController.class);

    @Autowired
    private LicenseService licenseService;

    /**
     * ✅ CREATE CLASSIFICATION - ED2 ONLY
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('Manager', 'Admin')")
    public ResponseEntity<?> createClassification(@Valid @RequestBody ClassificationDto classificationDto) {
        String username = getCurrentUsername();

        // ✅ ED2 LICENSE CHECK
        if (!licenseService.isLicenseValid()) {
            logger.warn("License validation failed for createClassification");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "License expired or not found"));
        }

        if (!isClassificationManagementAllowed()) {
            logger.warn("Classification management denied - Edition: {}", licenseService.getCurrentEdition());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(createEditionUpgradeResponse("create classifications"));
        }

        try {
            Long userId = getCurrentUserId();
            ClassificationDto created = classificationService.createClassification(classificationDto, userId);

            activityLogService.logByUsername(username, ActivityLogService.CLASSIFICATION_ADD,
                    "Created classification: '" + classificationDto.getClassificationName() + "'");

            return new ResponseEntity<>(created, HttpStatus.CREATED);
        } catch (Exception e) {
            activityLogService.logByUsername(username, ActivityLogService.CLASSIFICATION_ADD_FAIL,
                    "Failed to create classification: '" + classificationDto.getClassificationName() + "' (Reason: " + e.getMessage() + ")");
            throw e;
        }
    }

    /**
     * ✅ UPDATE CLASSIFICATION - ED2 ONLY
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('Manager', 'Admin')")
    public ResponseEntity<?> updateClassification(@PathVariable Long id,
                                                  @Valid @RequestBody ClassificationDto classificationDto) {
        String username = getCurrentUsername();

        // ✅ ED2 LICENSE CHECK
        if (!licenseService.isLicenseValid()) {
            logger.warn("License validation failed for updateClassification");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "License expired or not found"));
        }

        if (!isClassificationManagementAllowed()) {
            logger.warn("Classification management denied - Edition: {}", licenseService.getCurrentEdition());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(createEditionUpgradeResponse("edit classifications"));
        }

        try {
            Long userId = getCurrentUserId();

            // Fetch old classification name
            ClassificationDto oldClassification = classificationService.getClassificationById(id);
            String oldName = oldClassification.getClassificationName();

            // Update classification
            ClassificationDto updated = classificationService.updateClassification(id, classificationDto, userId);
            String newName = updated.getClassificationName();

            activityLogService.logByUsername(
                    username,
                    ActivityLogService.CLASSIFICATION_EDIT,
                    "Updated classification from '" + oldName + "' to '" + newName + "'"
            );

            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            activityLogService.logByUsername(
                    username,
                    ActivityLogService.CLASSIFICATION_EDIT_FAIL,
                    "Failed to update classification (Reason: " + e.getMessage() + ")"
            );
            throw e;
        }
    }

    /**
     * ✅ DELETE CLASSIFICATION - ED2 ONLY
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('Manager', 'Admin')")
    public ResponseEntity<?> deleteClassification(@PathVariable Long id) {
        String username = getCurrentUsername();

        // ✅ ED2 LICENSE CHECK
        if (!licenseService.isLicenseValid()) {
            logger.warn("License validation failed for deleteClassification");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "License expired or not found"));
        }

        if (!isClassificationManagementAllowed()) {
            logger.warn("Classification management denied - Edition: {}", licenseService.getCurrentEdition());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(createEditionUpgradeResponse("delete classifications"));
        }

        try {
            Long userId = getCurrentUserId();

            // Fetch name before deletion
            ClassificationDto classification = classificationService.getClassificationById(id);
            String className = classification.getClassificationName();

            // Delete classification
            classificationService.deleteClassification(id, userId);

            activityLogService.logByUsername(
                    username,
                    ActivityLogService.CLASSIFICATION_DELETE,
                    "Deleted classification '" + className + "'"
            );

            // Response
            Map<String, String> response = new HashMap<>();
            response.put("message", "Classification '" + className + "' deleted successfully");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            activityLogService.logByUsername(
                    username,
                    ActivityLogService.CLASSIFICATION_DELETE_FAIL,
                    "Failed to delete classification with ID: " + id + " (Reason: " + e.getMessage() + ")"
            );
            throw e;
        }
    }

    /**
     * ✅ GET CLASSIFICATION BY ID - READ-ONLY (ALL USERS)
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getClassificationById(@PathVariable Long id) {
        // ✅ LICENSE CHECK (READ-ONLY - ALL EDITIONS)
        if (!licenseService.isLicenseValid()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "License expired or not found"));
        }

        return ResponseEntity.ok(classificationService.getClassificationById(id));
    }

    /**
     * ✅ GET ALL CLASSIFICATIONS - READ-ONLY (ALL USERS)
     */
    @PreAuthorize("hasAnyAuthority('Manager', 'Admin')")
    @GetMapping
    public ResponseEntity<?> getAllClassifications() {
        // ✅ LICENSE CHECK (READ-ONLY - ALL EDITIONS)
        if (!licenseService.isLicenseValid()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "License expired or not found"));
        }

        return ResponseEntity.ok(classificationService.getAllClassifications());
    }

    /**
     * ✅ GET MY CLASSIFICATIONS - READ-ONLY (ALL USERS)
     */
    @GetMapping("/my-classifications")
    public ResponseEntity<?> getMyClassifications() {
        // ✅ LICENSE CHECK (READ-ONLY - ALL EDITIONS)
        if (!licenseService.isLicenseValid()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "License expired or not found"));
        }

        return ResponseEntity.ok(classificationService.getClassificationsByUser(getCurrentUserId()));
    }

    /**
     * ✅ GET MY EDITED CLASSIFICATIONS - READ-ONLY (ALL USERS)
     */
    @GetMapping("/my-edited-classifications")
    public ResponseEntity<?> getMyEditedClassifications() {
        // ✅ LICENSE CHECK (READ-ONLY - ALL EDITIONS)
        if (!licenseService.isLicenseValid()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "License expired or not found"));
        }

        return ResponseEntity.ok(classificationService.getClassificationsEditedByUser(getCurrentUserId()));
    }

    /**
     * ✅ GET DOCUMENTS BY CLASSIFICATION - READ-ONLY (ALL USERS)
     */
    @GetMapping("/{id}/documents")
    public ResponseEntity<?> getDocumentsByClassification(@PathVariable Long id) {
        // ✅ LICENSE CHECK (READ-ONLY - ALL EDITIONS)
        if (!licenseService.isLicenseValid()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "License expired or not found"));
        }

        try {
            List<Map<String, Object>> documents = classificationService.getDocumentsByClassificationId(id);
            return ResponseEntity.ok(documents);
        } catch (Exception e) {
            logger.error("Failed to fetch documents for classification ID: " + id, e);
            throw e;
        }
    }

    /**
     * ✅ GET ALL CLASSIFICATION NAMES - READ-ONLY (ALL EDITIONS)
     * Used for document upload dropdown (ED1 users can SELECT but not CREATE)
     */
    @GetMapping("/all")
    public ResponseEntity<?> getAllClassificationNames() {
        // ✅ LICENSE CHECK (READ-ONLY - ALL EDITIONS)
        if (!licenseService.isLicenseValid()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "License expired or not found"));
        }

        try {
            logger.debug("Fetching all classification names");

            // Get all classifications and extract just the names
            List<ClassificationDto> allClassifications = classificationService.getAllClassifications();

            List<String> classificationNames = allClassifications.stream()
                    .map(ClassificationDto::getClassificationName)
                    .filter(name -> name != null && !name.trim().isEmpty())
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());

            logger.info("Returning {} unique classification names", classificationNames.size());

            return ResponseEntity.ok(classificationNames);
        } catch (Exception e) {
            logger.error("Failed to fetch classification names", e);
            // Return empty list instead of error to prevent frontend issues
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * ✅ NEW ENDPOINT: Check if classification management is allowed
     * Used by frontend to show/hide management features
     */
    @GetMapping("/check-management-access")
    public ResponseEntity<Map<String, Object>> checkManagementAccess() {
        Map<String, Object> response = new HashMap<>();

        boolean licenseValid = licenseService.isLicenseValid();
        boolean managementAllowed = isClassificationManagementAllowed();
        String currentEdition = licenseService.getCurrentEdition();
        long daysRemaining = licenseService.getDaysRemaining();

        response.put("licenseValid", licenseValid);
        response.put("managementAllowed", managementAllowed);
        response.put("edition", currentEdition != null ? currentEdition : "NONE");
        response.put("daysRemaining", daysRemaining);
        response.put("message", managementAllowed
                ? "Classification management is available"
                : "Upgrade to ED2 Professional to manage classifications");

        return ResponseEntity.ok(response);
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    /**
     * Check if classification management is allowed (ED2 only)
     */
    private boolean isClassificationManagementAllowed() {
        if (!licenseService.isLicenseValid()) {
            return false;
        }
        String edition = licenseService.getCurrentEdition();
        return "ED2".equalsIgnoreCase(edition);
    }

    /**
     * Create edition upgrade response
     */
    private Map<String, Object> createEditionUpgradeResponse(String feature) {
        Map<String, Object> error = new HashMap<>();
        String currentEdition = licenseService.getCurrentEdition();
        long daysRemaining = licenseService.getDaysRemaining();

        error.put("error", "Classification management is not available in your edition");
        error.put("feature", feature);
        error.put("currentEdition", currentEdition != null ? currentEdition : "ED1");
        error.put("requiredEdition", "ED2");
        error.put("daysRemaining", daysRemaining);
        error.put("message", "Please upgrade to ED2 Professional Edition to " + feature + ". Contact your administrator.");

        return error;
    }

    /**
     * Get current user ID
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }
        User user = userRepository.findByUsername(authentication.getName());
        if (user == null) {
            throw new RuntimeException("User not found with username: " + authentication.getName());
        }
        return user.getId();
    }

    /**
     * Get current username
     */
    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null) ? auth.getName() : "Unknown";
    }
}