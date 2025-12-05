package codesAndStandards.springboot.userApp.controller;

import codesAndStandards.springboot.userApp.dto.*;
import codesAndStandards.springboot.userApp.service.GroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.propertyeditors.CustomCollectionEditor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/access-groups")
@RequiredArgsConstructor
@Slf4j
public class AccessGroupApiController {

    private final GroupService groupService;

    /**
     * Get all groups
     * GET /api/access-groups
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('Admin', 'Manager', 'User')")
    public ResponseEntity<List<GroupListDTO>> getAllGroups() {
        log.info("REST request to get all groups");
        try {
            List<GroupListDTO> groups = groupService.getAllGroups();
            return ResponseEntity.ok(groups);
        } catch (Exception e) {
            log.error("Error fetching groups", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get group by ID
     * GET /api/access-groups/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('Admin', 'Manager', 'User')")
    public ResponseEntity<?> getGroupById(@PathVariable Long id) {
        log.info("REST request to get group : {}", id);
        try {
            GroupResponseDTO group = groupService.getGroupById(id);
            return ResponseEntity.ok(group);
        } catch (RuntimeException e) {
            log.error("Error fetching group with ID: {}", id, e);
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        } catch (Exception e) {
            log.error("Unexpected error fetching group", e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "Failed to fetch group");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Create new group
     * POST /api/access-groups
     * Only Admin can create groups
     */
    @PostMapping
    @PreAuthorize("hasAuthority('Admin')")
    public ResponseEntity<?> createGroup(@RequestBody GroupRequestDTO requestDTO) {
        log.info("REST request to create group : {}", requestDTO.getGroupName());
        try {
            GroupResponseDTO createdGroup = groupService.createGroup(requestDTO);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdGroup);
        } catch (IllegalArgumentException e) {
            log.warn("Validation error creating group: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            log.error("Error creating group", e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "Failed to create group: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Update group
     * PUT /api/access-groups/{id}
     * Only Admin can update groups
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('Admin')")
    public ResponseEntity<?> updateGroup(
            @PathVariable Long id,
            @RequestBody GroupRequestDTO requestDTO) {
        log.info("REST request to update group : {}", id);
        try {
            GroupResponseDTO updatedGroup = groupService.updateGroup(id, requestDTO);
            return ResponseEntity.ok(updatedGroup);
        } catch (RuntimeException e) {
            log.error("Error updating group with ID: {}", id, e);
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        } catch (Exception e) {
            log.error("Unexpected error updating group", e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "Failed to update group");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Delete group
     * DELETE /api/access-groups/{id}
     * Only Admin can delete groups
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('Admin')")
    public ResponseEntity<?> deleteGroup(@PathVariable Long id) {
        log.info("REST request to delete group : {}", id);
        try {
            groupService.deleteGroup(id);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Group deleted successfully");
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Error deleting group with ID: {}", id, e);
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        } catch (Exception e) {
            log.error("Unexpected error deleting group", e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "Failed to delete group");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Get groups by document ID
     * GET /api/access-groups/by-document/{documentId}
     */
    @GetMapping("/by-document/{documentId}")
    @PreAuthorize("hasAnyAuthority('Admin', 'Manager')")
    public ResponseEntity<List<GroupListDTO>> getGroupsByDocument(@PathVariable Long documentId) {
        log.info("REST request to get groups for document : {}", documentId);
        try {
            List<GroupListDTO> groups = groupService.getGroupsByDocumentId(documentId);
            return ResponseEntity.ok(groups);
        } catch (Exception e) {
            log.error("Error fetching groups for document", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get groups by user ID
     * GET /api/access-groups/by-user/{userId}
     */
    @GetMapping("/by-user/{userId}")
    @PreAuthorize("hasAnyAuthority('Admin', 'Manager')")
    public ResponseEntity<List<GroupListDTO>> getGroupsByUser(@PathVariable Long userId) {
        log.info("REST request to get groups for user : {}", userId);
        try {
            List<GroupListDTO> groups = groupService.getGroupsByUserId(userId);
            return ResponseEntity.ok(groups);
        } catch (Exception e) {
            log.error("Error fetching groups for user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Check if user has access to document
     * GET /api/access-groups/check-access?userId={userId}&documentId={documentId}
     */
    @GetMapping("/check-access")
    @PreAuthorize("hasAnyAuthority('Admin', 'Manager', 'User')")
    public ResponseEntity<AccessCheckDTO> checkAccess(
            @RequestParam Long userId,
            @RequestParam Long documentId) {
        log.info("REST request to check access for user {} to document {}", userId, documentId);
        try {
            AccessCheckDTO result = groupService.checkUserAccessToDocument(userId, documentId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error checking access", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get accessible document IDs for a user
     * GET /api/access-groups/accessible-documents/{userId}
     */
    @GetMapping("/accessible-documents/{userId}")
    @PreAuthorize("hasAnyAuthority('Admin', 'Manager', 'User')")
    public ResponseEntity<List<Long>> getAccessibleDocuments(@PathVariable Long userId) {
        log.info("REST request to get accessible documents for user : {}", userId);
        try {
            List<Long> documentIds = groupService.getAccessibleDocumentIds(userId);
            return ResponseEntity.ok(documentIds);
        } catch (Exception e) {
            log.error("Error fetching accessible documents", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Add document to group
     * POST /api/access-groups/{groupId}/documents/{documentId}
     */
    @PostMapping("/{groupId}/documents/{documentId}")
    @PreAuthorize("hasAuthority('Admin')")
    public ResponseEntity<?> addDocumentToGroup(
            @PathVariable Long groupId,
            @PathVariable Long documentId) {
        log.info("REST request to add document {} to group {}", documentId, groupId);
        try {
            groupService.addDocumentToGroup(groupId, documentId);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Document added to group successfully");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            log.error("Error adding document to group", e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "Failed to add document to group");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Remove document from group
     * DELETE /api/access-groups/{groupId}/documents/{documentId}
     */
    @DeleteMapping("/{groupId}/documents/{documentId}")
    @PreAuthorize("hasAuthority('Admin')")
    public ResponseEntity<?> removeDocumentFromGroup(
            @PathVariable Long groupId,
            @PathVariable Long documentId) {
        log.info("REST request to remove document {} from group {}", documentId, groupId);
        try {
            groupService.removeDocumentFromGroup(groupId, documentId);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Document removed from group successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error removing document from group", e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "Failed to remove document from group");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Add user to group
     * POST /api/access-groups/{groupId}/users/{userId}
     */
    @PostMapping("/{groupId}/users/{userId}")
    @PreAuthorize("hasAuthority('Admin')")
    public ResponseEntity<?> addUserToGroup(
            @PathVariable Long groupId,
            @PathVariable Long userId) {
        log.info("REST request to add user {} to group {}", userId, groupId);
        try {
            groupService.addUserToGroup(groupId, userId);
            Map<String, String> response = new HashMap<>();
            response.put("message", "User added to group successfully");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            log.error("Error adding user to group", e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "Failed to add user to group");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Remove user from group
     * DELETE /api/access-groups/{groupId}/users/{userId}
     */
    @DeleteMapping("/{groupId}/users/{userId}")
    @PreAuthorize("hasAuthority('Admin')")
    public ResponseEntity<?> removeUserFromGroup(
            @PathVariable Long groupId,
            @PathVariable Long userId) {
        log.info("REST request to remove user {} from group {}", userId, groupId);
        try {
            groupService.removeUserFromGroup(groupId, userId);
            Map<String, String> response = new HashMap<>();
            response.put("message", "User removed from group successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error removing user from group", e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "Failed to remove user from group");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(List.class, "groupIds",
                new CustomCollectionEditor(List.class) {
                    @Override
                    protected Object convertElement(Object element) {
                        if (element == null) return null;
                        return Long.parseLong(element.toString());
                    }
                });
    }

}