package com.company.hrms.organization.repository;

import com.company.hrms.organization.model.Department;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface DepartmentRepo extends JpaRepository<Department, Long> {
}
