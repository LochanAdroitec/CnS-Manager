package codesAndStandards.springboot.userApp.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDto {

    private Long id;

    @NotEmpty(message = "File name should not be empty")
    private String title;

    @NotEmpty(message = "Product code should not be empty")
    private String productCode;

    private String edition;
    private String publishDate;
    private Integer noOfPages;
    private String notes;

    private String filePath;
    private String uploadedAt;
    private Long uploadedByUserId;
    private String uploadedByUsername;

    //    private Long uploadedByUserId;
//      private String filePath;
    // Tags and Classifications as lists
    private List<TagDto> tags;
    private List<ClassificationDto> classifications;

    // For form submission (comma-separated IDs or names)
    private String tagIds;
    private String classificationIds;

    private String tagNames;           // Comma-separated tag names
    private String classificationNames; // Comma-separated classification names

    // ✅ Add these (needed for dropdown binding in Thymeleaf)
    private String publishMonth;  // Example "01", "02", "12"
    private String publishYear;   // Example "2024"
}