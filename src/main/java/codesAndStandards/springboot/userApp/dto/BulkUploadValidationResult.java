package codesAndStandards.springboot.userApp.dto;


import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BulkUploadValidationResult {
    private int totalDocuments;
    private int validDocuments;
    private List<ValidationIssue> errors;
    private List<ValidationIssue> warnings;

    public BulkUploadValidationResult() {
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
    }

    public void addError(String title, String description) {
        ValidationIssue issue = new ValidationIssue();
        issue.setTitle(title);
        issue.setDescription(description);
        issue.setType("error");
        this.errors.add(issue);
    }

    public void addWarning(String title, String description) {
        ValidationIssue issue = new ValidationIssue();
        issue.setTitle(title);
        issue.setDescription(description);
        issue.setType("warning");
        this.warnings.add(issue);
    }

    @Data
    public static class ValidationIssue {
        private String title;
        private String description;
        private String type; // error or warning
    }
}
