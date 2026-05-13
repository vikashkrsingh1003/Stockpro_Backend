package com.stockpro.authservice.repository;

import com.stockpro.authservice.entities.User;
import com.stockpro.authservice.entities.Role;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUserId(String userId);

    boolean existsByEmail(String email);

    List<User> findAllByRole(Role role);

    List<User> findByDepartment(String department);

    List<User> findByActive(boolean active);

    @Query("select u.email from User u where u.active = true and u.email is not null and u.email <> ''")
    List<String> findActiveUserEmails();

    void deleteByUserId(String userId);
}
