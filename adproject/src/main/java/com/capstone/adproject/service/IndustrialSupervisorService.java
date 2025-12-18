package com.capstone.adproject.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.AssessmentComment;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.IndustrialSupervisor;
import com.capstone.adproject.model.Mark;
import com.capstone.adproject.model.Rating;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.SubRubric;
import com.capstone.adproject.repositories.AssessmentCommentRepository;
import com.capstone.adproject.repositories.GroupRepository;
import com.capstone.adproject.repositories.IndustrialSupervisorRepository;
import com.capstone.adproject.repositories.MarkRepository;
import com.capstone.adproject.repositories.RatingRepository;

@Service
@Transactional
public class IndustrialSupervisorService {
    
    private final IndustrialSupervisorRepository industrialSupervisorRepository;
    private final GroupRepository groupRepository;
    private final PasswordEncoder passwordEncoder;
    
    @Autowired
    private MarkRepository markRepository;
    
    @Autowired
    private AssessmentCommentRepository commentRepository;
    
    @Autowired
    private RatingRepository ratingRepository;
    
    public IndustrialSupervisorService(
            IndustrialSupervisorRepository industrialSupervisorRepository,
            GroupRepository groupRepository,
            PasswordEncoder passwordEncoder) {
        this.industrialSupervisorRepository = industrialSupervisorRepository;
        this.groupRepository = groupRepository;
        this.passwordEncoder = passwordEncoder;
    }
    
    /**
     * Find industrial supervisor by username
     */
    public Optional<IndustrialSupervisor> findByUsername(String username) {
        return industrialSupervisorRepository.findByUsername(username);
    }
    
    /**
     * Find industrial supervisor by email
     */
    public Optional<IndustrialSupervisor> findByEmail(String email) {
        return industrialSupervisorRepository.findByEmail(email);
    }
    
    /**
     * Get all groups assigned to a specific industrial supervisor
     */
    public List<Group> getAssignedGroups(Long supervisorId) {
        return groupRepository.findByIndustrialSupervisorId(supervisorId);
    }
    
    /**
     * Get all groups assigned to a specific industrial supervisor by username
     */
    public List<Group> getAssignedGroupsByUsername(String username) {
        Optional<IndustrialSupervisor> supervisor = findByUsername(username);
        if (supervisor.isPresent()) {
            return getAssignedGroups(supervisor.get().getId());
        }
        return List.of();
    }
    
    /**
     * Save or update industrial supervisor
     */
    public IndustrialSupervisor save(IndustrialSupervisor supervisor) {
        return industrialSupervisorRepository.save(supervisor);
    }
    
    /**
     * Create new industrial supervisor with encoded password
     */
    public IndustrialSupervisor createSupervisor(String username, String password, String email) {
        IndustrialSupervisor supervisor = new IndustrialSupervisor();
        supervisor.setUsername(username);
        supervisor.setPassword(passwordEncoder.encode(password));
        supervisor.setEmail(email);
        return save(supervisor);
    }
    
    /**
     * Check if username exists
     */
    public boolean existsByUsername(String username) {
        return industrialSupervisorRepository.existsByUsername(username);
    }
    
    /**
     * Check if email exists
     */
    public boolean existsByEmail(String email) {
        return industrialSupervisorRepository.existsByEmail(email);
    }
    
    // ========== CONTINUOUS EVALUATION METHODS ==========
    
    /**
     * Check if evaluation exists for group
     */
    public boolean hasEvaluationForGroup(Long supervisorId, Long groupId, Long assessmentId) {
        Long count = markRepository.countBySupervisorIdAndAssessmentId(supervisorId, assessmentId);
        return count != null && count > 0;
    }
    
    /**
     * Calculate evaluation progress for a group
     */
    public Map<String, Object> getEvaluationProgress(
            Long supervisorId, Group group, Assessment assessment) {
        
        Map<String, Object> progress = new HashMap<>();
        
        // Get group and individual rubrics
        List<Rubric> groupRubrics = assessment.getRubrics().stream()
            .filter(r -> "Group Assessment".equals(r.getAssessmentTypes()))
            .collect(Collectors.toList());
        
        List<Rubric> individualRubrics = assessment.getRubrics().stream()
            .filter(r -> "Individual Assessment".equals(r.getAssessmentTypes()))
            .collect(Collectors.toList());
        
        // Count total rubrics/sub-rubrics that need evaluation
        int totalGroupItems = countEvaluationItems(groupRubrics);
        int totalIndividualItems = countEvaluationItems(individualRubrics);
        
        // Check group evaluation completion
        List<Student> students = group.getStudents();
        if (students.isEmpty()) {
            progress.put("status", "NO_STUDENTS");
            progress.put("percentage", 0.0);
            return progress;
        }
        
        Student firstStudent = students.get(0);
        
        // Count completed group items (check first student as proxy for group)
        int completedGroupItems = countCompletedItems(
            supervisorId, firstStudent.getId(), assessment.getId(), groupRubrics);
        
        boolean groupComplete = (totalGroupItems > 0 && completedGroupItems >= totalGroupItems);
        
        // Check group comments
        int groupCommentCount = assessment.getGroupCommentCount();
        long existingGroupComments = commentRepository
            .findSupervisorCommentsForStudent(supervisorId, firstStudent.getId(), assessment.getId())
            .stream()
            .filter(c -> "Group Assessment".equals(c.getRubricAssessmentType()))
            .count();
        
        boolean groupCommentsComplete = (groupCommentCount == 0 || existingGroupComments >= groupCommentCount);
        
        // Count students evaluated
        int studentsEvaluated = 0;
        for (Student student : students) {
            int completedItems = countCompletedItems(
                supervisorId, student.getId(), assessment.getId(), individualRubrics);
            
            long existingComments = commentRepository
                .findSupervisorCommentsForStudent(supervisorId, student.getId(), assessment.getId())
                .stream()
                .filter(c -> "Individual Assessment".equals(c.getRubricAssessmentType()))
                .count();
            
            int individualCommentCount = assessment.getIndividualCommentCount();
            boolean commentsComplete = (individualCommentCount == 0 || existingComments >= individualCommentCount);
            
            if (totalIndividualItems > 0 && completedItems >= totalIndividualItems && commentsComplete) {
                studentsEvaluated++;
            }
        }
        
        // Calculate overall completion
        boolean groupEvaluationComplete = groupComplete && groupCommentsComplete;
        boolean allStudentsComplete = (studentsEvaluated >= students.size());
        
        // Calculate percentage
        int totalParts = (totalGroupItems > 0 ? 1 : 0) + students.size();
        int completedParts = (groupEvaluationComplete ? 1 : 0) + studentsEvaluated;
        double percentage = totalParts > 0 ? (completedParts * 100.0 / totalParts) : 0.0;
        
        // Determine status
        String status;
        if (percentage >= 100.0) {
            status = "COMPLETED";
        } else if (percentage > 0) {
            status = "IN_PROGRESS";
        } else {
            status = "NOT_STARTED";
        }
        
        progress.put("status", status);
        progress.put("percentage", percentage);
        progress.put("groupComplete", groupEvaluationComplete);
        progress.put("studentsEvaluated", studentsEvaluated);
        progress.put("totalStudents", students.size());
        progress.put("completedParts", completedParts);
        progress.put("totalParts", totalParts);
        
        return progress;
    }
    
    /**
     * Count total evaluation items (rubrics + sub-rubrics) that need ratings
     */
    private int countEvaluationItems(List<Rubric> rubrics) {
        int count = 0;
        for (Rubric rubric : rubrics) {
            if (rubric.hasSubRubrics()) {
                count += rubric.getSubRubrics().size();
            } else if (rubric.hasDirectRatings()) {
                count += 1;
            }
        }
        return count;
    }
    
    /**
     * Count completed items for a student
     */
    private int countCompletedItems(
            Long supervisorId, Long studentId, Long assessmentId, List<Rubric> rubrics) {
        
        // Get all marks for this student from this supervisor
        List<Mark> marks = markRepository.findByEvaluatorStudentIdAndEvaluatedStudentIdAndAssessmentId(
            studentId, studentId, assessmentId);
        
        // Filter to supervisor marks only
        marks = marks.stream()
            .filter(m -> m.getSupervisorId() != null && m.getSupervisorId().equals(supervisorId))
            .collect(Collectors.toList());
        
        // Count unique rubric/sub-rubric IDs
        Set<Long> completedRubrics = new HashSet<>();
        Set<Long> completedSubRubrics = new HashSet<>();
        
        for (Mark mark : marks) {
            if (mark.getSubRubric() != null) {
                completedSubRubrics.add(mark.getSubRubric().getId());
            } else if (mark.getRubric() != null) {
                completedRubrics.add(mark.getRubric().getId());
            }
        }
        
        // Count how many required items are completed
        int completed = 0;
        for (Rubric rubric : rubrics) {
            if (rubric.hasSubRubrics()) {
                for (SubRubric sr : rubric.getSubRubrics()) {
                    if (completedSubRubrics.contains(sr.getId())) {
                        completed++;
                    }
                }
            } else if (rubric.hasDirectRatings()) {
                if (completedRubrics.contains(rubric.getId())) {
                    completed++;
                }
            }
        }
        
        return completed;
    }
    
    /**
     * Save continuous evaluation using existing Mark and AssessmentComment entities
     */
    @Transactional
    public void saveContinuousEvaluation(
            IndustrialSupervisor supervisor,
            Group group,
            Assessment assessment,
            Map<String, String> formData) {
        
        List<Student> students = group.getStudents();
        if (students.isEmpty()) {
            throw new RuntimeException("No students in group");
        }
        
        // Get group and individual rubrics
        List<Rubric> groupRubrics = assessment.getRubrics().stream()
            .filter(r -> "Group Assessment".equals(r.getAssessmentTypes()))
            .collect(Collectors.toList());
        
        List<Rubric> individualRubrics = assessment.getRubrics().stream()
            .filter(r -> "Individual Assessment".equals(r.getAssessmentTypes()))
            .collect(Collectors.toList());
        
        // Parse group evaluation data
        Map<Long, Long> groupRubricRatings = extractRatings(formData, "group_rubric");
        Map<Long, Long> groupSubRubricRatings = extractRatings(formData, "group_subrubric");
        List<String> groupComments = extractComments(formData, "group_comment", 
            assessment.getGroupCommentCount());
        
        // Parse group rubric-specific comments
        Map<Long, Map<Integer, String>> groupRubricComments = extractRubricComments(
            formData, "group", groupRubrics);
        
        // Save group evaluation for ALL students
        if (!groupRubricRatings.isEmpty() || !groupSubRubricRatings.isEmpty()) {
            for (Student student : students) {
                saveMarksForStudent(supervisor, student, student, assessment, 
                    groupRubricRatings, groupSubRubricRatings, "Group Assessment", true);
            }
        }
        
        // Save group comments for ALL students (only if there are non-empty comments)
        boolean hasGroupComments = groupComments.stream().anyMatch(c -> c != null && !c.trim().isEmpty());
        if (hasGroupComments) {
            for (Student student : students) {
                saveCommentsForStudent(supervisor, student, assessment, 
                    groupComments, "Group Assessment");
            }
        }
        
        // Save group rubric-specific comments for ALL students
        if (!groupRubricComments.isEmpty()) {
            for (Student student : students) {
                saveRubricCommentsForStudent(supervisor, student, assessment, 
                    groupRubricComments, "Group Assessment");
            }
        }
        
        // Parse and save each student's individual evaluation
        for (Student student : students) {
            String prefix = "student_" + student.getId();
            
            Map<Long, Long> studentRubricRatings = extractRatings(formData, prefix + "_rubric");
            Map<Long, Long> studentSubRubricRatings = extractRatings(formData, prefix + "_subrubric");
            List<String> studentComments = extractComments(formData, prefix + "_comment", 
                assessment.getIndividualCommentCount());
            
            // Parse individual rubric-specific comments
            Map<Long, Map<Integer, String>> studentRubricComments = extractRubricComments(
                formData, prefix, individualRubrics);
            
            // Save individual marks
            if (!studentRubricRatings.isEmpty() || !studentSubRubricRatings.isEmpty()) {
                // For individual: evaluatorStudent = student, evaluatedStudent = student
                saveMarksForStudent(supervisor, student, student, assessment, 
                    studentRubricRatings, studentSubRubricRatings, "Individual Assessment", true);
            }
            
            // Save individual comments (only if there are non-empty comments)
            boolean hasIndividualComments = studentComments.stream().anyMatch(c -> c != null && !c.trim().isEmpty());
            if (hasIndividualComments) {
                saveCommentsForStudent(supervisor, student, assessment, 
                    studentComments, "Individual Assessment");
            }
            
            // Save individual rubric-specific comments
            if (!studentRubricComments.isEmpty()) {
                saveRubricCommentsForStudent(supervisor, student, assessment, 
                    studentRubricComments, "Individual Assessment");
            }
        }
    }
    
    /**
     * Extract ratings from form data
     */
    private Map<Long, Long> extractRatings(Map<String, String> formData, String prefix) {
        Map<Long, Long> ratings = new HashMap<>();
        
        for (Map.Entry<String, String> entry : formData.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(prefix + "[") && key.endsWith("]")) {
                try {
                    Long id = Long.parseLong(key.substring(
                        key.indexOf("[") + 1, key.indexOf("]")));
                    Long ratingId = Long.parseLong(entry.getValue());
                    ratings.put(id, ratingId);
                } catch (NumberFormatException e) {
                    // Skip invalid
                }
            }
        }
        
        return ratings;
    }
    
    /**
     * Extract comments from form data
     */
    private List<String> extractComments(Map<String, String> formData, String prefix, int count) {
        List<String> comments = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String key = prefix + "[" + i + "]";
            comments.add(formData.getOrDefault(key, ""));
        }
        return comments;
    }
    
    /**
     * Extract rubric-specific comments from form data
     * Returns Map<rubricId, Map<commentIndex, commentText>>
     */
    private Map<Long, Map<Integer, String>> extractRubricComments(
            Map<String, String> formData, String prefix, List<Rubric> rubrics) {
        
        Map<Long, Map<Integer, String>> rubricCommentsMap = new HashMap<>();
        
        for (Rubric rubric : rubrics) {
            Integer commentCount = rubric.getRubricCommentCount();
            if (commentCount == null || commentCount == 0) {
                continue;
            }
            
            Map<Integer, String> commentsForRubric = new HashMap<>();
            
            for (int i = 0; i < commentCount; i++) {
                // Field name: group_rubric_{rubricId}_comment_{index}
                // or: student_{studentId}_rubric_{rubricId}_comment_{index}
                String key = prefix + "_rubric_" + rubric.getId() + "_comment_" + i;
                String commentText = formData.getOrDefault(key, "");
                
                if (commentText != null && !commentText.trim().isEmpty()) {
                    commentsForRubric.put(i, commentText);
                }
            }
            
            if (!commentsForRubric.isEmpty()) {
                rubricCommentsMap.put(rubric.getId(), commentsForRubric);
            }
        }
        
        return rubricCommentsMap;
    }
    
    /**
     * Save marks for a student using existing Mark entity
     */
    @Transactional
    private void saveMarksForStudent(
            IndustrialSupervisor supervisor,
            Student evaluatorStudent,
            Student evaluatedStudent,
            Assessment assessment,
            Map<Long, Long> rubricRatings,
            Map<Long, Long> subRubricRatings,
            String assessmentType,
            boolean isSupervisorMark) {
        
        // Delete existing marks for this student
        List<Mark> existingMarks = markRepository.findByEvaluatorStudentAndEvaluatedStudentAndAssessment(
            evaluatorStudent, evaluatedStudent, assessment);
        
        existingMarks = existingMarks.stream()
            .filter(m -> m.getSupervisorId() != null && m.getSupervisorId().equals(supervisor.getId()))
            .filter(m -> assessmentType.equals(m.getAssessmentType()))
            .collect(Collectors.toList());
        
        markRepository.deleteAll(existingMarks);
        
        List<Mark> newMarks = new ArrayList<>();
        
        // Save rubric ratings
        for (Map.Entry<Long, Long> entry : rubricRatings.entrySet()) {
            Long rubricId = entry.getKey();
            Long ratingId = entry.getValue();
            
            Rating rating = ratingRepository.findById(ratingId).orElse(null);
            if (rating == null) continue;
            
            Mark mark = new Mark();
            mark.setEvaluatorStudent(evaluatorStudent);
            mark.setEvaluatedStudent(evaluatedStudent);
            mark.setAssessment(assessment);
            mark.setRubric(rating.getRubric());
            mark.setRating(rating);
            mark.setAssessmentType(assessmentType);
            mark.setStatus(Mark.SubmissionStatus.SUBMITTED);
            mark.setSupervisorId(supervisor.getId());
            mark.setIsSupervisorMark(isSupervisorMark);
            
            newMarks.add(mark);
        }
        
        // Save sub-rubric ratings
        for (Map.Entry<Long, Long> entry : subRubricRatings.entrySet()) {
            Long subRubricId = entry.getKey();
            Long ratingId = entry.getValue();
            
            Rating rating = ratingRepository.findById(ratingId).orElse(null);
            if (rating == null) continue;
            
            Mark mark = new Mark();
            mark.setEvaluatorStudent(evaluatorStudent);
            mark.setEvaluatedStudent(evaluatedStudent);
            mark.setAssessment(assessment);
            mark.setSubRubric(rating.getSubRubric());
            mark.setRubric(rating.getSubRubric().getRubric());
            mark.setRating(rating);
            mark.setAssessmentType(assessmentType);
            mark.setStatus(Mark.SubmissionStatus.SUBMITTED);
            mark.setSupervisorId(supervisor.getId());
            mark.setIsSupervisorMark(isSupervisorMark);
            
            newMarks.add(mark);
        }
        
        markRepository.saveAll(newMarks);
    }
    
    @Transactional
private void saveCommentsForStudent(
        IndustrialSupervisor supervisor,
        Student student,
        Assessment assessment,
        List<String> comments,
        String rubricAssessmentType) {
    
    // ✅ FIXED: Check if comments are configured before processing
    List<String> commentLabels = "Group Assessment".equals(rubricAssessmentType) ?
        assessment.getGroupCommentLabels() :
        assessment.getIndividualCommentLabels();
    
    if (commentLabels == null || commentLabels.isEmpty()) {
        // No comments configured for this assessment type - skip entirely
        return;
    }
    
    // Build list of non-empty comments first
    List<AssessmentComment> newComments = new ArrayList<>();
    
    for (int i = 0; i < comments.size(); i++) {
        String commentText = comments.get(i);
        if (commentText == null || commentText.trim().isEmpty()) continue;
        
        AssessmentComment comment = new AssessmentComment();
        comment.setEvaluatedStudent(student);
        comment.setEvaluatorId(supervisor.getId());
        comment.setEvaluatorType(AssessmentComment.EvaluatorType.SUPERVISOR);
        comment.setEvaluatorName(supervisor.getUsername());
        comment.setAssessment(assessment);
        comment.setCommentText(commentText);
        comment.setAssessmentType(AssessmentComment.CommentAssessmentType.SUPERVISOR_EVALUATION);
        comment.setCommentIndex(i);
        comment.setRubricAssessmentType(rubricAssessmentType);
        comment.setRubricId(null);
        
        // Set label and anonymity
        if ("Group Assessment".equals(rubricAssessmentType)) {
            comment.setCommentLabel(assessment.getGroupCommentLabel(i));
            
            Boolean isAnonymous = assessment.isGroupCommentAnonymous(i);
            if (isAnonymous != null && isAnonymous) {
                comment.setAnonymousIdentifier("Supervisor");
            } else {
                comment.setAnonymousIdentifier(supervisor.getUsername());
            }
        } else {
            comment.setCommentLabel(assessment.getIndividualCommentLabel(i));
            
            Boolean isAnonymous = assessment.isIndividualCommentAnonymous(i);
            if (isAnonymous != null && isAnonymous) {
                comment.setAnonymousIdentifier("Supervisor");
            } else {
                comment.setAnonymousIdentifier(supervisor.getUsername());
            }
        }
        
        newComments.add(comment);
    }
    
    // Only delete and save if we have actual comments to save
    if (!newComments.isEmpty()) {
        // Delete existing comments for this specific assessment type (general comments only)
        List<AssessmentComment> existingComments = commentRepository
            .findSupervisorCommentsForStudent(supervisor.getId(), student.getId(), assessment.getId())
            .stream()
            .filter(c -> rubricAssessmentType.equals(c.getRubricAssessmentType()))
            .filter(c -> c.getRubricId() == null)
            .collect(Collectors.toList());
        
        if (!existingComments.isEmpty()) {
            commentRepository.deleteAll(existingComments);
        }
        
        commentRepository.saveAll(newComments);
    }
}
    
    @Transactional
private void saveRubricCommentsForStudent(
        IndustrialSupervisor supervisor,
        Student student,
        Assessment assessment,
        Map<Long, Map<Integer, String>> rubricComments,
        String rubricAssessmentType) {
    
    for (Map.Entry<Long, Map<Integer, String>> entry : rubricComments.entrySet()) {
        Long rubricId = entry.getKey();
        Map<Integer, String> commentsForRubric = entry.getValue();
        
        // Delete existing rubric-specific comments for this rubric
        List<AssessmentComment> existingComments = commentRepository
            .findSupervisorCommentsForStudent(supervisor.getId(), student.getId(), assessment.getId())
            .stream()
            .filter(c -> rubricId.equals(c.getRubricId()))
            .collect(Collectors.toList());
        
        if (!existingComments.isEmpty()) {
            commentRepository.deleteAll(existingComments);
        }
        
        // Get the rubric for labels and anonymity settings
        Rubric rubric = assessment.getRubrics().stream()
            .filter(r -> r.getId().equals(rubricId))
            .findFirst()
            .orElse(null);
        
        if (rubric == null) {
            continue;
        }
        
        // Save new rubric-specific comments
        List<AssessmentComment> newComments = new ArrayList<>();
        
        for (Map.Entry<Integer, String> commentEntry : commentsForRubric.entrySet()) {
            Integer commentIndex = commentEntry.getKey();
            String commentText = commentEntry.getValue();
            
            if (commentText == null || commentText.trim().isEmpty()) {
                continue;
            }
            
            AssessmentComment comment = new AssessmentComment();
            comment.setEvaluatedStudent(student);
            comment.setEvaluatorId(supervisor.getId());
            comment.setEvaluatorType(AssessmentComment.EvaluatorType.SUPERVISOR);
            comment.setEvaluatorName(supervisor.getUsername());
            comment.setAssessment(assessment);
            comment.setCommentText(commentText.trim());
            comment.setAssessmentType(AssessmentComment.CommentAssessmentType.SUPERVISOR_EVALUATION);
            comment.setCommentIndex(commentIndex);
            comment.setRubricAssessmentType(rubricAssessmentType);
            comment.setRubricId(rubricId);
            comment.setCommentLabel(rubric.getRubricCommentLabel(commentIndex));
            
            // Check rubric's anonymity setting for this specific comment
            Boolean isAnonymous = rubric.isRubricCommentAnonymous(commentIndex);
            if (isAnonymous != null && isAnonymous) {
                comment.setAnonymousIdentifier("Supervisor");
            } else {
                comment.setAnonymousIdentifier(supervisor.getUsername());
            }
            
            newComments.add(comment);
        }
        
        if (!newComments.isEmpty()) {
            commentRepository.saveAll(newComments);
        }
    }
}
    
    /**
     * Load existing evaluation data
     */
    public Map<String, Object> loadExistingEvaluation(
            IndustrialSupervisor supervisor, Group group, Assessment assessment) {
        
        Map<String, Object> data = new HashMap<>();
        
        List<Student> students = group.getStudents();
        if (students.isEmpty()) {
            return data;
        }
        
        Student firstStudent = students.get(0);
        
        // Load group evaluation (from first student)
        List<Mark> groupMarks = markRepository
            .findByEvaluatorStudentAndEvaluatedStudentAndAssessment(firstStudent, firstStudent, assessment)
            .stream()
            .filter(m -> m.getSupervisorId() != null && m.getSupervisorId().equals(supervisor.getId()))
            .filter(m -> "Group Assessment".equals(m.getAssessmentType()))
            .collect(Collectors.toList());
        
        Map<Long, Long> groupRubricRatings = new HashMap<>();
        Map<Long, Long> groupSubRubricRatings = new HashMap<>();
        
        for (Mark mark : groupMarks) {
            if (mark.getSubRubric() != null) {
                groupSubRubricRatings.put(mark.getSubRubric().getId(), mark.getRating().getId());
            } else if (mark.getRubric() != null) {
                groupRubricRatings.put(mark.getRubric().getId(), mark.getRating().getId());
            }
        }
        
        data.put("groupRubricRatings", groupRubricRatings);
        data.put("groupSubRubricRatings", groupSubRubricRatings);
        
        // Load group comments (general assessment-level)
        List<AssessmentComment> groupComments = commentRepository
            .findSupervisorCommentsForStudent(supervisor.getId(), firstStudent.getId(), assessment.getId())
            .stream()
            .filter(c -> "Group Assessment".equals(c.getRubricAssessmentType()))
            .filter(c -> c.getRubricId() == null) // Only general comments
            .sorted((a, b) -> Integer.compare(a.getCommentIndex(), b.getCommentIndex()))
            .collect(Collectors.toList());
        
        List<String> groupCommentTexts = groupComments.stream()
            .map(AssessmentComment::getCommentText)
            .collect(Collectors.toList());
        
        data.put("groupComments", groupCommentTexts);
        
        // Load group rubric-specific comments
        Map<Long, Map<Integer, String>> groupRubricComments = new HashMap<>();
        List<AssessmentComment> allGroupComments = commentRepository
            .findSupervisorCommentsForStudent(supervisor.getId(), firstStudent.getId(), assessment.getId())
            .stream()
            .filter(c -> "Group Assessment".equals(c.getRubricAssessmentType()))
            .filter(c -> c.getRubricId() != null) // Only rubric-specific comments
            .collect(Collectors.toList());
        
        for (AssessmentComment comment : allGroupComments) {
            Long rubricId = comment.getRubricId();
            groupRubricComments.putIfAbsent(rubricId, new HashMap<>());
            groupRubricComments.get(rubricId).put(comment.getCommentIndex(), comment.getCommentText());
        }
        
        data.put("groupRubricComments", groupRubricComments);
        
        // Load each student's data
        Map<Long, Map<String, Object>> studentData = new HashMap<>();
        
        for (Student student : students) {
            Map<String, Object> sData = new HashMap<>();
            
            // Load marks
            List<Mark> studentMarks = markRepository
                .findByEvaluatedStudentAndAssessment(student, assessment)
                .stream()
                .filter(m -> m.getSupervisorId() != null && m.getSupervisorId().equals(supervisor.getId()))
                .filter(m -> "Individual Assessment".equals(m.getAssessmentType()))
                .collect(Collectors.toList());
            
            Map<Long, Long> studentRubricRatings = new HashMap<>();
            Map<Long, Long> studentSubRubricRatings = new HashMap<>();
            
            for (Mark mark : studentMarks) {
                if (mark.getSubRubric() != null) {
                    studentSubRubricRatings.put(mark.getSubRubric().getId(), mark.getRating().getId());
                } else if (mark.getRubric() != null) {
                    studentRubricRatings.put(mark.getRubric().getId(), mark.getRating().getId());
                }
            }
            
            sData.put("rubricRatings", studentRubricRatings);
            sData.put("subRubricRatings", studentSubRubricRatings);
            
            // Load comments (general assessment-level)
            List<AssessmentComment> studentComments = commentRepository
                .findSupervisorCommentsForStudent(supervisor.getId(), student.getId(), assessment.getId())
                .stream()
                .filter(c -> "Individual Assessment".equals(c.getRubricAssessmentType()))
                .filter(c -> c.getRubricId() == null) // Only general comments
                .sorted((a, b) -> Integer.compare(a.getCommentIndex(), b.getCommentIndex()))
                .collect(Collectors.toList());
            
            List<String> studentCommentTexts = studentComments.stream()
                .map(AssessmentComment::getCommentText)
                .collect(Collectors.toList());
            
            sData.put("comments", studentCommentTexts);
            
            // Load individual rubric-specific comments
            Map<Long, Map<Integer, String>> studentRubricComments = new HashMap<>();
            List<AssessmentComment> allStudentComments = commentRepository
                .findSupervisorCommentsForStudent(supervisor.getId(), student.getId(), assessment.getId())
                .stream()
                .filter(c -> "Individual Assessment".equals(c.getRubricAssessmentType()))
                .filter(c -> c.getRubricId() != null) // Only rubric-specific comments
                .collect(Collectors.toList());
            
            for (AssessmentComment comment : allStudentComments) {
                Long rubricId = comment.getRubricId();
                studentRubricComments.putIfAbsent(rubricId, new HashMap<>());
                studentRubricComments.get(rubricId).put(comment.getCommentIndex(), comment.getCommentText());
            }
            
            sData.put("rubricComments", studentRubricComments);
            
            studentData.put(student.getId(), sData);
        }
        
        data.put("studentData", studentData);
        
        return data;
    }
}