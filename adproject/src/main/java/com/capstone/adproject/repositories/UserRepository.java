package com.capstone.adproject.repositories;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.NoRepositoryBean;

// This annotation means Spring Data JPA should not create an instance for this interface
@NoRepositoryBean 
public interface UserRepository<T> extends CrudRepository<T, Long> {
    T findByEmail(String email);
    T findByResetPasswordToken(String token);
}