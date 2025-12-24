package codesAndStandards.springboot.userApp.controller;

import codesAndStandards.springboot.userApp.service.LicenseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/license")
@CrossOrigin(origins = "*")
public class LicenseController {

    @Autowired
    private LicenseService licenseService;

    /**
     * Get system information for license request
     */
    @GetMapping("/system-info")
    public ResponseEntity<Map<String, String>> getSystemInfo() {
        Map<String, String> info = new HashMap<>();
        info.put("systemName", licenseService.getSystemName());
        info.put("requestCode", licenseService.generateRequestCode());
        info.put("hardwareId", licenseService.generateHardwareId());

        return ResponseEntity.ok(info);
    }

    /**
     * Upload and activate license file
     */
    @PostMapping("/activate")
    public ResponseEntity<Map<String, String>> activateLicense(
            @RequestParam("file") MultipartFile file) {

        Map<String, String> response = new HashMap<>();

        try {
            if (file.isEmpty()) {
                response.put("error", "Please select a license file");
                return ResponseEntity.badRequest().body(response);
            }

            if (!file.getOriginalFilename().endsWith(".lic")) {
                response.put("error", "Invalid file format. Please upload a .lic file");
                return ResponseEntity.badRequest().body(response);
            }

            boolean success = licenseService.activateLicense(file);

            if (success) {
                response.put("message", "License activated successfully!");
                response.put("edition", licenseService.getCurrentEdition());
                return ResponseEntity.ok(response);
            } else {
                response.put("error", "License activation failed");
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            response.put("error", "Activation failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get current license status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getLicenseStatus() {
        Map<String, Object> status = new HashMap<>();

        LicenseService.LicenseInfo license = licenseService.getCurrentLicense();

        if (license == null) {
            status.put("isActive", false);
            status.put("isValid", false);
            status.put("edition", "NONE");
            status.put("bulkUploadAllowed", false);
            status.put("message", "No license found. Please activate a license.");
            return ResponseEntity.ok(status);
        }

        boolean isValid = licenseService.isLicenseValid();
        long daysRemaining = licenseService.getDaysRemaining();
        String edition = license.getEdition();
        boolean bulkUploadAllowed = licenseService.isBulkUploadAllowed();

        status.put("isActive", license.getIsActive());
        status.put("isValid", isValid);
        status.put("edition", edition != null ? edition : "ED1");
        status.put("systemName", licenseService.getSystemName());
        status.put("issueDate", license.getIssueDate().format(DateTimeFormatter.ISO_DATE_TIME));
        status.put("expiryDate", license.getExpiryDate().format(DateTimeFormatter.ISO_DATE_TIME));
        status.put("daysRemaining", daysRemaining);
        status.put("bulkUploadAllowed", bulkUploadAllowed);

        // Add warning messages
        if (!isValid) {
            status.put("message", "License has expired. Please renew your license.");
        } else if (daysRemaining <= 30) {
            status.put("warning", "License expires in " + daysRemaining + " days. Please renew soon.");
        }

        // Add edition-specific messages
        if (isValid && "ED1".equals(edition)) {
            status.put("editionMessage", "You are using ED1 Standard Edition. Upgrade to ED2 for advanced features like bulk upload.");
        } else if (isValid && "ED2".equals(edition)) {
            status.put("editionMessage", "You are using ED2 Professional Edition with all features unlocked.");
        }

        return ResponseEntity.ok(status);
    }

    /**
     * Check if bulk upload is allowed
     */
    @GetMapping("/check-bulk-upload")
    public ResponseEntity<Map<String, Object>> checkBulkUpload() {
        Map<String, Object> response = new HashMap<>();

        boolean allowed = licenseService.isBulkUploadAllowed();
        String edition = licenseService.getCurrentEdition();
        boolean isValid = licenseService.isLicenseValid();

        response.put("allowed", allowed);
        response.put("edition", edition != null ? edition : "ED1");
        response.put("isValid", isValid);

        if (!allowed && isValid) {
            response.put("reason", "Feature requires ED2 Professional Edition");
            response.put("currentEdition", edition != null ? edition : "ED1");
            response.put("requiredEdition", "ED2");
        } else if (!allowed && !isValid) {
            response.put("reason", "License expired or not found");
        }

        return ResponseEntity.ok(response);
    }

    /**
     * ⭐ NEW: Check if a specific feature is allowed
     * This is a flexible endpoint for checking any feature based on edition
     */
    @GetMapping("/check-feature")
    public ResponseEntity<Map<String, Object>> checkFeature(
            @RequestParam String featureName) {

        Map<String, Object> response = new HashMap<>();

        boolean isValid = licenseService.isLicenseValid();
        String edition = licenseService.getCurrentEdition();
        long daysRemaining = licenseService.getDaysRemaining();

        response.put("isValid", isValid);
        response.put("edition", edition != null ? edition : "ED1");
        response.put("daysRemaining", daysRemaining);

        // Check feature availability based on edition
        boolean featureAllowed = false;
        String requiredEdition = "ED1";

        switch (featureName.toLowerCase()) {
            case "bulk-upload":
            case "bulkupload":
                featureAllowed = licenseService.isBulkUploadAllowed();
                requiredEdition = "ED2";
                break;

            case "advanced-analytics":
            case "analytics":
                featureAllowed = "ED2".equals(edition) && isValid;
                requiredEdition = "ED2";
                break;

            case "basic-upload":
            case "document-view":
            case "search":
                featureAllowed = isValid; // Available in all editions
                requiredEdition = "ED1";
                break;

            default:
                featureAllowed = isValid; // Default to basic feature
                requiredEdition = "ED1";
        }

        response.put("featureName", featureName);
        response.put("allowed", featureAllowed);
        response.put("requiredEdition", requiredEdition);

        if (!featureAllowed && isValid) {
            response.put("reason", "Feature requires " + requiredEdition + " edition");
            response.put("message", "Please upgrade your license to access this feature");
        } else if (!featureAllowed && !isValid) {
            response.put("reason", "License expired or not found");
            response.put("message", "Please activate or renew your license");
        }

        return ResponseEntity.ok(response);
    }
}