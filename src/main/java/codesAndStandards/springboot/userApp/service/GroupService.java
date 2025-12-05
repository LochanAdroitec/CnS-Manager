package codesAndStandards.springboot.userApp.service;

import codesAndStandards.springboot.userApp.dto.*;
import codesAndStandards.springboot.userApp.entity.*;
import codesAndStandards.springboot.userApp.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupService {

    private final GroupRepository groupRepository;
    private final codesAndStandards.springboot.userApp.repository.GroupUserRepository groupUserRepository;
    private final codesAndStandards.springboot.userApp.repository.AccessControlLogicRepository accessControlLogicRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    /**
     * Get all groups with counts
     */
    @Transactional(readOnly = true)
    public List<GroupListDTO> getAllGroups() {
        log.info("Fetching all groups");
        List<Group> groups = groupRepository.findAllWithCreator();

        return groups.stream()
                .map(this::convertToListDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get group by ID with full details
     */
    @Transactional(readOnly = true)
    public GroupResponseDTO getGroupById(Long id) {
        log.info("Fetching group with ID: {}", id);

        Group group = groupRepository.findByIdWithAssociations(id)
                .orElseThrow(() -> new RuntimeException("Group not found with ID: " + id));

        return convertToResponseDTO(group);
    }

    /**
     * Create new group
     */
    @Transactional
    public GroupResponseDTO createGroup(GroupRequestDTO requestDTO) {
        log.info("Creating new group: {}", requestDTO.getGroupName());

        // Validate group name
        if (requestDTO.getGroupName() == null || requestDTO.getGroupName().trim().isEmpty()) {
            throw new IllegalArgumentException("Group name cannot be empty");
        }

        String groupName = requestDTO.getGroupName().trim();

        // Check if group name already exists
        if (groupRepository.existsByGroupNameIgnoreCase(groupName)) {
            throw new IllegalArgumentException("Group with name '" + groupName + "' already exists");
        }

        // Get current user
        User currentUser = getCurrentUser();

        // Create new group
        Group group = new Group();
        group.setGroupName(groupName);
        group.setDescription(requestDTO.getDescription());
        group.setCreatedBy(currentUser);
        group.setCreatedAt(LocalDateTime.now());

        // Save group first
        Group savedGroup = groupRepository.save(group);
        log.info("Group created with ID: {}", savedGroup.getId());

        // Add documents to group
        if (requestDTO.getDocumentIds() != null && !requestDTO.getDocumentIds().isEmpty()) {
            addDocumentsToGroup(savedGroup, requestDTO.getDocumentIds(), currentUser);
        }

        // Add users to group
        if (requestDTO.getUserIds() != null && !requestDTO.getUserIds().isEmpty()) {
            addUsersToGroup(savedGroup, requestDTO.getUserIds(), currentUser);
        }

        log.info("Group created successfully with ID: {}", savedGroup.getId());
        return getGroupById(savedGroup.getId());
    }
    @Transactional
    public List<Long> getGroupIdsByDocumentId(Long documentId) {
        try {
            log.info("Getting group IDs for document: {}", documentId);
            List<AccessControlLogic> aclList = accessControlLogicRepository.findByDocumentId(documentId);
            List<Long> groupIds = aclList.stream()
                    .map(acl -> acl.getGroup().getId())
                    .collect(Collectors.toList());
            log.info("Found {} groups for document {}", groupIds.size(), documentId);
            return groupIds;
        } catch (Exception e) {
            log.error("Error getting group IDs for document {}: {}", documentId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Update existing group
     */
    @Transactional
    public GroupResponseDTO updateGroup(Long id, GroupRequestDTO requestDTO) {
        log.info("Updating group with ID: {}", id);

        // Find existing group
        Group group = groupRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Group not found with ID: " + id));

        // Get current user
        User currentUser = getCurrentUser();

        // Validate and update group name if changed
        if (requestDTO.getGroupName() != null && !requestDTO.getGroupName().trim().isEmpty()) {
            String newGroupName = requestDTO.getGroupName().trim();

            if (!group.getGroupName().equalsIgnoreCase(newGroupName)) {
                // Check if new name already exists
                if (groupRepository.existsByGroupNameIgnoreCase(newGroupName)) {
                    throw new IllegalArgumentException("Group with name '" + newGroupName + "' already exists");
                }
                group.setGroupName(newGroupName);
            }
        }

        // Update description
        if (requestDTO.getDescription() != null) {
            group.setDescription(requestDTO.getDescription());
        }

        // Save group
        groupRepository.save(group);

        // Update documents
        updateGroupDocuments(group, requestDTO.getDocumentIds(), currentUser);

        // Update users
        updateGroupUsers(group, requestDTO.getUserIds(), currentUser);

        log.info("Group updated successfully");
        return getGroupById(id);
    }

    /**
     * Delete group
     */
    @Transactional
    public void deleteGroup(Long id) {
        log.info("Deleting group with ID: {}", id);

        Group group = groupRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Group not found with ID: " + id));

        // Delete all associated GroupUser records
        groupUserRepository.deleteByGroupId(id);

        // Delete all associated AccessControlLogic records
        accessControlLogicRepository.deleteByGroupId(id);

        // Delete group
        groupRepository.delete(group);
        log.info("Group deleted successfully");
    }

    /**
     * Get groups by document ID
     */
    @Transactional(readOnly = true)
    public List<GroupListDTO> getGroupsByDocumentId(Long documentId) {
        log.info("Fetching groups for document ID: {}", documentId);

        List<Group> groups = groupRepository.findGroupsByDocumentId(documentId);

        return groups.stream()
                .map(this::convertToListDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get groups by user ID
     */
    @Transactional(readOnly = true)
    public List<GroupListDTO> getGroupsByUserId(Long userId) {
        log.info("Fetching groups for user ID: {}", userId);

        List<Group> groups = groupRepository.findGroupsByUserId(userId);

        return groups.stream()
                .map(this::convertToListDTO)
                .collect(Collectors.toList());
    }

    /**
     * Check if user has access to document
     */
    @Transactional(readOnly = true)
    public AccessCheckDTO checkUserAccessToDocument(Long userId, Long documentId) {
        log.debug("Checking access for user {} to document {}", userId, documentId);

        boolean hasAccess = accessControlLogicRepository.hasUserAccessToDocument(userId, documentId);

        // Get group names that provide access
        List<String> groupNames = List.of();
        if (hasAccess) {
            List<Group> groups = groupRepository.findGroupsByUserId(userId);
            groupNames = groups.stream()
                    .filter(g -> g.getGroupDocument().stream()
                            .anyMatch(acl -> acl.getDocument().getId().equals(documentId)))
                    .map(Group::getGroupName)
                    .collect(Collectors.toList());
        }

        String message = hasAccess
                ? "User has access to document through groups: " + String.join(", ", groupNames)
                : "User does not have access to this document";

        return AccessCheckDTO.builder()
                .hasAccess(hasAccess)
                .groupNames(groupNames)
                .message(message)
                .build();
    }

    /**
     * Get all documents accessible by a user
     */
    @Transactional(readOnly = true)
    public List<Long> getAccessibleDocumentIds(Long userId) {
        log.info("Fetching accessible documents for user ID: {}", userId);
        return accessControlLogicRepository.findAccessibleDocumentIdsByUserId(userId);
    }

    /**
     * Add documents to group
     */
    @Transactional
    public void addDocumentToGroup(Long groupId, Long documentId) {
        log.info("Adding document {} to group {}", documentId, groupId);

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        // Check if already exists
        if (accessControlLogicRepository.existsByDocumentIdAndGroupId(documentId, groupId)) {
            throw new IllegalArgumentException("Document already in group");
        }

        User currentUser = getCurrentUser();

        AccessControlLogic acl = AccessControlLogic.builder()
                .document(document)
                .group(group)
                .createdBy(currentUser)
                .createdAt(LocalDateTime.now())
                .build();

        accessControlLogicRepository.save(acl);
        log.info("Document added to group successfully");
    }

    /**
     * Remove document from group
     */
    @Transactional
    public void removeDocumentFromGroup(Long groupId, Long documentId) {
        log.info("Removing document {} from group {}", documentId, groupId);
        accessControlLogicRepository.deleteByDocumentIdAndGroupId(documentId, groupId);
        log.info("Document removed from group successfully");
    }

    /**
     * Add user to group
     */
    @Transactional
    public void addUserToGroup(Long groupId, Long userId) {
        log.info("Adding user {} to group {}", userId, groupId);

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Check if already exists
        if (groupUserRepository.existsByUserIdAndGroupId(userId, groupId)) {
            throw new IllegalArgumentException("User already in group");
        }

        User currentUser = getCurrentUser();

        GroupUser groupUser = GroupUser.builder()
                .user(user)
                .group(group)
                .createdBy(currentUser)
                .createdAt(LocalDateTime.now())
                .build();

        groupUserRepository.save(groupUser);
        log.info("User added to group successfully");
    }

    /**
     * Remove user from group
     */
    @Transactional
    public void removeUserFromGroup(Long groupId, Long userId) {
        log.info("Removing user {} from group {}", userId, groupId);
        groupUserRepository.deleteByUserIdAndGroupId(userId, groupId);
        log.info("User removed from group successfully");
    }

    // Helper methods

    @Transactional
    public void addDocumentsToGroup(Group group, List<Long> documentIds, User currentUser) {

        // Convert Long → Integer
        List<Long> docIdsAsInt = documentIds.stream()
//                .map(Long::intValue)
                .collect(Collectors.toList());

        List<Document> documents = documentRepository.findAllById(docIdsAsInt);

        for (Document document : documents) {
            if (!accessControlLogicRepository.existsByDocumentIdAndGroupId(document.getId(), group.getId())) {
                AccessControlLogic acl = AccessControlLogic.builder()
                        .document(document)
                        .group(group)
                        .createdBy(currentUser)
                        .createdAt(LocalDateTime.now())
                        .build();
                accessControlLogicRepository.save(acl);
            }
        }
        log.info("Added {} documents to group", documents.size());
    }

    private void addUsersToGroup(Group group, List<Long> userIds, User currentUser) {
        List<User> users = userRepository.findAllById(userIds);

        for (User user : users) {
            if (!groupUserRepository.existsByUserIdAndGroupId(user.getId(), group.getId())) {
                GroupUser groupUser = GroupUser.builder()
                        .user(user)
                        .group(group)
                        .createdBy(currentUser)
                        .createdAt(LocalDateTime.now())
                        .build();
                groupUserRepository.save(groupUser);
            }
        }
        log.info("Added {} users to group", users.size());
    }

    private void updateGroupDocuments(Group group, List<Long> documentIds, User currentUser) {
        // Delete existing documents
        accessControlLogicRepository.deleteByGroupId(group.getId());

        // Add new documents
        if (documentIds != null && !documentIds.isEmpty()) {
            addDocumentsToGroup(group, documentIds, currentUser);
        }
    }

    private void updateGroupUsers(Group group, List<Long> userIds, User currentUser) {
        // Delete existing users
        groupUserRepository.deleteByGroupId(group.getId());

        // Add new users
        if (userIds != null && !userIds.isEmpty()) {
            addUsersToGroup(group, userIds, currentUser);
        }
    }

    private GroupListDTO convertToListDTO(Group group) {
        long documentCount = accessControlLogicRepository.countByGroupId(group.getId());
        long userCount = groupUserRepository.countByGroupId(group.getId());

        return GroupListDTO.builder()
                .id(group.getId())
                .groupName(group.getGroupName())
                .description(group.getDescription())
                .createdByUsername(group.getCreatedBy() != null ? group.getCreatedBy().getUsername() : "Unknown")
                .createdAt(group.getCreatedAt())
                .documentCount((int) documentCount)
                .userCount((int) userCount)
                .build();
    }

    private GroupResponseDTO convertToResponseDTO(Group group) {

        List<Long> documentIds = accessControlLogicRepository.findDocumentIdsByGroupId(group.getId());
        List<Long> userIds = groupUserRepository.findUserIdsByGroupId(group.getId());

        // Document details (removed getDocumentCode + getCategory)
        List<DocumentInfoDTO> documents = group.getGroupDocument().stream()
                .map(acl -> DocumentInfoDTO.builder()
                        .id(acl.getDocument().getId())
                        .title(acl.getDocument().getTitle())
                        // Removed fields that do not exist
                        .build())
                .collect(Collectors.toList());

        // User details (fixed role + removed department)
        List<UserInfoDTO> users = group.getGroupUsers().stream()
                .map(gu -> UserInfoDTO.builder()
                        .id(gu.getUser().getId())
                        .username(gu.getUser().getUsername())
                        .email(gu.getUser().getEmail())
                        .role(gu.getUser().getRole() != null ? gu.getUser().getRole().toString() : null)
                        .department(null) // removed getDepartment()
                        .build())
                .collect(Collectors.toList());

        return GroupResponseDTO.builder()
                .id(group.getId())
                .groupName(group.getGroupName())
                .description(group.getDescription())
                .createdById(group.getCreatedBy() != null ? group.getCreatedBy().getId() : null)
                .createdByUsername(group.getCreatedBy() != null ? group.getCreatedBy().getUsername() : "Unknown")
                .createdAt(group.getCreatedAt())
                .documentCount(documentIds.size())
                .userCount(userIds.size())
                .documentIds(documentIds)
                .userIds(userIds)
                .documents(documents)
                .users(users)
                .build();
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            return userRepository.findByUsername(username);
//                    .orElseThrow(() -> new RuntimeException("Current user not found"));
        }
        throw new RuntimeException("User not authenticated");
    }
}