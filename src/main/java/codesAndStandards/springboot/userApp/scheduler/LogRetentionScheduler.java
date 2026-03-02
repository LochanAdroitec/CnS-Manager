package codesAndStandards.springboot.userApp.scheduler;

import codesAndStandards.springboot.userApp.repository.ActivityLogRepository;
import codesAndStandards.springboot.userApp.service.ApplicationSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Scheduled service for automatic activity log cleanup based on retention policy.
 * Runs daily at 2:00 AM to delete logs older than the configured retention period.
 */
@Component
public class LogRetentionScheduler {

    private static final Logger logger = LoggerFactory.getLogger(LogRetentionScheduler.class);

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private ApplicationSettingsService applicationSettingsService;

    /**
     * Scheduled job that runs every day
     * Cron format: "second minute hour day month weekday"
     * "0 0 2 * * ?" = At 02:00:00 AM every day
     */
    @Scheduled(cron = "0 0 */1 * * ?")
    @Transactional
    public void cleanupOldLogs() {
        logger.info("🕐 Log retention scheduler started");

        try {
            // Check if activity logging is enabled
            Boolean loggingEnabled = applicationSettingsService.isActivityLoggingEnabled();

            if (!Boolean.TRUE.equals(loggingEnabled)) {
                logger.info("⏭️ Log retention skipped - activity logging is disabled");
                return;
            }

            // Get retention period in days
            String timeoutStr = applicationSettingsService.getSetting("log_retention_days");

            Integer retentionDays = 90; // Default fallback

            if (timeoutStr != null && !timeoutStr.trim().isEmpty()) {
                try {
                    retentionDays = Integer.parseInt(timeoutStr);

                    if (retentionDays < 1 || retentionDays > 3650) {
                        logger.warn("⚠️ Invalid retention days: {}, using default 90", retentionDays);
                        retentionDays = 90;
                    }
                } catch (NumberFormatException e) {
                    logger.error("❌ Error parsing retention days '{}', using default 90", timeoutStr);
                }
            }

            // Calculate cutoff date
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);

            logger.info("🧹 Starting log cleanup - Retention: {} days, Cutoff date: {}",
                    retentionDays, cutoffDate);

            // Delete old logs
            int deletedCount = activityLogRepository.deleteOldLogs(cutoffDate);

            if (deletedCount > 0) {
                logger.info("✅ Successfully deleted {} activity log(s) older than {} days",
                        deletedCount, retentionDays);
            } else {
                logger.info("✅ No logs found older than {} days", retentionDays);
            }

        } catch (Exception e) {
            logger.error("❌ Error during log retention cleanup: {}", e.getMessage(), e);
        }
    }
}