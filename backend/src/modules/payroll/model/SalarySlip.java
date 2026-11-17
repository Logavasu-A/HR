package com.company.hrms.payroll.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.company.hrms.common.BaseEntity;
import com.company.hrms.employee.model.Employee;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "salary_slip")
@Getter
@Setter
public class SalarySlip extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "run_id")
    @JsonIgnoreProperties({"skipped"})
    private PayrollRun run;

    @ManyToOne
    @JoinColumn(name = "employee_id")
    private Employee employee;

    private Integer periodYear;

    private Integer periodMonth;

    private BigDecimal workingDays;

    private BigDecimal paymentDays;

    private BigDecimal lwpDays;

    private BigDecimal grossPay;

    private BigDecimal totalDeduction;

    private BigDecimal netPay;

    @Enumerated(EnumType.STRING)
    private PayrollRun.Status status;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "slip_id")
    @OrderBy("id")
    private List<SalarySlipLine> lines = new ArrayList<>();
}
