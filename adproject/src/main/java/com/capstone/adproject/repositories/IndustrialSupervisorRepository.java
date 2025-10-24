package com.capstone.adproject.repositories;

import com.capstone.adproject.model.IndustrialSupervisor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IndustrialSupervisorRepository extends JpaRepository<IndustrialSupervisor, Long> {
    Optional<IndustrialSupervisor> findByUsername(String username);
}
