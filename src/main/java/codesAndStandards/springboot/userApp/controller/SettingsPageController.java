package codesAndStandards.springboot.userApp.controller;

import codesAndStandards.springboot.userApp.service.LicenseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * MVC Controller for rendering the Settings page
 * Admin-only access
 */
@Controller
@PreAuthorize("hasAuthority('Admin')")
public class SettingsPageController {

    private static final Logger logger = LoggerFactory.getLogger(SettingsPageController.class);

    @Autowired
    private LicenseService licenseService;

    /**
     * GET /settings - Render settings page
     */
    @GetMapping("/settings")
    public String showSettingsPage(Model model) {
        try {
            // Check license
            if (!licenseService.isLicenseValid()) {
                logger.warn("❌ License invalid - redirecting to license page");
                model.addAttribute("error", "Invalid or expired license");
                return "redirect:/license-error";  // Redirect to license error page
            }

            logger.info("✅ Rendering settings page");
            return "settings";  // Returns settings.html

        } catch (Exception e) {
            logger.error("❌ Error rendering settings page: {}", e.getMessage(), e);
            model.addAttribute("error", "Failed to load settings page");
            return "error";
        }
    }
}
