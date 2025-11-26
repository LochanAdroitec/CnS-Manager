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

        if (file.isEmpty()) {
            throw new RuntimeException("Please select a file to upload");
        }

        if (!"application/pdf".equals(file.getContentType())) {
            throw new RuntimeException("Only PDF files are allowed");
        }

        File uploadDirectory = new File(uploadDir);
        if (!uploadDirectory.exists()) {
            uploadDirectory.mkdirs();
        }

        String originalFileName = file.getOriginalFilename();
        String fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
        String uniqueFileName = UUID.randomUUID().toString() + fileExtension;
        Path filePath = Paths.get(uploadDir, uniqueFileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

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

        logger.info("Saving Doc -> Title: {}, PublishDate: {}, Tags: {}, Classifications: {}",
                documentDto.getTitle(), publishDate, documentDto.getTagNames(), documentDto.getClassificationNames());

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
    }

    @Override
    @Transactional
    public void updateDocument(Long id, DocumentDto documentDto, MultipartFile file, String username) throws Exception {
        logger.info("Updating document ID: {}", id);

        // 1️⃣ Handle file if provided
        String filePathStr = null;
        if (file != null && !file.isEmpty()) {
            if (!"application/pdf".equals(file.getContentType())) {
                throw new RuntimeException("Only PDF files are allowed");
            }

            File uploadDirectory = new File(uploadDir);
            if (!uploadDirectory.exists()) {
                uploadDirectory.mkdirs();
            }

            String originalFileName = file.getOriginalFilename();
            String fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
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

        // 2️⃣ Validate user
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        // 3️⃣ Handle publish date from year/month
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

        logger.info("Updating Doc -> ID: {}, Title: {}, PublishDate: {}, Tags: {}, Classifications: {}",
                id, documentDto.getTitle(), publishDate, documentDto.getTagNames(), documentDto.getClassificationNames());

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

        logger.info("✅ Document updated successfully: {}", id);
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
}
