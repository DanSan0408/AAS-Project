package com.capstone.adproject.exception;

import java.sql.SQLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.adproject.service.MonitoringService;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class GlobalExceptionHandlerTest {

    @RestController
    static class Faulty {
        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("kaput");
        }

        @GetMapping("/schema")
        String schema() {
            throw new BadSqlGrammarException("insert", "INSERT INTO marks ...",
                    new SQLException("Unknown column 'legacyScore' in 'field list'"));
        }

        @GetMapping("/duplicate")
        String duplicate() {
            throw new DataIntegrityViolationException("Duplicate entry 'a@b.c' for key 'email'");
        }

        @GetMapping("/denied")
        String denied() {
            throw new AccessDeniedException("nope");
        }

        @GetMapping("/param")
        String param(@RequestParam("needed") String needed) {
            return needed;
        }
    }

    private MonitoringService monitoring;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        monitoring = mock(MonitoringService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new Faulty())
                .setControllerAdvice(new GlobalExceptionHandler(monitoring))
                .build();
    }

    @Test
    void unexpectedErrorShowsTheErrorPageWithAReferenceAndIsRecorded() throws Exception {
        mockMvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("error"))
                .andExpect(model().attribute("status", 500))
                .andExpect(model().attributeExists("errorId", "errorTitle", "errorMessage"));

        verify(monitoring).incrementCriticalErrorCount();
        verify(monitoring).recordError(anyString(), eq(500), eq("GET"), eq("/boom"), any(IllegalStateException.class));
    }

    @Test
    void scriptCallersGetJsonInsteadOfAnHtmlPage() throws Exception {
        mockMvc.perform(get("/boom").header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.errorId").isNotEmpty())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void missingParameterIsAClientErrorThatIsNotCountedAsCritical() throws Exception {
        mockMvc.perform(get("/param"))
                .andExpect(status().isBadRequest())
                .andExpect(model().attribute("status", 400));

        verify(monitoring, never()).incrementCriticalErrorCount();
    }

    @Test
    void duplicateDataIsAConflictNotACrash() throws Exception {
        mockMvc.perform(get("/duplicate"))
                .andExpect(status().isConflict())
                .andExpect(model().attribute("status", 409));
        verify(monitoring, never()).recordSchemaIncident(anyString());
    }

    @Test
    void schemaDriftIsReportedAsASeparateIncidentAndNotShownAsABareCrash() throws Exception {
        mockMvc.perform(get("/schema"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(model().attribute("status", 503));

        verify(monitoring).recordSchemaIncident(contains("Unknown column"));
        verify(monitoring, never()).incrementCriticalErrorCount();
    }

    @Test
    void securityExceptionsAreLeftForSpringSecurityInsteadOfBecomingA500() {
        assertThrows(Exception.class, () -> mockMvc.perform(get("/denied")));
        verify(monitoring, never()).incrementCriticalErrorCount();
    }
}
