package com.company.hrms.modules.expense.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.company.hrms.modules.expense.entity.ExpenseType;

@Repository
public interface ExpenseTypeRepo extends JpaRepository<ExpenseType, Long> {
}
