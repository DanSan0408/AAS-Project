package com.capstone.adproject.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.spi.MappingMetamodelImplementor;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.capstone.adproject.model.AssessmentComment;

import jakarta.persistence.EntityManagerFactory;

class CommentStorageDiagnosticsTest {
    @Test
    void checksBothTextColumnsWithoutChangingData() {
        var jdbc = mock(JdbcTemplate.class);
        var emf = mock(EntityManagerFactory.class);
        var factory = mock(SessionFactoryImplementor.class);
        var metamodel = mock(MappingMetamodelImplementor.class);
        var mapping = mock(AbstractEntityPersister.class);
        when(emf.unwrap(SessionFactoryImplementor.class)).thenReturn(factory);
        when(factory.getMappingMetamodel()).thenReturn(metamodel);
        when(metamodel.getEntityDescriptor(AssessmentComment.class)).thenReturn(mapping);
        when(mapping.getPropertyColumnNames("commentText")).thenReturn(new String[]{"comment_text"});
        when(jdbc.queryForObject("SELECT DATABASE()", String.class)).thenReturn("fixture");
        when(jdbc.queryForList(anyString(), eq(String.class)))
            .thenReturn(List.of("id", "commentText", "comment_text"));
        when(jdbc.queryForMap(anyString())).thenReturn(Map.of("total", 2, "nonblank", 2));

        new CommentStorageDiagnostics(jdbc, emf).run();

        verify(jdbc).queryForObject("SELECT DATABASE()", String.class);
        verify(jdbc).queryForList(anyString(), eq(String.class));
        for (String column : List.of("commentText", "comment_text")) {
            verify(jdbc).queryForMap("SELECT COUNT(*) AS total, "
                + "COUNT(CASE WHEN CHAR_LENGTH(TRIM(`" + column + "`)) > 0 THEN 1 END) AS nonblank "
                + "FROM assessment_comments");
        }
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void unavailableDiagnosticsDoNotPreventStartup() {
        var emf = mock(EntityManagerFactory.class);
        when(emf.unwrap(SessionFactoryImplementor.class)).thenThrow(new IllegalStateException());
        assertDoesNotThrow(() -> new CommentStorageDiagnostics(mock(JdbcTemplate.class), emf).run());
    }
}
