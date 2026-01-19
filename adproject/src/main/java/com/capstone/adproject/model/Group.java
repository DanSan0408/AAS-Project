package com.capstone.adproject.model;

import java.util.ArrayList;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "project_group")
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String groupName;
    private int groupSize; // Optional, to store the desired size if needed, but the actual size is determined by students.size()

    @ManyToOne
    @JoinColumn(name = "academic_supervisor_id")
    private Lecturer academicSupervisor;

    @ManyToOne
    @JoinColumn(name = "industrial_supervisor_id")
    private IndustrialSupervisor industrialSupervisor;

    // You can define the relationship to students here, or just use the mappedBy attribute in Student
    // @OneToMany(mappedBy = "group")
    // private List<Student> students; 

    @OneToMany(mappedBy = "group")
    private List<Student> students = new ArrayList<>(); // Initialize the list

    // ... (existing constructors)

    // NEW: Getter/Setter for students
    public List<Student> getStudents() {
        return students;
    }

    public void setStudents(List<Student> students) {
        this.students = students;

    }
    // Constructors
    public Group() {}

    public Group(String groupName) {
        this.groupName = groupName;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public Lecturer getAcademicSupervisor() {
        return academicSupervisor;
    }

    public void setAcademicSupervisor(Lecturer academicSupervisor) {
        this.academicSupervisor = academicSupervisor;
    }

    public IndustrialSupervisor getIndustrialSupervisor() {
        return industrialSupervisor;
    }

    public void setIndustrialSupervisor(IndustrialSupervisor industrialSupervisor) {
        this.industrialSupervisor = industrialSupervisor;
    }

    public int getGroupSize() {
        return groupSize;
    }

    public void setGroupSize(int groupSize) {
        this.groupSize = groupSize;
    }
}