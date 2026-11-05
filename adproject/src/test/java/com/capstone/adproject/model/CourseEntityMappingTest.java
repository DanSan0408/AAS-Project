package com.capstone.adproject.model;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class CourseEntityMappingTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void testTableAndColumnAnnotationsMatchPhysicalSchema() throws Exception {
        Table table = Course.class.getAnnotation(Table.class);
        assertNotNull(table, "Course should have @Table annotation");
        assertEquals("courses", table.name());

        Field courseNameField = Course.class.getDeclaredField("courseName");
        Column nameCol = courseNameField.getAnnotation(Column.class);
        assertNotNull(nameCol, "courseName should have explicit @Column annotation");
        assertEquals("courseName", nameCol.name(), "courseName column must match camelCase physical column");

        Field courseCodeField = Course.class.getDeclaredField("courseCode");
        Column codeCol = courseCodeField.getAnnotation(Column.class);
        assertNotNull(codeCol, "courseCode should have explicit @Column annotation");
        assertEquals("courseCode", codeCol.name(), "courseCode column must match camelCase physical column");

        Field createdByField = Course.class.getDeclaredField("createdBy");
        JoinColumn joinCol = createdByField.getAnnotation(JoinColumn.class);
        assertNotNull(joinCol, "createdBy should have explicit @JoinColumn annotation");
        assertEquals("created_by_lecturer_id", joinCol.name());
    }

    @Test
    void testCourseValidation_BlankFieldsFailValidation() {
        Course course = new Course();
        course.setCourseName("");
        course.setCourseCode("");

        Set<ConstraintViolation<Course>> violations = validator.validate(course);
        assertEquals(2, violations.size(), "Both blank courseName and courseCode must produce violations");
    }

    @Test
    void testCourseValidation_ValidCoursePasses() {
        Course course = new Course();
        course.setCourseName("Software Engineering");
        course.setCourseCode("SECJ3104");
        course.setDescription("Capstone Project course");

        Set<ConstraintViolation<Course>> violations = validator.validate(course);
        assertTrue(violations.isEmpty(), "Valid course should have no constraint violations");
    }
}

