package com.capstone.adproject.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.capstone.adproject.model.Rating;
@Repository
public interface RatingRepository extends JpaRepository<Rating, Long> {
}
