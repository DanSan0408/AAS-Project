package com.capstone.adproject.model;

import java.util.ArrayList; // <-- New Import
import java.util.List;     // <-- New Import
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.persistence.CascadeType;
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
    // FIX 1: Change Set to List and initialize with ArrayList
    private List<Rubric> rubrics = new ArrayList<>();

    @OneToMany(mappedBy = "assessment", cascade = CascadeType.ALL, orphanRemoval = true)
    // FIX 2: Change Set to List and initialize with ArrayList
    private List<Criteria> criteria = new ArrayList<>(); 

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    // FIX 3: Update getters/setters to use List
    public List<Rubric> getRubrics() { 
        if (rubrics == null) { rubrics = new ArrayList<>(); }
        return rubrics; 
    }
    public void setRubrics(List<Rubric> rubrics) { this.rubrics = rubrics; }

    @Transient
    private double totalMarks;

    // Calculation logic remains fine
    public double getTotalMarks() {
        double rubricMarks = this.getRubrics().stream()
                .mapToDouble(rubric -> rubric.getMarks() != null ? rubric.getMarks().doubleValue() : 0.0)
                .sum();
        
        double criteriaMarks = this.getCriteria().stream()
                .mapToDouble(criteria -> criteria.getMarks() != null ? criteria.getMarks().doubleValue() : 0.0)
                .sum();

        // This setter is redundant if you're using a getter that calculates the value, 
        // but it's kept to respect the original code structure.
        this.totalMarks = rubricMarks + criteriaMarks; 
        return this.totalMarks;
    }

    public Map<Integer, Double> getCloMarks() {
        // Calculation logic remains fine
        Stream<Map.Entry<Integer, Double>> rubricCloStream = this.getRubrics().stream()
            .filter(r -> r.getClo() != null && r.getCloMarks() != null)
            .map(r -> Map.entry(r.getClo(), r.getCloMarks()));

        Stream<Map.Entry<Integer, Double>> criteriaCloStream = this.getCriteria().stream()
            .filter(c -> c.getClo() != null && c.getCloMarks() != null)
            .map(c -> Map.entry(c.getClo(), c.getCloMarks()));

        return Stream.concat(rubricCloStream, criteriaCloStream)
            .collect(Collectors.groupingBy(
                Map.Entry::getKey,
                Collectors.summingDouble(Map.Entry::getValue)
            ));
    }

    public void setTotalMarks(double totalMarks) {
        this.totalMarks = totalMarks;
    }

    @Transient
    private Map<Integer, Double> cloMarks;

    // Note: Since getCloMarks() is calculated, this setter and the transient field are only 
    // useful if you explicitly calculate and set the value in the service layer 
    // before passing it to the view.
    public void setCloMarks(Map<Integer, Double> cloMarks) {
        this.cloMarks = cloMarks;
    }
    
    // FIX 4: Update getters/setters to use List
    public List<Criteria> getCriteria() {
        if (criteria == null) { criteria = new ArrayList<>(); }
        return criteria;
    }

    public void setCriteria(List<Criteria> criteria) {
        this.criteria = criteria;
    }
    
    // FIX 5: Update helper methods to use List
    public void addCriteria(Criteria criterion) {
        if (this.criteria == null) this.criteria = new ArrayList<>();
        this.criteria.add(criterion);
        criterion.setAssessment(this);
    }
    
    // FIX 6: Update helper methods to use List
    public void removeCriteria(Criteria criterion) {
        if (this.criteria != null) this.criteria.remove(criterion);
        criterion.setAssessment(null);
    }
    
    // Optional: Add helper methods for Rubric as well
    public void addRubric(Rubric rubric) {
        if (this.rubrics == null) this.rubrics = new ArrayList<>();
        this.rubrics.add(rubric);
        rubric.setAssessment(this);
    }
    
    public void removeRubric(Rubric rubric) {
        if (this.rubrics != null) this.rubrics.remove(rubric);
        rubric.setAssessment(null);
    }
}