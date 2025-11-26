package codesAndStandards.springboot.userApp.controller;

import codesAndStandards.springboot.userApp.dto.BulkUploadResult;
import codesAndStandards.springboot.userApp.dto.BulkUploadValidationResult;
import codesAndStandards.springboot.userApp.service.ActivityLogService;
import codesAndStandards.springboot.userApp.service.BulkUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Controller
@RequestMapping("/api/bulk-upload")
public class BulkUploadController {

    private static final Logger logger = LoggerFactory.getLogger(BulkUploadController.class);

    @Autowired
    private BulkUploadService bulkUploadService;

    @Autowired
    private ActivityLogService activityLogService;

    private String getCurrentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }


    /**
     * Generate Excel template from uploaded PDF files
     */
    @PostMapping("/generate-template")
    @PreAuthorize("hasAuthority('Admin')")
    public ResponseEntity<Resource> generateTemplate(
            @RequestParam("pdfFiles") MultipartFile[] pdfFiles) {

        try {
            logger.info("Generating Excel template for {} PDF files", pdfFiles != null ? pdfFiles.length : 0);

            if (pdfFiles == null || pdfFiles.length == 0) {
                logger.warn("No PDF files provided for template generation");
                return ResponseEntity.badRequest().build();
            }

            // Validate files
            for (MultipartFile file : pdfFiles) {
                if (file.isEmpty()) {
                    logger.warn("Empty file detected: {}", file.getOriginalFilename());
                    continue;
                }
                logger.debug("Processing file: {} ({}  bytes)", file.getOriginalFilename(), file.getSize());
            }

            ByteArrayOutputStream outputStream = bulkUploadService.generateExcelTemplate(pdfFiles);

            if (outputStream == null || outputStream.size() == 0) {
                logger.error("Generated Excel template is empty");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            ByteArrayResource resource = new ByteArrayResource(outputStream.toByteArray());

            // Generate filename with timestamp
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "bulk_upload_template_" + timestamp + ".xlsx";

            logger.info("Excel template generated successfully: {} ({} bytes)", filename, resource.contentLength());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(resource.contentLength())
                    .body(resource);

        } catch (Exception e) {
            logger.error("Error generating Excel template", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Download blank Excel template (without any filenames)
     */
    @GetMapping("/download-template")
    @PreAuthorize("hasAuthority('Admin')")
    public ResponseEntity<Resource> downloadBlankTemplate() {
        try {
            logger.info("Downloading blank Excel template");

            // Create empty template
            ByteArrayOutputStream outputStream = bulkUploadService.generateExcelTemplate(new MultipartFile[0]);

            ByteArrayResource resource = new ByteArrayResource(outputStream.toByteArray());

            logger.info("Blank template generated successfully ({} bytes)", resource.contentLength());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"bulk_upload_template.xlsx\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(resource.contentLength())
                    .body(resource);

        } catch (Exception e) {
            logger.error("Error downloading blank template", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Validate bulk upload files
     */
    @PostMapping("/validate")
    @PreAuthorize("hasAuthority('Admin')")
    @ResponseBody
    public ResponseEntity<BulkUploadValidationResult> validateBulkUpload(
            @RequestParam("pdfFiles") MultipartFile[] pdfFiles,
            @RequestParam("excelFile") MultipartFile excelFile) {

        try {
            logger.info("Validating bulk upload: {} PDF files, Excel file: {}",
                    pdfFiles != null ? pdfFiles.length : 0,
                    excelFile != null ? excelFile.getOriginalFilename() : "null");

            if (pdfFiles == null || pdfFiles.length == 0) {
                logger.warn("No PDF files provided for validation");
                BulkUploadValidationResult errorResult = new BulkUploadValidationResult();
                errorResult.addError("Validation Error", "No PDF files provided");
                return ResponseEntity.badRequest().body(errorResult);
            }

            if (excelFile == null || excelFile.isEmpty()) {
                logger.warn("No Excel file provided for validation");
                BulkUploadValidationResult errorResult = new BulkUploadValidationResult();
                errorResult.addError("Validation Error", "No Excel file provided");
                return ResponseEntity.badRequest().body(errorResult);
            }

            BulkUploadValidationResult result = bulkUploadService.validateBulkUpload(pdfFiles, excelFile);

            logger.info("Validation complete: Total={}, Valid={}, Errors={}, Warnings={}",
                    result.getTotalDocuments(),
                    result.getValidDocuments(),
                    result.getErrors() != null ? result.getErrors().size() : 0,
                    result.getWarnings() != null ? result.getWarnings().size() : 0);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("Error validating bulk upload", e);
            BulkUploadValidationResult errorResult = new BulkUploadValidationResult();
            errorResult.addError("Validation Error", "Failed to validate files: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
        }
    }

    /**
     * Process bulk upload
     */
    @PostMapping("/process")
    @PreAuthorize("hasAuthority('Admin')")
    @ResponseBody
    public ResponseEntity<BulkUploadResult> processBulkUpload(
            @RequestParam("pdfFiles") MultipartFile[] pdfFiles,
            @RequestParam("excelFile") MultipartFile excelFile) {
        String username = getCurrentUsername();

        try {
            logger.info("Processing bulk upload: {} PDF files, Excel file: {}",
                    pdfFiles != null ? pdfFiles.length : 0,
                    excelFile != null ? excelFile.getOriginalFilename() : "null");

            if (pdfFiles == null || pdfFiles.length == 0) {
                logger.warn("No PDF files provided for processing");
                BulkUploadResult errorResult = new BulkUploadResult();
                errorResult.addError("No PDF files provided");
                return ResponseEntity.badRequest().body(errorResult);
            }

            if (excelFile == null || excelFile.isEmpty()) {
                logger.warn("No Excel file provided for processing");
                BulkUploadResult errorResult = new BulkUploadResult();
                errorResult.addError("No Excel file provided");
                return ResponseEntity.badRequest().body(errorResult);
            }

            long startTime = System.currentTimeMillis();

            BulkUploadResult result = bulkUploadService.processBulkUpload(pdfFiles, excelFile);

            long duration = System.currentTimeMillis() - startTime;

            // Log success
            activityLogService.logByUsername(
                    username,
                    ActivityLogService.BULK_DOCUMENT_UPLOADED,
                    "Successfully processed bulk upload of " + pdfFiles.length +
                            " PDF(s) using metadata file: " + excelFile.getOriginalFilename()
            );
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("Error processing bulk upload", e);

            // Log failure
            activityLogService.logByUsername(
                    username,
                    ActivityLogService.BULK_DOCUMENT_UPLOAD_FAIL,
                    "Bulk upload failed (Reason: " + e.getMessage() + ")"
            );

            BulkUploadResult errorResult = new BulkUploadResult();
            errorResult.addError("Upload Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
        }
    }
}