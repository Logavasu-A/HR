package com.company.hrms.payroll.repository;

import com.company.hrms.payroll.model.SalaryComponent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface SalaryComponentRepo extends JpaRepository<SalaryComponent, Long> {
}
