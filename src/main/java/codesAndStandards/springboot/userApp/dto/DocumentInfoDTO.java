package codesAndStandards.springboot.userApp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentInfoDTO {
    private Long id;
    private String title;
    private String documentCode;
    private String category;
}