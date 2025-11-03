package codesAndStandards.springboot.userApp.controller;

import codesAndStandards.springboot.userApp.entity.ActivityLog;
import codesAndStandards.springboot.userApp.entity.User;
import codesAndStandards.springboot.userApp.repository.UserRepository;
import codesAndStandards.springboot.userApp.service.ActivityLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
//@RequestMapping("/activity-logs")
@PreAuthorize("hasAuthority('Admin')")
public class ActivityLogsController {

    @Autowired
    private ActivityLogService activityLogService;

    @Autowired
    private UserRepository userRepository;

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


        model.addAttribute("logs", logs);
        model.addAttribute("userMap", userMap);
        model.addAttribute("todayCount", todayCount);
        model.addAttribute("totalLogs", logs.size());
        model.addAttribute("countSuccessLogs", countSuccessLogs);
        model.addAttribute("countFailedLogs", countFailedLogs);

        return "activity-logs";
    }
}