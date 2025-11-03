package codesAndStandards.springboot.userApp.repository;

import codesAndStandards.springboot.userApp.entity.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    /**
     * Find all bookmarks for a user and document (can be multiple pages)
     */
//    List<Bookmark> findByUserIdAndDocumentIdOrderByPageNumberAsc(Long userId, Long documentId);

    /**
     * Find specific bookmark by user, document, and page number
     */
//    Optional<Bookmark> findByUserIdAndDocumentIdAndPageNumber(Long userId, Long documentId, Integer pageNumber);
    Optional<Bookmark> findByUserIdAndDocumentId(Long userId, Long documentId);

    /**
     * Find all bookmarks for a specific user
     */
    List<Bookmark> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Find all bookmarks for a specific document
     */
//    List<Bookmark> findByDocumentIdOrderByPageNumberAsc(Long documentId);
    List<Bookmark> findByDocumentId(Long documentId);

    /**
     * Delete specific bookmark by ID
     */
    void deleteById(Long bookmarkId);

    /**
     * Check if bookmark exists for specific page
     */
//    boolean existsByUserIdAndDocumentIdAndPageNumber(Long userId, Long documentId, Integer pageNumber);
    boolean existsByUserIdAndDocumentId(Long userId, Long documentId);

    //Only for stored procedure
    @Procedure(procedureName = "AddBookmark")
    void addBookmark(
            @Param("UserId") Long userId,
            @Param("DocumentId") Long documentId,
            @Param("BookmarkName") String bookmarkName
    );

    @Procedure(procedureName = "DeleteBookmark")
    void deleteBookmarkSP(@Param("BookmarkId") Long bookmarkId);
}