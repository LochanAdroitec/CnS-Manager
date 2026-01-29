package codesAndStandards.springboot.userApp.service;

import codesAndStandards.springboot.userApp.dto.DocumentDto;
import codesAndStandards.springboot.userApp.entity.Document;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface DocumentService {
    // ✅ UPDATED: Added groupIds parameter
    void saveDocument(DocumentDto documentDto, MultipartFile file, String username, String groupIds) throws Exception;

    List<DocumentDto> findAllDocuments();

    DocumentDto findDocumentById(Long id);

    // ✅ UPDATED: Added groupIds parameter
    void updateDocument(Long id, DocumentDto documentDto, MultipartFile file, String username, String groupIds) throws Exception;

    void deleteDocument(Long id);

    String getFilePath(Long id);

    List<DocumentDto> findDocumentsAccessibleByUser(Long userId);

    String getGroupNamesForDocument(Long documentId);
    /**
     * ⭐ NEW - Check if user has access to document
     */
    boolean hasUserAccessToDocument(Long userId, Long documentId);
    /**
     * ⭐ NEW - Get list of accessible document IDs for a user
     * This uses the SAME logic as the document library
     */
    List<Long> getAccessibleDocumentIds(Long userId);
}