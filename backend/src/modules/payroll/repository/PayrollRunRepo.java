package modules.payroll.repository;

import modules.payroll.model.PayrollRun;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

@Repository
public interface PayrollRunRepo extends JpaRepository<PayrollRun, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PayrollRun> findByPeriodYearAndPeriodMonth(Integer year, Integer month);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PayrollRun> findOneById(Long id);

    List<PayrollRun> findAllByOrderByPeriodYearDescPeriodMonthDesc();

    Page<PayrollRun> findAllByOrderByPeriodYearDescPeriodMonthDesc(Pageable pageable);
}
