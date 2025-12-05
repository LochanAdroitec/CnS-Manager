package codesAndStandards.springboot.userApp.repository;

import codesAndStandards.springboot.userApp.entity.AccessControlLogic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccessControlLogicRepository extends JpaRepository<AccessControlLogic, Long> {

    /**
     * Find all documents in a specific group
     */
    @Query("SELECT acl FROM AccessControlLogic acl " +
            "JOIN FETCH acl.document " +
            "WHERE acl.group.id = :groupId")
    List<AccessControlLogic> findByGroupId(@Param("groupId") Long groupId);

    /**
     * Find all groups for a specific document
     */
    @Query("SELECT acl FROM AccessControlLogic acl " +
            "JOIN FETCH acl.group " +
            "WHERE acl.document.id = :documentId")
    List<AccessControlLogic> findByDocumentId(@Param("documentId") Long documentId);

    /**
     * Check if document is in a group
     */
    @Query("SELECT CASE WHEN COUNT(acl) > 0 THEN true ELSE false END " +
            "FROM AccessControlLogic acl " +
            "WHERE acl.document.id = :documentId AND acl.group.id = :groupId")
    boolean existsByDocumentIdAndGroupId(@Param("documentId") Long documentId, @Param("groupId") Long groupId);

    /**
     * Find specific document-group association
     */
    @Query("SELECT acl FROM AccessControlLogic acl " +
            "WHERE acl.document.id = :documentId AND acl.group.id = :groupId")
    Optional<AccessControlLogic> findByDocumentIdAndGroupId(@Param("documentId") Long documentId, @Param("groupId") Long groupId);

    /**
     * Delete all documents from a group
     */
    @Modifying
    @Query("DELETE FROM AccessControlLogic acl WHERE acl.group.id = :groupId")
    void deleteByGroupId(@Param("groupId") Long groupId);

    /**
     * ✅ NEW: Delete all group associations for a specific document
     * Used when updating a document's group assignments
     */
    @Modifying
    @Query("DELETE FROM AccessControlLogic acl WHERE acl.document.id = :documentId")
    void deleteByDocumentId(@Param("documentId") Long documentId);

    /**
     * Delete specific document from a group
     */
    @Modifying
    @Query("DELETE FROM AccessControlLogic acl WHERE acl.document.id = :documentId AND acl.group.id = :groupId")
    void deleteByDocumentIdAndGroupId(@Param("documentId") Long documentId, @Param("groupId") Long groupId);

    /**
     * Count documents in a group
     */
    @Query("SELECT COUNT(acl) FROM AccessControlLogic acl WHERE acl.group.id = :groupId")
    long countByGroupId(@Param("groupId") Long groupId);

    /**
     * Get document IDs in a group
     */
    @Query("SELECT acl.document.id FROM AccessControlLogic acl WHERE acl.group.id = :groupId")
    List<Long> findDocumentIdsByGroupId(@Param("groupId") Long groupId);

    /**
     * Check if user has access to document through any group
     */
    @Query("SELECT CASE WHEN COUNT(acl) > 0 THEN true ELSE false END " +
            "FROM AccessControlLogic acl " +
            "JOIN GroupUser gu ON acl.group.id = gu.group.id " +
            "WHERE gu.user.id = :userId AND acl.document.id = :documentId")
    boolean hasUserAccessToDocument(@Param("userId") Long userId, @Param("documentId") Long documentId);

//    @Query("SELECT acl FROM AccessControlLogic acl WHERE acl.document.id = :documentId")
//    List<AccessControlLogic> findByDocumentId(@Param("documentId") int documentId);

    /**
     * Get all document IDs accessible by a user
     */
    @Query("SELECT DISTINCT acl.document.id " +
            "FROM AccessControlLogic acl " +
            "JOIN GroupUser gu ON acl.group.id = gu.group.id " +
            "WHERE gu.user.id = :userId")
    List<Long> findAccessibleDocumentIdsByUserId(@Param("userId") Long userId);
}