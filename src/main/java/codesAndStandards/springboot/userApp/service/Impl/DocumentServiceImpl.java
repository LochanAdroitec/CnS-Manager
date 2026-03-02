package codesAndStandards.springboot.userApp.service.Impl;

import codesAndStandards.springboot.userApp.dto.DocumentDto;
import codesAndStandards.springboot.userApp.entity.*;
import codesAndStandards.springboot.userApp.repository.DocumentRepository;
import codesAndStandards.springboot.userApp.repository.StoredProcedureRepository;
import codesAndStandards.springboot.userApp.repository.UserRepository;
import codesAndStandards.springboot.userApp.repository.AccessControlLogicRepository;
import codesAndStandards.springboot.userApp.service.ApplicationSettingsService;
import codesAndStandards.springboot.userApp.service.DocumentService;
import codesAndStandards.springboot.userApp.service.GroupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DocumentServiceImpl implements DocumentService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentServiceImpl.class);

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final StoredProcedureRepository storedProcedureRepository;
    private final GroupService groupService;
    private final AccessControlLogicRepository accessControlLogicRepository;
    @Autowired
    private ApplicationSettingsService settingsService;

    @Value("${file.upload-dir}")
    private String uploadDir;

    public DocumentServiceImpl(DocumentRepository documentRepository,
                               UserRepository userRepository,
                               StoredProcedureRepository storedProcedureRepository,
                               GroupService groupService,
                               AccessControlLogicRepository accessControlLogicRepository) {
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.storedProcedureRepository = storedProcedureRepository;
        this.groupService = groupService;
        this.accessControlLogicRepository = accessControlLogicRepository;
    }


    // ✅ UPDATED: Added groupIds parameter
    @Override
    @Transactional
    public void saveDocument(DocumentDto documentDto, MultipartFile file, String username, String groupIds) throws Exception {

        if (file.isEmpty()) {
            throw new RuntimeException("Please select a file to upload");
        }

        // ⭐ NEW: Get max file size from settings
        Integer maxFileSizeMB = settingsService.getMaxFileSizeMB();
        long maxSizeBytes = maxFileSizeMB * 1024L * 1024L;

        // ⭐ NEW: Check file size against settings
        if (file.getSize() > maxSizeBytes) {
            throw new RuntimeException(String.format("File size (%dMB) exceeds maximum allowed size of %dMB",
                    file.getSize() / (1024 * 1024), maxFileSizeMB));
        }

        // ⭐ EXISTING VARIABLES - Don't redeclare!
        String originalFileName = file.getOriginalFilename();
        String fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));

        // ⭐ NEW: Extract extension for validation (without the dot)
        String extensionForValidation = fileExtension.substring(1).toUpperCase(); // Remove dot, convert to uppercase

        // ⭐ NEW: Check if format is allowed
        if (!settingsService.isFormatAllowed(extensionForValidation)) {
            throw new RuntimeException(String.format("File format '%s' is not allowed. Allowed formats: %s",
                    extensionForValidation, String.join(", ", settingsService.getAllowedFormats())));
        }

        // REMOVE THIS OLD CHECK (commented out or delete):
        // if (!"application/pdf".equals(file.getContentType())) {
        //     throw new RuntimeException("Only PDF files are allowed");
        // }

        File uploadDirectory = new File(uploadDir);
        if (!uploadDirectory.exists()) {
            uploadDirectory.mkdirs();
        }

//        String originalFileName = file.getOriginalFilename();
//        String fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
        String uniqueFileName = UUID.randomUUID().toString() + fileExtension;
        Path filePath = Paths.get(uploadDir, uniqueFileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        // Build publishDate from year/month
        if (documentDto.getPublishYear() != null && !documentDto.getPublishYear().isEmpty()) {
            if (documentDto.getPublishMonth() != null && !documentDto.getPublishMonth().isEmpty()) {
                documentDto.setPublishDate(documentDto.getPublishYear() + "-" + documentDto.getPublishMonth());
            } else {
                documentDto.setPublishDate(documentDto.getPublishYear());
            }
        } else {
            documentDto.setPublishDate(null);
        }

        String publishDate = (documentDto.getPublishDate() != null && !documentDto.getPublishDate().isEmpty())
                ? documentDto.getPublishDate()
                : null;

        logger.info("Saving Doc -> Title: {}, PublishDate: {}, Tags: {}, Classifications: {}, Groups: {}",
                documentDto.getTitle(), publishDate, documentDto.getTagNames(),
                documentDto.getClassificationNames(), groupIds);

        Long documentId = storedProcedureRepository.uploadDocument(
                documentDto.getTitle(),
                documentDto.getProductCode(),
                documentDto.getEdition(),
                publishDate,
                documentDto.getNoOfPages(),
                documentDto.getNotes(),
                filePath.toString(),
                user.getId(),
                documentDto.getTagNames(),
                documentDto.getClassificationNames()
        );

        if (documentId == null) {
            throw new RuntimeException("Stored procedure failed to return document ID");
        }

        logger.info("✅ Document uploaded successfully. ID = {}", documentId);

        // ✅ Link uploaded document to selected groups (if any)
        if (groupIds != null && !groupIds.trim().isEmpty()) {
            List<Long> groupIdList = Arrays.stream(groupIds.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::valueOf)
                    .collect(Collectors.toList());

            if (!groupIdList.isEmpty()) {
                for (Long groupId : groupIdList) {
                    try {
                        // Uses existing access-control logic; it already checks duplicates
                        groupService.addDocumentToGroup(groupId, documentId);
                        logger.info("✅ Linked document {} to group {}", documentId, groupId);
                    } catch (Exception e) {
                        logger.error("❌ Failed to link document {} to group {}: {}",
                                documentId, groupId, e.getMessage());
                        // Continue with other groups even if one fails
                    }
                }
                logger.info("✅ Successfully linked document {} to {} groups", documentId, groupIdList.size());
            }
        } else {
            logger.info("ℹ️ No groups selected for document {}", documentId);
        }
    }

    // ✅ UPDATED: Added groupIds parameter and group update logic
    @Override
    @Transactional
    public void updateDocument(Long id, DocumentDto documentDto, MultipartFile file, String username, String groupIds) throws Exception {
        logger.info("Updating document ID: {}", id);

        // Handle file if provided
        String filePathStr = null;
        if (file != null && !file.isEmpty()) {

            // ⭐ NEW: Get max file size from settings
            Integer maxFileSizeMB = settingsService.getMaxFileSizeMB();
            long maxSizeBytes = maxFileSizeMB * 1024L * 1024L;

            // ⭐ NEW: Check file size against settings
            if (file.getSize() > maxSizeBytes) {
                throw new RuntimeException(String.format("File size (%dMB) exceeds maximum allowed size of %dMB",
                        file.getSize() / (1024 * 1024), maxFileSizeMB));
            }

            // ⭐ EXISTING VARIABLES - Don't redeclare!
            String originalFileName = file.getOriginalFilename();
            String fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));

            // ⭐ NEW: Extract extension for validation (without the dot)
            String extensionForValidation = fileExtension.substring(1).toUpperCase(); // Remove dot, convert to uppercase

            // ⭐ NEW: Check if format is allowed
            if (!settingsService.isFormatAllowed(extensionForValidation)) {
                throw new RuntimeException(String.format("File format '%s' is not allowed. Allowed formats: %s",
                        extensionForValidation, String.join(", ", settingsService.getAllowedFormats())));
            }

            // ⭐ REMOVE THIS OLD CHECK (commented out or delete):
            // if (!"application/pdf".equals(file.getContentType())) {
            //     throw new RuntimeException("Only PDF files are allowed");
            // }


            File uploadDirectory = new File(uploadDir);
            if (!uploadDirectory.exists()) {
                uploadDirectory.mkdirs();
            }

//            String originalFileName = file.getOriginalFilename();
//            String fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
            String uniqueFileName = UUID.randomUUID().toString() + fileExtension;
            Path filePath = Paths.get(uploadDir, uniqueFileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            filePathStr = filePath.toString();
        }

        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found with id: " + id));

        if (documentDto.getFilePath() != null && !documentDto.getFilePath().isEmpty()) {
            document.setFilePath(documentDto.getFilePath());
        }

        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        if (documentDto.getPublishYear() != null && !documentDto.getPublishYear().isEmpty()) {
            if (documentDto.getPublishMonth() != null && !documentDto.getPublishMonth().isEmpty()) {
                documentDto.setPublishDate(documentDto.getPublishYear() + "-" + documentDto.getPublishMonth());
            } else {
                documentDto.setPublishDate(documentDto.getPublishYear());
            }
        } else {
            documentDto.setPublishDate(null);
        }

        String publishDate = (documentDto.getPublishDate() != null && !documentDto.getPublishDate().isEmpty())
                ? documentDto.getPublishDate()
                : null;

        logger.info("Updating Doc -> ID: {}, Title: {}, PublishDate: {}, Tags: {}, Classifications: {}, Groups: {}",
                id, documentDto.getTitle(), publishDate, documentDto.getTagNames(),
                documentDto.getClassificationNames(), groupIds);

        boolean updated = storedProcedureRepository.updateDocument(
                id,
                documentDto.getTitle(),
                documentDto.getProductCode(),
                documentDto.getEdition(),
                publishDate,
                documentDto.getNoOfPages(),
                documentDto.getNotes(),
                documentDto.getTagNames(),
                documentDto.getClassificationNames()
        );

        if (!updated) {
            throw new RuntimeException("Document not found or update failed");
        }

        logger.info("✅ Document metadata updated successfully: {}", id);

        // ✅ NEW: Update group associations
        // First, remove all existing group associations for this document
        try {
            accessControlLogicRepository.deleteByDocumentId(id);
            logger.info("✅ Removed existing group associations for document {}", id);
        } catch (Exception e) {
            logger.warn("⚠️ No existing group associations to remove for document {}: {}", id, e.getMessage());
        }

        // Then, add new group associations
        if (groupIds != null && !groupIds.trim().isEmpty()) {
            List<Long> groupIdList = Arrays.stream(groupIds.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::valueOf)
                    .collect(Collectors.toList());

            if (!groupIdList.isEmpty()) {
                for (Long groupId : groupIdList) {
                    try {
                        groupService.addDocumentToGroup(groupId, id);
                        logger.info("✅ Linked document {} to group {}", id, groupId);
                    } catch (Exception e) {
                        logger.error("❌ Failed to link document {} to group {}: {}",
                                id, groupId, e.getMessage());
                        // Continue with other groups even if one fails
                    }
                }
                logger.info("✅ Successfully updated group associations for document {}", id);
            }
        } else {
            logger.info("ℹ️ No groups selected for document {} (all associations removed)", id);
        }
    }
    //groupname sdor docuemnts- Lochan
    public String getGroupNamesForDocument(Long documentId) {
        try {
            List<AccessControlLogic> accessControls =
                    accessControlLogicRepository.findByDocumentId(documentId);

            if (accessControls == null || accessControls.isEmpty()) {
                return "";
            }

            return accessControls.stream()
                    .map(ac -> ac.getGroup().getGroupName())
                    .collect(Collectors.joining(", "));

        } catch (Exception e) {
            logger.warn("Failed to fetch groups for document {}: {}", documentId, e.getMessage());
            return "";
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentDto> findAllDocuments() {
        return documentRepository.findAll()
                .stream().map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentDto findDocumentById(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        return convertToDto(document);
    }

    @Override
    @Transactional
    public void deleteDocument(Long id) {
        logger.info("Deleting document ID: {}", id);

        Map<String, Object> result = storedProcedureRepository.deleteDocument(id);
        Boolean deleted = (Boolean) result.get("deleted");
        String filePath = (String) result.get("filePath");

        if (deleted == null || !deleted) {
            throw new RuntimeException("Failed to delete document");
        }

        if (filePath != null && !filePath.isEmpty()) {
            try {
                Files.deleteIfExists(Paths.get(filePath));
                logger.info("Deleted physical file: {}", filePath);
            } catch (Exception e) {
                logger.error("Error deleting file: {}", filePath, e);
            }
        }
    }

    @Override
    public String getFilePath(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        return document.getFilePath();
    }

    private DocumentDto convertToDto(Document document) {
        DocumentDto dto = new DocumentDto();

        dto.setId(document.getId());
        dto.setTitle(document.getTitle());
        dto.setProductCode(document.getProductCode());
        dto.setEdition(document.getEdition());

        if (document.getPublishDate() != null) {
            dto.setPublishDate(document.getPublishDate());  // e.g. "2024-05"
            String[] parts = document.getPublishDate().split("-");
            dto.setPublishYear(parts[0]);
            if (parts.length > 1) {
                dto.setPublishMonth(parts[1]);
            }
        }

        dto.setNoOfPages(document.getNoOfPages());
        dto.setNotes(document.getNotes());
        dto.setFilePath(document.getFilePath());

        if (document.getUploadedAt() != null) {
            dto.setUploadedAt(document.getUploadedAt()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }

        if (document.getUploadedBy() != null) {
            dto.setUploadedByUsername(document.getUploadedBy().getUsername());
        }

        dto.setTagNames(document.getTags().stream()
                .map(Tag::getTagName)
                .collect(Collectors.joining(",")));

        dto.setClassificationNames(document.getClassifications().stream()
                .map(Classification::getClassificationName)
                .collect(Collectors.joining(",")));

        return dto;
    }

    @Override
    public List<DocumentDto> findDocumentsAccessibleByUser(Long userId) {
        List<Document> docs = documentRepository.findDocumentsAccessibleByUser(userId);
        return docs.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    /**
     * ⭐ NEW METHOD - Get accessible document IDs using document library logic
     * This ensures bookmarks use the SAME access logic as the document library
     */
    @Override
    public List<Long> getAccessibleDocumentIds(Long userId) {
        try {
            logger.info("Getting accessible document IDs for user: {}", userId);

            // Use the SAME method that document library uses
            List<Document> accessibleDocs = documentRepository.findDocumentsAccessibleByUser(userId);

            List<Long> documentIds = accessibleDocs.stream()
                    .map(Document::getId)
                    .collect(Collectors.toList());

            logger.info("User {} has access to {} documents", userId, documentIds.size());

            return documentIds;

        } catch (Exception e) {
            logger.error("Error getting accessible documents for user {}: {}", userId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * ⭐ NEW METHOD - Check if a user has access to a specific document
     * - Admins always have access (checked in controller)
     * - For other users, check if they're in allowed groups
     *
     * @param userId - User ID to check
     * @param documentId - Document ID to check
     * @return true if user has access, false otherwise
     */
    /**
     * ⭐ ENHANCED - Check if a user has access to a specific document
     * Now with detailed logging for debugging access issues
     */
    /**
     * ⭐ UPDATED - Check access using document library logic
     * Now uses the SAME logic as document library for consistency
     */
    @Override
    public boolean hasUserAccessToDocument(Long userId, Long documentId) {
        try {
            logger.info("=== ACCESS CHECK (Using Document Library Logic) ===");
            logger.info("User ID: {}, Document ID: {}", userId, documentId);

            // Get all accessible document IDs using document library logic
            List<Long> accessibleDocIds = getAccessibleDocumentIds(userId);

            // Check if the document is in the accessible list
            boolean hasAccess = accessibleDocIds.contains(documentId);

            if (hasAccess) {
                logger.info("✅ User {} HAS access to document {} (found in document library)", userId, documentId);
            } else {
                logger.warn("❌ User {} DOES NOT have access to document {} (NOT in document library)", userId, documentId);
            }

            logger.info("=== ACCESS CHECK END: {} ===", hasAccess ? "GRANTED" : "DENIED");
            return hasAccess;

        } catch (Exception e) {
            logger.error("❌ ERROR checking document access for user {} and document {}: {}",
                    userId, documentId, e.getMessage(), e);
            logger.info("=== ACCESS CHECK END: DENIED (Error) ===");
            return false;
        }
    }
}