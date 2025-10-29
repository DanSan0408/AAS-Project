package com.capstone.adproject.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Student;

public interface StudentRepository extends JpaRepository<Student, Long> {
    
    Optional<Student> findByUsername(String username);

    // New method to find students not assigned to a group (group is NULL)
    List<Student> findByGroupIsNull();

    @Query("SELECT s FROM Student s LEFT JOIN FETCH s.group")
    List<Student> findAllWithGroupEagerly();

    List<Student> findByGroupId(Long groupId);

    List<Student> findByGroup(Group group);

    long countByGroup(Group group);

    List<Student> findByGroupIsNullAndUsernameContainingIgnoreCase(String username);

    long countByGroupIsNull();

    Optional<Student> findByEmail(String email);

    // **New Method for Forgot Password - Find by Token**
    Optional<Student> findByResetPasswordToken(String resetPasswordToken);
}