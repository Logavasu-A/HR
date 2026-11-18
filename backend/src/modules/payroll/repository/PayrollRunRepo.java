package modules.payroll.repository;

import modules.payroll.model.PayrollRun;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PayrollRunRepo extends JpaRepository<PayrollRun, Long> {
    Optional<PayrollRun> findByPeriodYearAndPeriodMonth(Integer year, Integer month);

    List<PayrollRun> findAllByOrderByPeriodYearDescPeriodMonthDesc();

    Page<PayrollRun> findAllByOrderByPeriodYearDescPeriodMonthDesc(Pageable pageable);
}
