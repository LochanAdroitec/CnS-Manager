package codesAndStandards.springboot.userApp.security;

import codesAndStandards.springboot.userApp.service.ActivityLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationListener {

    @Autowired
    private ActivityLogService activityLogService;

    /**
     * Log when user logs in
     */
    @EventListener
    public void onLogin(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        activityLogService.logByUsername(username, ActivityLogService.LOGIN, "User logged in - "+username);
    }

    /**
     * Log when user logs out
     */
    @EventListener
    public void onLogout(LogoutSuccessEvent event) {
        if (event.getAuthentication() != null) {
            String username = event.getAuthentication().getName();
            activityLogService.logByUsername(username, ActivityLogService.LOGOUT, "User logged out");
        }
    }
    /**
     * Log when login fails (e.g., wrong password or unknown username)
     */
    @EventListener
    public void onLoginFailed(AbstractAuthenticationFailureEvent event) {
        String username = event.getAuthentication().getName();
        String reason = event.getException().getMessage();  // e.g., "Bad credentials"
        activityLogService.logByUsername(username, ActivityLogService.LOGIN_FAILED,
                "Login failed for username: " + username + " - Reason: " + reason);
    }
}