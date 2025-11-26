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
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class BulkUploadService {

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

    /**
     * Generate Excel template from uploaded PDF files
     */
    public ByteArrayOutputStream generateExcelTemplate(MultipartFile[] pdfFiles) throws Exception {
        // Extract PDF filenames
        Set<String> pdfFilenames = extractPdfFilenames(pdfFiles);

        // Create Excel workbook
        Workbook workbook = new XSSFWorkbook();
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

        // Create data rows with filenames
        int rowNum = 1;
        List<String> sortedFilenames = new ArrayList<>(pdfFilenames);
        Collections.sort(sortedFilenames);

        for (String filename : sortedFilenames) {
            Row row = sheet.createRow(rowNum++);

            // Filename (pre-filled)
            Cell filenameCell = row.createCell(0);
            filenameCell.setCellValue(filename);

            // Empty cells for metadata to be filled by user
            for (int i = 1; i < columns.length; i++) {
                row.createCell(i).setCellValue("");
            }
        }

        // Write workbook to ByteArrayOutputStream
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();

        return outputStream;
    }

    /**
     * Validate bulk upload files
     */
    public BulkUploadValidationResult validateBulkUpload(MultipartFile[] pdfFiles, MultipartFile excelFile) throws Exception {
        BulkUploadValidationResult result = new BulkUploadValidationResult();

        // Parse Excel file
        List<DocumentMetadata> metadataList = parseExcelFile(excelFile);

        // Extract PDF filenames
        Set<String> pdfFilenames = extractPdfFilenames(pdfFiles);

        // Validation checks
        result.setTotalDocuments(metadataList.size());

        // Check if all PDFs in metadata exist in uploaded files
        for (DocumentMetadata metadata : metadataList) {
            String filename = metadata.getFilename();

            if (!pdfFilenames.contains(filename)) {
                result.addError("Missing PDF File", "PDF file '" + filename + "' mentioned in Excel not found in uploaded files");
            } else {
                // Validate metadata fields
                validateMetadata(metadata, result);
            }
        }

        // Check for extra PDFs not in metadata
        Set<String> metadataFilenames = metadataList.stream()
                .map(DocumentMetadata::getFilename)
                .collect(Collectors.toSet());

        for (String pdfFilename : pdfFilenames) {
            if (!metadataFilenames.contains(pdfFilename)) {
                result.addWarning("Extra PDF File", "PDF file '" + pdfFilename + "' uploaded but not found in Excel metadata");
            }
        }

        // Calculate valid documents
        int errorCount = result.getErrors().size();
        result.setValidDocuments(result.getTotalDocuments() - errorCount);

        return result;
    }

    /**
     * Process bulk upload
     */
    @Transactional
    public BulkUploadResult processBulkUpload(MultipartFile[] pdfFiles, MultipartFile excelFile) throws Exception {
        BulkUploadResult result = new BulkUploadResult();

        try {
            // Parse Excel file
            List<DocumentMetadata> metadataList = parseExcelFile(excelFile);

            // Create map of filename to file
            Map<String, MultipartFile> fileMap = createFileMap(pdfFiles);

            // Process each document
            for (DocumentMetadata metadata : metadataList) {
                try {
                    MultipartFile pdfFile = fileMap.get(metadata.getFilename());

                    if (pdfFile != null) {
                        uploadDocument(pdfFile, metadata);
                        result.addSuccess(metadata.getFilename());
                    } else {
                        result.addFailure(metadata.getFilename(), "PDF file not found");
                    }
                } catch (Exception e) {
                    result.addFailure(metadata.getFilename(), e.getMessage());
                }
            }

        } catch (Exception e) {
            result.addError("Bulk upload processing failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * Parse Excel file and extract metadata
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
                metadata.setPublishMonth(getCellValueAsString(row.getCell(4)));
                metadata.setPublishYear(getCellValueAsString(row.getCell(5)));
                metadata.setNoOfPages(getCellValueAsInteger(row.getCell(6)));
                metadata.setNotes(getCellValueAsString(row.getCell(7)));
                metadata.setTags(getCellValueAsString(row.getCell(8)));
                metadata.setClassifications(getCellValueAsString(row.getCell(9)));

                // Only add if filename is present
                if (metadata.getFilename() != null && !metadata.getFilename().isEmpty()) {
                    metadataList.add(metadata);
                }
            }
        }

        return metadataList;
    }

    /**
     * Extract PDF filenames from uploaded files (including ZIP extraction)
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
                            String filename = new File(entry.getName()).getName();
                            filenames.add(filename);
                        }
                        zis.closeEntry();
                    }
                }
            } else if (originalFilename != null && originalFilename.toLowerCase().endsWith(".pdf")) {
                filenames.add(originalFilename);
            }
        }

        return filenames;
    }

    /**
     * Create map of filename to MultipartFile (handles ZIP extraction)
     */
    private Map<String, MultipartFile> createFileMap(MultipartFile[] files) throws IOException {
        Map<String, MultipartFile> fileMap = new HashMap<>();

        for (MultipartFile file : files) {
            String originalFilename = file.getOriginalFilename();

            if (originalFilename != null && originalFilename.toLowerCase().endsWith(".zip")) {
                // Extract and store files from ZIP
                extractZipFiles(file, fileMap);
            } else if (originalFilename != null && originalFilename.toLowerCase().endsWith(".pdf")) {
                fileMap.put(originalFilename, file);
            }
        }

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
                    String filename = new File(entry.getName()).getName();
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

        String networkPath = "\\\\172.16.20.241\\DEV-FileServer\\USERDATA\\Abhay\\" + savedName;

        Path savedPath = Paths.get(networkPath);
        Files.createDirectories(savedPath.getParent());

        Files.copy(file.getInputStream(), savedPath, StandardCopyOption.REPLACE_EXISTING);

        // 3. AUTO-DETECT PAGE COUNT IF NOT PROVIDED
        Integer pageCount = metadata.getNoOfPages();
        if (pageCount == null || pageCount == 0) {
            try {
                pageCount = detectPageCount(file);
            } catch (Exception e) {
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
        }

        // 9. SAVE DOCUMENT AGAIN with tags and classifications
        documentRepository.save(document);
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
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf((long) cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
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
                return Integer.parseInt(cell.getStringCellValue());
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}