package com.company.hrms.expense.repository;

import com.company.hrms.expense.model.EmployeeAdvance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface EmployeeAdvanceRepo extends JpaRepository<EmployeeAdvance, Long> {
}
