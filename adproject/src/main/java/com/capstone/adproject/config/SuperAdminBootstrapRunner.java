package com.capstone.adproject.config;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.capstone.adproject.model.SuperAdmin;
import com.capstone.adproject.repositories.SuperAdminRepository;

@Component
@Order(1)
public class SuperAdminBootstrapRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(SuperAdminBootstrapRunner.class);

    @Value("${app.bootstrap.superadmin.email:}")
    private String bootstrapEmail;

    @Value("${app.bootstrap.superadmin.password:}")
    private String bootstrapPassword;

    private final SuperAdminRepository superAdminRepository;
    private final PasswordEncoder passwordEncoder;

    public SuperAdminBootstrapRunner(SuperAdminRepository superAdminRepository, PasswordEncoder passwordEncoder) {
        this.superAdminRepository = superAdminRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (bootstrapEmail == null || bootstrapEmail.isBlank() || 
            bootstrapPassword == null || bootstrapPassword.isBlank()) {
            logger.info("BOOTSTRAP: SuperAdmin bootstrap skipped. 'app.bootstrap.superadmin.email' or 'app.bootstrap.superadmin.password' not configured.");
            return;
        }

        String email = bootstrapEmail.trim();
        Optional<SuperAdmin> existing = superAdminRepository.findByEmail(email);
        if (existing.isPresent()) {
            logger.info("INFO: Super admin {} already exists.", email);
            return;
        }

        SuperAdmin superAdmin = new SuperAdmin();
        superAdmin.setEmail(email);
        superAdmin.setUsername("superadmin");
        superAdmin.setPassword(passwordEncoder.encode(bootstrapPassword.trim()));
        superAdmin.setRoles("ROLE_SUPER_ADMIN,ROLE_ADMIN,ROLE_LECTURER");

        superAdminRepository.save(superAdmin);
        logger.info("BOOTSTRAP: Successfully created super admin account for: {}", email);
    }
}
