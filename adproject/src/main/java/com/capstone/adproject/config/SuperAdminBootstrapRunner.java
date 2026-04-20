package com.capstone.adproject.config;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final String DEFAULT_EMAIL = "ihsan.rubix@gmail.com";

    private final SuperAdminRepository superAdminRepository;
    private final PasswordEncoder passwordEncoder;

    public SuperAdminBootstrapRunner(SuperAdminRepository superAdminRepository, PasswordEncoder passwordEncoder) {
        this.superAdminRepository = superAdminRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        Optional<SuperAdmin> existing = superAdminRepository.findByEmail(DEFAULT_EMAIL);
        if (existing.isPresent()) {
            logger.info("INFO: Super admin {} already exists.", DEFAULT_EMAIL);
            return;
        }

        SuperAdmin superAdmin = new SuperAdmin();
        superAdmin.setEmail(DEFAULT_EMAIL);
        superAdmin.setUsername("superadmin");
        superAdmin.setPassword(passwordEncoder.encode("Admin@12345"));
        superAdmin.setRoles("ROLE_SUPER_ADMIN,ROLE_ADMIN,ROLE_LECTURER");

        superAdminRepository.save(superAdmin);
        logger.warn("BOOTSTRAP: Created super admin account: {} / Admin@12345", DEFAULT_EMAIL);
        logger.warn("BOOTSTRAP: Please change this password immediately after login.");
    }
}
