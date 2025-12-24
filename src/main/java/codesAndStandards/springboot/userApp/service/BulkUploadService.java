package codesAndStandards.springboot.userApp.service;

import codesAndStandards.springboot.userApp.dto.BulkUploadValidationResult;
import codesAndStandards.springboot.userApp.dto.BulkUploadResult;
import codesAndStandards.springboot.userApp.dto.DocumentMetadata;
import codesAndStandards.springboot.userApp.dto.ExtractedMultipartFile;
import codesAndStandards.springboot.userApp.entity.Document;
import codesAndStandards.springboot.userApp.entity.Tag;
import codesAndStandards.springboot.userApp.entity.Classification;
import codesAndStandards.springboot.userApp.entity.User;
import codesAndStandards.springboot.userApp.repository.DocumentRepository;
import codesAndStandards.springboot.userApp.repository.TagRepository;
import codesAndStandards.springboot.userApp.repository.ClassificationRepository;
import codesAndStandards.springboot.userApp.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class BulkUploadService {

    private static final Logger logger = LoggerFactory.getLogger(BulkUploadService.class);

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClassificationRepository classificationRepository;

    @Value("${file.network-base-path:}")
    private String networkBasePath; // e.g., \\172.16.20.241\DEV-FileServer\USERDATA

    @Value("${file.upload-dir}")
    private String uploadDir;


    /* =====================================================
       =============== VALIDATION HELPERS ==================
       ===================================================== */

    private String normalizeFilename(String name) {
        if (name == null) return null;
        return new File(name).getName().trim().toLowerCase();
    }

    private boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * Validate ONE document and decide validity
     */
    private boolean validateSingleDocument(
            DocumentMetadata metadata,
            Set<String> pdfFilenames,
            BulkUploadValidationResult result,
            Set<String> invalidDocuments
    ) {
        String filename = normalizeFilename(metadata.getFilename());
        boolean hasError = false;

        // PDF existence
        if (!pdfFilenames.contains(filename)) {
            result.addError("Missing PDF File",
                    "Document '" + filename + "' PDF not found in upload");
            hasError = true;
        }

        // Mandatory fields → ERRORS
        if (isEmpty(metadata.getTitle())) {
            result.addError("Missing Title",
                    "Document '" + filename + "' is missing title");
            hasError = true;
        }

        if (isEmpty(metadata.getProductCode())) {
            result.addError("Missing Product Code",
                    "Document '" + filename + "' is missing product code");
            hasError = true;
        }

        if (isEmpty(metadata.getPublishYear())) {
            result.addError("Missing Publish Year",
                    "Document '" + filename + "' is missing publish year");
            hasError = true;
        }

        // Optional → WARNINGS ONLY
        if (metadata.getNoOfPages() == null || metadata.getNoOfPages() <= 0) {
            result.addWarning("Invalid Page Count",
                    "Document '" + filename + "' has invalid or missing page count");
        }

        if (hasError) {
            invalidDocuments.add(filename);
        }

        return !hasError;
    }

    /**
     * Generate Excel template from uploaded PDF files
     * FEATURES 3, 4, 5: Add month dropdown, page numbers, and second sheet
     */
    public ByteArrayOutputStream generateExcelTemplate(MultipartFile[] pdfFiles) throws Exception {
        // Extract PDF filenames
        Set<String> pdfFilenames = extractPdfFilenames(pdfFiles);

        // Create Excel workbook
        Workbook workbook = new XSSFWorkbook();

        // ============= SHEET 1: DOCUMENT METADATA =============
        Sheet sheet = workbook.createSheet("Documents");

        // Create header style
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 12);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);

        // Create header row
        Row headerRow = sheet.createRow(0);
        String[] columns = {
                "Filename", "Title", "Product Code", "Edition",
                "Publish Month", "Publish Year", "No of Pages", "Notes",
                "Tags (comma-separated)", "Classifications (comma-separated)"
        };

        for (int i = 0; i < columns.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);

            // Auto-size columns
            if (i == 0) {
                sheet.setColumnWidth(i, 40 * 256); // Filename column wider
            } else if (i == 1) {
                sheet.setColumnWidth(i, 50 * 256); // Title column wider
            } else if (i == 7) {
                sheet.setColumnWidth(i, 40 * 256); // Notes column wider
            } else {
                sheet.setColumnWidth(i, 20 * 256);
            }
        }

        // FEATURE 3: Create dropdown for Publish Month column
        DataValidationHelper validationHelper = sheet.getDataValidationHelper();
        DataValidationConstraint monthConstraint = validationHelper.createExplicitListConstraint(
                new String[]{
                        "01 (Jan)", "02 (Feb)", "03 (Mar)", "04 (Apr)",
                        "05 (May)", "06 (Jun)", "07 (Jul)", "08 (Aug)",
                        "09 (Sep)", "10 (Oct)", "11 (Nov)", "12 (Dec)"
                }
        );

        // Apply dropdown to Publish Month column (column index 4) for rows 1-1000
        CellRangeAddressList monthRange = new CellRangeAddressList(1, 1000, 4, 4);
        DataValidation monthValidation = validationHelper.createValidation(monthConstraint, monthRange);
        monthValidation.setShowErrorBox(true);
        sheet.addValidationData(monthValidation);

        // Create data rows with filenames
        int rowNum = 1;
        List<String> sortedFilenames = new ArrayList<>(pdfFilenames);
        Collections.sort(sortedFilenames);

        // FEATURE 4: Create file map to detect page numbers
        Map<String, MultipartFile> fileMap = createFileMap(pdfFiles);

        for (String filename : sortedFilenames) {
            Row row = sheet.createRow(rowNum++);

            // Column 0: Filename (pre-filled)
            Cell filenameCell = row.createCell(0);
            filenameCell.setCellValue(filename);

            // Columns 1-5: Empty (Title, Product Code, Edition, Publish Month, Publish Year)
            for (int i = 1; i <= 5; i++) {
                row.createCell(i).setCellValue("");
            }

            // Column 6: FEATURE 4 - Auto-detect page count
            Cell pageCell = row.createCell(6);
            try {
                MultipartFile file = fileMap.get(filename);
                if (file != null) {
                    Integer pageCount = detectPageCount(file);
                    if (pageCount != null && pageCount > 0) {
                        pageCell.setCellValue(pageCount);
                    } else {
                        pageCell.setCellValue("");
                    }
                } else {
                    pageCell.setCellValue("");
                }
            } catch (Exception e) {
                logger.warn("Failed to detect page count for {}: {}", filename, e.getMessage());
                pageCell.setCellValue("");
            }

            // Columns 7-9: Empty (Notes, Tags, Classifications)
            for (int i = 7; i <= 9; i++) {
                row.createCell(i).setCellValue("");
            }
        }

        // ============= SHEET 2: REFERENCE DATA =============
        // FEATURE 5: Create second sheet with existing tags and classifications
        Sheet referenceSheet = workbook.createSheet("Reference Data");

        // Create header style for reference sheet
        Row refHeaderRow = referenceSheet.createRow(0);
        Cell tagsHeaderCell = refHeaderRow.createCell(0);
        tagsHeaderCell.setCellValue("Available Tags");
        tagsHeaderCell.setCellStyle(headerStyle);

        Cell classHeaderCell = refHeaderRow.createCell(2);
        classHeaderCell.setCellValue("Available Classifications");
        classHeaderCell.setCellStyle(headerStyle);

        referenceSheet.setColumnWidth(0, 30 * 256);
        referenceSheet.setColumnWidth(2, 30 * 256);

        // Fetch existing tags and classifications from database
        List<String> existingTags = tagRepository.findAll().stream()
                .map(Tag::getTagName)
                .filter(name -> name != null && !name.isEmpty())
                .sorted()
                .distinct()
                .collect(Collectors.toList());

        List<String> existingClassifications = classificationRepository.findAll().stream()
                .map(Classification::getClassificationName)
                .filter(name -> name != null && !name.isEmpty())
                .sorted()
                .distinct()
                .collect(Collectors.toList());

        // Add tags to column A (starting from row 1)
        int tagRowNum = 1;
        for (String tag : existingTags) {
            Row row = referenceSheet.getRow(tagRowNum);
            if (row == null) row = referenceSheet.createRow(tagRowNum);
            row.createCell(0).setCellValue(tag);
            tagRowNum++;
        }

        // Add classifications to column C (starting from row 1)
        int classRowNum = 1;
        for (String classification : existingClassifications) {
            Row row = referenceSheet.getRow(classRowNum);
            if (row == null) row = referenceSheet.createRow(classRowNum);
            row.createCell(2).setCellValue(classification);
            classRowNum++;
        }

        // Add note at the bottom
        int noteRowNum = Math.max(tagRowNum, classRowNum) + 2;
        Row noteRow = referenceSheet.createRow(noteRowNum);
        Cell noteCell = noteRow.createCell(0);
        noteCell.setCellValue("Note: You can add new tags/classifications in the Documents sheet. " +
                "New entries will be created automatically during upload.");

        CellStyle noteStyle = workbook.createCellStyle();
        Font noteFont = workbook.createFont();
        noteFont.setItalic(true);
        noteFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        noteStyle.setFont(noteFont);
        noteCell.setCellStyle(noteStyle);

        referenceSheet.addMergedRegion(new CellRangeAddress(noteRowNum, noteRowNum, 0, 2));

        // Write workbook to ByteArrayOutputStream
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();

        return outputStream;
    }


    /**
     * Validate bulk upload files
     * UPDATED: Now accepts selfValidationJson parameter for edited metadata
     */
    /* =====================================================
       =============== BULK VALIDATION =====================
       ===================================================== */

    public BulkUploadValidationResult validateBulkUpload(
            MultipartFile[] pdfFiles,
            MultipartFile excelFile,
            String selfValidationJson) throws Exception {

        BulkUploadValidationResult result = new BulkUploadValidationResult();

        // 1️⃣ Load metadata
        List<DocumentMetadata> metadataList =
                (selfValidationJson != null && !selfValidationJson.isEmpty())
                        ? parseJsonToMetadataList(selfValidationJson)
                        : parseExcelFile(excelFile);

        // 2️⃣ Normalize PDF filenames
        Set<String> pdfFilenames = extractPdfFilenames(pdfFiles)
                .stream()
                .map(this::normalizeFilename)
                .collect(Collectors.toSet());

        // 3️⃣ Track invalid documents (LOCAL, SAFE)
        Set<String> invalidDocuments = new HashSet<>();

        // 4️⃣ Validate documents
        for (DocumentMetadata metadata : metadataList) {
            validateSingleDocument(metadata, pdfFilenames, result, invalidDocuments);
        }

        // 5️⃣ Extra PDFs → WARNINGS
        Set<String> metadataFilenames = metadataList.stream()
                .map(m -> normalizeFilename(m.getFilename()))
                .collect(Collectors.toSet());

        for (String pdf : pdfFilenames) {
            if (!metadataFilenames.contains(pdf)) {
                result.addWarning("Extra PDF File",
                        "PDF '" + pdf + "' uploaded but not found in metadata");
            }
        }

        // 6️⃣ Final counts
        int total = metadataList.size();
        int invalid = invalidDocuments.size();
        int valid = total - invalid;

        result.setTotalDocuments(total);
        result.setValidDocuments(valid);

        logger.info(
                "Validation summary → Total={}, Valid={}, Invalid={}, Errors={}, Warnings={}",
                total,
                valid,
                invalid,
                result.getErrors() != null ? result.getErrors().size() : 0,
                result.getWarnings() != null ? result.getWarnings().size() : 0
        );

        return result;
    }

    /**
     * Process bulk upload
     * UPDATED: Now accepts selfValidationJson and uploadOnlyValid parameters
     */
    @Transactional
    public BulkUploadResult processBulkUpload(
            MultipartFile[] pdfFiles,
            MultipartFile excelFile,
            String selfValidationJson,
            boolean uploadOnlyValid) throws Exception {

        BulkUploadResult result = new BulkUploadResult();

        List<DocumentMetadata> metadataList =
                (selfValidationJson != null && !selfValidationJson.isEmpty())
                        ? parseJsonToMetadataList(selfValidationJson)
                        : parseExcelFile(excelFile);

        if (uploadOnlyValid) {
            BulkUploadValidationResult validation =
                    validateBulkUpload(pdfFiles, excelFile, selfValidationJson);

            Set<String> invalidFiles = validation.getErrors().stream()
                    .map(e -> e.toString())
                    .filter(s -> s.contains("'"))
                    .map(s -> s.substring(s.indexOf("'") + 1, s.lastIndexOf("'")))
                    .map(this::normalizeFilename)
                    .collect(Collectors.toSet());

            metadataList = metadataList.stream()
                    .filter(m -> !invalidFiles.contains(normalizeFilename(m.getFilename())))
                    .collect(Collectors.toList());
        }

        Map<String, MultipartFile> fileMap = createFileMap(pdfFiles);

        for (DocumentMetadata metadata : metadataList) {
            String filename = normalizeFilename(metadata.getFilename());
            MultipartFile pdfFile = fileMap.get(filename);

            if (pdfFile != null) {
                uploadDocument(pdfFile, metadata);
                result.addSuccess(filename, metadata.getTitle());
            } else {
                result.addFailure(filename, "PDF file not found");
            }
        }

        return result;
    }

    /**
     * FIXED: Parse JSON to metadata with proper month handling
     */
    private List<DocumentMetadata> parseJsonToMetadataList(String jsonString) throws Exception {
        try {
            ObjectMapper objectMapper = new ObjectMapper();

            List<Map<String, Object>> jsonList = objectMapper.readValue(
                    jsonString,
                    new TypeReference<List<Map<String, Object>>>() {
                    }
            );

            List<DocumentMetadata> metadataList = new ArrayList<>();

            for (Map<String, Object> jsonObj : jsonList) {
                DocumentMetadata metadata = new DocumentMetadata();

                // Extract fields from JSON
                metadata.setFilename(getStringValue(jsonObj, "fileName"));
                metadata.setTitle(getStringValue(jsonObj, "title"));
                metadata.setProductCode(getStringValue(jsonObj, "productCode"));
                metadata.setEdition(getStringValue(jsonObj, "edition"));

                // FIXED: Handle publishMonth properly
                String publishMonth = getStringValue(jsonObj, "publishMonth");
                if (publishMonth != null && !publishMonth.trim().isEmpty()) {
                    publishMonth = publishMonth.trim();

                    // If format is "01 (Jan)", extract just "01"
                    if (publishMonth.contains("(")) {
                        publishMonth = publishMonth.substring(0, publishMonth.indexOf("(")).trim();
                    }

                    // Ensure it's always 2 digits
                    if (publishMonth.length() == 1) {
                        publishMonth = "0" + publishMonth;
                    }

                    logger.debug("Parsed publishMonth from JSON: '{}'", publishMonth);
                }
                metadata.setPublishMonth(publishMonth);

                metadata.setPublishYear(getStringValue(jsonObj, "publishYear"));

                // Handle noOfPages
                Object noOfPagesObj = jsonObj.get("noOfPages");
                if (noOfPagesObj != null) {
                    if (noOfPagesObj instanceof Number) {
                        metadata.setNoOfPages(((Number) noOfPagesObj).intValue());
                    } else if (noOfPagesObj instanceof String) {
                        String noOfPagesStr = (String) noOfPagesObj;
                        if (!noOfPagesStr.isEmpty()) {
                            try {
                                metadata.setNoOfPages(Integer.parseInt(noOfPagesStr));
                            } catch (NumberFormatException e) {
                                logger.warn("Invalid page count for {}: {}", metadata.getFilename(), noOfPagesStr);
                            }
                        }
                    }
                }

                metadata.setNotes(getStringValue(jsonObj, "notes"));

                // Handle tags array with normalization
                Object tagsObj = jsonObj.get("tags");
                if (tagsObj instanceof List) {
                    List<?> tagsList = (List<?>) tagsObj;
                    String tagsString = tagsList.stream()
                            .map(Object::toString)
                            .map(this::normalizeTag)
                            .filter(tag -> !tag.isEmpty())
                            .collect(Collectors.joining(","));
                    metadata.setTags(tagsString);
                } else if (tagsObj instanceof String) {
                    String tagsValue = (String) tagsObj;
                    String normalizedTags = Arrays.stream(tagsValue.split(","))
                            .map(this::normalizeTag)
                            .filter(tag -> !tag.isEmpty())
                            .collect(Collectors.joining(","));
                    metadata.setTags(normalizedTags);
                }

                // Handle classifications array
                Object classificationsObj = jsonObj.get("classifications");
                if (classificationsObj instanceof List) {
                    List<?> classificationsList = (List<?>) classificationsObj;
                    String classificationsString = classificationsList.stream()
                            .map(Object::toString)
                            .collect(Collectors.joining(","));
                    metadata.setClassifications(classificationsString);
                } else if (classificationsObj instanceof String) {
                    metadata.setClassifications((String) classificationsObj);
                }

                metadataList.add(metadata);

                logger.debug("Parsed metadata from JSON: filename={}, title={}, publishMonth={}, tags={}, classifications={}",
                        metadata.getFilename(),
                        metadata.getTitle(),
                        metadata.getPublishMonth(),
                        metadata.getTags(),
                        metadata.getClassifications());
            }

            logger.info("Parsed {} documents from JSON metadata", metadataList.size());
            return metadataList;

        } catch (Exception e) {
            logger.error("Failed to parse JSON metadata", e);
            throw new Exception("Failed to parse edited metadata: " + e.getMessage(), e);
        }
    }

    /**
     * Helper method to safely extract string values from JSON map
     */
    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        return value.toString().trim();
    }

    /**
     * Parse Excel file and extract metadata
     * FIXED: Properly handle month parsing from dropdown format
     */
    private List<DocumentMetadata> parseExcelFile(MultipartFile excelFile) throws Exception {
        List<DocumentMetadata> metadataList = new ArrayList<>();

        try (InputStream is = excelFile.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);

            // Skip header row
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                DocumentMetadata metadata = new DocumentMetadata();

                // Parse each column
                metadata.setFilename(getCellValueAsString(row.getCell(0)));
                metadata.setTitle(getCellValueAsString(row.getCell(1)));
                metadata.setProductCode(getCellValueAsString(row.getCell(2)));
                metadata.setEdition(getCellValueAsString(row.getCell(3)));

                // FIXED: Parse month from dropdown (handle multiple formats)
                String publishMonth = getCellValueAsString(row.getCell(4));
                if (publishMonth != null && !publishMonth.trim().isEmpty()) {
                    // Handle format "01 (Jan)" or "01" or "1"
                    publishMonth = publishMonth.trim();

                    // If format is "01 (Jan)", extract just "01"
                    if (publishMonth.contains("(")) {
                        publishMonth = publishMonth.substring(0, publishMonth.indexOf("(")).trim();
                    }

                    // Ensure it's always 2 digits (convert "1" to "01")
                    if (publishMonth.length() == 1) {
                        publishMonth = "0" + publishMonth;
                    }

                    logger.debug("Parsed publish month: '{}'", publishMonth);
                }
                metadata.setPublishMonth(publishMonth);

                metadata.setPublishYear(getCellValueAsString(row.getCell(5)));
                metadata.setNoOfPages(getCellValueAsInteger(row.getCell(6)));
                metadata.setNotes(getCellValueAsString(row.getCell(7)));

                // Parse and normalize tags
                String tagsValue = getCellValueAsString(row.getCell(8));
                if (tagsValue != null && !tagsValue.isEmpty()) {
                    String normalizedTags = Arrays.stream(tagsValue.split(","))
                            .map(tag -> normalizeTag(tag))
                            .filter(tag -> !tag.isEmpty())
                            .collect(Collectors.joining(","));
                    metadata.setTags(normalizedTags);
                } else {
                    metadata.setTags("");
                }

                metadata.setClassifications(getCellValueAsString(row.getCell(9)));

                // Only add if filename is present
                if (metadata.getFilename() != null && !metadata.getFilename().isEmpty()) {
                    metadataList.add(metadata);

                    // Log the parsed metadata for debugging
                    logger.debug("Parsed metadata: filename={}, publishMonth={}",
                            metadata.getFilename(), metadata.getPublishMonth());
                }
            }
        }

        logger.info("Parsed {} documents from Excel", metadataList.size());
        return metadataList;
    }

    /**
     * FEATURE 6: Normalize tag - remove all spaces and convert to lowercase
     */
    private String normalizeTag(String tag) {
        if (tag == null || tag.trim().isEmpty()) {
            return "";
        }
        return tag.trim().replaceAll("\\s+", "").toLowerCase();
    }

    /**
     * Extract PDF filenames from uploaded files (including ZIP extraction)
     * CRITICAL FIX: Handle folder uploads where originalFilename includes path
     */
    private Set<String> extractPdfFilenames(MultipartFile[] files) throws IOException {
        Set<String> filenames = new HashSet<>();

        for (MultipartFile file : files) {
            String originalFilename = file.getOriginalFilename();

            if (originalFilename != null && originalFilename.toLowerCase().endsWith(".zip")) {
                // Extract filenames from ZIP
                try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".pdf")) {
                            // Get just the filename without path
                            String filename = normalizeFilename(entry.getName());
                            filenames.add(filename);
                            logger.debug("Extracted from ZIP: {}", filename);
                        }
                        zis.closeEntry();
                    }
                }
            } else if (originalFilename != null && originalFilename.toLowerCase().endsWith(".pdf")) {
                // CRITICAL FIX: Extract just the filename from path (handles folder uploads)
                // Example: "normal/dilligent.pdf" -> "dilligent.pdf"
                String justFilename = normalizeFilename(originalFilename);
                filenames.add(justFilename.trim().toLowerCase());
                logger.debug("Extracted filename: {} (from: {})", justFilename, originalFilename);
            }
        }

        logger.info("Extracted {} unique PDF filenames", filenames.size());
        return filenames;
    }

    /**
     * Create map of filename to MultipartFile (handles ZIP extraction)
     * CRITICAL FIX: Handle folder uploads where originalFilename includes path
     */
    private Map<String, MultipartFile> createFileMap(MultipartFile[] files) throws IOException {
        Map<String, MultipartFile> fileMap = new HashMap<>();

        for (MultipartFile file : files) {
            String originalFilename = file.getOriginalFilename();

            if (originalFilename != null && originalFilename.toLowerCase().endsWith(".zip")) {
                // Extract and store files from ZIP
                extractZipFiles(file, fileMap);
            } else if (originalFilename != null && originalFilename.toLowerCase().endsWith(".pdf")) {
                // CRITICAL FIX: Use just the filename as key (without folder path)
                // Example: "normal/dilligent.pdf" -> key is "dilligent.pdf"
                String justFilename = normalizeFilename(originalFilename);
                fileMap.put(justFilename, file);
                logger.debug("Added to fileMap: {} -> {}", justFilename, originalFilename);
            }
        }

        logger.info("Created file map with {} entries", fileMap.size());
        return fileMap;
    }

    /**
     * Extract PDF files from ZIP and add to file map
     */
    private void extractZipFiles(MultipartFile zipFile, Map<String, MultipartFile> fileMap) throws IOException {
        Path tempDir = Files.createTempDirectory("bulk-upload-");

        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".pdf")) {
                    String filename = normalizeFilename(entry.getName());
                    Path tempFile = tempDir.resolve(filename);
                    Files.copy(zis, tempFile, StandardCopyOption.REPLACE_EXISTING);

                    // Create a wrapper MultipartFile for the extracted file
                    ExtractedMultipartFile extractedFile = new ExtractedMultipartFile(tempFile.toFile(), filename);
                    fileMap.put(filename, extractedFile);
                }
                zis.closeEntry();
            }
        }
    }

    /**
     * Validate metadata fields
     */
    private void validateMetadata(DocumentMetadata metadata, BulkUploadValidationResult result) {
        if (metadata.getTitle() == null || metadata.getTitle().trim().isEmpty()) {
            result.addError("Missing Title", "Document '" + metadata.getFilename() + "' is missing title");
        }

        if (metadata.getProductCode() == null || metadata.getProductCode().trim().isEmpty()) {
            result.addError("Missing Product Code", "Document '" + metadata.getFilename() + "' is missing product code");
        }

        if (metadata.getPublishYear() == null || metadata.getPublishYear().trim().isEmpty()) {
            result.addError("Missing Publish Year", "Document '" + metadata.getFilename() + "' is missing publish year");
        }

        if (metadata.getNoOfPages() == null || metadata.getNoOfPages() <= 0) {
            result.addWarning("Invalid Page Count", "Document '" + metadata.getFilename() + "' has invalid or missing page count");
        }
    }

    /**
     * Upload a single document with metadata
     */
    @Transactional
    private void uploadDocument(MultipartFile file, DocumentMetadata metadata) throws Exception {
        // Get currently logged-in user
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            throw new Exception("User not authenticated");
        }

        // 1. CREATE USER-SPECIFIC DIRECTORY
        String username = currentUser.getUsername();
        Path userUploadPath = Paths.get(uploadDir, username);

        // Ensure user folder exists
        if (!Files.exists(userUploadPath)) {
            Files.createDirectories(userUploadPath);
        }

        // 2. SAVE FILE WITH TIMESTAMP
        String savedName = System.currentTimeMillis() + "_" + file.getOriginalFilename();

//        String networkPath = "C:/Users/Imp user 006/Documents/CNS_documents/" + savedName;
        // Use application.properties path
        Path savedPath = Paths.get(uploadDir, savedName);
//        Path savedPath = Paths.get(networkPath);
        Files.createDirectories(savedPath.getParent());

        Files.copy(file.getInputStream(), savedPath, StandardCopyOption.REPLACE_EXISTING);
        // DEFINE networkPath HERE ✅
        String networkPath = savedPath.toString();

        // 3. AUTO-DETECT PAGE COUNT IF NOT PROVIDED
        Integer pageCount = metadata.getNoOfPages();
        if (pageCount == null || pageCount == 0) {
            try {
                pageCount = detectPageCount(file);
                logger.info("Auto-detected page count for {}: {}", file.getOriginalFilename(), pageCount);
            } catch (Exception e) {
                logger.warn("Failed to auto-detect page count for {}: {}", file.getOriginalFilename(), e.getMessage());
                pageCount = null;
            }
        }

        // 4. CREATE DOCUMENT ENTITY
        Document document = new Document();
        document.setTitle(metadata.getTitle());
        document.setProductCode(metadata.getProductCode());
        document.setEdition(metadata.getEdition());
        document.setNoOfPages(pageCount);
        document.setNotes(metadata.getNotes());

        // Set file path (network path)
        document.setFilePath(networkPath);

        // Set upload metadata
        document.setUploadedAt(LocalDateTime.now());

        // SET UPLOADER
        document.setUploadedBy(currentUser);

        // 5. SET PUBLISH DATE
        if (metadata.getPublishYear() != null && !metadata.getPublishYear().isEmpty()) {
            String year = metadata.getPublishYear();
            String month = metadata.getPublishMonth();
            if (month != null && !month.isEmpty()) {
                document.setPublishDate(year + "-" + month);
            } else {
                document.setPublishDate(year);
            }
        }

        // 6. SAVE DOCUMENT FIRST (to get ID for relationships)
        document = documentRepository.save(document);

        // 7. HANDLE TAGS (after document is saved)
        if (metadata.getTags() != null && !metadata.getTags().isEmpty()) {
            Set<Tag> tags = new HashSet<>();
            for (String tagStr : metadata.getTags().split(",")) {
                String tagName = tagStr.trim().toLowerCase();
                if (tagName.isEmpty()) continue;

                Tag tag = tagRepository.findByTagName(tagName)
                        .orElseGet(() -> {
                            Tag newTag = new Tag();
                            newTag.setTagName(tagName);
                            newTag.setCreatedBy(currentUser);
                            newTag.setCreatedAt(LocalDateTime.now());
                            return tagRepository.save(newTag);
                        });
                tags.add(tag);
            }
            document.setTags(tags);
            logger.info("Added {} tags to document: {}", tags.size(), file.getOriginalFilename());
        }

        // 8. HANDLE CLASSIFICATIONS (after document is saved)
        if (metadata.getClassifications() != null && !metadata.getClassifications().isEmpty()) {
            Set<Classification> classifications = new HashSet<>();
            for (String classStr : metadata.getClassifications().split(",")) {
                String className = classStr.trim();
                if (className.isEmpty()) continue;

                Classification classification = classificationRepository.findByClassificationName(className)
                        .orElseGet(() -> {
                            Classification newClass = new Classification();
                            newClass.setClassificationName(className);
                            newClass.setCreatedBy(currentUser);
                            newClass.setCreatedAt(LocalDateTime.now());
                            return classificationRepository.save(newClass);
                        });
                classifications.add(classification);
            }
            document.setClassifications(classifications);
            logger.info("Added {} classifications to document: {}", classifications.size(), file.getOriginalFilename());
        }

        // 9. SAVE DOCUMENT AGAIN with tags and classifications
        documentRepository.save(document);
        logger.info("Document saved successfully: {}", file.getOriginalFilename());
    }

    /**
     * Get currently authenticated user
     */
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        String username = authentication.getName();
        return userRepository.findByUsername(username);
    }

    /**
     * Detect page count from PDF file
     */
    private Integer detectPageCount(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream();
             PDDocument document = PDDocument.load(inputStream)) {
            return document.getNumberOfPages();
        } catch (Exception e) {
            throw new IOException("Failed to detect page count: " + e.getMessage(), e);
        }
    }

    /**
     * Get cell value as string
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return null;

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    // Check if it's a whole number
                    double numValue = cell.getNumericCellValue();
                    if (numValue == Math.floor(numValue)) {
                        // It's a whole number, return as integer string
                        return String.valueOf((int) numValue);
                    } else {
                        return String.valueOf(numValue);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue().trim();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BLANK:
                return null;
            default:
                return null;
        }
    }

    /**
     * Get cell value as integer
     */
    private Integer getCellValueAsInteger(Cell cell) {
        if (cell == null) return null;

        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return (int) cell.getNumericCellValue();
            } else if (cell.getCellType() == CellType.STRING) {
                String value = cell.getStringCellValue().trim();
                if (value.isEmpty()) return null;
                return Integer.parseInt(value);
            } else if (cell.getCellType() == CellType.BLANK) {
                return null;
            }
        } catch (Exception e) {
            logger.warn("Failed to parse integer from cell", e);
            return null;
        }
        return null;
    }
}