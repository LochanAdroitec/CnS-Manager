package codesAndStandards.springboot.userApp.repository;

import codesAndStandards.springboot.userApp.entity.GroupUser;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupUserRepository extends JpaRepository<GroupUser, Long> {

    /**
     * Find all users in a specific group
     */
    @Query("SELECT gu FROM GroupUser gu " +
            "JOIN FETCH gu.user " +
            "WHERE gu.group.id = :groupId")
    List<GroupUser> findByGroupId(@Param("groupId") Long groupId);

    /**
     * Find all groups for a specific user
     */
    @Query("SELECT gu FROM GroupUser gu " +
            "JOIN FETCH gu.group " +
            "WHERE gu.user.id = :userId")
    List<GroupUser> findByUserId(@Param("userId") Long userId);

    /**
     * Check if user is in a group
     */
    @Query("SELECT CASE WHEN COUNT(gu) > 0 THEN true ELSE false END " +
            "FROM GroupUser gu " +
            "WHERE gu.user.id = :userId AND gu.group.id = :groupId")
    boolean existsByUserIdAndGroupId(@Param("userId") Long userId, @Param("groupId") Long groupId);

    /**
     * Find specific user-group association
     */
    @Query("SELECT gu FROM GroupUser gu " +
            "WHERE gu.user.id = :userId AND gu.group.id = :groupId")
    Optional<GroupUser> findByUserIdAndGroupId(@Param("userId") Long userId, @Param("groupId") Long groupId);

    /**
     * Delete all users from a group
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM GroupUser gu WHERE gu.group.id = :groupId")
    void deleteByGroupId(@Param("groupId") Long groupId);

    /**
     * Delete all groups for a user - FIXED VERSION
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM GroupUser WHERE user_id = :userId", nativeQuery = true)
    void deleteByUserId(@Param("userId") Long userId);

    /**
     * Insert user-group association - FIXED VERSION
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO GroupUser (user_id, groupId, created_by, created_at) " +
            "VALUES (:userId, :groupId, :createdBy, GETDATE())", nativeQuery = true)
    void insertUserGroup(@Param("userId") Long userId,
                         @Param("groupId") Long groupId,
                         @Param("createdBy") Long createdBy);

    /**
     * Delete specific user from a group
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM GroupUser gu WHERE gu.user.id = :userId AND gu.group.id = :groupId")
    void deleteByUserIdAndGroupId(@Param("userId") Long userId, @Param("groupId") Long groupId);

    /**
     * Count users in a group
     */
    @Query("SELECT COUNT(gu) FROM GroupUser gu WHERE gu.group.id = :groupId")
    long countByGroupId(@Param("groupId") Long groupId);

    /**
     * Get user IDs in a group
     */
    @Query("SELECT gu.user.id FROM GroupUser gu WHERE gu.group.id = :groupId")
    List<Long> findUserIdsByGroupId(@Param("groupId") Long groupId);
}