package codesAndStandards.springboot.userApp.service.Impl;

import codesAndStandards.springboot.userApp.dto.GroupListDTO;
import codesAndStandards.springboot.userApp.dto.UserDto;
import codesAndStandards.springboot.userApp.entity.Group;
import codesAndStandards.springboot.userApp.entity.GroupUser;
import codesAndStandards.springboot.userApp.entity.Role;
import codesAndStandards.springboot.userApp.entity.User;
import codesAndStandards.springboot.userApp.repository.GroupRepository;
import codesAndStandards.springboot.userApp.repository.GroupUserRepository;
import codesAndStandards.springboot.userApp.repository.RoleRepository;
import codesAndStandards.springboot.userApp.repository.UserRepository;
import codesAndStandards.springboot.userApp.service.UserService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.StoredProcedureQuery;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final GroupUserRepository groupUserRepository;
    private final GroupRepository groupRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public UserServiceImpl(UserRepository userRepository,
                           RoleRepository roleRepository,
                           PasswordEncoder passwordEncoder,
                           GroupUserRepository groupUserRepository,
                           GroupRepository groupRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.groupUserRepository = groupUserRepository;
        this.groupRepository = groupRepository;
    }

    // =====================================================
    // NEW STORED PROCEDURE METHODS FOR SQL SERVER
    // =====================================================

    @Override
    @Transactional
    public void saveUserWithStoredProcedure(UserDto userDto) throws RuntimeException {

        logger.info("Starting saveUserWithStoredProcedure for user: {}", userDto.getUsername());

        try {
            // =============================
            // 1. Prepare variables
            // =============================
            String firstName = userDto.getFirstName();
            String lastName = userDto.getLastName();
            String username = userDto.getUsername();
            String email = userDto.getEmail();
            String encodedPassword = passwordEncoder.encode(userDto.getPassword());
            Long roleId = userDto.getRoleId();

            // created_by
            Long createdById = null;
            if (userDto.getCreatedByUsername() != null && !userDto.getCreatedByUsername().isEmpty()) {
                User createdByUser = userRepository.findByUsername(userDto.getCreatedByUsername());
                if (createdByUser != null) {
                    createdById = createdByUser.getId();
                }
            }

            logger.info("Calling AddUser stored procedure...");

            // =============================
            // 2. Call stored procedure
            // =============================
            StoredProcedureQuery sp = entityManager.createStoredProcedureQuery("AddUser");

            sp.registerStoredProcedureParameter(1, String.class, ParameterMode.IN);
            sp.registerStoredProcedureParameter(2, String.class, ParameterMode.IN);
            sp.registerStoredProcedureParameter(3, String.class, ParameterMode.IN);
            sp.registerStoredProcedureParameter(4, String.class, ParameterMode.IN);
            sp.registerStoredProcedureParameter(5, String.class, ParameterMode.IN);
            sp.registerStoredProcedureParameter(6, Long.class, ParameterMode.IN);
            sp.registerStoredProcedureParameter(7, Long.class, ParameterMode.IN);

            sp.setParameter(1, firstName);
            sp.setParameter(2, lastName);
            sp.setParameter(3, username);
            sp.setParameter(4, email);
            sp.setParameter(5, encodedPassword);
            sp.setParameter(6, roleId);
            sp.setParameter(7, createdById);

            sp.execute();

            logger.info("Stored procedure executed successfully.");

            // =============================
            // 3. Get newly created user ID
            // =============================
            User createdUser = userRepository.findByUsername(username);
            if (createdUser == null) {
                throw new RuntimeException("User created but cannot fetch userId.");
            }
            Long userId = createdUser.getId();

            logger.info("New user created with userId: {}", userId);

            // =============================
            // 4. Save Group Mappings
            // =============================

            // Remove old groups (in case of update)
            groupUserRepository.deleteByUserId(userId);
            logger.info("Old group mappings removed for userId: {}", userId);

            // Insert new groups
            if (userDto.getGroupIds() != null && !userDto.getGroupIds().isEmpty()) {
                for (Long groupId : userDto.getGroupIds()) {
                    groupUserRepository.insertUserGroup(userId, groupId, createdById);
                    logger.info("Added groupId {} for userId {}", groupId, userId);
                }
            } else {
                logger.info("No groupIds provided for this user.");
            }

            logger.info("User and group mapping saved successfully.");

        } catch (Exception e) {
            logger.error("Error in saveUserWithStoredProcedure: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create/update user");
        }
    }


    @Override
    @Transactional
    public void editUserByAdminWithStoredProcedure(String username, UserDto userDto) throws RuntimeException {
        logger.info("Starting editUserByAdminWithStoredProcedure for user: {}", username);

        try {
            StoredProcedureQuery storedProcedure = entityManager.createStoredProcedureQuery("EditUserByAdmin");

            // Register parameters
            storedProcedure.registerStoredProcedureParameter(1, String.class, ParameterMode.IN);
            storedProcedure.registerStoredProcedureParameter(2, String.class, ParameterMode.IN);
            storedProcedure.registerStoredProcedureParameter(3, String.class, ParameterMode.IN);
            storedProcedure.registerStoredProcedureParameter(4, String.class, ParameterMode.IN);
            storedProcedure.registerStoredProcedureParameter(5, String.class, ParameterMode.IN);
            storedProcedure.registerStoredProcedureParameter(6, Long.class, ParameterMode.IN);

            // Set parameters - NULL if empty/not provided
            storedProcedure.setParameter(1, username);
            storedProcedure.setParameter(2,
                    (userDto.getFirstName() != null && !userDto.getFirstName().isEmpty()) ? userDto.getFirstName() : null);
            storedProcedure.setParameter(3,
                    (userDto.getLastName() != null && !userDto.getLastName().isEmpty()) ? userDto.getLastName() : null);
            storedProcedure.setParameter(4,
                    (userDto.getEmail() != null && !userDto.getEmail().isEmpty()) ? userDto.getEmail() : null);
            storedProcedure.setParameter(5,
                    (userDto.getPassword() != null && !userDto.getPassword().isEmpty())
                            ? passwordEncoder.encode(userDto.getPassword()) : null);
            storedProcedure.setParameter(6, userDto.getRoleId());

            storedProcedure.execute();
            logger.info("User updated by admin successfully: {}", username);

        } catch (Exception e) {
            logger.error("Error in editUserByAdmin: {}", username, e);
            handleStoredProcedureException(e, "Failed to update user");
        }
    }

    @Override
    @Transactional
    public void editUserProfileWithStoredProcedure(String username, UserDto userDto) throws RuntimeException {
        logger.info("Starting editUserProfileWithStoredProcedure for user: {}", username);

        try {
            StoredProcedureQuery storedProcedure = entityManager.createStoredProcedureQuery("EditUserProfile");

            // Register parameters
            storedProcedure.registerStoredProcedureParameter(1, String.class, ParameterMode.IN);
            storedProcedure.registerStoredProcedureParameter(2, String.class, ParameterMode.IN);
            storedProcedure.registerStoredProcedureParameter(3, String.class, ParameterMode.IN);
            storedProcedure.registerStoredProcedureParameter(4, String.class, ParameterMode.IN);

            // Set parameters - NULL if empty/not provided
            storedProcedure.setParameter(1, username);
            storedProcedure.setParameter(2,
                    (userDto.getFirstName() != null && !userDto.getFirstName().isEmpty()) ? userDto.getFirstName() : null);
            storedProcedure.setParameter(3,
                    (userDto.getLastName() != null && !userDto.getLastName().isEmpty()) ? userDto.getLastName() : null);
            storedProcedure.setParameter(4,
                    (userDto.getPassword() != null && !userDto.getPassword().isEmpty())
                            ? passwordEncoder.encode(userDto.getPassword()) : null);

            storedProcedure.execute();
            logger.info("User profile updated successfully: {}", username);

        } catch (Exception e) {
            logger.error("Error in editUserProfile: {}", username, e);
            handleStoredProcedureException(e, "Failed to update profile");
        }
    }

    @Override
    @Transactional
    public void deleteUserWithStoredProcedure(String username) throws RuntimeException {
        logger.info("Starting deleteUserWithStoredProcedure for user: {}", username);

        try {
            StoredProcedureQuery storedProcedure = entityManager.createStoredProcedureQuery("DeleteUser");

            // Register input parameter
            storedProcedure.registerStoredProcedureParameter(1, String.class, ParameterMode.IN);

            // Set parameter value
            storedProcedure.setParameter(1, username);

            storedProcedure.execute();
            logger.info("User deleted successfully: {}", username);

        } catch (Exception e) {
            logger.error("Error deleting user: {}", username, e);
            handleStoredProcedureException(e, "Failed to delete user");
        }
    }

    // Helper method to handle stored procedure exceptions
    private void handleStoredProcedureException(Exception e, String defaultMessage) {
        String errorMessage = e.getMessage();
        Throwable rootCause = e;

        // Find the root cause
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }

        String rootMessage = rootCause.getMessage();
        logger.error("Root cause message: {}", rootMessage);

        if ((errorMessage != null && errorMessage.contains("User does not exist")) ||
                (rootMessage != null && rootMessage.contains("User does not exist"))) {
            throw new RuntimeException("User does not exist.");
        } else if ((errorMessage != null && errorMessage.contains("Username already exists")) ||
                (rootMessage != null && rootMessage.contains("Username already exists"))) {
            throw new RuntimeException("Username already exists.");
        } else if ((errorMessage != null && errorMessage.contains("Email already exists")) ||
                (rootMessage != null && rootMessage.contains("Email already exists"))) {
            throw new RuntimeException("Email already exists.");
        } else {
            throw new RuntimeException(defaultMessage + ": " +
                    (rootMessage != null ? rootMessage :
                            errorMessage != null ? errorMessage : "Unknown error"));
        }
    }

    // =====================================================
    // FALLBACK METHOD USING REGULAR JPA
    // =====================================================

    public void saveUserFallback(UserDto userDto) throws RuntimeException {
        logger.info("Using fallback JPA method for user: {}", userDto.getUsername());

        // Check if username already exists
        User existingUser = userRepository.findByUsername(userDto.getUsername());
        if (existingUser != null) {
            throw new RuntimeException("Username already exists.");
        }

        // Check if email already exists
        if (userRepository.existsByEmail(userDto.getEmail())) {
            throw new RuntimeException("Email already exists.");
        }

        // Create new user
        User user = new User();
        user.setFirstName(userDto.getFirstName());
        user.setLastName(userDto.getLastName());
        user.setUsername(userDto.getUsername());
        user.setEmail(userDto.getEmail());
        user.setPassword(passwordEncoder.encode(userDto.getPassword()));

        // Set role
        Role role = roleRepository.findById(userDto.getRoleId())
                .orElseThrow(() -> new RuntimeException("Role not found"));
        user.setRole(role);

        // Set created timestamp
        user.setCreatedAt(LocalDateTime.now());

        // Set createdBy if provided
        if (userDto.getCreatedByUsername() != null && !userDto.getCreatedByUsername().isEmpty()) {
            User createdByUser = userRepository.findByUsername(userDto.getCreatedByUsername());
            if (createdByUser != null) {
                user.setCreatedBy(createdByUser);
            }
        }

        userRepository.save(user);
        logger.info("User saved successfully using JPA fallback: {}", userDto.getUsername());
    }

    // =====================================================
    // ORIGINAL JPA METHODS (for backward compatibility)
    // =====================================================

    @Override
    public void saveUser(UserDto userDto) {
        User user = new User();
        user.setFirstName(userDto.getFirstName());
        user.setLastName(userDto.getLastName());
        user.setUsername(userDto.getUsername());
        user.setEmail(userDto.getEmail());
        user.setPassword(passwordEncoder.encode(userDto.getPassword()));

        // Set role
        Role role = roleRepository.findById(userDto.getRoleId())
                .orElseThrow(() -> new RuntimeException("Role not found"));
        user.setRole(role);

        // Set created timestamp
        user.setCreatedAt(LocalDateTime.now());

        // Set createdBy if provided
        if (userDto.getCreatedByUsername() != null && !userDto.getCreatedByUsername().isEmpty()) {
            User createdByUser = userRepository.findByUsername(userDto.getCreatedByUsername());
            if (createdByUser != null) {
                user.setCreatedBy(createdByUser);
            }
        }

        userRepository.save(user);
    }

    @Override
    public void deleteUserById(Long userId) {
        userRepository.deleteById(userId);
    }

    @Override
    public boolean doesUserExist(Long userId) {
        return userRepository.existsById(userId);
    }

    @Override
    public User findUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Transactional
    @Override
    public UserDto findUserById(Long userId) {
        User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            return null;
        }

        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());

        // Role
        if (user.getRole() != null) {
            dto.setRoleId(user.getRole().getId());
            dto.setRoleName(user.getRole().getRoleName());
        }

        dto.setCreatedAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);

        // Created By
        if (user.getCreatedBy() != null) {
            dto.setCreatedByUsername(user.getCreatedBy().getUsername());
        } else {
            dto.setCreatedByUsername("System");
        }

        // ✅ Correct: Load groups through groupUsers → group
        if (user.getGroupUsers() != null && !user.getGroupUsers().isEmpty()) {

            List<GroupListDTO> groupDtos = user.getGroupUsers().stream()
                    .map(GroupUser::getGroup)
                    .filter(g -> g != null)
                    .map(g -> GroupListDTO.builder()
                            .id(g.getId())
                            .groupName(g.getGroupName())
                            .build()
                    )
                    .toList();

            dto.setGroups(groupDtos);
        }


        return dto;
    }



    @Override
    public void editUser(UserDto updatedUserDto, Long userId) {
        User existingUser = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        existingUser.setFirstName(updatedUserDto.getFirstName());
        existingUser.setLastName(updatedUserDto.getLastName());
        existingUser.setUsername(updatedUserDto.getUsername());
        existingUser.setEmail(updatedUserDto.getEmail());

        // Only update password if it's provided and not empty
        if (updatedUserDto.getPassword() != null &&
                !updatedUserDto.getPassword().isEmpty() &&
                !updatedUserDto.getPassword().equals(existingUser.getPassword())) {
            existingUser.setPassword(passwordEncoder.encode(updatedUserDto.getPassword()));
        }

        // Update role
        Role role = roleRepository.findById(updatedUserDto.getRoleId())
                .orElseThrow(() -> new RuntimeException("Role not found"));
        existingUser.setRole(role);

        userRepository.save(existingUser);
    }

    @Override
    public List<UserDto> findAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToUserDto)
                .collect(Collectors.toList());
    }

    private UserDto mapToUserDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        // Don't set actual password in DTO for security
        dto.setPassword(""); // Empty for display purposes
        dto.setRoleId(user.getRole().getId());
        dto.setRoleName(user.getRole().getRoleName());

        // Set createdBy username if available
        if (user.getCreatedBy() != null) {
            dto.setCreatedByUsername(user.getCreatedBy().getUsername());
        }

        // Format createdAt to string
        if (user.getCreatedAt() != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            dto.setCreatedAt(user.getCreatedAt().format(formatter));
        }

        return dto;
    }

    @Override
    public boolean existsByUsername(String username) {
        System.out.println("Checking username: " + username);
        Optional<User> user = userRepository.findOptionalByUsername(username);
        boolean exists = user.isPresent();
        System.out.println("Username '" + username + "' exists: " + exists);
        return exists;
    }

    @Override
    public boolean existsByEmail(String email) {
        System.out.println("Checking email: " + email);
        Optional<User> user = userRepository.findByEmail(email);
        boolean exists = user.isPresent();
        System.out.println("Email '" + email + "' exists: " + exists);
        return exists;
    }

    @Override
//    @Transactional(readOnly = true)
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    @Override
    public Long getLoggedInUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<User> user = findByUsername(username);
        return user.map(User::getId).orElse(null);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findOptionalByUsername(username);
    }

    /**
     * Save user-group associations after creating a user
     */
    @Transactional
    public void saveUserGroupAssociations(Long userId, List<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            logger.info("No groups to assign for user ID: {}", userId);
            return;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

        // Get current logged-in user for audit
        User currentUser = getCurrentUser();
        Long createdById = currentUser != null ? currentUser.getId() : null;

        logger.info("Assigning {} groups to user ID: {}", groupIds.size(), userId);

        for (Long groupId : groupIds) {
            try {
                // Verify group exists
                Group group = groupRepository.findById(groupId)
                        .orElseThrow(() -> new RuntimeException("Group not found with ID: " + groupId));

                // Check if association already exists
                if (!groupUserRepository.existsByUserIdAndGroupId(userId, groupId)) {
                    // Use native query to insert
                    groupUserRepository.insertUserGroup(userId, groupId, createdById);
                    logger.info("✅ Assigned user {} to group {} ({})", userId, groupId, group.getGroupName());
                } else {
                    logger.info("ℹ️ User {} already in group {} ({})", userId, groupId, group.getGroupName());
                }
            } catch (Exception e) {
                logger.error("❌ Failed to assign user {} to group {}: {}", userId, groupId, e.getMessage());
                // Continue with other groups even if one fails
            }
        }

        logger.info("✅ Group assignment completed for user ID: {}", userId);
    }

    /**
     * Update user-group associations when editing a user
     */
    @Transactional
    public void updateUserGroupAssociations(Long userId, List<Long> groupIds) {
        logger.info("🔄 Updating group associations for user ID: {}", userId);

        try {
            // Delete existing associations
            groupUserRepository.deleteByUserId(userId);
            logger.info("🗑️ Deleted existing group associations for user ID: {}", userId);

            // Add new associations
            if (groupIds != null && !groupIds.isEmpty()) {
                saveUserGroupAssociations(userId, groupIds);
            } else {
                logger.info("ℹ️ No groups selected for user ID: {}", userId);
            }
        } catch (Exception e) {
            logger.error("❌ Error updating group associations for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to update group associations: " + e.getMessage());
        }
    }

    /**
     * Parse comma-separated group IDs from form input
     */
    private List<Long> parseGroupIds(String groupIds) {
        if (groupIds == null || groupIds.trim().isEmpty()) {
            return Collections.emptyList();
        }

        return Arrays.stream(groupIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toList());
    }

    /**
     * Get groups for a specific user
     */
    @Transactional
    public List<GroupListDTO> getUserGroups(Long userId) {
        logger.info("Fetching groups for user ID: {}", userId);

        List<GroupUser> groupUsers = groupUserRepository.findByUserId(userId);

        return groupUsers.stream()
                .map(gu -> {
                    Group group = gu.getGroup();
                    return GroupListDTO.builder()
                            .id(group.getId())
                            .groupName(group.getGroupName())
                            .description(group.getDescription())
                            .build();
                })
                .collect(Collectors.toList());
    }


    /**
     * Parse comma-separated group IDs
     */
//    private List<Long> parseGroupIds(String groupIds) {
//        if (groupIds == null || groupIds.trim().isEmpty()) {
//            return Collections.emptyList();
//        }
//
//        return Arrays.stream(groupIds.split(","))
//                .map(String::trim)
//                .filter(s -> !s.isEmpty())
//                .map(Long::parseLong)
//                .collect(Collectors.toList());
//    }

    /**
     * Get current logged-in user
     */
    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new RuntimeException("Current user not found");
        }
        return user;
    }
}