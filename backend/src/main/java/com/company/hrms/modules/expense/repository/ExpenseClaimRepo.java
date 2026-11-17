package com.company.hrms.modules.expense.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.company.hrms.common.enums.ApprovalStatus;
import com.company.hrms.modules.expense.entity.ExpenseClaim;

@Repository
public interface ExpenseClaimRepo extends JpaRepository<ExpenseClaim, Long> {
    List<ExpenseClaim> findByEmployeeIdOrderByClaimDateDesc(Long employeeId);

    long countByStatus(ApprovalStatus status);
}
