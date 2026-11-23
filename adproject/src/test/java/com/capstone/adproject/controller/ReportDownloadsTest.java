package com.capstone.adproject.controller;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openpdf.text.pdf.PdfReader;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.capstone.adproject.model.Course;
import com.capstone.adproject.repositories.FactorOverrideHistoryRepository;
import com.capstone.adproject.repositories.FactorWeightageRepository;
import com.capstone.adproject.repositories.GroupRepository;
import com.capstone.adproject.repositories.MarkRepository;
import com.capstone.adproject.repositories.StudentResultOverrideRepository;
import com.capstone.adproject.service.AdminService;
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.CalculateService;
import com.capstone.adproject.service.CourseScopeService;
import com.capstone.adproject.service.LecturerAnalyticsService;
import com.capstone.adproject.service.StudentService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Course Analytics and Marking Consistency can be downloaded as .xlsx and .pdf. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportDownloadsTest {

    // --- Course Analytics ------------------------------------------------------------------------------------------------

    @Mock private AssessmentService assessmentService;
    @Mock private MarkRepository markRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private StudentService studentService;
    @Mock private StudentResultOverrideRepository overrideRepository;
    @Mock private CalculateService calculateService;
    @Mock private CourseScopeService courseScopeService;
    @Mock private AdminService adminService;
    @Mock private FactorWeightageRepository factorWeightageRepository;
    @Mock private FactorOverrideHistoryRepository factorOverrideHistoryRepository;
    @InjectMocks private DataViewController dataViewController;

    private MockMvc courseMvc;

    @BeforeEach
    void setUp() {
        courseMvc = MockMvcBuilders.standaloneSetup(dataViewController).build();
        Course course = new Course();
        course.setId(10L);
        course.setCourseCode("SECJ 4999/A");
        course.setCourseName("Capstone");
        course.setCloTargetPercent(65.0);
        when(courseScopeService.getActiveCourseForCurrentUser()).thenReturn(course);
    }

    @Test
    void courseAnalyticsDownloadsAsARealExcelFileNamedAfterTheCourse() throws Exception {
        MvcResult result = courseMvc.perform(get("/admin/data-views/course-analytics/export").param("format", "xlsx"))
                .andExpect(status().isOk()).andReturn();

        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", result.getResponse().getContentType());
        String disposition = result.getResponse().getHeader("Content-Disposition");
        assertTrue(disposition.startsWith("attachment"), disposition);
        assertTrue(disposition.contains("course-analytics-SECJ_4999_A-"), "the course code is made safe for a file name: " + disposition);
        assertTrue(disposition.endsWith(".xlsx\""), disposition);
        assertTrue(result.getResponse().getHeader("Cache-Control").contains("no-store"));
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            assertEquals("Summary", book.getSheetName(0));
            assertEquals("Course Analytics", book.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
        }
    }

    @Test
    void courseAnalyticsDownloadsAsARealPdfAndCarriesTheSavedOutcomeTarget() throws Exception {
        MvcResult result = courseMvc.perform(get("/admin/data-views/course-analytics/export").param("format", "PDF"))
                .andExpect(status().isOk()).andReturn();

        assertEquals(MediaType.APPLICATION_PDF_VALUE, result.getResponse().getContentType());
        byte[] pdf = result.getResponse().getContentAsByteArray();
        assertEquals("%PDF", new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        String text = new org.openpdf.text.pdf.parser.PdfTextExtractor(new PdfReader(pdf)).getTextFromPage(1);
        assertTrue(text.contains("learning outcome target 65%"), text);
    }

    @Test
    void anUnknownFormatIsRefusedAndNoFileIsMade() throws Exception {
        for (String bad : new String[]{"csv", "docx", ""}) {
            courseMvc.perform(get("/admin/data-views/course-analytics/export").param("format", bad))
                    .andExpect(status().isBadRequest());
        }
        courseMvc.perform(get("/admin/data-views/course-analytics/export")).andExpect(status().isBadRequest());
    }

    // --- Marking Consistency ------------------------------------------------------------------------------------------------

    private CourseScopeService scope;
    private MockMvc consistencyMvc;

    private void consistencySetup(Long activeCourse) {
        scope = mock(CourseScopeService.class);
        when(scope.getActiveCourseIdForCurrentUser()).thenReturn(activeCourse);
        consistencyMvc = MockMvcBuilders.standaloneSetup(new LecturerAnalyticsController(scope, mock(LecturerAnalyticsService.class))).build();
    }

    private static final String BODY = """
            {"title":"Marking consistency","subtitle":"Final presentation","details":["Team: all"],
             "sections":[{"heading":"Assessors","columns":["Lecturer","Mean (%)"],"rows":[["Dr Lim",71.5],["=cmd|' /C calc'!A0",null]]}]}""";

    @Test
    void theMarkingConsistencyTablesBecomeAnExcelFileAndAFormulaStaysText() throws Exception {
        consistencySetup(10L);

        MvcResult result = consistencyMvc.perform(post("/admin/analytics/lecturer-comparison/export").param("format", "xlsx")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andReturn();

        String disposition = result.getResponse().getHeader("Content-Disposition");
        assertTrue(disposition.contains("marking-consistency-") && disposition.endsWith(".xlsx\""), disposition);
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            assertEquals("Assessors", book.getSheetName(0));
            var sheet = book.getSheetAt(0);
            boolean foundText = false;
            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                var cell = sheet.getRow(i) == null ? null : sheet.getRow(i).getCell(0);
                if (cell != null && cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING
                        && cell.getStringCellValue().startsWith("=cmd")) {
                    foundText = true;
                }
            }
            assertTrue(foundText, "the formula-looking text is kept as text");
        }
    }

    @Test
    void theSameTablesAlsoBecomeAPdf() throws Exception {
        consistencySetup(10L);

        MvcResult result = consistencyMvc.perform(post("/admin/analytics/lecturer-comparison/export").param("format", "pdf")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andReturn();

        byte[] pdf = result.getResponse().getContentAsByteArray();
        assertEquals("%PDF", new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        assertTrue(new org.openpdf.text.pdf.parser.PdfTextExtractor(new PdfReader(pdf)).getTextFromPage(1).contains("Dr Lim"));
    }

    @Test
    void withoutAnActiveCourseNothingIsExported() throws Exception {
        consistencySetup(null);

        consistencyMvc.perform(post("/admin/analytics/lecturer-comparison/export").param("format", "xlsx")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").exists());
    }

    @Test
    void anUnknownFormatAnEmptyRequestAndABadCellAreRefusedWithAMessage() throws Exception {
        consistencySetup(10L);

        consistencyMvc.perform(post("/admin/analytics/lecturer-comparison/export").param("format", "csv")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Choose xlsx or pdf."));
        consistencyMvc.perform(post("/admin/analytics/lecturer-comparison/export").param("format", "pdf")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"T\",\"sections\":[]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("There is nothing to export."));
        consistencyMvc.perform(post("/admin/analytics/lecturer-comparison/export").param("format", "pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T\",\"sections\":[{\"heading\":\"S\",\"columns\":[\"a\"],\"rows\":[[{\"x\":1}]]}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theRequestCannotChooseTheFileName() throws Exception {
        consistencySetup(10L);
        String sneaky = "{\"title\":\"../../etc/passwd\",\"subtitle\":\"x\",\"filename\":\"evil.exe\",\"sections\":[{\"heading\":\"S\",\"columns\":[\"a\"],\"rows\":[[\"1\"]]}]}";

        MvcResult result = consistencyMvc.perform(post("/admin/analytics/lecturer-comparison/export").param("format", "pdf")
                        .contentType(MediaType.APPLICATION_JSON).content(sneaky))
                .andReturn();

        String disposition = String.valueOf(result.getResponse().getHeader("Content-Disposition"));
        assertFalse(disposition.contains("evil") || disposition.contains("passwd"), disposition);
    }

    @Test
    void anOversizedBodyIsRefusedWithoutBeingParsed() throws Exception {
        consistencySetup(10L);
        byte[] huge = new byte[6 * 1024 * 1024];
        java.util.Arrays.fill(huge, (byte) 'x');

        consistencyMvc.perform(post("/admin/analytics/lecturer-comparison/export").param("format", "xlsx")
                        .contentType(MediaType.APPLICATION_JSON).content(huge))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.message").exists());
    }

    @Test
    void aBodyThatIsNotJsonIsRefusedWithAMessageNotAnError() throws Exception {
        consistencySetup(10L);

        consistencyMvc.perform(post("/admin/analytics/lecturer-comparison/export").param("format", "xlsx")
                        .contentType(MediaType.APPLICATION_JSON).content("this is {not json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("The export request is not valid."));
    }

    @SuppressWarnings("unused")
    private static final List<String> NOTHING = List.of();
}
