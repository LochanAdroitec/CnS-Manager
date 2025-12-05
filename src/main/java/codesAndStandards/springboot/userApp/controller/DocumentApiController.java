package codesAndStandards.springboot.userApp.controller;

import codesAndStandards.springboot.userApp.dto.DocumentInfoDTO;
import codesAndStandards.springboot.userApp.entity.Document;
import codesAndStandards.springboot.userApp.repository.DocumentRepository;
import codesAndStandards.springboot.userApp.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentApiController {

    private final DocumentRepository documentRepository;
    private final UserService userService;
    /**
     * Get all documents for access control selection
     * GET /api/documents
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('Admin', 'Manager', 'User')")
    public ResponseEntity<List<DocumentInfoDTO>> getAllDocuments() {
        log.info("REST request to get documents (filtered by group access)");

        try {
            Long userId = userService.getLoggedInUserId();
//            List<Document> documents = documentRepository.findDocumentsAccessibleByUser(userId);
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
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    /**
     * Get document by ID
     * GET /api/documents/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('Admin', 'Manager', 'User')")
    public ResponseEntity<?> getDocumentById(@PathVariable Long id) {
        log.info("REST request to get document : {}", id);
        try {
            Document document = documentRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Document not found with ID: " + id));

            DocumentInfoDTO dto = DocumentInfoDTO.builder()
                    .id(document.getId())
                    .title(document.getTitle())
//                    .documentCode(document.getDocumentCode())
//                    .category(document.getCategory())
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
     */
    @GetMapping("/search")
    @PreAuthorize("hasAnyAuthority('Admin', 'Manager', 'User')")
    public ResponseEntity<List<DocumentInfoDTO>> searchDocuments(@RequestParam String query) {
        log.info("REST request to search documents with query: {}", query);
        try {
            List<Document> documents = documentRepository.findAll().stream()
                    .filter(doc ->
                            (doc.getTitle() != null && doc.getTitle().toLowerCase().contains(query.toLowerCase()))
//                                    (doc.getDocumentCode() != null && doc.getDocumentCode().toLowerCase().contains(query.toLowerCase()))
                    )
                    .collect(Collectors.toList());

            List<DocumentInfoDTO> documentDTOs = documents.stream()
                    .map(doc -> DocumentInfoDTO.builder()
                            .id(doc.getId())
                            .title(doc.getTitle())
//                            .documentCode(doc.getDocumentCode())
//                            .category(doc.getCategory())
                            .build())
                    .collect(Collectors.toList());

            return ResponseEntity.ok(documentDTOs);
        } catch (Exception e) {
            log.error("Error searching documents", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
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