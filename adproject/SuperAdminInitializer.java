package com.capstone.adproject.config;

import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.repositories.LecturerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class SuperAdminInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(SuperAdminInitializer.class);
    private final LecturerRepository lecturerRepository;

    public SuperAdminInitializer(LecturerRepository lecturerRepository) {
        this.lecturerRepository = lecturerRepository;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        String superAdminEmail = "ihsan.rubix@gmail.com";
        String superAdminRole = "ROLE_SUPER_ADMIN";

        Optional<Lecturer> userOpt = lecturerRepository.findByEmail(superAdminEmail);

        if (userOpt.isPresent()) {
            Lecturer user = userOpt.get();
            String roles = user.getRoles() != null ? user.getRoles() : "";

            if (!roles.contains(superAdminRole)) {
                user.setRoles(roles.isEmpty() ? superAdminRole : roles + "," + superAdminRole);
                lecturerRepository.save(user);
                logger.info("SUCCESS: Assigned {} to user {}", superAdminRole, superAdminEmail);
            }
        }
    }
}