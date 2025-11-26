package codesAndStandards.springboot.userApp.service;

import codesAndStandards.springboot.userApp.entity.ActivityLog;
import codesAndStandards.springboot.userApp.entity.User;
import codesAndStandards.springboot.userApp.repository.ActivityLogRepository;
import codesAndStandards.springboot.userApp.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class ActivityLogService {

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private UserRepository userRepository;

    // Action constants
    public static final String LOGIN = "LOGIN";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String LOGOUT = "LOGOUT";
    public static final String USER_CREATE = "USER_CREATE";
    public static final String USER_CREATE_FAILED = "USER_CREATE_FAILED";
    public static final String USER_EDIT = "USER_EDIT";
    public static final String USER_EDIT_FAILED = "USER_EDIT_FAILED";
    public static final String EDIT_PROFILE = "EDIT_PROFILE";
    public static final String EDIT_PROFILE_FAILED = "EDIT_PROFILE_FAILED";
    public static final String USER_DELETE = "USER_DELETE";
    public static final String USER_DELETE_FAILED = "USER_DELETE_FAILED";

    public static final String DOCUMENT_UPLOAD = "DOCUMENT_UPLOAD";
    public static final String DOCUMENT_UPLOAD_FAILED = "DOCUMENT_UPLOAD_FAILED";
    public static final String DOCUMENT_EDIT = "DOCUMENT_EDIT";
    public static final String DOCUMENT_EDIT_FAILED = "DOCUMENT_EDIT_FAILED";
    public static final String DOCUMENT_DELETE = "DOCUMENT_DELETE";
    public static final String DOCUMENT_DELETE_FAILED = "DOCUMENT_DELETE_FAILED";
    public static final String DOCUMENT_DOWNLOAD = "DOCUMENT_DOWNLOAD";
    public static final String DOCUMENT_DOWNLOAD_FAILED = "DOCUMENT_DOWNLOAD_FAILED";

    public static final String TAG_ADD = "TAG_ADD";
    public static final String TAG_EDIT = "TAG_EDIT";
    public static final String TAG_DELETE = "TAG_DELETE";

    public static final String TAG_ADD_FAIL = "TAG_ADD_FAIL";
    public static final String TAG_EDIT_FAIL = "TAG_EDIT_FAIL";
    public static final String TAG_DELETE_FAIL = "TAG_DELETE_FAIL";

    public static final String CLASSIFICATION_ADD = "CLASSIFICATION_ADD";
    public static final String CLASSIFICATION_EDIT = "CLASSIFICATION_EDIT";
    public static final String CLASSIFICATION_DELETE = "CLASSIFICATION_DELETE";

    public static final String CLASSIFICATION_ADD_FAIL = "CLASSIFICATION_ADD_FAIL";
    public static final String CLASSIFICATION_EDIT_FAIL = "CLASSIFICATION_EDIT_FAIL";
    public static final String CLASSIFICATION_DELETE_FAIL = "CLASSIFICATION_DELETE_FAIL";

    public static final String BULK_DOCUMENT_UPLOADED = "BULK_DOCUMENT_UPLOADED";
    public static final String BULK_DOCUMENT_UPLOAD_FAIL = "BULK_DOCUMENT_UPLOAD_FAIL";

    /**
     * Simple method to log activity
     */
    public void log(User user, String action, String details) {
        try {
            ActivityLog log = new ActivityLog(user, action, details);
            activityLogRepository.save(log);
        } catch (Exception e) {
            // Silent fail - logging shouldn't break the app
            System.err.println("Failed to log activity: " + e.getMessage());
        }
    }

    /**
     * Log activity by username (finds user ID automatically)
     */
    public void logByUsername(String username, String action, String details) {
        try {
            User user = userRepository.findByUsername(username);
            if (user != null) {
//                Long userId = user.getId();
                log(user, action, details);
            }
        } catch (Exception e) {
            // Silent fail - logging shouldn't break the app
            System.err.println("Failed to log activity for user " + username + ": " + e.getMessage());
        }
    }

    /**
     * Get all logs
     */
    public List<ActivityLog> getAllLogs() {
        return activityLogRepository.findAllByOrderByTimestampDesc();
    }

    /**
     * Get user's logs
     */
    public List<ActivityLog> getUserLogs(String username) {
        User user = userRepository.findByUsername(username);
        if (user != null) {
            return activityLogRepository.findByUserOrderByTimestampDesc(user);
        }
        return List.of();
    }

    /**
     * Get today's count - FIXED METHOD
     */
    public Long getTodayCount() {
        try {
            LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
            LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);
            return activityLogRepository.countTodayLogs(startOfDay, endOfDay);
        } catch (Exception e) {
            System.err.println("Failed to get today's count: " + e.getMessage());
            return 0L;
        }
    }

    /** Count all successful actions */
    public Long countSuccessLogs() {
        try {
            return activityLogRepository.countSuccessLogs();
        } catch (Exception e) {
            System.err.println("Failed to count success logs: " + e.getMessage());
            return 0L;
        }
    }

    /** Count all failed actions */
    public Long countFailedLogs() {
        try {
            return activityLogRepository.countFailedLogs();
        } catch (Exception e) {
            System.err.println("Failed to count failed logs: " + e.getMessage());
            return 0L;
        }
    }

    /**
     * 🧹 Automatically delete old logs (older than 90 days)
     * Runs daily at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * ?") // runs every day at 2 AM
//    @Scheduled(cron = "0 * * * * ?") // runs every 1 minute
    @Transactional
    public void deleteOldLogs() {
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(90); // 90 days retention
            int deletedCount = activityLogRepository.deleteOldLogs(cutoffDate);
            if (deletedCount > 0) {
                System.out.println("🧹 Deleted " + deletedCount + " old activity logs before " + cutoffDate);
            }
        } catch (Exception e) {
            System.err.println("Failed to delete old logs: " + e.getMessage());
        }
    }
}