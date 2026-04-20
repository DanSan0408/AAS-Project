package com.capstone.adproject.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Mark;
import com.capstone.adproject.model.Rating;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.SubRubric;
import com.capstone.adproject.repositories.AssessmentRepository;
import com.capstone.adproject.repositories.MarkRepository;
import com.capstone.adproject.repositories.RubricRepository;
import com.capstone.adproject.repositories.SubRubricRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;

@Service
public class RubricService {

    private final AssessmentRepository assessmentRepository;
    private final RubricRepository rubricRepository;
    private final SubRubricRepository subRubricRepository;
    private final MarkRepository markRepository;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    public RubricService(AssessmentRepository assessmentRepository, 
                         RubricRepository rubricRepository,
                         SubRubricRepository subRubricRepository,
                         MarkRepository markRepository,
                         EntityManager entityManager,
                         JdbcTemplate jdbcTemplate) {
        this.assessmentRepository = assessmentRepository;
        this.rubricRepository = rubricRepository;
        this.subRubricRepository = subRubricRepository;
        this.markRepository = markRepository;
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void saveBulkAssessment(Assessment formAssessment) {
        Assessment existing = findAssessmentById(formAssessment.getId());
        
        // Update Overall comments
        existing.setGroupCommentLabels(formAssessment.getGroupCommentLabels());
        existing.setIndividualCommentLabels(formAssessment.getIndividualCommentLabels());
        
        // Update Rubrics
        for (int i = 0; i < formAssessment.getRubrics().size(); i++) {
            Rubric formRubric = formAssessment.getRubrics().get(i);
            Rubric existingRubric = existing.getRubrics().get(i);
            
            existingRubric.setName(formRubric.getName());
            existingRubric.setDescription(formRubric.getDescription());
            existingRubric.setMarks(formRubric.getMarks());
            existingRubric.setClo(formRubric.getClo());
            existingRubric.setCloMarks(formRubric.getMarks() != null ? formRubric.getMarks().doubleValue() : 0.0);
            
            // Update Rubric comments
            existingRubric.setRubricCommentLabels(formRubric.getRubricCommentLabels());
            
            // Update Direct Ratings
            if (formRubric.getRatings() != null) {
                for (int j = 0; j < formRubric.getRatings().size(); j++) {
                    existingRubric.getRatings().get(j).setName(formRubric.getRatings().get(j).getName());
                    existingRubric.getRatings().get(j).setMarks(formRubric.getRatings().get(j).getMarks());
                }
            }
            
            // Update Sub-Rubrics
            if (formRubric.getSubRubrics() != null) {
                for (int k = 0; k < formRubric.getSubRubrics().size(); k++) {
                    SubRubric formSub = formRubric.getSubRubrics().get(k);
                    SubRubric existingSub = existingRubric.getSubRubrics().get(k);
                    
                    existingSub.setName(formSub.getName());
                    existingSub.setDescription(formSub.getDescription());
                    existingSub.setMarks(formSub.getMarks());
                    
                    // Update Sub-Rubric Ratings
                    if (formSub.getRatings() != null) {
                        for (int l = 0; l < formSub.getRatings().size(); l++) {
                            existingSub.getRatings().get(l).setName(formSub.getRatings().get(l).getName());
                            existingSub.getRatings().get(l).setMarks(formSub.getRatings().get(l).getMarks());
                        }
                    }
                }
            }
        }
        
        calculateAssessmentMarks(existing);
        assessmentRepository.save(existing);
    }

    public void calculateAssessmentMarks(Assessment assessment) {
        if (assessment == null) return;

        double totalMarks = 0.0;
        Map<Integer, Double> cloMarksMap = new HashMap<>();

        if (assessment.getRubrics() != null) {
            for (Rubric rubric : assessment.getRubrics()) {
                if (rubric.getMarks() != null) {
                    totalMarks += rubric.getMarks().doubleValue(); 
                    
                    if (rubric.getClo() != null && rubric.getCloMarks() != null) {
                        cloMarksMap.merge(rubric.getClo(), rubric.getCloMarks(), Double::sum);
                    }
                }
            }
        }

        assessment.setTotalMarks(totalMarks);
        assessment.setCloMarks(cloMarksMap);
    }

    public List<Assessment> findAllAssessments() {
        List<Assessment> assessments = assessmentRepository.findAll();
        for (Assessment assessment : assessments) {
            calculateAssessmentMarks(assessment);
        }
        return assessments;
    }

    @Transactional
    public Assessment findAssessmentById(Long id) {
        Assessment assessment = assessmentRepository.findById(id) 
            .orElseThrow(() -> new EntityNotFoundException("Assessment not found with id: " + id));
            
        Hibernate.initialize(assessment.getRubrics());
        
        for (Rubric rubric : assessment.getRubrics()) {
            Hibernate.initialize(rubric.getSubRubrics());
            Hibernate.initialize(rubric.getRatings());
            Hibernate.initialize(rubric.getRubricCommentLabels());
            Hibernate.initialize(rubric.getRubricCommentMinLengths());
            Hibernate.initialize(rubric.getRubricCommentAnonymousFlags());
            
            if (rubric.getSubRubrics() != null) {
                for (SubRubric subRubric : rubric.getSubRubrics()) {
                    Hibernate.initialize(subRubric.getRatings());
                }
            }
        }
            
        calculateAssessmentMarks(assessment);
        return assessment;
    }

    @Transactional
    public Assessment saveAssessment(Assessment assessment) {
        // Validate course exists and is loaded from database
        if (assessment.getCourse() == null || assessment.getCourse().getId() == null) {
            throw new IllegalArgumentException("Assessment must have a valid course assigned");
        }
        
        // Ensure course is loaded from database (not a detached/transient object)
        Long courseId = assessment.getCourse().getId();
        com.capstone.adproject.model.Course course = entityManager.find(com.capstone.adproject.model.Course.class, courseId);
        
        if (course == null) {
            throw new EntityNotFoundException("Course not found with ID: " + courseId);
        }

        // If legacy schemas have both course/courses, ensure FK target table contains this id.
        ensureAssessmentForeignKeyCourseExists(courseId);
        
        // Set the attached course object
        assessment.setCourse(course);
        return assessmentRepository.save(assessment);
    }

    private void ensureAssessmentForeignKeyCourseExists(Long courseId) {
        String schema = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        if (isBlank(schema)) {
            return;
        }

        String referencedCourseTable = jdbcTemplate.query(
            """
            SELECT kcu.REFERENCED_TABLE_NAME
            FROM information_schema.KEY_COLUMN_USAGE kcu
            JOIN information_schema.TABLE_CONSTRAINTS tc
              ON tc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA
             AND tc.TABLE_NAME = kcu.TABLE_NAME
             AND tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME
            WHERE tc.CONSTRAINT_TYPE = 'FOREIGN KEY'
              AND kcu.TABLE_SCHEMA = ?
              AND LOWER(kcu.TABLE_NAME) = 'assessment'
              AND LOWER(kcu.COLUMN_NAME) = 'course_id'
              AND kcu.REFERENCED_TABLE_NAME IS NOT NULL
            LIMIT 1
            """,
            rs -> rs.next() ? rs.getString(1) : null,
            schema
        );

        if (isBlank(referencedCourseTable)) {
            return;
        }

        Integer exists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + q(referencedCourseTable) + " WHERE id = ?",
            Integer.class,
            courseId
        );
        if (exists != null && exists > 0) {
            return;
        }

        String fallbackSourceTable = "courses".equalsIgnoreCase(referencedCourseTable) ? "course" : "courses";
        Integer sourceExists = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = ?
              AND LOWER(TABLE_NAME) = LOWER(?)
            """,
            Integer.class,
            schema,
            fallbackSourceTable
        );

        if (sourceExists == null || sourceExists == 0) {
            throw new EntityNotFoundException("Course not found in FK target table " + referencedCourseTable + " for id " + courseId);
        }

        int copied = jdbcTemplate.update(
            Objects.requireNonNull(
                """
                INSERT INTO %s (id, courseName, courseCode, description)
                SELECT c.id, c.courseName, c.courseCode, c.description
                FROM %s c
                WHERE c.id = ?
                  AND NOT EXISTS (SELECT 1 FROM %s t WHERE t.id = c.id)
                """.formatted(q(referencedCourseTable), q(fallbackSourceTable), q(referencedCourseTable))
            ),
            courseId
        );

        if (copied == 0) {
            throw new EntityNotFoundException("Course not found with ID: " + courseId);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String q(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }
    
    @Transactional
public void deleteAssessment(Long id){
    if (!assessmentRepository.existsById(id)) {
        throw new EntityNotFoundException("Cannot delete. Assessment not found with id: " + id);
    }
    
    Assessment assessment = assessmentRepository.findById(id).orElse(null);
    if (assessment == null) {
        throw new EntityNotFoundException("Assessment not found with id: " + id);
    }
    
    System.out.println("=== DELETING ASSESSMENT: " + assessment.getTitle() + " (ID: " + id + ") ===");
    
    try {
        System.out.println("Step 1: Deleting calculated results...");
        assessmentRepository.deleteCalculatedResultsByAssessmentId(id);
        assessmentRepository.flush();
        
        System.out.println("Step 2: Deleting assessment comments...");
        assessmentRepository.deleteCommentsByAssessmentId(id);
        assessmentRepository.flush();
        
        System.out.println("Step 3: Deleting marks for rubrics...");
        if (assessment.getRubrics() != null && !assessment.getRubrics().isEmpty()) {
            List<Rubric> rubricsCopy = new ArrayList<>(assessment.getRubrics());
            for (Rubric rubric : rubricsCopy) {
                deleteMarksForRubric(rubric);
            }
        }
        
        System.out.println("Step 4: Deleting lecturer assignments...");
        assessmentRepository.deleteLecturerAssignmentsByAssessmentId(id);
        assessmentRepository.flush();
        
        System.out.println("Step 5: Deleting deadlines...");
        assessmentRepository.deleteDeadlinesByAssessmentId(id);
        assessmentRepository.flush();
        
        System.out.println("Step 6: Clearing element collections...");
        if (assessment.getGroupCommentLabels() != null) {
            assessment.getGroupCommentLabels().clear();
        }
        if (assessment.getIndividualCommentLabels() != null) {
            assessment.getIndividualCommentLabels().clear();
        }
        
        System.out.println("Step 7: Flushing changes...");
        assessmentRepository.saveAndFlush(assessment);
        
        System.out.println("Step 8: Deleting assessment...");
        assessmentRepository.deleteById(id);
        assessmentRepository.flush();
        
        System.out.println("=== ASSESSMENT DELETED SUCCESSFULLY ===");
        
    } catch (Exception e) {
        System.err.println("ERROR during assessment deletion: " + e.getMessage());
        e.printStackTrace();
        throw new RuntimeException("Failed to delete assessment: " + e.getMessage(), e);
    }
}
    
    @Transactional
    public Rubric findRubricById(Long id) {
        Rubric rubric = rubricRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Rubric not found with id: " + id));
        
        Hibernate.initialize(rubric.getSubRubrics());
        Hibernate.initialize(rubric.getRatings());
        Hibernate.initialize(rubric.getRubricCommentLabels());
        Hibernate.initialize(rubric.getRubricCommentMinLengths());
        Hibernate.initialize(rubric.getRubricCommentAnonymousFlags());
        
        if (rubric.getSubRubrics() != null) {
            for (SubRubric sr : rubric.getSubRubrics()) {
                Hibernate.initialize(sr.getRatings());
            }
        }
        
        return rubric;
    }

    @Transactional
public Rubric saveRubric(Rubric formRubric) { 
    
    if (formRubric.getId() != null) {
        
        Rubric existingRubric = rubricRepository.findById(formRubric.getId())
            .orElseThrow(() -> new EntityNotFoundException("Rubric not found with id: " + formRubric.getId()));
            
        existingRubric.setName(formRubric.getName());
        existingRubric.setDescription(formRubric.getDescription());
        existingRubric.setMarks(formRubric.getMarks());
        existingRubric.setAssessmentTypes(formRubric.getAssessmentTypes());
        existingRubric.setClo(formRubric.getClo());
        existingRubric.setCloMarks(formRubric.getCloMarks());
        existingRubric.setDisplayOrder(formRubric.getDisplayOrder());
        
        existingRubric.setRubricCommentLabels(formRubric.getRubricCommentLabels());
        existingRubric.setRubricCommentMinLengths(formRubric.getRubricCommentMinLengths());
        existingRubric.setRubricCommentAnonymousFlags(formRubric.getRubricCommentAnonymousFlags());

        updateSubRubrics(existingRubric, formRubric.getSubRubrics());
        updateDirectRatings(existingRubric, formRubric.getRatings());
        
        autoCalculateSubRubricMarks(existingRubric);
        
        return rubricRepository.save(existingRubric);
        
    } else {
        // Always append new rubrics to the end of their assessment type bucket.
        Assessment assessment = assessmentRepository.findById(formRubric.getAssessment().getId())
            .orElseThrow(() -> new EntityNotFoundException("Parent Assessment not found."));

        String newRubricType = formRubric.getAssessmentTypes();
        long maxOrder = assessment.getRubrics().stream()
            .filter(r -> Objects.equals(normalizeAssessmentType(r.getAssessmentTypes()), normalizeAssessmentType(newRubricType)))
            .mapToInt(r -> r.getDisplayOrder() != null ? r.getDisplayOrder() : 0)
            .max()
            .orElse(-1);

        formRubric.setDisplayOrder((int) maxOrder + 1);
        
        if (formRubric.getSubRubrics() != null) {
            for (SubRubric subRubric : formRubric.getSubRubrics()) {
                subRubric.setRubric(formRubric);
                
                if (subRubric.getRatings() != null) {
                    for (Rating rating : subRubric.getRatings()) {
                        rating.setSubRubric(subRubric);
                        rating.setRubric(null);
                    }
                }
            }
        }
        
        if (formRubric.getRatings() != null) {
            for (Rating rating : formRubric.getRatings()) {
                rating.setRubric(formRubric);
                rating.setSubRubric(null);
            }
        }
        
        if (formRubric.getAssessment() != null && formRubric.getAssessment().getId() != null) {
            Assessment attachedAssessment = assessmentRepository.findById(formRubric.getAssessment().getId())
                .orElseThrow(() -> new EntityNotFoundException("Parent Assessment not found."));
            formRubric.setAssessment(attachedAssessment); 
        }

        autoCalculateSubRubricMarks(formRubric);

        return rubricRepository.save(formRubric);
    }
}

private String normalizeAssessmentType(String assessmentType) {
    return isBlank(assessmentType) ? "Uncategorized" : assessmentType;
}

private void autoCalculateSubRubricMarks(Rubric rubric) {
    if (rubric.getSubRubrics() == null || rubric.getSubRubrics().isEmpty()) {
        return;
    }
    
    for (SubRubric subRubric : rubric.getSubRubrics()) {
        if (subRubric.getRatings() == null || subRubric.getRatings().isEmpty()) {
            // No ratings, set marks to 0
            subRubric.setMarks(BigDecimal.ZERO);
            continue;
        }
        
        BigDecimal maxRatingMark = subRubric.getRatings().stream()
            .map(Rating::getMarks)
            .filter(mark -> mark != null)
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);
        
        subRubric.setMarks(maxRatingMark);
    }
}

    private void updateSubRubrics(Rubric existingRubric, List<SubRubric> formSubRubrics) {
        List<SubRubric> managedSubRubrics = existingRubric.getSubRubrics();
        
        if (formSubRubrics == null) {
            formSubRubrics = new ArrayList<>();
        }

        Set<Long> formSubRubricIds = formSubRubrics.stream()
                                             .map(SubRubric::getId)
                                             .filter(java.util.Objects::nonNull)
                                             .collect(Collectors.toSet());

        managedSubRubrics.removeIf(sr -> sr.getId() != null && !formSubRubricIds.contains(sr.getId()));
        
        Map<Long, SubRubric> existingSubRubricMap = managedSubRubrics.stream()
            .filter(sr -> sr.getId() != null)
            .collect(Collectors.toMap(SubRubric::getId, sr -> sr));

        List<SubRubric> updatedSubRubricsList = new ArrayList<>();
        
        for (SubRubric formSubRubric : formSubRubrics) {
            SubRubric targetSubRubric;

            if (formSubRubric.getId() != null && existingSubRubricMap.containsKey(formSubRubric.getId())) {
                targetSubRubric = existingSubRubricMap.get(formSubRubric.getId());
                targetSubRubric.setDescription(formSubRubric.getDescription());
                targetSubRubric.setName(formSubRubric.getName()); 
                targetSubRubric.setMarks(formSubRubric.getMarks());
                
                updateSubRubricRatings(targetSubRubric, formSubRubric.getRatings());
                
            } else {
                targetSubRubric = formSubRubric;
                if (targetSubRubric.getRatings() != null) {
                    targetSubRubric.getRatings().forEach(r -> {
                        r.setSubRubric(targetSubRubric);
                        r.setRubric(null);
                    });
                }
            }
            
            targetSubRubric.setRubric(existingRubric); 
            updatedSubRubricsList.add(targetSubRubric);
        }
        
        managedSubRubrics.clear(); 
        managedSubRubrics.addAll(updatedSubRubricsList);
    }

    private void updateSubRubricRatings(SubRubric targetSubRubric, List<Rating> formRatings) {
        List<Rating> managedRatings = targetSubRubric.getRatings();
        
        if (formRatings == null) {
            formRatings = new ArrayList<>();
        }
        
        Set<Long> formRatingIds = formRatings.stream()
                                     .map(Rating::getId)
                                     .filter(java.util.Objects::nonNull)
                                     .collect(Collectors.toSet());
        
        managedRatings.removeIf(r -> r.getId() != null && !formRatingIds.contains(r.getId()));
        
        Map<Long, Rating> existingRatingMap = managedRatings.stream()
                                     .filter(r -> r.getId() != null)
                                     .collect(Collectors.toMap(Rating::getId, r -> r));

        List<Rating> finalOrderedRatings = new ArrayList<>();

        for (Rating formRating : formRatings) {
            Rating entityToSave;
            
            if (formRating.getId() != null && existingRatingMap.containsKey(formRating.getId())) {
                entityToSave = existingRatingMap.get(formRating.getId());
                entityToSave.setName(formRating.getName());
                entityToSave.setDescription(formRating.getDescription());
                entityToSave.setMarks(formRating.getMarks());
                entityToSave.setSubRubric(targetSubRubric); 
                entityToSave.setRubric(null);
            } else {
                entityToSave = formRating;
                entityToSave.setSubRubric(targetSubRubric); 
                entityToSave.setRubric(null);
            }
            
            finalOrderedRatings.add(entityToSave);
        }

        managedRatings.clear(); 
        managedRatings.addAll(finalOrderedRatings);
    }

    private void updateDirectRatings(Rubric existingRubric, List<Rating> formRatings) {
        List<Rating> managedRatings = existingRubric.getRatings();
        
        if (formRatings == null) {
            formRatings = new ArrayList<>();
        }
        
        Set<Long> formRatingIds = formRatings.stream()
                                     .map(Rating::getId)
                                     .filter(java.util.Objects::nonNull)
                                     .collect(Collectors.toSet());
        
        managedRatings.removeIf(r -> r.getId() != null && !formRatingIds.contains(r.getId()));
        
        Map<Long, Rating> existingRatingMap = managedRatings.stream()
                                     .filter(r -> r.getId() != null)
                                     .collect(Collectors.toMap(Rating::getId, r -> r));

        List<Rating> finalOrderedRatings = new ArrayList<>();

        for (Rating formRating : formRatings) {
            Rating entityToSave;
            
            if (formRating.getId() != null && existingRatingMap.containsKey(formRating.getId())) {
                entityToSave = existingRatingMap.get(formRating.getId());
                entityToSave.setName(formRating.getName());
                entityToSave.setDescription(formRating.getDescription());
                entityToSave.setMarks(formRating.getMarks());
                entityToSave.setRubric(existingRubric); 
                entityToSave.setSubRubric(null);
            } else {
                entityToSave = formRating;
                entityToSave.setRubric(existingRubric); 
                entityToSave.setSubRubric(null);
            }
            
            finalOrderedRatings.add(entityToSave);
        }

        managedRatings.clear(); 
        managedRatings.addAll(finalOrderedRatings);
    }

    @Transactional
    public void deleteRubric(Long rubricId) {
        if (!rubricRepository.existsById(rubricId)) {
            throw new EntityNotFoundException("Cannot delete. Rubric not found with id: " + rubricId);
        }
        
        Rubric rubric = rubricRepository.findById(rubricId).orElse(null);
        if (rubric != null) {
            deleteMarksForRubric(rubric);
        }
        
        rubricRepository.deleteById(rubricId);
    }

    private void deleteMarksForRubric(Rubric rubric) {
        List<Mark> rubricMarks = markRepository.findByRubric(rubric);
        if (!rubricMarks.isEmpty()) {
            markRepository.deleteAll(rubricMarks);
        }
        
        if (rubric.getSubRubrics() != null) {
            for (SubRubric subRubric : rubric.getSubRubrics()) {
                List<Mark> subRubricMarks = markRepository.findBySubRubric(subRubric);
                if (!subRubricMarks.isEmpty()) {
                    markRepository.deleteAll(subRubricMarks);
                }
            }
        }
    }

    public boolean isRubricNameDuplicate(String name, Long assessmentId, Long rubricIdToExclude) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        
        String normalizedName = name.replaceAll("\\s+", "").toLowerCase();
        
        Assessment assessment = assessmentRepository.findById(assessmentId).orElse(null);
        if (assessment != null && assessment.getRubrics() != null) {
            for (Rubric rubric : assessment.getRubrics()) {
                if (rubricIdToExclude != null && rubric.getId().equals(rubricIdToExclude)) {
                    continue;
                }
                
                if (rubric.getName() != null) {
                    String normalizedExisting = rubric.getName().replaceAll("\\s+", "").toLowerCase();
                    if (normalizedExisting.equals(normalizedName)) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    @Transactional
    public void moveAssessmentBlock(Long assessmentId, String blockType, String direction) {
        Assessment assessment = assessmentRepository.findById(assessmentId)
            .orElseThrow(() -> new EntityNotFoundException("Assessment not found"));
        
        Integer individualOrder = assessment.getIndividualOrder() != null ? assessment.getIndividualOrder() : 0;
        Integer groupOrder = assessment.getGroupOrder() != null ? assessment.getGroupOrder() : 1;
        
        if ("Individual".equalsIgnoreCase(blockType)) {
            if ("down".equalsIgnoreCase(direction) && individualOrder < groupOrder) {
                
                assessment.setIndividualOrder(groupOrder);
                assessment.setGroupOrder(individualOrder);
            } else if ("up".equalsIgnoreCase(direction) && individualOrder > groupOrder) {
                
                assessment.setIndividualOrder(groupOrder);
                assessment.setGroupOrder(individualOrder);
            }
        } else if ("Group".equalsIgnoreCase(blockType)) {
            if ("down".equalsIgnoreCase(direction) && groupOrder < individualOrder) {
                
                assessment.setGroupOrder(individualOrder);
                assessment.setIndividualOrder(groupOrder);
            } else if ("up".equalsIgnoreCase(direction) && groupOrder > individualOrder) {
                
                assessment.setGroupOrder(individualOrder);
                assessment.setIndividualOrder(groupOrder);
            }
        }
        
        assessmentRepository.save(assessment);
    }
    
@Transactional
public void moveRubric(Long rubricId, String direction) {
    Rubric rubric = rubricRepository.findById(rubricId)
        .orElseThrow(() -> new EntityNotFoundException("Rubric not found"));
    
    Assessment assessment = rubric.getAssessment();
    String assessmentType = rubric.getAssessmentTypes();
    
    List<Rubric> sameTypeRubrics = assessment.getRubrics().stream()
        .filter(r -> assessmentType.equals(r.getAssessmentTypes()))
        .sorted((r1, r2) -> {
            Integer order1 = r1.getDisplayOrder() != null ? r1.getDisplayOrder() : 0;
            Integer order2 = r2.getDisplayOrder() != null ? r2.getDisplayOrder() : 0;
            return Integer.compare(order1, order2);
        })
        .collect(Collectors.toList());
    
    int currentIndex = -1;
    for (int i = 0; i < sameTypeRubrics.size(); i++) {
        if (sameTypeRubrics.get(i).getId().equals(rubricId)) {
            currentIndex = i;
            break;
        }
    }
    
    if (currentIndex == -1) {
        throw new IllegalStateException("Rubric not found in its assessment type list");
    }
    
    System.out.println("DEBUG: Moving rubric " + rubric.getName() + " (ID: " + rubricId + ")");
    System.out.println("DEBUG: Current index: " + currentIndex + ", Direction: " + direction);
    System.out.println("DEBUG: List size: " + sameTypeRubrics.size());
    
    if ("up".equalsIgnoreCase(direction) && currentIndex > 0) {
        
        Rubric previousRubric = sameTypeRubrics.get(currentIndex - 1);
        Integer tempOrder = rubric.getDisplayOrder();
        
        System.out.println("DEBUG: Swapping " + rubric.getName() + " (order " + rubric.getDisplayOrder() + ") with " + previousRubric.getName() + " (order " + previousRubric.getDisplayOrder() + ")");
        
        rubric.setDisplayOrder(previousRubric.getDisplayOrder());
        previousRubric.setDisplayOrder(tempOrder);
        
        rubricRepository.save(rubric);
        rubricRepository.save(previousRubric);
        
        System.out.println("DEBUG: After swap - " + rubric.getName() + " order: " + rubric.getDisplayOrder() + ", " + previousRubric.getName() + " order: " + previousRubric.getDisplayOrder());
        
    } else if ("down".equalsIgnoreCase(direction) && currentIndex < sameTypeRubrics.size() - 1) {
        
        Rubric nextRubric = sameTypeRubrics.get(currentIndex + 1);
        Integer tempOrder = rubric.getDisplayOrder();
        
        System.out.println("DEBUG: Swapping " + rubric.getName() + " (order " + rubric.getDisplayOrder() + ") with " + nextRubric.getName() + " (order " + nextRubric.getDisplayOrder() + ")");
        
        rubric.setDisplayOrder(nextRubric.getDisplayOrder());
        nextRubric.setDisplayOrder(tempOrder);
        
        rubricRepository.save(rubric);
        rubricRepository.save(nextRubric);
        
        System.out.println("DEBUG: After swap - " + rubric.getName() + " order: " + rubric.getDisplayOrder() + ", " + nextRubric.getName() + " order: " + nextRubric.getDisplayOrder());
    } else {
        System.out.println("DEBUG: No swap performed - at boundary or invalid direction");
    }
}

@Transactional
public void initializeRubricOrders(Long assessmentId) {
    Assessment assessment = assessmentRepository.findById(assessmentId)
        .orElseThrow(() -> new EntityNotFoundException("Assessment not found"));
    
    Map<String, List<Rubric>> rubricsByType = new HashMap<>();
    
    for (Rubric rubric : assessment.getRubrics()) {
        String type = rubric.getAssessmentTypes();
        if (type == null || type.trim().isEmpty()) {
            type = "Uncategorized";
        }
        rubricsByType.computeIfAbsent(type, k -> new ArrayList<>()).add(rubric);
    }
    
    for (Map.Entry<String, List<Rubric>> entry : rubricsByType.entrySet()) {
        List<Rubric> rubrics = entry.getValue();
        
        System.out.println("DEBUG - Initializing orders for type: " + entry.getKey());
        
        rubrics.sort((r1, r2) -> {
            Integer o1 = r1.getDisplayOrder();
            Integer o2 = r2.getDisplayOrder();
            
            if (o1 == null && o2 == null) {
                return r1.getId().compareTo(r2.getId()); 
            }
            if (o1 == null) return 1;
            if (o2 == null) return -1;
            
            int orderCompare = Integer.compare(o1, o2);
            if (orderCompare == 0) {
                return r1.getId().compareTo(r2.getId());
            }
            return orderCompare;
        });
        
        for (int i = 0; i < rubrics.size(); i++) {
            Rubric rubric = rubrics.get(i);
            Integer currentOrder = rubric.getDisplayOrder();
            
            if (currentOrder == null || currentOrder != i) {
                System.out.println("DEBUG - Setting " + rubric.getName() + " order from " + currentOrder + " to " + i);
                rubric.setDisplayOrder(i);
                rubricRepository.saveAndFlush(rubric);
            } else {
                System.out.println("DEBUG - " + rubric.getName() + " already has correct order: " + i);
            }
        }
    }
}
}