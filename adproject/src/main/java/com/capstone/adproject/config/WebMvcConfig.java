package com.capstone.adproject.config;

import com.capstone.adproject.interceptor.RateLimitInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final CourseScopeInterceptor courseScopeInterceptor;

    @Autowired
    public WebMvcConfig(RateLimitInterceptor rateLimitInterceptor, CourseScopeInterceptor courseScopeInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.courseScopeInterceptor = courseScopeInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**"); // Apply rate limiting to all /api endpoints

        registry.addInterceptor(courseScopeInterceptor)
                .addPathPatterns("/admin/**", "/rubrics/**", "/deadlines/**"); // Enforce course scoping for admin contexts
    }
}