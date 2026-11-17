package com.company.hrms.payroll.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.company.hrms.common.BaseEntity;
import com.company.hrms.employee.model.Employee;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Gives an employee a salary structure and a monthly base salary from a date onward. */
@Entity
@Table(name = "salary_assignment")
@Getter
@Setter
public class SalaryAssignment extends BaseEntity {

    @NotNull
    @ManyToOne
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "structure_id")
    @JsonIgnoreProperties({"lines"})
    private SalaryStructure structure;

    @NotNull
    private BigDecimal baseAmount;

    @NotNull
    private LocalDate fromDate;
}
