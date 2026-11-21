package com.capstone.adproject.util;

import org.junit.jupiter.api.Test;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Rating;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.SubRubric;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssessmentOwnershipTest {

    private static Assessment assessment(Long id) {
        Assessment a = new Assessment();
        a.setId(id);
        return a;
    }

    private static Rubric rubricOf(Assessment a) {
        Rubric r = new Rubric();
        r.setAssessment(a);
        return r;
    }

    @Test
    void aRubricOfThisAssessmentIsAccepted() {
        assertDoesNotThrow(() -> AssessmentOwnership.requireRubric(assessment(1L), rubricOf(assessment(1L))));
    }

    @Test
    void aRubricOfAnotherAssessmentIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> AssessmentOwnership.requireRubric(assessment(1L), rubricOf(assessment(2L))));
    }

    @Test
    void aMissingRubricIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> AssessmentOwnership.requireRubric(assessment(1L), null));
    }

    @Test
    void aRatingReachedThroughItsSubRubricIsCheckedAgainstThatRubricsAssessment() {
        SubRubric sub = new SubRubric();
        sub.setRubric(rubricOf(assessment(2L)));
        Rating rating = new Rating();
        rating.setSubRubric(sub);

        assertThrows(IllegalArgumentException.class, () -> AssessmentOwnership.requireRating(assessment(1L), rating));

        sub.setRubric(rubricOf(assessment(1L)));
        assertDoesNotThrow(() -> AssessmentOwnership.requireRating(assessment(1L), rating));
    }

    @Test
    void aDirectRatingIsCheckedAgainstItsRubric() {
        Rating rating = new Rating();
        rating.setRubric(rubricOf(assessment(2L)));

        assertThrows(IllegalArgumentException.class, () -> AssessmentOwnership.requireRating(assessment(1L), rating));
    }

    @Test
    void noAssessmentToCheckAgainstIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> AssessmentOwnership.requireRubric(null, rubricOf(assessment(1L))));
    }
}
