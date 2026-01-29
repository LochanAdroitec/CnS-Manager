package codesAndStandards.springboot.userApp.controller;

import codesAndStandards.springboot.userApp.dto.TagDto;
import codesAndStandards.springboot.userApp.entity.User;
import codesAndStandards.springboot.userApp.repository.UserRepository;
import codesAndStandards.springboot.userApp.service.TagService;
import codesAndStandards.springboot.userApp.service.ActivityLogService;
import codesAndStandards.springboot.userApp.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
//@CrossOrigin(origins = "http://localhost:8080", allowCredentials = "true")
public class TagController {

    private final TagService tagService;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;
    private static final Logger logger = LoggerFactory.getLogger(TagController.class);
    private final UserService userService;

    @PostMapping
    public ResponseEntity<TagDto> createTag(@Valid @RequestBody TagDto tagDto) {
        String username = getCurrentUsername();
        try {
            Long userId = getCurrentUserId();
            TagDto createdTag = tagService.createTag(tagDto, userId);
            logger.info("New tag: "+tagDto.getTagName()+" added successfully");

            activityLogService.logByUsername(username, ActivityLogService.TAG_ADD,
                    "Created tag: '" + tagDto.getTagName() + "'");

            return new ResponseEntity<>(createdTag, HttpStatus.CREATED);
        } catch (Exception e) {
            logger.info("Adding tag failed");
            activityLogService.logByUsername(username, ActivityLogService.TAG_ADD_FAIL,
                    "Failed to create tag: '" + tagDto.getTagName() + "' (Reason: " + e.getMessage() + ")");
            throw e;
        }
    }


    @PutMapping("/{id}")
    public ResponseEntity<TagDto> updateTag(@PathVariable Long id, @Valid @RequestBody TagDto tagDto) {
        String username = getCurrentUsername();
        try {
            Long userId = getCurrentUserId();

            // Fetch the old tag name before updating
            String oldTagName = tagService.getTagById(id).getTagName();

            // Update the tag
            TagDto updatedTag = tagService.updateTag(id, tagDto, userId);
            String newTagName = updatedTag.getTagName();

            logger.info("Edited tag: " + oldTagName + " to " + newTagName + " successfully");

            activityLogService.logByUsername(
                    username,
                    ActivityLogService.TAG_EDIT,
                    "Updated tag '" + oldTagName + "' to '" + newTagName + "'"
            );

            return ResponseEntity.ok(updatedTag);
        } catch (Exception e) {
            logger.info("Editing tag failed");

            activityLogService.logByUsername(
                    username,
                    ActivityLogService.TAG_EDIT_FAIL,
                    "Failed to update tag (Reason: " + e.getMessage() + ")"
            );
            throw e;
        }
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteTag(@PathVariable Long id) {
        String username = getCurrentUsername();
        try {
            Long userId = getCurrentUserId();

            // Fetch the tag name before deletion
            TagDto tag = tagService.getTagById(id);
            String tagName = tag.getTagName();

            // Delete the tag
            tagService.deleteTag(id, userId);

            logger.info("Tag '" + tagName + "' deleted successfully");

            activityLogService.logByUsername(
                    username,
                    ActivityLogService.TAG_DELETE,
                    "Deleted tag: '" + tagName + "'"
            );

            // Send response with tag name
            Map<String, String> response = new HashMap<>();
            response.put("message", "Tag '" + tagName + "' deleted successfully");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.info("Deleting tag failed");

            activityLogService.logByUsername(
                    username,
                    ActivityLogService.TAG_DELETE_FAIL,
                    "Failed to delete tag (Reason: " + e.getMessage() + ")"
            );
            throw e;
        }
    }


    // 🔹 Remaining methods (unchanged below) -----------------------------------------------------

    @GetMapping("/{id}")
    public ResponseEntity<TagDto> getTagById(@PathVariable Long id) {
        return ResponseEntity.ok(tagService.getTagById(id));
    }

    @PreAuthorize("hasAnyAuthority('Manager', 'Admin')")
    @GetMapping
    public ResponseEntity<List<TagDto>> getAllTags() {
        return ResponseEntity.ok(tagService.getAllTags());
    }

    @GetMapping("/my-tags")
    public ResponseEntity<List<TagDto>> getMyTags() {
        return ResponseEntity.ok(tagService.getTagsByUser(getCurrentUserId()));
    }

    @GetMapping("/my-edited-tags")
    public ResponseEntity<List<TagDto>> getMyEditedTags() {
        return ResponseEntity.ok(tagService.getTagsEditedByUser(getCurrentUserId()));
    }

    // 🔹 NEW ENDPOINT - Get documents by tag ID -----------------------------------------------------

    /**
     * Get all documents that are using a specific tag
     * Returns list of documents with id and title
     */
    @GetMapping("/{id}/documents")
    public ResponseEntity<List<Map<String, Object>>> getDocumentsByTag(@PathVariable Long id) {
        try {
            List<Map<String, Object>> documents = tagService.getDocumentsByTagId(id);
            return ResponseEntity.ok(documents);
        } catch (Exception e) {
            logger.error("Failed to fetch documents for tag ID: " + id, e);
            throw e;
        }
    }

    // ============================================================================
    // 🆕 NEW ENDPOINT FOR BULK UPLOAD - Get all tag names as simple string list
    // ============================================================================

    /**
     * Get all tags as a simple list of tag names (strings only)
     * Used by bulk upload feature to populate dropdowns
     * Returns: Sorted list of unique tag names
     *
     * Example response: ["safety", "quality", "design", "manufacturing"]
     */
    @GetMapping("/all")
    public ResponseEntity<List<String>> getAllTagNames() {
        try {
            logger.debug("Fetching all tag names for bulk upload");

            // Get all tags and extract just the names
            List<TagDto> allTags = tagService.getAllTags();

            List<String> tagNames = allTags.stream()
                    .map(TagDto::getTagName)
                    .filter(name -> name != null && !name.trim().isEmpty())
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());

            logger.info("Returning {} unique tag names", tagNames.size());

            return ResponseEntity.ok(tagNames);
        } catch (Exception e) {
            logger.error("Failed to fetch tag names for bulk upload", e);
            // Return empty list instead of error to prevent frontend issues
            return ResponseEntity.ok(List.of());
        }
    }

    // 🔹 Utility methods -----------------------------------------------------

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }
        User user = userRepository.findByUsername(authentication.getName());
        if (user == null) {
            throw new RuntimeException("User not found with username: " + authentication.getName());
        }
        return user.getId();
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null) ? auth.getName() : "Unknown";
    }
}