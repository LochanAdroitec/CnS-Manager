package codesAndStandards.springboot.userApp.dto;

import lombok.Data;

@Data
public class DocumentMetadata {
    private String filename;
    private String title;
    private String productCode;
    private String edition;
    private String publishMonth;
    private String publishYear;
    private Integer noOfPages;
    private String notes;
    private String tags;              // Comma-separated tag names
    private String classifications;    // Comma-separated classification names
}