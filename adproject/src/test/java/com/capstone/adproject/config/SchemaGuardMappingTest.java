package com.capstone.adproject.config;

import java.util.Map;
import java.util.Set;

import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import jakarta.persistence.Entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Builds a real Hibernate session factory from every entity in the application (no database connection needed) and
 * checks that the schema guard reads the table/column mapping correctly from it. This is the part of the guard that a
 * mock cannot prove.
 */
class SchemaGuardMappingTest {

    /** Refuses every connection: building the session factory must not need a database. */
    public static class NoConnections implements org.hibernate.engine.jdbc.connections.spi.ConnectionProvider {
        @Override
        public java.sql.Connection getConnection() throws java.sql.SQLException {
            throw new java.sql.SQLException("no database in this test");
        }

        @Override
        public void closeConnection(java.sql.Connection connection) {
        }

        @Override
        public boolean supportsAggressiveRelease() {
            return false;
        }

        @Override
        public boolean isUnwrappableAs(Class<?> unwrapType) {
            return false;
        }

        @Override
        public <T> T unwrap(Class<T> unwrapType) {
            throw new UnsupportedOperationException();
        }
    }

    private static SessionFactory sessionFactory;
    private static StandardServiceRegistry registry;

    @BeforeAll
    static void bootHibernateWithoutADatabase() throws Exception {
        registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .applySetting("hibernate.connection.provider_class", NoConnections.class.getName())
                .applySetting("hibernate.hbm2ddl.auto", "none")
                // The same naming strategies the application runs with.
                .applySetting("hibernate.physical_naming_strategy",
                        "org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl")
                .applySetting("hibernate.implicit_naming_strategy",
                        "org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy")
                .build();
        MetadataSources sources = new MetadataSources(registry);
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        for (BeanDefinition definition : scanner.findCandidateComponents("com.capstone.adproject.model")) {
            sources.addAnnotatedClass(Class.forName(definition.getBeanClassName()));
        }
        sessionFactory = sources.buildMetadata().buildSessionFactory();
    }

    @AfterAll
    static void close() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
        if (registry != null) {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    @Test
    void readsTablesAndColumnsFromEveryEntity() {
        Map<String, Set<String>> mapped = LegacyColumnCleanupMigrationRunner.mappedColumns(sessionFactory);

        assertTrue(mapped.size() >= 20, "expected a table per entity, found " + mapped.keySet());
        assertTrue(mapped.get("student").containsAll(Set.of("id", "email", "welcomeemailsentat")),
                "student columns: " + mapped.get("student"));
        assertTrue(mapped.get("courses").containsAll(Set.of("coursecode", "coursename")),
                "courses columns: " + mapped.get("courses"));
    }

    @Test
    void doesNotListAColumnTheCodeNoLongerMaps() {
        Map<String, Set<String>> mapped = LegacyColumnCleanupMigrationRunner.mappedColumns(sessionFactory);

        assertFalse(mapped.get("courses").contains("course_code"),
                "legacy course_code must read as unmapped so the guard can relax it");
        assertEquals(mapped.get("courses"), mapped.get("courses"));
    }

    /**
     * A plain list of values (an element collection without an order column) is stored as a table of its owner's id and the
     * value; Hibernate creates no "id" column for it. The guard once expected one and reported five tables as damaged.
     */
    @Test
    void aPlainValueCollectionIsExpectedToHaveOnlyItsKeyAndValueColumns() {
        Map<String, Set<String>> mapped = LegacyColumnCleanupMigrationRunner.mappedColumns(sessionFactory);

        assertEquals(Set.of("rubric_id", "comment_label"), mapped.get("rubric_comment_labels"));
        assertEquals(Set.of("rubric_id", "comment_min_length"), mapped.get("rubric_comment_min_lengths"));
        assertEquals(Set.of("rubric_id", "comment_anonymous"), mapped.get("rubric_comment_anonymous_flags"));
        for (String table : Set.of("assessment_groupcommentlabels", "assessment_individualcommentlabels")) {
            assertFalse(mapped.get(table).contains("id"), table + " has no id column: " + mapped.get(table));
        }
    }
}
