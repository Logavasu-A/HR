package com.company.hrms.expense.repository;

import com.company.hrms.expense.model.ExpenseType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface ExpenseTypeRepo extends JpaRepository<ExpenseType, Long> {
}
