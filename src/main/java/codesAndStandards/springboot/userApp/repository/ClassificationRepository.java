package codesAndStandards.springboot.userApp.repository;

import codesAndStandards.springboot.userApp.entity.Classification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClassificationRepository extends JpaRepository<Classification, Long> {
    Optional<Classification> findByClassificationName(String classificationName);
    boolean existsByClassificationName(String classificationName);
}

