package com.capstone.adproject.dto;

// This is a new, simple DTO for the randomization input form.
// Alternatively, you can define it as a static inner class within AdminController (as shown above in AdminController).

public  class RandomizationInputDto {
    private int maxStudentsPerGroup; // Renamed for clarity
    
    // Add default constructor if needed for Spring binding
    public RandomizationInputDto() {} 

    public int getMaxStudentsPerGroup() { return maxStudentsPerGroup; }
    public void setMaxStudentsPerGroup(int maxStudentsPerGroup) { this.maxStudentsPerGroup = maxStudentsPerGroup; }
}
