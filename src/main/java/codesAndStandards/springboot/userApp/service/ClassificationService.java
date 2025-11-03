package codesAndStandards.springboot.userApp.service;

import codesAndStandards.springboot.userApp.dto.ClassificationDto;

import java.util.List;

public interface ClassificationService {
    List<ClassificationDto> getAllClassifications();
    ClassificationDto getClassificationById(Long id);
    ClassificationDto createClassification(String classificationName);
    ClassificationDto findOrCreateClassification(String classificationName);
    void deleteClassification(Long id);
}