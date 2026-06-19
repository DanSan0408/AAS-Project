package com.capstone.adproject.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "student_assessment_assignment", indexes = {
    @Index(name = "idx_saa_assessment_student", columnList = "assessment_id, student_id"),
    @Index(name = "idx_saa_student", columnList = "student_id")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_saa_assessment_student", columnNames = {"assessment_id", "student_id"})
})
public class StudentAssessmentAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "self_assessment")
    private Boolean selfAssessment = false;

    @Column(name = "peer_assessment")
    private Boolean peerAssessment = false;

    @Column(name = "group_assessment")
    private Boolean groupAssessment = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", nullable = false)
    private Assessment assessment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false, foreignKey = @ForeignKey(name = "fk_saa_student"))
    private Student student;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Assessment getAssessment() {
        return assessment;
    }

    public void setAssessment(Assessment assessment) {
        this.assessment = assessment;
    }

    public Student getStudent() {
        return student;
    }

    public void setStudent(Student student) {
        this.student = student;
    }

    public Boolean getSelfAssessment() {
        return selfAssessment;
    }

    public void setSelfAssessment(Boolean selfAssessment) {
        this.selfAssessment = selfAssessment;
    }

    public Boolean getPeerAssessment() {
        return peerAssessment;
    }

    public void setPeerAssessment(Boolean peerAssessment) {
        this.peerAssessment = peerAssessment;
    }

    public Boolean getGroupAssessment() {
        return groupAssessment;
    }

    public void setGroupAssessment(Boolean groupAssessment) {
        this.groupAssessment = groupAssessment;
    }
}