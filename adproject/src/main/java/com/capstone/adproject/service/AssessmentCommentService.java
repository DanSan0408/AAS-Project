package com.capstone.adproject.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.AssessmentComment;
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
     * Get or create anonymous identifier for peer comments
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
            comment.setRubricAssessmentType("Individual Assessment"); // ✅ NEW
            
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
            comment.setRubricAssessmentType("Individual Assessment"); // ✅ NEW
            
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
            comment.setAssessmentType(AssessmentComment.CommentAssessmentType.TEAM); // ✅ NEW type
            comment.setAnonymousIdentifier("You (Team Evaluation)");
            comment.setCommentIndex(i);
            comment.setCommentLabel(assessment.getGroupCommentLabel(i));
            comment.setRubricAssessmentType("Group Assessment"); // ✅ CRITICAL
            
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
            comment.setRubricAssessmentType(rubricType); // ✅ NEW
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
            comment.setRubricAssessmentType(rubricType); // ✅ NEW
        }
        
        return commentRepository.save(comment);
    }
    
    /**
     * Get compiled comments for a student - organized by assessment
     */
    public Map<String, Object> getCompiledCommentsForStudent(Student student) {
        Map<String, Object> result = new HashMap<>();
        
        List<AssessmentComment> allComments = commentRepository.findAllCommentsForStudent(student);
        
        // Group by assessment
        Map<Assessment, List<AssessmentComment>> byAssessment = allComments.stream()
                .collect(Collectors.groupingBy(AssessmentComment::getAssessment));
        
        // Organize comments within each assessment
        Map<String, Map<String, List<AssessmentComment>>> organized = new HashMap<>();
        
        for (Map.Entry<Assessment, List<AssessmentComment>> entry : byAssessment.entrySet()) {
            Assessment assessment = entry.getKey();
            List<AssessmentComment> comments = entry.getValue();
            
            Map<String, List<AssessmentComment>> categorized = new HashMap<>();
            
            // Separate into categories
            List<AssessmentComment> selfComments = new ArrayList<>();
            List<AssessmentComment> peerComments = new ArrayList<>();
            List<AssessmentComment> teamComments = new ArrayList<>();
            List<AssessmentComment> lecturerComments = new ArrayList<>();
            List<AssessmentComment> supervisorComments = new ArrayList<>();
            
            for (AssessmentComment comment : comments) {
                switch (comment.getAssessmentType()) {
                    case SELF:
                        selfComments.add(comment);
                        break;
                    case PEER:
                        peerComments.add(comment);
                        break;
                    case TEAM:
                        teamComments.add(comment);
                        break;
                    case LECTURER_EVALUATION:
                        lecturerComments.add(comment);
                        break;
                    case SUPERVISOR_EVALUATION:
                        supervisorComments.add(comment);
                        break;
                }
            }
            
            categorized.put("self", selfComments);
            categorized.put("peer", peerComments);
            categorized.put("team", teamComments);
            categorized.put("lecturer", lecturerComments);
            categorized.put("supervisor", supervisorComments);
            
            organized.put(assessment.getTitle(), categorized);
        }
        
        result.put("commentsByAssessment", organized);
        result.put("totalComments", allComments.size());
        
        return result;
    }
    
    /**
     * Get existing comments for edit - returns all comments from evaluator to student in assessment
     */
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
                    .collect(Collectors.toList());
            
            if (teamComments.size() < requiredGroupCommentCount) {
                return false;
            }
        }
        
        return true;
    }
}