package com.capstone.adproject.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "rubric")
public class Rubric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;
    private BigDecimal marks;
    private Integer clo;
    private Double cloMarks;
    private String assessmentTypes;
    
    @Column(name = "display_order")
    private Integer displayOrder = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id")
    private Assessment assessment;

    @OneToMany(mappedBy = "rubric", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SubRubric> subRubrics = new ArrayList<>();

    @OneToMany(mappedBy = "rubric", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Rating> ratings = new ArrayList<>();

    @ElementCollection
    @Column(name = "comment_label", length = 500)
    private List<String> rubricCommentLabels = new ArrayList<>();
    
    @ElementCollection
    @Column(name = "comment_min_length")
    private List<Integer> rubricCommentMinLengths = new ArrayList<>();
    
    @ElementCollection
    @Column(name = "comment_anonymous")
    private List<Boolean> rubricCommentAnonymousFlags = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public BigDecimal getMarks() { return marks; }
    public void setMarks(BigDecimal marks) { this.marks = marks; }
    
    public Assessment getAssessment() { return assessment; }
    public void setAssessment(Assessment assessment) { this.assessment = assessment; }
    
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
    
    public List<SubRubric> getSubRubrics() { 
        if (subRubrics == null) {
            subRubrics = new ArrayList<>();
        }
        return subRubrics; 
    }
    
    public void setSubRubrics(List<SubRubric> subRubrics) { 
        this.subRubrics = subRubrics; 
    }

    public List<Rating> getRatings() {
        if (ratings == null) {
            ratings = new ArrayList<>();
        }
        return ratings;
    }

    public void setRatings(List<Rating> ratings) {
        this.ratings = ratings;
    }

    public void addSubRubric(SubRubric subRubric) {
        if (subRubric != null) {
            if (this.subRubrics == null) {
                this.subRubrics = new ArrayList<>();
            }
            subRubric.setRubric(this); 
            this.subRubrics.add(subRubric); 
        }
    }

    public void addRating(Rating rating) {
        if (rating != null) {
            if (this.ratings == null) {
                this.ratings = new ArrayList<>();
            }
            rating.setRubric(this);
            this.ratings.add(rating);
        }
    }
    
    public Integer getClo() { return clo; }
    public void setClo(Integer clo) { this.clo = clo; }
    
    public Double getCloMarks() { return cloMarks; }
    public void setCloMarks(Double cloMarks) { this.cloMarks = cloMarks; }
    
    public String getAssessmentTypes() { return assessmentTypes; }
    public void setAssessmentTypes(String assessmentTypes) { this.assessmentTypes = assessmentTypes; }

    public List<String> getRubricCommentLabels() {
        if (rubricCommentLabels == null) {
            rubricCommentLabels = new ArrayList<>();
        }
        return rubricCommentLabels;
    }
    
    public void setRubricCommentLabels(List<String> rubricCommentLabels) {
        this.rubricCommentLabels = rubricCommentLabels;
    }
    
    public List<Integer> getRubricCommentMinLengths() {
        if (rubricCommentMinLengths == null) {
            rubricCommentMinLengths = new ArrayList<>();
        }
        return rubricCommentMinLengths;
    }
    
    public void setRubricCommentMinLengths(List<Integer> rubricCommentMinLengths) {
        this.rubricCommentMinLengths = rubricCommentMinLengths;
    }
    
    public List<Boolean> getRubricCommentAnonymousFlags() {
        if (rubricCommentAnonymousFlags == null) {
            rubricCommentAnonymousFlags = new ArrayList<>();
        }
        return rubricCommentAnonymousFlags;
    }
    
    public void setRubricCommentAnonymousFlags(List<Boolean> rubricCommentAnonymousFlags) {
        this.rubricCommentAnonymousFlags = rubricCommentAnonymousFlags;
    }
    
    public Integer getRubricCommentCount() {
        return rubricCommentLabels != null ? rubricCommentLabels.size() : 0;
    }
    
    public String getRubricCommentLabel(int index) {
        if (rubricCommentLabels == null || index >= rubricCommentLabels.size()) {
            return "Comment " + (index + 1);
        }
        return rubricCommentLabels.get(index);
    }
    
    public Integer getRubricCommentMinLength(int index) {
        if (rubricCommentMinLengths == null || index >= rubricCommentMinLengths.size()) {
            return 20; 
        }
        return rubricCommentMinLengths.get(index);
    }
    
    public Boolean isRubricCommentAnonymous(int index) {
        System.out.println("=== isRubricCommentAnonymous DEBUG ===");
        System.out.println("Checking index: " + index);
        System.out.println("rubricCommentAnonymousFlags: " + rubricCommentAnonymousFlags);
        
        if (rubricCommentAnonymousFlags == null) {
            System.out.println("Flags list is NULL - returning false");
            return false;
        }
        
        System.out.println("Flags list size: " + rubricCommentAnonymousFlags.size());
        
        if (index >= rubricCommentAnonymousFlags.size()) {
            System.out.println("Index out of bounds - returning false");
            return false;
        }
        
        Boolean flag = rubricCommentAnonymousFlags.get(index);
        System.out.println("Flag at index " + index + ": " + flag);
        System.out.println("====================================");
        
        return flag != null ? flag : false;
    }

    public boolean hasSubRubrics() {
        return subRubrics != null && !subRubrics.isEmpty();
    }

    public boolean hasDirectRatings() {
        return ratings != null && !ratings.isEmpty();
    }

    public BigDecimal calculateChildrenMarks() {
        BigDecimal total = BigDecimal.ZERO;
        
        if (subRubrics != null) {
            for (SubRubric sr : subRubrics) {
                if (sr.getMarks() != null) {
                    total = total.add(sr.getMarks());
                }
            }
        }
        
        if (ratings != null) {
            for (Rating r : ratings) {
                if (r.getMarks() != null) {
                    total = total.add(r.getMarks());
                }
            }
        }
        
        return total;
    }
    
    @Deprecated
    public Boolean getCommentsAnonymous() {
        if (rubricCommentAnonymousFlags != null && !rubricCommentAnonymousFlags.isEmpty()) {
            return rubricCommentAnonymousFlags.stream().anyMatch(flag -> flag != null && flag);
        }
        return false;
    }
    
    @Deprecated
    public void setCommentsAnonymous(Boolean commentsAnonymous) {
        
    }
    
    @Deprecated
    public Integer getRubricCommentMinLength() {
        if (rubricCommentMinLengths != null && !rubricCommentMinLengths.isEmpty()) {
            return rubricCommentMinLengths.get(0);
        }
        return 20;
    }
    
    @Deprecated
    public void setRubricCommentMinLength(Integer minLength) {
        
    }
    
    @Deprecated
    public void setRubricCommentCount(Integer count) {
    }
}