package codesAndStandards.springboot.userApp.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private CustomSuccessHandler customSuccessHandler;

    @Bean
    public static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/**") // Disable CSRF for API endpoints
                )
                .authorizeHttpRequests(authorize -> authorize

                        // ========================================
                        // ✅ LICENSE ENDPOINTS (ADD THIS SECTION)
                        // ========================================
                        .requestMatchers("/license-activation", "/license-activation/**").permitAll()
                        .requestMatchers("/api/license/**").permitAll()

                        // ========================================
                        // PUBLIC ENDPOINTS (MOVED UP FOR PRIORITY)
                        // ========================================
                        .requestMatchers("/register/**").permitAll()
                        .requestMatchers("/login/**").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/fonts/**").permitAll()

                        // ========================================
                        // ROLE-BASED PERMISSIONS
                        // ========================================

                        // Bulk upload - Admin only
                        .requestMatchers("/bulk-upload").hasAuthority("Admin")  // ← ADD THIS LINE
                        // Upload endpoint
                        .requestMatchers("/upload").hasAnyAuthority("Admin", "Manager")

                        // Document API
                        .requestMatchers(HttpMethod.GET, "/api/documents/**")
                        .hasAnyAuthority("Admin", "Manager", "User")

                        // User API
                        .requestMatchers(HttpMethod.GET, "/api/users/**")
                        .hasAnyAuthority("Admin", "Manager")

                        // Access Groups API
                        .requestMatchers(HttpMethod.GET, "/api/access-groups/**")
                        .hasAnyAuthority("Admin", "Manager", "User")
                        .requestMatchers("/api/access-groups/**")
                        .hasAuthority("Admin")

                        // Documents pages
                        .requestMatchers("/documents").hasAnyAuthority("Admin","Manager","Viewer")
                        .requestMatchers("/documents/**").hasAnyAuthority("Admin","Manager","Viewer")
                        .requestMatchers("/my-bookmarks").hasAnyAuthority("Admin","Manager","Viewer")
                        .requestMatchers("/DocViewer").hasAnyAuthority("Admin","Manager","Viewer")

                        // Activity logs - Admin only
                        .requestMatchers("/activity-logs").hasAuthority("Admin")

                        // Tags and Classifications management
                        .requestMatchers("/tags-management","/classifications-management").hasAnyAuthority("Admin","Manager")
                        .requestMatchers("/api/tags/**","/api/classifications/**").hasAnyAuthority("Admin","Manager")

                        // Admin only endpoints
                        .requestMatchers("/users", "/users/**").hasAuthority("Admin")
                        .requestMatchers("/add", "/add/**").hasAuthority("Admin")
                        .requestMatchers("/edit/**").hasAuthority("Admin")
                        .requestMatchers("/delete/**").hasAuthority("Admin")

                        // Manager endpoints
                        .requestMatchers("/manager", "/manager/**").hasAuthority("Manager")
                        .requestMatchers("/manager/documents/**").hasAuthority("Manager")

                        // Viewer endpoints
                        .requestMatchers("/viewer", "/viewer/**").hasAuthority("Viewer")

                        // Profile accessible by all authenticated users
                        .requestMatchers("/profile", "/profile/**").authenticated()

                        // All other requests require authentication
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .successHandler(customSuccessHandler)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                );

        // Optional: Session management (currently commented out)
        // http.sessionManagement(session -> session
        //         .sessionConcurrency(concurrency -> concurrency
        //                 .maximumSessions(1)
        //                 .maxSessionsPreventsLogin(true)
        //                 .expiredUrl("/login?expired")
        //         )
        // );

        return http.build();
    }

    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        auth
                .userDetailsService(userDetailsService)
                .passwordEncoder(passwordEncoder());
    }
}