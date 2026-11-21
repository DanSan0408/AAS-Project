package com.capstone.adproject.model;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A mark must never carry NaN or Infinity into the database (MySQL refuses it and the evaluation fails to save). */
class MarkNumbersTest {

    private static Mark mark(String rubricMarks, Double rubricCloMarks, String ratingMarks) {
        Rubric rubric = new Rubric();
        rubric.setMarks(rubricMarks == null ? null : new BigDecimal(rubricMarks));
        rubric.setCloMarks(rubricCloMarks);
        rubric.setClo(1);
        Rating rating = new Rating();
        rating.setMarks(new BigDecimal(ratingMarks));
        Mark m = new Mark();
        m.setRubric(rubric);
        m.setRating(rating);
        return m;
    }

    @Test
    void aRubricWithNoMarksAndAZeroRatingDoesNotProduceNaN() {
        Mark m = mark("0", 10.0, "0");
        m.setCloMarks(0.0);

        m.prePersistOrUpdate();

        assertTrue(Double.isFinite(m.getCloMarks()));
        assertTrue(Double.isFinite(m.getMarkValue()));
        assertEquals(0.0, m.getCloMarks(), 0.0001);
    }

    @Test
    void aRubricWorthZeroMarksRecordsTheRatingButAddsNoCloMarks() {
        Mark m = mark("0", 0.0, "3");
        m.setCloMarks(3.0);   // what a form handler may have worked out from the rating alone

        m.prePersistOrUpdate();

        assertEquals(0.0, m.getCloMarks(), 0.0001, "a rubric worth nothing contributes nothing");
        assertEquals(3.0, m.getMarkValue(), 0.0001, "the rating the evaluator chose is still recorded");
    }

    @Test
    void aZeroMarkRubricWithNoCloMarksSetIsStillZeroNotTheRatingValue() {
        Mark m = mark("0", null, "4");
        m.setCloMarks(4.0);

        m.prePersistOrUpdate();

        assertEquals(0.0, m.getCloMarks(), 0.0001);
    }

    @Test
    void anOrdinaryMarkIsCalculatedAsBefore() {
        Mark m = mark("8", 10.0, "4");

        m.prePersistOrUpdate();

        assertEquals(5.0, m.getCloMarks(), 0.0001);   // 4 / 8 of 10 CLO marks
        assertEquals(4.0, m.getMarkValue(), 0.0001);
    }

    @Test
    void aNonFiniteValueFromAnywhereIsReplacedByZeroBeforeItIsSaved() {
        Mark m = mark("8", null, "4");
        m.setCloMarks(Double.NaN);
        m.setMarkValue(Double.POSITIVE_INFINITY);
        m.setRating(null);

        m.prePersistOrUpdate();

        assertEquals(0.0, m.getCloMarks(), 0.0001);
        assertEquals(0.0, m.getMarkValue(), 0.0001);
    }
}
