package com.company.hrms.modules.payroll.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.company.hrms.modules.payroll.entity.PayrollRun;
import com.company.hrms.modules.payroll.entity.SalarySlip;

@Repository
public interface SalarySlipRepo extends JpaRepository<SalarySlip, Long> {
    List<SalarySlip> findByRunIdOrderByEmployeeEmployeeCode(Long runId);

    List<SalarySlip> findByEmployeeIdAndStatusOrderByPeriodYearDescPeriodMonthDesc(
            Long employeeId, PayrollRun.Status status);
}
