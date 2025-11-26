package codesAndStandards.springboot.userApp.controller;

import codesAndStandards.springboot.userApp.dto.UserDto;
import codesAndStandards.springboot.userApp.entity.ActivityLog;
import codesAndStandards.springboot.userApp.entity.User;
import codesAndStandards.springboot.userApp.repository.UserRepository;
import codesAndStandards.springboot.userApp.service.ActivityLogService;
import codesAndStandards.springboot.userApp.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
//@RequestMapping("/activity-logs")
@PreAuthorize("hasAnyAuthority('Admin','Manager','Viewer')")
public class ActivityLogsController {

    @Autowired
    private ActivityLogService activityLogService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    /**
     * View all activity logs
     */
    @GetMapping("/activity-logs")
    public String viewLogs(Model model) {
        // Get all logs
        List<ActivityLog> logs = activityLogService.getAllLogs();

        // Get user details for each log
        Map<Long, User> userMap = new HashMap<>();
        for (ActivityLog log : logs) {
            if (log.getUser() != null) { // ✅ Safe check
                Long userId = log.getUser().getId();

                if (!userMap.containsKey(userId)) {
                    User user = userRepository.findById(userId).orElse(null);
                    userMap.put(userId, user);
                }
            }
        }

        // Get statistics
        Long todayCount = activityLogService.getTodayCount();
        Long countSuccessLogs = activityLogService.countSuccessLogs();
        Long countFailedLogs = activityLogService.countFailedLogs();

        // Count download logs (both successful and failed)
        long countDownloadLogs = logs.stream()     .filter(log -> "DOCUMENT_DOWNLOAD".equals(log.getAction()))     .count();


        model.addAttribute("logs", logs);
        model.addAttribute("userMap", userMap);
        model.addAttribute("todayCount", todayCount);
        model.addAttribute("totalLogs", logs.size());
        model.addAttribute("countSuccessLogs", countSuccessLogs);
        model.addAttribute("countFailedLogs", countFailedLogs);
        model.addAttribute("countDownloadLogs", countDownloadLogs);

        return "activity-logs";
    }

    @GetMapping("/api/users/{userId}")
    @ResponseBody
    public ResponseEntity<?> getUserDetails(@PathVariable Long userId) {
        try {
            System.out.println("API called - Fetching user with ID: " + userId); // Debug log

            UserDto userDTO = userService.findUserById(userId);

            if (userDTO == null) {
                System.out.println("User not found with ID: " + userId); // Debug log
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "User not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }

            System.out.println("User found: " + userDTO.getUsername()); // Debug log
            return ResponseEntity.ok(userDTO);

        } catch (Exception e) {
            System.err.println("Error fetching user: " + e.getMessage()); // Debug log
            e.printStackTrace();
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Error fetching user details: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

}