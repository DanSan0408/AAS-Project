package com.capstone.adproject.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The AI spending alerts a Super Admin set on the AI usage page: one row, id 1. A row's existence means "saved in the app", so
 * a saved empty value stays off instead of falling back to the server's default. Times are UTC.
 */
@Entity
@Table(name = "ai_usage_settings")
public class AiUsageSettings {

    public static final long ID = 1L;

    @Id
    private Long id = ID;

    /** US dollars per calendar month; null = no budget alert. */
    @Column(precision = 12, scale = 2)
    private BigDecimal monthlyBudgetUsd;

    /** In the provider account's own currency; null = no low-balance alert. */
    @Column(precision = 12, scale = 2)
    private BigDecimal lowBalanceAlert;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(length = 100)
    private String updatedBy;

    public Long getId() { return id; }
    public BigDecimal getMonthlyBudgetUsd() { return monthlyBudgetUsd; }
    public void setMonthlyBudgetUsd(BigDecimal monthlyBudgetUsd) { this.monthlyBudgetUsd = monthlyBudgetUsd; }
    public BigDecimal getLowBalanceAlert() { return lowBalanceAlert; }
    public void setLowBalanceAlert(BigDecimal lowBalanceAlert) { this.lowBalanceAlert = lowBalanceAlert; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
