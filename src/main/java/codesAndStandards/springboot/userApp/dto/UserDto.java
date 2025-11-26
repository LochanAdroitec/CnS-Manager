package codesAndStandards.springboot.userApp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {

    private Long id;

    @NotEmpty(message = "First name should not be empty")
    private String firstName;

    @NotEmpty(message = "Last name should not be empty")
    private String lastName;

    @NotEmpty(message = "Username should not be empty")
    private String username;

    @NotEmpty(message = "Email should not be empty")
    @Email
    private String email;

//    @NotEmpty(message = "Password should not be empty")
    private String password;

    // Only store role_id from frontend
    private Long roleId;
    private String roleName;

    private String createdByUsername; // Who created the user
    private String createdAt;         // Timestamp in string format

    private String currentPassword;
    private String newPassword;
    private String confirmPassword;
}
