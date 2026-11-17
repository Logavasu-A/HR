package modules.expense.repository;

import modules.expense.model.ExpenseClaim;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import shared.common.ApprovalStatus;

@Repository
public interface ExpenseClaimRepo extends JpaRepository<ExpenseClaim, Long> {
    List<ExpenseClaim> findByEmployeeIdOrderByClaimDateDesc(Long employeeId);

    long countByStatus(ApprovalStatus status);
}
