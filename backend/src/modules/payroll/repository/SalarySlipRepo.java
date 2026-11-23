package modules.payroll.repository;

import modules.payroll.model.PayrollRun;
import modules.payroll.model.SalarySlip;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SalarySlipRepo extends JpaRepository<SalarySlip, Long> {
    List<SalarySlip> findByRunIdOrderByEmployeeEmployeeCode(Long runId);

    Page<SalarySlip> findByRunIdOrderByEmployeeEmployeeCode(Long runId, Pageable pageable);

    List<SalarySlip> findByEmployeeIdAndStatusOrderByPeriodYearDescPeriodMonthDesc(
            Long employeeId, PayrollRun.Status status);

    Page<SalarySlip> findByEmployeeIdAndStatusOrderByPeriodYearDescPeriodMonthDesc(
            Long employeeId, PayrollRun.Status status, Pageable pageable);
}
