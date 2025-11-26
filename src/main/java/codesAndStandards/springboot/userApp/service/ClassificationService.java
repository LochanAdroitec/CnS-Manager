package codesAndStandards.springboot.userApp.service;

import codesAndStandards.springboot.userApp.dto.ClassificationDto;

import java.util.List;
import java.util.Map;

public interface ClassificationService {

    ClassificationDto createClassification(ClassificationDto classificationDto, Long userId);

    ClassificationDto updateClassification(Long classificationId, ClassificationDto classificationDto, Long userId);

    void deleteClassification(Long classificationId, Long userId);

    ClassificationDto getClassificationById(Long classificationId);

    List<ClassificationDto> getAllClassifications();

    List<ClassificationDto> getClassificationsByUser(Long userId);

    List<ClassificationDto> getClassificationsEditedByUser(Long userId);

    List<Map<String, Object>> getDocumentsByClassificationId(Long classificationId);
}