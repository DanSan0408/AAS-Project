package com.capstone.adproject.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.capstone.adproject.model.AiUsageSettings;
import com.capstone.adproject.repositories.AiUsageSettingsRepository;

/** The AI spending alerts set on the page: what is in force, and what is refused instead of silently switching an alert off. */
class AiUsageSettingsServiceTest {

    private AiUsageSettingsRepository repo;

    @BeforeEach
    void setUp() {
        repo = mock(AiUsageSettingsRepository.class);
        when(repo.findById(AiUsageSettings.ID)).thenReturn(Optional.empty());
    }

    private AiUsageSettingsService service(String defaultBudget, String defaultLow) {
        var s = new AiUsageSettingsService(repo, defaultBudget == null ? null : new BigDecimal(defaultBudget),
                defaultLow == null ? null : new BigDecimal(defaultLow));
        s.clock = Clock.fixed(Instant.parse("2026-11-23T12:00:00Z"), ZoneOffset.UTC);
        return s;
    }

    private static AiUsageSettings row(String budget, String low) {
        var r = new AiUsageSettings();
        r.setMonthlyBudgetUsd(budget == null ? null : new BigDecimal(budget));
        r.setLowBalanceAlert(low == null ? null : new BigDecimal(low));
        r.setUpdatedAt(LocalDateTime.parse("2026-11-20T08:00:00"));
        r.setUpdatedBy("super1");
        return r;
    }

    // ---- What is in force ----------------------------------------------------------------------------------------

    @Test
    void untilSomeoneSavesTheServerDefaultsApply() {
        var view = service("20", "5").view();
        assertEquals(0, new BigDecimal("20").compareTo(view.monthlyBudgetUsd()));
        assertEquals(0, new BigDecimal("5").compareTo(view.lowBalanceAlert()));
        assertFalse(view.savedInApp());
        assertNull(view.updatedBy());
    }

    @Test
    void aDefaultOfZeroOrNothingMeansOff() {
        var view = service("0", null).view();
        assertNull(view.monthlyBudgetUsd());
        assertNull(view.lowBalanceAlert());
        assertNull(service("-5", "-1").monthlyBudgetUsd());
    }

    @Test
    void aSavedValueWinsOverTheDefaultAndASavedEmptyStaysOffInsteadOfFallingBack() {
        when(repo.findById(AiUsageSettings.ID)).thenReturn(Optional.of(row("40", null)));
        var s = service("20", "5");
        assertEquals(0, new BigDecimal("40").compareTo(s.monthlyBudgetUsd()));
        assertNull(s.lowBalanceAlert());                         // saved as off; the default 5 must not come back
        assertTrue(s.view().savedInApp());
        assertEquals("super1", s.view().updatedBy());
    }

    @Test
    void aServiceWithoutADatabaseSimplyUsesTheDefaults() {
        var s = new AiUsageSettingsService(null, new BigDecimal("7"), null);
        assertEquals(0, new BigDecimal("7").compareTo(s.monthlyBudgetUsd()));
    }

    // ---- Saving ------------------------------------------------------------------------------------------------------

    @Test
    void valuesAreSavedToTwoDecimalsWithWhoChangedThemAndWhen() {
        var view = service(null, null).save(" 20 ", "7.505", "super1");

        var saved = ArgumentCaptor.forClass(AiUsageSettings.class);
        verify(repo).save(saved.capture());
        assertEquals(AiUsageSettings.ID, saved.getValue().getId());
        assertEquals(new BigDecimal("20.00"), saved.getValue().getMonthlyBudgetUsd());
        assertEquals(new BigDecimal("7.51"), saved.getValue().getLowBalanceAlert());
        assertEquals("super1", saved.getValue().getUpdatedBy());
        assertEquals(LocalDateTime.parse("2026-11-23T12:00:00"), saved.getValue().getUpdatedAt());
        assertTrue(view.savedInApp());
    }

    @Test
    void emptyOrZeroTurnsThatAlertOffAndTheOtherIsKept() {
        service("20", "5").save("", "0", "super1");

        var saved = ArgumentCaptor.forClass(AiUsageSettings.class);
        verify(repo).save(saved.capture());
        assertNull(saved.getValue().getMonthlyBudgetUsd());
        assertNull(saved.getValue().getLowBalanceAlert());
        assertNull(service(null, null).save(null, "10", "super1").monthlyBudgetUsd());
    }

    @Test
    void anExistingRowIsUpdatedNotDuplicated() {
        var existing = row("40", "5");
        when(repo.findById(AiUsageSettings.ID)).thenReturn(Optional.of(existing));

        service(null, null).save("50", "6", "super2");

        verify(repo).save(existing);
        assertEquals(new BigDecimal("50.00"), existing.getMonthlyBudgetUsd());
        assertEquals("super2", existing.getUpdatedBy());
    }

    @Test
    void aBlankOrVeryLongNameIsStillRecorded() {
        var saved = ArgumentCaptor.forClass(AiUsageSettings.class);
        service(null, null).save("1", "1", " ");
        service(null, null).save("1", "1", "n".repeat(300));
        verify(repo, org.mockito.Mockito.times(2)).save(saved.capture());
        assertEquals("unknown", saved.getAllValues().get(0).getUpdatedBy());
        assertEquals(100, saved.getAllValues().get(1).getUpdatedBy().length());
    }

    @Test
    void anythingThatIsNotAReasonableAmountIsRefusedWithAClearMessageAndNothingIsChanged() {
        for (String bad : new String[] {"abc", "NaN", "Infinity", "1,5", "$20", "--3"}) {
            var e = assertThrows(IllegalArgumentException.class, () -> service(null, null).save(bad, "", "super1"), bad);
            assertTrue(e.getMessage().contains("must be a number"), bad);
        }
        assertTrue(assertThrows(IllegalArgumentException.class, () -> service(null, null).save("-1", "", "u")).getMessage().contains("cannot be negative"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> service(null, null).save("1000000.01", "", "u")).getMessage().contains("1,000,000"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> service(null, null).save("0.001", "", "u")).getMessage().contains("at least 0.01"));
        verify(repo, never()).save(any());
    }

    @Test
    void aBadLowBalanceNamesTheLowBalanceNotTheBudget() {
        var e = assertThrows(IllegalArgumentException.class, () -> service(null, null).save("20", "lots", "u"));
        assertTrue(e.getMessage().startsWith("The low-balance alert"));
        var e2 = assertThrows(IllegalArgumentException.class, () -> service(null, null).save("lots", "5", "u"));
        assertTrue(e2.getMessage().startsWith("The monthly budget"));
    }

    @Test
    void theLargestAllowedValueIsAccepted() {
        assertEquals(new BigDecimal("1000000.00"), service(null, null).save("1000000", "", "u").monthlyBudgetUsd());
    }
}
