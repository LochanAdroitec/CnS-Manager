package codesAndStandards.springboot.userApp.service.Impl;

import codesAndStandards.springboot.userApp.dto.ClassificationDto;
import codesAndStandards.springboot.userApp.entity.Classification;
import codesAndStandards.springboot.userApp.entity.Document;
import codesAndStandards.springboot.userApp.entity.User;
import codesAndStandards.springboot.userApp.exception.ResourceNotFoundException;
import codesAndStandards.springboot.userApp.repository.ClassificationRepository;
import codesAndStandards.springboot.userApp.repository.DocumentRepository;
import codesAndStandards.springboot.userApp.repository.UserRepository;
import codesAndStandards.springboot.userApp.service.ClassificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClassificationServiceImpl implements ClassificationService {

    private final ClassificationRepository classificationRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;

    @Override
    @Transactional
    public ClassificationDto createClassification(ClassificationDto classificationDto, Long userId) {
        // Check if classification already exists
        if (classificationRepository.existsByClassificationName(classificationDto.getClassificationName())) {
            throw new IllegalArgumentException("Classification with name '" + classificationDto.getClassificationName() + "' already exists");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        Classification classification = new Classification();
        classification.setClassificationName(classificationDto.getClassificationName());
        classification.setCreatedBy(user);

        Classification savedClassification = classificationRepository.save(classification);
        return mapToDto(savedClassification);
    }

    @Override
    @Transactional
    public ClassificationDto updateClassification(Long classificationId, ClassificationDto classificationDto, Long userId) {
        Classification classification = classificationRepository.findByIdWithUsers(classificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Classification not found with id: " + classificationId));

        // Check if new name already exists (excluding current classification)
        if (!classification.getClassificationName().equals(classificationDto.getClassificationName()) &&
                classificationRepository.existsByClassificationName(classificationDto.getClassificationName())) {
            throw new IllegalArgumentException("Classification with name '" + classificationDto.getClassificationName() + "' already exists");
        }

        // Get the user who is updating
        User updatingUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        classification.setClassificationName(classificationDto.getClassificationName());
        classification.setUpdatedBy(updatingUser);

        Classification updatedClassification = classificationRepository.save(classification);
        return mapToDto(updatedClassification);
    }

    @Override
    public List<Map<String, Object>> getDocumentsByClassificationId(Long classificationId) {
        // Verify classification exists
        if (!classificationRepository.existsById(classificationId)) {
            throw new RuntimeException("Classification not found with id: " + classificationId);
        }

        // Use repository query instead of lazy loading
        List<Document> documents = documentRepository.findByClassificationId(classificationId);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Document doc : documents) {
            Map<String, Object> docMap = new HashMap<>();
            docMap.put("id", doc.getId());
            docMap.put("title", doc.getTitle());
            result.add(docMap);
        }

        return result;
    }

    @Override
    @Transactional
    public void deleteClassification(Long classificationId, Long userId) {
        Classification classification = classificationRepository.findByIdWithDocuments(classificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Classification not found with id: " + classificationId));

        // Check if classification is being used by any documents
//        if (!classification.getDocuments().isEmpty()) {
//            throw new IllegalStateException("Cannot delete classification. It is being used by " +
//                    classification.getDocuments().size() + " document(s)");
//        }

        classification.getDocuments().forEach(document -> document.getClassifications().remove(classification));
        classification.getDocuments().clear();

        classificationRepository.delete(classification);
    }

    @Override
    @Transactional(readOnly = true)
    public ClassificationDto getClassificationById(Long classificationId) {
        Classification classification = classificationRepository.findByIdWithUsers(classificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Classification not found with id: " + classificationId));
        return mapToDto(classification);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClassificationDto> getAllClassifications() {
        return classificationRepository.findAllWithCreatorAndUpdater().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClassificationDto> getClassificationsByUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        return classificationRepository.findByCreatedBy(user).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClassificationDto> getClassificationsEditedByUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        return classificationRepository.findByUpdatedBy(user).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private ClassificationDto mapToDto(Classification classification) {
        ClassificationDto dto = new ClassificationDto();
        dto.setId(classification.getId());
        dto.setClassificationName(classification.getClassificationName());

        // Handle NULL created_by safely
        if (classification.getCreatedBy() != null) {
            dto.setCreatedBy(classification.getCreatedBy().getId());
            dto.setCreatedByUsername(classification.getCreatedBy().getUsername());
            dto.setCreatedAt(classification.getCreatedAt());
        } else {
            dto.setCreatedBy(null);
            dto.setCreatedByUsername("Unknown");
            dto.setCreatedAt(classification.getCreatedAt());
        }

        // Handle NULL updated_by safely
        if (classification.getUpdatedBy() != null) {
            dto.setUpdatedBy(classification.getUpdatedBy().getId());
            dto.setUpdatedByUsername(classification.getUpdatedBy().getUsername());
        }
        dto.setUpdatedAt(classification.getUpdatedAt());

        dto.setDocumentCount(classification.getDocuments() != null ? classification.getDocuments().size() : 0);
        return dto;
    }
}