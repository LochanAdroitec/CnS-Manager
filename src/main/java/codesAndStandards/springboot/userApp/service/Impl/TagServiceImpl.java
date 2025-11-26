package codesAndStandards.springboot.userApp.service.Impl;

import codesAndStandards.springboot.userApp.dto.TagDto;
import codesAndStandards.springboot.userApp.entity.Document;
import codesAndStandards.springboot.userApp.entity.Tag;
import codesAndStandards.springboot.userApp.entity.User;
import codesAndStandards.springboot.userApp.exception.ResourceNotFoundException;
import codesAndStandards.springboot.userApp.exception.UnauthorizedException;
import codesAndStandards.springboot.userApp.repository.DocumentRepository;
import codesAndStandards.springboot.userApp.repository.TagRepository;
import codesAndStandards.springboot.userApp.repository.UserRepository;
import codesAndStandards.springboot.userApp.service.TagService;
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
public class TagServiceImpl implements TagService {

    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;

    @Override
    @Transactional
    public TagDto createTag(TagDto tagDto, Long userId) {
        // Check if tag already exists
        if (tagRepository.existsByTagName(tagDto.getTagName())) {
            throw new IllegalArgumentException("Tag with name '" + tagDto.getTagName() + "' already exists");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        Tag tag = new Tag();
        tag.setTagName(tagDto.getTagName());
        tag.setCreatedBy(user);

        Tag savedTag = tagRepository.save(tag);
        return mapToDto(savedTag);
    }

    public void createTagIfNotExists(String tagName, Long userId) {
        if (!tagRepository.existsByTagName(tagName)) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            Tag tag = new Tag();
            tag.setTagName(tagName);
            tag.setCreatedBy(user);  // ✅ Set creator
            // created_at is set automatically by @CreationTimestamp
            tagRepository.save(tag);
        }
    }

    @Override
    public List<Map<String, Object>> getDocumentsByTagId(Long tagId) {
        // Verify tag exists
        if (!tagRepository.existsById(tagId)) {
            throw new RuntimeException("Tag not found with id: " + tagId);
        }

        // Use repository query instead of lazy loading

        List<Document> documents = documentRepository.findByTagId(tagId);

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
    public TagDto updateTag(Long tagId, TagDto tagDto, Long userId) {
        Tag tag = tagRepository.findByIdWithUsers(tagId)
                .orElseThrow(() -> new ResourceNotFoundException("Tag not found with id: " + tagId));

//        // Handle NULL created_by (for old tags)
//        if (tag.getCreatedBy() == null) {
//            throw new IllegalStateException("This tag has no owner and cannot be updated. Please contact administrator.");
//        }
//
//        // Check if user is the creator
//        if (!tag.getCreatedBy().getId().equals(userId)) {
//            throw new UnauthorizedException("You don't have permission to update this tag");
//        }

        // Check if new name already exists (excluding current tag)
        if (!tag.getTagName().equals(tagDto.getTagName()) &&
                tagRepository.existsByTagName(tagDto.getTagName())) {
            throw new IllegalArgumentException("Tag with name '" + tagDto.getTagName() + "' already exists");
        }

        // Get the user who is updating
        User updatingUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        tag.setTagName(tagDto.getTagName());
        tag.setUpdatedBy(updatingUser);

        Tag updatedTag = tagRepository.save(tag);
        return mapToDto(updatedTag);
    }

    @Override
    @Transactional
    public void deleteTag(Long tagId, Long userId) {
        Tag tag = tagRepository.findByIdWithDocuments(tagId)
                .orElseThrow(() -> new ResourceNotFoundException("Tag not found with id: " + tagId));

//        // Handle NULL created_by (for old tags)
//        if (tag.getCreatedBy() == null) {
//            throw new IllegalStateException("This tag has no owner and cannot be deleted. Please contact administrator.");
//        }
//
//        // Check if user is the creator
//        if (!tag.getCreatedBy().getId().equals(userId)) {
//            throw new UnauthorizedException("You don't have permission to delete this tag");
//        }

        // Check if tag is being used by any documents
//        if (!tag.getDocuments().isEmpty()) {
//            throw new IllegalStateException("Cannot delete tag. It is being used by " +
//                    tag.getDocuments().size() + " document(s)");
//        }

        // Clear the relationship from both sides before deleting
        tag.getDocuments().forEach(document -> document.getTags().remove(tag));
        tag.getDocuments().clear();

        tagRepository.delete(tag);
    }

    @Override
    @Transactional(readOnly = true)
    public TagDto getTagById(Long tagId) {
        Tag tag = tagRepository.findByIdWithUsers(tagId)
                .orElseThrow(() -> new ResourceNotFoundException("Tag not found with id: " + tagId));
        return mapToDto(tag);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TagDto> getAllTags() {
        return tagRepository.findAllWithCreatorAndUpdater().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TagDto> getTagsByUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        return tagRepository.findByCreatedBy(user).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TagDto> getTagsEditedByUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        return tagRepository.findByUpdatedBy(user).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private TagDto mapToDto(Tag tag) {
        TagDto dto = new TagDto();
        dto.setId(tag.getId());
        dto.setTagName(tag.getTagName());

        // Handle NULL created_by safely
        if (tag.getCreatedBy() != null) {
            dto.setCreatedBy(tag.getCreatedBy().getId());
            dto.setCreatedByUsername(tag.getCreatedBy().getUsername()); // Use getUsername()
            dto.setCreatedAt(tag.getCreatedAt());
        } else {
            // Default values for tags without creator (legacy data)
            dto.setCreatedBy(null);
            dto.setCreatedByUsername("Unknown");
            dto.setCreatedAt(tag.getCreatedAt());
        }

        // Handle NULL updated_by safely (may be null if never edited)
        if (tag.getUpdatedBy() != null) {
            dto.setUpdatedBy(tag.getUpdatedBy().getId());
            dto.setUpdatedByUsername(tag.getUpdatedBy().getUsername()); // Use getUsername()
        }
        dto.setUpdatedAt(tag.getUpdatedAt());

        dto.setDocumentCount(tag.getDocuments() != null ? tag.getDocuments().size() : 0);
        return dto;
    }

    @Transactional
    public Tag getOrCreateTag(String tagName, Long userId) {
        // Try to find existing tag
        return tagRepository.findByTagName(tagName)
                .orElseGet(() -> {
                    // Create new tag with creator info
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new RuntimeException("User not found"));

                    Tag newTag = new Tag();
                    newTag.setTagName(tagName);
                    newTag.setCreatedBy(user);  // ✅ Set creator
                    // created_at is auto-set by @CreationTimestamp

                    return tagRepository.save(newTag);
                });
    }

    /**
     * Get or create multiple tags at once
     */
    @Transactional
    public List<Tag> getOrCreateTags(List<String> tagNames, Long userId) {
        return tagNames.stream()
                .map(tagName -> getOrCreateTag(tagName, userId))
                .collect(Collectors.toList());
    }
}