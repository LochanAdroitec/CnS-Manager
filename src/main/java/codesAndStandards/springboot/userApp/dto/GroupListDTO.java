package codesAndStandards.springboot.userApp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupListDTO {
    private Long id;
    private String groupName;
    private String description;
    private String createdByUsername;
    private LocalDateTime createdAt;
    private Integer documentCount;
    private Integer userCount;
}
