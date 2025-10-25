package com.capstone.adproject.dto;

import java.util.List;

public class GroupAssignmentDto {

    private String groupName;
    private Long academicSupervisorId;
    private Long industrialSupervisorId;
    private List<Long> selectedStudentIds;

    // Constructors, Getters, and Setters

    public GroupAssignmentDto() {}

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public Long getAcademicSupervisorId() {
        return academicSupervisorId;
    }

    public void setAcademicSupervisorId(Long academicSupervisorId) {
        this.academicSupervisorId = academicSupervisorId;
    }

    public Long getIndustrialSupervisorId() {
        return industrialSupervisorId;
    }

    public void setIndustrialSupervisorId(Long industrialSupervisorId) {
        this.industrialSupervisorId = industrialSupervisorId;
    }

    public List<Long> getSelectedStudentIds() {
        return selectedStudentIds;
    }

    public void setSelectedStudentIds(List<Long> selectedStudentIds) {
        this.selectedStudentIds = selectedStudentIds;
    }
}