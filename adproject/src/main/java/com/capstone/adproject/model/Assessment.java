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
    
    // ========== COMMENT CONFIGURATION FIELDS ==========
    
    /**
     * Number of comment fields students must fill out
     * Default: 1 (just the overall comment)
     */
    @Column(name = "comment_count")
    private Integer commentCount = 1;
    
    /**
     * Minimum character length for each comment
     * Default: 20
     */
    @Column(name = "comment_min_length")
    private Integer commentMinLength = 20;
    
    /**
     * Whether commenters should be shown as anonymous to the evaluated student
     * true = anonymous (students see "Teammate 1", "Teammate 2", etc.)
     * false = show real names
     * Default: true (anonymous)
     */
    @Column(name = "comments_anonymous")
    private Boolean commentsAnonymous = true;
    
    /**
     * Labels/prompts for each comment field (optional)
     * If not set, defaults to "Comment 1", "Comment 2", etc.
     */
    @ElementCollection
    @Column(name = "label", length = 500)
    private List<String> commentLabels = new ArrayList<>();

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
    
    // Comment configuration getters/setters
    
    public Integer getCommentCount() {
        return commentCount != null ? commentCount : 1;
    }
    
    public void setCommentCount(Integer commentCount) {
        this.commentCount = commentCount;
    }
    
    public Integer getCommentMinLength() {
        return commentMinLength != null ? commentMinLength : 20;
    }
    
    public void setCommentMinLength(Integer commentMinLength) {
        this.commentMinLength = commentMinLength;
    }
    
    public Boolean getCommentsAnonymous() {
        return commentsAnonymous != null ? commentsAnonymous : true;
    }
    
    public void setCommentsAnonymous(Boolean commentsAnonymous) {
        this.commentsAnonymous = commentsAnonymous;
    }
    
    public List<String> getCommentLabels() {
        if (commentLabels == null) {
            commentLabels = new ArrayList<>();
        }
        return commentLabels;
    }
    
    public void setCommentLabels(List<String> commentLabels) {
        this.commentLabels = commentLabels;
    }
    
    /**
     * Get label for a specific comment index (0-based)
     * Returns default label if custom label not set
     */
    public String getCommentLabel(int index) {
        if (commentLabels != null && index < commentLabels.size() && 
            commentLabels.get(index) != null && !commentLabels.get(index).trim().isEmpty()) {
            return commentLabels.get(index);
        }
        return "Comment " + (index + 1);
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