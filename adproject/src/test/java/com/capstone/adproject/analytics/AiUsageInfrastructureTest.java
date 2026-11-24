package com.capstone.adproject.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDateTime;

import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.parser.PartTree;

import com.capstone.adproject.model.AiUsageEvent;
import com.capstone.adproject.repositories.AiUsageEventRepository;

/** The usage table and its queries are valid against the real mappings, checked without a database. */
class AiUsageInfrastructureTest {

    @Test
    void theSpendQueryParsesAgainstTheEntityMapping() throws Exception {
        Configuration config = new Configuration();
        config.setProperty("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        config.setProperty("hibernate.boot.allow_jdbc_metadata_access", "false");
        config.setProperty("hibernate.hbm2ddl.auto", "none");
        config.addAnnotatedClass(AiUsageEvent.class);
        String hql = AiUsageEventRepository.class.getMethod("sumCostSince", LocalDateTime.class).getAnnotation(Query.class).value();
        try (var factory = config.buildSessionFactory(); var session = factory.openSession()) {
            var query = session.createQuery(hql, BigDecimal.class);
            query.setParameter("from", LocalDateTime.parse("2026-11-01T00:00:00"));
            assertNotNull(query);
        }
    }

    @Test
    void everyDerivedQueryNamesRealPropertiesOfTheEntity() {
        for (String name : new String[] {"findByOccurredAtGreaterThanEqualOrderByOccurredAtAsc",
                "findTop25ByOrderByOccurredAtDescIdDesc", "deleteByOccurredAtBefore"}) {
            new PartTree(name, AiUsageEvent.class);   // throws on an unknown property
        }
    }

    @Test
    void theTableIsCreatedWithTheColumnsAndPrecisionTheCodeRelies() throws Exception {
        var script = Files.createTempFile("ai-usage-ddl", ".sql");
        try {
            Configuration config = new Configuration();
            config.setProperty("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
            config.setProperty("hibernate.boot.allow_jdbc_metadata_access", "false");
            config.setProperty("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl");
            config.setProperty("jakarta.persistence.schema-generation.database.action", "none");
            config.setProperty("jakarta.persistence.schema-generation.scripts.action", "create");
            config.setProperty("jakarta.persistence.schema-generation.scripts.create-target", script.toString());
            config.addAnnotatedClass(AiUsageEvent.class);
            config.buildSessionFactory().close();
            String ddl = Files.readString(script).toLowerCase();
            assertTrue(ddl.contains("create table ai_usage_event"), ddl);
            assertTrue(ddl.contains("costusd decimal(14,8)"), ddl);
            assertTrue(ddl.contains("occurredat datetime"), ddl);
            assertTrue(ddl.contains("create index idx_ai_usage_event_time on ai_usage_event (occurredat)"), ddl);
            assertEquals(1, ddl.split("create table", -1).length - 1);
        } finally {
            Files.deleteIfExists(script);
        }
    }

    @Test
    void theSettingsTableIsOneRowWithTheAmountsAndWhoChangedThem() throws Exception {
        var script = Files.createTempFile("ai-settings-ddl", ".sql");
        try {
            Configuration config = new Configuration();
            config.setProperty("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
            config.setProperty("hibernate.boot.allow_jdbc_metadata_access", "false");
            config.setProperty("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl");
            config.setProperty("jakarta.persistence.schema-generation.database.action", "none");
            config.setProperty("jakarta.persistence.schema-generation.scripts.action", "create");
            config.setProperty("jakarta.persistence.schema-generation.scripts.create-target", script.toString());
            config.addAnnotatedClass(com.capstone.adproject.model.AiUsageSettings.class);
            config.buildSessionFactory().close();
            String ddl = Files.readString(script).toLowerCase();
            assertTrue(ddl.contains("create table ai_usage_settings"), ddl);
            assertTrue(ddl.contains("monthlybudgetusd decimal(12,2)"), ddl);
            assertTrue(ddl.contains("lowbalancealert decimal(12,2)"), ddl);
            assertTrue(ddl.contains("updatedat datetime"), ddl);
            assertTrue(ddl.contains("id bigint not null"), ddl);
            assertFalse(ddl.contains("auto_increment"), "the one row has a fixed id, not a generated one");
        } finally {
            Files.deleteIfExists(script);
        }
    }
}
