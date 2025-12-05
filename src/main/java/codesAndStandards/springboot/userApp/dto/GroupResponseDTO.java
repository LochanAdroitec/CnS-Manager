package codesAndStandards.springboot.userApp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupResponseDTO {
    private Long id;
    private String groupName;
    private String description;
    private Long createdById;
    private String createdByUsername;
    private LocalDateTime createdAt;
    private Integer documentCount;
    private Integer userCount;
    private List<Long> documentIds;
    private List<Long> userIds;
    private List<DocumentInfoDTO> documents;
    private List<UserInfoDTO> users;
}
