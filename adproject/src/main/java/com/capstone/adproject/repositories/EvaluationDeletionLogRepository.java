package com.capstone.adproject.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.capstone.adproject.model.EvaluationDeletionLog;

@Repository
public interface EvaluationDeletionLogRepository extends JpaRepository<EvaluationDeletionLog, Long> {

    List<EvaluationDeletionLog> findTop20ByAssessmentIdOrderByDeletedAtDesc(Long assessmentId);
}
