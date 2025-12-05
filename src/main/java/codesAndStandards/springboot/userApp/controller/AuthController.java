package codesAndStandards.springboot.userApp.controller;

import codesAndStandards.springboot.userApp.dto.*;
import codesAndStandards.springboot.userApp.entity.User;
import codesAndStandards.springboot.userApp.entity.Role;
import codesAndStandards.springboot.userApp.repository.RoleRepository;
import codesAndStandards.springboot.userApp.repository.UserRepository;
import codesAndStandards.springboot.userApp.service.*;
import codesAndStandards.springboot.userApp.service.Impl.UserServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.security.Principal;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.web.multipart.MultipartFile;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import org.springframework.http.ResponseEntity;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Controller
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final UserServiceImpl userServiceImpl;
    private final RoleRepository roleRepository;
    private final DocumentService documentService;
    private final ActivityLogService activityLogService;
    private final GroupService groupService;

    public AuthController(UserService userService, UserServiceImpl userServiceImpl,
                          RoleRepository roleRepository, DocumentService documentService, ActivityLogService activityLogService, GroupService groupService) {
        this.userService = userService;
        this.userServiceImpl = userServiceImpl;
        this.roleRepository = roleRepository;
        this.documentService = documentService;
        this.activityLogService = activityLogService;
        this.groupService = groupService;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String loginForm() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            return "redirect:/users";
        }

        return "login";
    }

    //Registering user before login(NOT required not) -AJ

//    @GetMapping("/register")
//    public String showRegistrationForm(Model model) {
//        model.addAttribute("user", new UserDto());
//        // Add roles to the model for dropdown
//        List<Role> roles = roleRepository.findAll();
//        model.addAttribute("roles", roles);
//        return "register";
//    }

    //NOT REQUIRED
    @PostMapping("/register/save")
    public String registration(@Valid @ModelAttribute("user") UserDto userDto,
                               BindingResult result,
                               Model model,
                               RedirectAttributes redirectAttributes) {

        logger.info("Registration attempt for username: {}", userDto.getUsername());

        // Basic validation first
        if (userDto.getPassword().length() < 1) {
            result.rejectValue("password", null, "Password should have at least 1 characters");
        }

        if (result.hasErrors()) {
            model.addAttribute("user", userDto);
            List<Role> roles = roleRepository.findAll();
            model.addAttribute("roles", roles);
            return "register";
        }

        try {
            // Try stored procedure first
            userService.saveUserWithStoredProcedure(userDto);
            logger.info("User registered successfully using stored procedure: {}", userDto.getUsername());
            redirectAttributes.addFlashAttribute("success", "Registration successful! You can now login.");
            return "redirect:/register?success=true";
        } catch (RuntimeException e) {
            logger.warn("Stored procedure failed for registration, trying fallback: {}", e.getMessage());
            try {
                // Fallback to JPA method
                userServiceImpl.saveUserFallback(userDto);
                logger.info("User registered successfully using fallback method: {}", userDto.getUsername());
                redirectAttributes.addFlashAttribute("success", "Registration successful! You can now login.");
                return "redirect:/register?success=true";
            } catch (RuntimeException fallbackError) {
                logger.error("Both stored procedure and fallback failed for registration", fallbackError);
                if (fallbackError.getMessage().contains("Username already exists")) {
                    result.rejectValue("username", null, fallbackError.getMessage());
                } else if (fallbackError.getMessage().contains("Email already exists")) {
                    result.rejectValue("email", null, fallbackError.getMessage());
                } else {
                    result.rejectValue("username", null, "Registration failed: " + fallbackError.getMessage());
                }
                model.addAttribute("user", userDto);
                List<Role> roles = roleRepository.findAll();
                model.addAttribute("roles", roles);
                return "register";
            }
        }
    }

    // ================== ADMIN ONLY ==================  -AJ

    // User Management -AJ
    @PreAuthorize("hasAuthority('Admin')")
    @GetMapping("/users")
    public String users(Model model) {
        List<UserDto> users = userService.findAllUsers();
        List<Role> roles = roleRepository.findAll();

        // Calculate statistics
        long totalUsers = users.size();
        long adminCount = users.stream().filter(u -> "Admin".equals(u.getRoleName())).count();
        long managerCount = users.stream().filter(u -> "Manager".equals(u.getRoleName())).count();
        long viewerCount = users.stream().filter(u -> "Viewer".equals(u.getRoleName())).count();

        model.addAttribute("users", users);
        model.addAttribute("roles", roles);
        model.addAttribute("user", new UserDto());


        // Add statistics to model
        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("adminCount", adminCount);
        model.addAttribute("managerCount", managerCount);
        model.addAttribute("viewerCount", viewerCount);

        return "users";
    }

    @GetMapping("/users/check-username")
    public ResponseEntity<Map<String, Boolean>> checkUsername(@RequestParam String username) {
        Map<String, Boolean> response = new HashMap<>();

        if (username == null || username.trim().isEmpty()) {
            response.put("exists", false);
            return ResponseEntity.ok(response);
        }

        boolean exists = userService.existsByUsername(username.trim());
        response.put("exists", exists);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/users/check-email")
    public ResponseEntity<Map<String, Boolean>> checkEmail(@RequestParam String email) {
        Map<String, Boolean> response = new HashMap<>();

        if (email == null || email.trim().isEmpty()) {
            response.put("exists", false);
            return ResponseEntity.ok(response);
        }

        boolean exists = userService.existsByEmail(email.trim());
        response.put("exists", exists);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        Optional<User> user = userService.findById(id);

        if (user.isPresent()) {
            return ResponseEntity.ok(user.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // FIXED in users.html for adding new user by admin -AJ
    @PreAuthorize("hasAuthority('Admin')")
    @PostMapping("/add/save")
    public String addUser(@Valid @ModelAttribute("user") UserDto userDto,
                          BindingResult result,
                          Model model,
                          Principal principal,
                          RedirectAttributes redirectAttributes) {

        String adminUsername = principal != null ? principal.getName() : "Unknown";

        logger.info("Admin attempting to add user: {}", userDto.getUsername());

        if (principal != null) {
            userDto.setCreatedByUsername(adminUsername);
        }

        if (userDto.getPassword().length() < 1) {
            result.rejectValue("password", null, "Password should have at least 1 characters");
        }

        if (result.hasErrors()) {
            logger.warn("Validation errors for user: {}", userDto.getUsername());

            activityLogService.logByUsername(
                    adminUsername,
                    ActivityLogService.USER_CREATE_FAILED,
                    "Validation failed while creating user: " + userDto.getUsername()
                            + " (Errors: " + result.getAllErrors() + ")"
            );

            redirectAttributes.addFlashAttribute("error", "Validation failed. Please check your input.");
            return "redirect:/users?error=true";
        }

        try {
            // Save user first
            userService.saveUserWithStoredProcedure(userDto);
            logger.info("User added successfully using stored procedure: {}", userDto.getUsername());

            // Get the created user to obtain the ID
            User createdUser = userService.findUserByUsername(userDto.getUsername());

            if (createdUser != null) {
                // Save user-group associations - DIRECTLY USE getGroupIds()
                try {
                    userService.saveUserGroupAssociations(createdUser.getId(), userDto.getGroupIds());
                    logger.info("User-group associations saved for user: {}", userDto.getUsername());
                } catch (Exception e) {
                    logger.error("Failed to save user-group associations: {}", e.getMessage());
                }
            }

            activityLogService.logByUsername(
                    adminUsername,
                    ActivityLogService.USER_CREATE,
                    "Created user: " + userDto.getUsername() +
                            " (Email: " + userDto.getEmail() + ", created by: " + userDto.getCreatedByUsername() + ")"
            );

            redirectAttributes.addFlashAttribute("success",
                    "User '" + userDto.getUsername() + "' has been added successfully!");
            return "redirect:/users?success=true";

        } catch (RuntimeException e) {
            logger.warn("Stored procedure failed for add user, trying fallback: {}", e.getMessage());

            try {
                // Fallback to JPA method
                userServiceImpl.saveUserFallback(userDto);
                logger.info("User added successfully using fallback method: {}", userDto.getUsername());

                User createdUser = userService.findUserByUsername(userDto.getUsername());

                if (createdUser != null) {
                    try {
                        userService.saveUserGroupAssociations(createdUser.getId(), userDto.getGroupIds());
                        logger.info("User-group associations saved for user: {}", userDto.getUsername());
                    } catch (Exception ex) {
                        logger.error("Failed to save user-group associations: {}", ex.getMessage());
                    }
                }

                activityLogService.logByUsername(
                        adminUsername,
                        ActivityLogService.USER_CREATE,
                        "Created user: " + userDto.getUsername() +
                                " (Email: " + userDto.getEmail() + ", created by: " + userDto.getCreatedByUsername() + ")"
                );

                redirectAttributes.addFlashAttribute("success",
                        "User '" + userDto.getUsername() + "' has been added successfully!");
                return "redirect:/users?success=true";

            } catch (RuntimeException fallbackError) {
                logger.error("Both stored procedure and fallback failed for add user", fallbackError);

                activityLogService.logByUsername(
                        adminUsername,
                        ActivityLogService.USER_CREATE_FAILED,
                        "Failed to create user: " + userDto.getUsername()
                                + " | Reason: " + fallbackError.getMessage()
                );

                if (fallbackError.getMessage().contains("Username already exists")) {
                    redirectAttributes.addFlashAttribute("error", "Username already exists");
                } else if (fallbackError.getMessage().contains("Email already exists")) {
                    redirectAttributes.addFlashAttribute("error", "Email already exists");
                } else {
                    redirectAttributes.addFlashAttribute("error", "Failed to add user: " + fallbackError.getMessage());
                }
                return "redirect:/users?error=true";
            }
        }
    }

    @PreAuthorize("hasAuthority('Admin')")
    @PostMapping("/edit/{id}")
    public String updateUserById(@Valid @ModelAttribute("user") UserDto updatedUserDto,
                                 BindingResult result,
                                 @PathVariable Long id,
                                 Model model,
                                 Principal principal,
                                 RedirectAttributes redirectAttributes) {

        String adminUsername = principal != null ? principal.getName() : "Unknown";

        if (result.hasErrors()) {
            activityLogService.logByUsername(
                    adminUsername,
                    ActivityLogService.USER_EDIT_FAILED,
                    "Validation failed while editing user ID: " + id
                            + " | Errors: " + result.getAllErrors()
            );

            redirectAttributes.addFlashAttribute("error", "Validation failed. Please check your input.");
            return "redirect:/users?error=true";
        }

        try {
            UserDto currentUser = userService.findUserById(id);
            if (currentUser == null) {
                activityLogService.logByUsername(
                        adminUsername,
                        ActivityLogService.USER_EDIT_FAILED,
                        "Failed to edit user: User not found with ID " + id
                );

                redirectAttributes.addFlashAttribute("error", "User not found");
                return "redirect:/users?error=true";
            }

            String username = currentUser.getUsername();

            try {
                userService.editUserByAdminWithStoredProcedure(username, updatedUserDto);
                logger.info("User updated using stored procedure: {}", username);
            } catch (RuntimeException e) {
                logger.warn("Stored procedure failed, using fallback: {}", e.getMessage());

                try {
                    userService.editUser(updatedUserDto, id);
                    logger.info("User updated successfully using fallback: {}", username);
                } catch (RuntimeException fallbackError) {
                    activityLogService.logByUsername(
                            adminUsername,
                            ActivityLogService.USER_EDIT_FAILED,
                            "Failed to edit user: " + username + " | Reason: " + fallbackError.getMessage()
                    );

                    redirectAttributes.addFlashAttribute("error", "Failed to update user: " + fallbackError.getMessage());
                    return "redirect:/users?error=true";
                }
            }

            // Update user-group associations - DIRECTLY USE getGroupIds()
            try {
                userService.updateUserGroupAssociations(id, updatedUserDto.getGroupIds());
                logger.info("User-group associations updated for user: {}", username);
            } catch (Exception e) {
                logger.error("Failed to update user-group associations: {}", e.getMessage());
            }

            activityLogService.logByUsername(
                    adminUsername,
                    ActivityLogService.USER_EDIT,
                    "Edited user: " + username + " (By: " + currentUser.getCreatedByUsername() + ")"
            );

            redirectAttributes.addFlashAttribute("success", "User updated successfully!");
            return "redirect:/users?success=true";

        } catch (Exception e) {
            activityLogService.logByUsername(
                    adminUsername,
                    ActivityLogService.USER_EDIT_FAILED,
                    "Failed to edit user ID: " + id + " | Reason: " + e.getMessage()
            );

            logger.error("Failed to update user", e);
            redirectAttributes.addFlashAttribute("error", "Failed to update user: " + e.getMessage());
            return "redirect:/users?error=true";
        }
    }

// DELETE this method - no longer needed
// private List<Long> parseGroupIdsFromString(String groupIds) { ... }

    //Editing user details(only by admin) -AJ
    @PreAuthorize("hasAuthority('Admin')")
    @GetMapping("/edit/{id}")
    public String editUser(@PathVariable Long id, Model model) {
        UserDto user = userService.findUserById(id);
        if (user == null) {
            return "redirect:/users?error=userNotFound";
        }
        model.addAttribute("user", user);
        // Add roles to the model for dropdown
        List<Role> roles = roleRepository.findAll();
        model.addAttribute("roles", roles);
        return "edit";
    }


    // All user can edit his own profile(Admin can't do any changes here) -AJ
    @GetMapping("/profile")
    public String showProfilePage(Model model, Principal principal) {
        User user = userService.findUserByUsername(principal.getName());
        if (user == null) {


            return "redirect:/login?error=userNotFound";
        }
        UserDto userDto = userService.findUserById(user.getId());
        model.addAttribute("user", userDto);
        // Add roles to the model for dropdown (if user can change role in profile)
        List<Role> roles = roleRepository.findAll();
        model.addAttribute("roles", roles);
        return "profile";
    }

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/profile/update")
    public String updateProfile(@Valid @ModelAttribute("user") UserDto userDto,
                                BindingResult result,
                                Principal principal,
                                Model model,
                                RedirectAttributes redirectAttributes) {

        String username = principal != null ? principal.getName() : "Unknown";

        if (result.hasErrors()) {
            model.addAttribute("user", userDto);
            model.addAttribute("roles", roleRepository.findAll());
            activityLogService.logByUsername(username, ActivityLogService.EDIT_PROFILE_FAILED, "Validation failed while editing profile");
            return "profile";
        }

        User loggedInUser = userService.findUserByUsername(username);
        if (loggedInUser == null) {
            return "redirect:/login?error=userNotFound";
        }

        try {
            String rawCurrent = userDto.getCurrentPassword();
            String newPassword = userDto.getNewPassword();
            String confirmPassword = userDto.getConfirmPassword();
            String encodedFromDb = loggedInUser.getPassword();

            logger.debug("CurrentPassword={}, NewPassword={}, ConfirmPassword={}",
                    userDto.getCurrentPassword(),
                    userDto.getNewPassword(),
                    userDto.getConfirmPassword());


            // If user is trying to change password, validate current password first
            if (rawCurrent != null && !rawCurrent.isBlank()) {

                // Basic sanity checks
                if (encodedFromDb == null || encodedFromDb.isBlank()) {
                    logger.warn("User {} has no encoded password in DB.", username);
                    redirectAttributes.addFlashAttribute("error", "Unable to verify current password.");
                    activityLogService.logByUsername(username, ActivityLogService.EDIT_PROFILE_FAILED, "No password stored for user");
                    return "redirect:/profile";
                }

                // Trim raw input to avoid accidental spaces
                rawCurrent = rawCurrent.trim();

                // DEBUG info (remove in production) — never log the raw password itself
                logger.debug("Verifying password for user '{}'. encoded length: {}", username, encodedFromDb.length());

                boolean matches = passwordEncoder.matches(rawCurrent, encodedFromDb);
                logger.debug("Password match result: {}", matches);

                if (!matches) {
                    redirectAttributes.addFlashAttribute("passwordError", "current");
                    activityLogService.logByUsername(username, ActivityLogService.EDIT_PROFILE_FAILED, "Incorrect current password entered");
                    return "redirect:/profile#changePasswordModal";
                }

                // Check new/confirm match
                if (newPassword == null || confirmPassword == null || !newPassword.equals(confirmPassword)) {
                    redirectAttributes.addFlashAttribute("passwordError", "mismatch");
                    return "redirect:/profile#changePasswordModal";
                }

                // Encode and set new password (do NOT encode original DB password)
                loggedInUser.setPassword(passwordEncoder.encode(newPassword));
            }

            // Update other fields (example)
            loggedInUser.setFirstName(userDto.getFirstName());
            loggedInUser.setEmail(userDto.getEmail());
//            loggedInUser.setMobile(userDto.getMobile());

            userRepository.save(loggedInUser);

            activityLogService.logByUsername(username, ActivityLogService.EDIT_PROFILE, "Profile updated successfully");
            redirectAttributes.addFlashAttribute("success", "Profile updated successfully!");
        } catch (Exception e) {
            logger.error("Failed to update profile", e);
            activityLogService.logByUsername(username, ActivityLogService.EDIT_PROFILE_FAILED, "Failed to update own profile: " + e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to update profile: " + e.getMessage());
        }

        return "redirect:/profile";
    }

    //Admin can delete any users(even other admins) but not himself -AJ
    @PreAuthorize("hasAuthority('Admin')")
    @GetMapping("/delete/{id}")
    public String deleteUser(@PathVariable Long id,
                             Principal principal,
                             RedirectAttributes redirectAttributes) {

        String adminUsername = principal != null ? principal.getName() : "Unknown";
        System.out.println("=== DELETE METHOD CALLED FOR ID: " + id + " ===");

        User loggedInUser = userService.findUserByUsername(adminUsername);

        // Prevent self-deletion
        if (loggedInUser != null && loggedInUser.getId().equals(id)) {
            System.out.println("=== BLOCKED: User trying to delete themselves ===");

            // LOG ACTIVITY - USER_DELETE_FAILED
            activityLogService.logByUsername(
                    adminUsername,
                    ActivityLogService.USER_DELETE_FAILED,
                    "Attempted to delete self (user ID: " + id + ")"
            );

            redirectAttributes.addFlashAttribute("error", "You cannot delete yourself.");
            return "redirect:/users";
        }

        // Check if user exists
        if (!userService.doesUserExist(id)) {
            System.out.println("=== BLOCKED: User does not exist ===");

            // LOG ACTIVITY - USER_DELETE_FAILED
            activityLogService.logByUsername(
                    adminUsername,
                    ActivityLogService.USER_DELETE_FAILED,
                    "Attempted to delete non-existent user (ID: " + id + ")"
            );

            redirectAttributes.addFlashAttribute("error", "User does not exist");
            return "redirect:/users";
        }

        try {
            UserDto userToDelete = userService.findUserById(id);
            String usernameToDelete = userToDelete.getUsername();
            String emailToDelete = userToDelete.getEmail();
            System.out.println("=== Attempting to delete user: " + usernameToDelete + " ===");

            try {
                // Try stored procedure first
                userService.deleteUserWithStoredProcedure(usernameToDelete);
                System.out.println("=== SUCCESS: Deleted via stored procedure ===");

                // LOG ACTIVITY - USER_DELETE (success)
                activityLogService.logByUsername(
                        adminUsername,
                        ActivityLogService.USER_DELETE,
                        "Deleted user: " + usernameToDelete + " (Email: " + emailToDelete + ")"
                );

                redirectAttributes.addFlashAttribute("success", "User has been deleted successfully");

            } catch (RuntimeException e) {
                System.out.println("=== Stored procedure failed: " + e.getMessage() + " ===");
                System.out.println("=== Attempting JPA fallback ===");

                try {
                    // Fallback to JPA
                    userService.deleteUserById(id);
                    System.out.println("=== SUCCESS: Deleted via JPA ===");

                    // LOG ACTIVITY - USER_DELETE (success)
                    activityLogService.logByUsername(
                            adminUsername,
                            ActivityLogService.USER_DELETE,
                            "Deleted user: " + usernameToDelete + " (Email: " + emailToDelete + ")"
                    );

                    redirectAttributes.addFlashAttribute("success", "User has been deleted successfully");

                } catch (RuntimeException fallbackError) {
                    // LOG ACTIVITY - USER_DELETE_FAILED
                    activityLogService.logByUsername(
                            adminUsername,
                            ActivityLogService.USER_DELETE_FAILED,
                            "Failed to delete user: " + usernameToDelete + " | Reason: " + fallbackError.getMessage()
                    );

                    System.out.println("=== FAILED: " + fallbackError.getMessage() + " ===");
                    fallbackError.printStackTrace();
                    redirectAttributes.addFlashAttribute("error",
                            "Failed to delete user: " + fallbackError.getMessage());
                }
            }
        } catch (Exception e) {
            // LOG ACTIVITY - USER_DELETE_FAILED
            activityLogService.logByUsername(
                    adminUsername,
                    ActivityLogService.USER_DELETE_FAILED,
                    "Failed to delete user ID: " + id + " | Reason: " + e.getMessage()
            );

            System.out.println("=== FAILED: " + e.getMessage() + " ===");
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Failed to delete user: " + e.getMessage());
        }

        return "redirect:/users";
    }


    //Page you get after logging in as a Manager -AJ
    @PreAuthorize("hasAuthority('Manager')")
    @GetMapping("/manager")
    public String managerPage(Model model, Principal principal) {
        User user = userService.findUserByUsername(principal.getName());
        if (user == null) {
            return "redirect:/login?error=userNotFound";
        }
        model.addAttribute("user", userService.findUserById(user.getId()));
        return "manager";
    }

    @Autowired
    private TagService tagService;

    @Autowired
    private ClassificationService classificationService;


    @GetMapping("/bulk-upload")
    public String showBulkUpload() {
        return "Bulk-Upload-Documents";
    }


    // Load upload page: Uploading new document, admin and manager both have permission for this -AJ
    @PreAuthorize("hasAnyAuthority('Manager', 'Admin')")
    @GetMapping("/upload")
    public String showUploadForm(Model model) {
        model.addAttribute("document", new DocumentDto());
        // Add existing tags and classifications for the dropdown
        List<TagDto> allTags = tagService.getAllTags();
        List<ClassificationDto> allClassifications = classificationService.getAllClassifications();
        List<GroupListDTO> allGroups = groupService.getAllGroups();
        // Limit to only 3 most recent
        List<TagDto> limitedTags = allTags.stream()
//                .limit(3)
                .collect(Collectors.toList());

        List<ClassificationDto> limitedClassifications = allClassifications.stream()
//                .limit(3)
                .collect(Collectors.toList());

        model.addAttribute("allTags", limitedTags);
        model.addAttribute("allClassifications", limitedClassifications);
        model.addAttribute("allGroups", allGroups);

        return "upload";
    }

    @PreAuthorize("hasAnyAuthority('Manager', 'Admin')")
    @PostMapping("/upload")
    public String uploadDocument(@Valid @ModelAttribute("document") DocumentDto documentDto,
                                 BindingResult result,
                                 @RequestParam("file") MultipartFile file,

                                 @RequestParam(value = "tagNames", required = false) String tagsJson,
                                 @RequestParam(value = "classificationNames", required = false) String classificationsJson,
                                 @RequestParam(value = "groupIds", required = false) String groupIds,
                                 Principal principal,
                                 Model model,
                                 RedirectAttributes redirectAttributes,
                                 Authentication authentication) {

        String username = principal != null ? principal.getName() : "Unknown";
        documentDto.setTagNames(tagsJson);
        documentDto.setClassificationNames(classificationsJson);
        documentDto.setGroupIds(groupIds);

        // Get current user ID
        Long userId = getCurrentUserId(authentication);

        // Parse tags from JSON
        List<String> tagNames = new ArrayList<>();
        if (tagsJson != null && !tagsJson.isEmpty()) {
            try {
                // Try parsing as JSON array
                tagNames = new ObjectMapper().readValue(tagsJson, new TypeReference<List<String>>() {
                });
            } catch (Exception e) {
                logger.error("Error parsing tags JSON: {}", e.getMessage());
                // Fallback to comma-separated
                tagNames = Arrays.asList(tagsJson.split(","));
            }
        }

        // Parse classifications from JSON
        List<String> classificationNames = new ArrayList<>();
        if (classificationsJson != null && !classificationsJson.isEmpty()) {
            try {
                classificationNames = new ObjectMapper().readValue(classificationsJson, new TypeReference<List<String>>() {
                });
            } catch (Exception e) {
                logger.error("Error parsing classifications JSON: {}", e.getMessage());
                classificationNames = Arrays.asList(classificationsJson.split(","));
            }
        }

        // Create tags if they don't exist - PASS userId
        for (String tagName : tagNames) {
            if (tagName != null && !tagName.trim().isEmpty()) {
                tagService.createTagIfNotExists(tagName.trim(), userId);
            }
        }

        // If classification are to be created while uploading document -AJ
//        for (String classificationName : classificationNames) {
//            if (classificationName != null && !classificationName.trim().isEmpty()) {
//                classificationService.createClassificationIfNotExists(classificationName.trim(), userId);
//            }
//        }

        // IMPORTANT: Set tag names in DTO BEFORE validation
        documentDto.setTagNames(String.join(",", tagNames));
        documentDto.setClassificationNames(String.join(",", classificationNames));

        // Validation errors
        if (result.hasErrors()) {
            model.addAttribute("document", documentDto);

            // Log all validation errors for debugging
            result.getAllErrors().forEach(error -> {
                logger.error("Validation error: {}", error.getDefaultMessage());
            });

            // Reload tags and classifications
            List<TagDto> allTags = tagService.getAllTags();
            List<ClassificationDto> allClassifications = classificationService.getAllClassifications();
            List<TagDto> limitedTags = allTags.stream().limit(3).collect(Collectors.toList());
            List<ClassificationDto> limitedClassifications = allClassifications.stream().limit(3).collect(Collectors.toList());

            model.addAttribute("allTags", limitedTags);
            model.addAttribute("allClassifications", limitedClassifications);

            // LOG FAILURE - DOCUMENT_UPLOAD_FAILED due to validation
            activityLogService.logByUsername(
                    username,
                    ActivityLogService.DOCUMENT_UPLOAD_FAILED,
                    "Validation failed while uploading document: '" + documentDto.getTitle() + "'"
            );

            return "upload";
        }

        try {
            // Save document (tags already set above)
            documentService.saveDocument(documentDto, file, username, groupIds);

            // LOG SUCCESS - DOCUMENT_UPLOAD
            activityLogService.logByUsername(
                    username,
                    ActivityLogService.DOCUMENT_UPLOAD,
                    String.format("Uploaded document: '%s' (File: %s)",
                            documentDto.getTitle(),
                            file.getOriginalFilename())
            );

            redirectAttributes.addFlashAttribute("success", "Document uploaded successfully!");
            return "redirect:/upload";

        } catch (Exception e) {
            logger.error("Failed to upload document", e);

            // LOG FAILURE - DOCUMENT_UPLOAD_FAILED
            activityLogService.logByUsername(
                    username,
                    ActivityLogService.DOCUMENT_UPLOAD_FAILED,
                    String.format("Failed to upload document: '%s' (File: %s) - Error: %s",
                            documentDto.getTitle(),
                            file.getOriginalFilename(),
                            e.getMessage())
            );

            redirectAttributes.addFlashAttribute("error", "Failed to upload document: " + e.getMessage());
            model.addAttribute("document", documentDto);

            return "redirect:/upload";
        }
    }

    private Long getCurrentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }

        String username = authentication.getName();
        User user = userRepository.findByUsername(username);

        if (user == null) {
            throw new RuntimeException("User not found with username: " + username);
        }

        return user.getId();
    }

    // Helper method to format file size (add to your controller) -AJ
    private String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return String.format("%.2f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    //From here it will navigate to TagController -AJ
    @PreAuthorize("hasAnyAuthority('Manager', 'Admin')")
    @GetMapping("/tags-management")
    public String tagsManagement() {
        return "tags-management";
    }

    //From here it will navigate to ClassificationController -AJ
    @PreAuthorize("hasAnyAuthority('Admin','Manager')")
    @GetMapping("/classifications-management")
    public String classificationsManagement() {
        return "classifications-management";
    }


    // ================== DOCUMENT LIST - SHOW ALL DOCUMENTS ==================

    //DOCUMENT LIBRARY: Show all the uploaded document -AJ
    @PreAuthorize("hasAnyAuthority('Manager', 'Admin', 'Viewer')")
    @GetMapping("/documents")
    public String listDocuments(Model model, Principal principal) {

        // Get logged-in user
        User user = userRepository.findByUsername(principal.getName());
        Long userId = user.getId();
        String userRole = user.getRole().getRoleName();

        List<DocumentDto> documentsToShow;

        // 🔥 If ADMIN → show all documents
        if ("Admin".equalsIgnoreCase(userRole)) {
            documentsToShow = documentService.findAllDocuments();
        } else {
            // 🔥 Otherwise → show only accessible documents
            documentsToShow = documentService.findDocumentsAccessibleByUser(userId);
        }

        // Add filtered documents to model
        model.addAttribute("documents", documentsToShow);
        model.addAttribute("currentUsername", principal.getName());
        model.addAttribute("userRole", userRole);

        // Tag dropdown
        model.addAttribute("allTags", tagService.getAllTags());

        // Classification dropdown
        model.addAttribute("allClassifications", classificationService.getAllClassifications());

        return "document-list";
    }

    // Editing only the metadata's of the document -AJ
    @PreAuthorize("hasAnyAuthority('Manager', 'Admin')")
    @GetMapping("/document/edit/{id}")
    public String showEditForm(@PathVariable("id") Long id, Model model) {
        DocumentDto document = documentService.findDocumentById(id);
        if (document == null) {
            return "redirect:/documents?error=Document not found";
        }

        logger.info("Loading edit form for document ID: {}", id);
        logger.info("Current tags: {}", document.getTagNames());
        logger.info("Current classifications: {}", document.getClassificationNames());

        // ✅ NEW: Get current document's groups
        try {
            List<Long> documentGroupIds = groupService.getGroupIdsByDocumentId(id);
            String groupIds = documentGroupIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            document.setGroupIds(groupIds);
            logger.info("Current groups: {}", groupIds);
        } catch (Exception e) {
            logger.error("Error loading groups for document: {}", e.getMessage());
            document.setGroupIds("");
        }

        model.addAttribute("document", document);
        model.addAttribute("allTags", tagService.getAllTags());
        model.addAttribute("allClassifications", classificationService.getAllClassifications());
        model.addAttribute("allGroups", groupService.getAllGroups()); // ✅ NEW: Add all groups

        return "edit-document";
    }

    @Autowired
    private NetworkFileService fileStorageService;

    //Tags can be created while updating document also and that will also be stored in Tags table -AJ
    //Tags can be created while updating document also and that will also be stored in Tags table -AJ
    @PreAuthorize("hasAnyAuthority('Manager', 'Admin')")
    @PostMapping("/document/edit/{id}")
    public String updateDocument(@PathVariable("id") Long id,
                                 @Valid @ModelAttribute("document") DocumentDto documentDto,
                                 BindingResult result,
                                 @RequestParam(value = "file", required = false) MultipartFile file,
                                 @RequestParam(value = "tagNames", required = false) String tagsJson,
                                 @RequestParam(value = "classificationNames", required = false) String classificationsJson,
                                 @RequestParam(value = "groupIds", required = false) String groupIds,
                                 Principal principal,
                                 Model model,
                                 RedirectAttributes redirectAttributes,
                                 Authentication authentication) {

        String username = principal != null ? principal.getName() : "Unknown";

        // Get current user ID
        Long userId = getCurrentUserId(authentication);

        // Parse tags from JSON
        List<String> tagNames = new ArrayList<>();
        if (tagsJson != null && !tagsJson.isEmpty()) {
            try {
                tagNames = new ObjectMapper().readValue(tagsJson, new TypeReference<List<String>>() {
                });
            } catch (Exception e) {
                logger.error("Error parsing tags JSON: {}", e.getMessage());
                tagNames = Arrays.asList(tagsJson.split(","));
            }
        }

        // Parse classifications from JSON
        List<String> classificationNames = new ArrayList<>();
        if (classificationsJson != null && !classificationsJson.isEmpty()) {
            try {
                classificationNames = new ObjectMapper().readValue(classificationsJson, new TypeReference<List<String>>() {
                });
            } catch (Exception e) {
                logger.error("Error parsing classifications JSON: {}", e.getMessage());
                classificationNames = Arrays.asList(classificationsJson.split(","));
            }
        }

        // Create tags if they don't exist
        for (String tagName : tagNames) {
            if (tagName != null && !tagName.trim().isEmpty()) {
                tagService.createTagIfNotExists(tagName.trim(), userId);
            }
        }

        // If classification are to be created while updating document -AJ
//        for (String classificationName : classificationNames) {
//            if (classificationName != null && !classificationName.trim().isEmpty()) {
//                classificationService.createClassificationIfNotExists(classificationName.trim(), userId);
//            }
//        }

        // Set tag/classification names in DTO before validation
        documentDto.setTagNames(String.join(",", tagNames));
        documentDto.setClassificationNames(String.join(",", classificationNames));
        documentDto.setGroupIds(groupIds);

        // Validation errors
        if (result.hasErrors()) {
            model.addAttribute("document", documentDto);
            model.addAttribute("allTags", tagService.getAllTags());
            model.addAttribute("allClassifications", classificationService.getAllClassifications());

            activityLogService.logByUsername(
                    username,
                    ActivityLogService.DOCUMENT_EDIT_FAILED,
                    "Validation failed while editing document ID: " + id + " ('" + documentDto.getTitle() + "')"
            );

            return "edit-document";
        }

        try {
            // Get OLD document details BEFORE updating
            DocumentDto oldDoc = documentService.findDocumentById(id);
            String oldTitle = oldDoc.getTitle();
            String oldProductCode = oldDoc.getProductCode();
            String oldFilePath = oldDoc.getFilePath();

            // Update document including optional file
            documentService.updateDocument(id, documentDto, file, username, groupIds);

            // Build change log
            StringBuilder changes = new StringBuilder();
            changes.append("Edited document: '").append(documentDto.getTitle()).append("'");

            if (!oldTitle.equals(documentDto.getTitle())) {
                changes.append(" | Title: '").append(oldTitle).append("' → '").append(documentDto.getTitle()).append("'");
            }
            if (!oldProductCode.equals(documentDto.getProductCode())) {
                changes.append(" | Product Code: '").append(oldProductCode).append("' → '").append(documentDto.getProductCode()).append("'");
            }
            if (file != null && !file.isEmpty()) {
                changes.append(" | File updated: ").append(file.getOriginalFilename());
            }

            // LOG SUCCESS - DOCUMENT_EDIT
            activityLogService.logByUsername(
                    username,
                    ActivityLogService.DOCUMENT_EDIT,
                    changes.toString()
            );

            redirectAttributes.addFlashAttribute("success", "Document updated successfully!");
            return "redirect:/documents";

        } catch (Exception e) {
            logger.error("Failed to update document", e);

            activityLogService.logByUsername(
                    username,
                    ActivityLogService.DOCUMENT_EDIT_FAILED,
                    String.format("Failed to edit document ID: %d ('%s') - Error: %s",
                            id, documentDto.getTitle(), e.getMessage())
            );

            redirectAttributes.addFlashAttribute("error", "Failed to update document: " + e.getMessage());
            model.addAttribute("document", documentDto);
            model.addAttribute("allTags", tagService.getAllTags());
            model.addAttribute("allClassifications", classificationService.getAllClassifications());
            return "edit-document";
        }
    }

    @Autowired

//    private NetworkFileService fileStorageService;

    @PreAuthorize("hasAuthority('Admin')")
    @GetMapping("/diagnose-network-share")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> diagnoseNetworkShare() {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("host", networkFileService.getHost());
            response.put("share", networkFileService.getShareName());
            response.put("folder", networkFileService.getFolder());

            Map<String, Object> permissions = networkFileService.checkPermissions();
            response.put("permissions", permissions);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    //Deleting any document -AJ
    @PreAuthorize("hasAnyAuthority('Manager', 'Admin')")
    @GetMapping("/documents/delete/{id}")
    public String deleteDocument(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        String username = principal != null ? principal.getName() : "Unknown";

        try {
            // Get document details BEFORE deleting
            DocumentDto document = documentService.findDocumentById(id);
            String title = document.getTitle();
            String productCode = document.getProductCode();

            // Delete document
            documentService.deleteDocument(id);

            // LOG SUCCESS - DOCUMENT_DELETE
            activityLogService.logByUsername(
                    username,
                    ActivityLogService.DOCUMENT_DELETE,
                    String.format("Deleted document: '%s' (Product Code: %s)", title, productCode)
            );

            redirectAttributes.addFlashAttribute("success", "Document deleted successfully");

        } catch (Exception e) {
            logger.error("Failed to delete document ID: {}", id, e);

            // LOG FAILURE - DOCUMENT_DELETE_FAILED
            activityLogService.logByUsername(
                    username,
                    ActivityLogService.DOCUMENT_DELETE_FAILED,
                    String.format("Failed to delete document ID: %d - Error: %s", id, e.getMessage())
            );

            redirectAttributes.addFlashAttribute("error", "Failed to delete document: " + e.getMessage());
        }

        return "redirect:/documents";
    }


    @Autowired
    private NetworkFileService networkFileService;

    @Autowired
    private WatermarkService watermarkService;

    //Viewing any document(all have permission for this) -AJ
    @PreAuthorize("hasAnyAuthority('Manager', 'Admin','Viewer')")
    @GetMapping("/documents/DocView/{id}")
    public ResponseEntity<Resource> viewDocument(@PathVariable Long id) {
        try {
            String filePath = documentService.getFilePath(id);
            Path path = Paths.get(filePath);
            Resource resource = new UrlResource(path.toUri());

            if (resource.exists() && resource.isReadable()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                throw new RuntimeException("File not found or not readable");
            }
        } catch (Exception e) {
            logger.error("Failed to view document", e);
            return ResponseEntity.notFound().build();
        }
    }


    @PreAuthorize("hasAnyAuthority('Manager', 'Admin','Viewer')")
    @GetMapping("/documents/DocViewer-view/{id}")
    public ResponseEntity<byte[]> viewDocumentForViewer(@PathVariable Long id, Principal principal) {
        try {
            DocumentDto document = documentService.findDocumentById(id);

            String filePath = documentService.getFilePath(id);
            logger.info("Loading PDF from network share: {}", filePath);

            byte[] pdfBytes = networkFileService.readFileFromNetworkShare(filePath);

            logger.info("Successfully loaded PDF, size: {} bytes", pdfBytes.length);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate")
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(pdfBytes.length))
                    .body(pdfBytes);

        } catch (Exception e) {
            logger.error("Failed to view document for viewer", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("Error loading PDF: " + e.getMessage()).getBytes());
        }
    }

// ================== SECURE PDF VIEWER API ================== -AJ

    @Autowired
    private UserRepository userRepository;

    @PreAuthorize("hasAnyAuthority('Manager', 'Admin','Viewer')")
    @GetMapping("/DocViewer")
    public String showPdfViewer(@RequestParam Long id, Model model, Principal principal) {
        logger.info("===== VIEWER METHOD CALLED =====");
        logger.info("Document ID: " + id);
        logger.info("User: " + principal.getName());

        try {
            DocumentDto document = documentService.findDocumentById(id);
            logger.info("Document found: " + document.getTitle());

            User user = userRepository.findByUsername(principal.getName());

            String userRole = user.getRole().getRoleName(); // "Admin", "Manager", or "Viewer"

            logger.info("User role: " + userRole);
            // ✅ CHANGE 4: Pass ALL required attributes to model
            model.addAttribute("documentId", id);
            model.addAttribute("document", document); // Pass document object
            model.addAttribute("userRole", userRole); // Pass user role

            logger.info("Document title: " + document.getTitle());
            logger.info("Document product code: " + document.getProductCode());
            logger.info("Document tags: " + document.getTagNames());
            model.addAttribute("documentId", id);
            logger.info("Returning viewer template");

            // Add username safely
            model.addAttribute("username", user.getUsername());

            return "DocViewer";
        } catch (Exception e) {
            logger.error("Failed to load viewer", e);
            return "redirect:/documents?error=notfound";
        }
    }

    @PreAuthorize("hasAnyAuthority('Manager', 'Admin','Viewer')")
    @GetMapping("/documents/info/{id}")
    public ResponseEntity<?> getDocumentInfo(@PathVariable Long id, Principal principal) {
        try {
            DocumentDto document = documentService.findDocumentById(id);

            // Verify the document belongs to the logged-in user
//            if (!document.getUploadedByUsername().equals(principal.getName())) {
//                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
//            }

            Map<String, Object> info = new HashMap<>();
            info.put("id", document.getId());
            info.put("title", document.getTitle());
            info.put("productCode", document.getProductCode());
            info.put("edition", document.getEdition());
//            info.put("publishDate", document.getPublishDate());
            info.put("publishDate", document.getPublishDate()); // ✅ String (YYYY or YYYY-MM)
            info.put("noOfPages", document.getNoOfPages());
            info.put("notes", document.getNotes());
            info.put("tagNames", document.getTagNames());
            return ResponseEntity.ok(info);
        } catch (Exception e) {
            logger.error("Failed to get document info", e);
            return ResponseEntity.notFound().build();
        }
    }


    //    Download with watermark(username and timestamp) -AJ
    @PreAuthorize("hasAnyAuthority('Manager', 'Admin')")
    @GetMapping("/documents/download/{id}")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable Long id, Principal principal) {
        String username = principal != null ? principal.getName() : "Unknown";

        try {
            DocumentDto document = documentService.findDocumentById(id);
            String filePath = documentService.getFilePath(id);
            logger.info("Downloading and watermarking document: {} for user: {}", document.getTitle(), username);

            // Read original PDF from network share
            byte[] originalPdfBytes = networkFileService.readFileFromNetworkShare(filePath);
            logger.info("Original PDF loaded, size: {} bytes", originalPdfBytes.length);

            // Add watermark
            byte[] watermarkedPdfBytes = watermarkService.addWatermarkToPdf(originalPdfBytes, username);

            // Prepare filename
            String filename = "WATERMARKED_" + document.getTitle() + ".pdf";

            logger.info("Downloaded PDF with watermark successfully, size: {} bytes", watermarkedPdfBytes.length);

            // LOG SUCCESS - DOCUMENT_DOWNLOAD
            activityLogService.logByUsername(
                    username,
                    ActivityLogService.DOCUMENT_DOWNLOAD,
                    String.format("Downloaded document: '%s' (Uploaded By: %s)",
                            document.getTitle(),
                            document.getUploadedByUsername())
            );

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(watermarkedPdfBytes.length))
                    .body(watermarkedPdfBytes);

        } catch (Exception e) {
            logger.error("Failed to download and watermark document", e);

            // LOG FAILURE - DOCUMENT_DOWNLOAD_FAILED
            activityLogService.logByUsername(
                    username,
                    ActivityLogService.DOCUMENT_DOWNLOAD_FAILED,
                    String.format("Failed to download document ID: %d - Error: %s", id, e.getMessage())
            );

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("Error downloading document: " + e.getMessage()).getBytes());
        }
    }

    //19.11.2025

//    @PreAuthorize("hasAnyAuthority('Manager', 'Admin')")
//    @GetMapping("/documents/download-watermarked/{id}")
//    public ResponseEntity<byte[]> downloadWatermarkedDocument(@PathVariable Long id, Principal principal) {
//        try {
//            DocumentDto document = documentService.findDocumentById(id);
//
//            String filePath = documentService.getFilePath(id);
//            logger.info("Downloading watermarked document from viewer: {} for user: {}",
//                    document.getTitle(), principal.getName());
//
//            // Read original PDF from network share
//            byte[] originalPdfBytes = networkFileService.readFileFromNetworkShare(filePath);
//
//            // Add watermark
//            byte[] watermarkedPdfBytes = watermarkService.addWatermarkToPdf(
//                    originalPdfBytes,
//                    principal.getName()
//            );
//
//            String watermarkedFilename = "WATERMARKED_" + document.getTitle() + ".pdf";
//
//            return ResponseEntity.ok()
//                    .contentType(MediaType.APPLICATION_PDF)
//                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + watermarkedFilename + "\"")
//                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(watermarkedPdfBytes.length))
//                    .body(watermarkedPdfBytes);
//
//        } catch (Exception e) {
//            logger.error("Failed to download watermarked document from viewer", e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .contentType(MediaType.TEXT_PLAIN)
//                    .body(("Error downloading document: " + e.getMessage()).getBytes());
//        }
//    }

    //Every user have their own bookmark -AJ
    @Autowired
    private BookmarkService bookmarkService;

    @PreAuthorize("hasAnyAuthority('Manager', 'Admin','Viewer')")
    @GetMapping("/my-bookmarks")
    public String showBookmarksPage(Principal principal) {
//        User user = userService.findUserByUsername(principal.getName());
//        model.addAttribute("user", user);
        return "bookmarks";
    }
// ================== BOOKMARK ENDPOINTS ==================

    //Only complete document can be bookmarked. It is not based on the any specific page in document -AJ
    @PreAuthorize("hasAnyAuthority('Manager', 'Admin','Viewer')")
    @PostMapping("/documents/bookmark/{id}")
    public ResponseEntity<?> saveBookmark(@PathVariable Long id,
//                                          @RequestParam int page,
                                          @RequestParam(required = false) String name,
                                          Principal principal) {
        try {
            User user = userService.findUserByUsername(principal.getName());

            // Generate bookmark name if not provided
//            String bookmarkName = (name != null && !name.isEmpty()) ? name : "Page " + page;
            String bookmarkName = (name != null && !name.isEmpty()) ? name : "Document Bookmark";

            // Save bookmark (now allows multiple per document)
            BookmarkDto bookmark = bookmarkService.saveBookmark(
                    user.getId(),
                    id,
//                    page,
                    bookmarkName
            );

//            logger.info("Bookmark saved for user: {} on document: {} at page: {}",
//                    principal.getName(), id, page);
            logger.info("Bookmark saved for user: {} on document: {}",
                    principal.getName(), id);

            return ResponseEntity.ok(bookmark);

        } catch (RuntimeException e) {
//            if (e.getMessage().contains("already bookmarked")) {
//                return ResponseEntity.status(HttpStatus.CONFLICT)
//                        .body(Map.of("error", "This page is already bookmarked"));
//            }
            if (e.getMessage().contains("already bookmarked")) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "This document is already bookmarked"));
            }
            logger.error("Error saving bookmark", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to save bookmark: " + e.getMessage()));
        }
    }

    @PreAuthorize("hasAnyAuthority('Manager', 'Admin','Viewer')")
    @GetMapping("/documents/bookmarks/{id}")
    public ResponseEntity<?> getDocumentBookmarks(@PathVariable Long id, Principal principal) {
        try {
            User user = userService.findUserByUsername(principal.getName());

            // Get ALL bookmarks for this document
            List<BookmarkDto> bookmarks = bookmarkService.getDocumentBookmarks(user.getId(), id);

            return ResponseEntity.ok(bookmarks);

        } catch (Exception e) {
            logger.error("Error retrieving document bookmarks", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve bookmarks"));
        }
    }

    //Cannot bookmark same document by the same user more than one time -AJ
    @GetMapping("/bookmark/check/{documentId}")
    public ResponseEntity<Boolean> checkIfBookmarked(@PathVariable Long documentId, Principal principal) {
        User user = userService.findUserByUsername(principal.getName());
        boolean isBookmarked = bookmarkService.isDocumentBookmarked(user.getId(), documentId);
        return ResponseEntity.ok(isBookmarked);
    }


    @PreAuthorize("hasAnyAuthority('Manager', 'Admin','Viewer')")
    @DeleteMapping("/bookmarks/document/{documentId}")
    public ResponseEntity<String> deleteBookmarkByDocument(
            @PathVariable Long documentId,
            Principal principal) {
        try {
            logger.info("Delete bookmark request for document: {} by user: {}", documentId, principal.getName());

            User user = userService.findUserByUsername(principal.getName());
            bookmarkService.deleteBookmarkByDocument(user.getId(), documentId);

            logger.info("Bookmark deleted successfully for document: {} by user: {}", documentId, principal.getName());
            return ResponseEntity.ok("Bookmark deleted successfully");

        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                logger.warn("Bookmark not found for document: {} by user: {}", documentId, principal.getName());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Bookmark not found");
            }
            logger.error("Error deleting bookmark for document: {} by user: {}", documentId, principal.getName(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to delete bookmark: " + e.getMessage());

        } catch (Exception e) {
            logger.error("Unexpected error deleting bookmark for document: {} by user: {}", documentId, principal.getName(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to delete bookmark: " + e.getMessage());
        }
    }

    @PreAuthorize("hasAnyAuthority('Manager', 'Admin','Viewer')")
    @DeleteMapping("/bookmarks/{id}")
    public ResponseEntity<String> deleteBookmark(@PathVariable Long id) {
        try {
            bookmarkService.deleteBookmark(id);
            return ResponseEntity.ok("Bookmark deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to delete bookmark: " + e.getMessage());
        }
    }

    @PreAuthorize("hasAnyAuthority('Manager', 'Admin','Viewer')")
    @GetMapping("/bookmarks")
    public ResponseEntity<?> getAllUserBookmarks(Principal principal) {
        try {
            User user = userService.findUserByUsername(principal.getName());

            List<BookmarkDto> bookmarks = bookmarkService.getUserBookmarks(user.getId());

            return ResponseEntity.ok(bookmarks);

        } catch (Exception e) {
            logger.error("Error retrieving user bookmarks", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve bookmarks"));
        }
    }

    @PreAuthorize("hasAnyAuthority('Manager', 'Admin', 'Viewer')")
    @PutMapping("/bookmarks/{id}")
    public ResponseEntity<?> updateBookmarkName(@PathVariable Long id,
                                                @RequestBody Map<String, String> payload) {
        try {
            String newName = payload.get("bookmarkName");
            if (newName == null || newName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Bookmark name cannot be empty"));
            }

            bookmarkService.updateBookmarkName(id, newName.trim());
            return ResponseEntity.ok(Map.of("message", "Bookmark name updated successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update bookmark: " + e.getMessage()));
        }
    }
}