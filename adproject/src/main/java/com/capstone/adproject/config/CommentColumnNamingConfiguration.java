package com.capstone.adproject.config;

import java.util.Set;

import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;

@Configuration
public class CommentColumnNamingConfiguration {
    @Bean
    HibernatePropertiesCustomizer preserveOriginalCommentColumns() {
        return properties -> {
            Object configured = properties.get("hibernate.physical_naming_strategy");
            PhysicalNamingStrategy delegate;
            if (configured instanceof PhysicalNamingStrategy strategy) {
                delegate = strategy;
            } else if (configured == null) {
                delegate = new CamelCaseToUnderscoresNamingStrategy();
            } else {
                try {
                    Class<?> type = configured instanceof Class<?> clazz ? clazz
                        : ClassUtils.forName(configured.toString(), ClassUtils.getDefaultClassLoader());
                    delegate = (PhysicalNamingStrategy) BeanUtils.instantiateClass(type);
                } catch (ClassNotFoundException ex) {
                    throw new IllegalStateException("Cannot load configured physical naming strategy", ex);
                }
            }
            properties.put("hibernate.physical_naming_strategy", new CommentColumnNamingStrategy(delegate));
        };
    }

    // Hibernate's snake_case strategy rewrites even quoted explicit column names.
    // Preserve only these quoted legacy names; delegate all other naming decisions.
    static class CommentColumnNamingStrategy implements PhysicalNamingStrategy {
        private static final Set<String> ORIGINAL_COLUMNS = Set.of("commentText", "evaluatorType", "assessmentType");
        private final PhysicalNamingStrategy delegate;

        CommentColumnNamingStrategy(PhysicalNamingStrategy delegate) {
            this.delegate = delegate;
        }

        @Override
        public Identifier toPhysicalColumnName(Identifier name, JdbcEnvironment environment) {
            if (name != null && name.isQuoted() && ORIGINAL_COLUMNS.contains(name.getText())) {
                return name;
            }
            return delegate.toPhysicalColumnName(name, environment);
        }

        @Override
        public Identifier toPhysicalCatalogName(Identifier name, JdbcEnvironment environment) {
            return delegate.toPhysicalCatalogName(name, environment);
        }

        @Override
        public Identifier toPhysicalSchemaName(Identifier name, JdbcEnvironment environment) {
            return delegate.toPhysicalSchemaName(name, environment);
        }

        @Override
        public Identifier toPhysicalTableName(Identifier name, JdbcEnvironment environment) {
            return delegate.toPhysicalTableName(name, environment);
        }

        @Override
        public Identifier toPhysicalSequenceName(Identifier name, JdbcEnvironment environment) {
            return delegate.toPhysicalSequenceName(name, environment);
        }
    }
}
