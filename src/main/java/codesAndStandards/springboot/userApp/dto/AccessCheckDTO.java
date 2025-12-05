package codesAndStandards.springboot.userApp.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccessCheckDTO {
    private boolean hasAccess;
    private List<String> groupNames;
    private String message;
}