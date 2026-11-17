package com.company.hrms.modules.payroll.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.company.hrms.modules.payroll.entity.SalaryAssignment;

@Repository
public interface SalaryAssignmentRepo extends JpaRepository<SalaryAssignment, Long> {
    List<SalaryAssignment> findByEmployeeIdOrderByFromDateDesc(Long employeeId);
}
