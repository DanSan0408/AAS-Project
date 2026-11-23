package com.capstone.adproject.controller;

import java.util.Objects;
import org.springframework.http.ContentDisposition;
import org.springframework.http.CacheControl;
import com.capstone.adproject.export.ReportFormat;
import com.capstone.adproject.export.ReportDocument;
import com.capstone.adproject.export.CourseAnalyticsReport;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.capstone.adproject.dto.AssessmentDataDto;
import com.capstone.adproject.dto.RubricCalculationDto;
import com.capstone.adproject.dto.StudentAssessmentDataDto;
import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Course;
import com.capstone.adproject.model.FactorOverrideHistory;
import com.capstone.adproject.model.FactorWeightage;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Mark;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.StudentResultOverride;
import com.capstone.adproject.repositories.FactorWeightageRepository;
import com.capstone.adproject.repositories.GroupRepository;
import com.capstone.adproject.repositories.MarkRepository;
import com.capstone.adproject.repositories.StudentResultOverrideRepository;
import com.capstone.adproject.service.AdminService;
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.CohortInputs;
import com.capstone.adproject.service.CohortMetrics;
import com.capstone.adproject.service.CalculateService;
import com.capstone.adproject.service.CourseScopeService;
import com.capstone.adproject.service.StudentService;

@Controller
@RequestMapping("/admin/data-views")
public class DataViewController {

    public static class OverrideForm {
        private List<StudentResultUpdate> updates = new ArrayList<>();
        public List<StudentResultUpdate> getUpdates() { return updates; }
        public void setUpdates(List<StudentResultUpdate> updates) { this.updates = updates; }
    }

    public static class StudentResultUpdate {
        private Long studentId;
        private Double factor;
        private Double grandTotal;
        private Double calculatedFactor;
        private Double calculatedGrandTotal;
        private String reason;

        public StudentResultUpdate() {}

        public StudentResultUpdate(Long studentId, Double factor, Double grandTotal, Double calculatedFactor, Double calculatedGrandTotal) {
            this.studentId = studentId;
            this.factor = factor;
            this.grandTotal = grandTotal;
            this.calculatedFactor = calculatedFactor;
            this.calculatedGrandTotal = calculatedGrandTotal;
        }

        public Long getStudentId() { return studentId; }
        public void setStudentId(Long studentId) { this.studentId = studentId; }
        public Double getFactor() { return factor; }
        public void setFactor(Double factor) { this.factor = factor; }
        public Double getGrandTotal() { return grandTotal; }
        public void setGrandTotal(Double grandTotal) { this.grandTotal = grandTotal; }
        public Double getCalculatedFactor() { return calculatedFactor; }
        public void setCalculatedFactor(Double calculatedFactor) { this.calculatedFactor = calculatedFactor; }
        public Double getCalculatedGrandTotal() { return calculatedGrandTotal; }
        public void setCalculatedGrandTotal(Double calculatedGrandTotal) { this.calculatedGrandTotal = calculatedGrandTotal; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    private final AssessmentService assessmentService;
    private final StudentService studentService;
    private final CalculateService calculateService;
    private final MarkRepository markRepository;
    private final GroupRepository groupRepository;
    private final StudentResultOverrideRepository overrideRepository;
    private final CourseScopeService courseScopeService;
    private final AdminService adminService;
    private final FactorWeightageRepository factorWeightageRepository;
    private final com.capstone.adproject.repositories.FactorOverrideHistoryRepository factorOverrideHistoryRepository;

    @Autowired
    public DataViewController(
            AssessmentService assessmentService,
            MarkRepository markRepository,
            GroupRepository groupRepository,
            StudentService studentService,
            StudentResultOverrideRepository overrideRepository,
            CalculateService calculateService,
            CourseScopeService courseScopeService,
            AdminService adminService,
            FactorWeightageRepository factorWeightageRepository,
            com.capstone.adproject.repositories.FactorOverrideHistoryRepository factorOverrideHistoryRepository) {
        this.assessmentService = assessmentService;
        this.markRepository = markRepository;
        this.groupRepository = groupRepository;
        this.studentService = studentService;
        this.overrideRepository = overrideRepository;
        this.calculateService = calculateService;
        this.courseScopeService = courseScopeService;
        this.adminService = adminService;
        this.factorWeightageRepository = factorWeightageRepository;
        this.factorOverrideHistoryRepository = factorOverrideHistoryRepository;
    }

    private String getLoggedInUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getName() != null) {
            return authentication.getName();
        }
        return "";
    }

    private Long getResolvedActiveCourseId() {
        Long activeCourseId = courseScopeService.getActiveCourseIdForCurrentUser();
        if (activeCourseId != null && activeCourseId > 0) {
            return activeCourseId;
        }
        Set<Long> activeCourseIds = courseScopeService.getActiveCourseIdsForCurrentUser();
        if (activeCourseIds != null && !activeCourseIds.isEmpty()) {
            return activeCourseIds.iterator().next();
        }
        return null;
    }

    private Set<Long> getManagedCourseIdsForCurrentUser() {
        Set<Long> activeCourseIds = courseScopeService.getActiveCourseIdsForCurrentUser();
        if (activeCourseIds != null && !activeCourseIds.isEmpty()) {
            return activeCourseIds;
        }
        Long activeCourseId = courseScopeService.getActiveCourseIdForCurrentUser();
        if (activeCourseId != null && activeCourseId > 0) {
            return Set.of(activeCourseId);
        }
        return Collections.emptySet();
    }

    private Course getResolvedActiveCourse(Long activeCourseId) {
        Course activeCourse = courseScopeService.getActiveCourseForCurrentUser();
        if (activeCourse != null && activeCourse.getId() != null) {
            return activeCourse;
        }
        if (activeCourseId == null) {
            return null;
        }
        Course fallback = new Course();
        fallback.setId(activeCourseId);
        return fallback;
    }

    private List<Student> resolveStudentsForGroup(Group group) {
        if (group == null) {
            return new ArrayList<>();
        }
        List<Student> scoped = adminService.getStudentsByGroup(group);
        return scoped != null ? new ArrayList<>(scoped) : new ArrayList<>();
    }

    private Group resolveStudentGroupForCourse(Student student, Long courseId) {
        if (student == null || courseId == null) {
            return null;
        }
        Group resolved = adminService.resolveStudentGroupForCourse(student, courseId);
        if (resolved != null) {
            return resolved;
        }
        if (student.getCourse() != null && courseId.equals(student.getCourse().getId())) {
            return student.getGroup();
        }
        return null;
    }
    
    @GetMapping("/factors")
    public String showFactorDisplayPage(@RequestParam(required = false) Long groupId, Model model) {
        Long activeCourseId = getResolvedActiveCourseId();
        List<Group> groups = activeCourseId == null
            ? List.of()
            : groupRepository.findByCourseId(activeCourseId);
        
        model.addAttribute("groups", groups);
        model.addAttribute("selectedGroupId", groupId);
        model.addAttribute("adminUsername", getLoggedInUsername());
        
        List<com.capstone.adproject.dto.FactorDetailsDto> factorDetailsList = new ArrayList<>();
        List<FactorOverrideHistory> allHistory = new ArrayList<>();
        
        if (groupId != null && activeCourseId != null) {
            Group group = groupRepository.findById(groupId).orElse(null);
            if (group != null && ownsGroup(group)) {
                List<Student> students = resolveStudentsForGroup(group);
                students.sort((s1, s2) -> {
                    String name1 = (s1.getUsername() != null && !s1.getUsername().trim().isEmpty()) ? s1.getUsername() : (s1.getEmail() != null ? s1.getEmail() : "");
                    String name2 = (s2.getUsername() != null && !s2.getUsername().trim().isEmpty()) ? s2.getUsername() : (s2.getEmail() != null ? s2.getEmail() : "");
                    return name1.compareToIgnoreCase(name2);
                });
                
                for (Student student : students) {
                    factorDetailsList.add(calculateService.getFactorDetailsForStudent(student, activeCourseId));
                }
                
                if (!students.isEmpty()) {
                    allHistory = factorOverrideHistoryRepository.findByStudentInAndCourseIdOrderByChangedAtDesc(students, activeCourseId);
                }
            }
        }
        
        model.addAttribute("factorDetailsList", factorDetailsList);
        model.addAttribute("allHistory", allHistory);
        
        return "factor_display";
    }

    private boolean ownsAssessment(Assessment assessment) {
        return assessment != null
            && assessment.getCourse() != null
            && assessment.getCourse().getId() != null
            && getManagedCourseIdsForCurrentUser().contains(assessment.getCourse().getId());
    }

    private boolean ownsGroup(Group group) {
        return group != null
            && group.getCourse() != null
            && group.getCourse().getId() != null
            && getManagedCourseIdsForCurrentUser().contains(group.getCourse().getId());
    }

    private boolean ownsStudent(Student student) {
        if (student == null) {
            return false;
        }
        if (adminService.isStudentInActiveCourse(student)) {
            return true;
        }
        return student.getCourse() != null
            && student.getCourse().getId() != null
            && getManagedCourseIdsForCurrentUser().contains(student.getCourse().getId());
    }

    @GetMapping
    public String showDataViewsHome(Model model) {
        Long activeCourseId = getResolvedActiveCourseId();
        model.addAttribute("assessments", activeCourseId == null
            ? List.of()
            : assessmentService.findAllAssessmentsWithRubricsByCourseId(activeCourseId));
        model.addAttribute("adminUsername", getLoggedInUsername());
        return "data_views_home";
    }

    /** Everything the Course Analytics page (and its downloads) show, worked out once so the file can never disagree with the page. */
    private record CourseAnalyticsView(Course course, double passMark, double cloTarget, CohortMetrics.Summary summary,
                                       List<CohortMetrics.CloRow> cloRows, List<CohortMetrics.CriterionRow> criterionRows,
                                       List<CohortMetrics.AttentionRow> attention, List<Assessment> assessments,
                                       Object allAssessments, Object groups) {}

    @SuppressWarnings("unchecked")
    private CourseAnalyticsView buildCourseAnalytics(Long groupId, List<Long> assessmentIds) {
        Map<String, Object> dataMap = getOverallDataModelMap(groupId, null, assessmentIds, false, 1, 25);
        List<Student> students = (List<Student>) dataMap.get("students");
        List<Assessment> assessments = (List<Assessment>) dataMap.get("assessments");
        Map<Long, Map<String, Object>> overall = (Map<Long, Map<String, Object>>) dataMap.get("studentOverallData");

        Course course = courseScopeService.getActiveCourseForCurrentUser();
        double passMark = course != null ? course.effectivePassMark() : Course.DEFAULT_PASS_MARK;
        double cloTarget = course != null ? course.effectiveCloTargetPercent() : Course.DEFAULT_CLO_TARGET_PERCENT;
        Long activeCourseId = getResolvedActiveCourseId();

        Map<Long, Map<Long, List<Mark>>> marksByAssessment = new HashMap<>();
        for (Assessment assessment : assessments) {
            marksByAssessment.put(assessment.getId(), calculateService.getMarksByEvaluatedStudentForAssessment(assessment));
        }
        Map<Long, Long> groupIdByStudent = new HashMap<>();
        for (Student student : students) {
            Group group = resolveStudentGroupForCourse(student, activeCourseId);
            if (student.getId() != null && group != null && group.getId() != null) {
                groupIdByStudent.put(student.getId(), group.getId());
            }
        }

        List<CohortMetrics.StudentResult> results = CohortInputs.toResults(students, overall);
        return new CourseAnalyticsView(course, passMark, cloTarget,
                CohortMetrics.summarise(results, passMark, calculateService::calculateGrade),
                CohortMetrics.cloRows(results, CohortInputs.cloAvailable(assessments), cloTarget),
                CohortMetrics.criterionRows(CohortInputs.criterionSamples(assessments, marksByAssessment, groupIdByStudent)),
                CohortMetrics.attention(results, passMark),
                assessments, dataMap.get("allAssessments"), dataMap.get("groups"));
    }

    /** Course Analytics: the few numbers an educator needs about how the cohort is doing. See {@link CohortMetrics}. */
    @GetMapping("/course-analytics")
    public String showCourseAnalytics(
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) List<Long> assessmentIds,
            Model model) {
        CourseAnalyticsView view = buildCourseAnalytics(groupId, assessmentIds);
        model.addAttribute("summary", view.summary());
        model.addAttribute("cloRows", view.cloRows());
        model.addAttribute("criterionRows", view.criterionRows());
        model.addAttribute("attention", view.attention());
        model.addAttribute("passMark", view.passMark());
        model.addAttribute("cloTarget", view.cloTarget());
        model.addAttribute("assessments", view.assessments());
        model.addAttribute("allAssessments", view.allAssessments());
        model.addAttribute("groups", view.groups());
        model.addAttribute("selectedGroupId", groupId);
        model.addAttribute("selectedAssessmentIds", assessmentIds == null ? List.of() : assessmentIds);
        model.addAttribute("activeCourse", view.course());
        model.addAttribute("adminUsername", getLoggedInUsername());
        return "course_analytics";
    }

    /** The Course Analytics page as a download (.xlsx or .pdf), for the same team and assessments the page is showing. */
    @GetMapping("/course-analytics/export")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> exportCourseAnalytics(
            @RequestParam("format") String format,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) List<Long> assessmentIds) {
        ReportFormat reportFormat;
        try {
            reportFormat = ReportFormat.parse(format);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body(e.getMessage());
        }
        CourseAnalyticsView view = buildCourseAnalytics(groupId, assessmentIds);
        String teamLabel = null;
        if (groupId != null && view.groups() instanceof List<?> groups) {
            for (Object g : groups) {
                if (g instanceof Group group && groupId.equals(group.getId())) {
                    teamLabel = group.getGroupName();
                }
            }
        }
        Course course = view.course();
        String courseLabel = course == null ? "" : ((course.getCourseCode() == null ? "" : course.getCourseCode() + " ")
                + (course.getCourseName() == null ? "" : course.getCourseName())).trim();
        List<String> titles = view.assessments().stream().map(Assessment::getTitle).filter(Objects::nonNull).toList();
        ReportDocument document = CourseAnalyticsReport.build(new CourseAnalyticsReport.Input(courseLabel, teamLabel,
                (assessmentIds == null || assessmentIds.isEmpty()) ? List.of() : titles, view.passMark(), view.cloTarget(),
                view.summary(), view.cloRows(), view.criterionRows(), view.attention()));
        String slug = (course == null || course.getCourseCode() == null ? "course" : course.getCourseCode())
                .replaceAll("[^A-Za-z0-9._-]+", "_");
        String filename = "course-analytics-" + slug + "-" + java.time.LocalDate.now() + "." + reportFormat.extension();
        return ResponseEntity.ok()
                .contentType(reportFormat.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(reportFormat.write(document));
    }

    @GetMapping("/overall")
    public String showOverallData(
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) String studentSearch,
            @RequestParam(required = false) List<Long> assessmentIds,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "25") int size,
            Model model) {
        Map<String, Object> dataMap = getOverallDataModelMap(groupId, studentSearch, assessmentIds, true, page, size);
        model.addAllAttributes(dataMap);
        model.addAttribute("adminUsername", getLoggedInUsername());
        return "overall_data_view";
    }

    @GetMapping("/overall/export")
    public ResponseEntity<byte[]> exportOverallDataToExcel(
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) String studentSearch,
            @RequestParam(required = false) List<Long> assessmentIds) {
        Map<String, Object> dataMap = getOverallDataModelMap(groupId, studentSearch, assessmentIds, false, 1, Integer.MAX_VALUE);
        @SuppressWarnings("unchecked")
        List<Student> students = (List<Student>) dataMap.get("students");
        @SuppressWarnings("unchecked")
        List<Assessment> assessments = (List<Assessment>) dataMap.get("assessments");
        @SuppressWarnings("unchecked")
        Map<Long, Map<String, Object>> studentOverallData = (Map<Long, Map<String, Object>>) dataMap.get("studentOverallData");
        @SuppressWarnings("unchecked")
        Map<Long, Set<Integer>> assessmentCLOsWithGroupRubrics = (Map<Long, Set<Integer>>) dataMap.get("assessmentCLOsWithGroupRubrics");

        StringBuilder csv = new StringBuilder();

        // Row 1: Headers (Assessment Titles, etc.)
        List<String> row1 = new ArrayList<>();
        row1.add(escapeCsv("Team Name"));
        row1.add(escapeCsv("Student Email"));
        row1.add(escapeCsv("Factor"));
        row1.add(escapeCsv("Capped (Max 1.05)"));

        for (Assessment assessment : assessments) {
            row1.add(escapeCsv(assessment.getTitle()));
            Set<Integer> cloKeys = assessment.getCloMarks() != null ? new TreeSet<>(assessment.getCloMarks().keySet()) : Collections.emptySet();
            int extraColumns = (cloKeys.size() * 4) + 2 - 1;
            for (int i = 0; i < extraColumns; i++) {
                row1.add("");
            }
        }
        row1.add(escapeCsv("Grand Total (Gt)"));
        row1.add(escapeCsv("Grade"));
        csv.append(String.join(",", row1)).append("\r\n");

        // Row 2: Sub-headers (CLOs, Totals)
        List<String> row2 = new ArrayList<>();
        row2.add(""); // Team Name
        row2.add(""); // Student Email
        row2.add(""); // Factor
        row2.add(""); // Capped Factor

        for (Assessment assessment : assessments) {
            Set<Integer> cloKeys = assessment.getCloMarks() != null ? new TreeSet<>(assessment.getCloMarks().keySet()) : Collections.emptySet();
            for (Integer cloKey : cloKeys) {
                row2.add(escapeCsv("CLO " + cloKey));
                row2.add("");
            }
            for (Integer cloKey : cloKeys) {
                row2.add(escapeCsv("CLO " + cloKey + " (W)"));
                row2.add("");
            }
            row2.add(escapeCsv("Total (Raw)"));
            row2.add(escapeCsv("Total (W)"));
        }
        row2.add(""); // Grand Total
        row2.add(""); // Grade
        csv.append(String.join(",", row2)).append("\r\n");

        // Row 3: Sub-headers (IAR, GAR)
        List<String> row3 = new ArrayList<>();
        row3.add(""); // Team Name
        row3.add(""); // Student Email
        row3.add(""); // Factor
        row3.add(""); // Capped Factor
        
        for (Assessment assessment : assessments) {
            Set<Integer> cloKeys = assessment.getCloMarks() != null ? new TreeSet<>(assessment.getCloMarks().keySet()) : Collections.emptySet();
            for (Integer cloKey : cloKeys) {
                row3.add(escapeCsv("IAR"));
                row3.add(escapeCsv("GAR"));
            }
            for (Integer cloKey : cloKeys) {
                row3.add(escapeCsv("IAR"));
                row3.add(escapeCsv("GAR"));
            }
            row3.add(""); // Total (Raw)
            row3.add(""); // Total (W)
        }
        row3.add(""); // Grand Total
        row3.add(""); // Grade
        csv.append(String.join(",", row3)).append("\r\n");

        // Data rows
        for (Student student : students) {
            Map<String, Object> data = studentOverallData.get(student.getId());
            List<String> row = new ArrayList<>();

            row.add(escapeCsv((String) data.get("teamName")));
            String displayName = student.getUsername() != null && !student.getUsername().isEmpty()
                ? student.getUsername()
                : student.getEmail();
            row.add(escapeCsv(displayName));

            Double factor = (Double) data.get("factor");
            row.add(factor != null && factor > 0 ? String.format("%.3f", factor) : "0.000");
            row.add(factor != null && factor >= 1.05 ? "1.050" : (factor != null && factor > 0 ? String.format("%.3f", factor) : "0.000"));

            @SuppressWarnings("unchecked")
            List<StudentAssessmentDataDto> assessmentDataList = (List<StudentAssessmentDataDto>) data.get("assessmentDataList");
            if (assessmentDataList != null) {
                for (StudentAssessmentDataDto assessmentData : assessmentDataList) {
                    Map<Integer, Double> cloSumsIndividualOnly = assessmentData.getCloSumsIndividualOnly();
                    Map<Integer, Double> cloSumsGroupOnly = assessmentData.getCloSumsGroupOnly();
                    Map<Integer, Double> cloWeightedSumsIndividualOnly = assessmentData.getCloWeightedSumsIndividualOnly();
                    Map<Integer, Double> cloWeightedSumsGroupOnly = assessmentData.getCloWeightedSumsGroupOnly();

                    Set<Integer> cloKeys = assessmentData.getAssessment().getCloMarks() != null
                        ? new TreeSet<>(assessmentData.getAssessment().getCloMarks().keySet())
                        : Collections.emptySet();

                    for (Integer cloKey : cloKeys) {
                        Double valI = cloSumsIndividualOnly != null ? cloSumsIndividualOnly.get(cloKey) : null;
                        Double valG = cloSumsGroupOnly != null ? cloSumsGroupOnly.get(cloKey) : null;
                        row.add(valI != null ? String.format("%.2f", valI) : "0.00");
                        row.add(valG != null ? String.format("%.2f", valG) : "0.00");
                    }
                    for (Integer cloKey : cloKeys) {
                        Double valI = cloWeightedSumsIndividualOnly != null ? cloWeightedSumsIndividualOnly.get(cloKey) : null;
                        Double valG = cloWeightedSumsGroupOnly != null ? cloWeightedSumsGroupOnly.get(cloKey) : null;
                        row.add(valI != null ? String.format("%.2f", valI) : "0.00");
                        row.add(valG != null ? String.format("%.2f", valG) : "0.00");
                    }

                    Double totalRaw = assessmentData.getTotalUnweightedMarks();
                    Double totalW = assessmentData.getTotalWeightedMarks();
                    row.add(totalRaw != null ? String.format("%.2f", totalRaw) : "0.00");
                    row.add(totalW != null ? String.format("%.2f", totalW) : "0.00");
                }
            }

            Double grandTotal = (Double) data.get("grandTotal");
            row.add(grandTotal != null ? String.format("%.2f", grandTotal) : "0.00");
            row.add(escapeCsv((String) data.get("grade")));

            csv.append(String.join(",", row)).append("\r\n");
        }

        byte[] bom = new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
        byte[] csvBytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] finalBytes = new byte[bom.length + csvBytes.length];
        System.arraycopy(bom, 0, finalBytes, 0, bom.length);
        System.arraycopy(csvBytes, 0, finalBytes, bom.length, csvBytes.length);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDispositionFormData("attachment", "Overall_Assessment_Data.csv");

        return ResponseEntity.ok()
            .headers(headers)
            .body(finalBytes);
    }

    private Map<String, Object> getOverallDataModelMap(
            Long selectedGroupId,
            String studentSearch,
            List<Long> selectedAssessmentIds,
            boolean paginate,
            int page,
            int size) {
        Long activeCourseId = getResolvedActiveCourseId();
        List<Group> groups = activeCourseId == null
            ? List.of()
            : groupRepository.findByCourseId(activeCourseId);
        List<Assessment> allAssessments = activeCourseId == null
            ? List.of()
            : assessmentService.findAllAssessmentsWithRubricsByCourseId(activeCourseId);
        List<Assessment> assessments = allAssessments;
        if (selectedAssessmentIds != null && !selectedAssessmentIds.isEmpty()) {
            Set<Long> selectedAssessmentIdSet = new java.util.HashSet<>(selectedAssessmentIds);
            assessments = allAssessments.stream()
                .filter(assessment -> assessment.getId() != null && selectedAssessmentIdSet.contains(assessment.getId()))
                .toList();
        }

        List<Student> students = activeCourseId == null
            ? new ArrayList<>()
            : new ArrayList<>(adminService.getAllStudents());

        String normalizedSearch = studentSearch == null ? "" : studentSearch.trim().toLowerCase();
        Map<Long, Group> studentGroupById = new HashMap<>();
        List<Student> filteredStudents = new ArrayList<>();
        for (Student student : students) {
            if (!ownsStudent(student)) {
                continue;
            }
            Group studentGroup = resolveStudentGroupForCourse(student, activeCourseId);
            if (student.getId() != null) {
                studentGroupById.put(student.getId(), studentGroup);
            }
            if (selectedGroupId != null) {
                if (studentGroup == null || studentGroup.getId() == null || !selectedGroupId.equals(studentGroup.getId())) {
                    continue;
                }
            }
            if (!normalizedSearch.isEmpty()) {
                String username = student.getUsername() != null ? student.getUsername().toLowerCase() : "";
                String email = student.getEmail() != null ? student.getEmail().toLowerCase() : "";
                String teamName = studentGroup != null && studentGroup.getGroupName() != null
                    ? studentGroup.getGroupName().toLowerCase()
                    : "";
                if (!username.contains(normalizedSearch)
                        && !email.contains(normalizedSearch)
                        && !teamName.contains(normalizedSearch)) {
                    continue;
                }
            }
            filteredStudents.add(student);
        }

        filteredStudents.sort((s1, s2) -> {
            String name1 = (s1.getUsername() != null && !s1.getUsername().trim().isEmpty()) ? s1.getUsername() : (s1.getEmail() != null ? s1.getEmail() : "");
            String name2 = (s2.getUsername() != null && !s2.getUsername().trim().isEmpty()) ? s2.getUsername() : (s2.getEmail() != null ? s2.getEmail() : "");
            return name1.compareToIgnoreCase(name2);
        });

        int totalStudents = filteredStudents.size();
        int safeSize = paginate ? Math.max(1, size) : Math.max(1, totalStudents);
        int totalPages = paginate ? Math.max(1, (int) Math.ceil((double) totalStudents / safeSize)) : 1;
        int currentPage = paginate ? Math.min(Math.max(page, 1), totalPages) : 1;
        int fromIndex = paginate ? Math.min((currentPage - 1) * safeSize, totalStudents) : 0;
        int toIndex = paginate ? Math.min(fromIndex + safeSize, totalStudents) : totalStudents;
        List<Student> visibleStudents = filteredStudents.subList(fromIndex, toIndex);
        
        Map<Long, Map<String, Object>> studentOverallData = new HashMap<>();
        Map<Long, Set<Integer>> assessmentCLOsWithGroupRubrics = new HashMap<>();
        Map<Long, Map<Long, List<Mark>>> marksByAssessmentId = new HashMap<>();
        Map<Long, StudentResultOverride> overridesByStudentId = new HashMap<>();
        if (activeCourseId != null && !visibleStudents.isEmpty()) {
            for (StudentResultOverride override : overrideRepository.findByStudentInAndCourseId(visibleStudents, activeCourseId)) {
                if (override.getStudent() != null && override.getStudent().getId() != null) {
                    overridesByStudentId.put(override.getStudent().getId(), override);
                }
            }
        }
        for (Assessment assessment : assessments) {
            assessmentCLOsWithGroupRubrics.put(assessment.getId(), calculateService.getCLOsWithGroupRubrics(assessment));
            marksByAssessmentId.put(assessment.getId(), calculateService.getMarksByEvaluatedStudentForAssessment(assessment));
        }
        
        for (Student student : visibleStudents) {
            Map<String, Object> data = new HashMap<>();
            Group studentGroup = student.getId() != null ? studentGroupById.get(student.getId()) : null;
            data.put("teamName", studentGroup != null ? studentGroup.getGroupName() : "No Team");
            
            Double resolvedFactor = calculateService.calculatePeerAssessmentFactorForStudent(student, activeCourseId);
            StudentResultOverride override = overridesByStudentId.get(student.getId());
            // An admin's override wins: the factor it sets is the one the totals are worked out with.
            Double factor = override != null && override.getOverriddenFactor() != null
                ? override.getOverriddenFactor()
                : resolvedFactor;
            Double grandTotal = 0.0;
            boolean evaluated = false;
            Map<Long, StudentAssessmentDataDto> assessmentDataById = new HashMap<>();
            for (Assessment assessment : assessments) {
                Map<Long, List<Mark>> marksByStudentId = marksByAssessmentId.get(assessment.getId());
                StudentAssessmentDataDto assessmentData = calculateService.calculateStudentAssessmentData(
                    student,
                    assessment,
                    factor,
                    marksByStudentId.get(student.getId())
                );
                assessmentDataById.put(assessment.getId(), assessmentData);
                grandTotal += assessmentData.getTotalMarks();
                evaluated = evaluated || assessmentData.isEvaluated();
            }

            boolean isOverridden = override != null && override.getOverriddenGrandTotal() != null;
            if (isOverridden) {
                grandTotal = override.getOverriddenGrandTotal();   // the total the admin set, and the grade that follows from it
            }
            
            data.put("factor", factor);
            data.put("grandTotal", grandTotal);
            data.put("isOverridden", isOverridden);
            data.put("evaluated", evaluated || isOverridden);
            data.put("grade", calculateService.calculateGrade(grandTotal));
            
            List<StudentAssessmentDataDto> details = new ArrayList<>();
            for(Assessment a : assessments) {
                details.add(assessmentDataById.get(a.getId()));
            }
            data.put("assessmentDataList", details);
            
            studentOverallData.put(student.getId(), data);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("students", visibleStudents);
        result.put("groups", groups);
        result.put("allAssessments", allAssessments);
        result.put("assessments", assessments);
        result.put("studentOverallData", studentOverallData);
        result.put("assessmentCLOsWithGroupRubrics", assessmentCLOsWithGroupRubrics);
        result.put("selectedGroupId", selectedGroupId);
        result.put("studentSearch", studentSearch);
        result.put("selectedAssessmentIds", selectedAssessmentIds == null ? List.of() : selectedAssessmentIds);
        result.put("currentPage", currentPage);
        result.put("pageSize", safeSize);
        result.put("totalStudents", totalStudents);
        result.put("showingFrom", totalStudents == 0 ? 0 : fromIndex + 1);
        result.put("showingTo", toIndex);
        result.put("totalPages", totalPages);
        result.put("hasPreviousPage", paginate && currentPage > 1);
        result.put("hasNextPage", paginate && currentPage < totalPages);
        return result;
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        String str = value.trim();
        // Prevent CSV Formula Injection (CWE-1236)
        if (str.startsWith("=") || str.startsWith("+") || str.startsWith("-") || 
            str.startsWith("@") || str.startsWith("\t") || str.startsWith("\r")) {
            str = "'" + str;
        }
        if (str.contains(",") || str.contains("\"") || str.contains("\n") || str.contains("\r")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }

    @GetMapping("/assessment/{assessmentId}")
    public String showAssessmentData(
            @PathVariable Long assessmentId,
            @RequestParam(required = false) Long groupId,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        Optional<Assessment> assessmentOpt = assessmentService.getAssessmentById(assessmentId);
        if (assessmentOpt.isEmpty() || !ownsAssessment(assessmentOpt.get())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Assessment not found or outside your active course.");
            return "redirect:/admin/data-views";
        }
        Assessment assessment = assessmentOpt.get();
        
        Long activeCourseId = getResolvedActiveCourseId();
        List<Group> allGroups = activeCourseId == null
            ? List.of()
            : groupRepository.findByCourseId(activeCourseId);
        model.addAttribute("allGroups", allGroups);
        model.addAttribute("assessment", assessment);
        
        Group selectedGroup = null;
        if (groupId != null) {
            selectedGroup = groupRepository.findById(groupId).orElse(null);
            if (selectedGroup != null && !ownsGroup(selectedGroup)) {
                selectedGroup = null;
            }
        } else if (!allGroups.isEmpty()) {
            selectedGroup = allGroups.get(0);
        }
        
        model.addAttribute("selectedGroup", selectedGroup);
        
        AssessmentDataDto assessmentData = new AssessmentDataDto();
        assessmentData.setAssessment(assessment);
        
        Map<String, List<Rubric>> rubricsByType = new HashMap<>();
        Map<Long, Map<Long, Map<String, Object>>> studentRubricDetails = new HashMap<>();
        Map<String, String> uniqueEvaluators = new java.util.LinkedHashMap<>();
        Map<Long, StudentAssessmentDataDto> studentAssessmentDataById = new HashMap<>();

        Map<Long, Object> studentGroupComments = new HashMap<>();
        Map<Long, Object> studentIndividualComments = new HashMap<>();
        
        if (assessment.getRubrics() != null) {
            for (Rubric rubric : assessment.getRubrics()) {
                String type = rubric.getAssessmentTypes() != null ? rubric.getAssessmentTypes() : "Other";
                rubricsByType.computeIfAbsent(type, k -> new ArrayList<>()).add(rubric);
            }
        }
        
        List<Student> students = resolveStudentsForGroup(selectedGroup);
        if (!students.isEmpty()) {
            
            students.sort((s1, s2) -> {
                String name1 = (s1.getUsername() != null && !s1.getUsername().trim().isEmpty()) ? s1.getUsername() : (s1.getEmail() != null ? s1.getEmail() : "");
                String name2 = (s2.getUsername() != null && !s2.getUsername().trim().isEmpty()) ? s2.getUsername() : (s2.getEmail() != null ? s2.getEmail() : "");
                return name1.compareToIgnoreCase(name2);
            });
            
            assessmentData = calculateService.calculateAssessmentData(assessment, students);
            if (assessmentData.getStudentDataList() != null) {
                for (StudentAssessmentDataDto studentData : assessmentData.getStudentDataList()) {
                    if (studentData != null && studentData.getStudent() != null && studentData.getStudent().getId() != null) {
                        studentAssessmentDataById.put(studentData.getStudent().getId(), studentData);
                    }
                }
            }
            
            for (Student student : students) {
                Map<Long, Map<String, Object>> rubricDetails = new HashMap<>();
                for (Rubric rubric : assessment.getRubrics()) {
                    Map<String, Object> details = new HashMap<>();
                    details.put("subRubricRatingsByEvaluator", calculateService.getSubRubricRatingsByEvaluator(student, assessment, rubric));
                    details.put("muSrmlnByEvaluator", calculateService.calculateMuSrmln(student, assessment, rubric));
                    details.put("muLn", calculateService.calculateMuLn(student, assessment, rubric));
                    details.put("evaluatorComments", calculateService.getEvaluatorCommentsForRubric(student, assessment, rubric));
                    details.put("directRatingsByEvaluator", calculateService.getDirectRatingsByEvaluator(student, assessment, rubric));
                    details.put("rfByEvaluator", calculateService.calculateRfByEvaluator(student, assessment, rubric));
                    details.put("muRf", calculateService.calculateMuRf(student, assessment, rubric));
                    details.put("drmax", calculateService.getDrmax(rubric));
                    rubricDetails.put(rubric.getId(), details);
                }
                studentRubricDetails.put(student.getId(), rubricDetails);

                studentGroupComments.put(student.getId(), calculateService.getGroupAssessmentComments(student, assessment));
                studentIndividualComments.put(student.getId(), calculateService.getIndividualAssessmentComments(student, assessment));
            }
            
            for (Student student : students) {
                StudentAssessmentDataDto studentData = studentAssessmentDataById.get(student.getId());
                if (studentData == null) {
                    continue;
                }
                for (Rubric rubric : assessment.getRubrics()) {
                    RubricCalculationDto rubricCalc = studentData.getRubricCalculations().get(rubric.getId());
                    if (rubricCalc != null && rubricCalc.getEvaluatorDetails() != null) {
                        for (Map<String, Object> evalDetail : rubricCalc.getEvaluatorDetails()) {
                            String evalType = (String) evalDetail.get("evaluatorType");
                            String evalName = (String) evalDetail.get("evaluatorName");
                            if (evalName != null) {
                                uniqueEvaluators.put(evalName, evalType);
                            }
                        }
                    }
                }
            }
        }
        
        model.addAttribute("assessmentData", assessmentData);
        model.addAttribute("studentGroupMap", adminService.getStudentGroupMapForActiveCourse(students));
        model.addAttribute("rubricsByType", rubricsByType);
        model.addAttribute("studentRubricDetails", studentRubricDetails);
        model.addAttribute("uniqueEvaluators", uniqueEvaluators);
        model.addAttribute("studentGroupComments", studentGroupComments);
        model.addAttribute("studentIndividualComments", studentIndividualComments);
        model.addAttribute("adminUsername", getLoggedInUsername());
        
        return "assessment_data_view";
    }

    @GetMapping("/edit-overrides")
    public String showEditOverridesPage(
            @RequestParam(required = false) Long groupId, 
            Model model) {
        
        Long activeCourseId = getResolvedActiveCourseId();
        List<Group> groups = activeCourseId == null
            ? List.of()
            : groupRepository.findByCourseId(activeCourseId);
        model.addAttribute("groups", groups);
        model.addAttribute("selectedGroupId", groupId);
        model.addAttribute("adminUsername", getLoggedInUsername());
        
        OverrideForm form = new OverrideForm();
        List<Student> students = new ArrayList<>();
        
        if (groupId != null && activeCourseId != null) {
            Group group = groupRepository.findById(groupId).orElse(null);
            if (group != null && ownsGroup(group)) {
                students = resolveStudentsForGroup(group);
                
                students.sort((s1, s2) -> {
                    String name1 = (s1.getUsername() != null && !s1.getUsername().trim().isEmpty()) ? s1.getUsername() : (s1.getEmail() != null ? s1.getEmail() : "");
                    String name2 = (s2.getUsername() != null && !s2.getUsername().trim().isEmpty()) ? s2.getUsername() : (s2.getEmail() != null ? s2.getEmail() : "");
                    return name1.compareToIgnoreCase(name2);
                });
                
                List<Assessment> assessments = assessmentService.findAllAssessmentsWithRubricsByCourseId(activeCourseId);
                
                for (Student student : students) {
                    Double calculatedFactor = 0.0;
                    if (!assessments.isEmpty()) {
                         calculatedFactor = calculateService.calculatePeerAssessmentFactorForStudent(student, activeCourseId);
                    }
                    Double calculatedTotal = calculateService.calculateGrandTotal(student, assessments, activeCourseId);
                    
                    Optional<StudentResultOverride> overrideOpt = overrideRepository.findByStudentAndCourseId(student, activeCourseId);
                    Double overrideFactor = overrideOpt.map(StudentResultOverride::getOverriddenFactor).orElse(null);
                    Double overrideTotal = overrideOpt.map(StudentResultOverride::getOverriddenGrandTotal).orElse(null);
                    
                    form.getUpdates().add(new StudentResultUpdate(student.getId(), overrideFactor, overrideTotal, calculatedFactor, calculatedTotal));
                }

                if (!students.isEmpty()) {
                    List<FactorOverrideHistory> auditLogs = factorOverrideHistoryRepository.findByStudentInAndCourseIdOrderByChangedAtDesc(students, activeCourseId);
                    model.addAttribute("recentAuditLogs", auditLogs);
                }
            }
        }
        
        model.addAttribute("students", students);
        model.addAttribute("form", form);
        
        return "edit_overrides";
    }

    @PostMapping("/edit-overrides/save")
    public String saveOverrides(@ModelAttribute OverrideForm form, @RequestParam(required = false) Long groupId) {
        Long activeCourseId = getResolvedActiveCourseId();
        if (activeCourseId == null) {
            return "redirect:/admin/data-views";
        }
        Course activeCourse = getResolvedActiveCourse(activeCourseId);
        for (StudentResultUpdate update : form.getUpdates()) {
            if (update.getStudentId() != null) {
                Student student = studentService.findStudentById(update.getStudentId()).orElse(null);
                if (student != null && ownsStudent(student)) {
                    boolean hasFactorOverride = update.getFactor() != null;
                    boolean hasTotalOverride = update.getGrandTotal() != null;
                    
                    Optional<StudentResultOverride> overrideOpt = overrideRepository.findByStudentAndCourseId(student, activeCourseId);
                    
                    if (!hasFactorOverride && !hasTotalOverride) {
                        if (overrideOpt.isPresent()) {
                            StudentResultOverride override = overrideOpt.get();
                            Double oldFactor = override.getOverriddenFactor();
                            Double oldTotal = override.getOverriddenGrandTotal();
                            overrideRepository.delete(override);
                            
                            FactorOverrideHistory history = new FactorOverrideHistory(
                                    student,
                                    oldFactor,
                                    null,
                                    oldTotal,
                                    null,
                                    getLoggedInUsername(),
                                    java.time.LocalDateTime.now(),
                                    update.getReason()
                            );
                            history.setCourse(activeCourse);
                            factorOverrideHistoryRepository.save(history);
                        }
                    } else {
                        StudentResultOverride override = overrideOpt.orElse(new StudentResultOverride());
                        
                        Double oldFactor = override.getOverriddenFactor();
                        Double oldTotal = override.getOverriddenGrandTotal();
                        
                        boolean isNew = (override.getId() == null);
                        boolean isChanged = isNew || 
                                            !java.util.Objects.equals(oldFactor, update.getFactor()) || 
                                            !java.util.Objects.equals(oldTotal, update.getGrandTotal());

                        override.setStudent(student);
                        override.setCourse(activeCourse);
                        override.setOverriddenFactor(update.getFactor());
                        override.setOverriddenGrandTotal(update.getGrandTotal());
                        
                        overrideRepository.save(override);
                        
                        if (isChanged) {
                            FactorOverrideHistory history = new FactorOverrideHistory(
                                    student,
                                    oldFactor,
                                    update.getFactor(),
                                    oldTotal,
                                    update.getGrandTotal(),
                                    getLoggedInUsername(),
                                    java.time.LocalDateTime.now(),
                                    update.getReason()
                            );
                            history.setCourse(activeCourse);
                            factorOverrideHistoryRepository.save(history);
                        }
                    }
                }
            }
        }
        return "redirect:/admin/data-views/edit-overrides?groupId=" + (groupId != null ? groupId : "");
    }

    @GetMapping("/calculate-factor")
    public String showCalculateFactorPage(Model model) {
        Long activeCourseId = getResolvedActiveCourseId();
        List<Assessment> assessments = activeCourseId == null
            ? List.of()
            : assessmentService.findAllAssessmentsWithRubricsByCourseId(activeCourseId);
        
        Map<Long, Double> currentWeightages = new HashMap<>();
        Map<Long, String> currentTypes = new HashMap<>();
        if (activeCourseId != null) {
            Course course = new Course();
            course.setId(activeCourseId);
            List<FactorWeightage> weightages = factorWeightageRepository.findByCourse(course);
            for (FactorWeightage fw : weightages) {
                currentWeightages.put(fw.getAssessment().getId(), fw.getWeightage());
                currentTypes.put(fw.getAssessment().getId(), fw.getFactorContributionType() != null ? fw.getFactorContributionType() : "BOTH");
            }
        }
        
        model.addAttribute("assessments", assessments);
        model.addAttribute("currentWeightages", currentWeightages);
        model.addAttribute("currentTypes", currentTypes);
        model.addAttribute("adminUsername", getLoggedInUsername());
        return "calculate_factor";
    }

    @PostMapping("/calculate-factor")
    public String calculateFactor(@RequestParam Map<String, String> params, RedirectAttributes redirectAttributes) {
        Long activeCourseId = getResolvedActiveCourseId();
        if (activeCourseId == null) return "redirect:/admin/data-views";
        
        Course dummyCourse = new Course();
        dummyCourse.setId(activeCourseId);
        
        double totalWeight = 0.0;
        List<FactorWeightage> pendingWeightages = new ArrayList<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("weight_")) {
                Long assessmentId;
                try {
                    assessmentId = Long.parseLong(key.substring(7));
                } catch (NumberFormatException e) {
                    continue;
                }
                String val = entry.getValue();
                if (val != null && !val.trim().isEmpty()) {
                    try {
                        double weight = Double.parseDouble(val);
                        if (weight > 0) {
                            Assessment assessment = assessmentService.getAssessmentById(assessmentId).orElse(null);
                            if (assessment == null || !ownsAssessment(assessment)) {
                                redirectAttributes.addFlashAttribute("errorMessage", "Unauthorized or invalid assessment selection.");
                                return "redirect:/admin/data-views/calculate-factor";
                            }
                            String typeVal = params.get("type_" + assessmentId);
                            if (typeVal == null || typeVal.trim().isEmpty()) {
                                typeVal = "BOTH";
                            }
                            totalWeight += weight;
                            FactorWeightage fw = new FactorWeightage(assessment.getCourse(), assessment, weight);
                            fw.setFactorContributionType(typeVal);
                            pendingWeightages.add(fw);
                        }
                    } catch (NumberFormatException e) {
                        // ignore invalid input
                    }
                }
            }
        }
        
        if (totalWeight > 0 && Math.abs(totalWeight - 100.0) >= 0.01) {
            redirectAttributes.addFlashAttribute("errorMessage", "Total weightage must be exactly 100%.");
            return "redirect:/admin/data-views/calculate-factor";
        }
        
        List<FactorWeightage> existing = factorWeightageRepository.findByCourse(dummyCourse);
        factorWeightageRepository.deleteAll(existing);
        factorWeightageRepository.saveAll(pendingWeightages);
        
        return "redirect:/admin/data-views/overall";
    }
}