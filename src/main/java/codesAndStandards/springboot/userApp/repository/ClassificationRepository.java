package codesAndStandards.springboot.userApp.repository;

import codesAndStandards.springboot.userApp.entity.Classification;
import codesAndStandards.springboot.userApp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClassificationRepository extends JpaRepository<Classification, Long> {

    Optional<Classification> findByClassificationName(String classificationName);

    boolean existsByClassificationName(String classificationName);

    List<Classification> findByCreatedBy(User user);

    List<Classification> findByUpdatedBy(User user);

    @Query("SELECT c FROM Classification c LEFT JOIN FETCH c.createdBy LEFT JOIN FETCH c.updatedBy ORDER BY c.createdAt DESC")
    List<Classification> findAllWithCreatorAndUpdater();

    @Query("SELECT c FROM Classification c LEFT JOIN FETCH c.documents WHERE c.id = :id")
    Optional<Classification> findByIdWithDocuments(Long id);

    @Query("SELECT c FROM Classification c LEFT JOIN FETCH c.createdBy LEFT JOIN FETCH c.updatedBy WHERE c.id = :id")
    Optional<Classification> findByIdWithUsers(Long id);

    @Modifying
    @Query("UPDATE Classification c SET c.createdBy = null WHERE c.createdBy.id = :userId")
    void clearCreatedByUser(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE Classification c SET c.updatedBy = null WHERE c.updatedBy.id = :userId")
    void clearUpdatedByUser(@Param("userId") Long userId);
}

