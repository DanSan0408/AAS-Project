package com.capstone.adproject.controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.AssessmentComment;
import com.capstone.adproject.model.Deadline;
import com.capstone.adproject.model.Mark;
import com.capstone.adproject.model.Rating;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.repositories.AssessmentRepository;
import com.capstone.adproject.repositories.GroupRepository;
import com.capstone.adproject.repositories.MarkRepository;
import com.capstone.adproject.repositories.RatingRepository;
import com.capstone.adproject.repositories.RubricRepository;
import com.capstone.adproject.repositories.StudentRepository;
import com.capstone.adproject.service.AssessmentCommentService;
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.DeadlineService;
import com.capstone.adproject.service.MarkService;

@Controller
@RequestMapping("/student")
public class StudentController {
    
    @Autowired
    private AssessmentService assessmentService;
    
    @Autowired
    private DeadlineService deadlineService;
    
    @Autowired
    private StudentRepository studentRepository;
    
    @Autowired
    private AssessmentRepository assessmentRepository;
    
    @Autowired
    private RubricRepository rubricRepository;
    
    @Autowired
    private RatingRepository ratingRepository;
    
    @Autowired
    private MarkService markService;
    
    @Autowired
    private AssessmentCommentService commentService;
    
    @Autowired
    private GroupRepository groupRepository;
    
    @Autowired
    private MarkRepository markRepository;
    
    private boolean isAssessmentOpen(Assessment assessment, String assessorType) {
        List<Deadline> deadlines = deadlineService.getDeadlinesByAssessmentIdAndAssessorType(
            assessment.getId(), assessorType
        );
        
        if (deadlines.isEmpty()) {
            return false;
        }
        
        long nowMillis = System.currentTimeMillis();
        
        for (Deadline deadline : deadlines) {
            long openMillis = 0;
            if (deadline.getOpenDate() != null) {
                openMillis = deadline.getOpenDate().getTime();
            }
            
            long closeMillis = deadline.getDate().getTime();
            
            if (nowMillis >= openMillis && nowMillis < closeMillis) {
                return true;
            }
        }
        
        return false;
    }
    
    private Deadline getAssessmentDeadline(Assessment assessment, String assessorType) {
        List<Deadline> deadlines = deadlineService.getDeadlinesByAssessmentIdAndAssessorType(
            assessment.getId(), assessorType
        );
        return deadlines.isEmpty() ? null : deadlines.get(0);
    }
    
    @GetMapping("/home")
    public String studentHome(Model model, Principal principal) {
        
        Function<Object, Boolean> isRubricType = component -> component instanceof Rubric;

        // Simplified grouping function, removing Criteria and EvaluationType logic
        Function<Assessment, Map<String, Map<String, List<Object>>>> groupAssessmentComponents = assessment -> {
            
            Map<String, Map<String, List<Object>>> finalGroup = new LinkedHashMap<>();
            final String DUMMY_KEY = "ASSESSMENT_GROUPING"; 
            
            // Get all Rubric components
            Stream<Object> combinedComponents = Stream.empty();
            if (assessment.getRubrics() != null) {
                combinedComponents = assessment.getRubrics().stream().map(r -> (Object)r);
            }
            
            List<Object> components = combinedComponents.collect(Collectors.toList());

            // Group components by Assessment Type (inner map)
            Map<String, List<Object>> byAssessType = components.stream()
                .collect(Collectors.groupingBy(c -> {
                    if (c instanceof Rubric) {
                        return ((Rubric) c).getAssessmentTypes(); 
                    }
                    return "Unknown";
                }, LinkedHashMap::new, Collectors.toList()));

            // Wrap in an outer map with a dummy key 
            finalGroup.put(DUMMY_KEY, byAssessType);

            return finalGroup;
        };
        
        Student currentStudent = studentRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Student not found"));
        
        List<Assessment> allAssessments = assessmentService.findAllAssessmentsWithRubrics();
        List<Deadline> allDeadlines = deadlineService.getAllDeadlines();
        List<Deadline> studentDeadlines = allDeadlines.stream()
            .filter(d -> "STUDENT".equals(d.getAssessorType()))
            .collect(Collectors.toList());
        
        boolean hasGroup = currentStudent.getGroup() != null;
        
        if (hasGroup) {
            Map<Long, Map<String, Object>> assessmentProgress = new HashMap<>();
            for (Assessment assessment : allAssessments) { 
                List<Student> teamMembers = studentRepository.findByGroup(currentStudent.getGroup());
                Map<String, Object> progress = markService.getAssessmentProgress(currentStudent, assessment, teamMembers);
                assessmentProgress.put(assessment.getId(), progress);
            }
            model.addAttribute("assessmentProgress", assessmentProgress);
        }
        
        Map<Long, Boolean> assessmentAccessMap = new HashMap<>();
        Map<Long, Deadline> assessmentDeadlineMap = new HashMap<>();
        for (Assessment assessment : allAssessments) {
            boolean isOpen = isAssessmentOpen(assessment, "STUDENT");
            assessmentAccessMap.put(assessment.getId(), isOpen);
            
            Deadline deadline = getAssessmentDeadline(assessment, "STUDENT");
            if (deadline != null) {
                assessmentDeadlineMap.put(assessment.getId(), deadline);
            }
        }
        
        model.addAttribute("allAssessments", allAssessments); 
        model.addAttribute("currentStudent", currentStudent);
        model.addAttribute("allDeadlines", allDeadlines); 
        model.addAttribute("deadlines", studentDeadlines); 
        model.addAttribute("hasGroup", hasGroup);
        model.addAttribute("assessmentAccessMap", assessmentAccessMap);
        model.addAttribute("assessmentDeadlineMap", assessmentDeadlineMap);
        model.addAttribute("groupAssessmentComponents", groupAssessmentComponents);
        model.addAttribute("isRubricType", isRubricType);
        
        return "student_home";
    }
    
    @GetMapping("/assessments")
    public String listAssessments(Model model, Principal principal) {
        
        Student currentStudent = studentRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Student not found"));
        
        if (currentStudent.getGroup() == null) {
            model.addAttribute("error", "You are not assigned to any group yet.");
            return "student_assessments";
        }
        
        List<Assessment> assessments = assessmentRepository.findAll();
        List<Assessment> peerSelfAssessments = new ArrayList<>();
        
        for (Assessment assessment : assessments) {
            // Simplified filtering logic - any assessment with rubrics is available
            List<Rubric> rubrics = assessment.getRubrics();
            
            boolean hasComponents = !rubrics.isEmpty(); 
            
            if (hasComponents && isAssessmentOpen(assessment, "STUDENT")) {
                peerSelfAssessments.add(assessment);
            }
        }
        
        model.addAttribute("assessments", peerSelfAssessments);
        model.addAttribute("currentStudent", currentStudent);
        model.addAttribute("group", currentStudent.getGroup());
        
        List<Student> teamMembers = studentRepository.findByGroup(currentStudent.getGroup());
        model.addAttribute("teamMembers", teamMembers);
        
        Map<Long, Deadline> assessmentDeadlineMap = new HashMap<>();
        for (Assessment assessment : peerSelfAssessments) {
            Deadline deadline = getAssessmentDeadline(assessment, "STUDENT");
            if (deadline != null) {
                assessmentDeadlineMap.put(assessment.getId(), deadline);
            }
        }
        model.addAttribute("assessmentDeadlineMap", assessmentDeadlineMap);
        
        return "student_assessments";
    }
    
   @GetMapping("/assessment/{assessmentId}/evaluate/{studentId}")
public String showPeerAssessmentForm(@PathVariable Long assessmentId, 
                                     @PathVariable Long studentId,
                                     Model model, 
                                     Principal principal,
                                     RedirectAttributes redirectAttributes) {
    
    Student evaluator = studentRepository.findByUsername(principal.getName())
            .orElseThrow(() -> new RuntimeException("Student not found"));
    
    Assessment assessment = assessmentRepository.findById(assessmentId)
            .orElseThrow(() -> new RuntimeException("Assessment not found"));
    
    if (!isAssessmentOpen(assessment, "STUDENT")) {
        redirectAttributes.addFlashAttribute("error", 
            "This assessment is not currently open for evaluation.");
        return "redirect:/student/assessments";
    }
    
    Student evaluatedStudent = studentRepository.findById(studentId)
            .orElseThrow(() -> new RuntimeException("Evaluated student not found"));
    
    if (evaluator.getGroup() == null || evaluatedStudent.getGroup() == null ||
        !evaluator.getGroup().getId().equals(evaluatedStudent.getGroup().getId())) {
        redirectAttributes.addFlashAttribute("error", "You can only evaluate members of your own group.");
        return "redirect:/student/assessments";
    }
    
    // Get all rubrics for this assessment
    List<Rubric> assessmentRubrics = assessment.getRubrics();
    
    // Check if previous evaluation exists
    List<Mark> existingMarks = markService.getMarksForStudentEvaluation(evaluator, evaluatedStudent, assessment);
    boolean hasExistingEvaluation = !existingMarks.isEmpty();
    
    // Get existing comments
    List<AssessmentComment> existingComments = commentService.getExistingComments(
        evaluator.getId(),
        AssessmentComment.EvaluatorType.STUDENT,
        evaluatedStudent,
        assessment
    );
    
    // Sort comments by index
    existingComments.sort((c1, c2) -> Integer.compare(c1.getCommentIndex(), c2.getCommentIndex()));
    
    // Prepare existing marks maps for BOTH direct rubric ratings AND sub-rubric ratings
    Map<Long, Mark> existingRubricMarksMap = existingMarks.stream()
            .filter(m -> m.getRubric() != null && m.getSubRubric() == null)
            .collect(Collectors.toMap(m -> m.getRubric().getId(), m -> m, (m1, m2) -> m1));
    
    Map<Long, Mark> existingSubRubricMarksMap = existingMarks.stream()
            .filter(m -> m.getSubRubric() != null)
            .collect(Collectors.toMap(m -> m.getSubRubric().getId(), m -> m, (m1, m2) -> m1));
    
    Deadline deadline = getAssessmentDeadline(assessment, "STUDENT");
    
    model.addAttribute("assessment", assessment);
    model.addAttribute("rubrics", assessmentRubrics);
    model.addAttribute("evaluator", evaluator);
    model.addAttribute("evaluatedStudent", evaluatedStudent);
    model.addAttribute("existingRubricMarks", existingRubricMarksMap);
    model.addAttribute("existingSubRubricMarks", existingSubRubricMarksMap);
    model.addAttribute("existingComments", existingComments); // NEW: Pass existing comments
    model.addAttribute("hasExistingEvaluation", hasExistingEvaluation);
    model.addAttribute("isSelfAssessment", evaluator.getId().equals(evaluatedStudent.getId()));
    model.addAttribute("deadline", deadline);
    
    return "peer_assessment_form";
}
    
    @PostMapping("/assessment/{assessmentId}/evaluate/{studentId}/submit")
public String submitPeerAssessment(@PathVariable Long assessmentId,
                                   @PathVariable Long studentId,
                                   @RequestParam Map<String, String> formData,
                                   Principal principal,
                                   RedirectAttributes redirectAttributes) {
    
    Student evaluator = studentRepository.findByUsername(principal.getName())
            .orElseThrow(() -> new RuntimeException("Student not found"));
    
    Assessment assessment = assessmentRepository.findById(assessmentId)
            .orElseThrow(() -> new RuntimeException("Assessment not found"));
    
    if (!isAssessmentOpen(assessment, "STUDENT")) {
        redirectAttributes.addFlashAttribute("error", 
            "This assessment has been closed. Submissions are no longer accepted.");
        return "redirect:/student/assessments";
    }
    
    Student evaluatedStudent = studentRepository.findById(studentId)
            .orElseThrow(() -> new RuntimeException("Evaluated student not found"));
    
    // ========== EXTRACT AND VALIDATE COMMENTS ==========
    int commentCount = assessment.getCommentCount();
    int minLength = assessment.getCommentMinLength();
    List<String> commentTexts = new ArrayList<>();
    
    for (int i = 0; i < commentCount; i++) {
        String commentKey = "comment_" + i;
        String commentText = formData.get(commentKey);
        
        // Validate each comment
        if (commentText == null || commentText.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", 
                "Comment " + (i + 1) + " is required!");
            return "redirect:/student/assessment/" + assessmentId + "/evaluate/" + studentId;
        }
        
        if (commentText.trim().length() < minLength) {
            redirectAttributes.addFlashAttribute("error", 
                "Comment " + (i + 1) + " must be at least " + minLength + " characters!");
            return "redirect:/student/assessment/" + assessmentId + "/evaluate/" + studentId;
        }
        
        commentTexts.add(commentText.trim());
    }
    
    // ========== DELETE EXISTING MARKS (if any) ==========
    List<Mark> existingMarks = markService.getMarksForStudentEvaluation(evaluator, evaluatedStudent, assessment);
    if (!existingMarks.isEmpty()) {
        markRepository.deleteAll(existingMarks);
    }
    
    // ========== PROCESS RUBRIC RATINGS ==========
    List<Mark> marks = new ArrayList<>();
    boolean isSelfAssessment = evaluator.getId().equals(evaluatedStudent.getId());
    String assessmentType = isSelfAssessment ? "Self Assessment" : "Peer Assessment";
    
    for (Map.Entry<String, String> entry : formData.entrySet()) {
        String paramName = entry.getKey();
        String paramValue = entry.getValue();
        
        // Skip comment parameters
        if (paramName.startsWith("comment_")) {
            continue;
        }
        
        try {
            // CASE 1: Direct Rating (rubric_X)
            if (paramName.startsWith("rubric_")) {
                Long rubricId = Long.parseLong(paramName.substring("rubric_".length()));
                Long ratingId = Long.parseLong(paramValue);
                
                Rubric rubric = rubricRepository.findById(rubricId)
                        .orElseThrow(() -> new RuntimeException("Rubric not found: " + rubricId));
                Rating rating = ratingRepository.findById(ratingId)
                        .orElseThrow(() -> new RuntimeException("Rating not found: " + ratingId));
                
                Mark mark = new Mark();
                mark.setEvaluatorStudent(evaluator);
                mark.setEvaluatedStudent(evaluatedStudent);
                mark.setAssessment(assessment);
                mark.setRubric(rubric);
                mark.setSubRubric(null);
                mark.setRating(rating);
                mark.setMarkValue(rating.getMarks() != null ? rating.getMarks().doubleValue() : 0.0);
                mark.setClo(rubric.getClo());
                mark.setCloMarks(rating.getMarks() != null ? rating.getMarks().doubleValue() : 0.0);
                mark.setAssessmentType(assessmentType);
                mark.setStatus(Mark.SubmissionStatus.FINAL);
                
                marks.add(mark);
            }
            // CASE 2: Sub-Rubric Rating (subRubric_X)
            else if (paramName.startsWith("subRubric_")) {
                Long subRubricId = Long.parseLong(paramName.substring("subRubric_".length()));
                Long ratingId = Long.parseLong(paramValue);
                
                com.capstone.adproject.model.SubRubric subRubric = null;
                Rubric parentRubric = null;
                
                for (Rubric r : assessment.getRubrics()) {
                    if (r.getSubRubrics() != null) {
                        for (com.capstone.adproject.model.SubRubric sr : r.getSubRubrics()) {
                            if (sr.getId().equals(subRubricId)) {
                                subRubric = sr;
                                parentRubric = r;
                                break;
                            }
                        }
                    }
                    if (subRubric != null) break;
                }
                
                if (subRubric == null) {
                    throw new RuntimeException("SubRubric not found: " + subRubricId);
                }
                
                Rating rating = ratingRepository.findById(ratingId)
                        .orElseThrow(() -> new RuntimeException("Rating not found: " + ratingId));
                
                Mark mark = new Mark();
                mark.setEvaluatorStudent(evaluator);
                mark.setEvaluatedStudent(evaluatedStudent);
                mark.setAssessment(assessment);
                mark.setRubric(parentRubric);
                mark.setSubRubric(subRubric);
                mark.setRating(rating);
                mark.setMarkValue(rating.getMarks() != null ? rating.getMarks().doubleValue() : 0.0);
                mark.setClo(parentRubric.getClo());
                
                // Calculate pro-rated CLO marks
                if (parentRubric.getMarks() != null && 
                    parentRubric.getMarks().compareTo(java.math.BigDecimal.ZERO) > 0 &&
                    subRubric.getMarks() != null && 
                    subRubric.getMarks().compareTo(java.math.BigDecimal.ZERO) > 0 &&
                    parentRubric.getCloMarks() != null) {
                    
                    double subRubricProportion = subRubric.getMarks()
                        .divide(parentRubric.getMarks(), 4, java.math.RoundingMode.HALF_UP)
                        .doubleValue();
                    
                    double cloMarksForSubRubric = parentRubric.getCloMarks() * subRubricProportion;
                    
                    double achievementProportion = rating.getMarks()
                        .divide(subRubric.getMarks(), 4, java.math.RoundingMode.HALF_UP)
                        .doubleValue();
                    
                    double cloMarksAwarded = cloMarksForSubRubric * achievementProportion;
                    
                    mark.setCloMarks(cloMarksAwarded);
                } else {
                    mark.setCloMarks(rating.getMarks() != null ? rating.getMarks().doubleValue() : 0.0);
                }
                
                mark.setAssessmentType(assessmentType);
                mark.setStatus(Mark.SubmissionStatus.FINAL);
                
                marks.add(mark);
            }
        } catch (NumberFormatException e) {
            // Skip invalid parameters
        }
    }
    
    // Validate that at least one rating was selected
    if (marks.isEmpty()) {
        redirectAttributes.addFlashAttribute("error", 
            "Please select at least one rating before submitting!");
        return "redirect:/student/assessment/" + assessmentId + "/evaluate/" + studentId;
    }
    
    // Save marks
    markService.saveAllMarks(marks);
    
    // ========== SAVE COMMENTS ==========
    if (isSelfAssessment) {
        commentService.submitSelfComments(evaluator, assessment, commentTexts);
    } else {
        commentService.submitPeerComments(evaluator, evaluatedStudent, assessment, commentTexts);
    }
    
    redirectAttributes.addFlashAttribute("success", 
        "Assessment and comments submitted successfully!");
    return "redirect:/student/assessments";
}
    
    @GetMapping("/comments")
    public String viewMyComments(Model model, Principal principal) {
        
        Student currentStudent = studentRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Student not found"));
        
        Map<String, Object> commentsData = commentService.getCompiledCommentsForStudent(currentStudent);
        
        @SuppressWarnings("unchecked")
        Map<String, Map<String, List<AssessmentComment>>> commentsByAssessment = 
            (Map<String, Map<String, List<AssessmentComment>>>) commentsData.get("commentsByAssessment");
        
        Integer totalComments = (Integer) commentsData.get("totalComments");
        
        int peerCommentCount = 0;
        int lecturerCommentCount = 0;
        int supervisorCommentCount = 0;
        
        if (commentsByAssessment != null) {
            for (Map<String, List<AssessmentComment>> assessmentComments : commentsByAssessment.values()) {
                peerCommentCount += assessmentComments.getOrDefault("peer", List.of()).size();
                lecturerCommentCount += assessmentComments.getOrDefault("lecturer", List.of()).size();
                supervisorCommentCount += assessmentComments.getOrDefault("supervisor", List.of()).size();
            }
        }
        
        model.addAttribute("commentsByAssessment", commentsByAssessment);
        model.addAttribute("totalComments", totalComments);
        model.addAttribute("peerCommentCount", peerCommentCount);
        model.addAttribute("lecturerCommentCount", lecturerCommentCount);
        model.addAttribute("supervisorCommentCount", supervisorCommentCount);
        model.addAttribute("currentStudent", currentStudent);
        
        return "student_comments";
    }
    
    @GetMapping("/assessment/{assessmentId}/results")
    public String viewAssessmentResults(@PathVariable Long assessmentId,
                                             Model model,
                                             Principal principal) {
        
        Student currentStudent = studentRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Student not found"));
        
        Assessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new RuntimeException("Assessment not found"));
        
        List<Mark> receivedMarks = markRepository.findByEvaluatedStudentAndAssessment(currentStudent, assessment);
        
        Map<Rubric, Double> averageScores = receivedMarks.stream()
                .filter(m -> m.getRubric() != null)
                .collect(Collectors.groupingBy(Mark::getRubric,
                        Collectors.averagingDouble(Mark::getMarkValue)));
        
        Double totalMarks = markService.calculateTotalMarks(currentStudent, assessment);
        Map<Integer, Double> cloMarks = markService.calculateCloMarks(currentStudent, assessment);
        
        model.addAttribute("assessment", assessment);
        model.addAttribute("receivedMarks", receivedMarks);
        model.addAttribute("averageScores", averageScores);
        model.addAttribute("totalMarks", totalMarks);
        model.addAttribute("cloMarks", cloMarks);
        model.addAttribute("currentStudent", currentStudent);
        
        return "assessment_results";
    }
}