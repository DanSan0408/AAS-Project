package com.capstone.adproject.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Criteria;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.Mark;
import com.capstone.adproject.model.Rating;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.SubRubric;
import com.capstone.adproject.repositories.AssessmentRepository;
import com.capstone.adproject.repositories.CriteriaRepository;
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
    private final CriteriaRepository criteriaRepository;

    public LecturerAssessmentService(
            MarkRepository markRepository,
            AssessmentRepository assessmentRepository,
            GroupRepository groupRepository,
            StudentRepository studentRepository,
            SubRubricRepository subRubricRepository,
            RatingRepository ratingRepository,
            CriteriaRepository criteriaRepository) {
        this.markRepository = markRepository;
        this.assessmentRepository = assessmentRepository;
        this.groupRepository = groupRepository;
        this.studentRepository = studentRepository;
        this.subRubricRepository = subRubricRepository;
        this.ratingRepository = ratingRepository;
        this.criteriaRepository = criteriaRepository;
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

    public boolean hasGroupBeenEvaluatedForGroupEvaluationAndGroupAssessment(
            Assessment assessment, Group group, Lecturer lecturer) {
        
        List<Student> groupMembers = studentRepository.findByGroupId(group.getId());
        if (groupMembers.isEmpty()) {
            return false;
        }
        
        boolean hasGroupEvalGroupAssessRubrics = assessment.getRubrics().stream()
            .anyMatch(r -> r.getEvaluationType() != null && 
                          r.getEvaluationType().toLowerCase().contains("group") &&
                          r.getAssessmentTypes() != null && 
                          r.getAssessmentTypes().toLowerCase().contains("group"));
        
        boolean hasGroupEvalGroupAssessCriteria = assessment.getCriteria().stream()
            .anyMatch(c -> c.getEvaluationType() != null && 
                          c.getEvaluationType().toLowerCase().contains("group") &&
                          c.getAssessmentTypes() != null && 
                          c.getAssessmentTypes().toLowerCase().contains("group"));
        
        if (!hasGroupEvalGroupAssessRubrics && !hasGroupEvalGroupAssessCriteria) {
            return false;
        }
        
        Student anyMember = groupMembers.get(0);
        List<Mark> marks = markRepository.findByEvaluatorStudentAndEvaluatedStudentAndAssessment(
            anyMember, anyMember, assessment);
        
        return marks.stream()
            .anyMatch(m -> m.getComments() != null && 
                          m.getComments().startsWith("LECTURER:" + lecturer.getId()) &&
                          m.getEvaluationType() != null &&
                          m.getEvaluationType().toLowerCase().contains("group") &&
                          m.getAssessmentType() != null &&
                          m.getAssessmentType().toLowerCase().contains("group"));
    }

    public boolean hasStudentBeenEvaluatedForIndividualEvaluationAndIndividualAssessment(
            Assessment assessment, Student student, Lecturer lecturer) {
        
        boolean hasIndivEvalIndivAssessRubrics = assessment.getRubrics().stream()
            .anyMatch(r -> r.getEvaluationType() != null && 
                          r.getEvaluationType().toLowerCase().contains("individual") &&
                          r.getAssessmentTypes() != null && 
                          r.getAssessmentTypes().toLowerCase().contains("individual"));
        
        boolean hasIndivEvalIndivAssessCriteria = assessment.getCriteria().stream()
            .anyMatch(c -> c.getEvaluationType() != null && 
                          c.getEvaluationType().toLowerCase().contains("individual") &&
                          c.getAssessmentTypes() != null && 
                          c.getAssessmentTypes().toLowerCase().contains("individual"));
        
        if (!hasIndivEvalIndivAssessRubrics && !hasIndivEvalIndivAssessCriteria) {
            return false;
        }
        
        List<Mark> marks = markRepository.findByEvaluatorStudentAndEvaluatedStudentAndAssessment(
            student, student, assessment);
        
        return marks.stream()
            .anyMatch(m -> m.getComments() != null && 
                          m.getComments().startsWith("LECTURER:" + lecturer.getId()) &&
                          m.getEvaluationType() != null &&
                          m.getEvaluationType().toLowerCase().contains("individual") &&
                          m.getAssessmentType() != null &&
                          m.getAssessmentType().toLowerCase().contains("individual"));
    }

    /**
     * NEW: Save evaluation scores with explicit evaluation and assessment types
     */
    @Transactional
    public void saveEvaluationScores(
            Long assessmentId,
            Long lecturerId,
            Long targetId,
            boolean isGroupTarget,
            String evaluationType,
            String assessmentType,
            Map<String, String> scores,
            String comments) {

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
        String lecturerComment = "LECTURER:" + lecturerId + ":" + (comments != null ? comments : "");

        // Process each student
        for (Student student : studentsToEvaluate) {
            // Delete existing marks for this specific evaluation/assessment type combination
            List<Mark> existingMarks = markRepository.findByEvaluatorStudentAndEvaluatedStudentAndAssessment(
                student, student, assessment);
            
            List<Mark> marksToDelete = existingMarks.stream()
                .filter(m -> m.getComments() != null && 
                            m.getComments().startsWith("LECTURER:" + lecturerId) &&
                            m.getEvaluationType() != null &&
                            m.getEvaluationType().equals(evaluationType) &&
                            m.getAssessmentType() != null &&
                            m.getAssessmentType().equals(assessmentType))
                .collect(Collectors.toList());
            
            markRepository.deleteAll(marksToDelete);

            // Create new marks
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
                mark.setComments(lecturerComment);
                mark.setEvaluationType(evaluationType);
                mark.setAssessmentType(assessmentType);

                // Parse the key to determine score type
                if (key.startsWith("subRubric_")) {
                    String[] parts = key.split("_");
                    Long subRubricId = Long.parseLong(parts[1]);

                    SubRubric subRubric = subRubricRepository.findById(subRubricId)
                        .orElseThrow(() -> new EntityNotFoundException("SubRubric not found"));
                    Rating rating = ratingRepository.findById(Long.parseLong(value))
                        .orElseThrow(() -> new EntityNotFoundException("Rating not found"));

                    mark.setRubric(subRubric.getRubric());
                    mark.setSubRubric(subRubric);
                    mark.setRating(rating);

                } else if (key.startsWith("criteria_")) {
                    String[] parts = key.split("_");
                    Long criteriaId = Long.parseLong(parts[1]);
                    Integer ratingLevel = Integer.parseInt(value);

                    Criteria criteria = criteriaRepository.findById(criteriaId)
                        .orElseThrow(() -> new EntityNotFoundException("Criteria not found"));

                    mark.setCriteria(criteria);
                    mark.setRatingLevel(ratingLevel);
                }

                markRepository.save(mark);
            }
        }
    }
}