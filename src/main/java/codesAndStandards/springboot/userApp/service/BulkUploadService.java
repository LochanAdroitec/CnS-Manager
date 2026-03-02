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

    // ====== CHANGE 2 + 4: Inject ApplicationSettingsService to read maxTagsPerDocument ======
    @Autowired
    private ApplicationSettingsService settingsService;

    @Value("${file.network-base-path:}")
    private String networkBasePath;

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
     * Generate Excel template from uploaded PDF files.
     *
     * CHANGE 2: Mandatory column headers now have " *" suffix.
     * CHANGE 4: Tags column header includes max-tags info from settings.
     */
    public ByteArrayOutputStream generateExcelTemplate(MultipartFile[] pdfFiles) throws Exception {
        // Extract PDF filenames
        Set<String> pdfFilenames = extractPdfFilenames(pdfFiles);

        // ====== CHANGE 4: Read maxTagsPerDocument from settings ======
        int maxTagsPerDoc = 10; // safe default
        try {
            Integer settingValue = settingsService.getMaxTagsPerDocument();
            if (settingValue != null && settingValue > 0) {
                maxTagsPerDoc = settingValue;
            }
        } catch (Exception e) {
            logger.warn("Could not read maxTagsPerDocument from settings, using default=10: {}", e.getMessage());
        }
        final int maxTagsForTemplate = maxTagsPerDoc;

        // Create Excel workbook
        Workbook workbook = new XSSFWorkbook();

        // ============= SHEET 1: DOCUMENT METADATA =============
        Sheet sheet = workbook.createSheet("Documents");

        // ====== Header styles ======
        CellStyle headerStyleRequired = workbook.createCellStyle();
        Font headerFontRequired = workbook.createFont();
        headerFontRequired.setBold(true);
        headerFontRequired.setFontHeightInPoints((short) 12);
        headerFontRequired.setColor(IndexedColors.WHITE.getIndex());
        headerStyleRequired.setFont(headerFontRequired);
        headerStyleRequired.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        headerStyleRequired.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyleRequired.setBorderBottom(BorderStyle.THIN);
        headerStyleRequired.setBorderTop(BorderStyle.THIN);
        headerStyleRequired.setBorderLeft(BorderStyle.THIN);
        headerStyleRequired.setBorderRight(BorderStyle.THIN);
        headerStyleRequired.setAlignment(HorizontalAlignment.CENTER);
        headerStyleRequired.setVerticalAlignment(VerticalAlignment.CENTER);

        // Optional header style — slightly different background so user can visually distinguish
        CellStyle headerStyleOptional = workbook.createCellStyle();
        Font headerFontOptional = workbook.createFont();
        headerFontOptional.setBold(true);
        headerFontOptional.setFontHeightInPoints((short) 11);
        headerFontOptional.setColor(IndexedColors.WHITE.getIndex());
        headerStyleOptional.setFont(headerFontOptional);
        headerStyleOptional.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex()); // darker grey for optional
        headerStyleOptional.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyleOptional.setBorderBottom(BorderStyle.THIN);
        headerStyleOptional.setBorderTop(BorderStyle.THIN);
        headerStyleOptional.setBorderLeft(BorderStyle.THIN);
        headerStyleOptional.setBorderRight(BorderStyle.THIN);
        headerStyleOptional.setAlignment(HorizontalAlignment.CENTER);
        headerStyleOptional.setVerticalAlignment(VerticalAlignment.CENTER);

        // ====== CHANGE 2: Headers with * on mandatory fields ======
        // CHANGE 4: Tags column includes max-tags info
        String tagsHeader = "Tags (comma-separated, max " + maxTagsForTemplate + ")";

        // Column index → [header text, isRequired]
        Object[][] columnDefs = {
                {"Filename *",            true},   // 0 — REQUIRED
                {"Title *",               true},   // 1 — REQUIRED
                {"Product Code *",        true},   // 2 — REQUIRED
                {"Edition",               false},  // 3 — optional
                {"Publish Month",         false},  // 4 — optional
                {"Publish Year *",        true},   // 5 — REQUIRED
                {"No of Pages",           false},  // 6 — optional
                {"Notes",                 false},  // 7 — optional
                {tagsHeader,              false},  // 8 — optional
                {"Classifications (comma-separated)", false} // 9 — optional
        };

        Row headerRow = sheet.createRow(0);
        headerRow.setHeightInPoints(22); // slightly taller for readability

        for (int i = 0; i < columnDefs.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue((String) columnDefs[i][0]);
            boolean required = (Boolean) columnDefs[i][1];
            cell.setCellStyle(required ? headerStyleRequired : headerStyleOptional);

            // Column widths
            if (i == 0 || i == 1) {
                sheet.setColumnWidth(i, 45 * 256);
            } else if (i == 7 || i == 8 || i == 9) {
                sheet.setColumnWidth(i, 38 * 256);
            } else {
                sheet.setColumnWidth(i, 22 * 256);
            }
        }

        // ====== CHANGE 2: Add a legend row below the header explaining * = required ======
        Row legendRow = sheet.createRow(1);
        CellStyle legendStyle = workbook.createCellStyle();
        Font legendFont = workbook.createFont();
        legendFont.setItalic(true);
        legendFont.setColor(IndexedColors.DARK_RED.getIndex());
        legendFont.setFontHeightInPoints((short) 10);
        legendStyle.setFont(legendFont);
        legendStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        legendStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Cell legendCell = legendRow.createCell(0);
        legendCell.setCellValue("* = Required field   |   Dark blue = Required   |   Grey = Optional   |   Tags must be comma-separated (e.g. tag1,tag2,tag3)   |   Max " + maxTagsForTemplate + " tags per document");
        legendCell.setCellStyle(legendStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, columnDefs.length - 1));

        // CHANGE 3 / CHANGE 4: Add example row at row index 2 (after legend)
        Row exampleRow = sheet.createRow(2);
        CellStyle exampleStyle = workbook.createCellStyle();
        Font exampleFont = workbook.createFont();
        exampleFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        exampleFont.setItalic(true);
        exampleStyle.setFont(exampleFont);

        String[] exampleData = {
                "document1.pdf",                   // Filename *
                "Product Manual v2.1",             // Title *
                "PM-001",                          // Product Code *
                "2.1",                             // Edition
                "06",                              // Publish Month
                "2024",                            // Publish Year *
                "150",                             // No of Pages
                "Updated version",                 // Notes
                "manual,technical,v2",             // Tags — comma-separated example (CHANGE 3 note)
                "Engineering,Safety"               // Classifications — comma-separated example
        };
        for (int i = 0; i < exampleData.length; i++) {
            Cell cell = exampleRow.createCell(i);
            cell.setCellValue(exampleData[i]);
            cell.setCellStyle(exampleStyle);
        }

        // ====== Dropdown for Publish Month column (column 4), starting from data row 3 ======
        DataValidationHelper validationHelper = sheet.getDataValidationHelper();
        DataValidationConstraint monthConstraint = validationHelper.createExplicitListConstraint(
                new String[]{
                        "01 (Jan)", "02 (Feb)", "03 (Mar)", "04 (Apr)",
                        "05 (May)", "06 (Jun)", "07 (Jul)", "08 (Aug)",
                        "09 (Sep)", "10 (Oct)", "11 (Nov)", "12 (Dec)"
                }
        );
        // Apply dropdown from row 3 (index) onward (rows 0=header, 1=legend, 2=example, 3+=data)
        CellRangeAddressList monthRange = new CellRangeAddressList(3, 1003, 4, 4);
        DataValidation monthValidation = validationHelper.createValidation(monthConstraint, monthRange);
        monthValidation.setShowErrorBox(true);
        sheet.addValidationData(monthValidation);

        // ====== Create data rows from row index 3 ======
        int rowNum = 3;
        List<String> sortedFilenames = new ArrayList<>(pdfFilenames);
        Collections.sort(sortedFilenames);

        Map<String, MultipartFile> fileMap = createFileMap(pdfFiles);

        for (String filename : sortedFilenames) {
            Row row = sheet.createRow(rowNum++);

            // Column 0: Filename (pre-filled)
            row.createCell(0).setCellValue(filename);

            // Columns 1-5: Empty (Title, Product Code, Edition, Publish Month, Publish Year)
            for (int i = 1; i <= 5; i++) {
                row.createCell(i).setCellValue("");
            }

            // Column 6: Auto-detect page count
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
        Sheet referenceSheet = workbook.createSheet("Reference Data");

        Row refHeaderRow = referenceSheet.createRow(0);
        Cell tagsHeaderCell = refHeaderRow.createCell(0);
        tagsHeaderCell.setCellValue("Available Tags (max " + maxTagsForTemplate + " per document)");
        tagsHeaderCell.setCellStyle(headerStyleRequired);

        Cell classHeaderCell = refHeaderRow.createCell(2);
        classHeaderCell.setCellValue("Available Classifications");
        classHeaderCell.setCellStyle(headerStyleOptional);

        referenceSheet.setColumnWidth(0, 35 * 256);
        referenceSheet.setColumnWidth(2, 35 * 256);

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

        // Add tags to column A
        int tagRowNum = 1;
        for (String tag : existingTags) {
            Row row = referenceSheet.getRow(tagRowNum);
            if (row == null) row = referenceSheet.createRow(tagRowNum);
            row.createCell(0).setCellValue(tag);
            tagRowNum++;
        }

        // Add classifications to column C
        int classRowNum = 1;
        for (String classification : existingClassifications) {
            Row row = referenceSheet.getRow(classRowNum);
            if (row == null) row = referenceSheet.createRow(classRowNum);
            row.createCell(2).setCellValue(classification);
            classRowNum++;
        }

        // Note at bottom of reference sheet
        int noteRowNum = Math.max(tagRowNum, classRowNum) + 2;
        Row noteRow = referenceSheet.createRow(noteRowNum);
        Cell noteCell = noteRow.createCell(0);
        noteCell.setCellValue("Note: Tags and Classifications in the Documents sheet must be comma-separated (e.g. tag1,tag2,tag3). " +
                "Max " + maxTagsForTemplate + " tags per document. New entries will be created automatically during upload.");

        CellStyle noteStyle = workbook.createCellStyle();
        Font noteFont = workbook.createFont();
        noteFont.setItalic(true);
        noteFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        noteStyle.setFont(noteFont);
        noteCell.setCellStyle(noteStyle);
        referenceSheet.addMergedRegion(new CellRangeAddress(noteRowNum, noteRowNum, 0, 2));

        // ====== CHANGE 4: Add separate "Tag Policies" sheet ======
        Sheet policiesSheet = workbook.createSheet("Tag Policies");
        Row policiesHeader = policiesSheet.createRow(0);
        Cell phCell = policiesHeader.createCell(0);
        phCell.setCellValue("Tag & Upload Policies");
        phCell.setCellStyle(headerStyleRequired);
        policiesSheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));
        policiesSheet.setColumnWidth(0, 40 * 256);
        policiesSheet.setColumnWidth(1, 20 * 256);

        String[][] policies = {
                {"Max Tags Per Document", String.valueOf(maxTagsForTemplate)},
                {"Tags Format", "Comma-separated (e.g. tag1,tag2,tag3)"},
                {"Tags Case", "Lowercase only — tags are auto-converted to lowercase"},
                {"Classifications Format", "Comma-separated (e.g. Class1,Class2)"},
                {"Required Fields", "Filename, Title, Product Code, Publish Year"}
        };
        int pRow = 1;
        for (String[] pair : policies) {
            Row r = policiesSheet.createRow(pRow++);
            r.createCell(0).setCellValue(pair[0]);
            r.createCell(1).setCellValue(pair[1]);
        }

        // Write workbook to ByteArrayOutputStream
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();

        return outputStream;
    }


    /* =====================================================
       =============== BULK VALIDATION =====================
       ===================================================== */

    public BulkUploadValidationResult validateBulkUpload(
            MultipartFile[] pdfFiles,
            MultipartFile excelFile,
            String selfValidationJson) throws Exception {

        BulkUploadValidationResult result = new BulkUploadValidationResult();

        List<DocumentMetadata> metadataList =
                (selfValidationJson != null && !selfValidationJson.isEmpty())
                        ? parseJsonToMetadataList(selfValidationJson)
                        : parseExcelFile(excelFile);

        Set<String> pdfFilenames = extractPdfFilenames(pdfFiles)
                .stream()
                .map(this::normalizeFilename)
                .collect(Collectors.toSet());

        Set<String> invalidDocuments = new HashSet<>();

        for (DocumentMetadata metadata : metadataList) {
            validateSingleDocument(metadata, pdfFilenames, result, invalidDocuments);
        }

        Set<String> metadataFilenames = metadataList.stream()
                .map(m -> normalizeFilename(m.getFilename()))
                .collect(Collectors.toSet());

        for (String pdf : pdfFilenames) {
            if (!metadataFilenames.contains(pdf)) {
                result.addWarning("Extra PDF File",
                        "PDF '" + pdf + "' uploaded but not found in metadata");
            }
        }

        int total = metadataList.size();
        int invalid = invalidDocuments.size();
        int valid = total - invalid;

        result.setTotalDocuments(total);
        result.setValidDocuments(valid);

        logger.info(
                "Validation summary → Total={}, Valid={}, Invalid={}, Errors={}, Warnings={}",
                total, valid, invalid,
                result.getErrors() != null ? result.getErrors().size() : 0,
                result.getWarnings() != null ? result.getWarnings().size() : 0
        );

        return result;
    }

    /**
     * Process bulk upload
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
                    .map(Object::toString)
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
     * Parse JSON to metadata list with proper month handling.
     * CHANGE 3: Tags from JSON are split by comma then normalized individually.
     */
    private List<DocumentMetadata> parseJsonToMetadataList(String jsonString) throws Exception {
        try {
            ObjectMapper objectMapper = new ObjectMapper();

            List<Map<String, Object>> jsonList = objectMapper.readValue(
                    jsonString,
                    new TypeReference<List<Map<String, Object>>>() {}
            );

            List<DocumentMetadata> metadataList = new ArrayList<>();

            for (Map<String, Object> jsonObj : jsonList) {
                DocumentMetadata metadata = new DocumentMetadata();

                metadata.setFilename(getStringValue(jsonObj, "fileName"));
                metadata.setTitle(getStringValue(jsonObj, "title"));
                metadata.setProductCode(getStringValue(jsonObj, "productCode"));
                metadata.setEdition(getStringValue(jsonObj, "edition"));

                String publishMonth = getStringValue(jsonObj, "publishMonth");
                if (publishMonth != null && !publishMonth.trim().isEmpty()) {
                    publishMonth = publishMonth.trim();
                    if (publishMonth.contains("(")) {
                        publishMonth = publishMonth.substring(0, publishMonth.indexOf("(")).trim();
                    }
                    if (publishMonth.length() == 1) {
                        publishMonth = "0" + publishMonth;
                    }
                    logger.debug("Parsed publishMonth from JSON: '{}'", publishMonth);
                }
                metadata.setPublishMonth(publishMonth);
                metadata.setPublishYear(getStringValue(jsonObj, "publishYear"));

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

                // ====== CHANGE 3: Tags from JSON — split by comma, trim each, then normalize ======
                Object tagsObj = jsonObj.get("tags");
                if (tagsObj instanceof List) {
                    List<?> tagsList = (List<?>) tagsObj;
                    String tagsString = tagsList.stream()
                            .map(Object::toString)
                            .map(String::trim)           // trim whitespace around each tag first
                            .filter(tag -> !tag.isEmpty())
                            .map(this::normalizeTag)     // then normalize (lowercase, no spaces)
                            .collect(Collectors.joining(","));
                    metadata.setTags(tagsString);
                } else if (tagsObj instanceof String) {
                    // ====== CHANGE 3: String form — split by comma, trim each part, then normalize ======
                    String tagsValue = (String) tagsObj;
                    String normalizedTags = Arrays.stream(tagsValue.split(","))
                            .map(String::trim)           // trim whitespace before normalizing
                            .filter(tag -> !tag.isEmpty())
                            .map(this::normalizeTag)
                            .collect(Collectors.joining(","));
                    metadata.setTags(normalizedTags);
                }

                // ====== CHANGE 3: Classifications from JSON — split by comma, trim each ======
                Object classificationsObj = jsonObj.get("classifications");
                if (classificationsObj instanceof List) {
                    List<?> classificationsList = (List<?>) classificationsObj;
                    String classificationsString = classificationsList.stream()
                            .map(Object::toString)
                            .map(String::trim)           // trim whitespace
                            .filter(c -> !c.isEmpty())
                            .collect(Collectors.joining(","));
                    metadata.setClassifications(classificationsString);
                } else if (classificationsObj instanceof String) {
                    // ====== CHANGE 3: String form — split by comma, trim each part ======
                    String classValue = (String) classificationsObj;
                    String normalizedClass = Arrays.stream(classValue.split(","))
                            .map(String::trim)
                            .filter(c -> !c.isEmpty())
                            .collect(Collectors.joining(","));
                    metadata.setClassifications(normalizedClass);
                }

                metadataList.add(metadata);

                logger.debug("Parsed metadata from JSON: filename={}, title={}, publishMonth={}, tags={}, classifications={}",
                        metadata.getFilename(), metadata.getTitle(), metadata.getPublishMonth(),
                        metadata.getTags(), metadata.getClassifications());
            }

            logger.info("Parsed {} documents from JSON metadata", metadataList.size());
            return metadataList;

        } catch (Exception e) {
            logger.error("Failed to parse JSON metadata", e);
            throw new Exception("Failed to parse edited metadata: " + e.getMessage(), e);
        }
    }

    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        return value.toString().trim();
    }

    /**
     * Parse Excel file and extract metadata.
     * CHANGE 3: Tags and classifications are properly split by comma then trimmed.
     */
    private List<DocumentMetadata> parseExcelFile(MultipartFile excelFile) throws Exception {
        List<DocumentMetadata> metadataList = new ArrayList<>();

        try (InputStream is = excelFile.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);

            // Skip header row (row 0), legend row (row 1), and example row (row 2) — data starts at row 3
            // Also support old templates that only have a single header row (row 0 = header, row 1 = data)
            int startRow = detectDataStartRow(sheet);
            logger.info("Excel data starts at row index: {}", startRow);

            for (int i = startRow; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                DocumentMetadata metadata = new DocumentMetadata();

                metadata.setFilename(getCellValueAsString(row.getCell(0)));
                metadata.setTitle(getCellValueAsString(row.getCell(1)));
                metadata.setProductCode(getCellValueAsString(row.getCell(2)));
                metadata.setEdition(getCellValueAsString(row.getCell(3)));

                // Parse month from dropdown (handle multiple formats)
                String publishMonth = getCellValueAsString(row.getCell(4));
                if (publishMonth != null && !publishMonth.trim().isEmpty()) {
                    publishMonth = publishMonth.trim();
                    if (publishMonth.contains("(")) {
                        publishMonth = publishMonth.substring(0, publishMonth.indexOf("(")).trim();
                    }
                    if (publishMonth.length() == 1) {
                        publishMonth = "0" + publishMonth;
                    }
                    logger.debug("Parsed publish month: '{}'", publishMonth);
                }
                metadata.setPublishMonth(publishMonth);

                metadata.setPublishYear(getCellValueAsString(row.getCell(5)));
                metadata.setNoOfPages(getCellValueAsInteger(row.getCell(6)));
                metadata.setNotes(getCellValueAsString(row.getCell(7)));

                // ====== CHANGE 3: Parse tags — split by comma, trim each part, then normalize ======
                String tagsValue = getCellValueAsString(row.getCell(8));
                if (tagsValue != null && !tagsValue.isEmpty()) {
                    String normalizedTags = Arrays.stream(tagsValue.split(","))
                            .map(String::trim)           // trim whitespace BEFORE normalizing
                            .filter(tag -> !tag.isEmpty())
                            .map(this::normalizeTag)     // then normalize (lowercase, collapse spaces)
                            .collect(Collectors.joining(","));
                    metadata.setTags(normalizedTags);
                } else {
                    metadata.setTags("");
                }

                // ====== CHANGE 3: Parse classifications — split by comma, trim each part ======
                String classValue = getCellValueAsString(row.getCell(9));
                if (classValue != null && !classValue.isEmpty()) {
                    String normalizedClass = Arrays.stream(classValue.split(","))
                            .map(String::trim)
                            .filter(c -> !c.isEmpty())
                            .collect(Collectors.joining(","));
                    metadata.setClassifications(normalizedClass);
                } else {
                    metadata.setClassifications(getCellValueAsString(row.getCell(9)));
                }

                // Only add if filename is present
                if (metadata.getFilename() != null && !metadata.getFilename().isEmpty()) {
                    metadataList.add(metadata);
                    logger.debug("Parsed metadata: filename={}, publishMonth={}, tags={}",
                            metadata.getFilename(), metadata.getPublishMonth(), metadata.getTags());
                }
            }
        }

        logger.info("Parsed {} documents from Excel", metadataList.size());
        return metadataList;
    }

    /**
     * Detect which row data actually starts at.
     * New templates have legend row (row 1) and example row (row 2) → data starts at row 3.
     * Old templates have no legend → data starts at row 1.
     */
    private int detectDataStartRow(Sheet sheet) {
        // Check if row 1 looks like a legend (non-empty string starting with "*")
        Row row1 = sheet.getRow(1);
        if (row1 != null) {
            Cell cell = row1.getCell(0);
            if (cell != null && cell.getCellType() == CellType.STRING) {
                String val = cell.getStringCellValue();
                if (val != null && (val.startsWith("*") || val.contains("= Required"))) {
                    // New template with legend and example rows
                    return 3;
                }
            }
        }
        // Old template or unknown — skip only header
        return 1;
    }

    /**
     * Normalize tag — remove all spaces and convert to lowercase.
     */
    private String normalizeTag(String tag) {
        if (tag == null || tag.trim().isEmpty()) {
            return "";
        }
        return tag.trim().replaceAll("\\s+", "").toLowerCase();
    }

    /**
     * Extract PDF filenames from uploaded files (including ZIP extraction).
     * Handles folder uploads where originalFilename includes path.
     */
    private Set<String> extractPdfFilenames(MultipartFile[] files) throws IOException {
        Set<String> filenames = new HashSet<>();

        for (MultipartFile file : files) {
            String originalFilename = file.getOriginalFilename();

            if (originalFilename != null && originalFilename.toLowerCase().endsWith(".zip")) {
                try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".pdf")) {
                            String filename = normalizeFilename(entry.getName());
                            filenames.add(filename);
                            logger.debug("Extracted from ZIP: {}", filename);
                        }
                        zis.closeEntry();
                    }
                }
            } else if (originalFilename != null && originalFilename.toLowerCase().endsWith(".pdf")) {
                String justFilename = normalizeFilename(originalFilename);
                filenames.add(justFilename.trim().toLowerCase());
                logger.debug("Extracted filename: {} (from: {})", justFilename, originalFilename);
            }
        }

        logger.info("Extracted {} unique PDF filenames", filenames.size());
        return filenames;
    }

    /**
     * Create map of filename to MultipartFile (handles ZIP extraction).
     */
    private Map<String, MultipartFile> createFileMap(MultipartFile[] files) throws IOException {
        Map<String, MultipartFile> fileMap = new HashMap<>();

        for (MultipartFile file : files) {
            String originalFilename = file.getOriginalFilename();

            if (originalFilename != null && originalFilename.toLowerCase().endsWith(".zip")) {
                extractZipFiles(file, fileMap);
            } else if (originalFilename != null && originalFilename.toLowerCase().endsWith(".pdf")) {
                String justFilename = normalizeFilename(originalFilename);
                fileMap.put(justFilename, file);
                logger.debug("Added to fileMap: {} -> {}", justFilename, originalFilename);
            }
        }

        logger.info("Created file map with {} entries", fileMap.size());
        return fileMap;
    }

    private void extractZipFiles(MultipartFile zipFile, Map<String, MultipartFile> fileMap) throws IOException {
        Path tempDir = Files.createTempDirectory("bulk-upload-");

        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".pdf")) {
                    String filename = normalizeFilename(entry.getName());
                    Path tempFile = tempDir.resolve(filename);
                    Files.copy(zis, tempFile, StandardCopyOption.REPLACE_EXISTING);
                    ExtractedMultipartFile extractedFile = new ExtractedMultipartFile(tempFile.toFile(), filename);
                    fileMap.put(filename, extractedFile);
                }
                zis.closeEntry();
            }
        }
    }

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

    @Transactional
    private void uploadDocument(MultipartFile file, DocumentMetadata metadata) throws Exception {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            throw new Exception("User not authenticated");
        }

        String username = currentUser.getUsername();
        Path userUploadPath = Paths.get(uploadDir, username);
        if (!Files.exists(userUploadPath)) {
            Files.createDirectories(userUploadPath);
        }

        String originalFileName = new File(file.getOriginalFilename()).getName();
        String fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
        String uniqueFileName = UUID.randomUUID().toString() + fileExtension;
        Path filePath = Paths.get(uploadDir, uniqueFileName);
        Files.createDirectories(filePath.getParent());
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        String networkPath = filePath.toString();

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

        Document document = new Document();
        document.setTitle(metadata.getTitle());
        document.setProductCode(metadata.getProductCode());
        document.setEdition(metadata.getEdition());
        document.setNoOfPages(pageCount);
        document.setNotes(metadata.getNotes());
        document.setFilePath(networkPath);
        document.setUploadedAt(LocalDateTime.now());
        document.setUploadedBy(currentUser);

        if (metadata.getPublishYear() != null && !metadata.getPublishYear().isEmpty()) {
            String year = metadata.getPublishYear();
            String month = metadata.getPublishMonth();
            if (month != null && !month.isEmpty()) {
                document.setPublishDate(year + "-" + month);
            } else {
                document.setPublishDate(year);
            }
        }

        document = documentRepository.save(document);

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

        documentRepository.save(document);
        logger.info("Document saved successfully: {}", file.getOriginalFilename());
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) return null;
        return userRepository.findByUsername(authentication.getName());
    }

    private Integer detectPageCount(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream();
             PDDocument document = PDDocument.load(inputStream)) {
            return document.getNumberOfPages();
        } catch (Exception e) {
            throw new IOException("Failed to detect page count: " + e.getMessage(), e);
        }
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    double numValue = cell.getNumericCellValue();
                    if (numValue == Math.floor(numValue)) {
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