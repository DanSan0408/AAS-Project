package com.capstone.adproject.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.capstone.adproject.model.AiUsageSettings;

public interface AiUsageSettingsRepository extends JpaRepository<AiUsageSettings, Long> {
}
