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
    public String getAnonymousIdentifier(Student evaluatedStudent, Long evaluatorId, Assessment assessment) {
        // Check if assessment requires anonymous comments
        if (!assessment.getCommentsAnonymous()) {
            // Return the evaluator's real name
            return studentRepository.findById(evaluatorId)
                    .map(Student::getUsername)
                    .orElse("Student");
        }
        
        // Get all peer comments for this student in this assessment
        List<AssessmentComment> peerComments = commentRepository.findPeerCommentsForStudentInAssessment(
                evaluatedStudent, assessment);
        
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
     * @param commentTexts List of comment texts, one for each configured comment field
     */
    @Transactional
    public List<AssessmentComment> submitPeerComments(Student evaluator, Student evaluatedStudent, 
                                                       Assessment assessment, List<String> commentTexts) {
        
        List<AssessmentComment> savedComments = new ArrayList<>();
        
        // Get anonymous identifier (consistent across all comments from this evaluator)
        String identifier = getAnonymousIdentifier(evaluatedStudent, evaluator.getId(), assessment);
        String evaluatorName = evaluator.getUsername();
        
        // Delete existing comments from this evaluator to this student in this assessment
        List<AssessmentComment> existingComments = commentRepository.findByEvaluatorAndStudentAndAssessment(
                evaluator.getId(),
                AssessmentComment.EvaluatorType.STUDENT,
                evaluatedStudent,
                assessment);
        
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
            comment.setCommentLabel(assessment.getCommentLabel(i));
            
            savedComments.add(commentRepository.save(comment));
        }
        
        return savedComments;
    }
    
    /**
     * Submit multiple self comments
     * @param commentTexts List of comment texts, one for each configured comment field
     */
    @Transactional
    public List<AssessmentComment> submitSelfComments(Student student, Assessment assessment, List<String> commentTexts) {
        
        List<AssessmentComment> savedComments = new ArrayList<>();
        
        // Delete existing self comments
        List<AssessmentComment> existingComments = commentRepository.findSelfCommentsForStudentInAssessment(
                student, assessment, student.getId());
        
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
            comment.setCommentLabel(assessment.getCommentLabel(i));
            
            savedComments.add(commentRepository.save(comment));
        }
        
        return savedComments;
    }
    
    /**
     * Submit lecturer comment
     */
    @Transactional
    public AssessmentComment submitLecturerComment(Long lecturerId, String lecturerName,
                                                     Student evaluatedStudent, Assessment assessment,
                                                     String commentText) {
        
        // For lecturers, we maintain backward compatibility with single comment
        List<AssessmentComment> existing = commentRepository.findByEvaluatorAndStudentAndAssessment(
                lecturerId,
                AssessmentComment.EvaluatorType.LECTURER,
                evaluatedStudent,
                assessment);
        
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
        }
        
        return commentRepository.save(comment);
    }
    
    /**
     * Submit supervisor comment
     */
    @Transactional
    public AssessmentComment submitSupervisorComment(Long supervisorId, String supervisorName,
                                                       Student evaluatedStudent, Assessment assessment,
                                                       String commentText) {
        
        // For supervisors, we maintain backward compatibility with single comment
        List<AssessmentComment> existing = commentRepository.findByEvaluatorAndStudentAndAssessment(
                supervisorId,
                AssessmentComment.EvaluatorType.SUPERVISOR,
                evaluatedStudent,
                assessment);
        
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
     */
    public boolean hasSubmittedAllComments(Student evaluator, Assessment assessment, List<Student> teamMembers) {
        int requiredCommentCount = assessment.getCommentCount();
        
        for (Student member : teamMembers) {
            List<AssessmentComment> comments = commentRepository.findByEvaluatorAndStudentAndAssessment(
                    evaluator.getId(),
                    AssessmentComment.EvaluatorType.STUDENT,
                    member,
                    assessment);
            
            // Check if all required comments are submitted for this team member
            if (comments.size() < requiredCommentCount) {
                return false;
            }
        }
        
        return true;
    }
}