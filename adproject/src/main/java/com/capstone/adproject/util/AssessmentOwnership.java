package com.capstone.adproject.util;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Rating;
import com.capstone.adproject.model.Rubric;

/**
 * Evaluation forms post rubric and rating ids. Before a mark or comment is written from them, the ids have to belong to the
 * assessment being evaluated; otherwise a crafted form could attach marks to another assessment's (or another course's) rubric.
 */
public final class AssessmentOwnership {

    private AssessmentOwnership() {}

    public static void requireRubric(Assessment assessment, Rubric rubric) {
        if (rubric == null || !sameAssessment(assessment, rubric.getAssessment())) {
            throw new IllegalArgumentException("That rubric does not belong to this assessment.");
        }
    }

    public static void requireRating(Assessment assessment, Rating rating) {
        Rubric owner = null;
        if (rating != null) {
            owner = rating.getSubRubric() != null ? rating.getSubRubric().getRubric() : rating.getRubric();
        }
        if (!sameAssessment(assessment, owner == null ? null : owner.getAssessment())) {
            throw new IllegalArgumentException("That rating does not belong to this assessment.");
        }
    }

    /** A rubric or rating that is attached to a different assessment is refused; one with no owner chain is not. */
    private static boolean sameAssessment(Assessment expected, Assessment actual) {
        if (expected == null || expected.getId() == null) {
            return false;
        }
        return actual == null || actual.getId() == null || expected.getId().equals(actual.getId());
    }
}
