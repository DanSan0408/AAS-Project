package com.capstone.adproject.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
public  class RandomizationInputDto {

    @Min(1)
    @Max(100)
    private int maxStudentsPerGroup; 

    public RandomizationInputDto() {} 

    public int getMaxStudentsPerGroup() { return maxStudentsPerGroup; }
    public void setMaxStudentsPerGroup(int maxStudentsPerGroup) { this.maxStudentsPerGroup = maxStudentsPerGroup; }
}
