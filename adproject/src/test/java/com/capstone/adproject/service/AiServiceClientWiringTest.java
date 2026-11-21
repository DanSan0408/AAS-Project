package com.capstone.adproject.service;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.MapPropertySource;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Spring itself must be able to build the client. A unit test that calls the constructor directly cannot catch a bean that
 * fails to start in production ("No default constructor found" when a class has two constructors and none is marked).
 */
class AiServiceClientWiringTest {

    @Test
    void springCanCreateTheClientFromItsProperties() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", java.util.Map.of(
                    "app.ai-service.url", "http://ai.test:8000", "app.ai-service.secret", "a-long-random-shared-secret")));
            context.register(PropertySourcesPlaceholderConfigurer.class, AiServiceClient.class);
            context.refresh();

            assertNotNull(context.getBean(AiServiceClient.class));
        }
    }
}
