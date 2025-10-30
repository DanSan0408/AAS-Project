// com.capstone.adproject.model.RubricCriteriaWrapper.java

package com.capstone.adproject.model;

// Add necessary imports

public class RubricCriteriaWrapper {
    private final Object item;

    public RubricCriteriaWrapper(Object item) {
        this.item = item;
    }

    public boolean isRubric() {
        return item instanceof Rubric;
    }

    public boolean isCriteria() {
        return item instanceof Criteria;
    }

    public Object getItem() {
        return item;
    }

    // You should keep all the existing getters on the wrapper
    public String getName() {
        if (isRubric()) return ((Rubric) item).getName();
        if (isCriteria()) return ((Criteria) item).getName();
        return null;
    }
    
    // ... include the rest of the original getter methods (getEvaluationType, getAssessmentTypes)
    public String getEvaluationType() {
        if (isRubric()) return ((Rubric) item).getEvaluationType();
        if (isCriteria()) return ((Criteria) item).getEvaluationType();
        return null;
    }
    
    public String getAssessmentTypes() {
        if (isRubric()) return ((Rubric) item).getAssessmentTypes();
        if (isCriteria()) return ((Criteria) item).getAssessmentTypes();
        return null;
    }
}