package com.capstone.adproject.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Transient;

@Entity
public class Assessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @OneToMany(mappedBy = "assessment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Rubric> rubrics = new ArrayList<>();
    
    // ========== COMMENT CONFIGURATION FIELDS - NOW SEPARATED BY ASSESSMENT TYPE ==========
    
    // ===== GROUP ASSESSMENT COMMENTS =====
    
    /**
     * Number of comment fields for GROUP assessments
     */
    @Column(name = "group_comment_count")
    private Integer groupCommentCount = 0;
    
    /**
     * Minimum character length for GROUP assessment comments
     */
    @Column(name = "group_comment_min_length")
    private Integer groupCommentMinLength = 20;
    
    /**
     * Whether GROUP assessment comments should be anonymous
     */
    @Column(name = "group_comments_anonymous")
    private Boolean groupCommentsAnonymous = true;
    
    /**
     * Labels/prompts for GROUP assessment comment fields
     */
    @ElementCollection
    @Column(name = "label", length = 500)
    private List<String> groupCommentLabels = new ArrayList<>();
    
    // ===== INDIVIDUAL ASSESSMENT COMMENTS =====
    
    /**
     * Number of comment fields for INDIVIDUAL assessments
     */
    @Column(name = "individual_comment_count")
    private Integer individualCommentCount = 0;
    
    /**
     * Minimum character length for INDIVIDUAL assessment comments
     */
    @Column(name = "individual_comment_min_length")
    private Integer individualCommentMinLength = 20;
    
    /**
     * Whether INDIVIDUAL assessment comments should be anonymous
     */
    @Column(name = "individual_comments_anonymous")
    private Boolean individualCommentsAnonymous = true;
    
    /**
     * Labels/prompts for INDIVIDUAL assessment comment fields
     */
    @ElementCollection
    @Column(name = "label", length = 500)
    private List<String> individualCommentLabels = new ArrayList<>();

    // ========== GETTERS AND SETTERS ==========
    
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public List<Rubric> getRubrics() { 
        if (rubrics == null) { 
            rubrics = new ArrayList<>(); 
        }
        return rubrics; 
    }
    
    public void setRubrics(List<Rubric> rubrics) { 
        this.rubrics = rubrics; 
    }
    
    // ===== GROUP ASSESSMENT COMMENT GETTERS/SETTERS =====
    
    public Integer getGroupCommentCount() {
        return groupCommentCount != null ? groupCommentCount : 0;
    }
    
    public void setGroupCommentCount(Integer groupCommentCount) {
        this.groupCommentCount = groupCommentCount;
    }
    
    public Integer getGroupCommentMinLength() {
        return groupCommentMinLength != null ? groupCommentMinLength : 20;
    }
    
    public void setGroupCommentMinLength(Integer groupCommentMinLength) {
        this.groupCommentMinLength = groupCommentMinLength;
    }
    
    public Boolean getGroupCommentsAnonymous() {
        return groupCommentsAnonymous != null ? groupCommentsAnonymous : true;
    }
    
    public void setGroupCommentsAnonymous(Boolean groupCommentsAnonymous) {
        this.groupCommentsAnonymous = groupCommentsAnonymous;
    }
    
    public List<String> getGroupCommentLabels() {
        if (groupCommentLabels == null) {
            groupCommentLabels = new ArrayList<>();
        }
        return groupCommentLabels;
    }
    
    public void setGroupCommentLabels(List<String> groupCommentLabels) {
        this.groupCommentLabels = groupCommentLabels;
    }
    
    public String getGroupCommentLabel(int index) {
    if (groupCommentLabels == null || groupCommentLabels.isEmpty() || index >= groupCommentLabels.size()) {
        return "Group Comment " + (index + 1);
    }
    return groupCommentLabels.get(index);
}
    
    // ===== INDIVIDUAL ASSESSMENT COMMENT GETTERS/SETTERS =====
    
    public Integer getIndividualCommentCount() {
        return individualCommentCount != null ? individualCommentCount : 0;
    }
    
    public void setIndividualCommentCount(Integer individualCommentCount) {
        this.individualCommentCount = individualCommentCount;
    }
    
    public Integer getIndividualCommentMinLength() {
        return individualCommentMinLength != null ? individualCommentMinLength : 20;
    }
    
    public void setIndividualCommentMinLength(Integer individualCommentMinLength) {
        this.individualCommentMinLength = individualCommentMinLength;
    }
    
    public Boolean getIndividualCommentsAnonymous() {
        return individualCommentsAnonymous != null ? individualCommentsAnonymous : true;
    }
    
    public void setIndividualCommentsAnonymous(Boolean individualCommentsAnonymous) {
        this.individualCommentsAnonymous = individualCommentsAnonymous;
    }
    
    public List<String> getIndividualCommentLabels() {
        if (individualCommentLabels == null) {
            individualCommentLabels = new ArrayList<>();
        }
        return individualCommentLabels;
    }
    
    public void setIndividualCommentLabels(List<String> individualCommentLabels) {
        this.individualCommentLabels = individualCommentLabels;
    }
    
    public String getIndividualCommentLabel(int index) {
    if (index < 0) {                         // ✅ ADD THIS
        return "Individual Comment 1";       // ✅ ADD THIS
    }                                        // ✅ ADD THIS
    if (individualCommentLabels == null || individualCommentLabels.isEmpty() || index >= individualCommentLabels.size()) {
        return "Individual Comment " + (index + 1);
    }
    return individualCommentLabels.get(index);  // ✅ NOW SAFE
}
    
    // ===== HELPER METHODS =====
    
    /**
     * Get comment count based on assessment type
     */
    public Integer getCommentCountForType(String assessmentType) {
        if (assessmentType != null && assessmentType.toLowerCase().contains("group")) {
            return getGroupCommentCount();
        } else {
            return getIndividualCommentCount();
        }
    }
    
    /**
     * Get comment min length based on assessment type
     */
    public Integer getCommentMinLengthForType(String assessmentType) {
        if (assessmentType != null && assessmentType.toLowerCase().contains("group")) {
            return getGroupCommentMinLength();
        } else {
            return getIndividualCommentMinLength();
        }
    }
    
    /**
     * Get comments anonymous setting based on assessment type
     */
    public Boolean getCommentsAnonymousForType(String assessmentType) {
        if (assessmentType != null && assessmentType.toLowerCase().contains("group")) {
            return getGroupCommentsAnonymous();
        } else {
            return getIndividualCommentsAnonymous();
        }
    }
    
    /**
     * Get comment label based on assessment type and index
     */
    public String getCommentLabelForType(String assessmentType, int index) {
        if (assessmentType != null && assessmentType.toLowerCase().contains("group")) {
            return getGroupCommentLabel(index);
        } else {
            return getIndividualCommentLabel(index);
        }
    }

    @Transient
    private double totalMarks;

    public double getTotalMarks() {
        double rubricMarks = this.getRubrics().stream()
                .mapToDouble(rubric -> rubric.getMarks() != null ? rubric.getMarks().doubleValue() : 0.0)
                .sum();

        this.totalMarks = rubricMarks; 
        return this.totalMarks;
    }

    public void setTotalMarks(double totalMarks) {
        this.totalMarks = totalMarks;
    }

    @Transient
    private Map<Integer, Double> cloMarks;

    public Map<Integer, Double> getCloMarks() {
        return this.getRubrics().stream()
            .filter(r -> r.getClo() != null && r.getCloMarks() != null)
            .collect(Collectors.groupingBy(
                Rubric::getClo,
                Collectors.summingDouble(Rubric::getCloMarks)
            ));
    }

    public void setCloMarks(Map<Integer, Double> cloMarks) {
        this.cloMarks = cloMarks;
    }
    
    public void addRubric(Rubric rubric) {
        if (this.rubrics == null) {
            this.rubrics = new ArrayList<>();
        }
        this.rubrics.add(rubric);
        rubric.setAssessment(this);
    }
    
    public void removeRubric(Rubric rubric) {
        if (this.rubrics != null) {
            this.rubrics.remove(rubric);
        }
        rubric.setAssessment(null);
    }
}