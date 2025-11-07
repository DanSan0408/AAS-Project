package com.capstone.adproject.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        // Get all peer comments for this student in this assessment
        List<AssessmentComment> peerComments = commentRepository.findPeerCommentsForStudentInAssessment(
                evaluatedStudent, assessment);
        
        // Check if this evaluator already has a teammate number
        for (AssessmentComment comment : peerComments) {
            if (comment.getEvaluatorId().equals(evaluatorId)) {
                return comment.getAnonymousIdentifier();
            }
        }
        
        // Assign new teammate number
        int nextTeammateNumber = peerComments.size() + 1;
        return "Teammate " + nextTeammateNumber;
    }
    
    /**
     * Submit peer comment (from student to peer)
     */
    @Transactional
    public AssessmentComment submitPeerComment(Student evaluator, Student evaluatedStudent, 
                                                Assessment assessment, String commentText) {
        
        // Check if comment already exists
        Optional<AssessmentComment> existing = commentRepository.findByEvaluatorAndStudentAndAssessment(
                evaluator.getId(), 
                AssessmentComment.EvaluatorType.STUDENT,
                evaluatedStudent, 
                assessment);
        
        AssessmentComment comment;
        if (existing.isPresent()) {
            comment = existing.get();
            comment.setCommentText(commentText);
        } else {
            comment = new AssessmentComment();
            comment.setEvaluatorId(evaluator.getId());
            comment.setEvaluatorType(AssessmentComment.EvaluatorType.STUDENT);
            comment.setEvaluatedStudent(evaluatedStudent);
            comment.setAssessment(assessment);
            comment.setCommentText(commentText);
            comment.setAssessmentType(AssessmentComment.CommentAssessmentType.PEER);
            
            // Set anonymous identifier
            String identifier = getAnonymousIdentifier(evaluatedStudent, evaluator.getId(), assessment);
            comment.setAnonymousIdentifier(identifier);
        }
        
        return commentRepository.save(comment);
    }
    
    /**
     * Submit self comment
     */
    @Transactional
    public AssessmentComment submitSelfComment(Student student, Assessment assessment, String commentText) {
        
        Optional<AssessmentComment> existing = commentRepository.findSelfCommentForStudentInAssessment(
                student, assessment, student.getId());
        
        AssessmentComment comment;
        if (existing.isPresent()) {
            comment = existing.get();
            comment.setCommentText(commentText);
        } else {
            comment = new AssessmentComment();
            comment.setEvaluatorId(student.getId());
            comment.setEvaluatorType(AssessmentComment.EvaluatorType.STUDENT);
            comment.setEvaluatedStudent(student);
            comment.setAssessment(assessment);
            comment.setCommentText(commentText);
            comment.setAssessmentType(AssessmentComment.CommentAssessmentType.SELF);
            comment.setAnonymousIdentifier("You (Self)");
        }
        
        return commentRepository.save(comment);
    }
    
    /**
     * Submit lecturer comment
     */
    @Transactional
    public AssessmentComment submitLecturerComment(Long lecturerId, String lecturerName,
                                                     Student evaluatedStudent, Assessment assessment,
                                                     String commentText) {
        
        Optional<AssessmentComment> existing = commentRepository.findByEvaluatorAndStudentAndAssessment(
                lecturerId,
                AssessmentComment.EvaluatorType.LECTURER,
                evaluatedStudent,
                assessment);
        
        AssessmentComment comment;
        if (existing.isPresent()) {
            comment = existing.get();
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
        
        Optional<AssessmentComment> existing = commentRepository.findByEvaluatorAndStudentAndAssessment(
                supervisorId,
                AssessmentComment.EvaluatorType.SUPERVISOR,
                evaluatedStudent,
                assessment);
        
        AssessmentComment comment;
        if (existing.isPresent()) {
            comment = existing.get();
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
     * Get existing comment for edit
     */
    public Optional<AssessmentComment> getExistingComment(Long evaluatorId, 
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
        long submittedCount = commentRepository.countByEvaluatorAndAssessment(
                evaluator.getId(),
                AssessmentComment.EvaluatorType.STUDENT,
                assessment);
        
        // Should have comments for all team members (including self)
        return submittedCount >= teamMembers.size();
    }
}