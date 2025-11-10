package codesAndStandards.springboot.userApp.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TagDto {

    private Long id;
    private String tagName;

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