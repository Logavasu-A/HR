package com.hrms.modules.admin.repository;

import com.hrms.modules.admin.model.AppUser;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepo extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findFirstByEmployeeId(Long employeeId);
}
