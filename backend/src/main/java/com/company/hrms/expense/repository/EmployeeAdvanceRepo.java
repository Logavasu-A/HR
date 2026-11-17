package com.company.hrms.modules.expense.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.company.hrms.modules.expense.entity.EmployeeAdvance;

@Repository
public interface EmployeeAdvanceRepo extends JpaRepository<EmployeeAdvance, Long> {
}
