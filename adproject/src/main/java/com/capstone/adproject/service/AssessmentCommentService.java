package com.capstone.adproject.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.AssessmentComment;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.repositories.AssessmentCommentRepository;
import com.capstone.adproject.repositories.StudentRepository;

@Service
public class AssessmentCommentService {
    
    @Autowired
    private AssessmentCommentRepository commentRepository;
    
    @Autowired
    private StudentRepository studentRepository;
    
    /**
     * Save a comment
     */
    @Transactional
    public AssessmentComment saveComment(AssessmentComment comment) {
        return commentRepository.save(comment);
    }
    
    /**
     * Get or create anonymous identifier for peer comments (assessment-level)
     * Ensures consistency - same evaluator always gets same teammate number for a student
     */
    public String getAnonymousIdentifier(Student evaluatedStudent, Long evaluatorId, Assessment assessment, String rubricAssessmentType) {
        // Check if assessment requires anonymous comments based on rubric type
        Boolean isAnonymous = assessment.getCommentsAnonymousForType(rubricAssessmentType);
        if (isAnonymous != null && !isAnonymous) {
            // Return the evaluator's real name
            return studentRepository.findById(evaluatorId)
                    .map(Student::getUsername)
                    .orElse("Student");
        }
        
        // Get all peer comments for this student in this assessment with this rubric type
        List<AssessmentComment> peerComments = commentRepository.findPeerCommentsForStudentInAssessment(
                evaluatedStudent, assessment).stream()
                .filter(c -> rubricAssessmentType.equalsIgnoreCase(c.getRubricAssessmentType()))
                .filter(c -> c.getRubricId() == null) // Only assessment-level comments
                .collect(Collectors.toList());
        
        // Check if this evaluator already has a teammate number
        for (AssessmentComment comment : peerComments) {
            if (comment.getEvaluatorId().equals(evaluatorId)) {
                return comment.getAnonymousIdentifier();
            }
        }
        
        // Count unique evaluators
        long uniqueEvaluators = peerComments.stream()
                .map(AssessmentComment::getEvaluatorId)
                .distinct()
                .count();
        
        // Assign new teammate number
        int nextTeammateNumber = (int) uniqueEvaluators + 1;
        return "Teammate " + nextTeammateNumber;
    }

    /**
     * ✅ NEW: Get or create anonymous identifier for rubric-specific comments
     * Each rubric comment question can have its own anonymity setting
     */
    public String getRubricAnonymousIdentifier(Student evaluatedStudent, Long evaluatorId, 
                                               Assessment assessment, Long rubricId, Integer commentIndex) {
        // Get all comments from this evaluator for this rubric question
        List<AssessmentComment> existingComments = commentRepository.findByEvaluatorAndStudentAndAssessment(
                evaluatorId,
                AssessmentComment.EvaluatorType.STUDENT,
                evaluatedStudent,
                assessment).stream()
                .filter(c -> c.getRubricId() != null && c.getRubricId().equals(rubricId))
                .filter(c -> c.getCommentIndex() != null && c.getCommentIndex().equals(commentIndex))
                .collect(Collectors.toList());
        
        // If this evaluator already has a comment for this rubric question, use the same identifier
        if (!existingComments.isEmpty()) {
            return existingComments.get(0).getAnonymousIdentifier();
        }
        
        // Get all peer comments for this student for this specific rubric question
        List<AssessmentComment> allRubricComments = commentRepository.findPeerCommentsForStudentInAssessment(
                evaluatedStudent, assessment).stream()
                .filter(c -> c.getRubricId() != null && c.getRubricId().equals(rubricId))
                .filter(c -> c.getCommentIndex() != null && c.getCommentIndex().equals(commentIndex))
                .collect(Collectors.toList());
        
        // Count unique evaluators for this specific rubric question
        long uniqueEvaluators = allRubricComments.stream()
                .map(AssessmentComment::getEvaluatorId)
                .distinct()
                .count();
        
        // Assign new teammate number
        int nextTeammateNumber = (int) uniqueEvaluators + 1;
        return "Teammate " + nextTeammateNumber;
    }

    @Transactional
    public void deleteComments(List<AssessmentComment> comments) {
        commentRepository.deleteAll(comments);
    }
    
    /**
     * Submit multiple peer comments (from student to peer)
     * ✅ UPDATED: Now sets rubricAssessmentType to "Individual Assessment"
     * @param commentTexts List of comment texts, one for each configured comment field
     */
    @Transactional
    public List<AssessmentComment> submitPeerComments(Student evaluator, Student evaluatedStudent, 
                                                       Assessment assessment, List<String> commentTexts) {
        
        List<AssessmentComment> savedComments = new ArrayList<>();
        
        // Get anonymous identifier (consistent across all comments from this evaluator)
        String identifier = getAnonymousIdentifier(evaluatedStudent, evaluator.getId(), assessment, "Individual Assessment");
        String evaluatorName = evaluator.getUsername();
        
        // Delete existing comments from this evaluator to this student in this assessment (Individual type only)
        List<AssessmentComment> existingComments = commentRepository.findByEvaluatorAndStudentAndAssessment(
                evaluator.getId(),
                AssessmentComment.EvaluatorType.STUDENT,
                evaluatedStudent,
                assessment).stream()
                .filter(c -> c.getRubricAssessmentType() == null || 
                             "Individual Assessment".equalsIgnoreCase(c.getRubricAssessmentType()))
                .filter(c -> c.getRubricId() == null) // Only assessment-level comments
                .collect(Collectors.toList());
        
        if (!existingComments.isEmpty()) {
            commentRepository.deleteAll(existingComments);
        }
        
        // Create new comments
        for (int i = 0; i < commentTexts.size(); i++) {
            String commentText = commentTexts.get(i);
            
            if (commentText == null || commentText.trim().isEmpty()) {
                continue; // Skip empty comments
            }
            
            AssessmentComment comment = new AssessmentComment();
            comment.setEvaluatorId(evaluator.getId());
            comment.setEvaluatorType(AssessmentComment.EvaluatorType.STUDENT);
            comment.setEvaluatorName(evaluatorName);
            comment.setEvaluatedStudent(evaluatedStudent);
            comment.setAssessment(assessment);
            comment.setCommentText(commentText.trim());
            comment.setAssessmentType(AssessmentComment.CommentAssessmentType.PEER);
            comment.setAnonymousIdentifier(identifier);
            comment.setCommentIndex(i);
            comment.setCommentLabel(assessment.getIndividualCommentLabel(i));
            comment.setRubricAssessmentType("Individual Assessment");
            
            savedComments.add(commentRepository.save(comment));
        }
        
        return savedComments;
    }
    
    /**
     * Submit multiple self comments
     * ✅ UPDATED: Now sets rubricAssessmentType to "Individual Assessment"
     * @param commentTexts List of comment texts, one for each configured comment field
     */
    @Transactional
    public List<AssessmentComment> submitSelfComments(Student student, Assessment assessment, List<String> commentTexts) {
        
        List<AssessmentComment> savedComments = new ArrayList<>();
        
        // Delete existing self comments (Individual type only)
        List<AssessmentComment> existingComments = commentRepository.findSelfCommentsForStudentInAssessment(
                student, assessment, student.getId()).stream()
                .filter(c -> c.getRubricAssessmentType() == null || 
                             "Individual Assessment".equalsIgnoreCase(c.getRubricAssessmentType()))
                .filter(c -> c.getRubricId() == null) // Only assessment-level comments
                .collect(Collectors.toList());
        
        if (!existingComments.isEmpty()) {
            commentRepository.deleteAll(existingComments);
        }
        
        // Create new comments
        for (int i = 0; i < commentTexts.size(); i++) {
            String commentText = commentTexts.get(i);
            
            if (commentText == null || commentText.trim().isEmpty()) {
                continue; // Skip empty comments
            }
            
            AssessmentComment comment = new AssessmentComment();
            comment.setEvaluatorId(student.getId());
            comment.setEvaluatorType(AssessmentComment.EvaluatorType.STUDENT);
            comment.setEvaluatorName(student.getUsername());
            comment.setEvaluatedStudent(student);
            comment.setAssessment(assessment);
            comment.setCommentText(commentText.trim());
            comment.setAssessmentType(AssessmentComment.CommentAssessmentType.SELF);
            comment.setAnonymousIdentifier("You (Self)");
            comment.setCommentIndex(i);
            comment.setCommentLabel(assessment.getIndividualCommentLabel(i));
            comment.setRubricAssessmentType("Individual Assessment");
            
            savedComments.add(commentRepository.save(comment));
        }
        
        return savedComments;
    }
    
    /**
     * ✅ NEW: Submit team evaluation comments (Group Assessment)
     * @param student The student submitting (all group members get same comments)
     * @param assessment The assessment
     * @param commentTexts List of comment texts
     */
    @Transactional
    public List<AssessmentComment> submitTeamComments(Student student, Assessment assessment, List<String> commentTexts) {
        
        List<AssessmentComment> savedComments = new ArrayList<>();
        
        // Delete existing team comments for this student
        List<AssessmentComment> existingComments = commentRepository.findByEvaluatorAndStudentAndAssessment(
                student.getId(),
                AssessmentComment.EvaluatorType.STUDENT,
                student,
                assessment).stream()
                .filter(c -> "Group Assessment".equalsIgnoreCase(c.getRubricAssessmentType()))
                .filter(c -> c.getRubricId() == null) // Only assessment-level comments
                .collect(Collectors.toList());
        
        if (!existingComments.isEmpty()) {
            commentRepository.deleteAll(existingComments);
        }
        
        // Create new team comments
        for (int i = 0; i < commentTexts.size(); i++) {
            String commentText = commentTexts.get(i);
            
            if (commentText == null || commentText.trim().isEmpty()) {
                continue;
            }
            
            AssessmentComment comment = new AssessmentComment();
            comment.setEvaluatorId(student.getId());
            comment.setEvaluatorType(AssessmentComment.EvaluatorType.STUDENT);
            comment.setEvaluatorName(student.getUsername());
            comment.setEvaluatedStudent(student);
            comment.setAssessment(assessment);
            comment.setCommentText(commentText.trim());
            comment.setAssessmentType(AssessmentComment.CommentAssessmentType.TEAM);
            comment.setAnonymousIdentifier("You (Team Evaluation)");
            comment.setCommentIndex(i);
            comment.setCommentLabel(assessment.getGroupCommentLabel(i));
            comment.setRubricAssessmentType("Group Assessment");
            
            savedComments.add(commentRepository.save(comment));
        }
        
        return savedComments;
    }
    
    /**
     * Submit lecturer comment
     * ✅ UPDATED: Now requires rubricType parameter to set rubricAssessmentType
     */
    @Transactional
    public AssessmentComment submitLecturerComment(Long lecturerId, String lecturerName,
                                                     Student evaluatedStudent, Assessment assessment,
                                                     String commentText, String rubricType) {
        
        // For lecturers, we maintain backward compatibility with single comment
        List<AssessmentComment> existing = commentRepository.findByEvaluatorAndStudentAndAssessment(
                lecturerId,
                AssessmentComment.EvaluatorType.LECTURER,
                evaluatedStudent,
                assessment).stream()
                .filter(c -> rubricType.equalsIgnoreCase(c.getRubricAssessmentType()))
                .filter(c -> c.getRubricId() == null) // Only assessment-level comments
                .collect(Collectors.toList());
        
        AssessmentComment comment;
        if (!existing.isEmpty()) {
            comment = existing.get(0);
            comment.setCommentText(commentText);
        } else {
            comment = new AssessmentComment();
            comment.setEvaluatorId(lecturerId);
            comment.setEvaluatorType(AssessmentComment.EvaluatorType.LECTURER);
            comment.setEvaluatorName(lecturerName);
            comment.setEvaluatedStudent(evaluatedStudent);
            comment.setAssessment(assessment);
            comment.setCommentText(commentText);
            comment.setAssessmentType(AssessmentComment.CommentAssessmentType.LECTURER_EVALUATION);
            comment.setCommentIndex(0);
            comment.setRubricAssessmentType(rubricType);
            comment.setAnonymousIdentifier(lecturerName); // Lecturers always show their name
        }
        
        return commentRepository.save(comment);
    }
    
    /**
     * Submit supervisor comment
     * ✅ UPDATED: Now requires rubricType parameter to set rubricAssessmentType
     */
    @Transactional
    public AssessmentComment submitSupervisorComment(Long supervisorId, String supervisorName,
                                                       Student evaluatedStudent, Assessment assessment,
                                                       String commentText, String rubricType) {
        
        // For supervisors, we maintain backward compatibility with single comment
        List<AssessmentComment> existing = commentRepository.findByEvaluatorAndStudentAndAssessment(
                supervisorId,
                AssessmentComment.EvaluatorType.SUPERVISOR,
                evaluatedStudent,
                assessment).stream()
                .filter(c -> rubricType.equalsIgnoreCase(c.getRubricAssessmentType()))
                .filter(c -> c.getRubricId() == null) // Only assessment-level comments
                .collect(Collectors.toList());
        
        AssessmentComment comment;
        if (!existing.isEmpty()) {
            comment = existing.get(0);
            comment.setCommentText(commentText);
        } else {
            comment = new AssessmentComment();
            comment.setEvaluatorId(supervisorId);
            comment.setEvaluatorType(AssessmentComment.EvaluatorType.SUPERVISOR);
            comment.setEvaluatorName(supervisorName);
            comment.setEvaluatedStudent(evaluatedStudent);
            comment.setAssessment(assessment);
            comment.setCommentText(commentText);
            comment.setAssessmentType(AssessmentComment.CommentAssessmentType.SUPERVISOR_EVALUATION);
            comment.setCommentIndex(0);
            comment.setRubricAssessmentType(rubricType);
            comment.setAnonymousIdentifier(supervisorName); // Supervisors always show their name
        }
        
        return commentRepository.save(comment);
    }
    
    /**
     * Get compiled comments for a student - organized by assessment
     */
    public Map<String, Object> getCompiledCommentsForStudent(Student student) {
    // Get ALL comments including rubric-specific ones
    List<AssessmentComment> allComments = commentRepository
        .findByEvaluatedStudent(student);
    
    System.out.println("===== FETCHING COMMENTS FOR STUDENT =====");
    System.out.println("Student: " + student.getUsername());
    System.out.println("Total comments found: " + allComments.size());
    
    int rubricComments = 0;
    int generalComments = 0;
    
    // Track by evaluator type
    int studentRubricComments = 0;
    int lecturerRubricComments = 0;
    int supervisorRubricComments = 0;
    
    for (AssessmentComment c : allComments) {
        if (c.getRubricId() != null) {
            rubricComments++;
            System.out.println("  - Rubric comment: rubricId=" + c.getRubricId() + 
                             ", label=" + c.getCommentLabel() +
                             ", evaluatorType=" + c.getEvaluatorType());
            
            // Count by type
            if (c.getEvaluatorType() == AssessmentComment.EvaluatorType.STUDENT) {
                studentRubricComments++;
            } else if (c.getEvaluatorType() == AssessmentComment.EvaluatorType.LECTURER) {
                lecturerRubricComments++;
            } else if (c.getEvaluatorType() == AssessmentComment.EvaluatorType.SUPERVISOR) {
                supervisorRubricComments++;
            }
        } else {
            generalComments++;
        }
    }
    
    System.out.println("General comments: " + generalComments);
    System.out.println("Rubric-specific comments: " + rubricComments);
    System.out.println("  - Student rubric comments: " + studentRubricComments);
    System.out.println("  - Lecturer rubric comments: " + lecturerRubricComments);
    System.out.println("  - Supervisor rubric comments: " + supervisorRubricComments);
    System.out.println("=========================================");
    
    // ✅ NEW: Process display names for all comments BEFORE grouping
    for (AssessmentComment comment : allComments) {
        String displayName = determineDisplayName(comment);
        comment.setDisplayName(displayName);
        
        System.out.println("Setting displayName: evaluatorType=" + comment.getEvaluatorType() + 
                         ", rubricId=" + comment.getRubricId() + 
                         ", evaluatorName=" + comment.getEvaluatorName() +
                         ", anonymousIdentifier=" + comment.getAnonymousIdentifier() +
                         ", displayName=" + displayName);
    }
    
    // Group by assessment, then by evaluator type
    Map<String, Map<String, List<AssessmentComment>>> commentsByAssessment = new LinkedHashMap<>();
    
    for (AssessmentComment comment : allComments) {
        String assessmentTitle = comment.getAssessment().getTitle();
        
        commentsByAssessment.putIfAbsent(assessmentTitle, new LinkedHashMap<>());
        Map<String, List<AssessmentComment>> categoryMap = commentsByAssessment.get(assessmentTitle);
        
        String category;
        if (comment.getEvaluatorType() == AssessmentComment.EvaluatorType.STUDENT) {
            if (comment.getAssessmentType() == AssessmentComment.CommentAssessmentType.SELF) {
                category = "self";
            } else if (comment.getAssessmentType() == AssessmentComment.CommentAssessmentType.TEAM) {
                category = "team";
            } else {
                category = "peer";
            }
        } else if (comment.getEvaluatorType() == AssessmentComment.EvaluatorType.LECTURER) {
            category = "lecturer";
        } else if (comment.getEvaluatorType() == AssessmentComment.EvaluatorType.SUPERVISOR) {
            category = "supervisor";
        } else {
            category = "other";
        }
        
        categoryMap.putIfAbsent(category, new ArrayList<>());
        categoryMap.get(category).add(comment);
    }
    
    // Sort comments by submission date and comment index
    for (Map<String, List<AssessmentComment>> categoryMap : commentsByAssessment.values()) {
        for (List<AssessmentComment> comments : categoryMap.values()) {
            comments.sort((c1, c2) -> {
                // First sort by rubricId (null first, then by rubricId)
                if (c1.getRubricId() == null && c2.getRubricId() != null) return -1;
                if (c1.getRubricId() != null && c2.getRubricId() == null) return 1;
                if (c1.getRubricId() != null && c2.getRubricId() != null) {
                    int rubricCompare = c1.getRubricId().compareTo(c2.getRubricId());
                    if (rubricCompare != 0) return rubricCompare;
                }
                // Then by comment index
                return Integer.compare(c1.getCommentIndex(), c2.getCommentIndex());
            });
        }
    }
    
    Map<String, Object> result = new HashMap<>();
    result.put("commentsByAssessment", commentsByAssessment);
    result.put("totalComments", allComments.size());
    
    return result;
}

/**
 * ✅ FIXED: Determine the correct display name for a comment based on anonymity settings
 */
private String determineDisplayName(AssessmentComment comment) {
    if (comment.getEvaluatorType() == AssessmentComment.EvaluatorType.STUDENT) {
        // Student comments: check assessment-level anonymity
        Assessment assessment = comment.getAssessment();
        if (assessment != null) {
            Boolean isAnonymous = assessment.getCommentsAnonymousForType(comment.getRubricAssessmentType());
            if (isAnonymous != null && !isAnonymous) {
                return comment.getEvaluatorName() != null ? comment.getEvaluatorName() : "Student";
            }
        }
        return comment.getAnonymousIdentifier() != null ? comment.getAnonymousIdentifier() : "Teammate";
        
    } else if (comment.getEvaluatorType() == AssessmentComment.EvaluatorType.LECTURER || 
               comment.getEvaluatorType() == AssessmentComment.EvaluatorType.SUPERVISOR) {
        
        // ✅ SIMPLE FIX: Check if anonymousIdentifier is "Lecturer" or "Supervisor"
        String anonymousId = comment.getAnonymousIdentifier();
        String evaluatorName = comment.getEvaluatorName();
        
        System.out.println("  -> Checking anonymity: anonymousId='" + anonymousId + 
                         "', evaluatorName='" + evaluatorName + "'");
        
        // If anonymousIdentifier is explicitly "Lecturer" or "Supervisor", it's anonymous
        if ("Lecturer".equals(anonymousId) || "Supervisor".equals(anonymousId)) {
            System.out.println("  -> Anonymous comment detected, using: " + anonymousId);
            return anonymousId;
        }
        
        // For rubric-specific comments with other anonymousIdentifier values, double-check with rubric settings
        if (comment.getRubricId() != null) {
            Assessment assessment = comment.getAssessment();
            if (assessment != null && assessment.getRubrics() != null) {
                Rubric rubric = assessment.getRubrics().stream()
                    .filter(r -> r.getId().equals(comment.getRubricId()))
                    .findFirst()
                    .orElse(null);
                
                if (rubric != null) {
                    Integer commentIndex = comment.getCommentIndex();
                    Boolean isAnonymous = rubric.isRubricCommentAnonymous(commentIndex != null ? commentIndex : 0);
                    
                    System.out.println("  -> Rubric comment anonymity check: rubricId=" + comment.getRubricId() + 
                                     ", commentIndex=" + commentIndex + ", isAnonymous=" + isAnonymous);
                    
                    if (isAnonymous != null && isAnonymous) {
                        // Use generic anonymous identifier
                        if (comment.getEvaluatorType() == AssessmentComment.EvaluatorType.LECTURER) {
                            return "Lecturer";
                        } else {
                            return "Supervisor";
                        }
                    }
                }
            }
        }
        
        // For assessment-level comments or when not anonymous, show actual name
        System.out.println("  -> Non-anonymous comment, using: " + evaluatorName);
        return evaluatorName != null ? evaluatorName : "Evaluator";
    }
    
    return comment.getEvaluatorName() != null ? comment.getEvaluatorName() : "Evaluator";
}

public List<AssessmentComment> getExistingComments(Long evaluatorId, 
                                                    AssessmentComment.EvaluatorType evaluatorType,
                                                    Student evaluatedStudent, 
                                                    Assessment assessment) {
    return commentRepository.findByEvaluatorAndStudentAndAssessment(
            evaluatorId, evaluatorType, evaluatedStudent, assessment);
}
    
    /**
     * Check if student has submitted all required comments for an assessment
     * ✅ UPDATED: Now checks both Individual and Group comment requirements
     */
    public boolean hasSubmittedAllComments(Student evaluator, Assessment assessment, List<Student> teamMembers) {
        int requiredIndividualCommentCount = assessment.getIndividualCommentCount();
        int requiredGroupCommentCount = assessment.getGroupCommentCount();
        
        // Check individual comments (peer/self)
        for (Student member : teamMembers) {
            List<AssessmentComment> comments = commentRepository.findByEvaluatorAndStudentAndAssessment(
                    evaluator.getId(),
                    AssessmentComment.EvaluatorType.STUDENT,
                    member,
                    assessment).stream()
                    .filter(c -> c.getRubricAssessmentType() == null || 
                                 "Individual Assessment".equalsIgnoreCase(c.getRubricAssessmentType()))
                    .filter(c -> c.getRubricId() == null) // Only assessment-level comments
                    .collect(Collectors.toList());
            
            // Check if all required individual comments are submitted for this team member
            if (comments.size() < requiredIndividualCommentCount) {
                return false;
            }
        }
        
        // Check team comments (group assessment)
        if (requiredGroupCommentCount > 0) {
            List<AssessmentComment> teamComments = commentRepository.findByEvaluatorAndStudentAndAssessment(
                    evaluator.getId(),
                    AssessmentComment.EvaluatorType.STUDENT,
                    evaluator,
                    assessment).stream()
                    .filter(c -> "Group Assessment".equalsIgnoreCase(c.getRubricAssessmentType()))
                    .filter(c -> c.getRubricId() == null) // Only assessment-level comments
                    .collect(Collectors.toList());
            
            if (teamComments.size() < requiredGroupCommentCount) {
                return false;
            }
        }
        
        return true;
    }
}