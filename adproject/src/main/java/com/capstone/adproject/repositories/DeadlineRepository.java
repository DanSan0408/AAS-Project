package com.capstone.adproject.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.capstone.adproject.model.Deadline;

@Repository
public interface DeadlineRepository extends JpaRepository<Deadline, Long> {
}