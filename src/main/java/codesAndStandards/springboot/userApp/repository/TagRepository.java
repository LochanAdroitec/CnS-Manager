package codesAndStandards.springboot.userApp.repository;

import codesAndStandards.springboot.userApp.entity.Tag;
import codesAndStandards.springboot.userApp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {

    Optional<Tag> findByTagName(String tagName);

    boolean existsByTagName(String tagName);

    List<Tag> findByCreatedBy(User user);

    List<Tag> findByUpdatedBy(User user);


    @Query("SELECT t FROM Tag t LEFT JOIN FETCH t.createdBy LEFT JOIN FETCH t.updatedBy ORDER BY t.createdAt DESC")
    List<Tag> findAllWithCreatorAndUpdater();

    @Query("SELECT t FROM Tag t LEFT JOIN FETCH t.documents WHERE t.id = :id")
    Optional<Tag> findByIdWithDocuments(Long id);

    @Query("SELECT t FROM Tag t LEFT JOIN FETCH t.createdBy LEFT JOIN FETCH t.updatedBy WHERE t.id = :id")
    Optional<Tag> findByIdWithUsers(Long id);

    @Modifying
    @Query("UPDATE Tag t SET t.createdBy = null WHERE t.createdBy.id = :userId")
    void clearCreatedByUser(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE Tag t SET t.updatedBy = null WHERE t.updatedBy.id = :userId")
    void clearUpdatedByUser(@Param("userId") Long userId);
}