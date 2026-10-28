package com.capstone.adproject.config;

import java.util.List;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.capstone.adproject.model.AssessmentComment;

import jakarta.persistence.EntityManagerFactory;

/** Read-only checks visible in container logs; never logs comment text or credentials. */
@Component
public class CommentStorageDiagnostics implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(CommentStorageDiagnostics.class);
    private final JdbcTemplate jdbcTemplate;
    private final EntityManagerFactory entityManagerFactory;

    public CommentStorageDiagnostics(JdbcTemplate jdbcTemplate, EntityManagerFactory entityManagerFactory) {
        this.jdbcTemplate = jdbcTemplate;
        this.entityManagerFactory = entityManagerFactory;
    }

    @Override
    public void run(String... args) {
        try {
            var sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
            var mapping = (AbstractEntityPersister) sessionFactory.getMappingMetamodel()
                .getEntityDescriptor(AssessmentComment.class);
            logger.info("Comment storage: database={}, mappedTextColumns={}",
                jdbcTemplate.queryForObject("SELECT DATABASE()", String.class),
                List.of(mapping.getPropertyColumnNames("commentText")));

            List<String> columns = jdbcTemplate.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'assessment_comments'
                ORDER BY ordinal_position
                """, String.class);
            logger.info("Comment storage: availableColumns={}", columns);

            // Only fixed, known identifiers are interpolated into SQL.
            for (String candidate : List.of("commentText", "comment_text")) {
                if (columns.stream().noneMatch(candidate::equalsIgnoreCase)) {
                    continue;
                }
                var counts = jdbcTemplate.queryForMap("SELECT COUNT(*) AS total, "
                    + "COUNT(CASE WHEN CHAR_LENGTH(TRIM(`" + candidate + "`)) > 0 THEN 1 END) AS nonblank "
                    + "FROM assessment_comments");
                logger.info("Comment storage: textColumn={}, counts={}", candidate, counts);
            }
        } catch (RuntimeException ex) {
            // A diagnostic must not prevent startup, including on databases without metadata access.
            logger.warn("Comment storage diagnostics unavailable ({})", ex.getClass().getSimpleName());
        }
    }
}
