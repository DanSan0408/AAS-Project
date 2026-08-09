package com.capstone.adproject.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.capstone.adproject.model.Course;
import com.capstone.adproject.model.FactorWeightage;

@Repository
public interface FactorWeightageRepository extends JpaRepository<FactorWeightage, Long> {
    List<FactorWeightage> findByCourse(Course course);
    void deleteByCourse(Course course);
}
