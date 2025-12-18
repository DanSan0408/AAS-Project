package com.capstone.adproject.dto;

import java.util.ArrayList;
import java.util.List;

public class GroupFactorDto {
    private Long groupId;
    private String groupName;
    private List<StudentFactorDto> studentFactors;
    private int teamSize;
    
    public GroupFactorDto() {
        this.studentFactors = new ArrayList<>();
    }
    
    // Getters and Setters
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    
    public List<StudentFactorDto> getStudentFactors() { return studentFactors; }
    public void setStudentFactors(List<StudentFactorDto> studentFactors) { this.studentFactors = studentFactors; }
    
    public int getTeamSize() { return teamSize; }
    public void setTeamSize(int teamSize) { this.teamSize = teamSize; }
}