package com.capstone.adproject.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.capstone.adproject.model.Rubric;
@Repository
public interface RubricRepository extends JpaRepository<Rubric, Long> {
}
