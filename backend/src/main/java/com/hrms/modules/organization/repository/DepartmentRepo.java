package com.hrms.modules.organization.repository;

import com.hrms.modules.organization.model.Department;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DepartmentRepo extends JpaRepository<Department, Long> {
}
