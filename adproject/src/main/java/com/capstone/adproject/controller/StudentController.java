package com.capstone.adproject.controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import com.capstone.adproject.model.Criteria;
import com.capstone.adproject.model.Deadline;
import com.capstone.adproject.model.Mark;
import com.capstone.adproject.model.Rating;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.repositories.AssessmentRepository;
import com.capstone.adproject.repositories.CriteriaRepository;
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
    private CriteriaRepository criteriaRepository;
    
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

        Function<Assessment, Map<String, Map<String, List<Object>>>> groupAssessmentComponents = assessment -> {
            
            Stream<Object> combinedComponents = Stream.concat(
                assessment.getRubrics().stream().map(r -> (Object)r),
                assessment.getCriteria().stream().map(c -> (Object)c)
            );
            
            List<Object> components = combinedComponents.collect(Collectors.toList());

            Map<String, List<Object>> byEvalType = components.stream()
                .collect(Collectors.groupingBy(c -> {
                    if (c instanceof Rubric) {
                        return ((Rubric) c).getEvaluationType();
                    } else if (c instanceof Criteria) {
                        return ((Criteria) c).getEvaluationType();
                    }
                    return "Unknown";
                }, LinkedHashMap::new, Collectors.toList()));

            Map<String, Map<String, List<Object>>> finalGroup = new LinkedHashMap<>();
            
            byEvalType.forEach((evalType, evalComponents) -> {
                Map<String, List<Object>> byAssessType = evalComponents.stream()
                    .collect(Collectors.groupingBy(c -> {
                        if (c instanceof Rubric) {
                            return ((Rubric) c).getAssessmentTypes();
                        } else if (c instanceof Criteria) {
                            return ((Criteria) c).getAssessmentTypes();
                        }
                        return "Unknown";
                    }, LinkedHashMap::new, Collectors.toList()));
                
                finalGroup.put(evalType, byAssessType);
            });

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
        model.addAttribute("allDeadlines", allDeadlines); // ⭐ UPDATED: Pass all deadlines
        model.addAttribute("deadlines", studentDeadlines); // Keep for compatibility
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
            List<Rubric> individualRubrics = assessment.getRubrics().stream()
                    .filter(r -> r.getEvaluationType() != null && 
                            r.getEvaluationType().equalsIgnoreCase("Individual"))
                    .collect(Collectors.toList());
            
            List<Criteria> individualCriteria = assessment.getCriteria().stream()
                    .filter(c -> c.getEvaluationType() != null && 
                            c.getEvaluationType().equalsIgnoreCase("Individual"))
                    .collect(Collectors.toList());
            
            boolean hasIndividualComponents = !individualRubrics.isEmpty() || !individualCriteria.isEmpty();
            
            if (hasIndividualComponents && isAssessmentOpen(assessment, "STUDENT")) {
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
    
    /**
     * ⭐ UPDATED: Show form with re-evaluation detection
     */
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
        
        List<Rubric> assessmentRubrics = assessment.getRubrics().stream()
                .filter(r -> r.getEvaluationType() != null && 
                            r.getEvaluationType().equalsIgnoreCase("Individual"))
                .collect(Collectors.toList());
        
        List<Criteria> assessmentCriteria = assessment.getCriteria().stream()
                .filter(c -> c.getEvaluationType() != null && 
                            c.getEvaluationType().equalsIgnoreCase("Individual"))
                .collect(Collectors.toList());
        
        // ⭐ Check if previous evaluation exists
        List<Mark> existingMarks = markService.getMarksForStudentEvaluation(evaluator, evaluatedStudent, assessment);
        boolean hasExistingEvaluation = !existingMarks.isEmpty();
        
        // Get existing comment
        Optional<AssessmentComment> existingComment = commentService.getExistingComment(
            evaluator.getId(),
            AssessmentComment.EvaluatorType.STUDENT,
            evaluatedStudent,
            assessment
        );
        
        // Prepare existing marks map for form pre-population
        Map<Long, Mark> existingRubricMarksMap = existingMarks.stream()
                .filter(m -> m.getRubric() != null)
                .collect(Collectors.toMap(m -> m.getRubric().getId(), m -> m));
        
        Map<Long, Mark> existingCriteriaMarksMap = existingMarks.stream()
                .filter(m -> m.getCriteria() != null)
                .collect(Collectors.toMap(m -> m.getCriteria().getId(), m -> m));
        
        Deadline deadline = getAssessmentDeadline(assessment, "STUDENT");
        
        model.addAttribute("assessment", assessment);
        model.addAttribute("rubrics", assessmentRubrics);
        model.addAttribute("criteria", assessmentCriteria);
        model.addAttribute("evaluator", evaluator);
        model.addAttribute("evaluatedStudent", evaluatedStudent);
        model.addAttribute("existingRubricMarks", existingRubricMarksMap);
        model.addAttribute("existingCriteriaMarks", existingCriteriaMarksMap);
        model.addAttribute("existingComment", existingComment.orElse(null));
        model.addAttribute("hasExistingEvaluation", hasExistingEvaluation); // ⭐ NEW
        model.addAttribute("isSelfAssessment", evaluator.getId().equals(evaluatedStudent.getId()));
        model.addAttribute("deadline", deadline);
        
        return "peer_assessment_form";
    }
    
    /**
     * ⭐ CORRECTED: Submit with all required Mark fields set.
     */
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
        
        // Validate overall comment
        String overallComment = formData.get("overallComment");
        
        if (overallComment == null || overallComment.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Overall comment is required!");
            return "redirect:/student/assessment/" + assessmentId + "/evaluate/" + studentId;
        }
        
        if (overallComment.trim().length() < 20) {
            redirectAttributes.addFlashAttribute("error", "Comment must be at least 20 characters!");
            return "redirect:/student/assessment/" + assessmentId + "/evaluate/" + studentId;
        }
        
        // ⭐ DELETE EXISTING MARKS (if any) - this replaces old evaluation
        List<Mark> existingMarks = markService.getMarksForStudentEvaluation(evaluator, evaluatedStudent, assessment);
        if (!existingMarks.isEmpty()) {
            markRepository.deleteAll(existingMarks);
        }
        
        List<Mark> marks = new ArrayList<>();
        
        // Handle RUBRIC ratings
        for (Map.Entry<String, String> entry : formData.entrySet()) {
            if (entry.getKey().startsWith("rating_")) {
                Long rubricId = Long.parseLong(entry.getKey().substring(7));
                Long ratingId = Long.parseLong(entry.getValue());
                
                Rubric rubric = rubricRepository.findById(rubricId)
                        .orElseThrow(() -> new RuntimeException("Rubric not found"));
                Rating rating = ratingRepository.findById(ratingId)
                        .orElseThrow(() -> new RuntimeException("Rating not found"));
                
                Mark mark = new Mark();
                mark.setEvaluatorStudent(evaluator);
                mark.setEvaluatedStudent(evaluatedStudent);
                mark.setAssessment(assessment);
                mark.setRubric(rubric); // <-- Critical for @PrePersist (to set CLO, EvalType, etc.)
                mark.setRating(rating); // <-- Critical for @PrePersist (to calculate MarkValue/CloMarks)
                
                // Set the basic assessment type which is used in the PrePersist
                mark.setAssessmentType(evaluator.getId().equals(evaluatedStudent.getId()) ? 
        "Self Assessment" : "Peer Assessment");
                
                mark.setStatus(Mark.SubmissionStatus.SUBMITTED);
                
                marks.add(mark);
            }
        }
        
        // Handle CRITERIA ratings
        for (Map.Entry<String, String> entry : formData.entrySet()) {
            if (entry.getKey().startsWith("criteria_")) {
                Long criteriaId = Long.parseLong(entry.getKey().substring(9));
                Integer ratingLevel = Integer.parseInt(entry.getValue());
                
                Criteria criteria = criteriaRepository.findById(criteriaId)
                        .orElseThrow(() -> new RuntimeException("Criteria not found"));
                
                Mark mark = new Mark();
                mark.setEvaluatorStudent(evaluator);
                mark.setEvaluatedStudent(evaluatedStudent);
                mark.setAssessment(assessment);
                mark.setCriteria(criteria); // <-- Critical for @PrePersist (to set CLO, EvalType, etc.)
                mark.setRatingLevel(ratingLevel); // <-- Critical for @PrePersist (to calculate MarkValue/CloMarks)
                
                // Set the basic assessment type which is used in the PrePersist
                mark.setAssessmentType(evaluator.getId().equals(evaluatedStudent.getId()) ? 
        "Self Assessment" : "Peer Assessment");
                
                mark.setStatus(Mark.SubmissionStatus.SUBMITTED);
                
                marks.add(mark);
            }
        }
        
        // Save new marks
        if (!marks.isEmpty()) {
            // This is where Hibernate triggers the @PrePersist method in the Mark entity
            markService.saveAllMarks(marks); 
        }
        
        // Save comment (will update if exists)
        boolean isSelf = evaluator.getId().equals(evaluatedStudent.getId());
        if (isSelf) {
            commentService.submitSelfComment(evaluator, assessment, overallComment.trim());
        } else {
            commentService.submitPeerComment(evaluator, evaluatedStudent, assessment, overallComment.trim());
        }
        
        redirectAttributes.addFlashAttribute("success", "Assessment and comment submitted successfully!");
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
        
        List<Mark> receivedMarks = new ArrayList<>();
        
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