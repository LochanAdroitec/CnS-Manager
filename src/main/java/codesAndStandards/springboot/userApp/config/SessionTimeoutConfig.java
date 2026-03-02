package codesAndStandards.springboot.userApp.config;

import codesAndStandards.springboot.userApp.service.ApplicationSettingsService;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Dynamically sets HTTP session timeout based on database settings.
 * Reads session timeout from ApplicationSettings and applies it to each new session.
 */
@Component
public class SessionTimeoutConfig implements HttpSessionListener {

    private static final Logger logger = LoggerFactory.getLogger(SessionTimeoutConfig.class);

    @Autowired
    private ApplicationSettingsService applicationSettingsService;

    @Override
    public void sessionCreated(HttpSessionEvent event) {
        try {
            // Get session timeout from database settings as STRING (to handle decimals)
            String timeoutStr = applicationSettingsService.getSetting("session_timeout_hours");

            double timeoutHours = 8.0; // Default fallback

            if (timeoutStr != null && !timeoutStr.trim().isEmpty()) {
                try {
                    timeoutHours = Double.parseDouble(timeoutStr);

                    // Validate range
                    if (timeoutHours < 0.01 || timeoutHours > 72) {
                        logger.warn("⚠️ Invalid timeout: {} hours, using default 8 hours", timeoutHours);
                        timeoutHours = 8.0;
                    }
                } catch (NumberFormatException e) {
                    logger.error("❌ Error parsing timeout value '{}', using default 8 hours", timeoutStr);
                    timeoutHours = 8.0;
                }
            }

            // Convert hours to seconds
            int timeoutSeconds = (int) (timeoutHours * 60 * 60);

            // Set the session timeout
            event.getSession().setMaxInactiveInterval(timeoutSeconds);

            logger.info("✅ Session created with timeout: {} hours ({} seconds)", timeoutHours, timeoutSeconds);

        } catch (Exception e) {
            logger.error("❌ Error setting session timeout, using default: {}", e.getMessage());
            // Fallback to 8 hours if there's an error
            event.getSession().setMaxInactiveInterval(8 * 60 * 60);
        }
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        logger.debug("🔒 Session destroyed: {}", event.getSession().getId());
    }
}
