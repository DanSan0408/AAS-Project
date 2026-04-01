package com.capstone.adproject.model;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "project_group")
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String groupName;
    private int groupSize;

    @ManyToOne
    @JoinColumn(name = "academic_supervisor_id")
    private Lecturer academicSupervisor;

    @ManyToOne
    @JoinColumn(name = "industrial_supervisor_id")
    private Lecturer industrialSupervisor;

    @ManyToOne
    @JoinColumn(name = "course_id")
    private Course course;

    @OneToMany(mappedBy = "group")
    private List<Student> students = new ArrayList<>(); 

    public List<Student> getStudents() {
        return students;
    }

    public void setStudents(List<Student> students) {
        this.students = students;

    }

    public Group() {}

    public Group(String groupName) {
        this.groupName = groupName;
    }

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

    public Lecturer getIndustrialSupervisor() {
        return industrialSupervisor;
    }

    public void setIndustrialSupervisor(Lecturer industrialSupervisor) {
        this.industrialSupervisor = industrialSupervisor;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public int getGroupSize() {
        return groupSize;
    }

    public void setGroupSize(int groupSize) {
        this.groupSize = groupSize;
    }
}
