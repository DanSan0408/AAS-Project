package com.capstone.adproject.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.AssessmentComment;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.Mark;
import com.capstone.adproject.model.Rating;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.SubRubric;
import com.capstone.adproject.repositories.AssessmentCommentRepository;
import com.capstone.adproject.repositories.AssessmentRepository;
import com.capstone.adproject.repositories.GroupRepository;
import com.capstone.adproject.repositories.MarkRepository;
import com.capstone.adproject.repositories.RatingRepository;
import com.capstone.adproject.repositories.StudentRepository;
import com.capstone.adproject.repositories.SubRubricRepository;

import jakarta.persistence.EntityNotFoundException;

@Service
public class LecturerAssessmentService {

    private final MarkRepository markRepository;
    private final AssessmentRepository assessmentRepository;
    private final GroupRepository groupRepository;
    private final StudentRepository studentRepository;
    private final SubRubricRepository subRubricRepository;
    private final RatingRepository ratingRepository;
    private final AssessmentCommentRepository assessmentCommentRepository;

    public LecturerAssessmentService(
            MarkRepository markRepository,
            AssessmentRepository assessmentRepository,
            GroupRepository groupRepository,
            StudentRepository studentRepository,
            SubRubricRepository subRubricRepository,
            RatingRepository ratingRepository,
            AssessmentCommentRepository assessmentCommentRepository) {
        this.markRepository = markRepository;
        this.assessmentRepository = assessmentRepository;
        this.groupRepository = groupRepository;
        this.studentRepository = studentRepository;
        this.subRubricRepository = subRubricRepository;
        this.ratingRepository = ratingRepository;
        this.assessmentCommentRepository = assessmentCommentRepository;
    }

    public List<Assessment> getAssessmentsForLecturerEvaluation() {
        List<Assessment> allAssessments = assessmentRepository.findAll();
        
        return allAssessments.stream()
            .filter(assessment -> {
                String title = assessment.getTitle().toLowerCase();
                return !title.contains("peer") && 
                        !title.contains("self") && 
                        !title.contains("industrial supervisor");
            })
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Group> getAllGroups() {
        List<Group> groups = groupRepository.findAll();
        groups.forEach(g -> {
            if (g.getStudents() != null) {
                g.getStudents().size();
            }
        });
        return groups;
    }

    @Transactional(readOnly = true)
    public List<Student> getAllStudents() {
        return studentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Student> getStudentsByGroup(Long groupId) {
        return studentRepository.findByGroupId(groupId);
    }

    /**
     * NEW: Check if a target has been evaluated by this lecturer for this assessment and rubric type
     */
    public boolean hasTargetBeenEvaluated(
            Assessment assessment, 
            Lecturer lecturer, 
            Long targetId, 
            boolean isGroupTarget,
            String rubricType) {
        
        if (isGroupTarget) {
            List<Student> groupMembers = studentRepository.findByGroupId(targetId);
            if (groupMembers.isEmpty()) {
                return false;
            }
            
            // Check if any group member has marks from this lecturer for this rubric type
            Student anyMember = groupMembers.get(0);
            List<Mark> marks = markRepository.findByEvaluatorStudentAndEvaluatedStudentAndAssessment(
                anyMember, anyMember, assessment);
            
            return marks.stream()
                .anyMatch(m -> m.getComments() != null && 
                              m.getComments().startsWith("LECTURER:" + lecturer.getId()) &&
                              m.getAssessmentType() != null &&
                              m.getAssessmentType().equalsIgnoreCase(rubricType));
        } else {
            Student student = studentRepository.findById(targetId).orElse(null);
            if (student == null) {
                return false;
            }
            
            List<Mark> marks = markRepository.findByEvaluatorStudentAndEvaluatedStudentAndAssessment(
                student, student, assessment);
            
            return marks.stream()
                .anyMatch(m -> m.getComments() != null && 
                              m.getComments().startsWith("LECTURER:" + lecturer.getId()) &&
                              m.getAssessmentType() != null &&
                              m.getAssessmentType().equalsIgnoreCase(rubricType));
        }
    }

    /**
     * NEW: Save evaluation scores with support for multiple comments and rubric type
     */
    @Transactional
    public void saveEvaluationScores(
            Long assessmentId,
            Long lecturerId,
            Long targetId,
            boolean isGroupTarget,
            String rubricType,
            Map<String, String> scores,
            Map<Integer, String> comments) {

        Assessment assessment = assessmentRepository.findById(assessmentId)
            .orElseThrow(() -> new EntityNotFoundException("Assessment not found"));

        List<Student> studentsToEvaluate;

        if (isGroupTarget) {
            Group group = groupRepository.findById(targetId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found"));
            studentsToEvaluate = studentRepository.findByGroupId(group.getId());
        } else {
            Student student = studentRepository.findById(targetId)
                .orElseThrow(() -> new EntityNotFoundException("Student not found"));
            studentsToEvaluate = List.of(student);
        }

        LocalDateTime now = LocalDateTime.now();
        String lecturerIdentifier = "LECTURER:" + lecturerId;

        // Process each student
        for (Student student : studentsToEvaluate) {
            // Delete existing marks from this lecturer for this assessment and rubric type
            List<Mark> existingMarks = markRepository.findByEvaluatorStudentAndEvaluatedStudentAndAssessment(
                student, student, assessment);
            
            List<Mark> marksToDelete = existingMarks.stream()
                .filter(m -> m.getComments() != null && 
                            m.getComments().startsWith(lecturerIdentifier) &&
                            m.getAssessmentType() != null &&
                            m.getAssessmentType().equalsIgnoreCase(rubricType))
                .collect(Collectors.toList());
            
            markRepository.deleteAll(marksToDelete);

            // Delete existing comments from this lecturer for this assessment
            List<AssessmentComment> existingComments = assessmentCommentRepository
                .findByEvaluatedStudentAndAssessment(student, assessment);
            
            List<AssessmentComment> commentsToDelete = existingComments.stream()
                .filter(c -> c.getEvaluatorId().equals(lecturerId) &&
                            c.getEvaluatorType() == AssessmentComment.EvaluatorType.LECTURER)
                .collect(Collectors.toList());
            
            assessmentCommentRepository.deleteAll(commentsToDelete);

            // Create new marks for rubric scores
            for (Map.Entry<String, String> entry : scores.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                if (value == null || value.trim().isEmpty()) {
                    continue;
                }

                Mark mark = new Mark();
                mark.setEvaluatorStudent(student);
                mark.setEvaluatedStudent(student);
                mark.setAssessment(assessment);
                mark.setStatus(Mark.SubmissionStatus.FINAL);
                mark.setSubmittedAt(now);
                mark.setComments(lecturerIdentifier);
                mark.setAssessmentType(rubricType);

                // Parse the key to get sub-rubric/rubric and rating
                if (key.startsWith("subRubric_")) {
                    // Handle sub-rubric ratings
                    String[] parts = key.split("_");
                    Long subRubricId = Long.parseLong(parts[1]);

                    SubRubric subRubric = subRubricRepository.findById(subRubricId)
                        .orElseThrow(() -> new EntityNotFoundException("SubRubric not found"));
                    Rating rating = ratingRepository.findById(Long.parseLong(value))
                        .orElseThrow(() -> new EntityNotFoundException("Rating not found"));

                    mark.setRubric(subRubric.getRubric());
                    mark.setSubRubric(subRubric);
                    mark.setRating(rating);
                } else if (key.startsWith("rubric_")) {
                    // Handle direct rubric ratings (no sub-rubric)
                    String[] parts = key.split("_");
                    Long rubricId = Long.parseLong(parts[1]);
                    
                    com.capstone.adproject.model.Rubric rubric = assessment.getRubrics().stream()
                        .filter(r -> r.getId().equals(rubricId))
                        .findFirst()
                        .orElseThrow(() -> new EntityNotFoundException("Rubric not found"));
                    
                    Rating rating = ratingRepository.findById(Long.parseLong(value))
                        .orElseThrow(() -> new EntityNotFoundException("Rating not found"));

                    mark.setRubric(rubric);
                    mark.setSubRubric(null); // Direct rating, no sub-rubric
                    mark.setRating(rating);
                }

                markRepository.save(mark);
            }

            // Create new assessment comments (supporting multiple comment fields)
            if (comments != null && !comments.isEmpty()) {
                for (Map.Entry<Integer, String> commentEntry : comments.entrySet()) {
                    int commentIndex = commentEntry.getKey();
                    String commentText = commentEntry.getValue();
                    
                    if (commentText == null || commentText.trim().isEmpty()) {
                        continue;
                    }
                    
                    AssessmentComment comment = new AssessmentComment();
                    comment.setEvaluatedStudent(student);
                    comment.setEvaluatorId(lecturerId);
                    comment.setEvaluatorType(AssessmentComment.EvaluatorType.LECTURER);
                    comment.setEvaluatorName("Lecturer"); // You can set actual lecturer name if available
                    comment.setAssessment(assessment);
                    comment.setCommentText(commentText);
                    comment.setAssessmentType(AssessmentComment.CommentAssessmentType.LECTURER_EVALUATION);
                    comment.setSubmittedAt(now);
                    comment.setCommentIndex(commentIndex);
                    
                    // Set label from assessment configuration
                    comment.setCommentLabel(assessment.getCommentLabel(commentIndex));
                    
                    assessmentCommentRepository.save(comment);
                }
            }
        }
    }
    
    /**
     * NEW: Get existing marks for a lecturer evaluation to pre-populate the form
     * Returns a map of "subRubric_X" or "rubric_X" -> ratingId
     */
    public Map<String, Long> getExistingMarks(Assessment assessment, Lecturer lecturer, 
                                               Student student, String rubricType) {
        Map<String, Long> existingMarks = new HashMap<>();
        
        List<Mark> marks = markRepository.findByEvaluatorStudentAndEvaluatedStudentAndAssessment(
            student, student, assessment);
        
        // Filter marks from this lecturer for this rubric type
        for (Mark mark : marks) {
            if (mark.getComments() != null && 
                mark.getComments().startsWith("LECTURER:" + lecturer.getId()) &&
                mark.getAssessmentType() != null &&
                mark.getAssessmentType().equalsIgnoreCase(rubricType)) {
                
                // Create the key based on whether it's a sub-rubric or direct rubric
                String key;
                if (mark.getSubRubric() != null) {
                    key = "subRubric_" + mark.getSubRubric().getId();
                } else if (mark.getRubric() != null) {
                    key = "rubric_" + mark.getRubric().getId();
                } else {
                    continue; // Skip invalid marks
                }
                
                // Store the rating ID
                if (mark.getRating() != null) {
                    existingMarks.put(key, mark.getRating().getId());
                }
            }
        }
        
        return existingMarks;
    }
    
    /**
     * NEW: Get existing comments for a lecturer evaluation to pre-populate the form
     * Returns a map of commentIndex -> commentText
     */
    public Map<Integer, String> getExistingComments(Assessment assessment, Lecturer lecturer, 
                                                     Student student) {
        Map<Integer, String> existingComments = new HashMap<>();
        
        List<AssessmentComment> comments = assessmentCommentRepository
            .findByEvaluatedStudentAndAssessmentAndEvaluatorIdAndEvaluatorType(
                student, assessment, lecturer.getId(), AssessmentComment.EvaluatorType.LECTURER);
        
        for (AssessmentComment comment : comments) {
            existingComments.put(comment.getCommentIndex(), comment.getCommentText());
        }
        
        return existingComments;
    }
}