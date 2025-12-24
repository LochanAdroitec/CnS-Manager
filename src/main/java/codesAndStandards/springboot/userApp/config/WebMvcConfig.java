package codesAndStandards.springboot.userApp.config;

import codesAndStandards.springboot.userApp.interceptor.LicenseInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private LicenseInterceptor licenseInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(licenseInterceptor)
                .addPathPatterns("/api/**")           // Check all API endpoints
                .excludePathPatterns(
                        "/api/license/**",                // Don't check license endpoints
                        "/api/public/**",                 // Don't check public endpoints
                        "/login",                         // Don't check login page
                        "/register"                       // Don't check register page
                );
    }
}