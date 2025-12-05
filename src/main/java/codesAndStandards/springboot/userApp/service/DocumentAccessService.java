package codesAndStandards.springboot.userApp.service;

import codesAndStandards.springboot.userApp.entity.Document;
import codesAndStandards.springboot.userApp.entity.User;
import codesAndStandards.springboot.userApp.repository.AccessControlLogicRepository;
import codesAndStandards.springboot.userApp.repository.DocumentRepository;
import codesAndStandards.springboot.userApp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentAccessService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final AccessControlLogicRepository accessControlLogicRepository;

    /**
     * Get all documents accessible by current user based on their role and group membership
     */
    @Transactional(readOnly = true)
    public List<Document> getAccessibleDocuments() {
        User currentUser = getCurrentUser();

        if (currentUser == null) {
            log.warn("No authenticated user found");
            return List.of();
        }

        String role = currentUser.getRole() != null ? currentUser.getRole().getRoleName() : "Viewer";

        log.info("Getting accessible documents for user: {} with role: {}",
                currentUser.getUsername(), role);

        // Admin can see all documents -AJ
        if ("Admin".equals(role)) {
            log.info("Admin user - returning all documents");
//            return documentRepository.findDocumentsAccessibleByUser(currentUser.getId());
            return documentRepository.findAll();
        }

        // Manager and other roles - return documents from their groups
        List<Long> accessibleDocIds = accessControlLogicRepository
                .findAccessibleDocumentIdsByUserId(currentUser.getId());



        log.info("User {} has access to {} documents through groups",
                currentUser.getUsername(), accessibleDocIds.size());

        if (accessibleDocIds.isEmpty()) {
            log.info("User {} has no group access - returning empty list", currentUser.getUsername());
            return List.of();
        }

        List<Document> documents = documentRepository.findAllById(accessibleDocIds);
        log.info("Returning {} accessible documents", documents.size());

        return documents;
    }

    /**
     * Get all documents accessible by specific user ID
     */
    @Transactional(readOnly = true)
    public List<Document> getAccessibleDocumentsByUserId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String role = user.getRole() != null ? user.getRole().getRoleName() : "Viewer";

        // Admin can see all documents
        if ("Admin".equals(role)) {
//            return documentRepository.findDocumentsAccessibleByUser(userId);
            return documentRepository.findAll();
        }

        // Get accessible document IDs from groups
        List<Long> accessibleDocIds = accessControlLogicRepository
                .findAccessibleDocumentIdsByUserId(userId);
//                .stream()
//                .map(Long::intValue)
//                .collect(Collectors.toList());

        if (accessibleDocIds.isEmpty()) {
            return List.of();
        }

        return documentRepository.findAllById(accessibleDocIds);
    }

    /**
     * Check if current user has access to a specific document
     */
    @Transactional(readOnly = true)
    public boolean hasAccessToDocument(Long documentId) {
        User currentUser = getCurrentUser();

        if (currentUser == null) {
            return false;
        }

        String role = currentUser.getRole() != null ? currentUser.getRole().getRoleName() : "Viewer";

        // Admin has access to all documents
        if ("Admin".equals(role)) {
            return true;
        }

        // Check if user has access through groups
        return accessControlLogicRepository.hasUserAccessToDocument(currentUser.getId(), documentId);
    }

    /**
     * Check if specific user has access to a document
     */
    @Transactional(readOnly = true)
    public boolean hasAccessToDocument(Long userId, Long documentId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String role = user.getRole() != null ? user.getRole().getRoleName() : "Viewer";

        // Admin has access to all documents
        if ("Admin".equals(role)) {
            return true;
        }

        // Check if user has access through groups
        return accessControlLogicRepository.hasUserAccessToDocument(userId, documentId);
    }

    /**
     * Get accessible document IDs for current user
     */
    @Transactional(readOnly = true)
    public List<Long> getAccessibleDocumentIds() {
        User currentUser = getCurrentUser();

        if (currentUser == null) {
            return List.of();
        }

        String role = currentUser.getRole() != null ? currentUser.getRole().getRoleName() : "Viewer";

        // Admin can see all documents
        if ("Admin".equals(role)) {
            return documentRepository.findAll().stream()
                    .map(Document::getId)
                    .collect(Collectors.toList());
        }

        // Get accessible document IDs from groups
        return accessControlLogicRepository.findAccessibleDocumentIdsByUserId(currentUser.getId());
//                .stream()
//                .map(Long::intValue)
//                .collect(Collectors.toList());
    }

    /**
     * Filter documents by user access
     */
    @Transactional(readOnly = true)
    public List<Document> filterByUserAccess(List<Document> documents) {
        User currentUser = getCurrentUser();

        if (currentUser == null) {
            return List.of();
        }

        String role = currentUser.getRole() != null ? currentUser.getRole().getRoleName() : "Viewer";

        // Admin can see all documents
        if ("Admin".equals(role)) {
            return documents;
        }

        // Get accessible document IDs
        List<Long> accessibleDocIds = accessControlLogicRepository
                .findAccessibleDocumentIdsByUserId(currentUser.getId());
//                .stream()
//                .map(Long::intValue)
//                .collect(Collectors.toList());


        // Filter documents
        return documents.stream()
                .filter(doc -> accessibleDocIds.contains(doc.getId()))
                .collect(Collectors.toList());
    }

    /**
     * Get current authenticated user
     */
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() ||
                "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }

        String username = authentication.getName();

        return userRepository.findByUsername(username);
//                .orElse(null);
    }
}