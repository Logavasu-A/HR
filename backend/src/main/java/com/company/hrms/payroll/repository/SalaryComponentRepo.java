package com.company.hrms.modules.payroll.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.company.hrms.modules.payroll.entity.SalaryComponent;

@Repository
public interface SalaryComponentRepo extends JpaRepository<SalaryComponent, Long> {
}
