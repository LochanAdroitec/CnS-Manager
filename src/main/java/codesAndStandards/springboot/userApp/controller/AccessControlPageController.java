package codesAndStandards.springboot.userApp.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
@Slf4j
public class AccessControlPageController {

    /**
     * Display Access Control Management page
     */
    @GetMapping("/access-control")
    @PreAuthorize("hasAnyAuthority('Admin', 'Manager', 'User')")
    public String accessControlPage(Model model) {
        log.info("Displaying Access Control Management page");
        return "access-control";
    }
}