package codesAndStandards.springboot.userApp.repository;

import codesAndStandards.springboot.userApp.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByUploadedBy_Username(String username);

    @Query("SELECT d FROM Document d JOIN d.tags t WHERE t.id = :tagId")
    List<Document> findByTagId(@Param("tagId") Long tagId);

    @Query("SELECT d FROM Document d JOIN d.classifications c WHERE c.id = :classificationId")
    List<Document> findByClassificationId(@Param("classificationId") Long classificationId);
}