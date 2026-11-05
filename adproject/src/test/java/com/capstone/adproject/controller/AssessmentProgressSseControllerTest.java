package com.capstone.adproject.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.service.AssessmentProgressStreamService;
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.ProgressTrackingService;

@ExtendWith(MockitoExtension.class)
class AssessmentProgressSseControllerTest {

    @Mock
    private AssessmentService assessmentService;

    @Mock
    private ProgressTrackingService progressTrackingService;

    private AssessmentProgressStreamService streamService;
    private AssessmentProgressSseController controller;
    private Assessment assessment;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        streamService = new AssessmentProgressStreamService(assessmentService, progressTrackingService);
        controller = new AssessmentProgressSseController(streamService);

        assessment = new Assessment();
        assessment.setId(10L);
        assessment.setTitle("Final Presentation");

        when(assessmentService.getAssessmentById(10L)).thenReturn(Optional.of(assessment));
        when(progressTrackingService.getLecturerProgress(assessment)).thenReturn(Map.of(
            "progressList", List.of(
                Map.of("lecturerName", "Dr. Alice", "assignedTarget", "4 Groups", "status", "Completed", "progress", 100),
                Map.of("lecturerName", "Dr. Bob", "assignedTarget", "4 Groups", "status", "In Progress", "progress", 50)
            )
        ));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testStreamProgress_SubscribesSuccessfully() {
        SseEmitter emitter = controller.streamProgress(10L);
        assertNotNull(emitter);
    }

    @Test
    void testGetSnapshot_ReturnsPayload() {
        ResponseEntity<?> resp = controller.getSnapshot(10L);
        assertNotNull(resp);
        assertEquals(200, resp.getStatusCode().value());
        assertTrue(resp.getBody() instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertEquals(10L, body.get("assessmentId"));
        assertEquals("Final Presentation", body.get("assessmentTitle"));
        assertEquals(75, body.get("overallPercent"));
        assertEquals(2, body.get("totalAssessors"));
        assertEquals(1, body.get("completedAssessors"));
    }

    @Test
    void testBroadcastProgress_SendsUpdates() {
        SseEmitter emitter = controller.streamProgress(10L);
        assertNotNull(emitter);

        assertDoesNotThrow(() -> {
            streamService.broadcastProgress(10L);
        });
    }
}

