package com.capstone.adproject.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.capstone.adproject.model.Group;

public interface GroupRepository extends JpaRepository<Group, Long> {
}