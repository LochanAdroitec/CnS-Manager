package codesAndStandards.springboot.userApp.repository;

import codesAndStandards.springboot.userApp.entity.Bookmark;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
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
//    List<Bookmark> findByUserIdAndDocumentIdOrderByPageNumberAsc(Long userId, Integer documentId);
//    List<Bookmark> findByUserIdAndDocumentId(Long userId, Integer documentId);
    /**
     * Find specific bookmark by user, document
     */
//    Optional<Bookmark> findByUserIdAndDocumentIdAndPageNumber(Long userId, Integer documentId, Integer pageNumber);
    Optional<Bookmark> findByUserIdAndDocumentId(Long userId, Long documentId);

    /**
     * Find all bookmarks for a specific user
     */
    List<Bookmark> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Find all bookmarks for a specific document
     */
//    List<Bookmark> findByDocumentIdOrderByPageNumberAsc(Integer documentId);
    List<Bookmark> findByDocumentId(Long documentId);
    /**
     * Delete specific bookmark by ID
     */
    void deleteById(Long bookmarkId);

    /**
     * Check if bookmark exists for specific page
     */
//    boolean existsByUserIdAndDocumentIdAndPageNumber(Long userId, Integer documentId, Integer pageNumber);
    boolean existsByUserIdAndDocumentId(Long userId, Long documentId);

    @Procedure(procedureName = "AddBookmark")
    void addBookmark(
            @Param("UserId") Long userId,
            @Param("DocumentId") Long documentId,
            @Param("BookmarkName") String bookmarkName
    );

    @Procedure(procedureName = "DeleteBookmark")
    void deleteBookmarkSP(@Param("BookmarkId") Long bookmarkId);

    @Modifying
    @Query(value = "EXEC updateBookmark :bookmarkId, :bookmarkName", nativeQuery = true)
    void updateBookmarkNameSP(@Param("bookmarkId") Long bookmarkId, @Param("bookmarkName") String bookmarkName);

    @Modifying
    @Transactional
    @Query("DELETE FROM Bookmark b WHERE b.user.id = :userId AND b.document.id = :documentId")
    void deleteByUserIdAndDocumentId(@Param("userId") Long userId, @Param("documentId") Long documentId);


}