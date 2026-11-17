package com.company.hrms.expense.repository;

import com.company.hrms.expense.model.ExpenseClaim;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.company.hrms.common.ApprovalStatus;

@Repository
public interface ExpenseClaimRepo extends JpaRepository<ExpenseClaim, Long> {
    List<ExpenseClaim> findByEmployeeIdOrderByClaimDateDesc(Long employeeId);

    long countByStatus(ApprovalStatus status);
}
