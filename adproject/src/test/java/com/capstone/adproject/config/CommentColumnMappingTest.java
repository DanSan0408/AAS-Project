package com.capstone.adproject.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import com.capstone.adproject.model.AssessmentComment;

import jakarta.persistence.Entity;

class CommentColumnMappingTest {
    @Test
    void readsOriginalColumnsWithLocalNamingStrategy() throws Exception {
        assertOriginalColumns(new PhysicalNamingStrategyStandardImpl());
    }

    @Test
    void readsOriginalColumnsWithProductionSnakeCaseNamingStrategy() throws Exception {
        assertOriginalColumns(new CamelCaseToUnderscoresNamingStrategy());
    }

    private void assertOriginalColumns(PhysicalNamingStrategy namingStrategy) throws Exception {
        var properties = new HashMap<String, Object>();
        properties.put("hibernate.physical_naming_strategy", namingStrategy.getClass().getName());
        new CommentColumnNamingConfiguration().preserveOriginalCommentColumns().customize(properties);
        var configured = (PhysicalNamingStrategy) properties.get("hibernate.physical_naming_strategy");
        var registry = new StandardServiceRegistryBuilder()
            .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
            .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
            .build();
        try {
            var sources = new MetadataSources(registry);
            var scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
            for (var candidate : scanner.findCandidateComponents("com.capstone.adproject.model")) {
                sources.addAnnotatedClass(Class.forName(candidate.getBeanClassName()));
            }
            var metadata = sources.getMetadataBuilder().applyPhysicalNamingStrategy(configured).build();
            var mapping = metadata.getEntityBinding(AssessmentComment.class.getName());
            for (String property : new String[]{"commentText", "evaluatorType", "assessmentType"}) {
                var column = mapping.getProperty(property).getColumns().getFirst();
                assertEquals(property, column.getName(), "Must read the populated legacy column");
                assertTrue(column.isQuoted(), "Naming overrides must not redirect reads to parallel columns");
            }
            var otherEntity = metadata.getEntityBinding("com.capstone.adproject.model.Assessment");
            assertEquals("title", otherEntity.getProperty("title").getColumns().getFirst().getName());
            var unquoted = org.hibernate.boot.model.naming.Identifier.toIdentifier("otherProperty");
            assertEquals(namingStrategy.toPhysicalColumnName(unquoted, null),
                configured.toPhysicalColumnName(unquoted, null));
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
