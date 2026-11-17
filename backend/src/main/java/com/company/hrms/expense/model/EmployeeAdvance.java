package com.company.hrms.expense.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.company.hrms.common.BaseEntity;
import com.company.hrms.common.ApprovalStatus;
import com.company.hrms.employee.model.Employee;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "employee_advance")
@Getter
@Setter
public class EmployeeAdvance extends BaseEntity {

    @NotNull
    @ManyToOne
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @NotNull
    private LocalDate advanceDate;

    @NotNull
    private BigDecimal amount;

    private String purpose;

    @Enumerated(EnumType.STRING)
    private ApprovalStatus status = ApprovalStatus.PENDING;
}
