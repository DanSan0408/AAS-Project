package com.capstone.adproject.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.capstone.adproject.model.Rating;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** One rating step: the smallest gap between two rating levels, so assessor differences can be judged the same on any scale. */
class CriterionStepTest {

    private static List<Rating> levels(double... marks) {
        List<Rating> ratings = new ArrayList<>();
        for (double m : marks) {
            Rating r = new Rating();
            r.setMarks(BigDecimal.valueOf(m));
            ratings.add(r);
        }
        return ratings;
    }

    @Test
    void aOneToFiveScaleHasAStepOfOne() {
        assertEquals(1.0, LecturerAnalyticsService.step(levels(1, 2, 3, 4, 5)), 0.0001);
    }

    @Test
    void levelsInAnyOrderAndWithRepeatsGiveTheSmallestGap() {
        assertEquals(5.0, LecturerAnalyticsService.step(levels(10, 0, 5, 5, 15)), 0.0001);
        assertEquals(0.5, LecturerAnalyticsService.step(levels(0, 0.5, 2, 4)), 0.0001);
    }

    @Test
    void fewerThanTwoDifferentLevelsHasNoStep() {
        assertEquals(0.0, LecturerAnalyticsService.step(levels(3)), 0.0001);
        assertEquals(0.0, LecturerAnalyticsService.step(levels(3, 3)), 0.0001);
        assertEquals(0.0, LecturerAnalyticsService.step(null), 0.0001);
        assertEquals(0.0, LecturerAnalyticsService.step(new ArrayList<>()), 0.0001);
    }
}
