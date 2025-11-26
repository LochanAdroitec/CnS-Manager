package codesAndStandards.springboot.userApp.service.Impl;

import codesAndStandards.springboot.userApp.dto.UserDto;
import codesAndStandards.springboot.userApp.entity.Role;
import codesAndStandards.springboot.userApp.entity.User;
import codesAndStandards.springboot.userApp.repository.ClassificationRepository;
import codesAndStandards.springboot.userApp.repository.RoleRepository;
import codesAndStandards.springboot.userApp.repository.TagRepository;
import codesAndStandards.springboot.userApp.repository.UserRepository;
import codesAndStandards.springboot.userApp.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
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

    @PersistenceContext
    private EntityManager entityManager;

    public UserServiceImpl(UserRepository userRepository,
                           RoleRepository roleRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // =====================================================
    // NEW STORED PROCEDURE METHODS FOR SQL SERVER
    // =====================================================

    @Override
    @Transactional
    public void saveUserWithStoredProcedure(UserDto userDto) throws RuntimeException {
        logger.info("Starting saveUserWithStoredProcedure for user: {}", userDto.getUsername());

        try {
            // Prepare parameters for stored procedure
            String firstName = userDto.getFirstName();
            String lastName = userDto.getLastName();
            String username = userDto.getUsername();
            String email = userDto.getEmail();
            String encodedPassword = passwordEncoder.encode(userDto.getPassword());
            Long roleId = userDto.getRoleId();


            // Get createdBy user ID if provided
            Long createdById = null;
            if (userDto.getCreatedByUsername() != null && !userDto.getCreatedByUsername().isEmpty()) {
                User createdByUser = userRepository.findByUsername(userDto.getCreatedByUsername());
                if (createdByUser != null) {
                    createdById = createdByUser.getId();
                }
            }

            logger.info("Calling stored procedure with parameters: firstName={}, lastName={}, username={}, email={}, roleId={}",
                    firstName, lastName, username, email, roleId);

            // Create and execute stored procedure
            StoredProcedureQuery storedProcedure = entityManager.createStoredProcedureQuery("AddUser");

            // Register input parameters (match your stored procedure parameter names exactly)
            storedProcedure.registerStoredProcedureParameter(1, String.class, ParameterMode.IN);  // firstName
            storedProcedure.registerStoredProcedureParameter(2, String.class, ParameterMode.IN);  // lastName
            storedProcedure.registerStoredProcedureParameter(3, String.class, ParameterMode.IN);  // username
            storedProcedure.registerStoredProcedureParameter(4, String.class, ParameterMode.IN);  // email
            storedProcedure.registerStoredProcedureParameter(5, String.class, ParameterMode.IN);  // password
            storedProcedure.registerStoredProcedureParameter(6, Long.class, ParameterMode.IN);    // roleId
            storedProcedure.registerStoredProcedureParameter(7, Long.class, ParameterMode.IN);    // createdById (nullable)

            // Set parameter values
            storedProcedure.setParameter(1, firstName);
            storedProcedure.setParameter(2, lastName);
            storedProcedure.setParameter(3, username);
            storedProcedure.setParameter(4, email);
            storedProcedure.setParameter(5, encodedPassword);
            storedProcedure.setParameter(6, roleId);
            storedProcedure.setParameter(7, createdById);

            logger.info("About to execute stored procedure...");

            // Execute the stored procedure
            storedProcedure.execute();

            logger.info("Stored procedure executed successfully for user: {}", username);

        } catch (Exception e) {
            logger.error("Error executing stored procedure for user: {}", userDto.getUsername(), e);
            handleStoredProcedureException(e, "Failed to create user");
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

        // ✅ Role mapping
        if (user.getRole() != null) {
            dto.setRoleId(user.getRole().getId());
            dto.setRoleName(user.getRole().getRoleName());
        }

        // ✅ createdAt formatting
        dto.setCreatedAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);

        // ✅ createdBy → another User (not string)
        if (user.getCreatedBy() != null) {
            dto.setCreatedByUsername(user.getCreatedBy().getUsername());
        } else {
            dto.setCreatedByUsername("System");
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

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private ClassificationRepository classificationRepository;

    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Clear tag references
        tagRepository.clearCreatedByUser(userId);
        tagRepository.clearUpdatedByUser(userId);

        // Clear classification references
        classificationRepository.clearCreatedByUser(userId);
        classificationRepository.clearUpdatedByUser(userId);

        // Now delete the user
        userRepository.delete(user);
    }
}