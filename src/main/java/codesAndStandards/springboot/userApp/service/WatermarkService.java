package codesAndStandards.springboot.userApp.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class WatermarkService {

    private static final Logger logger = LoggerFactory.getLogger(WatermarkService.class);
    private static final float FONT_SIZE = 48f;
    private static final float FOOTER_FONT_SIZE = 12f;

    /**
     * ADD WATERMARK TO PDF WITH CUSTOM SETTINGS
     */
    public byte[] addWatermarkToPdf(byte[] pdfData, String username, Integer opacity, String position, Integer fontSize) throws IOException {
        logger.info("Adding watermark to PDF for user: {} (Opacity: {}%, Position: {}, FontSize: {}%)", username, opacity, position, fontSize);

        float calculatedFontSize = calculateFontSize(fontSize);
        PDDocument document = null;

        try {
            // =============== STEP 1: TRY LOAD PDF ===============
            try {
                document = PDDocument.load(pdfData);
            } catch (InvalidPasswordException e) {
                logger.error("PDF is password protected. Cannot watermark without password.");
                throw new IOException("Cannot decrypt PDF, the password is incorrect");
            }

            // =============== STEP 2: REMOVE ALL SECURITY ===============
            document.setAllSecurityToBeRemoved(true);

            // =============== STEP 3: PREPARE WATERMARK TEXT ===============
            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss"));

            String line1 = username;                          // Line 1: username only
            String line2 = timestamp.split(" ")[0];           // Line 2: date only (dd-MMM-yyyy)
            String footerText = "Downloaded by: " + username + " on " + timestamp;

            // Convert opacity percentage to alpha (0.0 - 1.0)
            float alpha = calculateAlpha(opacity);

            logger.info("Processing {} pages with watermark: '{}' / '{}'", document.getNumberOfPages(), line1, line2);

            int pageNumber = 1;
            for (PDPage page : document.getPages()) {
                logger.debug("Adding watermark to page {}", pageNumber);

                // Apply main watermark based on position
                addWatermarkToPage(document, page, line1, line2, position, alpha, calculatedFontSize);

                // Always add static footer
                addStaticFooter(document, page, footerText);

                pageNumber++;
            }

            // =============== STEP 4: SAVE PDF ===============
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);

            logger.info("Watermark applied successfully to {} pages", pageNumber - 1);
            return outputStream.toByteArray();

        } finally {
            if (document != null) {
                document.close();
            }
        }
    }

    /**
     * BACKWARD COMPATIBILITY: Add watermark with default settings
     */
    public byte[] addWatermarkToPdf(byte[] pdfData, String username) throws IOException {
        return addWatermarkToPdf(pdfData, username, 30, "Diagonal", 100);
    }

    /**
     * Calculate alpha value from opacity percentage
     */
    private float calculateAlpha(Integer opacity) {
        if (opacity == null || opacity < 0) {
            return 0.3f;
        }
        if (opacity > 100) {
            return 1.0f;
        }
        return opacity / 100.0f;
    }

    /**
     * APPLY MAIN WATERMARK TO A SINGLE PAGE - TWO LINES
     */
    private void addWatermarkToPage(PDDocument document, PDPage page,
                                    String line1, String line2, String position,
                                    float alpha, float fontSize) throws IOException {

        PDPageContentStream cs = new PDPageContentStream(
                document, page,
                PDPageContentStream.AppendMode.APPEND,
                true,
                true
        );

        try {
            // Set transparency
            PDExtendedGraphicsState graphicsState = new PDExtendedGraphicsState();
            graphicsState.setNonStrokingAlphaConstant(alpha);
            cs.setGraphicsStateParameters(graphicsState);

            float pageWidth = page.getMediaBox().getWidth();
            float pageHeight = page.getMediaBox().getHeight();

            // Set font and color
            cs.setNonStrokingColor(Color.RED);
            cs.setFont(PDType1Font.HELVETICA_BOLD, fontSize);

            // Apply watermark based on position
            applyWatermarkPosition(cs, line1, line2, position, pageWidth, pageHeight, fontSize);

        } finally {
            cs.close();
        }
    }

    /**
     * ADD STATIC FOOTER TO EVERY PAGE (ALWAYS SHOWN)
     */
    private void addStaticFooter(PDDocument document, PDPage page, String footerText) throws IOException {

        PDPageContentStream cs = new PDPageContentStream(
                document, page,
                PDPageContentStream.AppendMode.APPEND,
                true,
                true
        );

        try {
            cs.setNonStrokingColor(Color.DARK_GRAY);
            cs.setFont(PDType1Font.HELVETICA, FOOTER_FONT_SIZE);

            float marginX = 30f;
            float marginY = 20f;

            cs.beginText();
            cs.newLineAtOffset(marginX, marginY);
            cs.showText(footerText);
            cs.endText();

            logger.debug("Added static footer: {}", footerText);

        } finally {
            cs.close();
        }
    }

    /**
     * APPLY WATERMARK AT SPECIFIED POSITION - TWO LINES
     */
    private void applyWatermarkPosition(PDPageContentStream cs, String line1, String line2,
                                        String position, float pageWidth,
                                        float pageHeight, float fontSize) throws IOException {

        String pos = (position != null) ? position : "Diagonal";

        float textWidth1 = PDType1Font.HELVETICA_BOLD.getStringWidth(line1) / 1000 * fontSize;
        float textWidth2 = PDType1Font.HELVETICA_BOLD.getStringWidth(line2) / 1000 * fontSize;
        float maxTextWidth = Math.max(textWidth1, textWidth2);
        float lineSpacing = fontSize * 1.4f;
        float marginX = 50;
        float marginY = 50;

        switch (pos) {
            case "Diagonal":
                applyDiagonalWatermark(cs, line1, line2, pageWidth, pageHeight, fontSize);
                break;

            case "TopLeft":
                applyTwoLineWatermark(cs, line1, line2,
                        marginX,
                        pageHeight - marginY,
                        lineSpacing);
                break;

            case "TopRight":
                applyTwoLineWatermark(cs, line1, line2,
                        pageWidth - maxTextWidth - marginX,
                        pageHeight - marginY,
                        lineSpacing);
                break;

            case "BottomLeft":
                applyTwoLineWatermark(cs, line1, line2,
                        marginX,
                        marginY + lineSpacing,
                        lineSpacing);
                break;

            case "BottomRight":
                applyTwoLineWatermark(cs, line1, line2,
                        pageWidth - maxTextWidth - marginX,
                        marginY + lineSpacing,
                        lineSpacing);
                break;

            case "Center":
                float centerX1 = (pageWidth - textWidth1) / 2;
                float centerX2 = (pageWidth - textWidth2) / 2;
                applyTwoLineWatermarkXY(cs, line1, line2,
                        centerX1, pageHeight / 2 + lineSpacing / 2,
                        centerX2, pageHeight / 2 - lineSpacing / 2);
                break;

            default:
                logger.warn("Unknown watermark position: {}, using Diagonal", position);
                applyDiagonalWatermark(cs, line1, line2, pageWidth, pageHeight, fontSize);
        }
    }

    /**
     * TWO-LINE WATERMARK - SAME X, STACKED VERTICALLY
     * Used for: TopLeft, TopRight, BottomLeft, BottomRight
     */
    private void applyTwoLineWatermark(PDPageContentStream cs, String line1, String line2,
                                       float x, float y, float lineSpacing) throws IOException {
        // Line 1
        cs.beginText();
        cs.newLineAtOffset(x, y);
        cs.showText(line1);
        cs.endText();

        // Line 2
        cs.beginText();
        cs.newLineAtOffset(x, y - lineSpacing);
        cs.showText(line2);
        cs.endText();
    }

    /**
     * TWO-LINE WATERMARK - INDEPENDENT X/Y PER LINE
     * Used for: Center (each line independently centered)
     */
    private void applyTwoLineWatermarkXY(PDPageContentStream cs, String line1, String line2,
                                         float x1, float y1, float x2, float y2) throws IOException {
        // Line 1
        cs.beginText();
        cs.newLineAtOffset(x1, y1);
        cs.showText(line1);
        cs.endText();

        // Line 2
        cs.beginText();
        cs.newLineAtOffset(x2, y2);
        cs.showText(line2);
        cs.endText();
    }

    /**
     * DIAGONAL - TWO LINES, BOTH ROTATED 45°, CENTERED ON PAGE
     */
    private void applyDiagonalWatermark(PDPageContentStream cs, String line1, String line2,
                                        float pageWidth, float pageHeight, float fontSize) throws IOException {

        float textWidth1 = PDType1Font.HELVETICA_BOLD.getStringWidth(line1) / 1000 * fontSize;
        float textWidth2 = PDType1Font.HELVETICA_BOLD.getStringWidth(line2) / 1000 * fontSize;
        float lineSpacing = fontSize * 1.4f;
        float centerX = pageWidth / 2;
        float centerY = pageHeight / 2;

        // Line 1 (username) - above center
        cs.beginText();
        Matrix matrix1 = new Matrix();
        matrix1.translate(centerX, centerY + lineSpacing / 2);
        matrix1.rotate(Math.toRadians(45));
        matrix1.translate(-textWidth1 / 2, 0);
        cs.setTextMatrix(matrix1);
        cs.showText(line1);
        cs.endText();

        // Line 2 (date) - below center
        cs.beginText();
        Matrix matrix2 = new Matrix();
        matrix2.translate(centerX, centerY - lineSpacing / 2);
        matrix2.rotate(Math.toRadians(45));
        matrix2.translate(-textWidth2 / 2, 0);
        cs.setTextMatrix(matrix2);
        cs.showText(line2);
        cs.endText();

        logger.debug("Applied two-line diagonal watermark at ({}, {})", centerX, centerY);
    }

    /**
     * Calculate actual font size (allowed range: 12–72)
     */
    private float calculateFontSize(Integer fontSize) {
        if (fontSize == null) {
            return FONT_SIZE;
        }
        if (fontSize < 12) {
            return 12f;
        }
        if (fontSize > 72) {
            return 72f;
        }
        return fontSize.floatValue();
    }
}