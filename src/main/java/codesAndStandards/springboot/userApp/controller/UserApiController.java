package codesAndStandards.springboot.userApp.controller;

import codesAndStandards.springboot.userApp.dto.GroupListDTO;
import codesAndStandards.springboot.userApp.dto.UserInfoDTO;
import codesAndStandards.springboot.userApp.entity.User;
import codesAndStandards.springboot.userApp.repository.UserRepository;
import codesAndStandards.springboot.userApp.service.Impl.DocumentServiceImpl;
import codesAndStandards.springboot.userApp.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserApiController {

    private static final Logger logger = LoggerFactory.getLogger(DocumentServiceImpl.class);

    private final UserRepository userRepository;
    private final UserService userService;

    /**
     * Get all users for access control selection
     * GET /api/users
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('Admin', 'Manager', 'User')")
    public ResponseEntity<List<UserInfoDTO>> getAllUsers() {
        log.info("REST request to get all users for access control");
        try {
            List<User> users = userRepository.findAll();

            List<UserInfoDTO> userDTOs = users.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());

            log.info("Returning {} users", userDTOs.size());
            return ResponseEntity.ok(userDTOs);
        } catch (Exception e) {
            log.error("Error fetching users", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get user by ID
     * GET /api/users/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('Admin', 'Manager', 'User')")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        log.info("REST request to get user : {}", id);
        try {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("User not found with ID: " + id));

            UserInfoDTO dto = convertToDTO(user);

            return ResponseEntity.ok(dto);
        } catch (RuntimeException e) {
            log.error("Error fetching user with ID: {}", id, e);
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        } catch (Exception e) {
            log.error("Unexpected error fetching user", e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "Failed to fetch user");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Search users by username, email, first name, or last name
     * GET /api/users/search?query=xyz
     */
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('Admin', 'Manager')")
    public ResponseEntity<List<UserInfoDTO>> searchUsers(@RequestParam String query) {
        log.info("REST request to search users with query: {}", query);
        try {
            List<User> users = userRepository.findAll().stream()
                    .filter(user -> {
                        String searchQuery = query.toLowerCase();
                        return (user.getUsername() != null && user.getUsername().toLowerCase().contains(searchQuery)) ||
                                (user.getEmail() != null && user.getEmail().toLowerCase().contains(searchQuery)) ||
                                (user.getFirstName() != null && user.getFirstName().toLowerCase().contains(searchQuery)) ||
                                (user.getLastName() != null && user.getLastName().toLowerCase().contains(searchQuery));
                    })
                    .collect(Collectors.toList());

            List<UserInfoDTO> userDTOs = users.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(userDTOs);
        } catch (Exception e) {
            log.error("Error searching users", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Convert User entity to UserInfoDTO
     */
    private UserInfoDTO convertToDTO(User user) {
        // Get role name from Role entity if available
        String roleName = "Unknown";
        if (user.getRole() != null) {
            roleName = user.getRole().getRoleName();
        }

        // Build full name
        String fullName = "";
        if (user.getFirstName() != null && user.getLastName() != null) {
            fullName = user.getFirstName() + " " + user.getLastName();
        } else if (user.getFirstName() != null) {
            fullName = user.getFirstName();
        } else if (user.getLastName() != null) {
            fullName = user.getLastName();
        }

        return UserInfoDTO.builder()
                .id(user.getId())
                .username(user.getUsername() != null ? user.getUsername() : "")
                .email(user.getEmail() != null ? user.getEmail() : "")
                .role(roleName)
                .department(fullName) // Using department field to store full name for display
                .build();
    }

    @GetMapping("/apis/users/{userId}/groups")
    @PreAuthorize("hasAnyAuthority('Admin', 'Manager')")
    public ResponseEntity<List<GroupListDTO>> getUserGroups(@PathVariable Long userId) {
        logger.info("REST request to get groups for user: {}", userId);
        try {
            List<GroupListDTO> groups = userService.getUserGroups(userId);
            return ResponseEntity.ok(groups);
        } catch (Exception e) {
            logger.error("Error fetching user groups", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}