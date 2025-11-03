package codesAndStandards.springboot.userApp.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

@Component
public class CustomSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        Set<String> roles = AuthorityUtils.authorityListToSet(authentication.getAuthorities());

        if (roles.contains("Admin")) {
            response.sendRedirect("/documents");
        } else if (roles.contains("Manager")) {
            response.sendRedirect("/documents");
        } else if (roles.contains("Viewer")) {
            response.sendRedirect("/documents");
        }
//        else {
//            response.sendRedirect("/profile");
//        }
        else {
            response.sendRedirect("/login?error=unauthorized");
        }
    }
}

