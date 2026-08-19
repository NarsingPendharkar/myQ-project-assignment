package com.schwab.audit.config;

import com.schwab.audit.entity.User;
import com.schwab.audit.entity.enums.UserRole;
import com.schwab.audit.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Creates one initial administrator only when explicit bootstrap credentials are supplied. */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class BootstrapAdminInitializer {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    ApplicationRunner bootstrapAdmin(
            @Value("${app.bootstrap-admin.username:}") String username,
            @Value("${app.bootstrap-admin.password:}") String password) {
        return args -> {
            if (userRepository.countByRole(UserRole.ADMIN) > 0) return;
            if (username.isBlank() || password.isBlank()) {
                log.warn("No admin exists. Set BOOTSTRAP_ADMIN_USERNAME and BOOTSTRAP_ADMIN_PASSWORD to initialize one.");
                return;
            }
            userRepository.save(User.builder().username(username).password(passwordEncoder.encode(password))
                    .role(UserRole.ADMIN).build());
            log.info("Bootstrap administrator created: {}", username);
        };
    }
}
