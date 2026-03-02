package codesAndStandards.springboot.userApp.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * DTO for transferring application settings data
 * Contains only Repository settings for now (other sections to be added later)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationSettingsDto {

    // Repository Settings
    private String repositoryPath;  // Read-only from application.properties
    private Integer maxFileSizeMb;
    private String allowedFiles;  // Comma-separated: "PDF,DOC,DOCX"

    // Metadata
    private LocalDateTime updatedAt;
    private String updatedByUsername;

    // Helper: Get allowed formats as array
    public String[] getAllowedFormatsArray() {
        if (allowedFiles == null || allowedFiles.trim().isEmpty()) {
            return new String[0];
        }
        return allowedFiles.split(",");
    }

    // Helper: Check if a format is allowed
    public boolean isFormatAllowed(String format) {
        if (allowedFiles == null || format == null) {
            return false;
        }
        String[] formats = getAllowedFormatsArray();
        for (String f : formats) {
            if (f.trim().equalsIgnoreCase(format.trim())) {
                return true;
            }
        }
        return false;
    }
}
