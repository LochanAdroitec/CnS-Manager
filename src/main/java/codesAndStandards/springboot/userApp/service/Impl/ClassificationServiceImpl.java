package codesAndStandards.springboot.userApp.service.Impl;

import codesAndStandards.springboot.userApp.dto.ClassificationDto;
import codesAndStandards.springboot.userApp.entity.Classification;
import codesAndStandards.springboot.userApp.repository.ClassificationRepository;
import codesAndStandards.springboot.userApp.service.ClassificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ClassificationServiceImpl implements ClassificationService {

    @Autowired
    private ClassificationRepository classificationRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ClassificationDto> getAllClassifications() {
        return classificationRepository.findAll()
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ClassificationDto getClassificationById(Long id) {
        Classification classification = classificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Classification not found"));
        return convertToDto(classification);
    }

    @Override
    @Transactional
    public ClassificationDto createClassification(String classificationName) {
        if (classificationRepository.existsByClassificationName(classificationName)) {
            throw new RuntimeException("Classification already exists");
        }

        Classification classification = new Classification();
        classification.setClassificationName(classificationName);
        classification = classificationRepository.save(classification);

        return convertToDto(classification);
    }

    @Override
    @Transactional
    public ClassificationDto findOrCreateClassification(String classificationName) {
        return classificationRepository.findByClassificationName(classificationName)
                .map(this::convertToDto)
                .orElseGet(() -> createClassification(classificationName));
    }

    @Override
    @Transactional
    public void deleteClassification(Long id) {
        classificationRepository.deleteById(id);
    }

    private ClassificationDto convertToDto(Classification classification) {
        ClassificationDto dto = new ClassificationDto();
        dto.setId(classification.getId());
        dto.setClassificationName(classification.getClassificationName());
        return dto;
    }
}