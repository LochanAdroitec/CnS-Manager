package codesAndStandards.springboot.userApp.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class BulkUploadResult {

    private int successCount;
    private int failedCount;
    private int totalProcessed;

    // key = filename, value = document title
    private Map<String, String> successfulUploads;

    private List<String> failedUploads;
    private List<String> errors;

    public BulkUploadResult() {
        this.successfulUploads = new LinkedHashMap<>();
        this.failedUploads = new ArrayList<>();
        this.errors = new ArrayList<>();
    }

    // ✅ Store filename + title
    public void addSuccess(String filename, String title) {
        this.successfulUploads.put(filename, title);
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
