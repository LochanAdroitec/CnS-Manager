package codesAndStandards.springboot.userApp.service;

import codesAndStandards.springboot.userApp.dto.UserDto;
import codesAndStandards.springboot.userApp.entity.User;

import java.util.List;
import java.util.Optional;

public interface UserService {

    void saveUser(UserDto userDto);

    List<UserDto> findAllUsers();

    User findUserByUsername(String username);

    UserDto findUserById(Long userId);

    boolean doesUserExist(Long userId);

    void editUser(UserDto updatedUserDto, Long userId);

    void deleteUserById(Long userId);

    // Methods using stored procedures
    void saveUserWithStoredProcedure(UserDto userDto) throws RuntimeException;

    // Add these two new methods to the interface
    void editUserByAdminWithStoredProcedure(String username, UserDto userDto) throws RuntimeException;
    void editUserProfileWithStoredProcedure(String username, UserDto userDto) throws RuntimeException;

    void deleteUserWithStoredProcedure(String username) throws RuntimeException;

    boolean existsByUsername(String username);

    /**
     * Check if email exists
     */
    boolean existsByEmail(String email);
    Optional<User> findById(Long id);
}