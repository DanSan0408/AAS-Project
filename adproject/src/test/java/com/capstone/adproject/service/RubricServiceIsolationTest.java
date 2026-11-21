package com.capstone.adproject.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.capstone.adproject.model.Rating;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.SubRubric;
import com.capstone.adproject.repositories.AssessmentRepository;
import com.capstone.adproject.repositories.MarkRepository;
import com.capstone.adproject.repositories.RubricRepository;
import com.capstone.adproject.repositories.SubRubricRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A rubric form may only change the sub-rubrics and ratings that already belong to that rubric. */
class RubricServiceIsolationTest {

    private final RubricRepository rubricRepository = mock(RubricRepository.class);
    private final RubricService service = new RubricService(
            mock(AssessmentRepository.class), rubricRepository, mock(SubRubricRepository.class), mock(MarkRepository.class),
            mock(jakarta.persistence.EntityManager.class), mock(org.springframework.jdbc.core.JdbcTemplate.class));

    private static Rating rating(Long id, String name, String marks) {
        Rating r = new Rating();
        r.setId(id);
        r.setName(name);
        r.setMarks(new BigDecimal(marks));
        return r;
    }

    private Rubric ownRubric() {
        Rubric own = new Rubric();
        own.setId(5L);
        own.setName("Mine");
        SubRubric sub = new SubRubric();
        sub.setId(50L);
        sub.setName("Clarity");
        sub.setRubric(own);
        sub.setRatings(new ArrayList<>(List.of(rating(500L, "Good", "5"))));
        own.setSubRubrics(new ArrayList<>(List.of(sub)));
        own.setRatings(new ArrayList<>(List.of(rating(600L, "Direct", "3"))));
        when(rubricRepository.findById(5L)).thenReturn(Optional.of(own));
        when(rubricRepository.save(any(Rubric.class))).thenAnswer(inv -> inv.getArgument(0));
        return own;
    }

    @Test
    void aSubRubricIdThatIsNotThisRubricsOwnIsTreatedAsNewNotAsSomeoneElsesRow() {
        Rubric own = ownRubric();
        Rubric form = new Rubric();
        form.setId(5L);
        form.setName("Mine");
        SubRubric foreign = new SubRubric();
        foreign.setId(9999L);            // belongs to a rubric in another course
        foreign.setName("Injected");
        foreign.setRatings(new ArrayList<>(List.of(rating(8888L, "Stolen", "9"))));
        form.setSubRubrics(new ArrayList<>(List.of(foreign)));
        form.setRatings(new ArrayList<>());

        service.saveRubric(form);

        ArgumentCaptor<Rubric> saved = ArgumentCaptor.forClass(Rubric.class);
        verify(rubricRepository).save(saved.capture());
        SubRubric result = saved.getValue().getSubRubrics().get(0);
        assertNull(result.getId(), "a foreign sub-rubric id must not survive");
        assertEquals("Injected", result.getName());
        assertNull(result.getRatings().get(0).getId(), "a foreign rating id must not survive");
        assertEquals(own, result.getRubric());
    }

    @Test
    void aDirectRatingIdThatIsNotThisRubricsOwnIsTreatedAsNew() {
        ownRubric();
        Rubric form = new Rubric();
        form.setId(5L);
        form.setName("Mine");
        form.setSubRubrics(new ArrayList<>());
        form.setRatings(new ArrayList<>(List.of(rating(7777L, "Foreign", "4"))));

        service.saveRubric(form);

        ArgumentCaptor<Rubric> saved = ArgumentCaptor.forClass(Rubric.class);
        verify(rubricRepository).save(saved.capture());
        assertNull(saved.getValue().getRatings().get(0).getId());
    }

    @Test
    void anIdThatIsThisRubricsOwnStillUpdatesInPlace() {
        Rubric own = ownRubric();
        Rubric form = new Rubric();
        form.setId(5L);
        form.setName("Mine");
        SubRubric same = new SubRubric();
        same.setId(50L);
        same.setName("Clarity (renamed)");
        same.setRatings(new ArrayList<>(List.of(rating(500L, "Very good", "6"))));
        form.setSubRubrics(new ArrayList<>(List.of(same)));
        form.setRatings(new ArrayList<>(List.of(rating(600L, "Direct", "3"))));

        service.saveRubric(form);

        SubRubric kept = own.getSubRubrics().get(0);
        assertEquals(50L, kept.getId());
        assertEquals("Clarity (renamed)", kept.getName());
        assertEquals(500L, kept.getRatings().get(0).getId());
        assertEquals("Very good", kept.getRatings().get(0).getName());
        assertEquals(600L, own.getRatings().get(0).getId());
    }
}
