package com.capstone.adproject.model;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "factor_override_history")
public class FactorOverrideHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "student_id", foreignKey = @ForeignKey(name = "fk_factor_override_history_student"))
    private Student student;

    private Double oldFactor;
    private Double newFactor;
    
    private Double oldGrandTotal;
    private Double newGrandTotal;

    private String changedBy;
    private LocalDateTime changedAt;

    public FactorOverrideHistory() {}

    public FactorOverrideHistory(Student student, Double oldFactor, Double newFactor, Double oldGrandTotal, Double newGrandTotal, String changedBy, LocalDateTime changedAt) {
        this.student = student;
        this.oldFactor = oldFactor;
        this.newFactor = newFactor;
        this.oldGrandTotal = oldGrandTotal;
        this.newGrandTotal = newGrandTotal;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }
    
    public Double getOldFactor() { return oldFactor; }
    public void setOldFactor(Double oldFactor) { this.oldFactor = oldFactor; }
    
    public Double getNewFactor() { return newFactor; }
    public void setNewFactor(Double newFactor) { this.newFactor = newFactor; }
    
    public Double getOldGrandTotal() { return oldGrandTotal; }
    public void setOldGrandTotal(Double oldGrandTotal) { this.oldGrandTotal = oldGrandTotal; }
    
    public Double getNewGrandTotal() { return newGrandTotal; }
    public void setNewGrandTotal(Double newGrandTotal) { this.newGrandTotal = newGrandTotal; }
    
    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }
    
    public LocalDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(LocalDateTime changedAt) { this.changedAt = changedAt; }
}
