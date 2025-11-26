package codesAndStandards.springboot.userApp.dto;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Utility class to generate Excel template for bulk upload
 *
 * Excel Template Structure:
 * -------------------------
 * Column A: filename (REQUIRED) - e.g., "document1.pdf"
 * Column B: title (REQUIRED) - e.g., "Product Manual v2.1"
 * Column C: productCode (REQUIRED) - e.g., "PM-001"
 * Column D: edition (OPTIONAL) - e.g., "1.0", "2.1"
 * Column E: publishMonth (OPTIONAL) - e.g., "01" to "12"
 * Column F: publishYear (REQUIRED) - e.g., "2024"
 * Column G: noOfPages (OPTIONAL) - e.g., 150
 * Column H: notes (OPTIONAL) - e.g., "Updated version with new specifications"
 * Column I: tags (OPTIONAL) - e.g., "manual,technical,v2" (comma-separated)
 * Column J: classifications (OPTIONAL) - e.g., "Engineering,Safety" (comma-separated)
 */
public class BulkUploadTemplateGenerator {

    public static void generateTemplate(String outputPath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Documents");

            // Create header row
            Row headerRow = sheet.createRow(0);

            // Create header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 12);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            // Define headers
            String[] headers = {
//                    "Filename *",
                    "Title *",
                    "Product Code *",
                    "Edition",
                    "Publish Month",
                    "Publish Year *",
                    "No. of Pages",
                    "Notes",
                    "Tags (comma-separated)",
                    "Classifications (comma-separated)"
            };

            // Create header cells
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 4000); // Set column width
            }

            // Create example row
            Row exampleRow = sheet.createRow(1);
            String[] exampleData = {
//                    "document1.pdf",
                    "Product Manual v2.1",
                    "PM-001",
                    "2.1",
                    "06",
                    "2024",
                    "150",
                    "Updated version with new specifications",
                    "manual,technical,v2",
                    "Engineering,Safety"
            };

            CellStyle exampleStyle = workbook.createCellStyle();
            Font exampleFont = workbook.createFont();
            exampleFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            exampleFont.setItalic(true);
            exampleStyle.setFont(exampleFont);

            for (int i = 0; i < exampleData.length; i++) {
                Cell cell = exampleRow.createCell(i);
                cell.setCellValue(exampleData[i]);
                cell.setCellStyle(exampleStyle);
            }

            // Add instructions sheet
            Sheet instructionsSheet = workbook.createSheet("Instructions");
            Row instructionRow = instructionsSheet.createRow(0);
            instructionRow.createCell(0).setCellValue("BULK UPLOAD INSTRUCTIONS");

            int rowNum = 2;
            String[] instructions = {
                    "1. Fill in the 'Documents' sheet with your document information",
                    "2. Fields marked with * are REQUIRED",
                    "3. Filename should match exactly with the PDF filename you're uploading",
                    "4. Publish Month should be 01-12 (01=January, 12=December)",
                    "5. Tags and Classifications should be comma-separated (e.g., 'tag1,tag2,tag3')",
                    "6. Remove the example row before uploading",
                    "7. You can upload multiple PDF files or a single ZIP file containing all PDFs",
                    "8. Make sure all filenames in Excel match the PDF files you're uploading",
                    "",
                    "FIELD DESCRIPTIONS:",
                    "- Filename: Exact name of the PDF file (e.g., 'document1.pdf')",
                    "- Title: Document title",
                    "- Product Code: Unique product code",
                    "- Edition: Version/edition of the document (optional)",
                    "- Publish Month: Month of publication (01-12, optional)",
                    "- Publish Year: Year of publication (required)",
                    "- No. of Pages: Number of pages in the document (optional)",
                    "- Notes: Additional notes or description (optional)",
                    "- Tags: Comma-separated tags (optional)",
                    "- Classifications: Comma-separated classifications (optional)"
            };

            for (String instruction : instructions) {
                Row row = instructionsSheet.createRow(rowNum++);
                row.createCell(0).setCellValue(instruction);
            }

            instructionsSheet.setColumnWidth(0, 15000);

            // Write to file
            try (FileOutputStream fileOut = new FileOutputStream(outputPath)) {
                workbook.write(fileOut);
            }
        }
    }

    public static void main(String[] args) {
        try {
            generateTemplate("bulk-upload-template.xlsx");
            System.out.println("Template generated successfully!");
        } catch (IOException e) {
            System.err.println("Error generating template: " + e.getMessage());
        }
    }
}