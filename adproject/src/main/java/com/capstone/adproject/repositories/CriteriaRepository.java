package com.capstone.adproject.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.capstone.adproject.model.Criteria;

// No need for @Repository annotation, JpaRepository handles it
@Repository
public interface CriteriaRepository extends JpaRepository<Criteria, Long> {
}