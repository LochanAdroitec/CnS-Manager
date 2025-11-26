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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class WatermarkService {

    private static final Logger logger = LoggerFactory.getLogger(WatermarkService.class);

    /**
     * ADD WATERMARK TO PDF
     * Works for:
     *    - Normal PDFs
     *    - Encrypted PDFs (but throws meaningful error)
     */
    public byte[] addWatermarkToPdf(byte[] pdfData, String username) throws IOException {
        logger.info("Adding watermark to PDF for user: {}", username);

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
            // (This is WHY watermarking works after decryption)
            document.setAllSecurityToBeRemoved(true);

            // =============== STEP 3: PREPARE WATERMARK TEXT ===============
            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            String mainWatermark = "CONFIDENTIAL - " + username;
            String footerWatermark = "Downloaded by: " + username + " on " + timestamp;

            logger.info("Processing {} pages", document.getNumberOfPages());

            int pageNumber = 1;
            for (PDPage page : document.getPages()) {
                logger.debug("Adding watermark to page {}", pageNumber);
                addWatermarksToPage(document, page, mainWatermark, footerWatermark);
                pageNumber++;
            }

            // =============== STEP 4: SAVE PDF ===============
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            return outputStream.toByteArray();

        } finally {
            if (document != null) {
                document.close();
            }
        }
    }

    /**
     * APPLY ALL WATERMARKS TO A SINGLE PAGE
     */
    private void addWatermarksToPage(PDDocument document, PDPage page,
                                     String mainWatermark, String footerWatermark) throws IOException {

        PDPageContentStream cs = new PDPageContentStream(
                document, page,
                PDPageContentStream.AppendMode.APPEND,
                true,
                true
        );

        try {
            // Transparency (alpha)
            PDExtendedGraphicsState alpha = new PDExtendedGraphicsState();
            alpha.setNonStrokingAlphaConstant(0.3f);
            cs.setGraphicsStateParameters(alpha);

            float pageWidth = page.getMediaBox().getWidth();
            float pageHeight = page.getMediaBox().getHeight();

            // =============== MAIN DIAGONAL WATERMARK ===============
            cs.setNonStrokingColor(Color.RED);
            cs.setFont(PDType1Font.HELVETICA_BOLD, 36);

            float textWidth = PDType1Font.HELVETICA_BOLD
                    .getStringWidth(mainWatermark) / 1000 * 36;

            float x = (pageWidth - textWidth) / 2;
            float y = pageHeight / 2;

            cs.beginText();
            Matrix matrix = new Matrix();
            matrix.translate(x, y);
            matrix.rotate(Math.toRadians(-45));
            cs.setTextMatrix(matrix);
            cs.showText(mainWatermark);
            cs.endText();

            // =============== FOOTER INFO WATERMARK ===============
            addFooter(cs, footerWatermark);

            // =============== LARGE FAINT CENTER WATERMARK ===============
            addCenterFaintMark(cs, pageWidth, pageHeight);

        } finally {
            cs.close();
        }
    }

    /**
     * SMALL FOOTER TEXT
     */
    private void addFooter(PDPageContentStream cs, String footerText) throws IOException {
        cs.setFont(PDType1Font.HELVETICA, 10);
        cs.setNonStrokingColor(Color.DARK_GRAY);

        cs.beginText();
        cs.newLineAtOffset(50, 10);
        cs.showText(footerText);
        cs.endText();
    }

    /**
     * LARGE, VERY FAINT "DOWNLOAD COPY"
     */
    private void addCenterFaintMark(PDPageContentStream cs,
                                    float pageWidth, float pageHeight) throws IOException {

        PDExtendedGraphicsState faintAlpha = new PDExtendedGraphicsState();
        faintAlpha.setNonStrokingAlphaConstant(0.08f);
        cs.setGraphicsStateParameters(faintAlpha);

        cs.setFont(PDType1Font.HELVETICA_BOLD, 80);
        cs.setNonStrokingColor(Color.LIGHT_GRAY);

        String text = "DOWNLOAD COPY";
        float textWidth = PDType1Font.HELVETICA_BOLD
                .getStringWidth(text) / 1000 * 80;

        cs.beginText();
        Matrix centerMatrix = new Matrix();
        centerMatrix.translate((pageWidth - textWidth) / 2, pageHeight / 2 - 150);
        centerMatrix.rotate(Math.toRadians(-45));
        cs.setTextMatrix(centerMatrix);
        cs.showText(text);
        cs.endText();
    }
}
