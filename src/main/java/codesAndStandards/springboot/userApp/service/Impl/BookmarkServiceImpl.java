package codesAndStandards.springboot.userApp.service.Impl;

import codesAndStandards.springboot.userApp.dto.BookmarkDto;
import codesAndStandards.springboot.userApp.entity.Bookmark;
import codesAndStandards.springboot.userApp.entity.Document;
import codesAndStandards.springboot.userApp.entity.User;
import codesAndStandards.springboot.userApp.repository.BookmarkRepository;
import codesAndStandards.springboot.userApp.repository.DocumentRepository;
import codesAndStandards.springboot.userApp.repository.UserRepository;
import codesAndStandards.springboot.userApp.service.BookmarkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class BookmarkServiceImpl implements BookmarkService {

    private static final Logger logger = LoggerFactory.getLogger(BookmarkServiceImpl.class);

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Override
    @Transactional
    public BookmarkDto saveBookmark(Long userId, Long documentId, String bookmarkName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        try {
            // 🔹 Attempt to call stored procedure
            bookmarkRepository.addBookmark(userId, documentId, bookmarkName);
            logger.info("✅ Bookmark added successfully for user: {} on document: {} via Stored Procedure",
                    userId, documentId);

            // If stored procedure succeeds, fetch and return the saved bookmark
            Bookmark savedBookmark = bookmarkRepository.findByUserIdAndDocumentId(userId, documentId)
                    .orElseThrow(() -> new RuntimeException("Bookmark not found after creation"));
            return convertToDto(savedBookmark);

        } catch (Exception e) {
            // 🔹 If stored procedure fails due to unique constraint, try fallback logic
            if (e.getMessage() != null &&
                    (e.getMessage().contains("UK_user_document") || e.getMessage().contains("duplicate key"))) {

                logger.warn("⚠ Bookmark already exists for user: {} and document: {}, skipping duplicate.",
                        userId, documentId);

                // ✅ Return existing bookmark instead of throwing an error
                return bookmarkRepository.findByUserIdAndDocumentId(userId, documentId)
                        .map(this::convertToDto)
                        .orElseThrow(() -> new RuntimeException("Bookmark already exists but could not be retrieved."));
            }

            // 🔹 For any other error, log and try fallback insert (if you want)
            logger.error("Stored procedure failed, trying normal save: {}", e.getMessage());

            try {
                Bookmark fallbackBookmark = new Bookmark();
                fallbackBookmark.setUser(user);
                fallbackBookmark.setDocument(document);
                fallbackBookmark.setBookmarkName(bookmarkName);
                fallbackBookmark.setCreatedAt(LocalDateTime.now());

                fallbackBookmark = bookmarkRepository.save(fallbackBookmark);
                logger.info("✅ Bookmark saved via fallback logic after stored procedure failure.");
                return convertToDto(fallbackBookmark);
            } catch (Exception ex) {
                logger.error("❌ Fallback insert also failed: {}", ex.getMessage());
                throw new RuntimeException("Error while adding bookmark: " + ex.getMessage());
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookmarkDto> getDocumentBookmarks(Long userId, Long documentId) {
//        return bookmarkRepository.findByUserIdAndDocumentIdOrderByPageNumberAsc(userId, documentId)
        return bookmarkRepository.findByUserIdAndDocumentId(userId, documentId)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookmarkDto> getUserBookmarks(Long userId) {
        return bookmarkRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
//    public void deleteBookmark(Long bookmarkId) {
//        bookmarkRepository.deleteById(bookmarkId);
//    }
    public void deleteBookmark(Long bookmarkId) {
        try {
            bookmarkRepository.deleteBookmarkSP(bookmarkId);
            logger.info("Bookmark deleted successfully via Stored Procedure, bookmarkId: {}", bookmarkId);
        } catch (Exception e) {
            if (e.getMessage().contains("Bookmark not found")) {
                throw new RuntimeException("Bookmark not found with ID: " + bookmarkId);
            }
            bookmarkRepository.deleteById(bookmarkId);
//            throw new RuntimeException("Error deleting bookmark: " + e.getMessage());
        }
    }
    @Override
    @Transactional(readOnly = true)
//    public boolean isPageBookmarked(Long userId, Long documentId, Integer pageNumber) {
//        return bookmarkRepository.existsByUserIdAndDocumentIdAndPageNumber(userId, documentId, pageNumber);
//    }

    public boolean isDocumentBookmarked(Long userId, Long documentId) {
        return bookmarkRepository.existsByUserIdAndDocumentId(userId, documentId);
    }

    /**
     * Convert Bookmark entity to DTO
     */
    private BookmarkDto convertToDto(Bookmark bookmark) {
        BookmarkDto dto = new BookmarkDto();
        dto.setId(bookmark.getId());
        dto.setUserId(bookmark.getUser().getId());
        dto.setDocumentId(bookmark.getDocument().getId());
//        dto.setPageNumber(bookmark.getPageNumber());
        dto.setBookmarkName(bookmark.getBookmarkName());
        dto.setCreatedAt(bookmark.getCreatedAt());

        // Set additional display fields
        dto.setUserName(bookmark.getUser().getUsername());
        dto.setDocumentTitle(bookmark.getDocument().getTitle());

        return dto;
    }

    @Override
    @Transactional
    public void updateBookmarkName(Long bookmarkId, String newName) {
        try {
            // Call stored procedure
            bookmarkRepository.updateBookmarkNameSP(bookmarkId, newName);
            logger.info("✅ Bookmark name updated successfully via Stored Procedure (ID: {})", bookmarkId);
        } catch (Exception e) {
            // Fallback: If SP fails, update via JPA
            logger.error("⚠ Stored Procedure failed for updateBookmarkName: {}, trying fallback. Error: {}", bookmarkId, e.getMessage());
            Bookmark bookmark = bookmarkRepository.findById(bookmarkId)
                    .orElseThrow(() -> new RuntimeException("Bookmark not found with ID: " + bookmarkId));
            bookmark.setBookmarkName(newName);
            bookmarkRepository.save(bookmark);
        }
    }

    @Override
    @Transactional
    public void deleteBookmarkByDocument(Long userId, Long documentId) {
        try {
            // Check if bookmark exists first
            Optional<Bookmark> bookmark = bookmarkRepository.findByUserIdAndDocumentId(userId, documentId);
            if (bookmark.isEmpty()) {
                throw new RuntimeException("Bookmark not found for user " + userId + " and document " + documentId);
            }

            // Delete the bookmark
            bookmarkRepository.deleteByUserIdAndDocumentId(userId, documentId);
            logger.info("✅ Bookmark deleted successfully for user: {} and document: {}", userId, documentId);
        } catch (Exception e) {
            logger.error("❌ Error deleting bookmark for user: {} and document: {}", userId, documentId, e);
            throw new RuntimeException("Failed to delete bookmark: " + e.getMessage());
        }
    }



}