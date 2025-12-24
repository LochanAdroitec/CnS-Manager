package codesAndStandards.springboot.userApp.interceptor;

import codesAndStandards.springboot.userApp.service.LicenseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class LicenseInterceptor implements HandlerInterceptor {

    @Autowired
    private LicenseService licenseService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        String uri = request.getRequestURI();

        // Skip license check for these endpoints
        if (uri.contains("/api/license/") ||
                uri.contains("/login") ||
                uri.contains("/register") ||
                uri.contains("/css/") ||
                uri.contains("/js/") ||
                uri.contains("/images/") ||
                uri.equals("/")) {
            return true; // Allow without license check
        }

        // ============================================================
        // STEP 1: Check if license is valid (for all API calls)
        // ============================================================
        if (uri.startsWith("/api/")) {

            if (!licenseService.isLicenseValid()) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");

                String errorMessage;
                if (licenseService.getCurrentLicense() == null) {
                    errorMessage = "{\"error\": \"No license found. Please activate a license.\", \"code\": \"LICENSE_NOT_FOUND\"}";
                } else {
                    long daysExpired = Math.abs(licenseService.getDaysRemaining());
                    errorMessage = "{\"error\": \"License has expired " + daysExpired + " days ago. Please renew your license.\", \"code\": \"LICENSE_EXPIRED\"}";
                }

                response.getWriter().write(errorMessage);
                return false; // Block the request
            }

            // ============================================================
            // STEP 2: Check EDITION for bulk upload endpoints (ED2 only)
            // ============================================================
            if (uri.startsWith("/api/bulk-upload/")) {

                // Check if bulk upload is allowed (ED2 edition only)
                if (!licenseService.isBulkUploadAllowed()) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");

                    String currentEdition = licenseService.getCurrentEdition();
                    long daysRemaining = licenseService.getDaysRemaining();

                    String errorMessage = String.format(
                            "{" +
                                    "\"error\": \"Bulk upload feature not available in your edition\", " +
                                    "\"code\": \"EDITION_UPGRADE_REQUIRED\", " +
                                    "\"currentEdition\": \"%s\", " +
                                    "\"requiredEdition\": \"ED2\", " +
                                    "\"daysRemaining\": %d, " +
                                    "\"message\": \"Please upgrade to ED2 Professional edition to use bulk upload feature. Contact your administrator.\"" +
                                    "}",
                            currentEdition != null ? currentEdition : "ED1",
                            daysRemaining
                    );

                    response.getWriter().write(errorMessage);
                    return false; // Block the request
                }
            }
        }

        // ============================================================
        // STEP 3: Check EDITION for bulk upload page view
        // ============================================================
        if (uri.equals("/bulk-upload")) {

            // First check if license is valid
            if (!licenseService.isLicenseValid()) {
                response.sendRedirect("/license-activation");
                return false;
            }

            // Then check if bulk upload is allowed (ED2 only)
            if (!licenseService.isBulkUploadAllowed()) {
                // Redirect to documents page with error message
                response.sendRedirect("/documents?error=Bulk+upload+feature+requires+ED2+Professional+edition");
                return false;
            }
        }

        return true; // Allow the request
    }
}