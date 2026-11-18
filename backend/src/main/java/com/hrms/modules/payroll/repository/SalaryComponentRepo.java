package com.hrms.modules.payroll.repository;

import com.hrms.modules.payroll.model.SalaryComponent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SalaryComponentRepo extends JpaRepository<SalaryComponent, Long> {
}
