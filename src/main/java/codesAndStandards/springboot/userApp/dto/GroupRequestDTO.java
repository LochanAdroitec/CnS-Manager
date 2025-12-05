package codesAndStandards.springboot.userApp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for creating/updating groups
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupRequestDTO {
    private String groupName;
    private String description;
    private List<Long> documentIds;
    private List<Long> userIds;
}