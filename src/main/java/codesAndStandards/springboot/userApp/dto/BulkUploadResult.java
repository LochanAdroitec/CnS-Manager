package codesAndStandards.springboot.userApp.dto;


import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BulkUploadResult {
    private int successCount;
    private int failedCount;
    private int totalProcessed;
    private List<String> successfulUploads;
    private List<String> failedUploads;
    private List<String> errors;

    public BulkUploadResult() {
        this.successfulUploads = new ArrayList<>();
        this.failedUploads = new ArrayList<>();
        this.errors = new ArrayList<>();
    }

    public void addSuccess(String filename) {
        this.successfulUploads.add(filename);
        this.successCount++;
        this.totalProcessed++;
    }

    public void addFailure(String filename, String reason) {
        this.failedUploads.add(filename + " - " + reason);
        this.failedCount++;
        this.totalProcessed++;
    }

    public void addError(String error) {
        this.errors.add(error);
    }
}
