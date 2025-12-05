package codesAndStandards.springboot.userApp.repository;

import codesAndStandards.springboot.userApp.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {

    /**
     * Find group by name (case-insensitive)
     */
    Optional<Group> findByGroupNameIgnoreCase(String groupName);

    /**
     * Check if group name exists (case-insensitive)
     */
    boolean existsByGroupNameIgnoreCase(String groupName);

    /**
     * Find all groups created by a specific user
     */
    @Query("SELECT g FROM Group g WHERE g.createdBy.id = :userId")
    List<Group> findByCreatedByUserId(@Param("userId") Long userId);

    /**
     * Get group with all associations eagerly loaded
     */
    @Query("SELECT g FROM Group g " +
            "LEFT JOIN FETCH g.groupUsers gu " +
            "LEFT JOIN FETCH gu.user " +
            "LEFT JOIN FETCH g.groupDocument gd " +
            "LEFT JOIN FETCH gd.document " +
            "WHERE g.id = :id")
    Optional<Group> findByIdWithAssociations(@Param("id") Long id);

    /**
     * Get all groups with counts
     */
    @Query("SELECT g FROM Group g " +
            "LEFT JOIN FETCH g.createdBy")
    List<Group> findAllWithCreator();

    /**
     * Search groups by name
     */
    @Query("SELECT g FROM Group g WHERE LOWER(g.groupName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<Group> searchByName(@Param("searchTerm") String searchTerm);

    /**
     * Find groups that contain a specific document
     */
    @Query("SELECT  g FROM Group g " +
            "JOIN g.groupDocument gd " +
            "WHERE gd.document.id = :documentId")
    List<Group> findGroupsByDocumentId(@Param("documentId") Long documentId);

    /**
     * Find groups that contain a specific user
     */
    @Query("SELECT g FROM Group g " +
            "JOIN g.groupUsers gu " +
            "WHERE gu.user.id = :userId")
    List<Group> findGroupsByUserId(@Param("userId") Long userId);
}