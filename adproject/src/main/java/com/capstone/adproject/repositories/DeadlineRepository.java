package com.capstone.adproject.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

import com.capstone.adproject.model.Deadline;

@Repository
public interface DeadlineRepository extends JpaRepository<Deadline, Long> {

   List<Deadline> findByTitleIgnoreCase(String title);
}