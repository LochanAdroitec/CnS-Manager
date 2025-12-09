//package codesAndStandards.springboot.userApp.security;
//
//import codesAndStandards.springboot.userApp.entity.Role;
//import codesAndStandards.springboot.userApp.entity.User;
//import codesAndStandards.springboot.userApp.repository.RoleRepository;
//import codesAndStandards.springboot.userApp.repository.UserRepository;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.transaction.annotation.Transactional;
//
//@Configuration
//public class DataInitializer {
//
//    @Bean
//    @Transactional
//    public CommandLineRunner initData(RoleRepository roleRepo,
//                                      UserRepository userRepo,
//                                      PasswordEncoder passwordEncoder) {
//
//        return args -> {
//
//            // Insert default roles if not exist
//            if (roleRepo.count() == 0) {
//                roleRepo.save(new Role(null, "Admin", null));
//                roleRepo.save(new Role(null, "Manager", null));
//                roleRepo.save(new Role(null, "Viewer", null));
//            }
//
//            // Insert admin user if not present
//            if (!userRepo.existsByUsername("admin")) {
//
//                Role adminRole = roleRepo.findByRoleNames("Admin");
//
//                User admin = new User();
//                admin.setFirstName("abhay");
//                admin.setLastName("joshi");
//                admin.setEmail("admin@example.com");
//                admin.setUsername("admin");
//                admin.setPassword(passwordEncoder.encode("a")); // encrypted
//                admin.setRole(adminRole);
//
//                userRepo.save(admin);
//            }
//
//            System.out.println("\n✔ Default roles & admin user inserted.\n");
//        };
//    }
//}
