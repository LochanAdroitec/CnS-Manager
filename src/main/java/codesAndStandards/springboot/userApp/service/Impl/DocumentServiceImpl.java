package codesAndStandards.springboot.userApp.service.Impl;

import codesAndStandards.springboot.userApp.dto.DocumentDto;
import codesAndStandards.springboot.userApp.entity.Classification;
import codesAndStandards.springboot.userApp.entity.Document;
import codesAndStandards.springboot.userApp.entity.Tag;
import codesAndStandards.springboot.userApp.entity.User;
import codesAndStandards.springboot.userApp.repository.DocumentRepository;
import codesAndStandards.springboot.userApp.repository.StoredProcedureRepository;
import codesAndStandards.springboot.userApp.repository.UserRepository;
import codesAndStandards.springboot.userApp.service.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DocumentServiceImpl implements DocumentService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentServiceImpl.class);

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final StoredProcedureRepository storedProcedureRepository;

    @Value("${file.upload-dir}")
    private String uploadDir;

    public DocumentServiceImpl(DocumentRepository documentRepository,
                               UserRepository userRepository,
                               StoredProcedureRepository storedProcedureRepository) {
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.storedProcedureRepository = storedProcedureRepository;
    }

    @Override
    @Transactional
    public void saveDocument(DocumentDto documentDto, MultipartFile file, String username) throws Exception {
        // Validate file
//        Document doc = new Document();
        if (file.isEmpty()) {
            throw new RuntimeException("Please select a file to upload");
        }

        // Validate file type (only PDF)
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            throw new RuntimeException("Only PDF files are allowed");
        }

        // Create upload directory if not exists
        File uploadDirectory = new File(uploadDir);
        if (!uploadDirectory.exists()) {
            uploadDirectory.mkdirs();
        }

        // Generate unique file name
        String originalFileName = file.getOriginalFilename();
        String fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
        String uniqueFileName = UUID.randomUUID().toString() + fileExtension;

        // Save file to server
        Path filePath = Paths.get(uploadDir, uniqueFileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // Get user
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        // Parse publish date
        LocalDate publishDate = null;
        if (documentDto.getPublishDate() != null && !documentDto.getPublishDate().isEmpty()) {
            publishDate = LocalDate.parse(documentDto.getPublishDate());
        }

        logger.info("Calling stored procedure sp_UploadDocument for document: {}", documentDto.getTitle());
        logger.info("Tags: {}, Classifications: {}", documentDto.getTagNames(), documentDto.getClassificationNames());


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
            logger.error("Stored procedure failed to return document ID");
            throw new RuntimeException("Failed to upload document - no ID returned");
        }

        logger.info("✅ Document uploaded successfully using STORED PROCEDURE sp_UploadDocument. Document ID: {}", documentId);
        logger.info("File saved to: {}", filePath.toString());
//        Document savedDoc = documentRepository.save(doc);
//        return savedDoc;
    }

    @Override
    @Transactional
    public void updateDocument(Long id, DocumentDto documentDto) throws Exception {
        logger.info("Calling stored procedure sp_UpdateDocument for document ID: {}", id);
        logger.info("New Title: {}, Tags: {}, Classifications: {}",
                documentDto.getTitle(), documentDto.getTagNames(), documentDto.getClassificationNames());

        // Parse publish date
        LocalDate publishDate = null;
        if (documentDto.getPublishDate() != null && !documentDto.getPublishDate().isEmpty()) {
            publishDate = LocalDate.parse(documentDto.getPublishDate());
        }

        // Call stored procedure to update document
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
            logger.error("Failed to update document with ID: {}", id);
            throw new RuntimeException("Document not found or failed to update");
        }

        logger.info("✅ Document updated successfully using STORED PROCEDURE sp_UpdateDocument. Document ID: {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentDto> findAllDocuments() {
        return documentRepository.findAll()
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentDto findDocumentById(Long id) {
        logger.info("========== FINDING DOCUMENT BY ID: {} ==========", id);

        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        logger.info("Document found: {}", document.getTitle());

        // Force initialization of lazy-loaded collections
        int tagCount = document.getTags().size();
        int classCount = document.getClassifications().size();

        logger.info("Tags count: {}", tagCount);
        logger.info("Classifications count: {}", classCount);

        // Log each tag
        if (tagCount > 0) {
            document.getTags().forEach(tag ->
                    logger.info("  - Tag: {}", tag.getTagName())
            );
        } else {
            logger.warn("NO TAGS FOUND for document ID: {}", id);
        }

        // Log each classification
        if (classCount > 0) {
            document.getClassifications().forEach(classification ->
                    logger.info("  - Classification: {}", classification.getClassificationName())
            );
        } else {
            logger.warn("NO CLASSIFICATIONS FOUND for document ID: {}", id);
        }

        DocumentDto dto = convertToDto(document);

        logger.info("DTO tagNames: '{}'", dto.getTagNames());
        logger.info("DTO classificationNames: '{}'", dto.getClassificationNames());
        logger.info("========== END FINDING DOCUMENT ==========");

        return dto;
    }

    @Override
    @Transactional
    public void deleteDocument(Long id) {
        logger.info("Calling stored procedure sp_DeleteDocument for document ID: {}", id);

        // Call stored procedure to delete document
        Map<String, Object> result = storedProcedureRepository.deleteDocument(id);

        Boolean deleted = (Boolean) result.get("deleted");
        String filePath = (String) result.get("filePath");

        if (deleted == null || !deleted) {
            logger.error("Failed to delete document with ID: {}. Document not found or deletion failed.", id);
            throw new RuntimeException("Document not found or failed to delete");
        }

        logger.info("✅ Document deleted successfully using STORED PROCEDURE sp_DeleteDocument. Document ID: {}", id);

        // Delete file from server
        if (filePath != null && !filePath.isEmpty()) {
            try {
                Path path = Paths.get(filePath);
                boolean fileDeleted = Files.deleteIfExists(path);
                if (fileDeleted) {
                    logger.info("✅ Physical file deleted successfully: {}", filePath);
                } else {
                    logger.warn("⚠️ Physical file not found or already deleted: {}", filePath);
                }
            } catch (Exception e) {
                logger.error("❌ Error deleting physical file: {}", filePath, e);
                // Don't throw exception - database deletion was successful
            }
        } else {
            logger.warn("⚠️ No file path returned from stored procedure for document ID: {}", id);
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
            dto.setPublishDate(document.getPublishDate().toString());
        }

        dto.setNoOfPages(document.getNoOfPages());
        dto.setNotes(document.getNotes());
        dto.setFilePath(document.getFilePath());

        if (document.getUploadedAt() != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            dto.setUploadedAt(document.getUploadedAt().format(formatter));
        }

        if (document.getUploadedBy() != null) {
            dto.setUploadedByUsername(document.getUploadedBy().getUsername());
        }

        // ✅ Load tags and classifications
        if (document.getTags() != null && !document.getTags().isEmpty()) {
            String tagNames = document.getTags().stream()
                    .map(Tag::getTagName)
                    .collect(Collectors.joining(","));
            dto.setTagNames(tagNames);
            logger.debug("Tags loaded: {}", tagNames);
        } else {
            dto.setTagNames("");
            logger.debug("No tags found for document");
        }

        if (document.getClassifications() != null && !document.getClassifications().isEmpty()) {
            String classificationNames = document.getClassifications().stream()
                    .map(Classification::getClassificationName)
                    .collect(Collectors.joining(","));
            dto.setClassificationNames(classificationNames);
            logger.debug("Classifications loaded: {}", classificationNames);
        } else {
            dto.setClassificationNames("");
            logger.debug("No classifications found for document");
        }

        return dto;
    }
}