package com.company.hrms.leave.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.company.hrms.common.BaseEntity;
import com.company.hrms.employee.model.Employee;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "leave_allocation")
@Getter
@Setter
public class LeaveAllocation extends BaseEntity {

    @NotNull
    @ManyToOne
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "leave_type_id")
    private LeaveType leaveType;

    @NotNull
    private LocalDate fromDate;

    @NotNull
    private LocalDate toDate;

    @NotNull
    private BigDecimal days;
}
