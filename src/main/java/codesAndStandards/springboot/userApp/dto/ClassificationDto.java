package codesAndStandards.springboot.userApp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClassificationDto {

    private Long id;

    @NotBlank(message = "Classification name is required")
    @Size(min = 2, max = 100, message = "Classification name must be between 2 and 100 characters")
    private String classificationName;

    // Created by information
    private Long createdBy;
    private String createdByUsername;
    private LocalDateTime createdAt;

    // Updated by information
    private Long updatedBy;
    private String updatedByUsername;
    private LocalDateTime updatedAt;

    private Integer documentCount;
}