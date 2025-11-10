package codesAndStandards.springboot.userApp.repository;

import codesAndStandards.springboot.userApp.entity.Classification;
import codesAndStandards.springboot.userApp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
}

