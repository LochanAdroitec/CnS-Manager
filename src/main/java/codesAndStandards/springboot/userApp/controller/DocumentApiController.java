package codesAndStandards.springboot.userApp.controller;

import codesAndStandards.springboot.userApp.dto.DocumentDto;
import codesAndStandards.springboot.userApp.dto.DocumentInfoDTO;
import codesAndStandards.springboot.userApp.entity.Document;
import codesAndStandards.springboot.userApp.repository.DocumentRepository;
import codesAndStandards.springboot.userApp.service.DocumentService;
import codesAndStandards.springboot.userApp.service.LicenseService;
import codesAndStandards.springboot.userApp.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentApiController {

    private final DocumentRepository documentRepository;
    private final UserService userService;
    private final DocumentService documentService;

    @Autowired
    private LicenseService licenseService;

    /**
     * Get all documents for access control selection
     * GET /api/documents
     * ✅ WITH LICENSE VALIDATION
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('Admin', 'Manager', 'User')")
    public ResponseEntity<?> getAllDocuments() {
        log.info("REST request to get documents (filtered by group access)");

        // ✅ LICENSE CHECK
        if (!licenseService.isLicenseValid()) {
            log.warn("License validation failed for getAllDocuments");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "License expired or not found"));
        }

        try {
            Long userId = userService.getLoggedInUserId();
            List<Document> documents = documentRepository.findAll();

            List<DocumentInfoDTO> documentDTOs = documents.stream()
                    .map(doc -> DocumentInfoDTO.builder()
                            .id(doc.getId())
                            .title(doc.getTitle())
                            .build())
                    .collect(Collectors.toList());

            log.info("Returning {} accessible documents", documentDTOs.size());
            return ResponseEntity.ok(documentDTOs);

        } catch (Exception e) {
            log.error("Error fetching documents", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch documents"));
        }
    }

    /**
     * Get document by ID
     * GET /api/documents/{id}
     * ✅ WITH LICENSE VALIDATION
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('Admin', 'Manager', 'User')")
    public ResponseEntity<?> getDocumentById(@PathVariable Long id) {
        log.info("REST request to get document : {}", id);

        // ✅ LICENSE CHECK
        if (!licenseService.isLicenseValid()) {
            log.warn("License validation failed for getDocumentById");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("License expired or not found"));
        }

        try {
            Document document = documentRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Document not found with ID: " + id));

            DocumentInfoDTO dto = DocumentInfoDTO.builder()
                    .id(document.getId())
                    .title(document.getTitle())
                    .build();

            return ResponseEntity.ok(dto);
        } catch (RuntimeException e) {
            log.error("Error fetching document with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error fetching document", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch document"));
        }
    }

    /**
     * Search documents by title or code
     * GET /api/documents/search?query=xyz
     * ✅ WITH LICENSE VALIDATION
     */
    @GetMapping("/search")
    @PreAuthorize("hasAnyAuthority('Admin', 'Manager', 'User')")
    public ResponseEntity<?> searchDocuments(@RequestParam String query) {
        log.info("REST request to search documents with query: {}", query);

        // ✅ LICENSE CHECK
        if (!licenseService.isLicenseValid()) {
            log.warn("License validation failed for searchDocuments");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "License expired or not found"));
        }

        try {
            List<Document> documents = documentRepository.findAll().stream()
                    .filter(doc ->
                            (doc.getTitle() != null && doc.getTitle().toLowerCase().contains(query.toLowerCase()))
                    )
                    .collect(Collectors.toList());

            List<DocumentInfoDTO> documentDTOs = documents.stream()
                    .map(doc -> DocumentInfoDTO.builder()
                            .id(doc.getId())
                            .title(doc.getTitle())
                            .build())
                    .collect(Collectors.toList());

            return ResponseEntity.ok(documentDTOs);
        } catch (Exception e) {
            log.error("Error searching documents", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to search documents"));
        }
    }

    /**
     * Upload single document
     * POST /api/documents/upload
     * ✅ WITH LICENSE VALIDATION
     */
    @PostMapping("/upload")
    @PreAuthorize("hasAnyAuthority('Admin', 'Manager')")
    public ResponseEntity<?> uploadDocument(
            @ModelAttribute DocumentDto documentDto,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "groupIds", required = false) String groupIds) {

        log.info("REST request to upload document: {}", documentDto.getTitle());

        // ✅ LICENSE CHECK
        if (!licenseService.isLicenseValid()) {
            log.warn("License validation failed for uploadDocument");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "License expired or not found"));
        }

        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();

            documentService.saveDocument(documentDto, file, username, groupIds);

            log.info("Document uploaded successfully: {}", documentDto.getTitle());
            return ResponseEntity.ok(Map.of("message", "Document uploaded successfully!"));

        } catch (Exception e) {
            log.error("Error uploading document", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update document
     * PUT /api/documents/{id}
     * ✅ WITH LICENSE VALIDATION
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('Admin', 'Manager')")
    public ResponseEntity<?> updateDocument(
            @PathVariable Long id,
            @ModelAttribute DocumentDto documentDto,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "groupIds", required = false) String groupIds) {

        log.info("REST request to update document: {}", id);

        // ✅ LICENSE CHECK
        if (!licenseService.isLicenseValid()) {
            log.warn("License validation failed for updateDocument");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "License expired or not found"));
        }

        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();

            documentService.updateDocument(id, documentDto, file, username, groupIds);

            log.info("Document updated successfully: {}", id);
            return ResponseEntity.ok(Map.of("message", "Document updated successfully!"));

        } catch (Exception e) {
            log.error("Error updating document", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Delete document
     * DELETE /api/documents/{id}
     * ✅ WITH LICENSE VALIDATION
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('Admin')")
    public ResponseEntity<?> deleteDocument(@PathVariable Long id) {
        log.info("REST request to delete document: {}", id);

        // ✅ LICENSE CHECK
        if (!licenseService.isLicenseValid()) {
            log.warn("License validation failed for deleteDocument");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "License expired or not found"));
        }

        try {
            documentService.deleteDocument(id);

            log.info("Document deleted successfully: {}", id);
            return ResponseEntity.ok(Map.of("message", "Document deleted successfully"));

        } catch (Exception e) {
            log.error("Error deleting document", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * ⭐ BULK UPLOAD DOCUMENTS - ED2 EDITION ONLY
     * POST /api/documents/bulk-upload
     * ✅ WITH LICENSE AND EDITION VALIDATION
     */
    @PostMapping("/bulk-upload")
    @PreAuthorize("hasAnyAuthority('Admin', 'Manager')")
    public ResponseEntity<?> bulkUpload(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "groupIds", required = false) String groupIds) {

        log.info("REST request to bulk upload {} files", files.length);

        // ✅ STEP 1: Check if license is valid
        if (!licenseService.isLicenseValid()) {
            log.warn("License validation failed for bulkUpload");
            Map<String, String> error = new HashMap<>();
            error.put("error", "License expired or not found");
            error.put("message", "Please activate or renew your license");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }

        // ✅ STEP 2: Check if bulk upload is allowed (ED2 edition only)
        if (!licenseService.isBulkUploadAllowed()) {
            log.warn("Bulk upload denied - Edition: {}", licenseService.getCurrentEdition());

            String currentEdition = licenseService.getCurrentEdition();
            long daysRemaining = licenseService.getDaysRemaining();

            Map<String, Object> error = new HashMap<>();
            error.put("error", "Bulk upload feature not available in your edition");
            error.put("currentEdition", currentEdition != null ? currentEdition : "ED1");
            error.put("requiredEdition", "ED2");
            error.put("daysRemaining", daysRemaining);
            error.put("message", "Please upgrade to ED2 edition to use bulk upload feature. Contact your administrator.");

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }

        // ✅ BOTH CHECKS PASSED - Proceed with bulk upload
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();

            int successCount = 0;
            int failedCount = 0;
            StringBuilder errors = new StringBuilder();

            // Process each file
            for (MultipartFile file : files) {
                try {
                    DocumentDto documentDto = new DocumentDto();
                    // Set basic info from filename
                    String fileName = file.getOriginalFilename();
                    if (fileName != null) {
                        documentDto.setTitle(fileName.replace(".pdf", ""));
                    }

                    documentService.saveDocument(documentDto, file, username, groupIds);
                    successCount++;
                    log.info("Bulk upload: Successfully uploaded {}", fileName);

                } catch (Exception e) {
                    failedCount++;
                    errors.append(file.getOriginalFilename())
                            .append(": ")
                            .append(e.getMessage())
                            .append("; ");
                    log.error("Bulk upload: Failed to upload {}", file.getOriginalFilename(), e);
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Bulk upload completed");
            response.put("totalFiles", files.length);
            response.put("successCount", successCount);
            response.put("failedCount", failedCount);
            response.put("edition", licenseService.getCurrentEdition());

            if (failedCount > 0) {
                response.put("errors", errors.toString());
            }

            log.info("Bulk upload completed: {} succeeded, {} failed", successCount, failedCount);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Bulk upload error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Bulk upload failed: " + e.getMessage()));
        }
    }

    /**
     * Get license test info
     * GET /api/documents/license-test
     */
    @GetMapping("/license-test")
    @PreAuthorize("hasAnyAuthority('Admin', 'Manager', 'User')")
    public ResponseEntity<?> testLicense() {
        Map<String, Object> response = new HashMap<>();
        response.put("licenseValid", licenseService.isLicenseValid());
        response.put("edition", licenseService.getCurrentEdition());
        response.put("bulkUploadAllowed", licenseService.isBulkUploadAllowed());
        response.put("daysRemaining", licenseService.getDaysRemaining());

        if (licenseService.getCurrentLicense() != null) {
            response.put("expiryDate", licenseService.getCurrentLicense().getExpiryDate().toString());
        }

        return ResponseEntity.ok(response);
    }

    // Error response class
    private static class ErrorResponse {
        private String message;

        public ErrorResponse(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
