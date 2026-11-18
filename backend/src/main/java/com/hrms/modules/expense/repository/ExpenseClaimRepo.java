package com.hrms.modules.expense.repository;

import com.hrms.modules.expense.model.ExpenseClaim;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.hrms.shared.common.ApprovalStatus;

@Repository
public interface ExpenseClaimRepo extends JpaRepository<ExpenseClaim, Long> {
    List<ExpenseClaim> findByEmployeeIdOrderByClaimDateDesc(Long employeeId);

    Page<ExpenseClaim> findByEmployeeIdOrderByClaimDateDesc(Long employeeId, Pageable pageable);

    long countByStatus(ApprovalStatus status);
}
