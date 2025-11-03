package codesAndStandards.springboot.userApp.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class WatermarkService {

    private static final Logger logger = LoggerFactory.getLogger(WatermarkService.class);


    public byte[] addWatermarkToPdf(byte[] pdfData, String username) throws IOException {
        logger.info("Adding watermark to PDF for user: {}", username);

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(pdfData);
             PDDocument document = PDDocument.load(inputStream);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            // Create watermark text with timestamp
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String mainWatermark = "CONFIDENTIAL - " + username;
            String footerWatermark = "Downloaded by: " + username + " on " + timestamp;

            logger.info("Processing {} pages", document.getNumberOfPages());

            // Add watermark to each page
            int pageNumber = 1;
            for (PDPage page : document.getPages()) {
                logger.debug("Adding watermark to page {}", pageNumber);
                addWatermarkToPage(document, page, mainWatermark, footerWatermark);
                pageNumber++;
            }

            document.save(outputStream);
            byte[] watermarkedPdf = outputStream.toByteArray();

            logger.info("Watermark added successfully. Original size: {} bytes, Watermarked size: {} bytes",
                    pdfData.length, watermarkedPdf.length);

            return watermarkedPdf;

        } catch (IOException e) {
            logger.error("Failed to add watermark to PDF", e);
            throw new IOException("Failed to add watermark: " + e.getMessage(), e);
        }
    }

    /**
     * Adds watermark to a single page
     */
    private void addWatermarkToPage(PDDocument document, PDPage page,
                                    String mainWatermark, String footerWatermark) throws IOException {

        PDPageContentStream contentStream = new PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true, true);

        try {
            // Set up graphics state for transparency
            PDExtendedGraphicsState graphicsState = new PDExtendedGraphicsState();
            graphicsState.setNonStrokingAlphaConstant(0.3f); // 30% opacity
            contentStream.setGraphicsStateParameters(graphicsState);

            // Get page dimensions
            float pageWidth = page.getMediaBox().getWidth();
            float pageHeight = page.getMediaBox().getHeight();

            // Set font and color for main diagonal watermark
            contentStream.setNonStrokingColor(Color.RED);
            contentStream.setFont(PDType1Font.HELVETICA_BOLD, 36);

            // Calculate text width for centering
            float textWidth = PDType1Font.HELVETICA_BOLD.getStringWidth(mainWatermark) / 1000 * 36;

            // Position for diagonal watermark (center)
            float x = (pageWidth - textWidth) / 2;
            float y = pageHeight / 2;

            // Add main diagonal watermark
            contentStream.beginText();
            Matrix matrix = new Matrix();
            matrix.translate(x, y);
            matrix.rotate(Math.toRadians(-45)); // 45-degree diagonal
            contentStream.setTextMatrix(matrix);
            contentStream.showText(mainWatermark);
            contentStream.endText();

            // Add header and footer watermarks
            addHeaderFooterWatermarks(contentStream, pageWidth, pageHeight, footerWatermark);

            // Add corner watermarks
            addCornerWatermarks(contentStream, pageWidth, pageHeight);

            // Add large faint center watermark
            addFaintCenterWatermark(contentStream, pageWidth, pageHeight);

        } finally {
            contentStream.close();
        }
    }

    /**
     * Adds header and footer watermarks
     */
    private void addHeaderFooterWatermarks(PDPageContentStream contentStream,
                                           float pageWidth, float pageHeight,
                                           String footerText) throws IOException {

        // Set font for header/footer
        contentStream.setFont(PDType1Font.HELVETICA, 10);
        contentStream.setNonStrokingColor(Color.DARK_GRAY);

        // Top header
//        contentStream.beginText();
//        contentStream.newLineAtOffset(50, pageHeight - 20);
//        contentStream.showText("CONFIDENTIAL DOCUMENT - DO NOT DISTRIBUTE");
//        contentStream.endText();

        // Bottom footer with download info
        contentStream.beginText();
        contentStream.newLineAtOffset(50, 10);
        contentStream.showText(footerText);
        contentStream.endText();
    }


    private void addCornerWatermarks(PDPageContentStream contentStream,
                                     float pageWidth, float pageHeight) throws IOException {

        // Set smaller font for corner watermarks
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 12);
        contentStream.setNonStrokingColor(Color.GRAY);

//        String cornerText = "CONFIDENTIAL";
//        float cornerTextWidth = PDType1Font.HELVETICA_BOLD.getStringWidth(cornerText) / 1000 * 12;
//
//        // Top-left corner
//        contentStream.beginText();
//        contentStream.newLineAtOffset(30, pageHeight - 40);
//        contentStream.showText(cornerText);
//        contentStream.endText();
//
//        // Top-right corner
//        contentStream.beginText();
//        contentStream.newLineAtOffset(pageWidth - cornerTextWidth - 30, pageHeight - 40);
//        contentStream.showText(cornerText);
//        contentStream.endText();
//
//        // Bottom-left corner
//        contentStream.beginText();
//        contentStream.newLineAtOffset(30, 30);
//        contentStream.showText(cornerText);
//        contentStream.endText();
//
//        // Bottom-right corner
//        contentStream.beginText();
//        contentStream.newLineAtOffset(pageWidth - cornerTextWidth - 30, 30);
//        contentStream.showText(cornerText);
//        contentStream.endText();
    }

    /**
     * Adds a large, very faint "DOWNLOAD COPY" watermark in the center
     */
    private void addFaintCenterWatermark(PDPageContentStream contentStream,
                                         float pageWidth, float pageHeight) throws IOException {

        // Create very faint graphics state
        PDExtendedGraphicsState faintState = new PDExtendedGraphicsState();
        faintState.setNonStrokingAlphaConstant(0.08f); // 8% opacity - very faint
        contentStream.setGraphicsStateParameters(faintState);

        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 80);
        contentStream.setNonStrokingColor(Color.LIGHT_GRAY);

        String centerText = "DOWNLOAD COPY";
        float centerTextWidth = PDType1Font.HELVETICA_BOLD.getStringWidth(centerText) / 1000 * 80;

        contentStream.beginText();
        Matrix centerMatrix = new Matrix();
        centerMatrix.translate((pageWidth - centerTextWidth) / 2, pageHeight / 2 - 150);
        centerMatrix.rotate(Math.toRadians(-45));
        contentStream.setTextMatrix(centerMatrix);
        contentStream.showText(centerText);
        contentStream.endText();
    }
}