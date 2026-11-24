package com.capstone.adproject.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import com.capstone.adproject.dto.ai.AiUsageReport;
import com.capstone.adproject.dto.ai.AiUsageReport.Totals;
import com.capstone.adproject.model.AiUsageEvent;
import com.capstone.adproject.model.AiUsageEvent.Feature;
import com.capstone.adproject.model.AiUsageEvent.Outcome;
import com.capstone.adproject.service.AiServiceClient;
import com.capstone.adproject.service.AiServiceClient.Amount;
import com.capstone.adproject.service.AiServiceClient.Balance;
import com.capstone.adproject.service.AiUsageService;
import com.capstone.adproject.service.AiUsageSettingsService;
import com.capstone.adproject.service.AlertingService;

/** The Super Admin "AI usage" page: its choices of period, the days-left sum, and how it reads in every state. */
class SuperAdminAiUsageControllerTest {

    private static AiUsageEvent event(String when, Outcome outcome, int calls, String cost, String error) {
        AiUsageEvent e = new AiUsageEvent();
        e.setOccurredAt(LocalDateTime.parse(when));
        e.setFeature(Feature.COMMENT_CHECK);
        e.setOutcome(outcome);
        e.setRequestedBy("admin1");
        e.setLlmCalls(calls);
        e.setPromptTokens(calls * 1000L);
        e.setCompletionTokens(calls * 200L);
        e.setCostUsd(cost == null ? null : new BigDecimal(cost));
        e.setLatencyMs(2100);
        e.setProvider("deepseek");
        e.setModel("deepseek-flash");
        e.setErrorKind(error);
        return e;
    }

    private static AiUsageReport report(List<AiUsageEvent> events, String burn, String budget, Integer budgetPercent) {
        Totals all = Totals.of(events);
        LocalDate day = LocalDate.parse("2026-11-23");
        var perDay = List.of(new AiUsageReport.Day(day.minusDays(1), Totals.NONE), new AiUsageReport.Day(day, all));
        var forecast = new AiUsageReport.Forecast(events.isEmpty() ? null : new BigDecimal("4.5"), burn == null ? null : new BigDecimal(burn),
                budget == null ? null : new BigDecimal(budget), budgetPercent);
        var latency = events.isEmpty() ? AiUsageReport.Latency.NONE : new AiUsageReport.Latency(events.size(), 2100, 2100, 2500, 3000);
        return new AiUsageReport(30, day.minusDays(29), day, all, all, all, forecast, latency, perDay,
                events.isEmpty() ? List.of() : List.of(new AiUsageReport.Row("COMMENT_CHECK", all)),
                events.isEmpty() ? List.of() : List.of(new AiUsageReport.Row("admin1", all)),
                events.isEmpty() ? List.of() : List.of(new AiUsageReport.Row("deepseek deepseek-flash", all)),
                events.stream().filter(e -> e.getErrorKind() != null).map(e -> new AiUsageReport.Failure(e.getErrorKind(), 1, e.getOccurredAt())).toList(),
                events);
    }

    private static Balance usd(double total, Boolean available) {
        return new Balance("deepseek", true, available, List.of(new Amount("USD", total, 2.0, total - 2.0)), null);
    }

    private static AiUsageSettingsService settings(String budget, String low) {
        return new AiUsageSettingsService(null, budget == null ? null : new BigDecimal(budget), low == null ? null : new BigDecimal(low));
    }

    private static String render(AiUsageReport report, Balance balance, BigDecimal lowBalanceAlert) {
        return render(report, balance, lowBalanceAlert == null ? null : lowBalanceAlert.toPlainString(), true);
    }

    private static String render(AiUsageReport report, Balance balance, String low, boolean channelConfigured) {
        AiUsageService service = mock(AiUsageService.class);
        AiServiceClient client = mock(AiServiceClient.class);
        AlertingService alerting = mock(AlertingService.class);
        when(alerting.isTelegramConfigured()).thenReturn(channelConfigured);
        when(service.report(anyInt())).thenReturn(report);
        when(client.providerBalance()).thenReturn(balance);
        var model = new ExtendedModelMap();
        new SuperAdminAiUsageController(service, client, settings("20", low), alerting).page(30, model);
        return html(new HashMap<>(model));
    }

    private static String html(Map<String, Object> model) {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        var servlet = new MockServletContext();
        var applicationContext = new StaticWebApplicationContext();
        applicationContext.setServletContext(servlet);
        applicationContext.refresh();
        servlet.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, applicationContext);
        var request = new MockHttpServletRequest(servlet);
        var response = new MockHttpServletResponse();
        var context = new WebContext(JakartaServletWebApplication.buildApplication(servlet).buildExchange(request, response));
        Map<String, Object> variables = new HashMap<>(model);
        variables.put("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "fixture"));
        context.setVariables(variables);
        return engine.process("superadmin_ai_usage", context);
    }

    // ---- The choice of period ----------------------------------------------------------------------------------------

    @Test
    void onlyTheOfferedPeriodsAreHonouredAnythingElseIsThirtyDays() {
        AiUsageService service = mock(AiUsageService.class);
        AiServiceClient client = mock(AiServiceClient.class);
        when(service.report(anyInt())).thenReturn(report(List.of(), null, null, null));
        when(client.providerBalance()).thenReturn(usd(1, true));
        var controller = new SuperAdminAiUsageController(service, client, settings(null, null), mock(AlertingService.class));

        for (int[] pair : new int[][] {{7, 7}, {90, 90}, {30, 30}, {0, 30}, {-1, 30}, {1000, 30}, {45, 30}}) {
            controller.page(pair[0], new ExtendedModelMap());
            verify(service, org.mockito.Mockito.atLeastOnce()).report(pair[1]);
        }
    }

    // ---- Days left ---------------------------------------------------------------------------------------------------

    @Test
    void daysLeftIsTheBalanceOverTheLastWeeksDailySpendRoundedDown() {
        var r = report(List.of(event("2026-11-23T08:00:00", Outcome.OK, 1, "0.1", null)), "0.25", null, null);
        assertEquals(40L, SuperAdminAiUsageController.runwayDays(usd(10.0, true), r));
    }

    @Test
    void daysLeftIsNotGuessedWhenItCannotBeWorkedOutHonestly() {
        var spending = report(List.of(event("2026-11-23T08:00:00", Outcome.OK, 1, "0.1", null)), "0.25", null, null);
        var noSpending = report(List.of(), "0", null, null);
        var unknownSpending = report(List.of(), null, null, null);
        assertNull(SuperAdminAiUsageController.runwayDays(usd(10.0, true), noSpending));       // would be infinite
        assertNull(SuperAdminAiUsageController.runwayDays(usd(10.0, true), unknownSpending));
        var yuan = new Balance("deepseek", true, true, List.of(new Amount("CNY", 100.0, 0.0, 100.0)), null);
        assertNull(SuperAdminAiUsageController.runwayDays(yuan, spending));                     // spend is in dollars: no rate to convert
        assertNull(SuperAdminAiUsageController.runwayDays(new Balance("gemini", false, null, List.of(), null), spending));
        assertNull(SuperAdminAiUsageController.runwayDays(new Balance("deepseek", true, null, List.of(), "HTTP 401"), spending));
        assertNull(SuperAdminAiUsageController.runwayDays(null, spending));
    }

    @Test
    void theBarsAreScaledToTheBusiestDayAndNeverDivideByZero() {
        assertEquals(1.0, SuperAdminAiUsageController.maxDayCost(report(List.of(), null, null, null)));
        var r = report(List.of(event("2026-11-23T08:00:00", Outcome.OK, 1, "0.75", null)), null, null, null);
        assertEquals(0.75, SuperAdminAiUsageController.maxDayCost(r));
        assertEquals(1, SuperAdminAiUsageController.activeDaysNewestFirst(r).size());     // the empty day is left out
    }

    // ---- The page in each state --------------------------------------------------------------------------------------

    @Test
    void aBusyPageShowsMoneyActivityBreakdownsAndTheBalance() {
        var events = List.of(event("2026-11-23T08:00:00", Outcome.OK, 1, "0.000212", null),
                event("2026-11-23T09:00:00", Outcome.UNAVAILABLE, 1, "0.000100", "HTTP402"));
        String html = render(report(events, "0.25", "20", 25), usd(12.5, true), new BigDecimal("5"));

        assertTrue(html.contains("Provider balance"));
        assertTrue(html.contains("12.50"));
        assertTrue(html.contains("Funds available"));
        assertTrue(html.contains("days left"));
        assertTrue(html.contains("25% of budget"));
        assertTrue(html.contains("Alert when it falls below"));
        assertTrue(html.contains("Comment check"), "feature shown by its readable name");
        assertTrue(html.contains("Model unavailable (fell back)"));
        assertTrue(html.contains("HTTP402"));
        assertTrue(html.contains("$0.0003"), "small amounts keep their decimals");
        assertTrue(html.contains("<progress"));
        assertTrue(html.contains("href=\"/superadmin/ai-usage?days=7\""));
        assertTrue(html.contains("aria-current=\"page\""));
        assertFalse(html.contains("No AI requests have been recorded"));
    }

    @Test
    void aPageWithNothingRecordedSaysSoInsteadOfShowingEmptyTables() {
        String html = render(report(List.of(), null, null, null), usd(5, true), null);

        assertTrue(html.contains("No AI requests have been recorded in this period"));
        assertTrue(html.contains("Nothing recorded yet."));
        assertFalse(html.contains("<progress"));
        assertFalse(html.contains("Where it goes"));
        assertTrue(html.contains("No monthly budget set"));
    }

    @Test
    void anOutOfFundsAccountIsSaidInWordsNotJustColour() {
        String html = render(report(List.of(), null, null, null), usd(0.0, false), null);
        assertTrue(html.contains("Out of funds"));
    }

    @Test
    void aBalanceThatCouldNotBeReadSaysWhyAndAProviderWithoutOneSaysThat() {
        String unreadable = render(report(List.of(), null, null, null),
                new Balance("deepseek", true, null, List.of(), "HTTP 401"), null);
        assertTrue(unreadable.contains("Could not read the balance"));
        assertTrue(unreadable.contains("HTTP 401"));

        String gemini = render(report(List.of(), null, null, null),
                new Balance("gemini", false, null, List.of(), null), null);
        assertTrue(gemini.contains("does not report a balance"));
    }

    @Test
    void aModelWithoutAPriceIsFlaggedSoTheAmountIsNotMistakenForComplete() {
        var events = List.of(event("2026-11-23T08:00:00", Outcome.OK, 1, null, null));
        String html = render(report(events, null, null, null), usd(5, true), null);
        assertTrue(html.contains("Not priced"));
        assertTrue(html.contains("AI_PRICES_JSON"));
    }

    @Test
    void textFromTheDatabaseIsEscapedNotTrustedAsHtml() {
        var events = List.of(event("2026-11-23T08:00:00", Outcome.OK, 1, "0.1", "<script>alert(1)</script>"));
        String html = render(report(events, null, null, null), usd(5, true), null);
        assertFalse(html.contains("<script>alert(1)</script>"));
        assertTrue(html.contains("&lt;script&gt;"));
    }

    // ---- Saving the alerts from the page -----------------------------------------------------------------------------

    private static org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap redirect() {
        return new org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap();
    }

    private static SuperAdminAiUsageController controllerWith(AiUsageSettingsService settings) {
        return new SuperAdminAiUsageController(mock(AiUsageService.class), mock(AiServiceClient.class), settings, mock(AlertingService.class));
    }

    @Test
    void savingTellsYouWhatIsNowInForceAndComesBackToTheSamePeriod() {
        AiUsageSettingsService settings = mock(AiUsageSettingsService.class);
        when(settings.save("25", "", "Super Admin")).thenReturn(new AiUsageSettingsService.View(new BigDecimal("25.00"), null, true, null, null));
        var flash = redirect();

        String view = controllerWith(settings).saveSettings("25", "", 7, flash);

        assertEquals("redirect:/superadmin/ai-usage?days=7", view);
        String message = (String) flash.getFlashAttributes().get("successMessage");
        assertTrue(message.contains("$25.00"), message);
        assertTrue(message.contains("Low-balance alert: off"), message);
        assertFalse(flash.getFlashAttributes().containsKey("errorMessage"));
    }

    @Test
    void aRefusedValueIsExplainedAndNothingIsClaimedSaved() {
        AiUsageSettingsService settings = mock(AiUsageSettingsService.class);
        when(settings.save("abc", "", "Super Admin")).thenThrow(new IllegalArgumentException("The monthly budget must be a number."));
        var flash = redirect();

        String view = controllerWith(settings).saveSettings("abc", "", 90, flash);

        assertEquals("redirect:/superadmin/ai-usage?days=90", view);
        assertEquals("The monthly budget must be a number.", flash.getFlashAttributes().get("errorMessage"));
        assertFalse(flash.getFlashAttributes().containsKey("successMessage"));
    }

    @Test
    void anUnknownPeriodOnTheFormFallsBackToThirtyDaysSoTheRedirectCannotBeSteered() {
        AiUsageSettingsService settings = mock(AiUsageSettingsService.class);
        when(settings.save(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AiUsageSettingsService.View(null, null, true, null, null));

        assertEquals("redirect:/superadmin/ai-usage?days=30", controllerWith(settings).saveSettings("", "", 12345, redirect()));
        assertEquals("redirect:/superadmin/ai-usage?days=30", controllerWith(settings).saveSettings("", "", -1, redirect()));
    }

    @Test
    void theFormShowsLabelledFieldsWithHintsAndWhereTheValuesCameFrom() {
        String html = render(report(List.of(), null, null, null), usd(5, true), "5", true);

        assertTrue(html.contains("Spending alerts"));
        assertTrue(html.contains("<label for=\"aiuBudget\">Monthly budget (US dollars)</label>"));
        assertTrue(html.contains("name=\"monthlyBudget\""));
        assertTrue(html.contains("name=\"lowBalance\""));
        assertTrue(html.contains("value=\"20\""), "the budget in force is filled in");
        assertTrue(html.contains("(<span>USD</span>)"), "the low-balance label names the account's currency");
        assertTrue(html.contains("Save alerts"));
        assertTrue(html.contains("Showing the server's default settings"));
        assertTrue(html.contains("name=\"days\""));
        assertFalse(html.contains("No channel"));
    }

    @Test
    void whenNoAlertChannelIsConfiguredTheFormSaysAWarningCouldNotBeDelivered() {
        String html = render(report(List.of(), null, null, null), usd(5, true), null, false);
        assertTrue(html.contains("No channel"));
        assertTrue(html.contains("could not be delivered"));
    }
}
