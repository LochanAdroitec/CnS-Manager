package codesAndStandards.springboot.userApp.repository;

import codesAndStandards.springboot.userApp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface  UserRepository extends JpaRepository<User, Long> {  //changed from Long to Integer

    // Primary method for login - returns User directly for backward compatibility
    User findByUsername(String username);

    // Optional wrapper version if needed for null safety
    Optional<User> findOptionalByUsername(String username);

    // Check if username exists during registration
    boolean existsByUsername(String username);

    // Email lookup (optional, if needed for password reset or validation)
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query(value = "SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END FROM users WHERE username = :username", nativeQuery = true)
    int existsByUsernameNative(@Param("username") String username);

    /**
     * Check if email exists - using native query
     */
    @Query(value = "SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END FROM users WHERE email = :email", nativeQuery = true)
    int existsByEmailNative(@Param("email") String email);

}