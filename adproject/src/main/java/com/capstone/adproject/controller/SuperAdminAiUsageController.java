package com.capstone.adproject.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.capstone.adproject.dto.ai.AiUsageReport;
import com.capstone.adproject.service.AiServiceClient;
import com.capstone.adproject.service.AiUsageService;
import com.capstone.adproject.service.AiUsageSettingsService;
import com.capstone.adproject.service.AlertingService;

/** Super Admin page: what the AI is costing and doing. Spend, tokens, speed, failures and the provider balance. */
@Controller
@RequestMapping("/superadmin/ai-usage")
public class SuperAdminAiUsageController {

    static final List<Integer> RANGES = List.of(7, 30, 90);
    static final int DEFAULT_DAYS = 30;
    static final Map<String, String> FEATURE_NAMES = Map.of("TEAM_AUDIT", "Team dynamics audit",
            "MODERATION_BRIEFING", "Moderation briefing", "COMMENT_CHECK", "Comment check");
    static final Map<String, String> OUTCOME_NAMES = Map.of("OK", "Answered", "PARTIAL", "Partly answered",
            "UNAVAILABLE", "Model unavailable (fell back)", "SERVICE_ERROR", "AI service error",
            "CACHE_HIT", "Repeat, answered from memory", "RATE_LIMITED", "Refused: too many requests");

    private final AiUsageService usageService;
    private final AiServiceClient aiClient;
    private final AiUsageSettingsService settings;
    private final AlertingService alerting;

    public SuperAdminAiUsageController(AiUsageService usageService, AiServiceClient aiClient, AiUsageSettingsService settings,
                                       AlertingService alerting) {
        this.usageService = usageService;
        this.aiClient = aiClient;
        this.settings = settings;
        this.alerting = alerting;
    }

    @GetMapping
    public String page(@RequestParam(name = "days", defaultValue = "30") int days, Model model) {
        int range = RANGES.contains(days) ? days : DEFAULT_DAYS;
        AiUsageReport report = usageService.report(range);
        AiServiceClient.Balance balance = aiClient.providerBalance();
        model.addAttribute("report", report);
        model.addAttribute("ranges", RANGES);
        model.addAttribute("balance", balance);
        model.addAttribute("firstBalance", balance.balances() == null || balance.balances().isEmpty() ? null : balance.balances().get(0));
        model.addAttribute("runwayDays", runwayDays(balance, report));
        model.addAttribute("settings", settings.view());
        model.addAttribute("balanceCurrency", balance.balances() == null || balance.balances().isEmpty() ? null : balance.balances().get(0).currency());
        model.addAttribute("alertChannelConfigured", alerting.isTelegramConfigured() || alerting.isEmailAlertConfigured());
        model.addAttribute("featureNames", FEATURE_NAMES);
        model.addAttribute("outcomeNames", OUTCOME_NAMES);
        List<AiUsageReport.Day> activeDays = activeDaysNewestFirst(report);
        model.addAttribute("activeDays", activeDays);
        model.addAttribute("quietDays", report.perDay().size() - activeDays.size());
        model.addAttribute("maxDayCost", maxDayCost(report));
        model.addAttribute("maxDayRequests", report.perDay().stream().mapToInt(d -> d.totals().requests()).max().orElse(0));
        model.addAttribute("superAdminUsername", username());
        return "superadmin_ai_usage";
    }

    /** Saves the budget and low-balance alert typed on the page; a refused value is explained and nothing changes. */
    @PostMapping("/settings")
    public String saveSettings(@RequestParam(name = "monthlyBudget", required = false) String monthlyBudget,
                               @RequestParam(name = "lowBalance", required = false) String lowBalance,
                               @RequestParam(name = "days", defaultValue = "30") int days,
                               RedirectAttributes redirectAttributes) {
        try {
            AiUsageSettingsService.View saved = settings.save(monthlyBudget, lowBalance, username());
            redirectAttributes.addFlashAttribute("successMessage", "Spending alerts saved. Monthly budget: "
                    + (saved.monthlyBudgetUsd() == null ? "off" : AiUsageReport.usd(saved.monthlyBudgetUsd()))
                    + ". Low-balance alert: " + (saved.lowBalanceAlert() == null ? "off" : saved.lowBalanceAlert().toPlainString()) + ".");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/superadmin/ai-usage?days=" + (RANGES.contains(days) ? days : DEFAULT_DAYS);
    }

    /**
     * How many days the balance lasts at the last week's spend. Only worked out when the balance is in dollars, because the spend is
     * an estimate in dollars and a different currency cannot be compared without a rate.
     */
    static Long runwayDays(AiServiceClient.Balance balance, AiUsageReport report) {
        if (balance == null || balance.balances() == null || balance.balances().isEmpty()) return null;
        AiServiceClient.Amount first = balance.balances().get(0);
        BigDecimal burn = report.forecast().dailyBurnUsd();
        if (first.total() == null || !"USD".equalsIgnoreCase(first.currency()) || burn == null || burn.signum() <= 0) return null;
        return BigDecimal.valueOf(first.total()).divide(burn, 0, RoundingMode.DOWN).longValue();
    }

    /** Days with any AI activity, newest first: a 90-day table of mostly empty days would hide the days that matter. */
    static List<AiUsageReport.Day> activeDaysNewestFirst(AiUsageReport report) {
        List<AiUsageReport.Day> days = new ArrayList<>(report.perDay().stream().filter(d -> d.totals().requests() > 0).toList());
        Collections.reverse(days);
        return days;
    }

    /** The largest single-day spend, as the full scale of the daily bars (1 when nothing was spent, so a bar is never divided by zero). */
    static double maxDayCost(AiUsageReport report) {
        double max = report.perDay().stream().mapToDouble(d -> d.totals().costUsd().doubleValue()).max().orElse(0);
        return max > 0 ? max : 1;
    }

    private static String username() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "Super Admin";
    }
}
