package com.company.hrms.payroll.repository;

import com.company.hrms.payroll.model.SalaryAssignment;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface SalaryAssignmentRepo extends JpaRepository<SalaryAssignment, Long> {
    List<SalaryAssignment> findByEmployeeIdOrderByFromDateDesc(Long employeeId);
}
