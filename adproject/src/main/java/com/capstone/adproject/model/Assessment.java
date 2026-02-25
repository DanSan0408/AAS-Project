package com.capstone.adproject.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    
    @Column(name = "individual_order")
    private Integer individualOrder = 0;
    
    @Column(name = "group_order")
    private Integer groupOrder = 1;

    @Column(name = "group_comment_count")
    private Integer groupCommentCount = 0;
 
    @Column(name = "group_comment_min_length")
    private Integer groupCommentMinLength = 20;

    @Column(name = "group_comments_anonymous")
    private Boolean groupCommentsAnonymous = true;
   
    @ElementCollection
    @Column(name = "label", length = 500)
    private List<String> groupCommentLabels = new ArrayList<>();
    
    @Column(name = "group_comment_min_lengths", columnDefinition = "TEXT")
    private String groupCommentMinLengthsJson;
   
    @Column(name = "group_comment_anonymous_flags", columnDefinition = "TEXT")
    private String groupCommentAnonymousFlagsJson;
  
    @Column(name = "individual_comment_count")
    private Integer individualCommentCount = 0;
    
    @Column(name = "individual_comment_min_length")
    private Integer individualCommentMinLength = 20;
   
    @Column(name = "individual_comments_anonymous")
    private Boolean individualCommentsAnonymous = true;
    
    @ElementCollection
    @Column(name = "label", length = 500)
    private List<String> individualCommentLabels = new ArrayList<>();
    
    @Column(name = "individual_comment_min_lengths", columnDefinition = "TEXT")
    private String individualCommentMinLengthsJson;
    
    @Column(name = "individual_comment_anonymous_flags", columnDefinition = "TEXT")
    private String individualCommentAnonymousFlagsJson;
    
    @Transient
    private static final ObjectMapper objectMapper = new ObjectMapper();

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
    
    public Integer getIndividualOrder() { return individualOrder; }
    public void setIndividualOrder(Integer individualOrder) { this.individualOrder = individualOrder; }
    
    public Integer getGroupOrder() { return groupOrder; }
    public void setGroupOrder(Integer groupOrder) { this.groupOrder = groupOrder; }
    
    public Integer getGroupCommentCount() {
        return groupCommentCount != null ? groupCommentCount : 0;
    }
    
    public void setGroupCommentCount(Integer groupCommentCount) {
        this.groupCommentCount = groupCommentCount;
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
   
    public List<Integer> getGroupCommentMinLengths() {
        if (groupCommentMinLengthsJson == null || groupCommentMinLengthsJson.trim().isEmpty()) {
            
            List<Integer> defaults = new ArrayList<>();
            int count = groupCommentLabels != null ? groupCommentLabels.size() : 0;
            for (int i = 0; i < count; i++) {
                defaults.add(20); 
            }
            return defaults;
        }
        try {
            return objectMapper.readValue(groupCommentMinLengthsJson, new TypeReference<List<Integer>>() {});
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
    
    public void setGroupCommentMinLengths(List<Integer> minLengths) {
        try {
            this.groupCommentMinLengthsJson = objectMapper.writeValueAsString(minLengths);
        } catch (IOException e) {
            e.printStackTrace();
            this.groupCommentMinLengthsJson = "[]";
        }
    }
    
    public Integer getGroupCommentMinLength(int index) {
        List<Integer> minLengths = getGroupCommentMinLengths();
        if (index >= 0 && index < minLengths.size()) {
            return minLengths.get(index);
        }
        return 20;
    }
    
    public List<Boolean> getGroupCommentAnonymousFlags() {
        if (groupCommentAnonymousFlagsJson == null || groupCommentAnonymousFlagsJson.trim().isEmpty()) {
            
            List<Boolean> defaults = new ArrayList<>();
            int count = groupCommentLabels != null ? groupCommentLabels.size() : 0;
            for (int i = 0; i < count; i++) {
                defaults.add(true);
            }
            return defaults;
        }
        try {
            return objectMapper.readValue(groupCommentAnonymousFlagsJson, new TypeReference<List<Boolean>>() {});
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
    
    public void setGroupCommentAnonymousFlags(List<Boolean> anonymousFlags) {
        try {
            this.groupCommentAnonymousFlagsJson = objectMapper.writeValueAsString(anonymousFlags);
        } catch (IOException e) {
            e.printStackTrace();
            this.groupCommentAnonymousFlagsJson = "[]";
        }
    }
    
    public Boolean isGroupCommentAnonymous(int index) {
        List<Boolean> flags = getGroupCommentAnonymousFlags();
        if (index >= 0 && index < flags.size()) {
            return flags.get(index);
        }
        return true; 
    }
    
    @Deprecated
    public Integer getGroupCommentMinLength() {
        return getGroupCommentMinLength(0);
    }
    
    @Deprecated
    public void setGroupCommentMinLength(Integer groupCommentMinLength) {
        this.groupCommentMinLength = groupCommentMinLength;
    }
   
    @Deprecated
    public Boolean getGroupCommentsAnonymous() {
        return isGroupCommentAnonymous(0);
    }
    
    @Deprecated
    public void setGroupCommentsAnonymous(Boolean groupCommentsAnonymous) {
        this.groupCommentsAnonymous = groupCommentsAnonymous;
    }
    
    public Integer getIndividualCommentCount() {
        return individualCommentCount != null ? individualCommentCount : 0;
    }
    
    public void setIndividualCommentCount(Integer individualCommentCount) {
        this.individualCommentCount = individualCommentCount;
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
        if (index < 0) {
            return "Individual Comment 1";
        }
        if (individualCommentLabels == null || individualCommentLabels.isEmpty() || index >= individualCommentLabels.size()) {
            return "Individual Comment " + (index + 1);
        }
        return individualCommentLabels.get(index);
    }
    
    public List<Integer> getIndividualCommentMinLengths() {
        if (individualCommentMinLengthsJson == null || individualCommentMinLengthsJson.trim().isEmpty()) {
           
            List<Integer> defaults = new ArrayList<>();
            int count = individualCommentLabels != null ? individualCommentLabels.size() : 0;
            for (int i = 0; i < count; i++) {
                defaults.add(20); 
            }
            return defaults;
        }
        try {
            return objectMapper.readValue(individualCommentMinLengthsJson, new TypeReference<List<Integer>>() {});
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
    
    public void setIndividualCommentMinLengths(List<Integer> minLengths) {
        try {
            this.individualCommentMinLengthsJson = objectMapper.writeValueAsString(minLengths);
        } catch (IOException e) {
            e.printStackTrace();
            this.individualCommentMinLengthsJson = "[]";
        }
    }
    
    public Integer getIndividualCommentMinLength(int index) {
        List<Integer> minLengths = getIndividualCommentMinLengths();
        if (index >= 0 && index < minLengths.size()) {
            return minLengths.get(index);
        }
        return 20; 
    }
    
    public List<Boolean> getIndividualCommentAnonymousFlags() {
        if (individualCommentAnonymousFlagsJson == null || individualCommentAnonymousFlagsJson.trim().isEmpty()) {
           
            List<Boolean> defaults = new ArrayList<>();
            int count = individualCommentLabels != null ? individualCommentLabels.size() : 0;
            for (int i = 0; i < count; i++) {
                defaults.add(true); 
            }
            return defaults;
        }
        try {
            return objectMapper.readValue(individualCommentAnonymousFlagsJson, new TypeReference<List<Boolean>>() {});
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
    
    public void setIndividualCommentAnonymousFlags(List<Boolean> anonymousFlags) {
        try {
            this.individualCommentAnonymousFlagsJson = objectMapper.writeValueAsString(anonymousFlags);
        } catch (IOException e) {
            e.printStackTrace();
            this.individualCommentAnonymousFlagsJson = "[]";
        }
    }
    
    public Boolean isIndividualCommentAnonymous(int index) {
        List<Boolean> flags = getIndividualCommentAnonymousFlags();
        if (index >= 0 && index < flags.size()) {
            return flags.get(index);
        }
        return true; 
    }
    
    @Deprecated
    public Integer getIndividualCommentMinLength() {
        return getIndividualCommentMinLength(0);
    }
    
    @Deprecated
    public void setIndividualCommentMinLength(Integer individualCommentMinLength) {
        this.individualCommentMinLength = individualCommentMinLength;
    }

    @Deprecated
    public Boolean getIndividualCommentsAnonymous() {
        return isIndividualCommentAnonymous(0);
    }
    
    @Deprecated
    public void setIndividualCommentsAnonymous(Boolean individualCommentsAnonymous) {
        this.individualCommentsAnonymous = individualCommentsAnonymous;
    }
    
    public Integer getCommentCountForType(String assessmentType) {
        if (assessmentType != null && assessmentType.toLowerCase().contains("group")) {
            return getGroupCommentCount();
        } else {
            return getIndividualCommentCount();
        }
    }
    
    public Integer getCommentMinLengthForType(String assessmentType, int index) {
        if (assessmentType != null && assessmentType.toLowerCase().contains("group")) {
            return getGroupCommentMinLength(index);
        } else {
            return getIndividualCommentMinLength(index);
        }
    }
    
    @Deprecated
    public Integer getCommentMinLengthForType(String assessmentType) {
        return getCommentMinLengthForType(assessmentType, 0);
    }
    
    public Boolean getCommentsAnonymousForType(String assessmentType, int index) {
        if (assessmentType != null && assessmentType.toLowerCase().contains("group")) {
            return isGroupCommentAnonymous(index);
        } else {
            return isIndividualCommentAnonymous(index);
        }
    }
    
    @Deprecated
    public Boolean getCommentsAnonymousForType(String assessmentType) {
        return getCommentsAnonymousForType(assessmentType, 0);
    }
    
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