package com.company.hrms.modules.payroll.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.company.hrms.modules.payroll.entity.PayrollRun;

@Repository
public interface PayrollRunRepo extends JpaRepository<PayrollRun, Long> {
    Optional<PayrollRun> findByPeriodYearAndPeriodMonth(Integer year, Integer month);

    List<PayrollRun> findAllByOrderByPeriodYearDescPeriodMonthDesc();
}
